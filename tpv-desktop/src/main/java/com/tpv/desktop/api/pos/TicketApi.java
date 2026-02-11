package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class TicketApi {
  private TicketApi(){}

  public static TicketResponse create() throws Exception {
    return ApiClient.post("/api/v1/pos/tickets", null, TicketResponse.class);
  }

  public static TicketResponse addLine(long ticketId, long productId, int qty) throws Exception {
    return ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/lines",
        new AddTicketLineRequest(productId, qty), TicketResponse.class);
  }

  public static TicketResponse updateQty(long ticketId, long lineId, int qty) throws Exception {
    return ApiClient.patch("/api/v1/pos/tickets/" + ticketId + "/lines/" + lineId,
        new UpdateLineQtyRequest(qty), TicketResponse.class);
  }

  public static TicketResponse deleteLine(long ticketId, long lineId) throws Exception {
    return ApiClient.delete("/api/v1/pos/tickets/" + ticketId + "/lines/" + lineId, TicketResponse.class);
  }

  public static TicketResponse cancel(long ticketId) throws Exception {
    return ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/cancel", null, TicketResponse.class);
  }
  public static TicketResponse getById(long ticketId) throws Exception {
    return ApiClient.get("/api/v1/pos/tickets/" + ticketId, TicketResponse.class);
  }

  public static PaymentSummaryResponse paymentSummary(long ticketId) throws Exception {
    return ApiClient.get("/api/v1/pos/tickets/" + ticketId + "/payment-summary", PaymentSummaryResponse.class);
  }

  public static TicketResponse setBillRequested(long ticketId, boolean requested) throws Exception {
    return ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/bill-requested",
        new SetBillRequestedRequest(requested), TicketResponse.class);
  }

  public static TicketResponse moveTable(long ticketId, int tableNumber) throws Exception {
    return ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/move-table",
        new MoveTableRequest(tableNumber), TicketResponse.class);
  }

  public static TicketResponse applyDiscount(long ticketId, Integer percent, Integer amountCents) throws Exception {
    return ApiClient.post("/api/v1/pos/tickets/" + ticketId + "/discount",
        new ApplyDiscountRequest(percent, amountCents), TicketResponse.class);
  }
}
