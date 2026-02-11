package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.app.AppState;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.LockService;

import java.time.Instant;

public class FakeLockService implements LockService {
    private static final long TTL_SECONDS = 90;
    private final FakeDataStore store;
    private final AppState appState;

    public FakeLockService(FakeDataStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
    }

    @Override
    public TableLock lock(int tableId) {
        store.cleanupExpiredLocks();
        String terminalId = appState.terminalIdProperty().get();
        TableLock existing = store.locks.get(tableId);
        if (existing != null && !terminalId.equalsIgnoreCase(existing.terminalId())) {
            throw new IllegalStateException("Bloqueada por " + existing.terminalId());
        }
        TableLock lock = new TableLock(tableId, terminalId, appState.activeUserProperty().get().displayName(), Instant.now().plusSeconds(TTL_SECONDS));
        store.locks.put(tableId, lock);
        return lock;
    }

    @Override
    public void unlock(int tableId) {
        TableLock lock = store.locks.get(tableId);
        if (lock == null) return;
        if (appState.terminalIdProperty().get().equalsIgnoreCase(lock.terminalId())) {
            store.locks.remove(tableId);
        }
    }

    @Override
    public TableLock heartbeat(int tableId) {
        TableLock lock = store.locks.get(tableId);
        if (lock == null) return lock(tableId);
        if (!appState.terminalIdProperty().get().equalsIgnoreCase(lock.terminalId())) {
            throw new IllegalStateException("No puedes renovar lock de " + lock.terminalId());
        }
        TableLock renewed = new TableLock(lock.tableId(), lock.terminalId(), lock.owner(), Instant.now().plusSeconds(TTL_SECONDS));
        store.locks.put(tableId, renewed);
        return renewed;
    }

    @Override
    public TableLock activeLock(int tableId) {
        store.cleanupExpiredLocks();
        return store.locks.get(tableId);
    }
}

