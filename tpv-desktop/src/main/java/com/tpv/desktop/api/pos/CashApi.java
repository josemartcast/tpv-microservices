package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class CashApi {

    private CashApi() {
    }

    public static CashSessionResponse current() throws Exception {
        return ApiClient.get("/api/v1/pos/cash-sessions/current", CashSessionResponse.class);
    }

    public static CashSessionResponse open(int openingCashCents, String note) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/open",
                new OpenCashSessionRequest(openingCashCents, note),
                CashSessionResponse.class
        );
    }

    public static CashSessionResponse close(long id, int closingCashCents, String note) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/" + id + "/close",
                new CloseCashSessionRequest(closingCashCents, note),
                CashSessionResponse.class
        );
    }


    public static CashSessionResponse close(long cashSessionId, CloseCashSessionRequest req) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/close",
                req,
                CashSessionResponse.class
        );
    }
}
