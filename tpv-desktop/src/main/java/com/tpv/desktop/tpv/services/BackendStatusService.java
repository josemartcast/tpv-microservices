package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.BackendStatus;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

public interface BackendStatusService {
    ObjectProperty<BackendStatus> statusProperty();
    LongProperty latencyMsProperty();
    StringProperty lastErrorProperty();
    ObservableList<String> errorHistory();
    void probe();
}

