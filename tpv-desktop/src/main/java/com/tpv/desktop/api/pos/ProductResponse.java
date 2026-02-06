package com.tpv.desktop.api.pos;

import java.time.Instant;

public record ProductResponse(
    long id,
    String name,
    int priceCents,
    boolean active,
    long categoryId,
    String categoryName,
    Instant createdAt,
    Instant updatedAt,
    int vatRateBps
) {}
