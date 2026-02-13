package com.tpv.desktop.api.pos;

public record CreateProductRequest(
        String name,
        int priceCents,
        long categoryId,
        int vatRateBps
) {
}
