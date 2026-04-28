package com.tpv.desktop.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.SettingsStore;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class ApiClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper MAPPER =
            new ObjectMapper().findAndRegisterModules();
    private static volatile String rememberedUser = "";
    private static volatile String rememberedPass = "";

    private static URI uri(String path) {
        return URI.create(SettingsStore.getApiBaseUrl() + path);
    }

    public static synchronized void rememberCredentials(String username, String password) {
        rememberedUser = username == null ? "" : username.trim();
        rememberedPass = password == null ? "" : password;
    }

    public static synchronized void clearRememberedCredentials() {
        rememberedUser = "";
        rememberedPass = "";
    }

    public static <T> T post(String path, Object body, Class<T> responseType) throws Exception {
        return post(path, body, responseType, null);
    }

    public static <T> T post(String path, Object body, Class<T> responseType, Map<String, String> extraHeaders) throws Exception {
        String json = body == null ? "" : MAPPER.writeValueAsString(body);

        HttpResponse<String> res = HTTP.send(buildPost(path, json, extraHeaders), HttpResponse.BodyHandlers.ofString());
        if (isAuthRecoverable(path, res.statusCode()) && tryRecoverToken()) {
            res = HTTP.send(buildPost(path, json, extraHeaders), HttpResponse.BodyHandlers.ofString());
        }
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T get(String path, Class<T> responseType) throws Exception {
        HttpResponse<String> res = HTTP.send(buildGet(path), HttpResponse.BodyHandlers.ofString());
        if (isAuthRecoverable(path, res.statusCode()) && tryRecoverToken()) {
            res = HTTP.send(buildGet(path), HttpResponse.BodyHandlers.ofString());
        }
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T patch(String path, Object body, Class<T> responseType) throws Exception {
        String json = body == null ? "" : MAPPER.writeValueAsString(body);

        HttpResponse<String> res = HTTP.send(buildPatch(path, json), HttpResponse.BodyHandlers.ofString());
        if (isAuthRecoverable(path, res.statusCode()) && tryRecoverToken()) {
            res = HTTP.send(buildPatch(path, json), HttpResponse.BodyHandlers.ofString());
        }
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T put(String path, Object body, Class<T> responseType) throws Exception {
        String json = body == null ? "" : MAPPER.writeValueAsString(body);

        HttpResponse<String> res = HTTP.send(buildPut(path, json), HttpResponse.BodyHandlers.ofString());
        if (isAuthRecoverable(path, res.statusCode()) && tryRecoverToken()) {
            res = HTTP.send(buildPut(path, json), HttpResponse.BodyHandlers.ofString());
        }
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    public static <T> T delete(String path, Class<T> responseType) throws Exception {
        HttpResponse<String> res = HTTP.send(buildDelete(path), HttpResponse.BodyHandlers.ofString());
        if (isAuthRecoverable(path, res.statusCode()) && tryRecoverToken()) {
            res = HTTP.send(buildDelete(path), HttpResponse.BodyHandlers.ofString());
        }
        handleError(res);

        return responseType == Void.class ? null : MAPPER.readValue(res.body(), responseType);
    }

    private static HttpRequest buildPost(String path, String json, Map<String, String> extraHeaders) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        addAuth(b);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    b.header(e.getKey(), e.getValue());
                }
            }
        }
        return b.build();
    }

    private static HttpRequest buildGet(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        addAuth(b);
        return b.build();
    }

    private static HttpRequest buildPatch(String path, String json) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json));
        addAuth(b);
        return b.build();
    }

    private static HttpRequest buildPut(String path, String json) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json));
        addAuth(b);
        return b.build();
    }

    private static HttpRequest buildDelete(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .DELETE();
        addAuth(b);
        return b.build();
    }

    private static void addAuth(HttpRequest.Builder b) {
        String token = AuthStore.getToken();
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        b.header("X-Terminal-Id", SettingsStore.getTerminalId());
    }

    private static void handleError(HttpResponse<String> res) {
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new ApiException(res.statusCode(), res.body());
        }
    }

    private static boolean isAuthRecoverable(String path, int statusCode) {
        if (statusCode != 401 && statusCode != 403) return false;
        return path == null || !path.startsWith("/api/v1/auth/login");
    }

    private static synchronized boolean tryRecoverToken() {
        String user = rememberedUser == null ? "" : rememberedUser.trim();
        String pass = rememberedPass == null ? "" : rememberedPass;
        if (user.isBlank() || pass.isBlank()) {
            user = readConfig("TPV_AUTH_USER", "tpv.auth.user", "");
            pass = readConfig("TPV_AUTH_PASS", "tpv.auth.pass", "");
        }
        if (user.isBlank() || pass.isBlank()) {
            return false;
        }
        try {
            String loginJson = MAPPER.writeValueAsString(Map.of("username", user, "password", pass));
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(uri("/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                    .build();
            HttpResponse<String> loginRes = HTTP.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            if (loginRes.statusCode() < 200 || loginRes.statusCode() >= 300) {
                return false;
            }
            JsonNode root = MAPPER.readTree(loginRes.body());
            String token = root.hasNonNull("accessToken") ? root.get("accessToken").asText() : null;
            if (token == null || token.isBlank()) {
                return false;
            }
            AuthStore.setToken(token);
            rememberCredentials(user, pass);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readConfig(String env, String prop, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            value = System.getProperty(prop);
        }
        return (value == null || value.isBlank()) ? fallback : value.trim();
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
