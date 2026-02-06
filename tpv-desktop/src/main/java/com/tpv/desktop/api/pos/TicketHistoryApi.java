package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class TicketHistoryApi {
  private TicketHistoryApi() {}

  public static TicketResponse[] listOpen() throws Exception {

    return ApiClient.get("/api/v1/pos/tickets/open", TicketResponse[].class);
  }

  public static TicketSummaryResponse summary(long ticketId) throws Exception {
    return ApiClient.get("/api/v1/pos/tickets/" + ticketId + "/summary", TicketSummaryResponse.class);
  }
}
