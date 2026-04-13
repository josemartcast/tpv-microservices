package com.tpv.desktop.ui.components;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public class TopBarController {

    public enum NetworkStatus {
        ONLINE,
        DEGRADED,
        OFFLINE
    }

    @FXML
    private Label venueLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Label networkBadge;
    @FXML
    private Label printerBadge;

    private final Tooltip networkTooltip = new Tooltip();

    public void setVenueName(String venue) {
        venueLabel.setText(venue == null || venue.isBlank() ? "Restaurante" : venue);
    }

    public void setTitle(String title) {
        titleLabel.setText(title == null || title.isBlank() ? "BARIX TPV" : title);
    }

    public void setCurrentUser(String username) {
        userLabel.setText(username == null || username.isBlank() ? "Sin usuario" : username);
    }

    public void setNetworkStatus(NetworkStatus status) {
        setNetworkStatus(status, null);
    }

    public void setNetworkStatus(NetworkStatus status, Long latencyMs) {
        setNetworkStatus(status, latencyMs, null);
    }

    public void setNetworkStatus(NetworkStatus status, Long latencyMs, String detail) {
        if (status == null) {
            status = NetworkStatus.OFFLINE;
        }
        networkBadge.getStyleClass().removeAll("status-online", "status-degraded", "status-offline");
        clearNetworkTooltip();
        switch (status) {
            case ONLINE -> {
                networkBadge.setText(latencyBadgeText("ONLINE", latencyMs));
                networkBadge.getStyleClass().add("status-online");
            }
            case DEGRADED -> {
                networkBadge.setText(latencyBadgeText("DEGRADED", latencyMs));
                networkBadge.getStyleClass().add("status-degraded");
            }
            case OFFLINE -> {
                networkBadge.setText("OFFLINE");
                networkBadge.getStyleClass().add("status-offline");
                if (detail != null && !detail.isBlank()) {
                    networkTooltip.setText(detail);
                    Tooltip.install(networkBadge, networkTooltip);
                }
            }
            default -> {
                networkBadge.setText("OFFLINE");
                networkBadge.getStyleClass().add("status-offline");
            }
        }
    }

    private String latencyBadgeText(String label, Long latencyMs) {
        if (latencyMs == null || latencyMs < 0) {
            return label;
        }
        return label + " " + latencyMs + "ms";
    }

    private void clearNetworkTooltip() {
        Tooltip.uninstall(networkBadge, networkTooltip);
        networkTooltip.setText("");
    }

    public void setPrinterStatus(int pending, boolean hasErrors) {
        if (hasErrors) {
            printerBadge.setText("PRINT ERR");
            printerBadge.getStyleClass().removeAll("status-online", "status-degraded");
            if (!printerBadge.getStyleClass().contains("status-offline")) {
                printerBadge.getStyleClass().add("status-offline");
            }
            return;
        }
        if (pending > 0) {
            printerBadge.setText("PRINT Q " + pending);
            printerBadge.getStyleClass().removeAll("status-online", "status-offline");
            if (!printerBadge.getStyleClass().contains("status-degraded")) {
                printerBadge.getStyleClass().add("status-degraded");
            }
            return;
        }
        printerBadge.setText("PRINT OK");
        printerBadge.getStyleClass().removeAll("status-degraded", "status-offline");
        if (!printerBadge.getStyleClass().contains("status-online")) {
            printerBadge.getStyleClass().add("status-online");
        }
    }
}
