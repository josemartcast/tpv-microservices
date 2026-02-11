package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import com.tpv.desktop.core.SettingsStore;

public final class SalonApi {
    private SalonApi() {
    }

    public static SalonTableResponse[] tables() throws Exception {
        return ApiClient.get("/api/v1/pos/salon/tables", SalonTableResponse[].class);
    }

    public static TicketResponse openTicket(int tableNumber) throws Exception {
        return ApiClient.post("/api/v1/pos/salon/tables/" + tableNumber + "/open-ticket", null, TicketResponse.class);
    }

    public static TableLockResponse lockTable(int tableNumber) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/salon/tables/" + tableNumber + "/lock",
                new TableLockRequest(SettingsStore.getTerminalId()),
                TableLockResponse.class
        );
    }

    public static TableLockResponse heartbeatTable(int tableNumber) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/salon/tables/" + tableNumber + "/heartbeat",
                new TableLockRequest(SettingsStore.getTerminalId()),
                TableLockResponse.class
        );
    }

    public static void unlockTable(int tableNumber) throws Exception {
        ApiClient.post(
                "/api/v1/pos/salon/tables/" + tableNumber + "/unlock",
                new TableLockRequest(SettingsStore.getTerminalId()),
                Void.class
        );
    }
}
