package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.domain.model.PrintQueueState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopBarBadgeMapperTest {

    @Test
    void backendBadgeClass_online() {
        assertEquals(TopBarBadgeMapper.BADGE_ONLINE, TopBarBadgeMapper.backendBadgeClass(BackendStatus.ONLINE));
    }

    @Test
    void backendBadgeClass_nullFallsBackToOffline() {
        assertEquals(TopBarBadgeMapper.BADGE_OFFLINE, TopBarBadgeMapper.backendBadgeClass(null));
    }

    @Test
    void printBadge_ok() {
        TopBarBadgeMapper.PrintBadgePresentation badge =
                TopBarBadgeMapper.printBadge(PrintQueueState.OK, 0, "");
        assertEquals("PRINT OK", badge.text());
        assertEquals(TopBarBadgeMapper.BADGE_ONLINE, badge.styleClass());
    }

    @Test
    void printBadge_queuedUsesPendingCount() {
        TopBarBadgeMapper.PrintBadgePresentation badge =
                TopBarBadgeMapper.printBadge(PrintQueueState.QUEUED, 3, "");
        assertEquals("PRINT Q 3", badge.text());
        assertEquals(TopBarBadgeMapper.BADGE_DEGRADED, badge.styleClass());
    }

    @Test
    void printBadge_error() {
        TopBarBadgeMapper.PrintBadgePresentation badge =
                TopBarBadgeMapper.printBadge(PrintQueueState.ERROR, 0, "paper jam");
        assertEquals("PRINT ERR", badge.text());
        assertEquals(TopBarBadgeMapper.BADGE_OFFLINE, badge.styleClass());
    }

    @Test
    void printBadge_nullStateFallsBackToHeuristics() {
        TopBarBadgeMapper.PrintBadgePresentation queued =
                TopBarBadgeMapper.printBadge(null, 2, "");
        assertEquals("PRINT Q 2", queued.text());
        assertEquals(TopBarBadgeMapper.BADGE_DEGRADED, queued.styleClass());

        TopBarBadgeMapper.PrintBadgePresentation error =
                TopBarBadgeMapper.printBadge(null, 0, "timeout");
        assertEquals("PRINT ERR", error.text());
        assertEquals(TopBarBadgeMapper.BADGE_OFFLINE, error.styleClass());
    }

    @Test
    void runtimeModeBadge_real() {
        TopBarBadgeMapper.RuntimeModePresentation mode = TopBarBadgeMapper.runtimeModeBadge("REAL");
        assertEquals("MODE REAL", mode.text());
        assertEquals(TopBarBadgeMapper.BADGE_MODE_REAL, mode.styleClass());
    }

    @Test
    void runtimeModeBadge_defaultFakeWhenUnknown() {
        TopBarBadgeMapper.RuntimeModePresentation mode = TopBarBadgeMapper.runtimeModeBadge("AUTO");
        assertEquals("MODE FAKE", mode.text());
        assertEquals(TopBarBadgeMapper.BADGE_MODE_FAKE, mode.styleClass());
    }
}
