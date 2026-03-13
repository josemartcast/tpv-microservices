package com.tpv.desktop.ui.settings;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.BusinessProfileApi;
import com.tpv.desktop.api.pos.BusinessProfileResponse;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.api.pos.UpdateBusinessProfileRequest;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.core.PrinterSettingsStore;
import com.tpv.desktop.core.PrinterSettingsStore.PrinterProfile;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import com.tpv.desktop.ui.UiDialogs;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.Desktop;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;

public class SettingsController {
  private static final String CONNECTIVITY_DIR = ".tpv-desktop/connectivity";
  private static final int MAX_CONNECTIVITY_LINES = 5;
  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @FXML private TextField apiBaseUrlField;
  @FXML private TextField terminalIdField;
  @FXML private TextField restaurantNameField;
  @FXML private TextField fiscalLegalNameField;
  @FXML private TextField fiscalTaxIdField;
  @FXML private TextField fiscalAddressField;
  @FXML private TextField fiscalPostalCodeField;
  @FXML private TextField fiscalCityField;
  @FXML private TextField fiscalProvinceField;
  @FXML private TextField fiscalCountryField;
  @FXML private TextField fiscalPhoneField;
  @FXML private TextField fiscalEmailField;
  @FXML private TextArea connectivityLogArea;
  @FXML private Label statusLabel;
  @FXML private TableView<PrinterProfile> printersTable;
  @FXML private TableColumn<PrinterProfile, String> printerLogicalCol;
  @FXML private TableColumn<PrinterProfile, String> printerDestinationCol;
  @FXML private TableColumn<PrinterProfile, String> printerSystemCol;
  @FXML private TableColumn<PrinterProfile, String> printerEnabledCol;
  @FXML private TextField printerLogicalNameField;
  @FXML private ComboBox<String> printerDestinationCombo;
  @FXML private ComboBox<String> systemPrinterCombo;
  @FXML private CheckBox printerEnabledCheck;

  @FXML
  public void initialize() {
    apiBaseUrlField.setText(SettingsStore.getApiBaseUrl());
    terminalIdField.setText(SettingsStore.getTerminalId());
    restaurantNameField.setText(SettingsStore.getRestaurantName());
    fiscalLegalNameField.setText(SettingsStore.getFiscalLegalName());
    fiscalTaxIdField.setText(SettingsStore.getFiscalTaxId());
    fiscalAddressField.setText(SettingsStore.getFiscalAddress());
    fiscalPostalCodeField.setText(SettingsStore.getFiscalPostalCode());
    fiscalCityField.setText(SettingsStore.getFiscalCity());
    fiscalProvinceField.setText(SettingsStore.getFiscalProvince());
    fiscalCountryField.setText(SettingsStore.getFiscalCountry());
    fiscalPhoneField.setText(SettingsStore.getFiscalPhone());
    fiscalEmailField.setText(SettingsStore.getFiscalEmail());
    loadConnectivityLogsPreview();
    loadBusinessProfileFromServerIfRealMode();
    initializePrinterSection();
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
      showValidationError("Nombre de negocio no valido (min 2 caracteres).");
      return;
    }

    String fiscalLegalName = valueOf(fiscalLegalNameField);
    String fiscalTaxId = valueOf(fiscalTaxIdField).toUpperCase(Locale.ROOT);
    String fiscalAddress = valueOf(fiscalAddressField);
    String fiscalPostalCode = valueOf(fiscalPostalCodeField);
    String fiscalCity = valueOf(fiscalCityField);
    String fiscalProvince = valueOf(fiscalProvinceField);
    String fiscalCountry = valueOf(fiscalCountryField).toUpperCase(Locale.ROOT);
    String fiscalPhone = valueOf(fiscalPhoneField);
    String fiscalEmail = valueOf(fiscalEmailField).toLowerCase(Locale.ROOT);

    boolean hasAnyFiscalData = !fiscalLegalName.isBlank()
            || !fiscalTaxId.isBlank()
            || !fiscalAddress.isBlank()
            || !fiscalPostalCode.isBlank()
            || !fiscalCity.isBlank()
            || !fiscalProvince.isBlank()
            || !fiscalCountry.isBlank();

