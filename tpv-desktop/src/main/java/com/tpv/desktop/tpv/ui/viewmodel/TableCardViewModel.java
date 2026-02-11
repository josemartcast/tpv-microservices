package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.domain.model.TableStatus;
import javafx.beans.property.*;

public class TableCardViewModel {
    private final IntegerProperty tableId = new SimpleIntegerProperty();
    private final LongProperty orderId = new SimpleLongProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty totalText = new SimpleStringProperty("-");
    private final StringProperty elapsedText = new SimpleStringProperty("-");
    private final StringProperty statusText = new SimpleStringProperty();
    private final StringProperty lockText = new SimpleStringProperty("");
    private final StringProperty actionText = new SimpleStringProperty("Abrir");
    private final BooleanProperty blocked = new SimpleBooleanProperty(false);
    private final BooleanProperty pendingWarn = new SimpleBooleanProperty(false);
    private final BooleanProperty billWarn = new SimpleBooleanProperty(false);
    private final ObjectProperty<TableStatus> status = new SimpleObjectProperty<>(TableStatus.FREE);

    public IntegerProperty tableIdProperty() { return tableId; }
    public LongProperty orderIdProperty() { return orderId; }
    public StringProperty titleProperty() { return title; }
    public StringProperty totalTextProperty() { return totalText; }
    public StringProperty elapsedTextProperty() { return elapsedText; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty lockTextProperty() { return lockText; }
    public StringProperty actionTextProperty() { return actionText; }
    public BooleanProperty blockedProperty() { return blocked; }
    public BooleanProperty pendingWarnProperty() { return pendingWarn; }
    public BooleanProperty billWarnProperty() { return billWarn; }
    public ObjectProperty<TableStatus> statusProperty() { return status; }

    public int getTableId() { return tableId.get(); }
    public long getOrderId() { return orderId.get(); }
}

