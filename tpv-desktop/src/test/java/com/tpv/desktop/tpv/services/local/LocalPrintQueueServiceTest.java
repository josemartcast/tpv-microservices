package com.tpv.desktop.tpv.services.local;

import com.tpv.desktop.tpv.domain.model.PrintQueueState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPrintQueueServiceTest {

    @Test
    void enqueue_success_setsOkAndClearsPending() throws Exception {
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger calls = new AtomicInteger();
        LocalPrintQueueService service = new LocalPrintQueueService(
                text -> calls.incrementAndGet(),
                Runnable::run,
                worker
        );
        try {
            service.enqueue("BAR", "test print");
            waitUntil(() -> service.pendingJobsProperty().get() == 0, 3000);

            assertEquals(1, calls.get());
            assertEquals(PrintQueueState.OK, service.stateProperty().get());
            assertTrue(service.lastErrorProperty().get().isBlank());
        } finally {
            service.close();
        }
    }

    @Test
    void enqueue_retry_then_success_staysOk() throws Exception {
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger attempts = new AtomicInteger();
        LocalPrintQueueService service = new LocalPrintQueueService(
                text -> {
                    int n = attempts.incrementAndGet();
                    if (n < 3) {
                        throw new RuntimeException("transient");
                    }
                },
                Runnable::run,
                worker
        );
        try {
            service.enqueue("COCINA", "retry print");
            waitUntil(() -> service.pendingJobsProperty().get() == 0, 7000);

            assertEquals(3, attempts.get());
            assertEquals(PrintQueueState.OK, service.stateProperty().get());
            assertTrue(service.lastErrorProperty().get().isBlank());
            assertTrue(service.errorHistory().isEmpty());
        } finally {
            service.close();
        }
    }

    @Test
    void enqueue_permanentFailure_setsErrorAndHistory() throws Exception {
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger attempts = new AtomicInteger();
        LocalPrintQueueService service = new LocalPrintQueueService(
                text -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("broken printer");
                },
                Runnable::run,
                worker
        );
        try {
            service.enqueue("POSTRES", "failed print");
            waitUntil(() -> service.pendingJobsProperty().get() == 0, 7000);

            assertEquals(3, attempts.get());
            assertEquals(PrintQueueState.ERROR, service.stateProperty().get());
            assertFalse(service.lastErrorProperty().get().isBlank());
            assertEquals(1, service.errorHistory().size());
        } finally {
            service.close();
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("Timeout waiting for condition");
    }
}

