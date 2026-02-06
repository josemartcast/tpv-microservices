package com.tpv.desktop.api.pos;

public record CreatePaymentRequest(String method, int amountCents) {}

