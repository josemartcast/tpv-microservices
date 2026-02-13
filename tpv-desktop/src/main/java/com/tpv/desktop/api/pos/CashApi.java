package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import java.util.List;
import java.util.Map;

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
        return close(cashSessionId, req, null);
    }

    public static CashSessionResponse close(long cashSessionId, CloseCashSessionRequest req, String idempotencyKey) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/close",
                req,
                CashSessionResponse.class,
                idempotencyKey == null ? null : Map.of("Idempotency-Key", idempotencyKey)
        );
    }

    public static CashSessionCloseSummaryResponse closeSummary(long cashSessionId) throws Exception {
        return ApiClient.get(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/close-summary",
                CashSessionCloseSummaryResponse.class
        );
    }

    public static CashIncidentResponse addIncident(long cashSessionId, String direction, int amountCents, String note) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/incidents",
                new CreateCashIncidentRequest(direction, amountCents, note),
                CashIncidentResponse.class
        );
    }

    public static List<CashIncidentResponse> listIncidents(long cashSessionId) throws Exception {
        CashIncidentResponse[] arr = ApiClient.get(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/incidents",
                CashIncidentResponse[].class
        );
        return arr == null ? List.of() : java.util.Arrays.asList(arr);
    }

    public static List<CashSessionOpenTicketResponse> openTickets(long cashSessionId) throws Exception {
        CashSessionOpenTicketResponse[] arr = ApiClient.get(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/open-tickets",
                CashSessionOpenTicketResponse[].class
        );
        return arr == null ? List.of() : java.util.Arrays.asList(arr);
    }

    public static ResolveOpenTicketsResponse resolveOpenTickets(long cashSessionId) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/cash-sessions/" + cashSessionId + "/resolve-open-tickets",
                null,
                ResolveOpenTicketsResponse.class
        );
    }
}
