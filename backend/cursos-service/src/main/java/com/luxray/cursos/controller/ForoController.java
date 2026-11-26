package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.config.ChatWebSocketAuthInterceptor;
import com.luxray.cursos.service.CursoAccesoService.Credenciales;
import com.luxray.cursos.service.ForoBroadcastService;
import com.luxray.cursos.service.ForoService;
import com.luxray.cursos.service.ForoService.MensajeRequest;
import com.luxray.cursos.service.ForoService.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Foro persistente y canal en tiempo real para cada clase/curso. */
@RestController
@Tag(name = "Foro por curso", description = "Conversación en tiempo real de alumnos y profesores")
public class ForoController {

    private final ForoService foro;
    private final ForoBroadcastService broadcast;

    public ForoController(ForoService foro, ForoBroadcastService broadcast) {
        this.foro = foro;
        this.broadcast = broadcast;
    }

    @GetMapping("/cursos/{cursoId}/foro")
    @Operation(summary = "Últimos mensajes del canal de una clase")
    public ResponseEntity<ApiResponse<List<MensajeResponse>>> listar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID cursoId) {
        return ResponseEntity.ok(foro.listar(auth, cursoId));
    }

    @PostMapping("/cursos/{cursoId}/foro")
    @Operation(summary = "Publica un mensaje (fallback REST del canal en tiempo real)")
    public ResponseEntity<ApiResponse<MensajeResponse>> crear(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID cursoId,
            @RequestBody MensajeRequest req) {
        var creado = foro.crear(auth, cursoId, req);
        broadcast.difundir(cursoId, creado.getData());
        return ResponseEntity.ok(creado);
    }

    @MessageMapping("/chat/{cursoId}")
    public void enviar(@DestinationVariable UUID cursoId,
                        MensajeRequest payload,
                        SimpMessageHeaderAccessor headers) {
        Map<String, Object> attrs = headers.getSessionAttributes();
        UUID usuarioId = attrs == null ? null : (UUID) attrs.get(ChatWebSocketAuthInterceptor.ATTR_USUARIO_ID);
        String rol = attrs == null ? "USER" : (String) attrs.get(ChatWebSocketAuthInterceptor.ATTR_ROL);
        String nombre = attrs == null ? "Participante" : (String) attrs.get(ChatWebSocketAuthInterceptor.ATTR_NOMBRE);
        String token = attrs == null ? null : (String) attrs.get(ChatWebSocketAuthInterceptor.ATTR_TOKEN);
        if (usuarioId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Sesión de chat no autenticada");
        }
        MensajeResponse mensaje = foro.crear(new Credenciales(usuarioId, rol, nombre, token),
                cursoId, payload == null ? null : payload.contenido());
        broadcast.difundir(cursoId, mensaje);
    }
}
