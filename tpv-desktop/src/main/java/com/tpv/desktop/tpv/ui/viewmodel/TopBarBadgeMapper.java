package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.domain.model.PrintQueueState;

public final class TopBarBadgeMapper {
    public static final String BADGE_ONLINE = "badge-online";
    public static final String BADGE_DEGRADED = "badge-degraded";
    public static final String BADGE_OFFLINE = "badge-offline";
    public static final String BADGE_MODE_REAL = "badge-mode-real";
    public static final String BADGE_MODE_FAKE = "badge-mode-fake";

    private TopBarBadgeMapper() {
    }

    public static String backendBadgeClass(BackendStatus status) {
        BackendStatus resolved = status == null ? BackendStatus.OFFLINE : status;
        return switch (resolved) {
            case ONLINE -> BADGE_ONLINE;
            case DEGRADED -> BADGE_DEGRADED;
            case OFFLINE -> BADGE_OFFLINE;
        };
    }

    public static PrintBadgePresentation printBadge(PrintQueueState state, int pendingJobs, String lastError) {
        PrintQueueState resolved = resolvePrintState(state, pendingJobs, lastError);
        return switch (resolved) {
            case OK -> new PrintBadgePresentation("PRINT OK", BADGE_ONLINE);
            case QUEUED -> {
                if (pendingJobs > 0) {
                    yield new PrintBadgePresentation("PRINT Q " + pendingJobs, BADGE_DEGRADED);
                }
                yield new PrintBadgePresentation("PRINT Q", BADGE_DEGRADED);
            }
            case ERROR -> new PrintBadgePresentation("PRINT ERR", BADGE_OFFLINE);
        };
    }

    private static PrintQueueState resolvePrintState(PrintQueueState state, int pendingJobs, String lastError) {
        if (state != null) {
            return state;
        }
        if (pendingJobs > 0) {
            return PrintQueueState.QUEUED;
        }
        if (lastError != null && !lastError.isBlank()) {
            return PrintQueueState.ERROR;
        }
        return PrintQueueState.OK;
    }

    public record PrintBadgePresentation(String text, String styleClass) {
    }

    public static RuntimeModePresentation runtimeModeBadge(String runtimeMode) {
        String normalized = runtimeMode == null ? "" : runtimeMode.trim().toUpperCase();
        if ("REAL".equals(normalized)) {
            return new RuntimeModePresentation("MODE REAL", BADGE_MODE_REAL);
        }
        return new RuntimeModePresentation("MODE FAKE", BADGE_MODE_FAKE);
    }

    public record RuntimeModePresentation(String text, String styleClass) {
    }
}
