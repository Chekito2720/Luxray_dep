package com.luxray.cursos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.cursos.model.Curso;
import com.luxray.cursos.model.EvidenciaPractica;
import com.luxray.cursos.model.Leccion;
import com.luxray.cursos.model.ListaCotejoItem;
import com.luxray.cursos.model.Inscripcion;
import com.luxray.cursos.model.ProgresoLeccion;
import com.luxray.cursos.model.SubidaMultipart;
import com.luxray.cursos.model.EntregaPractica;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EvidenciaPracticaRepository;
import com.luxray.cursos.repository.InscripcionRepository;
import com.luxray.cursos.repository.LeccionRepository;
import com.luxray.cursos.repository.ListaCotejoItemRepository;
import com.luxray.cursos.repository.ProgresoLeccionRepository;
import com.luxray.cursos.repository.EntregaPracticaRepository;
import com.luxray.cursos.repository.SubidaMultipartRepository;
import com.luxray.cursos.service.CursoAccesoService.Credenciales;
import com.luxray.cursos.service.StoragePresignService.InitMultipartDTO;
import com.luxray.cursos.service.StoragePresignService.ParteUploadDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del modelo versionado de entregas: archivos obligatorios, grupos de
 * versión, lista de cotejo, calificación normalizada sobre 100 y filtros de
 * revisión docente.
 */
class EvidenciasServiceTest {

    private static final UUID LECION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CURSO_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENTREGA_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID PROFESOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final String AUTH_ALUMNO = "Bearer token-alumno";
    private static final String AUTH_PROFESOR = "Bearer token-profesor";

    private EvidenciaPracticaRepository evidencias;
    private EntregaPracticaRepository entregas;
    private SubidaMultipartRepository subidas;
    private ListaCotejoItemRepository cotejos;
    private LeccionRepository lecciones;
    private CursoRepository cursos;
    private ProgresoLeccionRepository progreso;
    private InscripcionRepository inscripciones;
    private StoragePresignService storage;
    private CursoAccesoService acceso;
    private EvidenciasService service;
    private Leccion leccion;

    private final Map<UUID, EntregaPractica> entregasGuardadas = new HashMap<>();
    private final Map<UUID, List<EvidenciaPractica>> archivosPorEntrega = new HashMap<>();
    private final Map<String, SubidaMultipart> sesionesGuardadas = new HashMap<>();

