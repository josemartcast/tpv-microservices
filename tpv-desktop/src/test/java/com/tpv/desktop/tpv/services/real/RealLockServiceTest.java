package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.api.pos.TableLockResponse;
import com.tpv.desktop.tpv.domain.model.TableLock;
import com.tpv.desktop.tpv.services.LockException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealLockServiceTest {

    @Test
    void lock_conflictMapsToOwnedByOther() {
        StubGateway gateway = new StubGateway();
        gateway.lockError = new ApiException(409, "{\"message\":\"Table 4 is locked by waiter (T-003)\"}");
        RealLockService service = new RealLockService(gateway);

        LockException ex = assertThrows(LockException.class, () -> service.lock(4));

        assertEquals(LockException.Reason.OWNED_BY_OTHER, ex.reason());
        assertEquals(409, ex.httpStatus());
    }

    @Test
    void heartbeat_noActiveLockMapsToExpiredOrMissing() {
        StubGateway gateway = new StubGateway();
        gateway.heartbeatError = new ApiException(409, "{\"message\":\"No active lock for table 7\"}");
        RealLockService service = new RealLockService(gateway);

        LockException ex = assertThrows(LockException.class, () -> service.heartbeat(7));

        assertEquals(LockException.Reason.EXPIRED_OR_MISSING, ex.reason());
        assertEquals(409, ex.httpStatus());
    }

    @Test
    void unlock_unauthorizedMapsToAuth() {
        StubGateway gateway = new StubGateway();
        gateway.unlockError = new ApiException(401, "{\"message\":\"Unauthorized\"}");
        RealLockService service = new RealLockService(gateway);

        LockException ex = assertThrows(LockException.class, () -> service.unlock(9));

        assertEquals(LockException.Reason.AUTH, ex.reason());
        assertEquals(401, ex.httpStatus());
    }

    @Test
    void activeLock_returnsLockWhenPresentAndNotExpired() {
        StubGateway gateway = new StubGateway();
        gateway.tablesResponse = new SalonTableResponse[] {
                new SalonTableResponse(1, "Salon", "", "FREE", null, 0, 0, 0, null, null, null, null, null),
                new SalonTableResponse(2, "Salon", "VENTANA", "LOCKED", 100L, 1000, 3, 1, null, null, "admin", "T-002", Instant.now().plusSeconds(40))
        };
        RealLockService service = new RealLockService(gateway);

        TableLock lock = service.activeLock(2);

        assertNotNull(lock);
        assertEquals(2, lock.tableId());
        assertEquals("T-002", lock.terminalId());
    }

    @Test
    void activeLock_returnsNullWhenExpired() {
        StubGateway gateway = new StubGateway();
        gateway.tablesResponse = new SalonTableResponse[] {
                new SalonTableResponse(2, "Salon", "", "LOCKED", 100L, 1000, 3, 1, null, null, "admin", "T-002", Instant.now().minusSeconds(2))
        };
        RealLockService service = new RealLockService(gateway);

        TableLock lock = service.activeLock(2);

        assertNull(lock);
    }

    private static final class StubGateway implements RealLockService.LockGateway {
        private ApiException lockError;
        private ApiException unlockError;
        private ApiException heartbeatError;
        private SalonTableResponse[] tablesResponse = new SalonTableResponse[0];

        @Override
        public TableLockResponse lock(int tableId) throws Exception {
            if (lockError != null) throw lockError;
            return new TableLockResponse(tableId, "T-001", "admin", Instant.now().plusSeconds(90));
        }

        @Override
        public void unlock(int tableId) throws Exception {
            if (unlockError != null) throw unlockError;
        }

        @Override
        public TableLockResponse heartbeat(int tableId) throws Exception {
            if (heartbeatError != null) throw heartbeatError;
            return new TableLockResponse(tableId, "T-001", "admin", Instant.now().plusSeconds(90));
        }

        @Override
        public SalonTableResponse[] tables() {
            return tablesResponse;
        }
    }
}
