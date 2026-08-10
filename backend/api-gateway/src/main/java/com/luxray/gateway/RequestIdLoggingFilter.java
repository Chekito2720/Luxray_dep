package com.luxray.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtro global que:
 *  - Añade un X-Request-Id para trazabilidad.
 *  - Pasa el token Authorization tal cual a los microservicios.
 *  - Añade X-Forwarded-* estándar.
 */
@Component
public class RequestIdLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String requestId = req.getHeaders().getFirst("X-Request-Id");
        if (requestId == null) requestId = UUID.randomUUID().toString();

        ServerHttpRequest mutated = req.mutate()
                .header("X-Request-Id", requestId)
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", req.getURI().getHost())
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() { return -100; }
}
