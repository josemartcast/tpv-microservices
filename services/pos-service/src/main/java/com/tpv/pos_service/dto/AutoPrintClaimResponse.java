package com.tpv.pos_service.dto;

public record AutoPrintClaimResponse(
        long ticketId,
        String destination,
        String printJobId,
        boolean claimed
) {
}
