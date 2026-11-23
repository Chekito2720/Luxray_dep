package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.service.EvidenciasService;
import com.luxray.cursos.service.EvidenciasService.EvidenciaDetalleResponse;
import com.luxray.cursos.service.EvidenciasService.RevisionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Módulo de revisión docente: el profesor recibe las evidencias de práctica
 * subidas por los alumnos desde la app móvil, las revisa y marca la práctica
 * como completada (APROBADA) o la devuelve con retroalimentación (RECHAZADA).
 *
 * Acceso exclusivo de PROFESOR y ADMIN (el rol se resuelve del claim `role`
 * del JWT en JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/cursos")
@Tag(name = "Revisión docente", description = "Recepción, revisión y retroalimentación de evidencias de práctica")
public class RevisionesController {

    private final EvidenciasService service;

    public RevisionesController(EvidenciasService service) { this.service = service; }

    @GetMapping("/revisiones")
    @PreAuthorize("hasAnyRole('PROFESOR','ADMIN')")
    @Operation(summary = "Cola de evidencias para revisar — solo PROFESOR/ADMIN (filtro opcional por estado)")
    public ResponseEntity<ApiResponse<List<EvidenciaDetalleResponse>>> listar(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(service.listarParaRevision(estado));
    }

    @PostMapping("/revisiones/{evidenciaId}")
    @PreAuthorize("hasAnyRole('PROFESOR','ADMIN')")
    @Operation(summary = "Registra la revisión: APROBADA (práctica completada) o RECHAZADA con retroalimentación")
    public ResponseEntity<ApiResponse<EvidenciaDetalleResponse>> revisar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID evidenciaId,
            @RequestBody RevisionRequest req) {
        return ResponseEntity.ok(service.revisar(auth, evidenciaId, req));
    }

    @GetMapping("/expediente")
    @Operation(summary = "Expediente del alumno autenticado: todas sus evidencias con la retroalimentación del profesor")
    public ResponseEntity<ApiResponse<List<EvidenciaDetalleResponse>>> expediente(
            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(service.expediente(auth));
    }
}
