package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.*;
import com.tpv.desktop.tpv.services.LockService;
import com.tpv.desktop.tpv.services.TableService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FakeTableService implements TableService {
    private final FakeDataStore store;
    private final LockService lockService;
    private final AppState appState;

    public FakeTableService(FakeDataStore store, LockService lockService, AppState appState) {
        this.store = store;
        this.lockService = lockService;
        this.appState = appState;
    }

    @Override
    public List<TableSnapshot> tables() {
        List<TableSnapshot> out = new ArrayList<>();
        String currentTerminal = appState.terminalIdProperty().get();

        for (int i = 1; i <= 14; i++) {
            Order order = store.openOrdersByTable.get(i);
            TableLock lock = lockService.activeLock(i);

            TableStatus status;
                if (order == null) {
                    if (lock == null) {
                        status = TableStatus.FREE;
                    } else if (currentTerminal.equalsIgnoreCase(lock.terminalId())) {
                        status = TableStatus.LOCKED_BY_ME;
                    } else {
                        status = TableStatus.LOCKED_BY_OTHER;
                    }
                out.add(new TableSnapshot(i, "Salon", "Mesa " + i, status, 0, 0, 0, false,
                        lock == null ? null : lock.owner(), lock == null ? null : lock.terminalId(), 0));
                continue;
            }

            if (lock != null && !currentTerminal.equalsIgnoreCase(lock.terminalId())) {
                status = TableStatus.LOCKED_BY_OTHER;
            } else if (lock != null) {
                status = TableStatus.LOCKED_BY_ME;
            } else if (order.isBillRequested()) {
                status = TableStatus.BILL_REQUESTED;
            } else if (order.pendingCount() > 0) {
                status = TableStatus.PENDING_SEND;
            } else {
                status = TableStatus.OCCUPIED;
            }

            long elapsed = Math.max(0, Duration.between(order.getOpenedAt(), Instant.now()).toMinutes());
            out.add(new TableSnapshot(i, "Salon", "Mesa " + i, status, order.totalCents(), elapsed, order.pendingCount(), order.isBillRequested(),
                    lock == null ? null : lock.owner(), lock == null ? null : lock.terminalId(), order.getId()));
        }

        return out;
    }
}

