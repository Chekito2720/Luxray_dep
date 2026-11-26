package com.luxray.cursos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.cursos.model.*;
import com.luxray.cursos.repository.*;
import com.luxray.cursos.service.CursoAccesoService.Credenciales;
import com.luxray.cursos.service.StoragePresignService.InitMultipartDTO;
import com.luxray.cursos.service.StoragePresignService.ParteConEtag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Entregas de práctica versionadas. Cada versión reune la foto, el PDF y los
 * archivos opcionales del alumno; la revisión docente se aplica a la versión
 * completa y calcula una nota sobre 100 mediante la lista de cotejo.
 */
@Service
public class EvidenciasService {

    private static final Logger log = LoggerFactory.getLogger(EvidenciasService.class);

    private static final long MIN_PARTE_S3 = 5L * 1024 * 1024;
    private static final long MAX_VIDEO = 500L * 1024 * 1024;
    private static final long MAX_FOTO = 100L * 1024 * 1024;
    private static final long MAX_PDF = 100L * 1024 * 1024;
    private static final int MAX_PARTES = 500;
    private static final int MAX_RETRO = 2000;
    private static final int MAX_CRITERIOS = 50;

    private static final Set<String> ESTADOS_REVISION = Set.of("APROBADA", "RECHAZADA");
    private static final Set<String> ESTADOS_FILTRO = Set.of(
            "INCOMPLETA", "PENDIENTE", "APROBADA", "RECHAZADA");

    private final EvidenciaPracticaRepository evidencias;
    private final EntregaPracticaRepository entregas;
    private final SubidaMultipartRepository subidas;
    private final ListaCotejoItemRepository cotejos;
    private final LeccionRepository lecciones;
    private final CursoRepository cursos;
    private final ProgresoLeccionRepository progreso;
    private final InscripcionRepository inscripciones;
    private final StoragePresignService storage;
    private final ObjectMapper objectMapper;
    private final CursoAccesoService acceso;
    private final AdvisoryLockService locks;
    private final PlatformTransactionManager transactionManager;

    @Value("${luxray.storage.min-bytes-para-multipart:8388608}")
    private long minBytesParaMultipart;

    public EvidenciasService(EvidenciaPracticaRepository evidencias,
                              EntregaPracticaRepository entregas,
                              SubidaMultipartRepository subidas,
                              ListaCotejoItemRepository cotejos,
                              LeccionRepository lecciones,
                              CursoRepository cursos,
                              ProgresoLeccionRepository progreso,
                              InscripcionRepository inscripciones,
                              StoragePresignService storage,
                              ObjectMapper objectMapper,
                              CursoAccesoService acceso,
                              AdvisoryLockService locks,
                               PlatformTransactionManager transactionManager) {
        this.evidencias = evidencias;
        this.entregas = entregas;
        this.subidas = subidas;
        this.cotejos = cotejos;
        this.lecciones = lecciones;
        this.cursos = cursos;
        this.progreso = progreso;
        this.inscripciones = inscripciones;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.acceso = acceso;
        this.locks = locks;
        this.transactionManager = transactionManager;
    }

    /* ── DTOs ── */
    public record IniciarRequest(String nombreArchivo, String contentType,
                                 long tamanoBytes, String tipoArchivo) {
        public IniciarRequest(String nombreArchivo, String contentType, long tamanoBytes) {
            this(nombreArchivo, contentType, tamanoBytes, null);
        }
    }

    public record ParteCompletadaDTO(int numeroParte, String etag) { }

    public record CompletarRequest(String uploadId, String clave, String nombreArchivo,
                                   String contentType, long tamanoBytes, String tipoArchivo,
                                   List<ParteCompletadaDTO> partes) {
        public CompletarRequest(String uploadId, String clave, String nombreArchivo,
                                String contentType, long tamanoBytes,
                                List<ParteCompletadaDTO> partes) {
            this(uploadId, clave, nombreArchivo, contentType, tamanoBytes, null, partes);
        }
    }

    public record CancelarRequest(String uploadId, String clave) { }

    public record ArchivoResponse(UUID id, String tipoArchivo, String nombreArchivo,
                                  String contentType, long tamanoBytes, String url,
                                  String creadoEn) { }

    public record CriterioResponse(UUID id, String descripcion, int puntos, boolean cumplido) { }

    public record CriterioRequest(UUID itemId, boolean cumplido) { }

    public record RevisionRequest(String estado, String retroalimentacion,
                                  List<CriterioRequest> criterios) {
        public RevisionRequest(String estado, String retroalimentacion) {
            this(estado, retroalimentacion, List.of());
        }
    }

    /**
     * @param id identificador opcional de un criterio ya guardado; al
     *           conservarlo no se rompen las evaluaciones históricas.
     */
    public record CriterioConfigRequest(String descripcion, int puntos, UUID id) {
        public CriterioConfigRequest(String descripcion, int puntos) {
            this(descripcion, puntos, null);
        }
    }

    public record ListaCotejoResponse(UUID id, String descripcion, int puntos, int orden) { }

