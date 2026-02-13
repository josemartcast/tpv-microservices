package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class BusinessProfileApi {
    private BusinessProfileApi() {}

    public static BusinessProfileResponse get() throws Exception {
        return ApiClient.get("/api/v1/pos/business-profile", BusinessProfileResponse.class);
    }

    public static BusinessProfileResponse update(UpdateBusinessProfileRequest request) throws Exception {
        return ApiClient.put("/api/v1/pos/business-profile", request, BusinessProfileResponse.class);
    }
}
