package com.luxray.cursos.service;

import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.cursos.model.CursoProfesor;
import com.luxray.cursos.repository.CursoProfesorRepository;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EstadoAsignacionesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Administra qué profesores pueden gestionar cada clase/curso. */
@Service
public class CursoAsignacionService {

    private final CursoProfesorRepository asignaciones;
    private final CursoRepository cursos;
    private final EstadoAsignacionesRepository estadoAsignaciones;
    private final AdvisoryLockService locks;
    private final RestClient authClient;

    public CursoAsignacionService(
            CursoProfesorRepository asignaciones,
            CursoRepository cursos,
            EstadoAsignacionesRepository estadoAsignaciones,
            AdvisoryLockService locks,
            RestClient.Builder restClientBuilder,
            @Value("${luxray.auth.url:http://localhost:8081}") String authUrl) {
        this.asignaciones = asignaciones;
        this.cursos = cursos;
        this.estadoAsignaciones = estadoAsignaciones;
        this.locks = locks;
        this.authClient = restClientBuilder.baseUrl(authUrl).build();
    }

    public record AsignacionRequest(List<UUID> profesorIds) { }

    @Transactional(readOnly = true)
    public ApiResponse<List<UUID>> listar(UUID cursoId) {
        if (!cursos.existsById(cursoId)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        return ApiResponse.ok(asignaciones.findByCursoId(cursoId).stream()
                .map(CursoProfesor::getProfesorId).toList());
    }

    @Transactional
    public ApiResponse<List<UUID>> reemplazar(String authHeader, UUID cursoId,
                                                 AsignacionRequest req) {
        if (!cursos.existsById(cursoId)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        List<UUID> ids = req == null || req.profesorIds() == null ? List.of() : req.profesorIds();
        if (ids.size() > 100) {
            throw new BusinessException("No se pueden asignar más de 100 profesores a una clase");
        }
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException("La lista de profesores contiene un identificador vacío");
        }
        List<UUID> unicos = ids.stream().distinct().toList();
        // Serializa el reemplazo por curso: dos administradores no pueden
        // mezclar conjuntos al leer y escribir las asignaciones actuales.
        locks.bloquearAsignaciones(cursoId);
        validarProfesoresActivos(authHeader, unicos);

        // Se actualiza sólo el diferencial. Borrar y reinsertar toda la lista
        // puede provocar un conflicto con la clave única durante el flush de
        // Hibernate cuando una asignación se conserva.
        List<CursoProfesor> actuales = asignaciones.findByCursoId(cursoId);
        Set<UUID> actualesIds = actuales.stream()
                .map(CursoProfesor::getProfesorId)
                .collect(Collectors.toSet());
        Set<UUID> nuevosIds = new HashSet<>(unicos);

        for (CursoProfesor actual : actuales) {
            if (!nuevosIds.contains(actual.getProfesorId())) {
                asignaciones.delete(actual);
            }
        }
        for (UUID profesorId : nuevosIds) {
            if (!actualesIds.contains(profesorId)) {
                CursoProfesor asignacion = new CursoProfesor();
                asignacion.setCursoId(cursoId);
                asignacion.setProfesorId(profesorId);
                asignaciones.save(asignacion);
            }
        }
        // Guardar cualquier configuración (incluso vacía) cierra el modo de
        // compatibilidad: borrar después la última asignación no lo reabre.
        estadoAsignaciones.marcarInicializado();
        return ApiResponse.ok(unicos);
    }

    private record UsuarioValidado(UUID id, String rol, Boolean activo) { }

    private void validarProfesoresActivos(String authHeader, List<UUID> ids) {
        if (ids.isEmpty()) return;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("Token de autorización requerido");
        }

        final String idsCsv = ids.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("");
        try {
            ApiResponse<List<UsuarioValidado>> response = authClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/auth/usuarios")
                            .queryParam("ids", idsCsv)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException("No se pudo validar la lista de profesores");
            }

            Set<UUID> validos = new HashSet<>();
            for (UsuarioValidado usuario : response.getData()) {
                if (usuario != null && Boolean.TRUE.equals(usuario.activo())
                        && "PROFESOR".equalsIgnoreCase(usuario.rol())
                        && usuario.id() != null) {
                    validos.add(usuario.id());
                }
            }
            long invalidos = ids.stream().filter(id -> !validos.contains(id)).count();
            if (invalidos > 0) {
                throw new BusinessException(
                        "Uno o más usuarios no existen, están inactivos o no tienen el rol Profesor");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException("No se pudo validar la lista de profesores con el servicio de usuarios");
        }
    }
}
