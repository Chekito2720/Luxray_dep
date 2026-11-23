package com.luxray.cursos.service;

import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.model.Curso;
import com.luxray.cursos.model.EvidenciaPractica;
import com.luxray.cursos.model.Leccion;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EvidenciaPracticaRepository;
import com.luxray.cursos.repository.LeccionRepository;
import com.luxray.cursos.service.StoragePresignService.InitMultipartDTO;
import com.luxray.cursos.service.StoragePresignService.ParteUploadDTO;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprobación de la validación de entradas del módulo móvil de evidencias:
 * el backend rechaza nombres de archivo peligrosos (path traversal, caracteres
 * de control), tipos MIME fuera de la lista blanca y tamaños excesivos,
 * independientemente de lo que envíe el cliente.
 */
class EvidenciasServiceTest {

    private static final UUID LECION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CURSO_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EVIDENCIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String AUTH = "Bearer token-de-prueba";

    private EvidenciaPracticaRepository evidencias;
    private LeccionRepository lecciones;
    private CursoRepository cursos;
    private StoragePresignService storage;
    private EvidenciasService service;

    @BeforeEach
    void setUp() {
        evidencias = mock(EvidenciaPracticaRepository.class);
        lecciones = mock(LeccionRepository.class);
        cursos = mock(CursoRepository.class);
        storage = mock(StoragePresignService.class);

        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        Claims claims = mock(Claims.class);
        when(claims.get("uid", String.class)).thenReturn(USUARIO_ID.toString());
        when(jwt.parse(anyString())).thenReturn(claims);

        service = new EvidenciasService(evidencias, lecciones, cursos, storage, jwt);
        when(lecciones.existsById(any(UUID.class))).thenReturn(true);

        // Datos de lección/curso para los detalles del expediente y la revisión
        Leccion leccion = mock(Leccion.class);
        when(leccion.getTitulo()).thenReturn("Instalación de tableros");
        when(leccion.getCursoId()).thenReturn(CURSO_ID);
        when(lecciones.findById(LECION_ID)).thenReturn(Optional.of(leccion));

        Curso curso = mock(Curso.class);
        when(curso.getTitulo()).thenReturn("Electricidad básica");
        when(cursos.findById(CURSO_ID)).thenReturn(Optional.of(curso));
    }

    private EvidenciasService.IniciarRequest iniciarReq(String nombre, String tipo, long bytes) {
        return new EvidenciasService.IniciarRequest(nombre, tipo, bytes);
    }

