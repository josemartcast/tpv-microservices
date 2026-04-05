package com.tpv.desktop.tpv.diagnostics;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.services.local.LocalPrintQueueService;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight runtime memory telemetry for production troubleshooting.
 * Enabled only when TPV_MEM_DIAG / tpv.mem.diag is true.
 */
public final class MemoryDiagnostics implements AutoCloseable {
    private static final int PERIOD_SECONDS = 10;
    private final AppContext context;
    private final ScheduledExecutorService worker;

    private MemoryDiagnostics(AppContext context) {
        this.context = context;
        this.worker = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "tpv-memory-diag");
                t.setDaemon(true);
                return t;
            }
        });
        LeakDiagnostics.schedulerStarted("MemoryDiagnostics.worker");
        this.worker.scheduleWithFixedDelay(this::logSnapshot, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public static MemoryDiagnostics startIfEnabled(AppContext context) {
        if (!enabled()) {
            return null;
        }
        return new MemoryDiagnostics(context);
    }

    @Override
    public void close() {
        worker.shutdownNow();
        LeakDiagnostics.schedulerStopped("MemoryDiagnostics.worker");
    }

    private static boolean enabled() {
        String raw = System.getenv("TPV_MEM_DIAG");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("tpv.mem.diag");
        }
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    private void logSnapshot() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long totalMb = runtime.totalMemory() / (1024 * 1024);
            long maxMb = runtime.maxMemory() / (1024 * 1024);
            int pendingPrintJobs = context.printQueueService().pendingJobsProperty().get();
            int backendErrors = context.backendStatusService().errorHistory().size();
            int printErrors = context.printQueueService().errorHistory().size();
            String status = String.valueOf(context.backendStatusService().statusProperty().get());
            String printQueueStats = "n/a";
            if (context.printQueueService() instanceof LocalPrintQueueService localPrintQueueService) {
                LocalPrintQueueService.DiagnosticSnapshot snapshot = localPrintQueueService.diagnosticSnapshot();
                printQueueStats = String.format(
                        Locale.US,
                        "workerQ=%d,pending=%d,errors=%d,state=%s",
                        snapshot.workerQueueSize(),
                        snapshot.pendingJobs(),
                        snapshot.errorHistorySize(),
                        snapshot.state()
                );
            }
            var autoPrint = context.comandaAutoPrintService() == null
                    ? null
                    : context.comandaAutoPrintService().diagnosticSnapshot();
            String pendingActions = autoPrint == null
                    ? "n/a"
                    : String.format(
                    Locale.US,
                    "pendingByTicket=%d,lastPending=%d,pendingClosed=%d,paidPrinted=%d,supSend=%d,supPay=%d,supPrebill=%d",
                    autoPrint.pendingByTicket(),
                    autoPrint.lastPendingCountByTicket(),
                    autoPrint.pendingClosedTicketPrints(),
                    autoPrint.paidPrintedByTicket(),
                    autoPrint.localSendSuppress(),
                    autoPrint.localPaymentSuppress(),
                    autoPrint.localPrebillSuppress()
            );

            System.out.printf(
                    Locale.US,
                    "[MEM_DIAG] %s used=%dMB total=%dMB max=%dMB schedulers=%d timers=%d heartbeats=%d controllersAlive=%d controllers=%s pendingPrint=%d offlineQueue=%s backendStatus=%s backendErrors=%d printErrors=%d pendingActions=%s%n",
                    LocalDateTime.now(),
                    usedMb,
                    totalMb,
                    maxMb,
                    LeakDiagnostics.activeSchedulers(),
                    LeakDiagnostics.activeTimers(),
                    LeakDiagnostics.activeHeartbeats(),
                    LeakDiagnostics.controllersAlive(),
                    LeakDiagnostics.controllersAliveByType(),
                    pendingPrintJobs,
                    printQueueStats,
                    status,
                    backendErrors,
                    printErrors,
                    pendingActions
            );
        } catch (Exception ignored) {
            // Non-blocking diagnostics.
        }
    }
}
