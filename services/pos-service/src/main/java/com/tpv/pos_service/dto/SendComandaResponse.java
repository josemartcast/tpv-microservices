package com.tpv.pos_service.dto;

import java.util.List;

public record SendComandaResponse(
        long ticketId,
        String destination,
        int sentCount,
        List<Long> sentLineIds
) {
}
