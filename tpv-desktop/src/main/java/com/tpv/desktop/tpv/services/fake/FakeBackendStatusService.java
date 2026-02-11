package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.ApiClient;
import com.tpv.desktop.tpv.services.BackendStatusService;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class FakeBackendStatusService implements BackendStatusService {
    private final ApiClient apiClient;
    private final ObjectProperty<BackendStatus> status = new SimpleObjectProperty<>(BackendStatus.ONLINE);
    private final LongProperty latencyMs = new SimpleLongProperty(45);
    private final StringProperty lastError = new SimpleStringProperty("");
    private final ObservableList<String> errors = FXCollections.observableArrayList();
    private int failures;

    public FakeBackendStatusService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public ObjectProperty<BackendStatus> statusProperty() { return status; }
    @Override
    public LongProperty latencyMsProperty() { return latencyMs; }
    @Override
    public StringProperty lastErrorProperty() { return lastError; }
    @Override
    public ObservableList<String> errorHistory() { return errors; }

    @Override
    public void probe() {
        CompletableFuture.runAsync(() -> {
            long t0 = System.currentTimeMillis();
            try {
                apiClient.ping();
                long elapsed = System.currentTimeMillis() - t0;
                Platform.runLater(() -> {
                    failures = 0;
                    latencyMs.set(elapsed);
                    status.set(elapsed > 170 ? BackendStatus.DEGRADED : BackendStatus.ONLINE);
                });
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t0;
                Platform.runLater(() -> {
                    failures++;
                    latencyMs.set(elapsed);
                    status.set(failures >= 2 ? BackendStatus.OFFLINE : BackendStatus.DEGRADED);
                    String msg = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + e.getMessage();
                    lastError.set(msg);
                    errors.add(0, msg);
                    while (errors.size() > 5) errors.remove(errors.size() - 1);
                });
            }
        });
    }
}

