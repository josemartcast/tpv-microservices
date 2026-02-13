package com.tpv.desktop.tpv.domain.model;

public record Product(
        long id,
        long categoryId,
        String name,
        int priceCents,
        int vatRateBps,
        Destination destination,
        String colorClass
) {
}

