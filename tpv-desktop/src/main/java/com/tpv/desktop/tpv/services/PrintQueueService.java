package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.PrintQueueState;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

public interface PrintQueueService {
    ObjectProperty<PrintQueueState> stateProperty();
    IntegerProperty pendingJobsProperty();
    StringProperty lastErrorProperty();
    ObservableList<String> errorHistory();
    void enqueue(String destination, String text);
}
