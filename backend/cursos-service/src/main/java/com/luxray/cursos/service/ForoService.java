package com.luxray.cursos.service;

import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.cursos.model.MensajeForo;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.MensajeForoRepository;
import com.luxray.cursos.service.CursoAccesoService.Credenciales;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Persistencia y autorización del foro en tiempo real, un canal por curso. */
@Service
public class ForoService {

    private static final int MAX_MENSAJE = 2000;

    private final MensajeForoRepository mensajes;
    private final CursoRepository cursos;
    private final CursoAccesoService acceso;

    public ForoService(MensajeForoRepository mensajes,
                       CursoRepository cursos,
                       CursoAccesoService acceso) {
        this.mensajes = mensajes;
        this.cursos = cursos;
        this.acceso = acceso;
    }

    public record MensajeRequest(String contenido) { }

    public record MensajeResponse(UUID id, UUID cursoId, UUID usuarioId,
                                  String autorNombre, String autorRol,
                                  String contenido, String creadoEn) { }

    @Transactional(readOnly = true)
    public ApiResponse<List<MensajeResponse>> listar(String authHeader, UUID cursoId) {
        Credenciales cred = acceso.credenciales(authHeader);
        acceso.requiereAccesoCurso(cred, cursoId);
        List<MensajeForo> ultimos = mensajes.findTop100ByCursoIdOrderByCreadoEnDesc(cursoId);
        return ApiResponse.ok(ultimos.reversed().stream().map(this::aRespuesta).toList());
    }

    @Transactional
    public ApiResponse<MensajeResponse> crear(String authHeader, UUID cursoId,
                                               MensajeRequest req) {
        Credenciales cred = acceso.credenciales(authHeader);
        return ApiResponse.ok(crear(cred, cursoId, req == null ? null : req.contenido()));
    }

    @Transactional
    public MensajeResponse crear(Credenciales cred, UUID cursoId, String contenido) {
        acceso.requiereAccesoCurso(cred, cursoId);
        if (!cursos.existsById(cursoId)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        String texto = contenido == null ? "" : contenido.trim();
        if (texto.isEmpty()) throw new BusinessException("El mensaje no puede estar vacío");
        if (texto.length() > MAX_MENSAJE) {
            throw new BusinessException("El mensaje supera el máximo de " + MAX_MENSAJE + " caracteres");
        }

        MensajeForo mensaje = new MensajeForo();
        mensaje.setCursoId(cursoId);
        mensaje.setUsuarioId(cred.usuarioId());
        mensaje.setAutorNombre(cred.nombre() == null || cred.nombre().isBlank()
                ? "Participante" : cred.nombre());
        mensaje.setAutorRol(cred.rol());
        mensaje.setContenido(texto);
        mensaje.setCreadoEn(LocalDateTime.now());
        return aRespuesta(mensajes.save(mensaje));
    }

    public boolean puedeAcceder(UUID usuarioId, String rol, UUID cursoId) {
        return puedeAcceder(usuarioId, rol, cursoId, null);
    }

    public boolean puedeAcceder(UUID usuarioId, String rol, UUID cursoId, String token) {
        try {
            acceso.requiereAccesoCurso(
                    new Credenciales(usuarioId, rol, "Participante", token), cursoId);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private MensajeResponse aRespuesta(MensajeForo m) {
        return new MensajeResponse(m.getId(), m.getCursoId(), m.getUsuarioId(),
                m.getAutorNombre(), m.getAutorRol(), m.getContenido(),
                m.getCreadoEn() == null ? null : m.getCreadoEn().toString());
    }
}
