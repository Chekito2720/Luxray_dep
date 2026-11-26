package com.luxray.cursos.service;

import com.luxray.common.exception.BusinessException;
import com.luxray.cursos.model.MensajeForo;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.MensajeForoRepository;
import com.luxray.cursos.service.CursoAccesoService.Credenciales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForoServiceTest {

    private static final UUID CURSO = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ALUMNO = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final String AUTH = "Bearer token";

    private MensajeForoRepository mensajes;
    private CursoRepository cursos;
    private CursoAccesoService acceso;
    private ForoService service;

    @BeforeEach
    void setUp() {
        mensajes = mock(MensajeForoRepository.class);
        cursos = mock(CursoRepository.class);
        acceso = mock(CursoAccesoService.class);
        service = new ForoService(mensajes, cursos, acceso);
        when(cursos.existsById(CURSO)).thenReturn(true);
        when(mensajes.save(any(MensajeForo.class))).thenAnswer(inv -> {
            MensajeForo mensaje = inv.getArgument(0);
            if (mensaje.getId() == null) mensaje.setId(UUID.randomUUID());
            return mensaje;
        });
    }

    @Test
    void elHistorialDevuelveLosUltimosMensajesEnOrdenCronologico() {
        MensajeForo nuevo = mensaje("nuevo", LocalDateTime.of(2026, 11, 23, 12, 0));
        MensajeForo viejo = mensaje("viejo", LocalDateTime.of(2026, 11, 23, 10, 0));
        // El repositorio entrega DESC; el servicio debe devolverlo ASC.
        when(mensajes.findTop100ByCursoIdOrderByCreadoEnDesc(CURSO))
                .thenReturn(List.of(nuevo, viejo));

        var respuesta = service.listar(AUTH, CURSO);

        assertEquals(List.of("viejo", "nuevo"),
                respuesta.getData().stream().map(m -> m.contenido()).toList());
    }

    @Test
    void crearGuardaElMensajeRecortadoYConAutoridad() {
        var cred = new Credenciales(ALUMNO, "USER", "Ana Alumna");
        doNothing().when(acceso).requiereAccesoCurso(cred, CURSO);

        var respuesta = service.crear(cred, CURSO, "  Pregunta del alumno  ");

        assertEquals("Pregunta del alumno", respuesta.contenido());
        assertEquals("Ana Alumna", respuesta.autorNombre());
        assertEquals("USER", respuesta.autorRol());
        verify(mensajes).save(any(MensajeForo.class));
    }

    @Test
    void noSeAceptanMensajesVaciosNiExcesivamenteLargos() {
        var cred = new Credenciales(ALUMNO, "USER", "Ana Alumna");
        doNothing().when(acceso).requiereAccesoCurso(cred, CURSO);

        assertThrows(BusinessException.class, () -> service.crear(cred, CURSO, "   "));
        assertThrows(BusinessException.class,
                () -> service.crear(cred, CURSO, "x".repeat(2001)));
    }

    @Test
    void unParticipanteSinAccesoNoPuedePublicar() {
        var cred = new Credenciales(ALUMNO, "USER", "Ana Alumna");
        doThrow(new AccessDeniedException("No tienes acceso"))
                .when(acceso).requiereAccesoCurso(cred, CURSO);

        assertThrows(AccessDeniedException.class,
                () -> service.crear(cred, CURSO, "Mensaje"));
    }

    private MensajeForo mensaje(String contenido, LocalDateTime fecha) {
        MensajeForo mensaje = new MensajeForo();
        mensaje.setId(UUID.randomUUID());
        mensaje.setCursoId(CURSO);
        mensaje.setUsuarioId(ALUMNO);
        mensaje.setAutorNombre("Ana Alumna");
        mensaje.setAutorRol("USER");
        mensaje.setContenido(contenido);
        mensaje.setCreadoEn(fecha);
        return mensaje;
    }
}
