package com.luxray.cursos.service;

import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.model.Leccion;
import com.luxray.cursos.repository.CursoProfesorRepository;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EstadoAsignacionesRepository;
import com.luxray.cursos.repository.InscripcionRepository;
import com.luxray.cursos.repository.LeccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Centraliza la autorización por curso. Un alumno accede a las clases en las
 * que está inscrito; un profesor, a las clases asignadas. Mientras no exista
 * ninguna asignación (instalaciones nuevas), un profesor puede ver todas las
 * clases para que pueda operar el sistema; al asignar al primer profesor, la
 * separación por clase queda activa.
 */
@Service
public class CursoAccesoService {

    private final JwtTokenProvider jwt;
    private final CursoRepository cursos;
    private final LeccionRepository lecciones;
    private final InscripcionRepository inscripciones;
    private final CursoProfesorRepository asignaciones;
    private final EstadoAsignacionesRepository estadoAsignaciones;
    private final UsuarioActualService usuariosActuales;
    private final boolean fallbackProfesorGlobal;

    public CursoAccesoService(JwtTokenProvider jwt,
                              CursoRepository cursos,
                              LeccionRepository lecciones,
                              InscripcionRepository inscripciones,
                              CursoProfesorRepository asignaciones,
                              EstadoAsignacionesRepository estadoAsignaciones,
                              UsuarioActualService usuariosActuales,
                              @Value("${luxray.cursos.fallback-profesor-global:true}")
                              boolean fallbackProfesorGlobal) {
        this.jwt = jwt;
        this.cursos = cursos;
        this.lecciones = lecciones;
        this.inscripciones = inscripciones;
        this.asignaciones = asignaciones;
        this.estadoAsignaciones = estadoAsignaciones;
        this.usuariosActuales = usuariosActuales;
        this.fallbackProfesorGlobal = fallbackProfesorGlobal;
    }

    public record Credenciales(UUID usuarioId, String rol, String nombre, String token) {
        public Credenciales(UUID usuarioId, String rol, String nombre) {
            this(usuarioId, rol, nombre, null);
        }

        public boolean esAdmin() { return "ADMIN".equalsIgnoreCase(rol); }
        public boolean esProfesor() { return "PROFESOR".equalsIgnoreCase(rol); }
        public boolean esAlumno() { return "USER".equalsIgnoreCase(rol); }
    }

    public Credenciales credenciales(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("Token de autorización requerido");
        }
        try {
            var claims = jwt.parse(authHeader.substring(7));
            String uid = claims.get("uid", String.class);
            if (uid == null || uid.isBlank()) throw new IllegalArgumentException("uid ausente");
            String rol = claims.get("role", String.class);
            if (rol == null || rol.isBlank()) rol = "USER";
            String nombre = claims.get("nombre", String.class);
            if (nombre == null || nombre.isBlank()) nombre = claims.getSubject();
            return new Credenciales(UUID.fromString(uid), rol.toUpperCase(), nombre,
                    authHeader);
        } catch (Exception ex) {
            throw new BusinessException("Token inválido o expirado");
        }
    }

    @Transactional(readOnly = true)
    public Leccion leccion(UUID leccionId) {
        return lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lección no encontrada: " + leccionId));
    }

    @Transactional(readOnly = true)
    public void requiereAlumnoInscrito(Credenciales cred, UUID leccionId) {
        validarEstadoActual(cred);
        Leccion leccion = leccion(leccionId);
        if (!cred.esAlumno()) {
            throw new AccessDeniedException("Solo un alumno puede entregar una práctica");
        }
        if (inscripciones.findByUsuarioIdAndCursoId(cred.usuarioId(), leccion.getCursoId()).isEmpty()) {
            throw new AccessDeniedException("No estás inscrito en esta clase");
        }
    }

    @Transactional(readOnly = true)
    public void requiereAccesoCurso(Credenciales cred, UUID cursoId) {
        validarEstadoActual(cred);
        if (!cursos.existsById(cursoId)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        if (cred.esAdmin()) return;
        if (cred.esProfesor()) {
            if (puedeGestionarCurso(cred.usuarioId(), cursoId)) return;
            throw new AccessDeniedException("No gestionas esta clase");
        }
        if (inscripciones.findByUsuarioIdAndCursoId(cred.usuarioId(), cursoId).isPresent()) return;
        throw new AccessDeniedException("No tienes acceso a esta clase");
    }

    @Transactional(readOnly = true)
    public void requiereGestionLeccion(Credenciales cred, UUID leccionId) {
        Leccion leccion = leccion(leccionId);
        requiereGestionCurso(cred, leccion.getCursoId());
    }

    @Transactional(readOnly = true)
    public void requiereGestionCurso(Credenciales cred, UUID cursoId) {
        validarEstadoActual(cred);
        if (!cursos.existsById(cursoId)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        if (cred.esAdmin()) return;
        if (cred.esProfesor() && puedeGestionarCurso(cred.usuarioId(), cursoId)) return;
        throw new AccessDeniedException("No gestionas esta clase");
    }

    @Transactional(readOnly = true)
    public boolean puedeGestionarCurso(UUID profesorId, UUID cursoId) {
        if (asignaciones.existsByCursoIdAndProfesorId(cursoId, profesorId)) return true;
        // Compatibilidad de arranque: el acceso global sólo existe mientras
        // nadie haya configurado asignaciones. El indicador es persistente,
        // así que borrar la última asignación no lo reactiva.
        return fallbackGlobalDisponible();
    }

    private boolean fallbackGlobalDisponible() {
        if (!fallbackProfesorGlobal) return false;
        return !estadoAsignaciones.estaInicializado();
    }

    @Transactional(readOnly = true)
    public Set<UUID> cursosPermitidos(Credenciales cred) {
        validarEstadoActual(cred);
        if (cred.esAdmin()) {
            return new HashSet<>(cursos.findAll().stream().map(c -> c.getId()).toList());
        }
        if (cred.esProfesor()) {
            List<UUID> asignados = asignaciones.findByProfesorId(cred.usuarioId())
                    .stream().map(a -> a.getCursoId()).toList();
            if (!asignados.isEmpty()) return new HashSet<>(asignados);
            if (fallbackGlobalDisponible()) {
                return new HashSet<>(cursos.findAll().stream().map(c -> c.getId()).toList());
            }
            return Set.of();
        }
        return new HashSet<>(inscripciones.findByUsuarioId(cred.usuarioId())
                .stream().map(i -> i.getCursoId()).toList());
    }

    /**
     * Revalida contra auth-service que la cuenta siga activa y conserve el
     * rol emitido en el token. Las credenciales construidas internamente
     * sin token (por ejemplo en pruebas unitarias) no hacen llamada remota.
     */
    private void validarEstadoActual(Credenciales cred) {
        if (cred == null || cred.token() == null || cred.token().isBlank()) return;
        UsuarioActualService.UsuarioActual actual = usuariosActuales.consultar(cred.token());
        if (!actual.activo()
                || !cred.usuarioId().equals(actual.id())
                || !cred.rol().equalsIgnoreCase(actual.rol())) {
            throw new AccessDeniedException("La cuenta ya no está activa o su rol cambió");
        }
    }
}
