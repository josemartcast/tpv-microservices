package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Order;

import java.util.Map;

public interface OrderService {
    Order openOrGetByTable(int tableId);
    Order getById(long orderId);
    Order addProduct(long orderId, long productId);
    Order addProduct(long orderId, long productId, int qty);
    Order addCombinedProduct(long orderId, long baseProductId, long mixerProductId, int qty);
    Order updateLineQty(long orderId, long lineId, int qty);
    Order updateLinePrice(long orderId, long lineId, int priceCents);
    Order consumeLineForPayment(long orderId, long lineId, int qty);
    void removeLine(long orderId, long lineId);
    void removeLastPendingLine(long orderId);
    void setLastLineNote(long orderId, String note);
    Map<Destination, Integer> pendingByDestination(long orderId);
    int pendingPaymentCents(long orderId);
    void addPayment(long orderId, String method, int amountCents);
    void send(long orderId, java.util.Set<Destination> destinations, boolean deltaOnly);
    void setBillRequested(long orderId, boolean value);
    void applyDiscountPercent(long orderId, int percent);
    void applyDiscountAmount(long orderId, int amountCents);
    void clearDiscount(long orderId);
    void cancelOrder(long orderId);
    void moveOrder(long orderId, int newTableId);
}