    public record ActividadRevisionResponse(
            UUID leccionId, String leccionTitulo, UUID cursoId,
            String cursoTitulo, boolean tieneLista) { }

    public record CursoActividadesResponse(
            UUID cursoId, String cursoTitulo,
            List<ActividadRevisionResponse> actividades) { }

    public record EntregaResponse(
            UUID id, UUID usuarioId, UUID leccionId, String leccionTitulo,
            UUID cursoId, String cursoTitulo, int version, String estado,
            List<ArchivoResponse> archivos, String retroalimentacion,
            Integer puntuacion, List<CriterioResponse> criterios,
            String creadoEn, String revisadaEn) { }

    /* ── 1) Iniciar subida multipart ── */
    public ApiResponse<InitMultipartDTO> iniciar(String authHeader, UUID leccionId,
                                                  IniciarRequest req) {
        Credenciales cred = acceso.credenciales(authHeader);
        acceso.requiereAlumnoInscrito(cred, leccionId);
        Leccion leccion = acceso.leccion(leccionId);

        if (req == null || req.nombreArchivo() == null || req.nombreArchivo().isBlank()) {
            throw new BusinessException("nombreArchivo es requerido");
        }
        validarNombreArchivo(req.nombreArchivo());
        String tipo = normalizarTipo(req.tipoArchivo(), req.contentType());
        String contentType = req.contentType() == null
                ? "" : req.contentType().toLowerCase(Locale.ROOT);
        validarArchivo(tipo, contentType, req.tamanoBytes());

        String nombreArchivo = limitarNombre(req.nombreArchivo());
        String clave = storage.nuevaClave(cred.usuarioId(), leccion.getId(), nombreArchivo);
        int tamanoParte = (int) Math.max(MIN_PARTE_S3,
                Math.ceil((double) req.tamanoBytes() / MAX_PARTES));

        InitMultipartDTO init = storage.iniciarMultipart(
                clave, contentType, req.tamanoBytes(), tamanoParte);

        SubidaMultipart sesion = new SubidaMultipart();
        sesion.setUploadId(init.uploadId());
        sesion.setUsuarioId(cred.usuarioId());
        sesion.setLeccionId(leccion.getId());
        sesion.setClave(clave);
        sesion.setNombreArchivo(nombreArchivo);
        sesion.setContentType(contentType);
        sesion.setTamanoBytes(req.tamanoBytes());
        sesion.setTipoArchivo(tipo);
        sesion.setPartesEsperadas(init.partes().size());
        sesion.setCreadoEn(LocalDateTime.now());
        sesion.setExpiraEn(LocalDateTime.now().plusHours(2));
        subidas.save(sesion);
        return ApiResponse.ok(init);
    }

    /* ── 2) Confirmar subida y registrar archivo en una versión ── */
    @Transactional
    public ApiResponse<EntregaResponse> completar(String authHeader, UUID leccionId,
                                                   CompletarRequest req) {
        Credenciales cred = acceso.credenciales(authHeader);
        acceso.requiereAlumnoInscrito(cred, leccionId);
        Leccion leccion = acceso.leccion(leccionId);

        if (req == null || req.uploadId() == null || req.clave() == null || req.partes() == null) {
            throw new BusinessException("uploadId, clave y partes son requeridos");
        }
        if (req.partes().isEmpty() || req.partes().size() > 10_000) {
            throw new BusinessException("La lista de partes debe tener entre 1 y 10000 elementos");
        }

        SubidaMultipart sesion = subidas.findById(req.uploadId())
                .orElseThrow(() -> new BusinessException(
                        "La sesión de subida no existe o ya fue finalizada"));
        if (sesion.getExpiraEn() != null && sesion.getExpiraEn().isBefore(LocalDateTime.now())) {
            subidas.delete(sesion);
            throw new BusinessException("La sesión de subida expiró; inicia la carga otra vez");
        }
        validarMetadatosSubida(sesion, cred.usuarioId(), leccionId, req);

        String tipo = sesion.getTipoArchivo();
        String contentType = sesion.getContentType();
        long tamano = sesion.getTamanoBytes();

        storage.completarMultipart(sesion.getClave(), sesion.getUploadId(),
                req.partes().stream()
                        .sorted(Comparator.comparingInt(ParteCompletadaDTO::numeroParte))
                        .map(p -> new ParteConEtag(p.numeroParte(), p.etag()))
                        .toList());

        StoragePresignService.ObjetoMetadata metadata = storage.verificarObjeto(sesion.getClave());
        if (metadata.tamanoBytes() != tamano) {
            throw descartarSubidaInvalida(sesion,
                    "El tamaño del archivo subido no coincide con el registrado");
        }
        if (metadata.contentType() != null && !metadata.contentType().isBlank()
                && !metadata.contentType().equalsIgnoreCase(contentType)) {
            throw descartarSubidaInvalida(sesion,
                    "El tipo real del archivo no coincide con el registrado");
        }

        byte[] prefijo;
        try {
            prefijo = storage.leerPrefijo(sesion.getClave(), 1024);
        } catch (RuntimeException ex) {
            throw descartarSubidaInvalida(sesion,
                    "No se pudo validar el contenido del archivo");
        }
        if (!firmaValida(tipo, contentType, prefijo)) {
            throw descartarSubidaInvalida(sesion,
                    "El contenido del archivo no coincide con el formato declarado");
        }

        locks.bloquearEntrega(cred.usuarioId(), leccionId);
        EntregaPractica entrega = obtenerOCrearEntrega(
                cred.usuarioId(), leccion.getId(), leccion.getCursoId(), tipo);

        EvidenciaPractica archivo = new EvidenciaPractica();
        archivo.setUsuarioId(cred.usuarioId());
        archivo.setLeccionId(leccion.getId());
        archivo.setEntregaId(entrega.getId());
        archivo.setTipoArchivo(tipo);
        archivo.setVersion(entrega.getVersion());
        archivo.setNombreArchivo(sesion.getNombreArchivo());
        archivo.setContentType(contentType);
        archivo.setTamanoBytes(tamano);
        archivo.setClave(sesion.getClave());
        // El estado real vive en la entrega; la columna histórica se conserva.
        archivo.setEstado("PENDIENTE");
        evidencias.save(archivo);

        actualizarEstadoEntrega(entrega);
        entregas.save(entrega);
        subidas.delete(sesion);
        return ApiResponse.ok(aDetalle(entrega));
    }

