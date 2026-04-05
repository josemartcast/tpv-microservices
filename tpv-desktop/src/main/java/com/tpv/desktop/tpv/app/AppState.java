package com.tpv.desktop.tpv.app;

import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.domain.model.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;

public class AppState {
    private final StringProperty terminalId = new SimpleStringProperty("T-001");
    private final ObjectProperty<User> activeUser = new SimpleObjectProperty<>(new User(1, "Gasa Carmona", "GC"));
    private final ObjectProperty<BackendStatus> backendStatus = new SimpleObjectProperty<>(BackendStatus.ONLINE);
    private final StringProperty restaurantName = new SimpleStringProperty("Restaurante EL GUSTO");
    private final StringProperty runtimeMode = new SimpleStringProperty("FAKE");
    private final StringProperty activeCustomer = new SimpleStringProperty("Mostrador");
    private final StringProperty lastComandaPrintText = new SimpleStringProperty("");
    private final ObjectProperty<Map<String, String>> lastComandaPrintJobs = new SimpleObjectProperty<>(Map.of());
    private final BooleanProperty printSeparateByDestination = new SimpleBooleanProperty(true);
    private final BooleanProperty touchMode = new SimpleBooleanProperty(false);
    private final BooleanProperty kioskMode = new SimpleBooleanProperty(false);

    public StringProperty terminalIdProperty() { return terminalId; }
    public ObjectProperty<User> activeUserProperty() { return activeUser; }
    public ObjectProperty<BackendStatus> backendStatusProperty() { return backendStatus; }
    public StringProperty restaurantNameProperty() { return restaurantName; }
    public StringProperty runtimeModeProperty() { return runtimeMode; }
    public StringProperty activeCustomerProperty() { return activeCustomer; }
    public StringProperty lastComandaPrintTextProperty() { return lastComandaPrintText; }
    public ObjectProperty<Map<String, String>> lastComandaPrintJobsProperty() { return lastComandaPrintJobs; }
    public BooleanProperty printSeparateByDestinationProperty() { return printSeparateByDestination; }
    public BooleanProperty touchModeProperty() { return touchMode; }
    public BooleanProperty kioskModeProperty() { return kioskMode; }
}