    @Test
    void rechazaPathTraversalEnNombreDeArchivo() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("../../etc/passwd", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("a/b.jpg", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("a\\b.jpg", "image/jpeg", 1024)));
    }

    @Test
    void rechazaCaracteresDeControlEnNombre() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto\u0000.jpg", "image/jpeg", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("fo\tto.jpg", "image/jpeg", 1024)));
    }

    @Test
    void rechazaTipoMimeFueraDeListaBlanca() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("documento.pdf", "application/pdf", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("script.exe", "application/x-msdownload", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto.gif", "image/gif", 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto.jpg", null, 1024)));
    }

    @Test
    void rechazaTamanoExcesivoOCero() {
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto.jpg", "image/jpeg", 600L * 1024 * 1024)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto.jpg", "image/jpeg", 0)));
        assertThrows(BusinessException.class,
                () -> service.iniciar(AUTH, LECION_ID, iniciarReq("foto.jpg", "image/jpeg", -5)));
    }

    @Test
    void aceptaUnaSolicitudValida() {
        when(storage.nuevaClave(eq(USUARIO_ID), eq(LECION_ID), anyString())).thenReturn("evidencias/x/foto.jpg");
        when(storage.iniciarMultipart(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new InitMultipartDTO("upload-1", "evidencias/x/foto.jpg", 5 * 1024 * 1024,
                        List.of(new ParteUploadDTO(1, "https://bucket/presigned", 1024))));

        var resp = service.iniciar(AUTH, LECION_ID, iniciarReq("foto.jpg", "image/jpeg", 1024));

        assertNotNull(resp);
        assertEquals("upload-1", resp.getData().uploadId());
    }

    @Test
    void completarRechazaListaDePartesVacia() {
        var req = new EvidenciasService.CompletarRequest(
                "upload-1", "evidencias/x/foto.jpg", "foto.jpg", "image/jpeg", 1024, List.of());

        assertThrows(BusinessException.class, () -> service.completar(AUTH, LECION_ID, req));
    }

    @Test
    void completarRevalidaTipoMime() {
        // El cliente no puede colar un contentType no permitido en la confirmación
        var req = new EvidenciasService.CompletarRequest(
                "upload-1", "evidencias/x/doc.exe", "doc.exe", "application/x-msdownload", 1024,
                List.of(new EvidenciasService.ParteCompletadaDTO(1, "etag-1")));

        assertThrows(BusinessException.class, () -> service.completar(AUTH, LECION_ID, req));
    }

    /* ── Módulo de revisión docente ─────────────────────────────── */

    private EvidenciaPractica evidenciaPendiente() {
        EvidenciaPractica e = new EvidenciaPractica();
        e.setId(EVIDENCIA_ID);
        e.setUsuarioId(USUARIO_ID);
        e.setLeccionId(LECION_ID);
        e.setNombreArchivo("practica-tablero.jpg");
        e.setContentType("image/jpeg");
        e.setTamanoBytes(2048L);
        e.setClave("evidencias/x/practica-tablero.jpg");
        e.setEstado("PENDIENTE");
        return e;
    }

    @Test
    void revisarApruebaLaPracticaYQuedaCompletada() {
        EvidenciaPractica e = evidenciaPendiente();
        when(evidencias.findById(EVIDENCIA_ID)).thenReturn(Optional.of(e));

        var resp = service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "Excelente trabajo"));

        assertEquals("APROBADA", resp.getData().estado());
        assertEquals("Excelente trabajo", resp.getData().retroalimentacion());

        // La práctica queda registrada con quién y cuándo la revisó
        ArgumentCaptor<EvidenciaPractica> captor = ArgumentCaptor.forClass(EvidenciaPractica.class);
        verify(evidencias).save(captor.capture());
        assertEquals("APROBADA", captor.getValue().getEstado());
        assertEquals(USUARIO_ID, captor.getValue().getRevisadaPor(), "revisada_por = el profesor que revisa");
        assertNotNull(captor.getValue().getRevisadaEn());
    }

    @Test
    void revisarRechazaConRetroalimentacionObligatoria() {
        EvidenciaPractica e = evidenciaPendiente();
        when(evidencias.findById(EVIDENCIA_ID)).thenReturn(Optional.of(e));

        // Sin explicación del motivo no se puede rechazar
        assertThrows(BusinessException.class, () -> service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("RECHAZADA", "  ")));

        // Con motivo, sí queda registrada la retroalimentación para el alumno
        var resp = service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("RECHAZADA", "Revisa la conexión a tierra"));
        assertEquals("RECHAZADA", resp.getData().estado());
        assertEquals("Revisa la conexión a tierra", resp.getData().retroalimentacion());
    }

    @Test
    void revisarRechazaEstadosFueraDeLaLista() {
        when(evidencias.findById(EVIDENCIA_ID)).thenReturn(Optional.of(evidenciaPendiente()));

        assertThrows(BusinessException.class, () -> service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("COMPLETADA", null)));
        assertThrows(BusinessException.class, () -> service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest(null, null)));
        assertThrows(BusinessException.class, () -> service.revisar(AUTH, EVIDENCIA_ID, null));
    }

    @Test
    void revisarRechazaRetroalimentacionDemasiadoLarga() {
        when(evidencias.findById(EVIDENCIA_ID)).thenReturn(Optional.of(evidenciaPendiente()));
        String larga = "x".repeat(2001);

        assertThrows(BusinessException.class, () -> service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", larga)));
    }

    @Test
    void revisarEvidenciaInexistenteDa404() {
        when(evidencias.findById(EVIDENCIA_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revisar(AUTH, EVIDENCIA_ID,
                new EvidenciasService.RevisionRequest("APROBADA", "ok")));
    }

    @Test
    void listarParaRevisionValidaElFiltroDeEstado() {
        assertThrows(BusinessException.class, () -> service.listarParaRevision("CUALQUIERA"));
        // null / vacío → todas
        assertNotNull(service.listarParaRevision(null));
        assertNotNull(service.listarParaRevision("  "));
        assertNotNull(service.listarParaRevision("pendiente"));
    }

    @Test
    void expedienteDevuelveSoloLasEvidenciasDelAlumnoConDetalles() {
        when(evidencias.findByUsuarioIdOrderByCreadoEnDesc(USUARIO_ID))
                .thenReturn(List.of(evidenciaPendiente()));

        var resp = service.expediente(AUTH);

        assertEquals(1, resp.getData().size());
        var detalle = resp.getData().get(0);
        assertEquals("Electricidad básica", detalle.cursoTitulo());
        assertEquals("Instalación de tableros", detalle.leccionTitulo());
        assertEquals(USUARIO_ID, detalle.usuarioId());
    }
}
