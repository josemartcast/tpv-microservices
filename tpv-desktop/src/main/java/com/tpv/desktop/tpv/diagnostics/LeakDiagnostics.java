package com.tpv.desktop.tpv.diagnostics;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary leak diagnostics helper.
 * Enable with TPV_LEAK_DIAG=true or -Dtpv.leak.diag=true.
 */
public final class LeakDiagnostics {
    private static final boolean ENABLED = enabledFlag();

    private static final AtomicInteger ACTIVE_SCHEDULERS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_TIMERS = new AtomicInteger(0);
    private static final AtomicInteger ACTIVE_HEARTBEATS = new AtomicInteger(0);
    private static final AtomicInteger CONTROLLERS_CREATED = new AtomicInteger(0);
    private static final AtomicInteger CONTROLLERS_DESTROYED = new AtomicInteger(0);
    private static final Map<String, AtomicInteger> CONTROLLERS_ALIVE_BY_TYPE = new ConcurrentHashMap<>();

    private LeakDiagnostics() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static int activeSchedulers() {
        return ACTIVE_SCHEDULERS.get();
    }

    public static int activeTimers() {
        return ACTIVE_TIMERS.get();
    }

    public static int activeHeartbeats() {
        return ACTIVE_HEARTBEATS.get();
    }

    public static int controllersCreated() {
        return CONTROLLERS_CREATED.get();
    }

    public static int controllersDestroyed() {
        return CONTROLLERS_DESTROYED.get();
    }

    public static int controllersAlive() {
        return Math.max(0, CONTROLLERS_CREATED.get() - CONTROLLERS_DESTROYED.get());
    }

    public static String controllersAliveByType() {
        if (!ENABLED) {
            return "{}";
        }
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> entry : CONTROLLERS_ALIVE_BY_TYPE.entrySet()) {
            int value = entry.getValue().get();
            if (value <= 0) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(entry.getKey()).append('=').append(value);
        }
        out.append('}');
        return out.toString();
    }

    public static void controllerCreated(String controllerName) {
        if (!ENABLED) {
            return;
        }
        CONTROLLERS_CREATED.incrementAndGet();
        CONTROLLERS_ALIVE_BY_TYPE.computeIfAbsent(controllerName, k -> new AtomicInteger()).incrementAndGet();
        log("controller.created name=%s alive=%d byType=%s", controllerName, controllersAlive(), controllersAliveByType());
    }

    public static void controllerDestroyed(String controllerName) {
        if (!ENABLED) {
            return;
        }
        CONTROLLERS_DESTROYED.incrementAndGet();
        CONTROLLERS_ALIVE_BY_TYPE.computeIfAbsent(controllerName, k -> new AtomicInteger()).decrementAndGet();
        log("controller.destroyed name=%s alive=%d byType=%s", controllerName, controllersAlive(), controllersAliveByType());
    }

    public static void viewEnter(String viewName) {
        if (!ENABLED) {
            return;
        }
        log("view.enter name=%s", viewName);
    }

    public static void viewExit(String viewName) {
        if (!ENABLED) {
            return;
        }
        log("view.exit name=%s", viewName);
    }

    public static void schedulerStarted(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_SCHEDULERS.incrementAndGet();
        log("scheduler.start name=%s active=%d", name, active);
    }

    public static void schedulerStopped(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_SCHEDULERS.updateAndGet(value -> Math.max(0, value - 1));
        log("scheduler.stop name=%s active=%d", name, active);
    }

    public static void timerStarted(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_TIMERS.incrementAndGet();
        log("timer.start name=%s active=%d", name, active);
    }

    public static void timerStopped(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_TIMERS.updateAndGet(value -> Math.max(0, value - 1));
        log("timer.stop name=%s active=%d", name, active);
    }

    public static void heartbeatStarted(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_HEARTBEATS.incrementAndGet();
        log("heartbeat.start name=%s active=%d", name, active);
    }

    public static void heartbeatStopped(String name) {
        if (!ENABLED) {
            return;
        }
        int active = ACTIVE_HEARTBEATS.updateAndGet(value -> Math.max(0, value - 1));
        log("heartbeat.stop name=%s active=%d", name, active);
    }

    public static void log(String fmt, Object... args) {
        if (!ENABLED) {
            return;
        }
        String msg = (args == null || args.length == 0) ? fmt : String.format(Locale.US, fmt, args);
        System.out.printf(Locale.US, "[LEAK_DIAG] %s %s%n", LocalDateTime.now(), msg);
    }

    private static boolean enabledFlag() {
        String raw = System.getenv("TPV_LEAK_DIAG");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("tpv.leak.diag");
        }
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("TPV_MEM_DIAG");
        }
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
}

