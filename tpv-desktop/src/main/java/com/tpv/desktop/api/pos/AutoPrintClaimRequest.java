package com.tpv.desktop.api.pos;

public record AutoPrintClaimRequest(
        String destination,
        String printJobId
) {
}
