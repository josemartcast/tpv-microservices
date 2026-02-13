package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.BackendStatusService;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

public class TopBarViewModel {
    private final AppState appState;
    private final BackendStatusService backendStatusService;
    private final StringProperty centerTitle = new SimpleStringProperty();

    public TopBarViewModel() {
        AppContext ctx = AppContext.get();
        this.appState = ctx.appState();
        this.backendStatusService = ctx.backendStatusService();
        this.centerTitle.set(appState.restaurantNameProperty().get());
    }

    public StringProperty centerTitleProperty() { return centerTitle; }
    public StringProperty restaurantNameProperty() { return appState.restaurantNameProperty(); }
    public StringProperty usernameProperty() {
        return new SimpleStringProperty(appState.activeUserProperty().get().displayName());
    }

    public StringProperty backendBadgeTextProperty() {
        return new SimpleStringProperty(backendStatusService.statusProperty().get().name());
    }

    public StringProperty latencyTextProperty() {
        return new SimpleStringProperty(backendStatusService.latencyMsProperty().get() + "ms");
    }

    public ObservableList<String> errorHistory() { return backendStatusService.errorHistory(); }
    public StringProperty lastErrorProperty() { return backendStatusService.lastErrorProperty(); }

    public String badgeStyleClass() {
        BackendStatus st = backendStatusService.statusProperty().get();
        return switch (st) {
            case ONLINE -> "badge-online";
            case DEGRADED -> "badge-degraded";
            case OFFLINE -> "badge-offline";
        };
    }

    public void setCenterTitle(String value) {
        centerTitle.set(value);
    }

    public void bindReactive(StringProperty badgeText, StringProperty latencyText) {
        badgeText.bind(Bindings.createStringBinding(
                () -> backendStatusService.statusProperty().get().name(),
                backendStatusService.statusProperty()));
        latencyText.bind(Bindings.createStringBinding(
                () -> backendStatusService.latencyMsProperty().get() + "ms",
                backendStatusService.latencyMsProperty()));
    }
}

