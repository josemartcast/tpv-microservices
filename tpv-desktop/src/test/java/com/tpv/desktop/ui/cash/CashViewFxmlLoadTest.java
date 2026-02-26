package com.tpv.desktop.ui.cash;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CashViewFxmlLoadTest {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

    @BeforeAll
    static void initFxToolkit() throws Exception {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("JavaFX toolkit did not start in time");
            }
        }
    }

    @Test
    void cashViewFxmlLoads() throws Exception {
        URL fxml = getClass().getResource("/fxml/cash/CashView.fxml");
        assertNotNull(fxml, "CashView.fxml should exist in classpath");

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> loaded = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                loaded.set(FXMLLoader.load(fxml));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("Timeout loading CashView.fxml");
        }
        if (error.get() != null) {
            fail("CashView.fxml failed to load: " + error.get(), error.get());
        }
        assertNotNull(loaded.get(), "Loaded root should not be null");
    }
}