    if (hasAnyFiscalData) {
      if (fiscalLegalName.length() < 2) {
        showValidationError("La razon social es obligatoria para datos fiscales.");
        return;
      }
      if (fiscalTaxId.isBlank()) {
        showValidationError("El NIF/CIF es obligatorio para datos fiscales.");
        return;
      }
      if (!fiscalTaxId.matches("[A-Z0-9]{5,16}")) {
        showValidationError("NIF/CIF no valido. Usa formato alfanumerico (5-16 caracteres).");
        return;
      }
      if (!fiscalPostalCode.isBlank() && !fiscalPostalCode.matches("[0-9A-Z\\-\\s]{4,10}")) {
        showValidationError("Codigo postal no valido.");
        return;
      }
      if (!fiscalCountry.isBlank() && !fiscalCountry.matches("[A-Z]{2}")) {
        showValidationError("Pais no valido. Usa codigo ISO-2 (ej: ES, FR, PT).");
        return;
      }
    }
    if (!fiscalPhone.isBlank() && !fiscalPhone.matches("[0-9+()\\-\\s]{6,24}")) {
      showValidationError("Telefono fiscal no valido.");
      return;
    }
    if (!fiscalEmail.isBlank() && !fiscalEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
      showValidationError("Email fiscal no valido.");
      return;
    }

    if (fiscalCountry.isBlank()) {
      fiscalCountry = "ES";
      if (fiscalCountryField != null) {
        fiscalCountryField.setText(fiscalCountry);
      }
    }

    SettingsStore.setApiBaseUrl(url);
    SettingsStore.setTerminalId(terminalId);
    SettingsStore.setRestaurantName(restaurantName);
    SettingsStore.setFiscalLegalName(fiscalLegalName);
    SettingsStore.setFiscalTaxId(fiscalTaxId);
    SettingsStore.setFiscalAddress(fiscalAddress);
    SettingsStore.setFiscalPostalCode(fiscalPostalCode);
    SettingsStore.setFiscalCity(fiscalCity);
    SettingsStore.setFiscalProvince(fiscalProvince);
    SettingsStore.setFiscalCountry(fiscalCountry);
    SettingsStore.setFiscalPhone(fiscalPhone);
    SettingsStore.setFiscalEmail(fiscalEmail);

    if (fiscalLegalNameField != null && fiscalLegalNameField.getText() != null) {
      fiscalLegalNameField.setText(fiscalLegalName);
    }
    if (fiscalTaxIdField != null && fiscalTaxIdField.getText() != null) {
      fiscalTaxIdField.setText(fiscalTaxId);
    }
    if (fiscalCountryField != null && fiscalCountryField.getText() != null) {
      fiscalCountryField.setText(fiscalCountry);
    }
    if (fiscalEmailField != null && fiscalEmailField.getText() != null) {
      fiscalEmailField.setText(fiscalEmail);
    }

