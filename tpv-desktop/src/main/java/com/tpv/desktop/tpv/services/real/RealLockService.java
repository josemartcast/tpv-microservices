package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.TableLockResponse;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.LockService;

public class RealLockService implements LockService {
    @Override
    public TableLock lock(int tableId) {
        try {
            return toDomain(SalonApi.lockTable(tableId));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo bloquear mesa " + tableId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void unlock(int tableId) {
        try {
            SalonApi.unlockTable(tableId);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo desbloquear mesa " + tableId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public TableLock heartbeat(int tableId) {
        try {
            return toDomain(SalonApi.heartbeatTable(tableId));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo renovar lock de mesa " + tableId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public TableLock activeLock(int tableId) {
        return null;
    }

    private static TableLock toDomain(TableLockResponse lock) {
        return new TableLock(lock.tableNumber(), lock.terminalId(), lock.lockedBy(), lock.expiresAt());
    }
}

