package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;
import java.util.Arrays;
import java.util.List;

public final class SalonAdminApi {
    private SalonAdminApi() {}

    public static List<SalonAreaResponse> list() throws Exception {
        SalonAreaResponse[] response = ApiClient.get("/api/v1/pos/admin/salons", SalonAreaResponse[].class);
        return Arrays.asList(response == null ? new SalonAreaResponse[0] : response);
    }

    public static SalonAreaResponse create(String name, int tableCount, Integer firstTableNumber) throws Exception {
        return ApiClient.post(
                "/api/v1/pos/admin/salons",
                new CreateSalonAreaRequest(name, tableCount, firstTableNumber),
                SalonAreaResponse.class
        );
    }

    public static SalonAreaResponse rename(long id, String name) throws Exception {
        return ApiClient.put(
                "/api/v1/pos/admin/salons/" + id,
                new UpdateSalonAreaRequest(name),
                SalonAreaResponse.class
        );
    }

    public static void delete(long id) throws Exception {
        ApiClient.delete("/api/v1/pos/admin/salons/" + id, Void.class);
    }

    public static java.util.List<TableAliasResponse> listTableAliases(long salonId) throws Exception {
        TableAliasResponse[] response = ApiClient.get(
                "/api/v1/pos/admin/salons/" + salonId + "/table-aliases",
                TableAliasResponse[].class
        );
        return response == null ? java.util.List.of() : java.util.Arrays.asList(response);
    }

    public static TableAliasResponse updateTableAlias(long salonId, int tableNumber, String alias) throws Exception {
        return ApiClient.put(
                "/api/v1/pos/admin/salons/" + salonId + "/tables/" + tableNumber + "/alias",
                new UpdateTableAliasRequest(alias),
                TableAliasResponse.class
        );
    }
}
