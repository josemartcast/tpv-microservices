package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import java.util.Map;

public final class PaymentApi {
  private PaymentApi(){}

  public static void addPayment(long ticketId, String method, int amountCents) throws Exception {
    addPayment(ticketId, method, amountCents, null);
  }

  public static void addPayment(long ticketId, String method, int amountCents, String idempotencyKey) throws Exception {
    ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/payments",
        new CreatePaymentRequest(method, amountCents),
        Object.class, // no nos importa el body ahora
        idempotencyKey == null ? null : Map.of("Idempotency-Key", idempotencyKey)
    );
  }
}
