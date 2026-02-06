package com.tpv.desktop.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
  private MoneyUtil() {}

  public static int eurosToCents(String eurosText) {
    if (eurosText == null) return 0;
    String t = eurosText.trim().replace(",", ".");
    if (t.isBlank()) return 0;

    BigDecimal euros = new BigDecimal(t);
    BigDecimal cents = euros.multiply(BigDecimal.valueOf(100));
    return cents.setScale(0, RoundingMode.HALF_UP).intValueExact();
  }

  public static String centsToEuros(int cents) {
    BigDecimal euros = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return euros.toPlainString();
  }
}
