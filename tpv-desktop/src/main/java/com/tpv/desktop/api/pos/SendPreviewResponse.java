package com.tpv.desktop.api.pos;

import java.util.List;

public record SendPreviewResponse(
        long ticketId,
        List<TicketLineResponse> pendingLines
) {
}
