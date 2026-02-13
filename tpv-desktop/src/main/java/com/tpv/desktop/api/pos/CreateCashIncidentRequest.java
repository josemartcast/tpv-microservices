package com.tpv.desktop.api.pos;

public record CreateCashIncidentRequest(
        String direction,
        int amountCents,
        String note
) {
}
