package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.*;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.LockService;
import com.tpv.desktop.tpv.services.OrderService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OrderViewModel {
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final LockService lockService;

    private final LongProperty orderId = new SimpleLongProperty();
    private final IntegerProperty tableId = new SimpleIntegerProperty();
    private final IntegerProperty people = new SimpleIntegerProperty(4);
    private final StringProperty elapsed = new SimpleStringProperty("0 min");
    private final StringProperty subtotalText = new SimpleStringProperty("0.00 EUR");
    private final StringProperty feedback = new SimpleStringProperty("");

    private Instant openedAt;
    private final ObservableList<Category> categories = FXCollections.observableArrayList();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final ObservableList<OrderLine> lines = FXCollections.observableArrayList();

    public OrderViewModel() {
        AppContext ctx = AppContext.get();
        catalogService = ctx.catalogService();
        orderService = ctx.orderService();
        lockService = ctx.lockService();
    }

    public void bindOrder(long orderId, int tableId) {
        this.orderId.set(orderId);
        this.tableId.set(tableId);
        categories.setAll(catalogService.categories());
        if (!categories.isEmpty()) {
            loadProducts(categories.getFirst());
        }
        refreshOrder();
    }

    public void refreshOrder() {
        Order order = orderService.getById(orderId.get());
        this.people.set(order.getPeople());
        this.openedAt = order.getOpenedAt();
        lines.setAll(order.getLines());
        subtotalText.set(money(order.totalCents()));
        long minutes = Math.max(0, Duration.between(openedAt, Instant.now()).toMinutes());
        elapsed.set(minutes + " min");
    }

    public void loadProducts(Category category) {
        products.setAll(catalogService.productsByCategory(category.id()));
    }

    public void addProduct(Product product) {
        orderService.addProduct(orderId.get(), product.id());
        refreshOrder();
    }

    public void sendAll(boolean separateByDestination) {
        snapshotLastSend(EnumSet.allOf(Destination.class), separateByDestination);
        orderService.send(orderId.get(), EnumSet.allOf(Destination.class), true);
        refreshOrder();
        feedback.set("Comanda enviada.");
    }

    public void sendDestinations(Set<Destination> destinations, boolean separateByDestination) {
        snapshotLastSend(destinations, separateByDestination);
        orderService.send(orderId.get(), destinations, true);
        refreshOrder();
        feedback.set("Comanda enviada a " + destinations);
    }

    public Map<Destination, Integer> pendingByDestination() {
        return orderService.pendingByDestination(orderId.get());
    }

    public int pendingPaymentCents() {
        return orderService.pendingPaymentCents(orderId.get());
    }

    public boolean payFull(String method) {
        int pending = pendingPaymentCents();
        if (pending <= 0) {
            feedback.set("No hay importe pendiente.");
            return false;
        }
        return payPartial(method, pending);
    }

    public boolean payPartial(String method, int amountCents) {
        int pending = pendingPaymentCents();
        if (pending <= 0) {
            feedback.set("No hay importe pendiente.");
            return false;
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("El importe debe ser mayor que cero.");
        }
        if (amountCents > pending) {
            throw new IllegalArgumentException("El importe supera el pendiente (" + money(pending) + ").");
        }

        orderService.addPayment(orderId.get(), method, amountCents);
        if (amountCents >= pending) {
            lockService.unlock(tableId.get());
            feedback.set("Cobro registrado (" + method + ").");
            return true;
        }

        refreshOrder();
        feedback.set("Cobro parcial registrado (" + money(amountCents) + "). Pendiente: " + money(pending - amountCents));
        return false;
    }

    public void requestBill() {
        orderService.setBillRequested(orderId.get(), true);
        refreshOrder();
        feedback.set("Cuenta solicitada.");
    }

    public void applyDiscountPercent(int percent) {
        orderService.applyDiscountPercent(orderId.get(), percent);
        refreshOrder();
        feedback.set("Descuento aplicado: " + percent + "%.");
    }

    public void applyDiscountAmount(int amountCents) {
        orderService.applyDiscountAmount(orderId.get(), amountCents);
        refreshOrder();
        feedback.set("Descuento aplicado: " + money(amountCents) + ".");
    }

    public void clearDiscount() {
        orderService.clearDiscount(orderId.get());
        refreshOrder();
        feedback.set("Descuento eliminado.");
    }

    public void moveToTable(int newTable) {
        int oldTable = tableId.get();
        orderService.moveOrder(orderId.get(), newTable);
        tableId.set(newTable);
        try {
            lockService.unlock(oldTable);
        } catch (Exception ignored) {
        }
        lockService.lock(newTable);
        refreshOrder();
        feedback.set("Mesa movida a " + newTable + ".");
    }

    public void addNoteToLastPending(String note) {
        orderService.setLastLineNote(orderId.get(), note);
        refreshOrder();
    }

    public void removeLastPending() {
        orderService.removeLastPendingLine(orderId.get());
        refreshOrder();
    }

    public void cancelOrder() {
        orderService.cancelOrder(orderId.get());
        lockService.unlock(tableId.get());
    }

    public void closeOrReleaseOnBack() {
        try {
            Order latest = orderService.getById(orderId.get());
            if (latest.getLines() == null || latest.getLines().isEmpty()) {
                cancelOrder();
                feedback.set("Ticket vacio cancelado. Mesa liberada.");
                return;
            }
        } catch (Exception ignored) {
            // If ticket no longer exists/available, fall back to lock release.
        }
        releaseLock();
    }

    public void releaseLock() {
        lockService.unlock(tableId.get());
    }

    public void heartbeatLock() {
        lockService.heartbeat(tableId.get());
    }

    public LongProperty orderIdProperty() { return orderId; }
    public IntegerProperty tableIdProperty() { return tableId; }
    public IntegerProperty peopleProperty() { return people; }
    public StringProperty elapsedProperty() { return elapsed; }
    public StringProperty subtotalTextProperty() { return subtotalText; }
    public StringProperty feedbackProperty() { return feedback; }
    public ObservableList<Category> categories() { return categories; }
    public ObservableList<Product> products() { return products; }
    public ObservableList<OrderLine> lines() { return lines; }

    private void snapshotLastSend(Set<Destination> destinations, boolean separateByDestination) {
        List<OrderLine> pendingLines = lines.stream()
                .filter(line -> line.getPendingQty() > 0)
                .filter(line -> isDestinationIncluded(line.getDestination(), destinations))
                .toList();

        StringBuilder out = new StringBuilder();
        out.append("RESTAURANTE EL GUSTO").append('\n');
        out.append("ULTIMA COMANDA ENVIADA").append('\n');
        out.append("Mesa ").append(tableId.get()).append("  Ticket ").append(orderId.get()).append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append("--------------------------------------------").append('\n');
        if (pendingLines.isEmpty()) {
            out.append("Sin lineas pendientes para enviar").append('\n');
            out.append("--------------------------------------------").append('\n');
            AppContext.get().appState().lastComandaPrintTextProperty().set(out.toString());
            return;
        }

        if (separateByDestination) {
            appendDestinationDetail(out, Destination.BAR, pendingLines);
            appendDestinationDetail(out, Destination.COCINA, pendingLines);
            appendDestinationDetail(out, Destination.POSTRES, pendingLines);
        } else {
            int totalQty = pendingLines.stream().mapToInt(OrderLine::getPendingQty).sum();
            out.append("COMANDA UNIFICADA  ").append(totalQty).append(" productos").append('\n');
            for (OrderLine line : pendingLines) {
                int qty = line.getPendingQty();
                int total = qty * line.getUnitPriceCents();
                out.append(String.format(Locale.US, "%2dx %-24s %8.2f", qty, clip(line.getProductName(), 24), total / 100.0)).append('\n');
                if (line.getNote() != null && !line.getNote().isBlank()) {
                    out.append("   - ").append(line.getNote()).append('\n');
                }
            }
        }

        out.append("--------------------------------------------").append('\n');
        AppContext.get().appState().lastComandaPrintTextProperty().set(out.toString());
    }

    private static boolean isDestinationIncluded(Destination destination, Set<Destination> selected) {
        boolean all = selected == null || selected.isEmpty() || selected.containsAll(EnumSet.allOf(Destination.class));
        if (all) {
            return true;
        }
        return selected.contains(destination);
    }

    private static void appendDestinationDetail(StringBuilder out, Destination destination, List<OrderLine> pendingLines) {
        List<OrderLine> linesByDest = pendingLines.stream()
                .filter(line -> line.getDestination() == destination)
                .toList();
        if (linesByDest.isEmpty()) {
            return;
        }
        int qty = linesByDest.stream().mapToInt(OrderLine::getPendingQty).sum();
        out.append(destination.name()).append("  ").append(qty).append(" productos").append('\n');
        for (OrderLine line : linesByDest) {
            int pendingQty = line.getPendingQty();
            int total = pendingQty * line.getUnitPriceCents();
            out.append(String.format(Locale.US, "%2dx %-24s %8.2f", pendingQty, clip(line.getProductName(), 24), total / 100.0)).append('\n');
            if (line.getNote() != null && !line.getNote().isBlank()) {
                out.append("   - ").append(line.getNote()).append('\n');
            }
        }
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }

    private static String money(int cents) {
        return String.format(Locale.US, "%.2f EUR", cents / 100.0);
    }
}

