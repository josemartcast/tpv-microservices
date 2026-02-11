package com.tpv.pos_service.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashSessionTest {

    @Test
    void close_usesExpectedCashWithoutDoubleAddingOpening() {
        CashSession session = new CashSession(1_000, "admin", "open");
        session.setExpectedCashCents(1_500);

        session.close(1_400, "admin", "close");

        assertEquals(-100, session.getCashDifferenceCents());
    }
}
