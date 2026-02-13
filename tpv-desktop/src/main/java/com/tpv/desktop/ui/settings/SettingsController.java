package com.tpv.desktop.ui.settings;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.app.AppContext;
import java.awt.Desktop;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class SettingsController {
  private static final String CONNECTIVITY_DIR = ".tpv-desktop/connectivity";
  private static final int MAX_CONNECTIVITY_LINES = 5;
  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @FXML private TextField apiBaseUrlField;
  @FXML private TextField terminalIdField;
  @FXML private TextField restaurantNameField;
  @FXML private TextArea connectivityLogArea;
  @FXML private Label statusLabel;

  @FXML
  public void initialize() {
    apiBaseUrlField.setText(SettingsStore.getApiBaseUrl());
    terminalIdField.setText(SettingsStore.getTerminalId());
    restaurantNameField.setText(SettingsStore.getRestaurantName());
    loadConnectivityLogsPreview();
  }

  @FXML
  public void onSave() {
    String url = apiBaseUrlField.getText() == null ? "" : apiBaseUrlField.getText().trim();
    if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
      statusLabel.setText("URL no valida. Ejemplo: http://localhost:8080");
      return;
    }

    String terminalId = terminalIdField.getText() == null ? "" : terminalIdField.getText().trim();
    if (terminalId.isBlank()) {
      statusLabel.setText("Terminal ID no valido.");
      return;
    }

    String restaurantName = restaurantNameField.getText() == null ? "" : restaurantNameField.getText().trim();
    if (restaurantName.length() < 2) {
      statusLabel.setText("Nombre de negocio no valido (min 2 caracteres).");
      return;
    }

    SettingsStore.setApiBaseUrl(url);
    SettingsStore.setTerminalId(terminalId);
    SettingsStore.setRestaurantName(restaurantName);
    AppContext.get().appState().restaurantNameProperty().set(restaurantName);
    loadConnectivityLogsPreview();
    statusLabel.setText("Guardado.");
  }

  @FXML
  public void onResetTerminalId() {
    SettingsStore.clearTerminalId();
    terminalIdField.setText(SettingsStore.getTerminalId());
    loadConnectivityLogsPreview();
    statusLabel.setText("Terminal ID restaurado al valor automatico.");
  }

  @FXML
  public void onOpenConnectivityLogs() {
    try {
      Path dir = connectivityDir();
      Files.createDirectories(dir);
      if (!Desktop.isDesktopSupported()) {
        statusLabel.setText("No se puede abrir el explorador en este sistema.");
        return;
      }
      Desktop.getDesktop().open(dir.toFile());
      statusLabel.setText("Carpeta de logs abierta.");
    } catch (Exception e) {
      statusLabel.setText("No se pudo abrir logs: " + e.getMessage());
    }
  }

  @FXML
  public void onClearConnectivityLogs() {
    try {
      Path file = connectivityHistoryFile();
      Files.createDirectories(file.getParent());
      Files.write(file, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      loadConnectivityLogsPreview();
      statusLabel.setText("Logs de red limpiados para este terminal.");
    } catch (Exception e) {
      statusLabel.setText("No se pudo limpiar logs: " + e.getMessage());
    }
  }

  @FXML
  public void onReloadConnectivityLogs() {
    loadConnectivityLogsPreview();
    statusLabel.setText("Logs de red recargados.");
  }

  @FXML
  public void onCopyConnectivityDiagnostics() {
    try {
      String diagnostics = buildConnectivityDiagnostics();
      ClipboardContent content = new ClipboardContent();
      content.putString(diagnostics);
      Clipboard.getSystemClipboard().setContent(content);
      statusLabel.setText("Diagnostico copiado al portapapeles.");
    } catch (Exception e) {
      statusLabel.setText("No se pudo copiar diagnostico: " + e.getMessage());
    }
  }

  @FXML
  public void onTest() {
    statusLabel.setText("");
    try {
      CashApi.current();
      statusLabel.setText("Conexion OK.");
    } catch (Exception e) {
      statusLabel.setText("Fallo conexion/auth: " + e.getMessage());
    }
  }

  @FXML
  public void onReleaseOwnLocks() {
    statusLabel.setText("");
    try {
      String terminalId = SettingsStore.getTerminalId();
      SalonTableResponse[] tables = SalonApi.tables();
      int ownLocks = 0;
      int released = 0;

      for (SalonTableResponse table : tables) {
        String lockedTerminal = table.lockedTerminalId();
        if (lockedTerminal == null || !lockedTerminal.equalsIgnoreCase(terminalId)) {
          continue;
        }
        ownLocks++;
        try {
          SalonApi.unlockTable(table.tableNumber());
          released++;
        } catch (ApiException ex) {
          // 409 = conflicto de lock (p.ej. lock ya caducado/cambiado), seguimos con el resto.
          if (ex.getStatus() != 409) {
            throw ex;
          }
        } catch (Exception ex) {
          // Continue with remaining tables even if one unlock fails.
        }
      }

      if (ownLocks == 0) {
        statusLabel.setText("No hay mesas bloqueadas por este terminal.");
      } else if (released == ownLocks) {
        statusLabel.setText("Bloqueos liberados: " + released + ".");
      } else {
        statusLabel.setText("Liberados " + released + " de " + ownLocks + " bloqueos.");
      }
    } catch (ApiException e) {
      if (e.getStatus() == 401 || e.getStatus() == 403) {
        statusLabel.setText("No autorizado (sesion expirada o token invalido). Haz logout/login y reintenta.");
      } else {
        statusLabel.setText("No se pudieron liberar bloqueos: " + e.getMessage());
      }
    } catch (Exception e) {
      statusLabel.setText("No se pudieron liberar bloqueos: " + e.getMessage());
    }
  }

  @FXML
  public void onLogout() {
    AuthStore.clear();
    Nav.goToLogin();
  }

  private static Path connectivityDir() {
    String home = System.getProperty("user.home", ".");
    return Path.of(home, CONNECTIVITY_DIR);
  }

  private static Path connectivityHistoryFile() {
    String terminal = sanitizeTerminalId(SettingsStore.getTerminalId());
    return connectivityDir().resolve("backend-errors-" + terminal + ".log");
  }

  private static String sanitizeTerminalId(String terminalId) {
    if (terminalId == null || terminalId.isBlank()) {
      return "unknown";
    }
    return terminalId.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private void loadConnectivityLogsPreview() {
    if (connectivityLogArea == null) {
      return;
    }
    try {
      Path file = connectivityHistoryFile();
      if (!Files.exists(file)) {
        connectivityLogArea.setText("");
        return;
      }
      List<String> lines = Files.readAllLines(file);
      if (lines.isEmpty()) {
        connectivityLogArea.setText("");
        return;
      }
      int toIndex = Math.min(MAX_CONNECTIVITY_LINES, lines.size());
      List<String> preview = lines.subList(0, toIndex);
      connectivityLogArea.setText(String.join("\n", preview));
    } catch (Exception e) {
      connectivityLogArea.setText("No se pudieron cargar logs: " + e.getMessage());
    }
  }

  private String buildConnectivityDiagnostics() {
    Path logFile = connectivityHistoryFile();
    String lines = connectivityLogArea == null || connectivityLogArea.getText() == null || connectivityLogArea.getText().isBlank()
            ? "(sin errores recientes)"
            : connectivityLogArea.getText();

    return String.join("\n",
            "TPV Connectivity Diagnostics",
            "Timestamp: " + LocalDateTime.now().format(TS_FMT),
            "API Base URL: " + SettingsStore.getApiBaseUrl(),
            "Terminal ID: " + SettingsStore.getTerminalId(),
            "Log file: " + logFile.toAbsolutePath(),
            "Recent entries:",
            lines
    );
  }
}
