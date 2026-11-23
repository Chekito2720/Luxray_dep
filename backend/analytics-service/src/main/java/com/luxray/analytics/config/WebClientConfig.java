package com.luxray.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * URL del API Gateway. En local es http://localhost:8080; dentro de
     * Docker se sobreescribe con la variable LUXRAY_GATEWAY_URL
     * (p. ej. http://api-gateway:8080).
     */
    @Bean
    WebClient webClient(WebClient.Builder builder,
                        @Value("${luxray.gateway-url:http://localhost:8080}") String gatewayUrl) {
        return builder
                .baseUrl(gatewayUrl)
                .build();
    }
}