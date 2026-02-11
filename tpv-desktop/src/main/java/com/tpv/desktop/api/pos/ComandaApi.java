package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import java.util.Map;

public final class ComandaApi {
    private ComandaApi() {
    }

    public static SendPreviewResponse preview(long ticketId) throws Exception {
        return ApiClient.get("/api/v1/pos/tickets/" + ticketId + "/send-preview", SendPreviewResponse.class);
    }

    public static SendComandaResponse send(long ticketId, String destination) throws Exception {
        return send(ticketId, destination, null);
    }

    public static SendComandaResponse send(long ticketId, String destination, String idempotencyKey) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/tickets/" + ticketId + "/send",
                new SendComandaRequest(destination),
                SendComandaResponse.class,
                idempotencyKey == null ? null : Map.of("Idempotency-Key", idempotencyKey)
        );
    }
}
