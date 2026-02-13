package com.tpv.gateway.config;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

@Controller
public class PdaRedirectController {

    @GetMapping("/pda")
    public Mono<Void> redirectToIndex(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        response.getHeaders().setLocation(URI.create("/pda/index.html"));
        return response.setComplete();
    }

    @GetMapping("/favicon.ico")
    public Mono<Void> redirectFavicon(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        response.getHeaders().setLocation(URI.create("/pda/favicon.svg"));
        return response.setComplete();
    }
}
