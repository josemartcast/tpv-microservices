package com.tpv.desktop.tpv.services.local;

import com.tpv.desktop.tpv.domain.model.PrintQueueState;
import com.tpv.desktop.tpv.services.PrintQueueService;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class LocalPrintQueueService implements PrintQueueService, AutoCloseable {
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1200;

    private final ObjectProperty<PrintQueueState> state = new SimpleObjectProperty<>(PrintQueueState.OK);
    private final IntegerProperty pendingJobs = new SimpleIntegerProperty(0);
    private final StringProperty lastError = new SimpleStringProperty("");
    private final ObservableList<String> errors = FXCollections.observableArrayList();
    private final PrintGateway printGateway;
    private final UiExecutor uiExecutor;
    private final ScheduledExecutorService worker;

    public LocalPrintQueueService() {
        this(LocalPrintQueueService::printOnFxThread, Platform::runLater, createWorker());
    }

    LocalPrintQueueService(PrintGateway printGateway, UiExecutor uiExecutor, ScheduledExecutorService worker) {
        this.printGateway = printGateway;
        this.uiExecutor = uiExecutor;
        this.worker = worker;
    }

    private static ScheduledExecutorService createWorker() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "tpv-print-queue");
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public ObjectProperty<PrintQueueState> stateProperty() {
        return state;
    }

    @Override
    public IntegerProperty pendingJobsProperty() {
        return pendingJobs;
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
        if (text == null || text.isBlank()) {
            return;
        }
        PrintJob job = new PrintJob(destination == null || destination.isBlank() ? "ALL" : destination, text, 0);
        uiExecutor.execute(() -> {
            pendingJobs.set(pendingJobs.get() + 1);
            refreshState();
        });
        worker.execute(() -> process(job));
    }

    private void process(PrintJob job) {
        try {
            printGateway.print(job.text());
            uiExecutor.execute(() -> {
                pendingJobs.set(Math.max(0, pendingJobs.get() - 1));
                lastError.set("");
                refreshState();
            });
        } catch (Exception ex) {
            if (job.attempt() < MAX_RETRIES) {
                worker.schedule(() -> process(new PrintJob(job.destination(), job.text(), job.attempt() + 1)),
                        RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                return;
            }
            String msg = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + " - " + job.destination().toUpperCase(Locale.ROOT)
                    + " -> " + ex.getMessage();
            uiExecutor.execute(() -> {
                pendingJobs.set(Math.max(0, pendingJobs.get() - 1));
                lastError.set(msg);
                errors.add(0, msg);
                while (errors.size() > 5) {
                    errors.remove(errors.size() - 1);
                }
                refreshState();
            });
        }
    }

    private static void printOnFxThread(String text) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                PrintUtil.printTextToPdf(text, null);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.get(60, TimeUnit.SECONDS);
    }

    private void refreshState() {
        if (pendingJobs.get() > 0) {
            state.set(PrintQueueState.QUEUED);
        } else if (lastError.get() != null && !lastError.get().isBlank()) {
            state.set(PrintQueueState.ERROR);
        } else {
            state.set(PrintQueueState.OK);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    @FunctionalInterface
    interface PrintGateway {
        void print(String text) throws Exception;
    }

    @FunctionalInterface
    interface UiExecutor {
        void execute(Runnable runnable);
    }

    private record PrintJob(String destination, String text, int attempt) { }
}
