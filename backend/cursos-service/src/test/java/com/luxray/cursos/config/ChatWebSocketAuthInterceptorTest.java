package com.luxray.cursos.config;

import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.service.ForoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatWebSocketAuthInterceptorTest {

    private static final UUID CURSO = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private ForoService foro;
    private ChatSessionRegistry sesiones;
    private ChatWebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        foro = mock(ForoService.class);
        sesiones = new ChatSessionRegistry();
        interceptor = new ChatWebSocketAuthInterceptor(
                mock(JwtTokenProvider.class), foro, sesiones);
        when(foro.puedeAcceder(USUARIO, "USER", CURSO, "Bearer token"))
                .thenReturn(true);
    }

    @Test
    void aceptaLaSuscripcionCanonicaYRegistraLaSesion() {
        Message<byte[]> message = mensaje(StompCommand.SUBSCRIBE,
                "/user/queue/chat/" + CURSO);

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));

        var participantes = sesiones.participantes(CURSO);
        assertEquals(1, participantes.size());
        assertEquals(USUARIO, participantes.get(0).usuarioId());
        assertTrue(participantes.get(0).expiraEn() > System.currentTimeMillis());
    }

    @Test
    void aceptaElEnvioCanonicaALaAplicacion() {
        Message<byte[]> message = mensaje(StompCommand.SEND,
                "/app/chat/" + CURSO);

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void rechazaDestinosDelBrokerYDestinosDeOtroUsuario() {
        String[] destinosProhibidos = {
                "/topic/chat/" + CURSO,
                "/queue/chat/" + CURSO,
                "/user/otro-usuario/queue/chat/" + CURSO,
                "/user/queue/chat/" + CURSO + "/extra",
                "/app/chat/" + CURSO + "/extra"
        };

        for (String destino : destinosProhibidos) {
            Message<byte[]> message = mensaje(StompCommand.SUBSCRIBE, destino);
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preSend(message, mock(MessageChannel.class)),
                    "Debe rechazar el destino " + destino);
        }
    }

    @Test
    void rechazaEnviosDirectosAlBroker() {
        Message<byte[]> message = mensaje(StompCommand.SEND,
                "/topic/chat/" + CURSO);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    private Message<byte[]> mensaje(StompCommand comando, String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        accessor.setSessionId("sesion-" + comando.name());
        accessor.setDestination(destino);
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(ChatWebSocketAuthInterceptor.ATTR_USUARIO_ID, USUARIO);
        atributos.put(ChatWebSocketAuthInterceptor.ATTR_ROL, "USER");
        atributos.put(ChatWebSocketAuthInterceptor.ATTR_NOMBRE, "Alumno Prueba");
        atributos.put(ChatWebSocketAuthInterceptor.ATTR_EXPIRA_EN,
                System.currentTimeMillis() + 60_000L);
        atributos.put(ChatWebSocketAuthInterceptor.ATTR_TOKEN, "Bearer token");
        accessor.setSessionAttributes(atributos);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
