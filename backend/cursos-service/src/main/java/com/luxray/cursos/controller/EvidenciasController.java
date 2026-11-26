package com.luxray.cursos.controller;

import com.luxray.common.dto.ApiResponse;
import com.luxray.cursos.service.EvidenciasService;
import com.luxray.cursos.service.EvidenciasService.CompletarRequest;
import com.luxray.cursos.service.EvidenciasService.EntregaResponse;
import com.luxray.cursos.service.EvidenciasService.IniciarRequest;
import com.luxray.cursos.service.EvidenciasService.CancelarRequest;
import com.luxray.cursos.service.StoragePresignService.InitMultipartDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Evidencias de práctica: la app móvil sube fotos/videos directamente a S3
 * mediante Multipart Upload con URLs prefirmadas emitidas por este servicio.
 *
 * Flujo:
 *   1. POST /cursos/lecciones/{id}/uploads            → inicia multipart, devuelve URLs por parte
 *   2. (cliente) PUT de cada parte a su URL prefirmada → subida directa al almacenamiento
 *   3. POST /cursos/lecciones/{id}/uploads/completar   → une partes y registra la evidencia
 */
@RestController
@RequestMapping("/cursos/lecciones/{leccionId}/uploads")
@Tag(name = "Evidencias de práctica", description = "Subida multimedia multipart con URLs prefirmadas")
public class EvidenciasController {

    private final EvidenciasService service;

    public EvidenciasController(EvidenciasService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Inicia una subida multipart y devuelve URLs prefirmadas por parte")
    public ResponseEntity<ApiResponse<InitMultipartDTO>> iniciar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId,
            @RequestBody IniciarRequest req) {
        return ResponseEntity.ok(service.iniciar(auth, leccionId, req));
    }

    @PostMapping("/completar")
    @Operation(summary = "Confirma la subida con los ETags de cada parte y registra la evidencia")
    public ResponseEntity<ApiResponse<EntregaResponse>> completar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId,
            @RequestBody CompletarRequest req) {
        return ResponseEntity.ok(service.completar(auth, leccionId, req));
    }

    @PostMapping("/cancelar")
    @Operation(summary = "Aborta una subida multipart en curso")
    public ResponseEntity<ApiResponse<Void>> cancelar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId,
            @RequestBody CancelarRequest req) {
        return ResponseEntity.ok(service.cancelar(auth, leccionId, req));
    }

    @GetMapping
    @Operation(summary = "Evidencias de práctica del usuario autenticado para la lección")
    public ResponseEntity<ApiResponse<List<EntregaResponse>>> listar(
            @RequestHeader("Authorization") String auth,
            @PathVariable UUID leccionId) {
        return ResponseEntity.ok(service.listar(auth, leccionId));
    }
}
