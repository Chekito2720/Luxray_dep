package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.service.EvidenciasService;
import com.luxray.cursos.service.EvidenciasService.CriterioConfigRequest;
import com.luxray.cursos.service.EvidenciasService.ListaCotejoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Lista de cotejo configurable y consultable por actividad. */
@RestController
@RequestMapping("/cursos/lecciones/{leccionId}/lista-cotejo")
@Tag(name = "Listas de cotejo", description = "Criterios y ponderación de cada actividad")
public class ListasCotejoController {

    private final EvidenciasService service;

    public ListasCotejoController(EvidenciasService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Lista de cotejo visible por alumnos inscritos y profesores de la clase")
    public ResponseEntity<ApiResponse<List<ListaCotejoResponse>>> obtener(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId) {
        return ResponseEntity.ok(service.obtenerListaCotejo(auth, leccionId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('PROFESOR','ADMIN')")
    @Operation(summary = "Crea o reemplaza la lista de cotejo de una actividad")
    public ResponseEntity<ApiResponse<List<ListaCotejoResponse>>> guardar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId,
            @RequestBody List<CriterioConfigRequest> req) {
        return ResponseEntity.ok(service.guardarListaCotejo(auth, leccionId, req));
    }
}
