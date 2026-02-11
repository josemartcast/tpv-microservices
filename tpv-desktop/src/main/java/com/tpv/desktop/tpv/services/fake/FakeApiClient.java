package com.tpv.desktop.tpv.services.fake;

import com.tpv.desktop.tpv.services.ApiClient;

import java.util.Random;

public class FakeApiClient implements ApiClient {
    private final Random random = new Random();

    @Override
    public void ping() throws Exception {
        int latency = 20 + random.nextInt(180);
        Thread.sleep(latency);
        int r = random.nextInt(100);
        if (r < 8) {
            throw new RuntimeException("Gateway timeout");
        }
        if (r < 12) {
            throw new RuntimeException("Connection refused");
        }
    }
}

