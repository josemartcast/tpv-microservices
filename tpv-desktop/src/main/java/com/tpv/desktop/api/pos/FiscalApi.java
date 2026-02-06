package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class FiscalApi {
  private FiscalApi() {}

  public static FiscalSummaryResponse summary(long cashSessionId) throws Exception {
    return ApiClient.get("/api/v1/pos/cash-sessions/" + cashSessionId + "/fiscal-summary",
        FiscalSummaryResponse.class);
  }

  public static FiscalClosureResponse closure(long cashSessionId) throws Exception {
    return ApiClient.get("/api/v1/pos/cash-sessions/" + cashSessionId + "/fiscal-closure",
        FiscalClosureResponse.class);
  }
}
