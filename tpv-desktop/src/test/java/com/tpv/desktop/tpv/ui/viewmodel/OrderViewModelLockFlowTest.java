package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Order;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.LockException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderViewModelLockFlowTest {

    @Test
    void heartbeatLock_recoversWhenExpiredOrMissing() {
        FakeLockService lock = new FakeLockService();
        lock.heartbeatAction = tableId -> {
            throw new LockException(LockException.Reason.EXPIRED_OR_MISSING, "missing");
        };

        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                new StaticOrderService(100),
                lock,
                new NoopPrintQueueService(),
                new AppState()
        );
        vm.bindOrder(100, 1);

        assertDoesNotThrow(vm::heartbeatLock);
        assertEquals(1, lock.lockCalls.get());
        assertEquals("Bloqueo recuperado.", vm.feedbackProperty().get());
    }

    @Test
    void heartbeatLock_throwsWhenOwnedByOtherTerminal() {
        FakeLockService lock = new FakeLockService();
        lock.heartbeatAction = tableId -> {
            throw new LockException(LockException.Reason.OWNED_BY_OTHER, "other terminal");
        };

        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                new StaticOrderService(100),
                lock,
                new NoopPrintQueueService(),
                new AppState()
        );
        vm.bindOrder(100, 1);

        LockException ex = assertThrows(LockException.class, vm::heartbeatLock);
        assertEquals(LockException.Reason.OWNED_BY_OTHER, ex.reason());
        assertEquals(0, lock.lockCalls.get());
    }

    @Test
    void payFull_unlockOwnershipConflictStillSucceeds() {
        FakeLockService lock = new FakeLockService();
        lock.unlockAction = tableId -> {
            throw new LockException(LockException.Reason.OWNED_BY_OTHER, "locked by T-003");
        };
        StaticOrderService orders = new StaticOrderService(650);

        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                orders,
                lock,
                new NoopPrintQueueService(),
                new AppState()
        );
        vm.bindOrder(100, 1);

        boolean paid = vm.payFull("CARD");

        assertTrue(paid);
        assertEquals(1, orders.addPaymentCalls.get());
        assertEquals("Cobro registrado (CARD).", vm.feedbackProperty().get());
    }

    @Test
    void releaseLock_authIssueDoesNotThrowAndSetsWarning() {
        FakeLockService lock = new FakeLockService();
        lock.unlockAction = tableId -> {
            throw new LockException(LockException.Reason.AUTH, "unauthorized");
        };

        OrderViewModel vm = new OrderViewModel(
                new StaticCatalogService(),
                new StaticOrderService(0),
                lock,
                new NoopPrintQueueService(),
                new AppState()
        );
        vm.bindOrder(100, 1);

        assertDoesNotThrow(vm::releaseLock);
        assertTrue(vm.feedbackProperty().get().contains("sesion expirada"));
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
            return new Product(1, 1, "Test", 100, Destination.COCINA, "prod-dark");
        }
    }

    private static final class StaticOrderService implements OrderService {
        private final Order order = new Order(100, 1, 4, Instant.now());
        private int pendingPaymentCents;
        private final AtomicInteger addPaymentCalls = new AtomicInteger();

        StaticOrderService(int pendingPaymentCents) {
            this.pendingPaymentCents = pendingPaymentCents;
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
            return Map.of(Destination.BAR, 0, Destination.COCINA, 0, Destination.POSTRES, 0);
        }

        @Override
        public int pendingPaymentCents(long orderId) {
            return pendingPaymentCents;
        }

        @Override
        public void addPayment(long orderId, String method, int amountCents) {
            addPaymentCalls.incrementAndGet();
            pendingPaymentCents = Math.max(0, pendingPaymentCents - amountCents);
        }

        @Override
        public void send(long orderId, Set<Destination> destinations, boolean deltaOnly) {
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

    private static final class NoopPrintQueueService implements PrintQueueService {
        private final ObjectProperty<com.tpv.desktop.tpv.domain.model.PrintQueueState> state =
                new SimpleObjectProperty<>(com.tpv.desktop.tpv.domain.model.PrintQueueState.OK);
        private final IntegerProperty pending = new SimpleIntegerProperty(0);
        private final StringProperty lastError = new SimpleStringProperty("");
        private final ObservableList<String> errors = FXCollections.observableArrayList();

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
        }
    }

    private static final class FakeLockService implements LockService {
        private final AtomicInteger lockCalls = new AtomicInteger();
        private TableAction lockAction = tableId -> new TableLock(tableId, "T-001", "me", Instant.now().plusSeconds(60));
        private TableAction unlockAction = tableId -> null;
        private TableAction heartbeatAction = tableId -> new TableLock(tableId, "T-001", "me", Instant.now().plusSeconds(60));

        @Override
        public TableLock lock(int tableId) {
            lockCalls.incrementAndGet();
            return lockAction.execute(tableId);
        }

        @Override
        public void unlock(int tableId) {
            unlockAction.execute(tableId);
        }

        @Override
        public TableLock heartbeat(int tableId) {
            return heartbeatAction.execute(tableId);
        }

        @Override
        public TableLock activeLock(int tableId) {
            return null;
        }
    }

    @FunctionalInterface
    private interface TableAction {
        TableLock execute(int tableId);
    }
}
