package com.tpv.desktop.tpv.domain.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class OrderLine {
    private final LongProperty id = new SimpleLongProperty();
    private final LongProperty productId = new SimpleLongProperty();
    private final StringProperty productName = new SimpleStringProperty("");
    private final IntegerProperty qty = new SimpleIntegerProperty();
    private final IntegerProperty sentQty = new SimpleIntegerProperty();
    private final IntegerProperty unitPriceCents = new SimpleIntegerProperty();
    private final StringProperty note = new SimpleStringProperty("");
    private Destination destination;

    public OrderLine(long id, Product product, int qty) {
        this.id.set(id);
        this.productId.set(product.id());
        this.productName.set(product.name());
        this.qty.set(qty);
        this.sentQty.set(0);
        this.unitPriceCents.set(product.priceCents());
        this.destination = product.destination();
    }

    public long getId() { return id.get(); }
    public LongProperty idProperty() { return id; }
    public long getProductId() { return productId.get(); }
    public String getProductName() { return productName.get(); }
    public StringProperty productNameProperty() { return productName; }
    public int getQty() { return qty.get(); }
    public IntegerProperty qtyProperty() { return qty; }
    public int getSentQty() { return sentQty.get(); }
    public IntegerProperty sentQtyProperty() { return sentQty; }
    public int getUnitPriceCents() { return unitPriceCents.get(); }
    public IntegerProperty unitPriceCentsProperty() { return unitPriceCents; }
    public String getNote() { return note.get(); }
    public StringProperty noteProperty() { return note; }
    public Destination getDestination() { return destination; }

    public void addQty(int delta) {
        qty.set(qty.get() + delta);
    }

    public int getPendingQty() {
        return Math.max(0, getQty() - getSentQty());
    }

    public int lineTotalCents() {
        return getQty() * getUnitPriceCents();
    }

    public void markSentAll() {
        sentQty.set(getQty());
    }

    public void setNote(String value) {
        note.set(value == null ? "" : value.trim());
    }
}

