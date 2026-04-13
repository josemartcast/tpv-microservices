package com.tpv.desktop.ui.layout;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.User;
import com.tpv.desktop.tpv.diagnostics.LeakDiagnostics;
import com.tpv.desktop.ui.components.TopBarController;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MainLayoutController {
    private static final long DEGRADED_THRESHOLD_MS = 1000L;
    private static final int MAX_CONNECTIVITY_ERRORS = 5;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String CONNECTIVITY_DIR = ".tpv-desktop/connectivity";

    @FXML
    private StackPane content;
    @FXML
    private TopBarController topBarController;

    private final AtomicBoolean checkingBackend = new AtomicBoolean(false);
    private final Timeline backendStatusTicker =
            new Timeline(new KeyFrame(javafx.util.Duration.seconds(6), e -> checkBackendStatusAsync()));
    private final Deque<String> connectivityErrors = new ArrayDeque<>();
    private boolean backendTickerCounted;

    @FXML
    public void initialize() {
        if (topBarController != null) {
            topBarController.setVenueName("Restaurante");
            topBarController.setTitle("Salon");
            topBarController.setCurrentUser("Usuario activo");
            topBarController.setNetworkStatus(TopBarController.NetworkStatus.OFFLINE);
        }
        loadConnectivityHistory();
        backendStatusTicker.setCycleCount(Timeline.INDEFINITE);
        content.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                backendStatusTicker.stop();
                if (backendTickerCounted) {
                    backendTickerCounted = false;
                    LeakDiagnostics.timerStopped("MainLayoutController.backendStatusTicker");
                }
            } else {
                backendStatusTicker.play();
                if (!backendTickerCounted) {
                    backendTickerCounted = true;
                    LeakDiagnostics.timerStarted("MainLayoutController.backendStatusTicker");
                }
            }
        });
        checkBackendStatusAsync();
        goSalon();
    }

    @FXML
    public void goSalon() {
        loadCenter("/fxml/salon/SalonView.fxml");
    }

    @FXML
    public void goSales() {
        loadCenter("/fxml/sales/SalesView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Toma de comanda");
        }
    }

    @FXML
    public void goCash() {
        loadCenter("/fxml/cash/CashView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Caja");
        }
    }

    @FXML
    public void goHistory() {
        loadCenter("/fxml/history/HistoryView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Historial");
        }
    }

    @FXML
    public void goFiscal() {
        loadCenter("/fxml/fiscal/FiscalView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Resumen fiscal");
        }
    }

    @FXML
    public void goSettings() {
        loadCenter("/fxml/settings/SettingsView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Ajustes");
        }
    }

    @FXML
    public void goFiscalClosure() {
        loadCenter("/fxml/fiscal/FiscalClosureView.fxml");
        if (topBarController != null) {
            topBarController.setTitle("Cierre fiscal");
        }
    }

    @FXML
    public void logout() {
        backendStatusTicker.stop();
        AuthStore.clear();
        AppContext.get().appState().activeUserProperty().set(new User(0, "", ""));
        Navigator.get().goLogin();
    }

    private void loadCenter(String fxml) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(fxml));
            content.getChildren().setAll(view);
        } catch (Exception e) {
            content.getChildren().setAll(new Label("No se pudo cargar: " + fxml + "\n" + e.getMessage()));
        }
    }

    private void checkBackendStatusAsync() {
        if (topBarController == null || !checkingBackend.compareAndSet(false, true)) {
            return;
        }
        final long startedAt = System.nanoTime();
        CompletableFuture.runAsync(() -> {
            try {
                SalonApi.tables();
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                TopBarController.NetworkStatus status =
                        elapsedMs > DEGRADED_THRESHOLD_MS
                                ? TopBarController.NetworkStatus.DEGRADED
                                : TopBarController.NetworkStatus.ONLINE;
                long latencyMs = elapsedMs;
                Platform.runLater(() -> topBarController.setNetworkStatus(status, latencyMs));
            } catch (Exception ex) {
                String rootCause = rootCauseMessage(ex);
                rememberConnectivityError(rootCause);
                String detail = buildConnectivityDetail(rootCause);
                Platform.runLater(() -> topBarController.setNetworkStatus(TopBarController.NetworkStatus.OFFLINE, null, detail));
            } finally {
                checkingBackend.set(false);
            }
        });
    }

    private void rememberConnectivityError(String rootCause) {
        String line = LocalTime.now().format(TIME_FMT) + " - " + rootCause;
        connectivityErrors.addFirst(line);
        while (connectivityErrors.size() > MAX_CONNECTIVITY_ERRORS) {
            connectivityErrors.removeLast();
        }
        persistConnectivityHistory();
    }

    private String buildConnectivityDetail(String latestError) {
        List<String> lines = new ArrayList<>();
        lines.add("Backend unreachable: " + latestError);
        if (!connectivityErrors.isEmpty()) {
            lines.add("Recent errors:");
            lines.addAll(connectivityErrors);
        }
        return String.join("\n", lines);
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void loadConnectivityHistory() {
        try {
            Path file = connectivityHistoryFile();
            if (!Files.exists(file)) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8)
                    .stream()
                    .filter(line -> line != null && !line.isBlank())
                    .limit(MAX_CONNECTIVITY_ERRORS)
                    .collect(Collectors.toList());
            connectivityErrors.clear();
            connectivityErrors.addAll(lines);
        } catch (Exception ignored) {
            connectivityErrors.clear();
        }
    }

    private void persistConnectivityHistory() {
        try {
            Path file = connectivityHistoryFile();
            Files.createDirectories(file.getParent());
            Files.write(
                    file,
                    new ArrayList<>(connectivityErrors),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
            // Non-blocking diagnostic feature; ignore persistence failures.
        }
    }

    private Path connectivityHistoryFile() {
        String home = System.getProperty("user.home", ".");
        String terminal = sanitizeTerminalId(SettingsStore.getTerminalId());
        return Path.of(home, CONNECTIVITY_DIR, "backend-errors-" + terminal + ".log");
    }

    private static String sanitizeTerminalId(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return "unknown";
        }
        return terminalId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
