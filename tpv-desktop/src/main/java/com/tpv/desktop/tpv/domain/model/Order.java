package com.tpv.desktop.tpv.domain.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

public class Order {
    private final LongProperty id = new SimpleLongProperty();
    private final IntegerProperty tableId = new SimpleIntegerProperty();
    private final IntegerProperty people = new SimpleIntegerProperty(4);
    private final BooleanProperty billRequested = new SimpleBooleanProperty(false);
    private final Instant openedAt;
    private final ObservableList<OrderLine> lines = FXCollections.observableArrayList();

    public Order(long id, int tableId, int people, Instant openedAt) {
        this.id.set(id);
        this.tableId.set(tableId);
        this.people.set(people);
        this.openedAt = openedAt;
    }

    public long getId() { return id.get(); }
    public int getTableId() { return tableId.get(); }
    public int getPeople() { return people.get(); }
    public IntegerProperty peopleProperty() { return people; }
    public boolean isBillRequested() { return billRequested.get(); }
    public BooleanProperty billRequestedProperty() { return billRequested; }
    public Instant getOpenedAt() { return openedAt; }
    public ObservableList<OrderLine> getLines() { return lines; }

    public int totalCents() {
        return lines.stream().mapToInt(OrderLine::lineTotalCents).sum();
    }

    public int pendingCount() {
        return lines.stream().mapToInt(OrderLine::getPendingQty).sum();
    }

    public void setBillRequested(boolean value) {
        billRequested.set(value);
    }
}

