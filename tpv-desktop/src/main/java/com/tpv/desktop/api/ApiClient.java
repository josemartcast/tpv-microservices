package com.tpv.desktop.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.SettingsStore;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class ApiClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper MAPPER =
            new ObjectMapper().findAndRegisterModules();

    private static URI uri(String path) {
        return URI.create(SettingsStore.getApiBaseUrl() + path);
    }

    public static <T> T post(String path, Object body, Class<T> responseType) throws Exception {
        String json = body == null ? "" : MAPPER.writeValueAsString(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        addAuth(b);

        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T get(String path, Class<T> responseType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();

        addAuth(b);

        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T patch(String path, Object body, Class<T> responseType) throws Exception {
        String json = body == null ? "" : MAPPER.writeValueAsString(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json));

        addAuth(b);

        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T delete(String path, Class<T> responseType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .DELETE();

        addAuth(b);

        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    private static void addAuth(HttpRequest.Builder b) {
        String token = AuthStore.getToken();
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
    }

    private static void handleError(HttpResponse<String> res) {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new ApiException(res.statusCode(), res.body());
        }
    }

    public static class ApiException extends RuntimeException {
        private final int status;
        private final String body;

        public ApiException(int status, String body) {
            super("HTTP " + status + " -> " + body);
            this.status = status;
            this.body = body;
        }

        public int getStatus() { return status; }
        public String getBody() { return body; }
    }
}
