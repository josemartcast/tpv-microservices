package com.tpv.desktop.api.pos;

import java.util.List;

public record SendComandaResponse(
        long ticketId,
        String destination,
        int sentCount,
        List<Long> sentLineIds
) {
}
