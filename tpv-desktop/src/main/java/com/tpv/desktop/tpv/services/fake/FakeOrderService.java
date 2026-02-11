package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.domain.model.*;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.OrderService;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class FakeOrderService implements OrderService {
    private final FakeDataStore store;
    private final CatalogService catalogService;
    private final Map<Long, Integer> paidCentsByOrder = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Integer> discountCentsByOrder = new java.util.concurrent.ConcurrentHashMap<>();

    public FakeOrderService(FakeDataStore store, CatalogService catalogService) {
        this.store = store;
        this.catalogService = catalogService;
    }

    @Override
    public Order openOrGetByTable(int tableId) {
        Order existing = store.openOrdersByTable.get(tableId);
        if (existing != null) return existing;
        Order created = new Order(store.orderSeq.incrementAndGet(), tableId, 4, Instant.now());
        store.openOrdersByTable.put(tableId, created);
        store.ordersById.put(created.getId(), created);
        return created;
    }

    @Override
    public Order getById(long orderId) {
        Order order = store.ordersById.get(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);
        return order;
    }

    @Override
    public Order addProduct(long orderId, long productId) {
        Order order = getById(orderId);
        Product p = catalogService.productById(productId);
        OrderLine existingPending = order.getLines().stream()
                .filter(l -> l.getProductId() == productId && l.getPendingQty() > 0)
                .findFirst()
                .orElse(null);
        if (existingPending != null) {
            existingPending.addQty(1);
        } else {
            order.getLines().add(new OrderLine(store.lineSeq.incrementAndGet(), p, 1));
        }
        return order;
    }

    @Override
    public void removeLastPendingLine(long orderId) {
        Order order = getById(orderId);
        for (int i = order.getLines().size() - 1; i >= 0; i--) {
            OrderLine line = order.getLines().get(i);
            if (line.getPendingQty() > 0) {
                if (line.getQty() > 1) {
                    line.addQty(-1);
                } else {
                    order.getLines().remove(i);
                }
                return;
            }
        }
    }

    @Override
    public void setLastLineNote(long orderId, String note) {
        Order order = getById(orderId);
        for (int i = order.getLines().size() - 1; i >= 0; i--) {
            OrderLine line = order.getLines().get(i);
            if (line.getPendingQty() > 0) {
                line.setNote(note);
                return;
            }
        }
    }

    @Override
    public Map<Destination, Integer> pendingByDestination(long orderId) {
        Order order = getById(orderId);
        Map<Destination, Integer> map = new EnumMap<>(Destination.class);
        for (Destination d : Destination.values()) map.put(d, 0);
        order.getLines().forEach(l -> map.computeIfPresent(l.getDestination(), (k, v) -> v + l.getPendingQty()));
        return map;
    }

    @Override
    public int pendingPaymentCents(long orderId) {
        Order order = getById(orderId);
        int paid = paidCentsByOrder.getOrDefault(orderId, 0);
        int discount = discountCentsByOrder.getOrDefault(orderId, 0);
        int total = Math.max(0, order.totalCents() - Math.max(0, Math.min(discount, order.totalCents())));
        return Math.max(0, total - paid);
    }

    @Override
    public void addPayment(long orderId, String method, int amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Importe de cobro invalido");
        }
        Order order = getById(orderId);
        int paid = paidCentsByOrder.getOrDefault(orderId, 0) + amountCents;
        int discount = discountCentsByOrder.getOrDefault(orderId, 0);
        int total = Math.max(0, order.totalCents() - Math.max(0, Math.min(discount, order.totalCents())));
        if (paid >= total) {
            paidCentsByOrder.remove(orderId);
            discountCentsByOrder.remove(orderId);
            store.openOrdersByTable.remove(order.getTableId());
            store.ordersById.remove(orderId);
        } else {
            paidCentsByOrder.put(orderId, paid);
        }
    }

    @Override
    public void send(long orderId, Set<Destination> destinations, boolean deltaOnly) {
        Order order = getById(orderId);
        order.getLines().forEach(line -> {
            if (destinations.contains(line.getDestination())) {
                if (deltaOnly) {
                    line.markSentAll();
                } else {
                    line.markSentAll();
                }
            }
        });
    }

    @Override
    public void setBillRequested(long orderId, boolean value) {
        getById(orderId).setBillRequested(value);
    }

    @Override
    public void applyDiscountPercent(long orderId, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Porcentaje fuera de rango.");
        }
        Order order = getById(orderId);
        int discount = (order.totalCents() * percent) / 100;
        discountCentsByOrder.put(orderId, discount);
    }

    @Override
    public void applyDiscountAmount(long orderId, int amountCents) {
        if (amountCents < 0) {
            throw new IllegalArgumentException("Importe de descuento invalido.");
        }
        Order order = getById(orderId);
        if (amountCents > order.totalCents()) {
            throw new IllegalArgumentException("Descuento supera el total.");
        }
        discountCentsByOrder.put(orderId, amountCents);
    }

    @Override
    public void clearDiscount(long orderId) {
        discountCentsByOrder.remove(orderId);
    }

    @Override
    public void cancelOrder(long orderId) {
        Order order = getById(orderId);
        paidCentsByOrder.remove(orderId);
        discountCentsByOrder.remove(orderId);
        store.openOrdersByTable.remove(order.getTableId());
        store.ordersById.remove(order.getId());
    }

    @Override
    public void moveOrder(long orderId, int newTableId) {
        Order order = getById(orderId);
        if (store.openOrdersByTable.containsKey(newTableId)) {
            throw new IllegalStateException("La mesa destino ya está ocupada");
        }
        store.openOrdersByTable.remove(order.getTableId());
        Order moved = new Order(order.getId(), newTableId, order.getPeople(), order.getOpenedAt());
        moved.getLines().setAll(order.getLines());
        moved.setBillRequested(order.isBillRequested());
        store.openOrdersByTable.put(newTableId, moved);
        store.ordersById.put(moved.getId(), moved);
        if (discountCentsByOrder.containsKey(orderId)) {
            discountCentsByOrder.put(orderId, discountCentsByOrder.get(orderId));
        }
    }
}

