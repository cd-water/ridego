package com.cdwater.ridego.gateway.security;

import com.cdwater.ridego.auth.JwtCodec;
import com.cdwater.ridego.auth.Principal;
import com.cdwater.ridego.contracts.InternalHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/shared/",
            "/actuator"
    );

    private final JwtCodec jwtCodec;

    public JwtAuthFilter(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        Optional<Principal> principal = jwtCodec.verify(
                authorization.substring("Bearer ".length()));
        if (principal.isEmpty()) {
            return unauthorized(exchange);
        }

        Principal authenticated = principal.get();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(InternalHeaders.USER_ID);
                    headers.remove(InternalHeaders.ROLE);
                    headers.remove(InternalHeaders.DRIVER_ID);
                    headers.set(InternalHeaders.USER_ID, authenticated.userId());
                    headers.set(InternalHeaders.ROLE, authenticated.role());
                    if (authenticated.driverId() != null) {
                        headers.set(InternalHeaders.DRIVER_ID, authenticated.driverId());
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
