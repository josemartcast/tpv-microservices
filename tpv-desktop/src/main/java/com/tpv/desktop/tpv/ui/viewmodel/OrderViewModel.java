package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.*;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.LockException;
import com.tpv.desktop.tpv.services.LockService;
import com.tpv.desktop.tpv.services.OrderService;
import com.tpv.desktop.tpv.services.PrintQueueService;
import com.tpv.desktop.tpv.services.TableService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OrderViewModel {
    private static final int THERMAL_WIDTH = 42;
    private static final String THERMAL_SEPARATOR = "-".repeat(THERMAL_WIDTH);

    private final CatalogService catalogService;
    private final OrderService orderService;
    private final LockService lockService;
    private final PrintQueueService printQueueService;
    private final TableService tableService;
    private final AppState appState;

    private final LongProperty orderId = new SimpleLongProperty();
    private final IntegerProperty tableId = new SimpleIntegerProperty();
    private final StringProperty tableLabel = new SimpleStringProperty("Mesa");
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
        printQueueService = ctx.printQueueService();
        tableService = ctx.tableService();
        appState = ctx.appState();
    }

    OrderViewModel(
            CatalogService catalogService,
            OrderService orderService,
            LockService lockService,
            PrintQueueService printQueueService,
            AppState appState
    ) {
        this(catalogService, orderService, lockService, printQueueService, null, appState);
    }

    OrderViewModel(
            CatalogService catalogService,
            OrderService orderService,
            LockService lockService,
            PrintQueueService printQueueService,
            TableService tableService,
            AppState appState
    ) {
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.lockService = lockService;
        this.printQueueService = printQueueService;
        this.tableService = tableService;
        this.appState = appState;
    }

    public void bindOrder(long orderId, int tableId) {
        bindOrder(orderId, tableId, null);
    }

    public void bindOrder(long orderId, int tableId, String initialTableLabel) {
        this.orderId.set(orderId);
        this.tableId.set(tableId);
        this.tableLabel.set(normalizeTableLabel(initialTableLabel, tableId));
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
        refreshTableLabel();
    }

    public void loadProducts(Category category) {
        products.setAll(catalogService.productsByCategory(category.id()));
    }

    public void addProduct(Product product) {
        orderService.addProduct(orderId.get(), product.id());
        refreshOrder();
    }

    public boolean sendAll(boolean separateByDestination) {
        Set<Destination> selected = EnumSet.allOf(Destination.class);
        if (!hasPendingFor(selected)) {
            feedback.set("No hay lineas pendientes para enviar.");
            return false;
        }
        PrintBatch batch = snapshotLastSend(selected, separateByDestination);
        orderService.send(orderId.get(), selected, true);
        enqueuePrintJobs(batch.printJobsByDestination());
        refreshOrder();
        feedback.set("Comanda enviada.");
        return true;
    }

    public boolean sendDestinations(Set<Destination> destinations, boolean separateByDestination) {
        if (!hasPendingFor(destinations)) {
            feedback.set("No hay lineas pendientes para enviar.");
            return false;
        }
        PrintBatch batch = snapshotLastSend(destinations, separateByDestination);
        orderService.send(orderId.get(), destinations, true);
        enqueuePrintJobs(batch.printJobsByDestination());
        refreshOrder();
        feedback.set("Comanda enviada a " + destinations);
        return true;
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
            String unlockWarning = unlockWithPolicy();
            if (unlockWarning == null) {
                feedback.set("Cobro registrado (" + method + ").");
            } else {
                feedback.set("Cobro registrado (" + method + "). " + unlockWarning);
            }
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
            unlockWithPolicy(oldTable);
        } catch (RuntimeException ignored) {
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
        unlockWithPolicy();
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
        String warning = unlockWithPolicy();
        if (warning != null) {
            feedback.set(warning);
        }
    }

    public void heartbeatLock() {
        try {
            lockService.heartbeat(tableId.get());
        } catch (LockException ex) {
            if (ex.isRecoverableWithReacquire()) {
                lockService.lock(tableId.get());
                feedback.set("Bloqueo recuperado.");
                return;
            }
            throw ex;
        }
    }

    public LongProperty orderIdProperty() { return orderId; }
    public IntegerProperty tableIdProperty() { return tableId; }
    public StringProperty tableLabelProperty() { return tableLabel; }
    public IntegerProperty peopleProperty() { return people; }
    public StringProperty elapsedProperty() { return elapsed; }
    public StringProperty subtotalTextProperty() { return subtotalText; }
    public StringProperty feedbackProperty() { return feedback; }
    public ObservableList<Category> categories() { return categories; }
    public ObservableList<Product> products() { return products; }
    public ObservableList<OrderLine> lines() { return lines; }

    private boolean hasPendingFor(Set<Destination> destinations) {
        return lines.stream()
                .anyMatch(line -> line.getPendingQty() > 0 && isDestinationIncluded(line.getDestination(), destinations));
    }

    private PrintBatch snapshotLastSend(Set<Destination> destinations, boolean separateByDestination) {
        List<OrderLine> pendingLines = lines.stream()
                .filter(line -> line.getPendingQty() > 0)
                .filter(line -> isDestinationIncluded(line.getDestination(), destinations))
                .toList();
        LinkedHashMap<String, String> printJobsByDestination = new LinkedHashMap<>();
        String restaurantName = restaurantNameForPrint();

        StringBuilder out = new StringBuilder();
        out.append(restaurantName).append('\n');
        out.append("ULTIMA COMANDA ENVIADA").append('\n');
        out.append(tableLabelForPrint()).append("  Ticket ").append(orderId.get()).append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append(THERMAL_SEPARATOR).append('\n');
        if (pendingLines.isEmpty()) {
            out.append("Sin lineas pendientes para enviar").append('\n');
            out.append(THERMAL_SEPARATOR).append('\n');
            appState.lastComandaPrintTextProperty().set(out.toString());
            return new PrintBatch(out.toString(), printJobsByDestination);
        }

        if (separateByDestination) {
            appendDestinationDetail(out, Destination.BAR, pendingLines);
            appendDestinationDetail(out, Destination.COCINA, pendingLines);
            appendDestinationDetail(out, Destination.POSTRES, pendingLines);
            collectDestinationPrintJob(printJobsByDestination, Destination.BAR, pendingLines);
            collectDestinationPrintJob(printJobsByDestination, Destination.COCINA, pendingLines);
            collectDestinationPrintJob(printJobsByDestination, Destination.POSTRES, pendingLines);
        } else {
            int totalQty = pendingLines.stream().mapToInt(OrderLine::getPendingQty).sum();
            out.append("COMANDA UNIFICADA  ").append(totalQty).append(" productos").append('\n');
            out.append(THERMAL_SEPARATOR).append('\n');
            for (OrderLine line : pendingLines) {
                appendLineWithWrap(out, line.getPendingQty(), line.getProductName());
                appendNoteWithWrap(out, line.getNote());
            }
            printJobsByDestination.put("ALL", buildUnifiedPrintText(pendingLines, totalQty));
        }

        out.append(THERMAL_SEPARATOR).append('\n');
        appState.lastComandaPrintTextProperty().set(out.toString());
        return new PrintBatch(out.toString(), printJobsByDestination);
    }

    private static boolean isDestinationIncluded(Destination destination, Set<Destination> selected) {
        boolean all = selected == null || selected.isEmpty() || selected.containsAll(EnumSet.allOf(Destination.class));
        if (all) {
            return true;
        }
        return selected.contains(destination);
    }

    private static List<OrderLine> pendingByDestination(Destination destination, List<OrderLine> pendingLines) {
        return pendingLines.stream()
                .filter(line -> line.getDestination() == destination)
                .toList();
    }

    private static void appendDestinationDetail(StringBuilder out, Destination destination, List<OrderLine> pendingLines) {
        List<OrderLine> linesByDest = pendingByDestination(destination, pendingLines);
        if (linesByDest.isEmpty()) {
            return;
        }
        int qty = linesByDest.stream().mapToInt(OrderLine::getPendingQty).sum();
        out.append(destination.name()).append("  ").append(qty).append(" productos").append('\n');
        out.append(THERMAL_SEPARATOR).append('\n');
        for (OrderLine line : linesByDest) {
            appendLineWithWrap(out, line.getPendingQty(), line.getProductName());
            appendNoteWithWrap(out, line.getNote());
        }
        out.append('\n');
    }

    private void collectDestinationPrintJob(Map<String, String> out, Destination destination, List<OrderLine> pendingLines) {
        List<OrderLine> linesByDest = pendingByDestination(destination, pendingLines);
        if (linesByDest.isEmpty()) {
            return;
        }
        int qty = linesByDest.stream().mapToInt(OrderLine::getPendingQty).sum();
        out.put(destination.name(), buildDestinationPrintText(destination, linesByDest, qty));
    }

    private String buildUnifiedPrintText(List<OrderLine> pendingLines, int totalQty) {
        StringBuilder out = new StringBuilder();
        appendPrintHeader(out);
        out.append("COMANDA UNIFICADA  ").append(totalQty).append(" productos").append('\n');
        out.append(THERMAL_SEPARATOR).append('\n');
        for (OrderLine line : pendingLines) {
            appendLineWithWrap(out, line.getPendingQty(), line.getProductName());
            appendNoteWithWrap(out, line.getNote());
        }
        out.append(THERMAL_SEPARATOR).append('\n');
        return out.toString();
    }

    private String buildDestinationPrintText(Destination destination, List<OrderLine> linesByDest, int qty) {
        StringBuilder out = new StringBuilder();
        appendPrintHeader(out);
        out.append(destination.name()).append("  ").append(qty).append(" productos").append('\n');
        out.append(THERMAL_SEPARATOR).append('\n');
        for (OrderLine line : linesByDest) {
            appendLineWithWrap(out, line.getPendingQty(), line.getProductName());
            appendNoteWithWrap(out, line.getNote());
        }
        out.append(THERMAL_SEPARATOR).append('\n');
        return out.toString();
    }

    private void appendPrintHeader(StringBuilder out) {
        out.append(restaurantNameForPrint()).append('\n');
        out.append("ULTIMA COMANDA ENVIADA").append('\n');
        out.append(tableLabelForPrint()).append("  Ticket ").append(orderId.get()).append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
    }

    private void enqueuePrintJobs(Map<String, String> printJobsByDestination) {
        for (Map.Entry<String, String> entry : printJobsByDestination.entrySet()) {
            printQueueService.enqueue(entry.getKey(), entry.getValue());
        }
    }

    private static void appendLineWithWrap(StringBuilder out, int qty, String productName) {
        String name = productName == null ? "-" : productName.trim();
        String prefix = qty + "x ";
        int firstLineWidth = Math.max(8, THERMAL_WIDTH - prefix.length());
        List<String> wrapped = wrapByWords(name, firstLineWidth);
        if (wrapped.isEmpty()) {
            out.append(prefix).append('-').append('\n');
            return;
        }
        out.append(prefix).append(wrapped.getFirst()).append('\n');

        String indent = " ".repeat(prefix.length());
        int nextWidth = Math.max(8, THERMAL_WIDTH - indent.length());
        for (int i = 1; i < wrapped.size(); i++) {
            List<String> extraWrapped = wrapByWords(wrapped.get(i), nextWidth);
            if (extraWrapped.isEmpty()) {
                continue;
            }
            for (String piece : extraWrapped) {
                out.append(indent).append(piece).append('\n');
            }
        }
    }

    private static void appendNoteWithWrap(StringBuilder out, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        String normalized = note.trim();
        String prefix = "   - ";
        int width = Math.max(8, THERMAL_WIDTH - prefix.length());
        List<String> wrapped = wrapByWords(normalized, width);
        if (wrapped.isEmpty()) {
            return;
        }
        out.append(prefix).append(wrapped.getFirst()).append('\n');
        String indent = " ".repeat(prefix.length());
        for (int i = 1; i < wrapped.size(); i++) {
            out.append(indent).append(wrapped.get(i)).append('\n');
        }
    }

    private static List<String> wrapByWords(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.length() > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                int index = 0;
                while (index < word.length()) {
                    int end = Math.min(index + maxWidth, word.length());
                    lines.add(word.substring(index, end));
                    index = end;
                }
                continue;
            }

            if (current.isEmpty()) {
                current.append(word);
                continue;
            }

            if (current.length() + 1 + word.length() <= maxWidth) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String money(int cents) {
        return String.format(Locale.US, "%.2f EUR", cents / 100.0);
    }

    private String unlockWithPolicy() {
        return unlockWithPolicy(tableId.get());
    }

    private String unlockWithPolicy(int targetTableId) {
        try {
            lockService.unlock(targetTableId);
            return null;
        } catch (LockException ex) {
            if (ex.isOwnershipConflict() || ex.isRecoverableWithReacquire()) {
                return null;
            }
            if (ex.isAuthIssue()) {
                return "No se pudo liberar lock (sesion expirada). Se limpiara por TTL.";
            }
            return "No se pudo liberar lock. Se limpiara por TTL.";
        } catch (RuntimeException ex) {
            return "No se pudo liberar lock. Se limpiara por TTL.";
        }
    }

    private String restaurantNameForPrint() {
        String value = appState.restaurantNameProperty().get();
        if (value == null || value.isBlank()) {
            return "RESTAURANTE";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String tableLabelForPrint() {
        String value = tableLabel.get();
        if (value == null || value.isBlank()) {
            return "Mesa " + tableId.get();
        }
        return value;
    }

    private void refreshTableLabel() {
        if (tableService == null) {
            if (tableLabel.get() == null || tableLabel.get().isBlank()) {
                tableLabel.set("Mesa " + tableId.get());
            }
            return;
        }

        try {
            String resolved = tableService.tables().stream()
                    .filter(t -> t.tableId() == tableId.get())
                    .map(TableSnapshot::label)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse(null);
            if (resolved != null) {
                tableLabel.set(resolved.trim());
                return;
            }
        } catch (Exception ignored) {
            // Keep current label when tables sync is temporarily unavailable.
        }

        if (tableLabel.get() == null || tableLabel.get().isBlank()) {
            tableLabel.set("Mesa " + tableId.get());
        }
    }

    private static String normalizeTableLabel(String value, int tableId) {
        if (value == null || value.isBlank()) {
            return "Mesa " + tableId;
        }
        return value.trim();
    }

    private record PrintBatch(String lastComandaText, Map<String, String> printJobsByDestination) {}
}