    AppContext.get().appState().restaurantNameProperty().set(restaurantName);
    boolean remoteSaved = saveBusinessProfileToServerIfRealMode(
            restaurantName,
            fiscalLegalName,
            fiscalTaxId,
            fiscalAddress,
            fiscalPostalCode,
            fiscalCity,
            fiscalProvince,
            fiscalCountry,
            fiscalPhone,
            fiscalEmail
    );
    loadConnectivityLogsPreview();
    statusLabel.setText(remoteSaved ? "Guardado." : "Guardado local. Perfil remoto no actualizado.");
  }

  private static String valueOf(TextField field) {
    if (field == null || field.getText() == null) {
      return "";
    }
    return field.getText().trim();
  }

  private void showValidationError(String message) {
    statusLabel.setText(message);
    UiDialogs.warn("Validacion", message);
  }

  private void loadBusinessProfileFromServerIfRealMode() {
    if (!isRealMode()) {
      return;
    }
    try {
      BusinessProfileResponse profile = BusinessProfileApi.get();
      applyBusinessProfile(profile);
      statusLabel.setText("Perfil de negocio cargado desde servidor.");
    } catch (Exception e) {
      statusLabel.setText("No se pudo cargar perfil remoto. Usando configuracion local.");
    }
  }

  private boolean saveBusinessProfileToServerIfRealMode(
          String businessName,
          String legalName,
          String taxId,
          String address,
          String postalCode,
          String city,
          String province,
          String country,
          String phone,
          String email
  ) {
    if (!isRealMode()) {
      return true;
    }
    try {
      BusinessProfileResponse profile = BusinessProfileApi.update(new UpdateBusinessProfileRequest(
              businessName,
              legalName,
              taxId,
              address,
              postalCode,
              city,
              province,
              country,
              phone,
              email
      ));
      applyBusinessProfile(profile);
      return true;
    } catch (Exception e) {
      UiDialogs.warn(
              "Backend no disponible",
              "No se pudo guardar perfil fiscal en backend. Se guardo solo en local.\nDetalle: " + e.getMessage()
      );
      return false;
    }
  }

  private void applyBusinessProfile(BusinessProfileResponse profile) {
    if (profile == null) {
      return;
    }
    String businessName = normalizeOrDefault(profile.businessName(), "Restaurante EL GUSTO");
    String legalName = normalizeOrDefault(profile.legalName(), businessName);
    String taxId = normalize(profile.taxId()).toUpperCase(Locale.ROOT);
    String address = normalize(profile.address());
    String postalCode = normalize(profile.postalCode());
    String city = normalize(profile.city());
    String province = normalize(profile.province());
    String country = normalizeOrDefault(profile.country(), "ES").toUpperCase(Locale.ROOT);
    String phone = normalize(profile.phone());
    String email = normalize(profile.email()).toLowerCase(Locale.ROOT);

    restaurantNameField.setText(businessName);
    fiscalLegalNameField.setText(legalName);
    fiscalTaxIdField.setText(taxId);
    fiscalAddressField.setText(address);
    fiscalPostalCodeField.setText(postalCode);
    fiscalCityField.setText(city);
    fiscalProvinceField.setText(province);
    fiscalCountryField.setText(country);
    fiscalPhoneField.setText(phone);
    fiscalEmailField.setText(email);

    SettingsStore.setRestaurantName(businessName);
    SettingsStore.setFiscalLegalName(legalName);
    SettingsStore.setFiscalTaxId(taxId);
    SettingsStore.setFiscalAddress(address);
    SettingsStore.setFiscalPostalCode(postalCode);
    SettingsStore.setFiscalCity(city);
    SettingsStore.setFiscalProvince(province);
    SettingsStore.setFiscalCountry(country);
    SettingsStore.setFiscalPhone(phone);
    SettingsStore.setFiscalEmail(email);
    AppContext.get().appState().restaurantNameProperty().set(businessName);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static String normalizeOrDefault(String value, String fallback) {
    String normalized = normalize(value);
    return normalized.isBlank() ? fallback : normalized;
  }

  private static boolean isRealMode() {
    String mode = AppContext.get().appState().runtimeModeProperty().get();
    return "REAL".equalsIgnoreCase(mode);
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

  @FXML
  public void onCreateBackup() {
    try {
      Path repoRoot = resolveRepoRoot();
      Path script = repoRoot.resolve("scripts").resolve("db-backup.ps1");
      if (!Files.exists(script)) {
        UiDialogs.error("Backup", "No se encontro script:\n" + script);
        return;
      }
      Path backupRoot = repoRoot.resolve(".backups");
      Files.createDirectories(backupRoot);
      statusLabel.setText("Creando backup...");
      String output = runPowerShellScript(repoRoot, script, List.of(
              "-OutputRoot", backupRoot.toString()
      ));
      statusLabel.setText("Backup completado.");
      UiDialogs.info("Backup", "Backup completado.\n\n" + trimForDialog(output));
    } catch (Exception e) {
      String msg = "No se pudo crear backup:\n" + e.getMessage();
      statusLabel.setText(msg);
      UiDialogs.error("Backup", msg);
    }
  }

  @FXML
  public void onRestoreBackup() {
    try {
      Path repoRoot = resolveRepoRoot();
      Path script = repoRoot.resolve("scripts").resolve("db-restore.ps1");
      if (!Files.exists(script)) {
        UiDialogs.error("Restore", "No se encontro script:\n" + script);
        return;
      }
      Path backupRoot = repoRoot.resolve(".backups");
      Files.createDirectories(backupRoot);

      DirectoryChooser chooser = new DirectoryChooser();
      chooser.setTitle("Selecciona carpeta de backup");
      chooser.setInitialDirectory(backupRoot.toFile());
      var selected = chooser.showDialog(statusLabel.getScene().getWindow());
      if (selected == null) {
        return;
      }
      Path backupDir = selected.toPath();
      if (!Files.isDirectory(backupDir)) {
        UiDialogs.warn("Restore", "Seleccion no valida.");
        return;
      }

      boolean confirm = UiDialogs.confirm(
              "Restaurar backup",
              "Se restauraran las bases tpv_auth y tpv_pos desde:\n" + backupDir + "\n\nEsto sobrescribe datos actuales. Continuar?"
      );
      if (!confirm) {
        return;
      }
      boolean confirm2 = UiDialogs.confirm(
              "Confirmacion final",
              "Ultima confirmacion: vas a sobrescribir la base de datos actual.\n\nQuieres continuar con la restauracion?"
      );
      if (!confirm2) {
        return;
      }

      statusLabel.setText("Restaurando backup...");
      String output = runPowerShellScript(repoRoot, script, List.of(
              "-BackupDir", backupDir.toString(),
              "-AllowProductionRestore"
      ));
      statusLabel.setText("Restore completado.");
      UiDialogs.info("Restore", "Restore completado.\n\n" + trimForDialog(output));
    } catch (Exception e) {
      String msg = "No se pudo restaurar backup:\n" + e.getMessage();
      statusLabel.setText(msg);
      UiDialogs.error("Restore", msg);
    }
  }

  @FXML
  public void onOpenBackupsFolder() {
    try {
      Path repoRoot = resolveRepoRoot();
      Path backupRoot = repoRoot.resolve(".backups");
      Files.createDirectories(backupRoot);
      if (!Desktop.isDesktopSupported()) {
        statusLabel.setText("No se puede abrir explorador en este sistema.");
        return;
      }
      Desktop.getDesktop().open(backupRoot.toFile());
      statusLabel.setText("Carpeta de backups abierta.");
    } catch (Exception e) {
      String msg = "No se pudo abrir carpeta backups: " + e.getMessage();
      statusLabel.setText(msg);
      UiDialogs.error("Backups", msg);
    }
  }

  @FXML
  public void onPrinterSave() {
    String logicalName = normalize(printerLogicalNameField == null ? "" : printerLogicalNameField.getText());
    String destination = normalize(printerDestinationCombo == null ? "" : printerDestinationCombo.getValue()).toUpperCase(Locale.ROOT);
    String systemPrinter = normalize(systemPrinterCombo == null ? "" : systemPrinterCombo.getValue());
    boolean enabled = printerEnabledCheck == null || printerEnabledCheck.isSelected();

    if (logicalName.isBlank()) {
      UiDialogs.warn("Impresoras", "El nombre de impresora del negocio es obligatorio.");
      return;
    }
    if (destination.isBlank()) {
      UiDialogs.warn("Impresoras", "Debes seleccionar un destino (BAR/COCINA/POSTRES/ALL).");
      return;
    }
    if (systemPrinter.isBlank()) {
      UiDialogs.warn("Impresoras", "Debes seleccionar una impresora real del sistema.");
      return;
    }

    List<PrinterProfile> profiles = new ArrayList<>(PrinterSettingsStore.getProfiles());
    PrinterProfile selected = printersTable == null ? null : printersTable.getSelectionModel().getSelectedItem();
    PrinterProfile saved;
    if (selected == null) {
      saved = PrinterSettingsStore.newProfile(logicalName, destination, systemPrinter, enabled);
      profiles.add(saved);
    } else {
      saved = new PrinterProfile(selected.id(), logicalName, destination, systemPrinter, enabled);
      for (int i = 0; i < profiles.size(); i++) {
        if (profiles.get(i).id().equals(selected.id())) {
          profiles.set(i, saved);
          break;
        }
      }
    }

    PrinterSettingsStore.saveProfiles(profiles);
    reloadPrinterProfiles(saved.id());
    statusLabel.setText("Impresora guardada.");
  }

  @FXML
  public void onPrinterDelete() {
    if (printersTable == null) {
      return;
    }
    PrinterProfile selected = printersTable.getSelectionModel().getSelectedItem();
    if (selected == null) {
      UiDialogs.warn("Impresoras", "Selecciona una impresora para eliminar.");
      return;
    }
    boolean ok = UiDialogs.confirm(
            "Eliminar impresora",
            "Se eliminara la impresora '" + selected.logicalName() + "'. Continuar?"
    );
    if (!ok) {
      return;
    }

    List<PrinterProfile> profiles = new ArrayList<>(PrinterSettingsStore.getProfiles());
    profiles.removeIf(p -> p.id().equals(selected.id()));
    PrinterSettingsStore.saveProfiles(profiles);
    reloadPrinterProfiles(null);
    clearPrinterEditor();
    statusLabel.setText("Impresora eliminada.");
  }

  @FXML
  public void onPrinterNew() {
    if (printersTable != null) {
      printersTable.getSelectionModel().clearSelection();
    }
    clearPrinterEditor();
    statusLabel.setText("Nueva impresora.");
  }

  @FXML
  public void onRefreshSystemPrinters() {
    refreshSystemPrintersCombo();
    statusLabel.setText("Lista de impresoras del sistema recargada.");
  }

  @FXML
  public void onPrinterTest() {
    String printerName = normalize(systemPrinterCombo == null ? "" : systemPrinterCombo.getValue());
    if (printerName.isBlank()) {
      UiDialogs.warn("Impresoras", "Selecciona una impresora real para enviar test.");
      return;
    }
    try {
      String text = "TPV Desktop\nTEST IMPRESION\n" + LocalDateTime.now().format(TS_FMT) + "\n";
      PrintUtil.printTextToPrinter(printerName, text, null);
      statusLabel.setText("Test enviado a '" + printerName + "'.");
    } catch (Exception e) {
      UiDialogs.error("Impresoras", "No se pudo imprimir test:\n" + e.getMessage());
    }
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

  private void initializePrinterSection() {
    if (printerDestinationCombo == null || systemPrinterCombo == null || printersTable == null) {
      return;
    }

    printerDestinationCombo.setItems(FXCollections.observableArrayList(PrinterSettingsStore.supportedDestinations()));
    printerDestinationCombo.setValue("ALL");
    if (printerEnabledCheck != null) {
      printerEnabledCheck.setSelected(true);
    }

    printerLogicalCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().logicalName()));
    printerDestinationCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().destination()));
    printerSystemCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().systemPrinter()));
    printerEnabledCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().enabled() ? "Si" : "No"));

    printersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
      if (selected == null) {
        return;
      }
      printerLogicalNameField.setText(selected.logicalName());
      printerDestinationCombo.setValue(selected.destination());
      systemPrinterCombo.setValue(selected.systemPrinter());
      if (printerEnabledCheck != null) {
        printerEnabledCheck.setSelected(selected.enabled());
      }
    });

    refreshSystemPrintersCombo();
    reloadPrinterProfiles(null);
  }

  private void refreshSystemPrintersCombo() {
    if (systemPrinterCombo == null) {
      return;
    }
    String current = systemPrinterCombo.getValue();
    List<String> printers = PrintUtil.availablePrinterNames();
    systemPrinterCombo.setItems(FXCollections.observableArrayList(printers));
    if (current != null && !current.isBlank() && printers.contains(current)) {
      systemPrinterCombo.setValue(current);
    } else if (!printers.isEmpty()) {
      systemPrinterCombo.setValue(printers.getFirst());
    }
  }

  private void reloadPrinterProfiles(String selectId) {
    if (printersTable == null) {
      return;
    }
    List<PrinterProfile> profiles = new ArrayList<>(PrinterSettingsStore.getProfiles());
    profiles.sort(Comparator.comparing(PrinterProfile::logicalName, String.CASE_INSENSITIVE_ORDER));
    printersTable.getItems().setAll(profiles);
    if (profiles.isEmpty()) {
      return;
    }
    if (selectId == null || selectId.isBlank()) {
      printersTable.getSelectionModel().selectFirst();
      return;
    }
    for (PrinterProfile profile : profiles) {
      if (profile.id().equals(selectId)) {
        printersTable.getSelectionModel().select(profile);
        return;
      }
    }
    printersTable.getSelectionModel().selectFirst();
  }

  private void clearPrinterEditor() {
    if (printerLogicalNameField != null) {
      printerLogicalNameField.clear();
    }
    if (printerDestinationCombo != null) {
      printerDestinationCombo.setValue("ALL");
    }
    if (printerEnabledCheck != null) {
      printerEnabledCheck.setSelected(true);
    }
  }

  private static Path resolveRepoRoot() {
    Path current = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    for (int i = 0; i < 8 && current != null; i++) {
      Path backup = current.resolve("scripts").resolve("db-backup.ps1");
      Path restore = current.resolve("scripts").resolve("db-restore.ps1");
      if (Files.exists(backup) && Files.exists(restore)) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("No se encontro la raiz del repo (scripts/db-backup.ps1).");
  }

  private static String runPowerShellScript(Path workDir, Path script, List<String> args) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("powershell");
    command.add("-NoProfile");
    command.add("-ExecutionPolicy");
    command.add("Bypass");
    command.add("-File");
    command.add(script.toString());
    command.addAll(args);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workDir.toFile());
    pb.redirectErrorStream(true);
    Process process = pb.start();

    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append('\n');
      }
    }
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("Exit code " + exit + "\n" + output);
    }
    return output.toString();
  }

  private static String trimForDialog(String value) {
    if (value == null || value.isBlank()) {
      return "(sin salida)";
    }
    String trimmed = value.trim();
    if (trimmed.length() <= 2000) {
      return trimmed;
    }
    return trimmed.substring(0, 2000) + "\n...(truncado)";
  }
}
