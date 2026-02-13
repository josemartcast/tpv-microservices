package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.TableLockRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TicketMoveTableConcurrencyIT {

    @Autowired
    private TicketService ticketService;
    @Autowired
    private CashSessionRepository cashSessionRepo;
    @Autowired
    private TicketRepository ticketRepo;
    @Autowired
    private TableLockRepository tableLockRepo;

    @BeforeEach
    void cleanData() {
        tableLockRepo.deleteAll();
        ticketRepo.deleteAll();
        cashSessionRepo.deleteAll();
    }

    @Test
    void concurrentMoveToSameTarget_allowsOneAndRejectsOther() throws Exception {
        cashSessionRepo.save(new CashSession(0, "qa", null));

        TicketResponse ticketA = ticketService.create(1);
        TicketResponse ticketB = ticketService.create(2);
        int targetTable = 3;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<MoveResult> moveA = () -> runMove(start, ticketA.id(), targetTable, "T-A");
            Callable<MoveResult> moveB = () -> runMove(start, ticketB.id(), targetTable, "T-B");

            Future<MoveResult> futureA = pool.submit(moveA);
            Future<MoveResult> futureB = pool.submit(moveB);

            start.countDown();

            MoveResult resultA = futureA.get(10, TimeUnit.SECONDS);
            MoveResult resultB = futureB.get(10, TimeUnit.SECONDS);

            int successCount = (resultA.success ? 1 : 0) + (resultB.success ? 1 : 0);
            assertEquals(1, successCount, "exactly one move should succeed");

            MoveResult failed = resultA.success ? resultB : resultA;
            assertInstanceOf(ConflictException.class, failed.error, "failed move should be a ConflictException");

            var persistedA = ticketRepo.findById(ticketA.id()).orElse(null);
            var persistedB = ticketRepo.findById(ticketB.id()).orElse(null);
            assertNotNull(persistedA);
            assertNotNull(persistedB);

            boolean ticketAAtTarget = Integer.valueOf(targetTable).equals(persistedA.getTableNumber());
            boolean ticketBAtTarget = Integer.valueOf(targetTable).equals(persistedB.getTableNumber());
            assertTrue(ticketAAtTarget ^ ticketBAtTarget, "only one ticket should end at target table");

            Integer otherTable = ticketAAtTarget ? persistedB.getTableNumber() : persistedA.getTableNumber();
            assertNotNull(otherTable);
            assertTrue(otherTable == 1 || otherTable == 2, "losing ticket must remain on original table");
        } finally {
            pool.shutdownNow();
        }
    }

    private MoveResult runMove(CountDownLatch start, long ticketId, int targetTable, String terminalId) {
        try {
            start.await(5, TimeUnit.SECONDS);
            ticketService.moveTable(ticketId, targetTable, terminalId, "qa");
            return MoveResult.success();
        } catch (Throwable e) {
            return MoveResult.failure(e);
        }
    }

    private static final class MoveResult {
        private final boolean success;
        private final Throwable error;

        private MoveResult(boolean success, Throwable error) {
            this.success = success;
            this.error = error;
        }

        static MoveResult success() {
            return new MoveResult(true, null);
        }

        static MoveResult failure(Throwable error) {
            return new MoveResult(false, error);
        }
    }
}
