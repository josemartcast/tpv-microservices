package com.tpv.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Locale;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-50)
public class PdaCashGuardFilter implements WebFilter {

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (isBlockedCashOperationFromPda(exchange)) {
            String body = "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Cash session open/close is only allowed from TPV Desktop\"}";
            byte[] bytes = Objects.requireNonNull(body.getBytes(StandardCharsets.UTF_8));
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(toPublisher(dataBuffer));
        }
        return chain.filter(exchange);
    }

    private static @NonNull Publisher<? extends DataBuffer> toPublisher(@NonNull DataBuffer dataBuffer) {
        return Objects.requireNonNull(Mono.just(dataBuffer));
    }

    private static boolean isBlockedCashOperationFromPda(@NonNull ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return false;
        }
        String clientApp = exchange.getRequest().getHeaders().getFirst("X-Client-App");
        if (clientApp == null || !"PDA".equals(clientApp.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }

        String path = exchange.getRequest().getPath().value();
        if ("/api/v1/pos/cash-sessions/open".equals(path)) {
            return true;
        }
        return path.matches("^/api/v1/pos/cash-sessions/[^/]+/close$");
    }
}
