package com.tpv.desktop.api.pos;

public record AutoPrintClaimResponse(
        long ticketId,
        String destination,
        String printJobId,
        boolean claimed
) {
}
