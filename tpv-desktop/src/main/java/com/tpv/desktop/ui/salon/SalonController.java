package com.tpv.desktop.ui.salon;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.core.AppState;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.ui.components.MesaCardController;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;

public class SalonController {
    private static final String SYNC_FRESH = "sync-fresh";
    private static final String SYNC_WARN = "sync-warn";
    private static final String SYNC_STALE = "sync-stale";

    @FXML
    private BorderPane root;
    @FXML
    private ComboBox<String> zoneFilter;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private TilePane mesasPane;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Label lastSyncLabel;

    private final List<MesaViewModel> mesas = new ArrayList<>();
    private final Timeline autoRefresh = new Timeline(new KeyFrame(javafx.util.Duration.seconds(5), e -> loadFromBackend(false)));
    private final Timeline syncBadgeTicker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refreshSyncBadge()));
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private Instant lastSyncAt;

    @FXML
    public void initialize() {
        zoneFilter.getItems().setAll("All zones", "Salon");
        zoneFilter.getSelectionModel().selectFirst();

        statusFilter.getItems().setAll("All", "Free", "Occupied", "Pending", "Incident", "Locked");
        statusFilter.getSelectionModel().selectFirst();

        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        syncBadgeTicker.setCycleCount(Timeline.INDEFINITE);
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                autoRefresh.stop();
                syncBadgeTicker.stop();
            } else {
                autoRefresh.play();
                syncBadgeTicker.play();
            }
        });

        loadFromBackend(true);
    }

    @FXML
    public void onFilterChanged() {
        renderMesas();
    }

    @FXML
    public void onRefresh() {
        loadFromBackend(true);
    }

    private void loadFromBackend(boolean userTriggered) {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return SalonApi.tables();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((tables, error) -> Platform.runLater(() -> {
                    loading.set(false);
                    if (error != null) {
                        feedbackLabel.setText("Could not load tables: " + rootCauseMessage(error));
                        return;
                    }

                    mesas.clear();
                    if (tables != null) {
                        for (SalonTableResponse t : tables) {
                            MesaStatus resolvedStatus = resolveBusinessStatus(t);
                            mesas.add(new MesaViewModel(
                                    t.tableNumber(),
                                    "Mesa " + t.tableNumber(),
                                    resolvedStatus,
                                    t.ticketId(),
                                    t.elapsedMinutes(),
                                    t.totalCents(),
                                    t.pendingLines(),
                                    null,
                                    t.lockedBy(),
                                    t.lockedTerminalId(),
                                    t.lockExpiresAt()
                            ));
                        }
                    }
                    lastSyncAt = Instant.now();
                    refreshSyncBadge();
                    if (userTriggered) {
                        feedbackLabel.setText("Tables synced.");
                    }
                    renderMesas();
                }));
    }

    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void refreshSyncBadge() {
        if (lastSyncLabel == null) return;
        lastSyncLabel.getStyleClass().removeAll(SYNC_FRESH, SYNC_WARN, SYNC_STALE);
        if (lastSyncAt == null) {
            lastSyncLabel.setText("No sync yet");
            lastSyncLabel.getStyleClass().add(SYNC_STALE);
            return;
        }
        long seconds = Duration.between(lastSyncAt, Instant.now()).toSeconds();
        if (seconds < 1) {
            lastSyncLabel.setText("Synced now");
            lastSyncLabel.getStyleClass().add(SYNC_FRESH);
            return;
        }
        lastSyncLabel.setText("Synced " + seconds + "s ago");
        if (seconds <= 5) {
            lastSyncLabel.getStyleClass().add(SYNC_FRESH);
        } else if (seconds <= 15) {
            lastSyncLabel.getStyleClass().add(SYNC_WARN);
        } else {
            lastSyncLabel.getStyleClass().add(SYNC_STALE);
        }
    }

    private MesaStatus parseStatus(String status) {
        if (status == null) {
            return MesaStatus.FREE;
        }
        return switch (status.toUpperCase()) {
            case "FREE" -> MesaStatus.FREE;
            case "OCCUPIED" -> MesaStatus.OCCUPIED;
            case "PENDING_SEND" -> MesaStatus.PENDING_SEND;
            case "INCIDENT" -> MesaStatus.INCIDENT;
            case "LOCKED" -> MesaStatus.LOCKED;
            default -> MesaStatus.FREE;
        };
    }

    private MesaStatus resolveBusinessStatus(SalonTableResponse table) {
        MesaStatus apiStatus = parseStatus(table.status());
        if (apiStatus == MesaStatus.LOCKED && table.ticketId() != null) {
            return table.pendingLines() > 0 ? MesaStatus.PENDING_SEND : MesaStatus.OCCUPIED;
        }
        return apiStatus;
    }

    private void renderMesas() {
        mesasPane.getChildren().clear();
        for (MesaViewModel mesa : mesas) {
            if (!matchesStatusFilter(mesa)) {
                continue;
            }
            Node node = buildCardNode(mesa);
            if (node != null) {
                mesasPane.getChildren().add(node);
            }
        }
        if (mesasPane.getChildren().isEmpty()) {
            feedbackLabel.setText("No tables for selected filter.");
        }
    }

    private boolean matchesStatusFilter(MesaViewModel mesa) {
        String selected = statusFilter.getSelectionModel().getSelectedItem();
        if (selected == null || "All".equalsIgnoreCase(selected)) {
            return true;
        }
        MesaStatus status = mesa.status();
        return switch (selected) {
            case "Free" -> status == MesaStatus.FREE;
            case "Occupied" -> status == MesaStatus.OCCUPIED;
            case "Pending" -> status == MesaStatus.PENDING_SEND;
            case "Incident" -> status == MesaStatus.INCIDENT;
            case "Locked" -> mesa.hasActiveLock() || status == MesaStatus.LOCKED;
            default -> true;
        };
    }

    private Node buildCardNode(MesaViewModel mesa) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/MesaCard.fxml"));
            Node node = loader.load();
            MesaCardController cardController = loader.getController();
            cardController.bind(mesa, () -> onMesaAction(mesa));
            return node;
        } catch (IOException e) {
            feedbackLabel.setText("Could not render table card: " + e.getMessage());
            return null;
        }
    }

    private void onMesaAction(MesaViewModel mesa) {
        if (mesa.hasActiveLock() && !isLockedByCurrentTerminal(mesa.lockedTerminalId())) {
            feedbackLabel.setText("Table is locked.");
            return;
        }
        try {
            SalonApi.lockTable((int) mesa.id());
            Long existingTicketId = mesa.ticketId();
            Long targetTicketId;
            if (existingTicketId != null) {
                targetTicketId = existingTicketId;
            } else {
                var ticket = SalonApi.openTicket((int) mesa.id());
                targetTicketId = ticket.id();
            }
            AppState.setResumeTicketId(targetTicketId);
            Nav.goToSales();
        } catch (Exception e) {
            feedbackLabel.setText("Could not open table: " + e.getMessage());
        }
    }

    private boolean isLockedByCurrentTerminal(String lockedTerminalId) {
        if (lockedTerminalId == null || lockedTerminalId.isBlank()) {
            return false;
        }
        String terminalId = SettingsStore.getTerminalId();
        return terminalId != null && terminalId.equalsIgnoreCase(lockedTerminalId);
    }
}
