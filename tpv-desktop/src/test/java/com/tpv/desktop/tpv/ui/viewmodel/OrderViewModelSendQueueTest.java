package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Order;
import com.tpv.desktop.tpv.domain.model.OrderLine;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.LockService;
import com.tpv.desktop.tpv.services.OrderService;
import com.tpv.desktop.tpv.services.PrintQueueService;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderViewModelSendQueueTest {

    @Test
    void sendAll_separateByDestination_enqueuesOneJobPerPendingDestination() {
        Order order = buildOrder();
        FakeOrderService orderService = new FakeOrderService(order);
        CapturingPrintQueueService queue = new CapturingPrintQueueService();
        AppState appState = new AppState();
        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orderService,
                new NoopLockService(),
                queue,
                appState
        );
        vm.bindOrder(order.getId(), order.getTableId());

        boolean sent = vm.sendAll(true);

        assertTrue(sent);
        assertEquals(Set.of(Destination.BAR, Destination.COCINA, Destination.POSTRES), orderService.lastSentDestinations);
        assertEquals(1, orderService.sendCalls);
        assertEquals(List.of("BAR", "COCINA"), queue.destinations());
        assertTrue(queue.payloads().get(0).contains("BAR"));
        assertTrue(queue.payloads().get(1).contains("COCINA"));
        assertTrue(appState.lastComandaPrintTextProperty().get().contains("ULTIMA COMANDA ENVIADA"));
    }

    @Test
    void sendDestinations_barOnly_enqueuesOnlyBar() {
        Order order = buildOrder();
        FakeOrderService orderService = new FakeOrderService(order);
        CapturingPrintQueueService queue = new CapturingPrintQueueService();
        AppState appState = new AppState();
        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orderService,
                new NoopLockService(),
                queue,
                appState
        );
        vm.bindOrder(order.getId(), order.getTableId());

        boolean sent = vm.sendDestinations(Set.of(Destination.BAR), true);

        assertTrue(sent);
        assertEquals(Set.of(Destination.BAR), orderService.lastSentDestinations);
        assertEquals(1, orderService.sendCalls);
        assertEquals(List.of("BAR"), queue.destinations());
        assertTrue(queue.payloads().getFirst().contains("BAR"));
        assertFalse(queue.payloads().getFirst().contains("COCINA"));
    }

    @Test
    void sendAll_unified_enqueuesSingleAllJob() {
        Order order = buildOrder();
        FakeOrderService orderService = new FakeOrderService(order);
        CapturingPrintQueueService queue = new CapturingPrintQueueService();
        AppState appState = new AppState();
        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orderService,
                new NoopLockService(),
                queue,
                appState
        );
        vm.bindOrder(order.getId(), order.getTableId());

        boolean sent = vm.sendAll(false);

        assertTrue(sent);
        assertEquals(1, orderService.sendCalls);
        assertEquals(List.of("ALL"), queue.destinations());
        assertTrue(queue.payloads().getFirst().contains("COMANDA UNIFICADA"));
    }

    @Test
    void sendAll_separateByDestination_containsLineDetailAndNoPrices() {
        Order order = buildOrder();
        FakeOrderService orderService = new FakeOrderService(order);
        CapturingPrintQueueService queue = new CapturingPrintQueueService();
        AppState appState = new AppState();
        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orderService,
                new NoopLockService(),
                queue,
                appState
        );
        vm.bindOrder(order.getId(), order.getTableId());

        boolean sent = vm.sendAll(true);

        assertTrue(sent);
        assertEquals(2, queue.payloads().size());
        String bar = queue.payloads().get(0);
        String cocina = queue.payloads().get(1);

        assertTrue(bar.contains("2x Cerveza"));
        assertTrue(bar.contains("sin alcohol"));
        assertFalse(bar.contains("EUR"));
        assertFalse(bar.contains("Calamares"));

        assertTrue(cocina.contains("1x Calamares"));
        assertFalse(cocina.contains("EUR"));
        assertFalse(cocina.contains("Cerveza"));
    }

    @Test
    void sendAll_withoutPending_doesNotCallSendNorQueue() {
        Order order = buildOrder();
        order.getLines().forEach(OrderLine::markSentAll);
        FakeOrderService orderService = new FakeOrderService(order);
        CapturingPrintQueueService queue = new CapturingPrintQueueService();
        AppState appState = new AppState();
        appState.lastComandaPrintTextProperty().set("PREV");
        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orderService,
                new NoopLockService(),
                queue,
                appState
        );
        vm.bindOrder(order.getId(), order.getTableId());

        boolean sent = vm.sendAll(true);

        assertFalse(sent);
        assertEquals(0, orderService.sendCalls);
        assertTrue(queue.payloads().isEmpty());
        assertEquals("No hay lineas pendientes para enviar.", vm.feedbackProperty().get());
        assertEquals("PREV", appState.lastComandaPrintTextProperty().get());
    }

    private static Order buildOrder() {
        Product bar = new Product(1, 1, "Cerveza", 300, Destination.BAR, "prod-dark");
        Product cocina = new Product(2, 1, "Calamares", 900, Destination.COCINA, "prod-orange");
        Product postre = new Product(3, 1, "Tarta", 550, Destination.POSTRES, "prod-green");

        Order order = new Order(1001, 9, 4, Instant.now());

        OrderLine l1 = new OrderLine(10, bar, 2);
        l1.setNote("sin alcohol");
        order.getLines().add(l1);

        OrderLine l2 = new OrderLine(11, cocina, 1);
        order.getLines().add(l2);

        // Sent previously, should not be included in pending queue.
        OrderLine l3 = new OrderLine(12, postre, 1);
        l3.markSentAll();
        order.getLines().add(l3);
        return order;
    }

    private static final class StaticCatalogService implements CatalogService {
        @Override
        public List<Category> categories() {
            return List.of(new Category(1, "Entrantes"));
        }

        @Override
        public List<Product> productsByCategory(long categoryId) {
            return List.of();
        }

        @Override
        public Product productById(long productId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoopLockService implements LockService {
        @Override
        public TableLock lock(int tableId) {
            return null;
        }

        @Override
        public void unlock(int tableId) {
            // no-op
        }

        @Override
        public TableLock heartbeat(int tableId) {
            return null;
        }

        @Override
        public TableLock activeLock(int tableId) {
            return null;
        }
    }

    private static final class CapturingPrintQueueService implements PrintQueueService {
        private final ObjectProperty<com.tpv.desktop.tpv.domain.model.PrintQueueState> state =
                new SimpleObjectProperty<>(com.tpv.desktop.tpv.domain.model.PrintQueueState.OK);
        private final IntegerProperty pending = new SimpleIntegerProperty(0);
        private final StringProperty lastError = new SimpleStringProperty("");
        private final ObservableList<String> errors = FXCollections.observableArrayList();
        private final List<String> destinations = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        @Override
        public ObjectProperty<com.tpv.desktop.tpv.domain.model.PrintQueueState> stateProperty() {
            return state;
        }

        @Override
        public IntegerProperty pendingJobsProperty() {
            return pending;
        }

        @Override
        public StringProperty lastErrorProperty() {
            return lastError;
        }

        @Override
        public ObservableList<String> errorHistory() {
            return errors;
        }

        @Override
        public void enqueue(String destination, String text) {
            destinations.add(destination);
            payloads.add(text);
        }

        List<String> destinations() {
            return destinations;
        }

        List<String> payloads() {
            return payloads;
        }
    }

    private static final class FakeOrderService implements OrderService {
        private final Order order;
        private Set<Destination> lastSentDestinations = Set.of();
        private int sendCalls = 0;

        FakeOrderService(Order order) {
            this.order = order;
        }

        @Override
        public Order openOrGetByTable(int tableId) {
            return order;
        }

        @Override
        public Order getById(long orderId) {
            return order;
        }

        @Override
        public Order addProduct(long orderId, long productId) {
            return order;
        }

        @Override
        public void removeLastPendingLine(long orderId) {
        }

        @Override
        public void setLastLineNote(long orderId, String note) {
        }

        @Override
        public Map<Destination, Integer> pendingByDestination(long orderId) {
            Map<Destination, Integer> out = new EnumMap<>(Destination.class);
            out.put(Destination.BAR, 0);
            out.put(Destination.COCINA, 0);
            out.put(Destination.POSTRES, 0);
            for (OrderLine line : order.getLines()) {
                out.compute(line.getDestination(), (k, v) -> (v == null ? 0 : v) + line.getPendingQty());
            }
            return out;
        }

        @Override
        public int pendingPaymentCents(long orderId) {
            return 0;
        }

        @Override
        public void addPayment(long orderId, String method, int amountCents) {
        }

        @Override
        public void send(long orderId, Set<Destination> destinations, boolean deltaOnly) {
            this.sendCalls++;
            this.lastSentDestinations = destinations;
        }

        @Override
        public void setBillRequested(long orderId, boolean value) {
        }

        @Override
        public void applyDiscountPercent(long orderId, int percent) {
        }

        @Override
        public void applyDiscountAmount(long orderId, int amountCents) {
        }

        @Override
        public void clearDiscount(long orderId) {
        }

        @Override
        public void cancelOrder(long orderId) {
        }

        @Override
        public void moveOrder(long orderId, int newTableId) {
        }
    }
}
