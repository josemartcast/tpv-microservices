package com.tpv.pos_service.dto;

import java.time.Instant;

public record ProductResponse(
        long id,
        String name,
        int priceCents,
        boolean active,
        long categoryId,
        String categoryName,
        String categoryPrintDestination,
        Instant createdAt,
        Instant updatedAt,
        int vatRateBps
){

}
