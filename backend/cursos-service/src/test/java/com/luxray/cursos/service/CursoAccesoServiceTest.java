package com.luxray.cursos.service;

import com.luxray.common.exception.BusinessException;
import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.model.Curso;
import com.luxray.cursos.repository.CursoProfesorRepository;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.EstadoAsignacionesRepository;
import com.luxray.cursos.repository.InscripcionRepository;
import com.luxray.cursos.repository.LeccionRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CursoAccesoServiceTest {

    private static final UUID CURSO = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ALUMNO = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID PROFESOR = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000104");

    private CursoRepository cursos;
    private LeccionRepository lecciones;
    private InscripcionRepository inscripciones;
    private CursoProfesorRepository asignaciones;
    private EstadoAsignacionesRepository estadoAsignaciones;
    private UsuarioActualService usuariosActuales;
    private CursoAccesoService service;

    @BeforeEach
    void setUp() {
        cursos = mock(CursoRepository.class);
        lecciones = mock(LeccionRepository.class);
        inscripciones = mock(InscripcionRepository.class);
        asignaciones = mock(CursoProfesorRepository.class);
        estadoAsignaciones = mock(EstadoAsignacionesRepository.class);
        usuariosActuales = mock(UsuarioActualService.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        Claims claims = mock(Claims.class);
        when(jwt.parse(anyString())).thenReturn(claims);
        when(claims.get("uid", String.class)).thenReturn(ALUMNO.toString());
        when(claims.get("role", String.class)).thenReturn("USER");
        when(claims.get("nombre", String.class)).thenReturn("Alumno Prueba");

        service = new CursoAccesoService(jwt, cursos, lecciones, inscripciones,
                asignaciones, estadoAsignaciones, usuariosActuales, true);
        when(cursos.existsById(CURSO)).thenReturn(true);
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(ALUMNO, "USER", true));
    }

    @Test
    void elAlumnoInscritoAccedeASuClase() {
        when(inscripciones.findByUsuarioIdAndCursoId(ALUMNO, CURSO))
                .thenReturn(Optional.of(mock(com.luxray.cursos.model.Inscripcion.class)));

        var cred = service.credenciales("Bearer token");
        assertDoesNotThrow(() -> service.requiereAccesoCurso(cred, CURSO));
    }

    @Test
    void elAlumnoNoInscritoNoAccede() {
        when(inscripciones.findByUsuarioIdAndCursoId(ALUMNO, CURSO)).thenReturn(Optional.empty());

        var cred = service.credenciales("Bearer token");
        assertThrows(AccessDeniedException.class,
                () -> service.requiereAccesoCurso(cred, CURSO));
    }

    @Test
    void elProfesorAsignadoGestionaSuClase() {
        when(asignaciones.existsByCursoIdAndProfesorId(CURSO, PROFESOR)).thenReturn(true);
        var cred = new CursoAccesoService.Credenciales(PROFESOR, "PROFESOR", "Docente");

        assertDoesNotThrow(() -> service.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void elProfesorNoAsignadoPierdeElAccesoGlobalCuandoYaHayAsignaciones() {
        when(asignaciones.existsByCursoIdAndProfesorId(CURSO, PROFESOR)).thenReturn(false);
        when(estadoAsignaciones.estaInicializado()).thenReturn(true);
        var cred = new CursoAccesoService.Credenciales(PROFESOR, "PROFESOR", "Docente");

        assertThrows(AccessDeniedException.class,
                () -> service.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void elProfesorSinAsignacionesTieneAccesoGlobalSoloAntesDeConfigurarlas() {
        when(asignaciones.existsByCursoIdAndProfesorId(CURSO, PROFESOR)).thenReturn(false);
        when(estadoAsignaciones.estaInicializado()).thenReturn(false);
        var cred = new CursoAccesoService.Credenciales(PROFESOR, "PROFESOR", "Docente");

        assertDoesNotThrow(() -> service.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void borrarLaUltimaAsignacionNoReactivaElAccesoGlobal() {
        when(asignaciones.existsByCursoIdAndProfesorId(CURSO, PROFESOR)).thenReturn(false);
        when(estadoAsignaciones.estaInicializado()).thenReturn(true);
        var cred = new CursoAccesoService.Credenciales(PROFESOR, "PROFESOR", "Docente");

        assertThrows(AccessDeniedException.class,
                () -> service.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void elFallbackGlobalPuedeDesactivarsePorConfiguracion() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        CursoAccesoService sinFallback = new CursoAccesoService(
                jwt, cursos, lecciones, inscripciones, asignaciones, estadoAsignaciones,
                usuariosActuales, false);
        when(asignaciones.existsByCursoIdAndProfesorId(CURSO, PROFESOR)).thenReturn(false);
        when(estadoAsignaciones.estaInicializado()).thenReturn(false);
        var cred = new CursoAccesoService.Credenciales(PROFESOR, "PROFESOR", "Docente");

        assertThrows(AccessDeniedException.class,
                () -> sinFallback.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void unUsuarioInactivoPierdeElAcceroAunqueSuJwtSeaValido() {
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(ALUMNO, "USER", false));
        var cred = service.credenciales("Bearer token");

        assertThrows(AccessDeniedException.class,
                () -> service.requiereAccesoCurso(cred, CURSO));
    }

    @Test
    void unCambioDeRolInvalidaLosPermisosDelTokenAntiguo() {
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(ALUMNO, "PROFESOR", true));
        var cred = service.credenciales("Bearer token");

        assertThrows(AccessDeniedException.class,
                () -> service.requiereAccesoCurso(cred, CURSO));
    }

    @Test
    void elAdminGestionaTodasLasClases() {
        var cred = new CursoAccesoService.Credenciales(ADMIN, "ADMIN", "Administrador");

        assertDoesNotThrow(() -> service.requiereGestionCurso(cred, CURSO));
    }

    @Test
    void elAdminRecibeTodosLosCursosEnLaColaDeRevision() {
        Curso curso = new Curso();
        curso.setId(CURSO);
        when(cursos.findAll()).thenReturn(List.of(curso));
        var cred = new CursoAccesoService.Credenciales(ADMIN, "ADMIN", "Administrador");

        assertEquals(List.of(CURSO), List.copyOf(service.cursosPermitidos(cred)));
    }

    @Test
    void tokenInvalidoProduceUnErrorDeNegocioSinFiltrarLaCausa() {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        when(jwt.parse(anyString())).thenThrow(new IllegalArgumentException("detalle privado"));
        CursoAccesoService conJwt = new CursoAccesoService(
                jwt, cursos, lecciones, inscripciones, asignaciones, estadoAsignaciones,
                usuariosActuales, true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> conJwt.credenciales("Bearer invalido"));
        assertEquals("Token inválido o expirado", error.getMessage());
    }
}
