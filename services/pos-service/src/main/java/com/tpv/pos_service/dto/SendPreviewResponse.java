package com.tpv.pos_service.dto;

import java.util.List;

public record SendPreviewResponse(
        long ticketId,
        List<TicketLineResponse> pendingLines
) {
}
