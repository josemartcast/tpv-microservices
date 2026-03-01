package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class CustomerApi {
    private CustomerApi() {
    }

    public static CustomerResponse[] list() throws Exception {
        return ApiClient.get("/api/v1/pos/customers", CustomerResponse[].class);
    }

    public static CustomerResponse get(long id) throws Exception {
        return ApiClient.get("/api/v1/pos/customers/" + id, CustomerResponse.class);
    }

    public static CustomerResponse create(CreateCustomerRequest request) throws Exception {
        return ApiClient.post("/api/v1/pos/customers", request, CustomerResponse.class);
    }

    public static CustomerResponse update(long id, UpdateCustomerRequest request) throws Exception {
        return ApiClient.put("/api/v1/pos/customers/" + id, request, CustomerResponse.class);
    }

    public static void delete(long id) throws Exception {
        ApiClient.delete("/api/v1/pos/customers/" + id, Void.class);
    }
}
