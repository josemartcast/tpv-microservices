package com.tpv.desktop.api.pos;

public record CloseCashSessionRequest(int closingCashCents, String note) {}
