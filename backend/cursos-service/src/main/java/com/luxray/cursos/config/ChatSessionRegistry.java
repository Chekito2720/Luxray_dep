package com.luxray.cursos.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de suscripciones STOMP por sesión y clase. Permite revalidar el
 * acceso antes de cada difusión para que una asignación o inscripción
 * revocada no siga recibiendo mensajes.
 */
@Component
public class ChatSessionRegistry {

    public record Participante(UUID usuarioId, String rol, long expiraEn, String token) { }

    private record Suscripcion(UUID cursoId, Participante participante) { }

    private final Map<String, Map<UUID, Suscripcion>> porSesion = new ConcurrentHashMap<>();

    public void registrar(String sessionId, UUID cursoId, UUID usuarioId,
                          String rol, long expiraEn, String token) {
        if (sessionId == null || cursoId == null || usuarioId == null) return;
        porSesion.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(cursoId, new Suscripcion(cursoId,
                        new Participante(usuarioId, rol, expiraEn, token)));
    }

    public void eliminarSesion(String sessionId) {
        if (sessionId != null) porSesion.remove(sessionId);
    }

    /** Participantes suscritos a una clase, sin duplicar usuarios. */
    public List<Participante> participantes(UUID cursoId) {
        Map<UUID, Participante> porUsuario = new LinkedHashMap<>();
        for (Map<UUID, Suscripcion> suscripciones : porSesion.values()) {
            Suscripcion suscripcion = suscripciones.get(cursoId);
            if (suscripcion == null) continue;
            Participante participante = suscripcion.participante();
            if (participante == null) continue;
            Participante anterior = porUsuario.get(participante.usuarioId());
            if (anterior == null || participante.expiraEn() > anterior.expiraEn()) {
                porUsuario.put(participante.usuarioId(), participante);
            }
        }
        return new ArrayList<>(porUsuario.values());
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent evento) {
        eliminarSesion(evento.getSessionId());
    }
}
