package com.tpv.desktop.api.pos;

public record AddComboTicketLineRequest(
        long baseProductId,
        long mixerProductId,
        int qty
) {
}

