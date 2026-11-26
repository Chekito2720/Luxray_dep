package com.luxray.cursos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Canal STOMP del foro. El gateway expone /api/cursos/ws y elimina /api. */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final Set<String> allowedOrigins;
    private final ChatWebSocketAuthInterceptor authInterceptor;

    public ChatWebSocketConfig(
            ChatWebSocketAuthInterceptor authInterceptor,
            @Value("${luxray.chat.allowed-origins:http://localhost:4200}") String configuredOrigins) {
        this.authInterceptor = authInterceptor;
        Set<String> origins = new LinkedHashSet<>();
        origins.add("http://localhost:4200");
        origins.add("http://localhost:4300");
        origins.add("http://127.0.0.1:4200");
        // PWA en la WiFi de la universidad o de casa ( Rangos privados ).
        origins.add("http://10.*:4200");
        origins.add("http://192.168.*:4200");
        if (configuredOrigins != null && !configuredOrigins.isBlank()) {
            Arrays.stream(configuredOrigins.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).forEach(origins::add);
        }
        this.allowedOrigins = origins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // El gateway aplica StripPrefix=1: /api/cursos/ws llega al servicio
        // como /cursos/ws. Se conserva /ws para acceso directo al microservicio.
        registry.addEndpoint("/cursos/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El foro sólo usa destinos canónicos /app/chat/{cursoId} y
        // /user/queue/chat/{cursoId}. No se habilitan topics públicos ni colas
        // compartidas que puedan permitir al cliente eludir la autorización.
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