    /* ── 3) Cancelar subida ── */
    public ApiResponse<Void> cancelar(String authHeader, UUID leccionId, CancelarRequest req) {
        Credenciales cred = acceso.credenciales(authHeader);
        acceso.requiereAlumnoInscrito(cred, leccionId);
        if (req == null || req.uploadId() == null || req.clave() == null) {
            throw new BusinessException("uploadId y clave son requeridos");
        }
        validarClave(req.clave(), cred.usuarioId(), leccionId);
        var sesion = subidas.findById(req.uploadId());
        if (sesion.isPresent()) {
            SubidaMultipart datos = sesion.get();
            if (!datos.getUsuarioId().equals(cred.usuarioId())
                    || !datos.getLeccionId().equals(leccionId)
                    || !datos.getClave().equals(req.clave())) {
                throw new AccessDeniedException("La sesión de subida no pertenece a esta actividad");
            }
            storage.abortarMultipart(datos.getClave(), datos.getUploadId());
            subidas.delete(datos);
        }
        return ApiResponse.ok(null);
    }

    /* ── 4) Historial de entregas del alumno para una actividad ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EntregaResponse>> listar(String authHeader, UUID leccionId) {
        Credenciales cred = acceso.credenciales(authHeader);
        acceso.requiereAlumnoInscrito(cred, leccionId);
        List<EntregaResponse> data = entregas
                .findByUsuarioIdAndLeccionIdOrderByVersionDesc(cred.usuarioId(), leccionId)
                .stream().map(this::aDetalle).toList();
        return ApiResponse.ok(data);
    }

    /* ── 5) Cola docente agrupable por clase y actividad ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EntregaResponse>> listarParaRevision(String authHeader, String estado) {
        Credenciales cred = acceso.credenciales(authHeader);
        if (!cred.esProfesor() && !cred.esAdmin()) {
            throw new AccessDeniedException("Solo un profesor puede revisar entregas");
        }
        Set<UUID> permitidos = acceso.cursosPermitidos(cred);
        if (permitidos.isEmpty()) return ApiResponse.ok(List.of());

        List<EntregaPractica> lista;
        if (estado == null || estado.isBlank()) {
            lista = entregas.findByCursoIdInOrderByCreadoEnAsc(permitidos);
        } else {
            String filtro = estado.trim().toUpperCase();
            if (!ESTADOS_FILTRO.contains(filtro)) {
                throw new BusinessException("estado debe ser INCOMPLETA, PENDIENTE, APROBADA o RECHAZADA");
            }
            lista = entregas.findByCursoIdInAndEstadoOrderByCreadoEnAsc(permitidos, filtro);
        }
        return ApiResponse.ok(lista.stream().map(this::aDetalle).toList());
    }

    /**
     * Catálogo de actividades de las clases que el docente puede gestionar.
     * Se incluye aunque todavía no existan entregas, para permitir preparar
     * la lista de cotejo antes de que el alumno entregue por primera vez.
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CursoActividadesResponse>> listarActividades(String authHeader) {
        Credenciales cred = acceso.credenciales(authHeader);
        if (!cred.esProfesor() && !cred.esAdmin()) {
            throw new AccessDeniedException("Solo un profesor puede consultar sus actividades");
        }
        Set<UUID> permitidos = acceso.cursosPermitidos(cred);
        if (permitidos.isEmpty()) return ApiResponse.ok(List.of());

        List<CursoActividadesResponse> data = cursos.findAllById(permitidos).stream()
                .sorted(Comparator.comparing(Curso::getTitulo, String.CASE_INSENSITIVE_ORDER))
                .map(curso -> new CursoActividadesResponse(
                        curso.getId(), curso.getTitulo(),
                        lecciones.findByCursoIdOrderByOrdenAsc(curso.getId()).stream()
                                .map(leccion -> new ActividadRevisionResponse(
                                        leccion.getId(), leccion.getTitulo(),
                                        curso.getId(), curso.getTitulo(),
                                        cotejos.countByLeccionId(leccion.getId()) > 0))
                                .toList()))
                .toList();
        return ApiResponse.ok(data);
    }

    /* ── 6) Calificar con lista de cotejo ── */
    @Transactional
    public ApiResponse<EntregaResponse> revisar(String authHeader, UUID entregaId,
                                                 RevisionRequest req) {
        Credenciales cred = acceso.credenciales(authHeader);
        if (!cred.esProfesor() && !cred.esAdmin()) {
            throw new AccessDeniedException("Solo un profesor puede revisar entregas");
        }
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
        if ("RECHAZADA".equals(nuevoEstado) && retro.isEmpty()) {
            throw new BusinessException("Debes explicar el motivo del rechazo al alumno");
        }

        EntregaPractica entrega = entregas.findById(entregaId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada"));
        locks.bloquearEntrega(entrega.getUsuarioId(), entrega.getLeccionId());
        acceso.requiereGestionCurso(cred, entrega.getCursoId());
        if ("SUPERADA".equals(entrega.getEstado())) {
            throw new BusinessException("Esta versión fue reemplazada por una entrega más reciente");
        }
        if (!tieneFotoYPdf(entrega.getId())) {
            throw new BusinessException("La entrega debe incluir una foto y un PDF para poder calificarse");
        }

        List<ListaCotejoItem> items = cotejos.findByLeccionIdOrderByOrdenAsc(entrega.getLeccionId());
        List<CriterioResponse> evaluacion = construirEvaluacion(items, req.criterios(), nuevoEstado);
        Integer puntuacion = "APROBADA".equals(nuevoEstado)
                ? calcularPuntuacion(items, evaluacion) : null;

        entrega.setEstado(nuevoEstado);
        entrega.setRetroalimentacion(retro.isEmpty() ? null : retro);
        entrega.setRevisadaPor(cred.usuarioId());
        entrega.setRevisadaEn(LocalDateTime.now());
        entrega.setCriteriosEvaluacion(serializar(evaluacion));
        entrega.setPuntuacion(puntuacion);
        entrega.setActualizadoEn(LocalDateTime.now());
        entregas.save(entrega);

        if ("APROBADA".equals(nuevoEstado)) {
            if (esUltimaVersion(entrega)) {
                marcarProgresoAprobado(entrega.getUsuarioId(), entrega.getLeccionId(), puntuacion);
            }
        } else if (esUltimaVersion(entrega)) {
            marcarProgresoRechazado(entrega.getUsuarioId(), entrega.getLeccionId());
        }
        return ApiResponse.ok(aDetalle(entrega));
    }

    /* ── 7) Expediente del alumno ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<EntregaResponse>> expediente(String authHeader) {
        Credenciales cred = acceso.credenciales(authHeader);
        return ApiResponse.ok(entregas.findByUsuarioIdOrderByCreadoEnDesc(cred.usuarioId())
                .stream().map(this::aDetalle).toList());
    }

    /* ── 8) Lista de cotejo de una actividad ── */
    @Transactional(readOnly = true)
    public ApiResponse<List<ListaCotejoResponse>> obtenerListaCotejo(String authHeader,
                                                                      UUID leccionId) {
        Credenciales cred = acceso.credenciales(authHeader);
        Leccion leccion = acceso.leccion(leccionId);
        acceso.requiereAccesoCurso(cred, leccion.getCursoId());
        List<ListaCotejoResponse> data = cotejos.findByLeccionIdOrderByOrdenAsc(leccionId)
                .stream().map(i -> new ListaCotejoResponse(
                        i.getId(), i.getDescripcion(), i.getPuntos(), i.getOrden()))
                .toList();
        return ApiResponse.ok(data);
    }

    @Transactional
    public ApiResponse<List<ListaCotejoResponse>> guardarListaCotejo(
            String authHeader, UUID leccionId, List<CriterioConfigRequest> req) {
        Credenciales cred = acceso.credenciales(authHeader);
        if (!cred.esProfesor() && !cred.esAdmin()) {
            throw new AccessDeniedException("Solo un profesor puede configurar la lista de cotejo");
        }
        acceso.requiereGestionLeccion(cred, leccionId);
        locks.bloquearCotejo(leccionId);
        if (req == null || req.isEmpty()) {
            throw new BusinessException("La lista de cotejo debe tener al menos un criterio");
        }
        if (req.size() > MAX_CRITERIOS) {
            throw new BusinessException("La lista admite hasta " + MAX_CRITERIOS + " criterios");
        }

        int total = 0;
        for (CriterioConfigRequest item : req) {
            if (item == null || item.descripcion() == null
                    || item.descripcion().trim().length() < 3
                    || item.descripcion().trim().length() > 300) {
                throw new BusinessException("Cada criterio debe tener entre 3 y 300 caracteres");
            }
            if (item.puntos() <= 0 || item.puntos() > 1000) {
                throw new BusinessException("Cada criterio debe tener entre 1 y 1000 puntos");
            }
            total += item.puntos();
        }
        if (total > 10_000) {
            throw new BusinessException("La suma de puntos no puede superar 10000");
        }

        List<ListaCotejoItem> actuales = cotejos.findByLeccionIdOrderByOrdenAsc(leccionId);
        Map<UUID, ListaCotejoItem> porId = new HashMap<>();
        for (ListaCotejoItem actual : actuales) porId.put(actual.getId(), actual);

        List<ListaCotejoItem> guardados = new ArrayList<>();
        Set<UUID> conservados = new HashSet<>();
        for (int i = 0; i < req.size(); i++) {
            CriterioConfigRequest config = req.get(i);
            ListaCotejoItem item = null;
            if (config.id() != null) {
                item = porId.get(config.id());
                if (item != null && !conservados.add(config.id())) {
                    throw new BusinessException("Hay criterios duplicados en la lista de cotejo");
                }
            }
            if (item == null) {
                item = new ListaCotejoItem();
                item.setLeccionId(leccionId);
            }
            item.setDescripcion(config.descripcion().trim());
            item.setPuntos(config.puntos());
            item.setOrden(i + 1);
            guardados.add(cotejos.save(item));
        }
        for (ListaCotejoItem actual : actuales) {
            if (!conservados.contains(actual.getId())) cotejos.delete(actual);
        }
        return ApiResponse.ok(guardados.stream()
                .map(i -> new ListaCotejoResponse(i.getId(), i.getDescripcion(), i.getPuntos(), i.getOrden()))
                .toList());
    }

    /**
     * Una actividad sólo puede marcarse completada cuando la versión vigente
     * fue aprobada por el profesor. Tener foto y PDF la deja pendiente de
     * revisión; una entrega rechazada no puede eludir esa decisión.
     */
    @Transactional(readOnly = true)
    public boolean puedeMarcarCompletada(UUID usuarioId, UUID leccionId) {
        return entregas.findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(usuarioId, leccionId)
                .map(e -> "APROBADA".equals(e.getEstado()) && tieneFotoYPdf(e.getId()))
                .orElse(false);
    }

    /**
     * Comprueba la versión vigente bajo el mismo bloqueo usado al crear una
     * versión nueva y al revisar. Así no puede aprobarse una entrega y marcar
     * la lección en el instante en que aparece un reemplazo pendiente.
     */
    @Transactional(readOnly = true)
    public boolean puedeMarcarCompletadaBloqueado(UUID usuarioId, UUID leccionId) {
        locks.bloquearEntrega(usuarioId, leccionId);
        return puedeMarcarCompletada(usuarioId, leccionId);
    }

    /* ── Construcción de entregas y evaluaciones ── */
    private EntregaPractica obtenerOCrearEntrega(UUID usuarioId, UUID leccionId,
                                                  UUID cursoId, String tipoArchivo) {
        EntregaPractica latest = entregas
                .findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(usuarioId, leccionId)
                .orElse(null);
        if (latest != null) {
            List<EvidenciaPractica> archivos = evidencias.findByEntregaIdOrderByCreadoEnAsc(latest.getId());
            boolean yaTieneTipo = archivos.stream().anyMatch(a -> tipoArchivo.equals(a.getTipoArchivo()));
            boolean versionCompleta = tieneFotoYPdf(latest.getId());
            boolean revisada = "APROBADA".equals(latest.getEstado()) || "RECHAZADA".equals(latest.getEstado());
            if (!yaTieneTipo && !versionCompleta && !revisada) return latest;
        }

        if (latest != null && ("PENDIENTE".equals(latest.getEstado())
                || "INCOMPLETA".equals(latest.getEstado()))) {
            latest.setEstado("SUPERADA");
            latest.setActualizadoEn(LocalDateTime.now());
            entregas.save(latest);
        }

        EntregaPractica nueva = new EntregaPractica();
        nueva.setUsuarioId(usuarioId);
        nueva.setLeccionId(leccionId);
        nueva.setCursoId(cursoId);
        nueva.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        nueva.setEstado("INCOMPLETA");
        nueva.setCreadoEn(LocalDateTime.now());
        nueva.setActualizadoEn(LocalDateTime.now());
        EntregaPractica guardada = entregas.save(nueva);
        // La nota y el avance se refieren a la versión vigente. Al publicar
        // un reemplazo se invalidan hasta que el profesor revise la nueva.
        invalidarProgreso(usuarioId, leccionId, cursoId);
        return guardada;
    }

    private void actualizarEstadoEntrega(EntregaPractica entrega) {
        List<EvidenciaPractica> archivos = evidencias.findByEntregaIdOrderByCreadoEnAsc(entrega.getId());
        boolean foto = archivos.stream().anyMatch(a -> "FOTO".equals(a.getTipoArchivo()));
        boolean pdf = archivos.stream().anyMatch(a -> "PDF".equals(a.getTipoArchivo()));
        entrega.setEstado(foto && pdf ? "PENDIENTE" : "INCOMPLETA");
        entrega.setActualizadoEn(LocalDateTime.now());
    }

    private boolean tieneFotoYPdf(UUID entregaId) {
        List<EvidenciaPractica> archivos = evidencias.findByEntregaIdOrderByCreadoEnAsc(entregaId);
        return archivos.stream().anyMatch(a -> "FOTO".equals(a.getTipoArchivo()))
                && archivos.stream().anyMatch(a -> "PDF".equals(a.getTipoArchivo()));
    }

    private List<CriterioResponse> construirEvaluacion(List<ListaCotejoItem> items,
                                                        List<CriterioRequest> solicitados,
                                                        String estado) {
        if (items.isEmpty()) {
            if ("APROBADA".equals(estado)) {
                throw new BusinessException("Configura la lista de cotejo de la actividad antes de aprobar la entrega");
            }
            if (solicitados != null && !solicitados.isEmpty()) {
                throw new BusinessException("La actividad todavía no tiene una lista de cotejo");
            }
            return List.of();
        }

        Map<UUID, CriterioRequest> porItem = new HashMap<>();
        if (solicitados != null) {
            for (CriterioRequest c : solicitados) {
                if (c == null || c.itemId() == null) {
                    throw new BusinessException("Cada criterio debe incluir itemId");
                }
                if (porItem.put(c.itemId(), c) != null) {
                    throw new BusinessException("Hay criterios duplicados en la evaluación");
                }
            }
        }
        Set<UUID> validos = items.stream().map(ListaCotejoItem::getId).collect(Collectors.toSet());
        if (!validos.containsAll(porItem.keySet())) {
            throw new BusinessException("La evaluación incluye criterios que ya no existen");
        }
        if ("APROBADA".equals(estado) && porItem.size() != items.size()) {
            throw new BusinessException("Debes evaluar todos los criterios de la lista de cotejo");
        }

        List<CriterioResponse> evaluacion = new ArrayList<>();
        for (ListaCotejoItem item : items) {
            CriterioRequest c = porItem.get(item.getId());
            evaluacion.add(new CriterioResponse(item.getId(), item.getDescripcion(),
                    item.getPuntos(), c != null && c.cumplido()));
        }
        return evaluacion;
    }

    private Integer calcularPuntuacion(List<ListaCotejoItem> items, List<CriterioResponse> evaluacion) {
        if (items.isEmpty()) return null;
        int total = items.stream().mapToInt(ListaCotejoItem::getPuntos).sum();
        if (total <= 0) return null;
        int obtenido = evaluacion.stream().filter(CriterioResponse::cumplido)
                .mapToInt(CriterioResponse::puntos).sum();
        return (int) Math.round(obtenido * 100.0 / total);
    }

    private boolean esUltimaVersion(EntregaPractica entrega) {
        return entregas.findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(
                        entrega.getUsuarioId(), entrega.getLeccionId())
                .map(ultima -> ultima.getId().equals(entrega.getId()))
                .orElse(false);
    }

    private void marcarProgresoAprobado(UUID usuarioId, UUID leccionId, Integer puntuacion) {
        ProgresoLeccion p = progreso.findByUsuarioIdAndLeccionId(usuarioId, leccionId)
                .orElseGet(() -> {
                    ProgresoLeccion nuevo = new ProgresoLeccion();
                    nuevo.setUsuarioId(usuarioId);
                    nuevo.setLeccionId(leccionId);
                    return nuevo;
                });
        p.setCompletada(true);
        p.setPuntuacion(puntuacion);
        p.setCompletadoEn(LocalDateTime.now());
        progreso.save(p);
        recalcularInscripcion(usuarioId, acceso.leccion(leccionId).getCursoId());
    }

    private void marcarProgresoRechazado(UUID usuarioId, UUID leccionId) {
        invalidarProgreso(usuarioId, leccionId, acceso.leccion(leccionId).getCursoId());
    }

    private void invalidarProgreso(UUID usuarioId, UUID leccionId, UUID cursoId) {
        progreso.findByUsuarioIdAndLeccionId(usuarioId, leccionId).ifPresent(p -> {
            p.setCompletada(false);
            p.setPuntuacion(null);
            p.setCompletadoEn(null);
            progreso.save(p);
        });
        recalcularInscripcion(usuarioId, cursoId);
    }

    private void recalcularInscripcion(UUID usuarioId, UUID cursoId) {
        var inscripcion = inscripciones.findByUsuarioIdAndCursoId(usuarioId, cursoId)
                .orElse(null);
        if (inscripcion == null) return;

        List<Leccion> leccionesCurso = lecciones.findByCursoIdOrderByOrdenAsc(cursoId);
        Set<UUID> idsCurso = leccionesCurso.stream().map(Leccion::getId)
                .collect(Collectors.toSet());
        Set<UUID> completadas = progreso.findByUsuarioId(usuarioId).stream()
                .filter(pr -> Boolean.TRUE.equals(pr.getCompletada()))
                .map(ProgresoLeccion::getLeccionId)
                .filter(idsCurso::contains)
                .collect(Collectors.toSet());

        int total = leccionesCurso.size();
        int hechas = completadas.size();
        inscripcion.setLeccionesCompletadas(hechas);
        inscripcion.setPorcentaje(total == 0 ? 0 : (int) Math.round(hechas * 100.0 / total));
        String proxima = leccionesCurso.stream()
                .filter(leccion -> !completadas.contains(leccion.getId()))
                .map(Leccion::getTitulo)
                .findFirst()
                .orElse(null);
        inscripcion.setProximaLeccion(proxima);
        inscripciones.save(inscripcion);
    }

    /* ── Validaciones auxiliares ── */
    private boolean firmaValida(String tipo, String contentType, byte[] datos) {
        if (datos == null || datos.length < 4) return false;
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "FOTO" -> switch (mime) {
                case "image/jpeg" -> esJpeg(datos);
                case "image/png" -> esPng(datos);
                case "image/webp" -> esWebp(datos);
                default -> false;
            };
            case "PDF" -> "application/pdf".equals(mime) && contienePdf(datos);
            case "VIDEO" -> switch (mime) {
                case "video/mp4", "video/quicktime" -> esIsoBmff(datos);
                case "video/webm" -> esWebm(datos);
                default -> false;
            };
            default -> false;
        };
    }

