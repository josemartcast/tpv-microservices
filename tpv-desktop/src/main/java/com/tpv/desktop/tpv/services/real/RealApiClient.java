package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.tpv.services.ApiClient;

public class RealApiClient implements ApiClient {
    @Override
    public void ping() throws Exception {
        SalonApi.tables();
    }
}

