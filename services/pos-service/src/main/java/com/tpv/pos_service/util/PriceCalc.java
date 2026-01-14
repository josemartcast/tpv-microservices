package com.tpv.pos_service.util;

public final class PriceCalc {
    private PriceCalc() {}

 
    public static int netFromGross(int grossCents, int vatRateBps) {
        int denom = 10_000 + vatRateBps;
        return (grossCents * 10_000 + denom / 2) / denom; 
    }
}

