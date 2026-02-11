package com.tpv.desktop.ui.components;

import com.tpv.desktop.core.MoneyUtil;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.ui.salon.MesaStatus;
import com.tpv.desktop.ui.salon.MesaViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import java.time.Duration;
import java.time.Instant;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MesaCardController {

    @FXML
    private VBox root;
    @FXML
    private Label mesaNameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label lockLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label pendingLabel;
    @FXML
    private Button openButton;

    private Runnable onPrimaryAction;
    private MesaStatus currentStatus = MesaStatus.FREE;
    private String currentLockedBy;
    private String currentLockedTerminalId;
    private Instant currentLockExpiresAt;
    private final Timeline lockTicker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateLockLabel()));

    @FXML
    public void initialize() {
        lockTicker.setCycleCount(Timeline.INDEFINITE);
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                lockTicker.stop();
            } else {
                lockTicker.play();
            }
        });
    }

    public void bind(MesaViewModel model, Runnable action) {
        this.onPrimaryAction = action;
        this.currentStatus = model.status();
        this.currentLockedBy = model.lockedBy();
        this.currentLockedTerminalId = model.lockedTerminalId();
        this.currentLockExpiresAt = model.lockExpiresAt();
        mesaNameLabel.setText(model.name());
        timerLabel.setText(model.elapsedMinutes() > 0 ? model.elapsedMinutes() + " min" : "-");
        totalLabel.setText(model.totalCents() > 0 ? MoneyUtil.centsToEuros(model.totalCents()) + " EUR" : "-");
        pendingLabel.setText(model.pendingItems() > 0 ? "Pending " + model.pendingItems() : "No pending");
        applyStatus(currentStatus, currentLockedBy, currentLockedTerminalId, currentLockExpiresAt);
        updateLockLabel();
    }

    @FXML
    public void onPrimaryAction() {
        if (onPrimaryAction != null) {
            onPrimaryAction.run();
        }
    }

    private void applyStatus(MesaStatus status, String lockedBy, String lockedTerminalId, Instant lockExpiresAt) {
        root.getStyleClass().removeAll("mesa-free", "mesa-occupied", "mesa-pending", "mesa-incident", "mesa-locked", "mesa-has-lock");
        openButton.setDisable(false);
        openButton.setText("Open");
        lockLabel.setText("");
        boolean hasActiveLock = hasActiveLock(lockedBy, lockedTerminalId, lockExpiresAt);
        boolean lockedByMe = isLockedByCurrentTerminal(lockedTerminalId);

        switch (status) {
            case FREE -> {
                root.getStyleClass().add("mesa-free");
                statusLabel.setText("Free");
                openButton.setText("Open");
            }
            case OCCUPIED -> {
                root.getStyleClass().add("mesa-occupied");
                statusLabel.setText("Occupied");
                openButton.setText("Enter");
            }
            case PENDING_SEND -> {
                root.getStyleClass().add("mesa-pending");
                statusLabel.setText("Pending send");
                openButton.setText("Enter");
            }
            case INCIDENT -> {
                root.getStyleClass().add("mesa-incident");
                statusLabel.setText("Incident");
                openButton.setText("Enter");
            }
            case LOCKED -> {
                root.getStyleClass().add("mesa-locked");
                statusLabel.setText(lockedByMe ? "Locked (me)" : "Locked");
                openButton.setText(lockedByMe ? "Enter" : "In use");
                openButton.setDisable(!lockedByMe);
            }
            default -> {
                root.getStyleClass().add("mesa-free");
                statusLabel.setText("Free");
            }
        }

        if (hasActiveLock && status != MesaStatus.LOCKED) {
            root.getStyleClass().add("mesa-has-lock");
            lockLabel.setText(lockedByMe ? "Editing on this terminal" : "Editing on " + firstNonBlank(lockedBy, lockedTerminalId, "other terminal"));
            if (!lockedByMe) {
                openButton.setText("In use");
                openButton.setDisable(true);
            }
        }
    }

    private void updateLockLabel() {
        if (!hasActiveLock(currentLockedBy, currentLockedTerminalId, currentLockExpiresAt)) {
            lockLabel.setText("");
            return;
        }
        String owner = firstNonBlank(
                currentLockedBy,
                isLockedByCurrentTerminal(currentLockedTerminalId) ? "this terminal" : currentLockedTerminalId,
                "other terminal"
        );
        lockLabel.setText("By " + owner + " - " + lockRemainingText(currentLockExpiresAt));
    }

    private static boolean isLockedByCurrentTerminal(String lockedTerminalId) {
        if (lockedTerminalId == null || lockedTerminalId.isBlank()) return false;
        String terminalId = SettingsStore.getTerminalId();
        return terminalId != null && terminalId.equalsIgnoreCase(lockedTerminalId);
    }

    private static String firstNonBlank(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (secondary != null && !secondary.isBlank()) return secondary;
        return fallback;
    }

    private static String lockRemainingText(Instant lockExpiresAt) {
        if (lockExpiresAt == null) return "no TTL";
        long remaining = Duration.between(Instant.now(), lockExpiresAt).toSeconds();
        if (remaining <= 0) return "expiring";
        return remaining + "s";
    }

    private static boolean hasActiveLock(String lockedBy, String lockedTerminalId, Instant lockExpiresAt) {
        return (lockedBy != null && !lockedBy.isBlank())
                || (lockedTerminalId != null && !lockedTerminalId.isBlank())
                || lockExpiresAt != null;
    }
}
