package com.tpv.desktop.api.pos;

public record UpdateProductRequest(
        String name,
        int priceCents,
        long categoryId,
        int vatRateBps
) {
}
