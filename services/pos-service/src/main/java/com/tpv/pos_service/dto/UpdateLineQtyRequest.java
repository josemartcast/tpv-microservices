package com.tpv.pos_service.dto;

import jakarta.validation.constraints.Min;

public record UpdateLineQtyRequest(
    @Min(1) int qty
) {}
