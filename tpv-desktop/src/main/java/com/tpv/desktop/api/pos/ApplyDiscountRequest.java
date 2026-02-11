package com.tpv.desktop.api.pos;

public record ApplyDiscountRequest(
        Integer percent,
        Integer amountCents
) {
}