    private boolean esJpeg(byte[] d) {
        return d.length >= 3 && (d[0] & 0xff) == 0xff
                && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff;
    }

    private boolean esPng(byte[] d) {
        byte[] firma = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (d.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if (d[i] != firma[i]) return false;
        return true;
    }

    private boolean esWebp(byte[] d) {
        return d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private boolean esIsoBmff(byte[] d) {
        return d.length >= 8 && d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p';
    }

    private boolean esWebm(byte[] d) {
        return d.length >= 4 && (d[0] & 0xff) == 0x1a
                && (d[1] & 0xff) == 0x45 && (d[2] & 0xff) == 0xdf
                && (d[3] & 0xff) == 0xa3;
    }

    private boolean contienePdf(byte[] d) {
        byte[] firma = {'%', 'P', 'D', 'F', '-'};
        for (int i = 0; i <= d.length - firma.length; i++) {
            boolean igual = true;
            for (int j = 0; j < firma.length; j++) {
                if (d[i + j] != firma[j]) { igual = false; break; }
            }
            if (igual) return true;
        }
        return false;
    }

    private void eliminarObjetoInvalidado(String clave) {
        try {
            storage.eliminarObjeto(clave);
        } catch (RuntimeException ex) {
            log.warn("No se pudo eliminar el objeto inválido {}", clave, ex);
        }
    }

    private BusinessException descartarSubidaInvalida(SubidaMultipart sesion, String mensaje) {
        eliminarObjetoInvalidado(sesion.getClave());
        // El objeto S3 ya se borró: la sesión debe desaparecer aunque la
        // excepción haga rollback de la transacción principal.
        if (transactionManager == null) {
            subidas.delete(sesion);
        } else {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    estado -> subidas.deleteById(sesion.getUploadId()));
        }
        return new BusinessException(mensaje);
    }

    private void validarMetadatosSubida(SubidaMultipart sesion, UUID usuarioId,
                                        UUID leccionId, CompletarRequest req) {
        if (!sesion.getUsuarioId().equals(usuarioId)
                || !sesion.getLeccionId().equals(leccionId)) {
            throw new AccessDeniedException("La sesión de subida no pertenece a esta actividad");
        }
        if (!sesion.getClave().equals(req.clave())) {
            throw new BusinessException("La clave del archivo no coincide con la sesión de subida");
        }
        if (req.nombreArchivo() == null) {
            throw new BusinessException("nombreArchivo es requerido");
        }
        validarNombreArchivo(req.nombreArchivo());
        if (!sesion.getNombreArchivo().equals(limitarNombre(req.nombreArchivo()))) {
            throw new BusinessException("El nombre del archivo no coincide con la sesión de subida");
        }
        if (req.contentType() == null
                || !sesion.getContentType().equalsIgnoreCase(req.contentType())) {
            throw new BusinessException("El tipo MIME no coincide con la sesión de subida");
        }
        if (req.tamanoBytes() != sesion.getTamanoBytes()) {
            throw new BusinessException("El tamaño del archivo no coincide con la sesión de subida");
        }
        String tipo = normalizarTipo(req.tipoArchivo(), req.contentType());
        if (!sesion.getTipoArchivo().equals(tipo)) {
            throw new BusinessException("El tipo de archivo no coincide con la sesión de subida");
        }

        int esperadas = sesion.getPartesEsperadas() == null ? 0 : sesion.getPartesEsperadas();
        if (esperadas <= 0 || req.partes().size() != esperadas) {
            throw new BusinessException("La cantidad de partes no coincide con la sesión de subida");
        }
        Set<Integer> numeros = new HashSet<>();
        for (ParteCompletadaDTO parte : req.partes()) {
            if (parte == null || parte.numeroParte() <= 0
                    || parte.etag() == null || parte.etag().isBlank()) {
                throw new BusinessException("Cada parte debe incluir número y ETag válidos");
            }
            if (!numeros.add(parte.numeroParte())) {
                throw new BusinessException("Hay partes duplicadas en la subida");
            }
        }
        for (int numero = 1; numero <= esperadas; numero++) {
            if (!numeros.contains(numero)) {
                throw new BusinessException("Faltan partes de la subida iniciada");
            }
        }
    }

    private void validarNombreArchivo(String nombre) {
        String s = nombre.trim();
        if (s.isEmpty()) throw new BusinessException("nombreArchivo es requerido");
        if (s.length() > 200) throw new BusinessException("nombreArchivo demasiado largo");
        if (s.contains("..") || s.contains("/") || s.contains("\\") || s.matches(".*[\\x00-\\x1f].*")) {
            throw new BusinessException("nombreArchivo contiene caracteres no permitidos");
        }
    }

    private String limitarNombre(String nombre) {
        if (nombre == null) return "evidencia";
        String s = nombre.trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private String normalizarTipo(String tipo, String contentType) {
        String t = tipo == null || tipo.isBlank() ? inferirTipo(contentType) : tipo.trim().toUpperCase();
        if (!Set.of("FOTO", "PDF", "VIDEO").contains(t)) {
            throw new BusinessException("tipoArchivo debe ser FOTO, PDF o VIDEO");
        }
        return t;
    }

    private String inferirTipo(String contentType) {
        if (contentType == null) return "";
        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> "PDF";
            case "image/jpeg", "image/png", "image/webp" -> "FOTO";
            case "video/mp4", "video/webm", "video/quicktime" -> "VIDEO";
            default -> "";
        };
    }

    private void validarArchivo(String tipo, String contentType, long bytes) {
        if (bytes <= 0) throw new BusinessException("tamanoBytes debe ser mayor a 0");
        String mime = contentType == null ? "" : contentType.toLowerCase();
        boolean mimeValida = switch (tipo) {
            case "FOTO" -> Set.of("image/jpeg", "image/png", "image/webp").contains(mime);
            case "PDF" -> "application/pdf".equals(mime);
            case "VIDEO" -> Set.of("video/mp4", "video/webm", "video/quicktime").contains(mime);
            default -> false;
        };
        if (!mimeValida) {
            throw new BusinessException("El tipo de archivo no coincide con el formato permitido");
        }
        long max = "VIDEO".equals(tipo) ? MAX_VIDEO : "PDF".equals(tipo) ? MAX_PDF : MAX_FOTO;
        if (bytes > max) {
            throw new BusinessException("El archivo supera el límite de " + (max / (1024 * 1024)) + " MB");
        }
    }

    private void validarClave(String clave, UUID usuarioId, UUID leccionId) {
        if (clave == null || clave.length() > 500 || clave.contains("..")) {
            throw new BusinessException("Clave de archivo inválida");
        }
        String esperado = "/" + usuarioId + "/" + leccionId + "/";
        if (!clave.contains(esperado)) {
            throw new BusinessException("La clave de archivo no pertenece a esta entrega");
        }
    }

    /* ── DTOs de salida ── */
    private EntregaResponse aDetalle(EntregaPractica entrega) {
        Leccion leccion = lecciones.findById(entrega.getLeccionId()).orElse(null);
        Curso curso = cursos.findById(entrega.getCursoId()).orElse(null);
        List<ArchivoResponse> archivos = evidencias.findByEntregaIdOrderByCreadoEnAsc(entrega.getId())
                .stream().map(a -> new ArchivoResponse(
                        a.getId(), a.getTipoArchivo(), a.getNombreArchivo(), a.getContentType(),
                        a.getTamanoBytes() == null ? 0 : a.getTamanoBytes(),
                        storage.urlLectura(a.getClave()),
                        a.getCreadoEn() == null ? null : a.getCreadoEn().toString()))
                .toList();
        return new EntregaResponse(
                entrega.getId(), entrega.getUsuarioId(), entrega.getLeccionId(),
                leccion == null ? null : leccion.getTitulo(), entrega.getCursoId(),
                curso == null ? null : curso.getTitulo(), entrega.getVersion(), entrega.getEstado(),
                archivos, entrega.getRetroalimentacion(), entrega.getPuntuacion(),
                deserializarCriterios(entrega.getCriteriosEvaluacion()),
                entrega.getCreadoEn() == null ? null : entrega.getCreadoEn().toString(),
                entrega.getRevisadaEn() == null ? null : entrega.getRevisadaEn().toString());
    }

    private JsonNode serializar(List<CriterioResponse> criterios) {
        if (criterios == null || criterios.isEmpty()) return null;
        try {
            return objectMapper.valueToTree(criterios);
        } catch (Exception e) {
            throw new BusinessException("No se pudo guardar la lista de cotejo evaluada");
        }
    }

    private List<CriterioResponse> deserializarCriterios(JsonNode json) {
        if (json == null || json.isNull() || json.isEmpty()) return List.of();
        try {
            return objectMapper.convertValue(json, new TypeReference<List<CriterioResponse>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
