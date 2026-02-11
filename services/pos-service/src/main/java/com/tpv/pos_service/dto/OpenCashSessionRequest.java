package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record OpenCashSessionRequest(
        @Min(0) int openingCashCents,
        @Size(max = 255) String note
) {}
