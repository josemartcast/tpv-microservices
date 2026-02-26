package com.tpv.pos_service.dto;

import java.time.Instant;

public record SalonAreaResponse(
        long id,
        String name,
        int firstTableNumber,
        int tableCount,
        int lastTableNumber,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