    @BeforeEach
    void setUp() {
        evidencias = mock(EvidenciaPracticaRepository.class);
        entregas = mock(EntregaPracticaRepository.class);
        subidas = mock(SubidaMultipartRepository.class);
        cotejos = mock(ListaCotejoItemRepository.class);
        lecciones = mock(LeccionRepository.class);
        cursos = mock(CursoRepository.class);
        progreso = mock(ProgresoLeccionRepository.class);
        inscripciones = mock(InscripcionRepository.class);
        storage = mock(StoragePresignService.class);
        acceso = mock(CursoAccesoService.class);

        service = new EvidenciasService(
                evidencias, entregas, subidas, cotejos, lecciones, cursos, progreso,
                inscripciones, storage, new ObjectMapper(), acceso,
                new AdvisoryLockService(null), null);

        leccion = new Leccion();
        leccion.setId(LECION_ID);
        leccion.setCursoId(CURSO_ID);
        leccion.setTitulo("Instalación de tableros");
        when(lecciones.findById(LECION_ID)).thenReturn(Optional.of(leccion));
        when(acceso.leccion(LECION_ID)).thenReturn(leccion);
        doNothingOnAccess();

        Curso curso = new Curso();
        curso.setId(CURSO_ID);
        curso.setTitulo("Electricidad básica");
        when(cursos.findById(CURSO_ID)).thenReturn(Optional.of(curso));

        when(acceso.credenciales(AUTH_ALUMNO)).thenReturn(
                new Credenciales(USUARIO_ID, "USER", "Alumno Prueba"));
        when(acceso.credenciales(AUTH_PROFESOR)).thenReturn(
                new Credenciales(PROFESOR_ID, "PROFESOR", "Profesor Prueba"));
        when(acceso.puedeGestionarCurso(eq(PROFESOR_ID), any(UUID.class))).thenReturn(true);
        when(acceso.cursosPermitidos(any(Credenciales.class)))
                .thenReturn(Set.of(CURSO_ID));

        when(entregas.save(any(EntregaPractica.class))).thenAnswer(inv -> {
            EntregaPractica entrega = inv.getArgument(0);
            if (entrega.getId() == null) entrega.setId(UUID.randomUUID());
            entregasGuardadas.put(entrega.getId(), entrega);
            return entrega;
        });
        when(entregas.findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(
                eq(USUARIO_ID), eq(LECION_ID))).thenAnswer(inv -> entregasGuardadas.values().stream()
                .filter(e -> e.getUsuarioId().equals(USUARIO_ID)
                        && e.getLeccionId().equals(LECION_ID))
                .max(Comparator.comparing(EntregaPractica::getVersion)));
        when(evidencias.save(any(EvidenciaPractica.class))).thenAnswer(inv -> {
            EvidenciaPractica archivo = inv.getArgument(0);
            if (archivo.getId() == null) archivo.setId(UUID.randomUUID());
            archivosPorEntrega.computeIfAbsent(archivo.getEntregaId(), k -> new ArrayList<>())
                    .add(archivo);
            return archivo;
        });
        when(evidencias.findByEntregaIdOrderByCreadoEnAsc(any(UUID.class)))
                .thenAnswer(inv -> archivosPorEntrega.getOrDefault(inv.getArgument(0), List.of()));
        when(storage.urlLectura(anyString())).thenAnswer(inv -> "https://storage/" + inv.getArgument(0));
        when(storage.verificarObjeto(anyString()))
                .thenReturn(new StoragePresignService.ObjetoMetadata(1024, null));
        when(storage.leerPrefijo(anyString(), anyInt())).thenAnswer(inv -> {
            String clave = inv.getArgument(0);
            return clave.endsWith(".pdf")
                    ? "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
                    : new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        });
        when(subidas.save(any(SubidaMultipart.class))).thenAnswer(inv -> {
            SubidaMultipart sesion = inv.getArgument(0);
            sesionesGuardadas.put(sesion.getUploadId(), sesion);
            return sesion;
        });
        when(subidas.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(sesionesGuardadas.get(inv.getArgument(0))));
        org.mockito.Mockito.doAnswer(inv -> {
            SubidaMultipart sesion = inv.getArgument(0);
            sesionesGuardadas.remove(sesion.getUploadId());
            return null;
        }).when(subidas).delete(any(SubidaMultipart.class));
        when(storage.nuevaClave(any(UUID.class), any(UUID.class), anyString()))
                .thenAnswer(inv -> "evidencias/" + inv.getArgument(0) + "/"
                        + inv.getArgument(1) + "/" + inv.getArgument(2));
    }

    /** Evita depender de detalles de Mockito para métodos void de acceso. */
    private void doNothingOnAccess() {
        org.mockito.Mockito.doNothing().when(acceso).requiereAlumnoInscrito(any(), any(UUID.class));
        org.mockito.Mockito.doNothing().when(acceso).requiereAccesoCurso(any(), any(UUID.class));
        org.mockito.Mockito.doNothing().when(acceso).requiereGestionLeccion(any(), any(UUID.class));
        org.mockito.Mockito.doNothing().when(acceso).requiereGestionCurso(any(), any(UUID.class));
    }

    private EvidenciasService.IniciarRequest iniciarReq(String nombre, String tipo, long bytes) {
        return new EvidenciasService.IniciarRequest(nombre, tipo, bytes);
    }

