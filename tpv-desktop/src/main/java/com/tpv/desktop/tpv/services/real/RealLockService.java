package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.api.pos.TableLockResponse;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.LockException;
import com.tpv.desktop.tpv.services.LockService;

import java.time.Instant;

public class RealLockService implements LockService {
    private final LockGateway gateway;

    public RealLockService() {
        this(new LockGateway() {
            @Override
            public TableLockResponse lock(int tableId) throws Exception {
                return SalonApi.lockTable(tableId);
            }

            @Override
            public void unlock(int tableId) throws Exception {
                SalonApi.unlockTable(tableId);
            }

            @Override
            public TableLockResponse heartbeat(int tableId) throws Exception {
                return SalonApi.heartbeatTable(tableId);
            }

            @Override
            public SalonTableResponse[] tables() throws Exception {
                return SalonApi.tables();
            }
        });
    }

    RealLockService(LockGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public TableLock lock(int tableId) {
        try {
            return toDomain(gateway.lock(tableId));
        } catch (ApiException ex) {
            throw mapApiError("lock", tableId, ex);
        } catch (Exception e) {
            throw new LockException(
                    LockException.Reason.BACKEND,
                    "No se pudo bloquear mesa " + tableId + ": " + e.getMessage(),
                    null,
                    e
            );
        }
    }

    @Override
    public void unlock(int tableId) {
        try {
            gateway.unlock(tableId);
        } catch (ApiException ex) {
            throw mapApiError("unlock", tableId, ex);
        } catch (Exception e) {
            throw new LockException(
                    LockException.Reason.BACKEND,
                    "No se pudo desbloquear mesa " + tableId + ": " + e.getMessage(),
                    null,
                    e
            );
        }
    }

    @Override
    public TableLock heartbeat(int tableId) {
        try {
            return toDomain(gateway.heartbeat(tableId));
        } catch (ApiException ex) {
            throw mapApiError("heartbeat", tableId, ex);
        } catch (Exception e) {
            throw new LockException(
                    LockException.Reason.BACKEND,
                    "No se pudo renovar lock de mesa " + tableId + ": " + e.getMessage(),
                    null,
                    e
            );
        }
    }

    @Override
    public TableLock activeLock(int tableId) {
        try {
            SalonTableResponse[] tables = gateway.tables();
            if (tables == null) {
                return null;
            }
            for (SalonTableResponse t : tables) {
                if (t.tableNumber() != tableId) {
                    continue;
                }
                if (t.lockedTerminalId() == null || t.lockedTerminalId().isBlank()) {
                    return null;
                }
                if (t.lockExpiresAt() != null && !t.lockExpiresAt().isAfter(Instant.now())) {
                    return null;
                }
                return new TableLock(t.tableNumber(), t.lockedTerminalId(), t.lockedBy(), t.lockExpiresAt());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static TableLock toDomain(TableLockResponse lock) {
        return new TableLock(lock.tableNumber(), lock.terminalId(), lock.lockedBy(), lock.expiresAt());
    }

    private static LockException mapApiError(String operation, int tableId, ApiException ex) {
        int status = ex.getStatus();
        String body = ex.getBody();

        if (status == 401 || status == 403) {
            return new LockException(
                    LockException.Reason.AUTH,
                    "Sesion expirada/no autorizada en " + operation + " mesa " + tableId,
                    status,
                    ex
            );
        }

        if (status == 409) {
            String normalized = body == null ? "" : body.toLowerCase();
            if (normalized.contains("no active lock") || normalized.contains("lock expired")) {
                return new LockException(
                        LockException.Reason.EXPIRED_OR_MISSING,
                        "Lock expirado o inexistente en mesa " + tableId,
                        status,
                        ex
                );
            }
            if (normalized.contains("cannot heartbeat lock owned by")
                    || normalized.contains("cannot unlock table locked by")
                    || normalized.contains("is locked by")) {
                return new LockException(
                        LockException.Reason.OWNED_BY_OTHER,
                        "Mesa " + tableId + " bloqueada por otro terminal",
                        status,
                        ex
                );
            }
            LockException.Reason reason = "heartbeat".equalsIgnoreCase(operation)
                    ? LockException.Reason.EXPIRED_OR_MISSING
                    : LockException.Reason.OWNED_BY_OTHER;
            return new LockException(reason, "Conflicto de lock en mesa " + tableId, status, ex);
        }

        return new LockException(
                LockException.Reason.BACKEND,
                "Error backend lock mesa " + tableId + " (HTTP " + status + ")",
                status,
                ex
        );
    }

    interface LockGateway {
        TableLockResponse lock(int tableId) throws Exception;
        void unlock(int tableId) throws Exception;
        TableLockResponse heartbeat(int tableId) throws Exception;
        SalonTableResponse[] tables() throws Exception;
    }
}
