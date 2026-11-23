package com.luxray.cursos.service;

import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.model.EvidenciaPractica;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EvidenciaPracticaRepository;
import com.luxray.cursos.repository.LeccionRepository;
import com.luxray.cursos.service.StoragePresignService.InitMultipartDTO;
import com.luxray.cursos.service.StoragePresignService.ParteConEtag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenciasService {

    private static final long MIN_PARTE_S3 = 5L * 1024 * 1024;   // 5 MiB mínimo por parte (salvo la última)
    private static final long MAX_ARCHIVO = 500L * 1024 * 1024;   // 500 MB tope por evidencia
    private static final int MAX_PARTES = 500;

    /** Tope de la retroalimentación escrita por el profesor. */
    private static final int MAX_RETRO = 2000;

    /** Estados válidos de la revisión docente. */
    private static final Set<String> ESTADOS_REVISION = Set.of("APROBADA", "RECHAZADA");

    private final EvidenciaPracticaRepository evidencias;
    private final LeccionRepository lecciones;
    private final CursoRepository cursos;
    private final StoragePresignService storage;
    private final JwtTokenProvider jwt;

    @Value("${luxray.storage.min-bytes-para-multipart:8388608}")
    private long minBytesParaMultipart;

    public EvidenciasService(EvidenciaPracticaRepository evidencias,
                             LeccionRepository lecciones,
                             CursoRepository cursos,
                             StoragePresignService storage,
                             JwtTokenProvider jwt) {
        this.evidencias = evidencias;
        this.lecciones = lecciones;
        this.cursos = cursos;
        this.storage = storage;
        this.jwt = jwt;
    }

    /* ── DTOs de entrada/salida ── */
    public record IniciarRequest(String nombreArchivo, String contentType, long tamanoBytes) { }
    public record ParteCompletadaDTO(int numeroParte, String etag) { }
    public record CompletarRequest(String uploadId, String clave, String nombreArchivo,
                                   String contentType, long tamanoBytes,
                                   List<ParteCompletadaDTO> partes) { }
    public record CancelarRequest(String uploadId, String clave) { }
    public record EvidenciaResponse(
            UUID id, String nombreArchivo, String contentType,
            long tamanoBytes, String url, String estado, String creadoEn,
            String retroalimentacion, String revisadaEn) { }
    public record RevisionRequest(String estado, String retroalimentacion) { }

    /** Detalle ampliado para el panel de revisión docente y el expediente del alumno. */
    public record EvidenciaDetalleResponse(
            UUID id, UUID usuarioId, UUID leccionId, String leccionTitulo,
            UUID cursoId, String cursoTitulo,
            String nombreArchivo, String contentType,
            long tamanoBytes, String url, String estado, String creadoEn,
            String retroalimentacion, String revisadaEn) { }

    /* ── 1) Iniciar subida multipart ── */
    public ApiResponse<InitMultipartDTO> iniciar(String authHeader, UUID leccionId, IniciarRequest req) {
        UUID usuarioId = resolverUsuario(authHeader);
        validarLeccion(leccionId);

        if (req == null || req.nombreArchivo() == null || req.nombreArchivo().isBlank()) {
            throw new BusinessException("nombreArchivo es requerido");
        }
        validarNombreArchivo(req.nombreArchivo());
        if (req.tamanoBytes() <= 0) throw new BusinessException("tamanoBytes debe ser mayor a 0");
        if (req.tamanoBytes() > MAX_ARCHIVO) {
            throw new BusinessException("El archivo supera el límite de 500 MB");
        }
        if (!esTipoPermitido(req.contentType())) {
            throw new BusinessException("Solo se permiten imágenes (JPEG/PNG/WebP) o videos (MP4/WebM/QuickTime)");
        }

        String clave = storage.nuevaClave(usuarioId, leccionId, limitarNombre(req.nombreArchivo()));
        int tamanoParte = (int) Math.max(MIN_PARTE_S3,
                Math.ceil((double) req.tamanoBytes() / MAX_PARTES));

        InitMultipartDTO init = storage.iniciarMultipart(
                clave, req.contentType(), req.tamanoBytes(), tamanoParte);
        return ApiResponse.ok(init);
    }

    /* ── 2) Confirmar subida y registrar evidencia ── */
    @Transactional
    public ApiResponse<EvidenciaResponse> completar(String authHeader, UUID leccionId, CompletarRequest req) {
        UUID usuarioId = resolverUsuario(authHeader);
        validarLeccion(leccionId);

        if (req == null || req.uploadId() == null || req.clave() == null || req.partes() == null) {
            throw new BusinessException("uploadId, clave y partes son requeridos");
        }
        if (req.partes().isEmpty() || req.partes().size() > 10_000) {
            throw new BusinessException("La lista de partes debe tener entre 1 y 10000 elementos");
        }
        // Revalidación de los metadatos confirmados por el cliente (defensa en profundidad):
        // el cliente podría declarar en "completar" datos distintos de los validados en "iniciar".
        if (req.nombreArchivo() != null) validarNombreArchivo(req.nombreArchivo());
        if (!esTipoPermitido(req.contentType())) {
            throw new BusinessException("Solo se permiten imágenes (JPEG/PNG/WebP) o videos (MP4/WebM/QuickTime)");
        }
        if (req.tamanoBytes() <= 0 || req.tamanoBytes() > MAX_ARCHIVO) {
            throw new BusinessException("tamanoBytes inválido");
        }

        storage.completarMultipart(req.clave(), req.uploadId(),
                req.partes().stream()
                        .map(p -> new ParteConEtag(p.numeroParte(), p.etag()))
                        .toList());

        EvidenciaPractica e = new EvidenciaPractica();
        e.setUsuarioId(usuarioId);
        e.setLeccionId(leccionId);
        e.setNombreArchivo(limitarNombre(req.nombreArchivo()));
        e.setContentType(req.contentType());
        e.setTamanoBytes(req.tamanoBytes());
        e.setClave(req.clave());
        // Subida completada: queda PENDIENTE en la cola de revisión docente
        e.setEstado("PENDIENTE");
        evidencias.save(e);

        return ApiResponse.ok(aResp(e));
    }

    /* ── 3) Cancelar subida ── */
    public ApiResponse<Void> cancelar(String authHeader, UUID leccionId, CancelarRequest req) {
        UUID usuarioId = resolverUsuario(authHeader);
        validarLeccion(leccionId);
        if (req == null || req.uploadId() == null || req.clave() == null) {
            throw new BusinessException("uploadId y clave son requeridos");
        }
        storage.abortarMultipart(req.clave(), req.uploadId());
        return ApiResponse.ok(null);
    }

    /* ── 4) Listar evidencias del usuario para la lección ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EvidenciaResponse>> listar(String authHeader, UUID leccionId) {
        UUID usuarioId = resolverUsuario(authHeader);
        validarLeccion(leccionId);
        List<EvidenciaResponse> data = evidencias
                .findByUsuarioIdAndLeccionIdOrderByCreadoEnDesc(usuarioId, leccionId)
                .stream()
                .map(this::aResp)
                .toList();
        return ApiResponse.ok(data);
    }

    /* ── 5) Revisión docente: cola de evidencias de todos los alumnos ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EvidenciaDetalleResponse>> listarParaRevision(String estado) {
        List<EvidenciaPractica> lista;
        if (estado == null || estado.isBlank()) {
            lista = evidencias.findAllByOrderByCreadoEnAsc();
        } else {
            String filtro = estado.trim().toUpperCase();
            if (!filtro.equals("PENDIENTE") && !filtro.equals("APROBADA") && !filtro.equals("RECHAZADA")) {
                throw new BusinessException("estado debe ser PENDIENTE, APROBADA o RECHAZADA");
            }
            lista = evidencias.findByEstadoOrderByCreadoEnAsc(filtro);
        }
        return ApiResponse.ok(lista.stream().map(this::aDetalle).toList());
    }

    /* ── 6) Revisión docente: aprobar / rechazar con retroalimentación ── */
    @Transactional
    public ApiResponse<EvidenciaDetalleResponse> revisar(String authHeader, UUID evidenciaId, RevisionRequest req) {
        UUID profesorId = resolverUsuario(authHeader);

        if (req == null || req.estado() == null || req.estado().isBlank()) {
            throw new BusinessException("estado es requerido (APROBADA o RECHAZADA)");
        }
        String nuevoEstado = req.estado().trim().toUpperCase();
        if (!ESTADOS_REVISION.contains(nuevoEstado)) {
            throw new BusinessException("estado debe ser APROBADA o RECHAZADA");
        }

        String retro = req.retroalimentacion() == null ? "" : req.retroalimentacion().trim();
        if (retro.length() > MAX_RETRO) {
            throw new BusinessException("La retroalimentación supera el máximo de " + MAX_RETRO + " caracteres");
        }
        // Al rechazar, el profesor SIEMPRE debe explicar el motivo al alumno
        if ("RECHAZADA".equals(nuevoEstado) && retro.isEmpty()) {
            throw new BusinessException("Debes explicar el motivo del rechazo al alumno");
        }

        EvidenciaPractica e = evidencias.findById(evidenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidencia no encontrada"));

        e.setEstado(nuevoEstado);
        e.setRetroalimentacion(retro.isEmpty() ? null : retro);
        e.setRevisadaPor(profesorId);
        e.setRevisadaEn(LocalDateTime.now());
        evidencias.save(e);

        return ApiResponse.ok(aDetalle(e));
    }

    /* ── 7) Expediente del alumno: todas sus evidencias con retroalimentación ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EvidenciaDetalleResponse>> expediente(String authHeader) {
        UUID usuarioId = resolverUsuario(authHeader);
        return ApiResponse.ok(evidencias.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
                .stream()
                .map(this::aDetalle)
                .toList());
    }

    /* ── Auxiliares ── */
    /**
     * Validación de entradas sobre el nombre de archivo recibido del cliente:
     * se rechazan separadores de ruta y secuencias ".." (path traversal),
     * caracteres de control y nombres vacíos o kilométricos.
     */
    private void validarNombreArchivo(String nombre) {
        String s = nombre.trim();
        if (s.isEmpty()) throw new BusinessException("nombreArchivo es requerido");
        if (s.length() > 200) throw new BusinessException("nombreArchivo demasiado largo");
        if (s.contains("..") || s.contains("/") || s.contains("\\") || s.matches(".*[\\x00-\\x1f].*")) {
            throw new BusinessException("nombreArchivo contiene caracteres no permitidos");
        }
    }

    /** Recorta nombres kilométricos para no romper UI ni exceder el VARCHAR(260). */
    private String limitarNombre(String nombre) {
        if (nombre == null) return "evidencia";
        String s = nombre.trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
    private void validarLeccion(UUID leccionId) {
        if (!lecciones.existsById(leccionId)) {
            throw new ResourceNotFoundException("Lección no encontrada: " + leccionId);
        }
    }

    private boolean esTipoPermitido(String contentType) {
        if (contentType == null) return false;
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/png", "image/webp",
                 "video/mp4", "video/webm", "video/quicktime" -> true;
            default -> false;
        };
    }

    private EvidenciaResponse aResp(EvidenciaPractica e) {
        return new EvidenciaResponse(
                e.getId(),
                e.getNombreArchivo(),
                e.getContentType(),
                e.getTamanoBytes() == null ? 0 : e.getTamanoBytes(),
                storage.urlLectura(e.getClave()),
                e.getEstado(),
                e.getCreadoEn() == null ? null : e.getCreadoEn().toString(),
                e.getRetroalimentacion(),
                e.getRevisadaEn() == null ? null : e.getRevisadaEn().toString());
    }

    /** Detalle ampliado: añade alumno, lección y curso para el panel docente y el expediente. */
    private EvidenciaDetalleResponse aDetalle(EvidenciaPractica e) {
        String leccionTitulo = null;
        UUID cursoId = null;
        String cursoTitulo = null;
        var leccion = lecciones.findById(e.getLeccionId()).orElse(null);
        if (leccion != null) {
            leccionTitulo = leccion.getTitulo();
            cursoId = leccion.getCursoId();
            var curso = cursos.findById(cursoId).orElse(null);
            if (curso != null) cursoTitulo = curso.getTitulo();
        }
        return new EvidenciaDetalleResponse(
                e.getId(),
                e.getUsuarioId(),
                e.getLeccionId(),
                leccionTitulo,
                cursoId,
                cursoTitulo,
                e.getNombreArchivo(),
                e.getContentType(),
                e.getTamanoBytes() == null ? 0 : e.getTamanoBytes(),
                storage.urlLectura(e.getClave()),
                e.getEstado(),
                e.getCreadoEn() == null ? null : e.getCreadoEn().toString(),
                e.getRetroalimentacion(),
                e.getRevisadaEn() == null ? null : e.getRevisadaEn().toString());
    }

    private UUID resolverUsuario(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("Token de autorización requerido");
        }
        String token = authHeader.substring(7);
        try {
            var claims = jwt.parse(token);
            return UUID.fromString(claims.get("uid", String.class));
        } catch (Exception ex) {
            throw new BusinessException("Token inválido o expirado");
        }
    }
}