    @Test
    void rechazaPathTraversalEnNombreDeArchivo() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("../../etc/passwd", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("a/b.jpg", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("a\\b.jpg", "image/jpeg", 1024)));
    }

    @Test
    void rechazaCaracteresDeControlEnNombre() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("foto\u0000.jpg", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("fo\tto.jpg", "image/jpeg", 1024)));
    }

    @Test
    void rechazaTipoMimeFueraDeListaBlanca() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("documento.svg", "image/svg+xml", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("script.exe", "application/x-msdownload", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("foto.gif", "image/gif", 1024)));
    }

    @Test
    void rechazaTamanoExcesivoOCero() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("foto.jpg", "image/jpeg", 600L * 1024 * 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH_ALUMNO, LECION_ID,
                        iniciarReq("foto.jpg", "image/jpeg", 0)));
    }

    @Test
    void aceptaUnaSolicitudValida() {
        when(storage.iniciarMultipart(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new InitMultipartDTO("upload-1", "evidencias/x/foto.jpg",
                        5 * 1024 * 1024,
                        List.of(new ParteUploadDTO(1, "https://bucket/presigned", 1024))));

        var resp = service.iniciar(AUTH_ALUMNO, LECION_ID,
                iniciarReq("foto.jpg", "image/jpeg", 1024));

        assertNotNull(resp);
        assertEquals("upload-1", resp.getData().uploadId());
    }

    @Test
    void completarRechazaListaDePartesVacia() {
        var req = new EvidenciasService.CompletarRequest(
                "upload-1", "evidencias/x/foto.jpg", "foto.jpg", "image/jpeg", 1024, List.of());

        assertThrows(BusinessException.class,
                () -> service.completar(AUTH_ALUMNO, LECION_ID, req));
    }

    @Test
    void completarRevalidaTipoMime() {
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/foto.jpg";
        SubidaMultipart sesion = sesion("upload-mime", clave, "foto.jpg", "image/jpeg", 1024, "FOTO");
        sesionesGuardadas.put(sesion.getUploadId(), sesion);
        var req = new EvidenciasService.CompletarRequest(
                "upload-mime", clave, "foto.jpg", "application/x-msdownload", 1024, "FOTO",
                List.of(new EvidenciasService.ParteCompletadaDTO(1, "etag-1")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completar(AUTH_ALUMNO, LECION_ID, req));
        assertTrue(error.getMessage().contains("tipo MIME"));
    }

    @Test
    void completarRechazaTodaMetadatosQueNoCoincidanConLaSesion() {
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/foto.jpg";
        SubidaMultipart sesion = sesion("upload-meta", clave, "foto.jpg", "image/jpeg", 1024, "FOTO");
        sesionesGuardadas.put(sesion.getUploadId(), sesion);

        List<EvidenciasService.CompletarRequest> invalidos = List.of(
                new EvidenciasService.CompletarRequest("upload-meta", clave, "otro.jpg",
                        "image/jpeg", 1024, "FOTO", partesValidas()),
                new EvidenciasService.CompletarRequest("upload-meta", clave, "foto.jpg",
                        "image/png", 1024, "FOTO", partesValidas()),
                new EvidenciasService.CompletarRequest("upload-meta", clave, "foto.jpg",
                        "image/jpeg", 2048, "FOTO", partesValidas()),
                new EvidenciasService.CompletarRequest("upload-meta", clave, "foto.jpg",
                        "image/jpeg", 1024, "PDF", partesValidas()),
                new EvidenciasService.CompletarRequest("upload-meta", clave + "-otro", "foto.jpg",
                        "image/jpeg", 1024, "FOTO", partesValidas()),
                new EvidenciasService.CompletarRequest("upload-meta", clave, "foto.jpg",
                        "image/jpeg", 1024, "FOTO", List.of(
                                new EvidenciasService.ParteCompletadaDTO(1, "etag-1"),
                                new EvidenciasService.ParteCompletadaDTO(1, "etag-2"))),
                new EvidenciasService.CompletarRequest("upload-meta", clave, "foto.jpg",
                        "image/jpeg", 1024, "FOTO", List.of(
                                new EvidenciasService.ParteCompletadaDTO(1, " "))));

        for (EvidenciasService.CompletarRequest request : invalidos) {
            assertThrows(BusinessException.class,
                    () -> service.completar(AUTH_ALUMNO, LECION_ID, request));
        }
        assertTrue(sesionesGuardadas.containsKey("upload-meta"),
                "Un fallo de validación no debe consumir la sesión");
        verify(evidencias, never()).save(any(EvidenciaPractica.class));
    }

    @Test
    void rechazaContenidoQueNoCorrespondeAlFormatoDeclarado() {
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/falsa-foto.jpg";
        SubidaMultipart sesion = sesion("upload-falso", clave, "falsa-foto.jpg",
                "image/jpeg", 1024, "FOTO");
        sesionesGuardadas.put(sesion.getUploadId(), sesion);
        when(storage.leerPrefijo(clave, 1024)).thenReturn(
                "texto plano que no es una imagen".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request = new EvidenciasService.CompletarRequest(
                "upload-falso", clave, "falsa-foto.jpg", "image/jpeg", 1024, "FOTO",
                partesValidas());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completar(AUTH_ALUMNO, LECION_ID, request));
        assertTrue(error.getMessage().contains("contenido"));
        verify(storage).eliminarObjeto(clave);
        verify(evidencias, never()).save(any(EvidenciaPractica.class));
    }

    @Test
    void rechazaUnaFirmaQueNoCoincideConElMimeDeclarado() {
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/png-declarado-jpeg.jpg";
        SubidaMultipart sesion = sesion("upload-mime-incorrecto", clave,
                "png-declarado-jpeg.jpg", "image/jpeg", 1024, "FOTO");
        sesionesGuardadas.put(sesion.getUploadId(), sesion);
        when(storage.leerPrefijo(clave, 1024)).thenReturn(new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        var request = new EvidenciasService.CompletarRequest(
                "upload-mime-incorrecto", clave, "png-declarado-jpeg.jpg",
                "image/jpeg", 1024, "FOTO", partesValidas());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completar(AUTH_ALUMNO, LECION_ID, request));
        assertTrue(error.getMessage().contains("contenido"));
        verify(storage).eliminarObjeto(clave);
    }

    @Test
    void unaSesionDeSubidaNoPuedeUsarsePorOtroAlumno() {
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/foto.jpg";
        SubidaMultipart sesion = sesion("upload-ajeno", clave, "foto.jpg", "image/jpeg", 1024, "FOTO");
        sesion.setUsuarioId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        sesionesGuardadas.put(sesion.getUploadId(), sesion);
        var request = new EvidenciasService.CompletarRequest(
                "upload-ajeno", clave, "foto.jpg", "image/jpeg", 1024, "FOTO", partesValidas());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.completar(AUTH_ALUMNO, LECION_ID, request));
    }

    private List<EvidenciasService.ParteCompletadaDTO> partesValidas() {
        return List.of(new EvidenciasService.ParteCompletadaDTO(1, "etag-1"));
    }

    @Test
    void fotoYPdfQuedanEnLaMismaVersionYLaMarcanPendiente() {
        var foto = service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("foto.jpg", "image/jpeg", "FOTO"));
        var pdf = service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("practica.pdf", "application/pdf", "PDF"));

        assertEquals(1, foto.getData().version());
        assertEquals(1, pdf.getData().version());
        assertEquals("PENDIENTE", pdf.getData().estado());
        assertEquals(2, pdf.getData().archivos().size());
        assertFalse(service.puedeMarcarCompletada(USUARIO_ID, LECION_ID),
                "Una entrega pendiente todavía no puede completar la actividad");
    }

    @Test
    void cambiarUnArchivoDespuesDeCompletarCreaUnaVersionNueva() {
        service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("foto.jpg", "image/jpeg", "FOTO"));
        service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("practica.pdf", "application/pdf", "PDF"));

        var nueva = service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("foto-corregida.jpg", "image/jpeg", "FOTO"));

        assertEquals(2, nueva.getData().version());
        assertEquals("INCOMPLETA", nueva.getData().estado());
        assertEquals(1, nueva.getData().archivos().size());
        assertEquals(2, entregasGuardadas.size());
    }

    private EvidenciasService.CompletarRequest requestCompleto(
            String nombre, String contentType, String tipo) {
        String uploadId = "upload-" + nombre;
        String clave = "evidencias/" + USUARIO_ID + "/" + LECION_ID + "/" + nombre;
        SubidaMultipart sesion = sesion(uploadId, clave, nombre, contentType, 1024, tipo);
        sesionesGuardadas.put(uploadId, sesion);
        return new EvidenciasService.CompletarRequest(
                uploadId, clave, nombre, contentType, 1024, tipo,
                List.of(new EvidenciasService.ParteCompletadaDTO(1, "etag-1")));
    }

    private SubidaMultipart sesion(String uploadId, String clave, String nombre,
                                   String contentType, long bytes, String tipo) {
        SubidaMultipart sesion = new SubidaMultipart();
        sesion.setUploadId(uploadId);
        sesion.setUsuarioId(USUARIO_ID);
        sesion.setLeccionId(LECION_ID);
        sesion.setClave(clave);
        sesion.setNombreArchivo(nombre);
        sesion.setContentType(contentType);
        sesion.setTamanoBytes(bytes);
        sesion.setTipoArchivo(tipo);
        sesion.setPartesEsperadas(1);
        sesion.setCreadoEn(LocalDateTime.now());
        sesion.setExpiraEn(LocalDateTime.now().plusHours(2));
        return sesion;
    }

    @Test
    void revisarExigeFotoYPdfParaPoderCalificar() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("PENDIENTE");
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO")));

        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "Bien")));
    }

    @Test
    void revisarAprobadaNormalizaLaNotaSobreCien() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("PENDIENTE");
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));

        UUID criterioUno = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID criterioDos = UUID.fromString("00000000-0000-0000-0000-000000000012");
        when(cotejos.findByLeccionIdOrderByOrdenAsc(LECION_ID)).thenReturn(List.of(
                criterio(criterioUno, "Montaje correcto", 30, 1),
                criterio(criterioDos, "Cableado terminado", 70, 2)));

        var resp = service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "Excelente trabajo", List.of(
                        new EvidenciasService.CriterioRequest(criterioUno, true),
                        new EvidenciasService.CriterioRequest(criterioDos, false))));

        assertEquals("APROBADA", resp.getData().estado());
        assertEquals(30, resp.getData().puntuacion());
        assertEquals("Excelente trabajo", resp.getData().retroalimentacion());
        assertEquals(PROFESOR_ID, entrega.getRevisadaPor());
        assertNotNull(entrega.getRevisadaEn());
    }

    @Test
    void revisarRechazadaExplicaElMotivoYNoAsignaNota() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("PENDIENTE");
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));

        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("RECHAZADA", "  ")));

        var resp = service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("RECHAZADA", "Revisa la conexión a tierra"));

        assertEquals("RECHAZADA", resp.getData().estado());
        assertEquals("Revisa la conexión a tierra", resp.getData().retroalimentacion());
        assertNull(resp.getData().puntuacion());
    }

    @Test
    void rechazarLaVersionVigenteRevierteElProgresoYElPorcentaje() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("PENDIENTE");
        entregasGuardadas.put(ENTREGA_ID, entrega);
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));

        ProgresoLeccion progresoAprobado = progresoAprobado();
        Inscripcion inscripcion = inscripcionActiva();
        when(progreso.findByUsuarioIdAndLeccionId(USUARIO_ID, LECION_ID))
                .thenReturn(Optional.of(progresoAprobado));
        when(progreso.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(progresoAprobado));
        when(inscripciones.findByUsuarioIdAndCursoId(USUARIO_ID, CURSO_ID))
                .thenReturn(Optional.of(inscripcion));
        when(lecciones.findByCursoIdOrderByOrdenAsc(CURSO_ID)).thenReturn(List.of(leccion));

        var resp = service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("RECHAZADA", "Corrige el diagrama"));

        assertEquals("RECHAZADA", resp.getData().estado());
        assertFalse(progresoAprobado.getCompletada());
        assertNull(progresoAprobado.getPuntuacion());
        assertNull(progresoAprobado.getCompletadoEn());
        assertEquals(0, inscripcion.getLeccionesCompletadas());
        assertEquals(0, inscripcion.getPorcentaje());
    }

    @Test
    void unaEntregaPendienteORechazadaNoPuedeCompletarLaActividad() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("RECHAZADA");
        when(entregas.findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(
                USUARIO_ID, LECION_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));

        assertFalse(service.puedeMarcarCompletada(USUARIO_ID, LECION_ID));

        entrega.setEstado("APROBADA");
        assertTrue(service.puedeMarcarCompletada(USUARIO_ID, LECION_ID));
    }

    @Test
    void publicarUnaVersionDeReemplazoInvalidaElProgresoDeLaAnterior() {
        EntregaPractica anterior = entregaVersion(1);
        anterior.setEstado("APROBADA");
        anterior.setPuntuacion(100);
        entregasGuardadas.put(ENTREGA_ID, anterior);

        ProgresoLeccion progresoAprobado = progresoAprobado();
        Inscripcion inscripcion = inscripcionActiva();
        when(progreso.findByUsuarioIdAndLeccionId(USUARIO_ID, LECION_ID))
                .thenReturn(Optional.of(progresoAprobado));
        when(progreso.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(progresoAprobado));
        when(inscripciones.findByUsuarioIdAndCursoId(USUARIO_ID, CURSO_ID))
                .thenReturn(Optional.of(inscripcion));
        when(lecciones.findByCursoIdOrderByOrdenAsc(CURSO_ID)).thenReturn(List.of(leccion));

        var nueva = service.completar(AUTH_ALUMNO, LECION_ID,
                requestCompleto("foto-corregida.jpg", "image/jpeg", "FOTO"));

        assertEquals(2, nueva.getData().version());
        assertEquals("INCOMPLETA", nueva.getData().estado());
        assertFalse(progresoAprobado.getCompletada());
        assertNull(progresoAprobado.getPuntuacion());
        assertNull(progresoAprobado.getCompletadoEn());
        assertEquals(0, inscripcion.getPorcentaje());
    }

    @Test
    void aprobarSinListaDeCotejoNoEsPermitido() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setEstado("PENDIENTE");
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));
        when(cotejos.findByLeccionIdOrderByOrdenAsc(LECION_ID)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "Bien")));
    }

    @Test
    void revisarRechazaEstadosFueraDeLaLista() {
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.of(entregaVersion(1)));

        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("COMPLETADA", null)));
        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest(null, null)));
        assertThrows(BusinessException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID, null));
    }

    @Test
    void revisarEvidenciaInexistenteDa404() {
        when(entregas.findById(ENTREGA_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revisar(AUTH_PROFESOR, ENTREGA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "ok")));
    }

    @Test
    void listarParaRevisionValidaElFiltroDeEstado() {
        when(entregas.findByCursoIdInOrderByCreadoEnAsc(Set.of(CURSO_ID))).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.listarParaRevision(AUTH_PROFESOR, "CUALQUIERA"));
        assertNotNull(service.listarParaRevision(AUTH_PROFESOR, null));
        assertNotNull(service.listarParaRevision(AUTH_PROFESOR, "pendiente"));
    }

    @Test
    void expedienteDevuelveLasEntregasDelAlumnoConDetalles() {
        EntregaPractica entrega = entregaVersion(1);
        entrega.setUsuarioId(USUARIO_ID);
        when(entregas.findByUsuarioIdOrderByCreadoEnDesc(USUARIO_ID)).thenReturn(List.of(entrega));
        archivosPorEntrega.put(ENTREGA_ID, List.of(archivo("FOTO"), archivo("PDF")));

        var resp = service.expediente(AUTH_ALUMNO);

        assertEquals(1, resp.getData().size());
        var detalle = resp.getData().get(0);
        assertEquals("Electricidad básica", detalle.cursoTitulo());
        assertEquals("Instalación de tableros", detalle.leccionTitulo());
        assertEquals(USUARIO_ID, detalle.usuarioId());
        assertEquals(2, detalle.archivos().size());
    }

    @Test
    void guardarListaCotejoConservaLosIdsExistentes() {
        UUID criterioId = UUID.randomUUID();
        ListaCotejoItem actual = new ListaCotejoItem();
        actual.setId(criterioId);
        actual.setLeccionId(LECION_ID);
        actual.setDescripcion("Criterio anterior");
        actual.setPuntos(40);
        actual.setOrden(1);
        when(cotejos.findByLeccionIdOrderByOrdenAsc(LECION_ID)).thenReturn(List.of(actual));
        when(cotejos.save(any(ListaCotejoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.guardarListaCotejo(AUTH_PROFESOR, LECION_ID,
                List.of(new EvidenciasService.CriterioConfigRequest(
                        "Criterio actualizado", 40, criterioId)));

        assertEquals(criterioId, respuesta.getData().get(0).id());
        assertEquals("Criterio actualizado", respuesta.getData().get(0).descripcion());
        verify(cotejos, never()).delete(any(ListaCotejoItem.class));
    }

    private ProgresoLeccion progresoAprobado() {
        ProgresoLeccion progreso = new ProgresoLeccion();
        progreso.setUsuarioId(USUARIO_ID);
        progreso.setLeccionId(LECION_ID);
        progreso.setCompletada(true);
        progreso.setPuntuacion(100);
        progreso.setCompletadoEn(LocalDateTime.now());
        return progreso;
    }

    private Inscripcion inscripcionActiva() {
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setUsuarioId(USUARIO_ID);
        inscripcion.setCursoId(CURSO_ID);
        inscripcion.setLeccionesCompletadas(1);
        inscripcion.setPorcentaje(100);
        inscripcion.setProximaLeccion(null);
        return inscripcion;
    }

    private EntregaPractica entregaVersion(int version) {
        EntregaPractica entrega = new EntregaPractica();
        entrega.setId(ENTREGA_ID);
        entrega.setUsuarioId(USUARIO_ID);
        entrega.setLeccionId(LECION_ID);
        entrega.setCursoId(CURSO_ID);
        entrega.setVersion(version);
        entrega.setEstado("PENDIENTE");
        entrega.setCreadoEn(LocalDateTime.now());
        entrega.setActualizadoEn(LocalDateTime.now());
        return entrega;
    }

    private EvidenciaPractica archivo(String tipo) {
        EvidenciaPractica archivo = new EvidenciaPractica();
        archivo.setId(UUID.randomUUID());
        archivo.setUsuarioId(USUARIO_ID);
        archivo.setLeccionId(LECION_ID);
        archivo.setEntregaId(ENTREGA_ID);
        archivo.setTipoArchivo(tipo);
        archivo.setNombreArchivo(tipo.toLowerCase() + ".bin");
        archivo.setContentType("FOTO".equals(tipo) ? "image/jpeg" : "application/pdf");
        archivo.setTamanoBytes(1024L);
        archivo.setClave("evidencias/x/archivo");
        archivo.setEstado("PENDIENTE");
        return archivo;
    }

    private ListaCotejoItem criterio(UUID id, String descripcion, int puntos, int orden) {
        ListaCotejoItem item = new ListaCotejoItem();
        item.setId(id);
        item.setLeccionId(LECION_ID);
        item.setDescripcion(descripcion);
        item.setPuntos(puntos);
        item.setOrden(orden);
        return item;
    }
}
