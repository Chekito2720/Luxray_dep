package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.service.CursoAsignacionService;
import com.luxray.cursos.service.CursoAsignacionService.AsignacionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Asignación de profesores a las clases del catálogo. */
@RestController
@RequestMapping("/cursos/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Profesores por clase", description = "Asignación docente de cursos")
public class CursoProfesoresController {

    private final CursoAsignacionService service;

    public CursoProfesoresController(CursoAsignacionService service) { this.service = service; }

    @GetMapping("/{cursoId}/profesores")
    @Operation(summary = "Ids de los profesores asignados a la clase")
    public ResponseEntity<ApiResponse<List<UUID>>> listar(@PathVariable UUID cursoId) {
        return ResponseEntity.ok(service.listar(cursoId));
    }

    @PutMapping("/{cursoId}/profesores")
    @Operation(summary = "Reemplaza los profesores asignados a la clase")
    public ResponseEntity<ApiResponse<List<UUID>>> reemplazar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID cursoId,
            @RequestBody AsignacionRequest req) {
        return ResponseEntity.ok(service.reemplazar(auth, cursoId, req));
    }
}
