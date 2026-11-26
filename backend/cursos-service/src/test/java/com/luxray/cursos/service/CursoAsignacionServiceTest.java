package com.luxray.cursos.service;

import com.luxray.cursos.model.CursoProfesor;
import com.luxray.cursos.repository.CursoProfesorRepository;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EstadoAsignacionesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CursoAsignacionServiceTest {

    private static final UUID CURSO = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID PROFESOR = UUID.fromString("00000000-0000-0000-0000-000000000402");

    private CursoProfesorRepository asignaciones;
    private CursoRepository cursos;
    private EstadoAsignacionesRepository estadoAsignaciones;
    private MockRestServiceServer authServer;
    private CursoAsignacionService service;

    @BeforeEach
    void setUp() {
        asignaciones = mock(CursoProfesorRepository.class);
        cursos = mock(CursoRepository.class);
        estadoAsignaciones = mock(EstadoAsignacionesRepository.class);
        when(cursos.existsById(CURSO)).thenReturn(true);
        when(asignaciones.findByCursoId(CURSO)).thenReturn(List.of());

        RestClient.Builder builder = RestClient.builder();
        authServer = MockRestServiceServer.bindTo(builder).build();
        service = new CursoAsignacionService(asignaciones, cursos, estadoAsignaciones,
                new AdvisoryLockService(null), builder, "http://auth.test");
    }

    @Test
    void deduplicaLaAsignacionDelMismoProfesor() {
        authServer.expect(requestTo(
                        "http://auth.test/auth/usuarios?ids=" + PROFESOR))
                .andRespond(withSuccess(
                        "{\"success\":true,\"data\":[{\"id\":\"" + PROFESOR
                                + "\",\"rol\":\"PROFESOR\",\"activo\":true}]}",
                        MediaType.APPLICATION_JSON));

        var respuesta = service.reemplazar("Bearer token", CURSO,
                new CursoAsignacionService.AsignacionRequest(
                        List.of(PROFESOR, PROFESOR)));

        assertEquals(List.of(PROFESOR), respuesta.getData());
        verify(asignaciones, times(1)).save(any(CursoProfesor.class));
        verify(estadoAsignaciones, times(1)).marcarInicializado();
        authServer.verify();
    }

    @Test
    void conservaUnaAsignacionExistenteSinDuplicarla() {
        CursoProfesor actual = new CursoProfesor();
        actual.setCursoId(CURSO);
        actual.setProfesorId(PROFESOR);
        when(asignaciones.findByCursoId(CURSO)).thenReturn(List.of(actual));
        authServer.expect(requestTo(
                        "http://auth.test/auth/usuarios?ids=" + PROFESOR))
                .andRespond(withSuccess(
                        "{\"success\":true,\"data\":[{\"id\":\"" + PROFESOR
                                + "\",\"rol\":\"PROFESOR\",\"activo\":true}]}",
                        MediaType.APPLICATION_JSON));

        var respuesta = service.reemplazar("Bearer token", CURSO,
                new CursoAsignacionService.AsignacionRequest(List.of(PROFESOR)));

        assertEquals(List.of(PROFESOR), respuesta.getData());
        verify(asignaciones, times(0)).save(any(CursoProfesor.class));
        authServer.verify();
    }

    @Test
    void guardarUnaListaVaciaTambienCierraElFallbackGlobal() {
        var respuesta = service.reemplazar("Bearer token", CURSO,
                new CursoAsignacionService.AsignacionRequest(List.of()));

        assertEquals(List.of(), respuesta.getData());
        verify(estadoAsignaciones, times(1)).marcarInicializado();
        authServer.verify();
    }
}
