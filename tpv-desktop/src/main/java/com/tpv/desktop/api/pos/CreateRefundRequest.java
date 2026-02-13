package com.tpv.desktop.api.pos;

public record CreateRefundRequest(String method, int amountCents) {
}
