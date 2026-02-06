package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class PaymentApi {
  private PaymentApi(){}

  public static void addPayment(long ticketId, String method, int amountCents) throws Exception {
    ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/payments",
        new CreatePaymentRequest(method, amountCents),
        Object.class // no nos importa el body ahora
    );
  }
}
