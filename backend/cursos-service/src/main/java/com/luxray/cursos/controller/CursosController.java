package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.dto.BusquedaResponse;
import com.luxray.cursos.dto.CursoRequest;
import com.luxray.cursos.dto.CursoResponse;
import com.luxray.cursos.dto.InscripcionResponse;
import com.luxray.cursos.service.CursosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cursos")
@Tag(name = "Cursos", description = "Catálogo, detalle, inscripción y progreso")
public class CursosController {

    private final CursosService service;

    public CursosController(CursosService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Lista pública de cursos con filtros opcionales")
    public ResponseEntity<ApiResponse<List<CursoResponse>>> listar(
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.listar(nivel, q));
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscador interno del sitio: cursos, lecciones, conceptos y FAQ del giro eléctrico")
    public ResponseEntity<ApiResponse<BusquedaResponse>> buscar(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.buscar(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle completo del curso con sus lecciones")
    public ResponseEntity<ApiResponse<CursosService.CursoDetalleResponse>> detalle(@PathVariable UUID id) {
        return ResponseEntity.ok(service.detalle(id));
    }

    @PostMapping("/{id}/inscripcion")
    @Operation(summary = "Inscribir al usuario autenticado en el curso")
    public ResponseEntity<ApiResponse<InscripcionResponse>> inscribir(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.inscribir(auth, id));
    }

    @GetMapping("/mis-cursos")
    @Operation(summary = "Cursos inscritos del usuario autenticado")
    public ResponseEntity<ApiResponse<List<InscripcionResponse>>> misCursos(
            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(service.misCursos(auth));
    }

    @PostMapping("/lecciones/{id}/completar")
    @Operation(summary = "Marcar lección como completada y recalcular progreso")
    public ResponseEntity<ApiResponse<Void>> completar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.marcarLeccionCompletada(auth, id));
    }

    /* ───── ADMIN ───── */
    @GetMapping("/admin/todos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista completa de cursos (incluye no publicados) — solo ADMIN")
    public ResponseEntity<ApiResponse<List<CursoResponse>>> listarTodosAdmin() {
        return ResponseEntity.ok(service.listarTodosAdmin());
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear nuevo curso — solo ADMIN")
    public ResponseEntity<ApiResponse<CursoResponse>> crearAdmin(@Valid @RequestBody CursoRequest req) {
        return ResponseEntity.ok(service.crear(req));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar curso — solo ADMIN")
    public ResponseEntity<ApiResponse<CursoResponse>> actualizarAdmin(@PathVariable UUID id, @Valid @RequestBody CursoRequest req) {
        return ResponseEntity.ok(service.actualizar(id, req));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar curso — solo ADMIN")
    public ResponseEntity<ApiResponse<Void>> eliminarAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(service.eliminar(id));
    }

    @PatchMapping("/admin/{id}/publicado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Alternar estado publicado/no publicado — solo ADMIN")
    public ResponseEntity<ApiResponse<CursoResponse>> togglePublicado(@PathVariable UUID id) {
        return ResponseEntity.ok(service.togglePublicado(id));
    }
}
