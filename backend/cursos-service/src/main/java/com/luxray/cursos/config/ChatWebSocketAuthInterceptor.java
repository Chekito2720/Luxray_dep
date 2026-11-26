package com.luxray.cursos.config;

import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.service.ForoService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Autentica el frame CONNECT de STOMP y valida que el usuario pertenezca a la
 * clase antes de suscribirse o enviar mensajes.
 */
@Component
public class ChatWebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String ATTR_USUARIO_ID = "luxray.userId";
    public static final String ATTR_ROL = "luxray.role";
    public static final String ATTR_NOMBRE = "luxray.name";
    public static final String ATTR_EXPIRA_EN = "luxray.expiresAt";
    public static final String ATTR_TOKEN = "luxray.token";

    private final JwtTokenProvider jwt;
    private final ForoService foro;
    private final ChatSessionRegistry sesiones;

    public ChatWebSocketAuthInterceptor(JwtTokenProvider jwt, ForoService foro,
                                        ChatSessionRegistry sesiones) {
        this.jwt = jwt;
        this.foro = foro;
        this.sesiones = sesiones;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            autenticar(accessor);
            return message;
        }
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            sesiones.eliminarSesion(accessor.getSessionId());
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || StompCommand.SEND.equals(accessor.getCommand())) {
            UUID cursoId = cursoDeDestino(accessor.getCommand(), accessor.getDestination());
            UUID usuarioId = uuidAtributo(accessor, ATTR_USUARIO_ID);
            String rol = stringAtributo(accessor, ATTR_ROL);
            long expiraEn = longAtributo(accessor, ATTR_EXPIRA_EN);
            String token = tokenAtributo(accessor);
            if (cursoId == null || usuarioId == null) {
                throw new AccessDeniedException("No tienes acceso al foro de esta clase");
            }
            if (expiraEn <= System.currentTimeMillis()) {
                throw new AccessDeniedException("La sesión del chat expiró; vuelve a iniciar sesión");
            }
            if (!foro.puedeAcceder(usuarioId, rol, cursoId, token)) {
                throw new AccessDeniedException("No tienes acceso al foro de esta clase");
            }
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                sesiones.registrar(accessor.getSessionId(), cursoId, usuarioId, rol, expiraEn, token);
            }
        }
        return message;
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token de autorización requerido");
        }
        try {
            var claims = jwt.parse(authorization.substring(7));
            String uid = claims.get("uid", String.class);
            if (uid == null || uid.isBlank()) throw new IllegalArgumentException("uid ausente");
            String rol = claims.get("role", String.class);
            if (rol == null || rol.isBlank()) rol = "USER";
            String nombre = claims.get("nombre", String.class);
            if (nombre == null || nombre.isBlank()) nombre = claims.getSubject();
            long expiraEn = claims.getExpiration() == null
                    ? System.currentTimeMillis() + 3_600_000L
                    : claims.getExpiration().getTime();

            accessor.setLeaveMutable(true);
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null) {
                throw new AccessDeniedException("No se pudo iniciar una sesión de chat autenticada");
            }
            UUID usuarioId = UUID.fromString(uid);
            attrs.put(ATTR_USUARIO_ID, usuarioId);
            attrs.put(ATTR_ROL, rol.toUpperCase());
            attrs.put(ATTR_NOMBRE, nombre);
            attrs.put(ATTR_EXPIRA_EN, expiraEn);
            attrs.put(ATTR_TOKEN, authorization);
            // Spring necesita un Principal para resolver destinos /user/queue.
            accessor.setUser(new StompPrincipal(usuarioId.toString()));
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AccessDeniedException("Token inválido o expirado");
        }
    }

    /**
     * Extrae el curso únicamente de los destinos canónicos. Aceptar prefijos
     * laxos permitiría enviar o suscribirse directamente al broker y saltarse
     * la persistencia o el aislamiento por usuario.
     */
    private UUID cursoDeDestino(StompCommand comando, String destination) {
        String prefix = StompCommand.SUBSCRIBE.equals(comando)
                ? "/user/queue/chat/" : "/app/chat/";
        if (destination == null || !destination.startsWith(prefix)) return null;
        String sufijo = destination.substring(prefix.length());
        if (sufijo.isBlank() || sufijo.contains("/")) return null;
        try {
            return UUID.fromString(sufijo);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID uuidAtributo(StompHeaderAccessor accessor, String nombre) {
        Object value = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get(nombre);
        return value instanceof UUID id ? id : null;
    }

    private String stringAtributo(StompHeaderAccessor accessor, String nombre) {
        Object value = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get(nombre);
        return value instanceof String s ? s : "USER";
    }

    private String tokenAtributo(StompHeaderAccessor accessor) {
        Object value = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get(ATTR_TOKEN);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private long longAtributo(StompHeaderAccessor accessor, String nombre) {
        Object value = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get(nombre);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static final class StompPrincipal implements Principal {
        private final String name;

        private StompPrincipal(String name) { this.name = name; }

        @Override
        public String getName() { return name; }
    }
}
