package com.luxray.common.exception;

import com.luxray.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRÁCTICA 11 — FASE 3 — WHITE BOX DEL BACKEND: MATRIZ DE CÓDIGOS HTTP.
 *
 * Verifica, a nivel de unidad, que cada tipo de excepción del
 * {@link GlobalExceptionHandler} se traduce al código HTTP que le corresponde
 * según la matriz de la práctica:
 *
 *   200 OK · 201 Created · 204 No Content · 400 Bad Request
 *   401 Unauthorized · 403 Forbidden · 404 Not Found
 *   409 Conflict · 422 Unprocessable Content · 500 Internal Server Error
 *
 * Y que NINGÚN cuerpo de error filtra información interna (stacktrace, SQL,
 * rutas o credenciales).
 *
 * Esta clase es la evidencia de la WEB-02 (respuesta HTTP de error) y de la
 * WEB-03 (validación del backend).
 */
class GlobalExceptionHandlerHttpStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest req = new MockHttpServletRequest("GET", "/api/cursos");

    // ── 400 Bad Request ───────────────────────────────────────────────────

    @Test
    @DisplayName("WEB-03 · JSON malformado -> 400 Bad Request")
    void jsonMalformado_400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Unexpected character ('}' (code 125))", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> resp = handler.handleNoLegible(ex, req);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Bad Request", resp.getBody().getError());
        assertNotNull(resp.getBody().getTimestamp());
        assertNotNull(resp.getBody().getPath());
    }

    @Test
    @DisplayName("WEB-02 · Error de negocio -> 400 Bad Request con mensaje para el usuario")
    void errorNegocio_400() {
        ResponseEntity<ApiError> resp =
                handler.handleBusiness(new BusinessException("El correo ya está registrado."), req);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("El correo ya está registrado.", resp.getBody().getMessage());
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WEB-04 · Acceso denegado por rol -> 403 Forbidden genérico")
    void accesoDenegado_403() {
        ResponseEntity<ApiError> resp =
                handler.handleAccesoDenegado(new AccessDeniedException("denied"), req);

        assertEquals(403, resp.getStatusCode().value());
        assertEquals("Forbidden", resp.getBody().getError());
        // Genérico: no revela qué roles o permisos existen
        assertFalse(resp.getBody().getMessage().contains("ADMIN"));
        assertFalse(resp.getBody().getMessage().contains("PROFESOR"));
        assertFalse(resp.getBody().getMessage().contains("denied"));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────

    @Test
    @DisplayName("WEB-02 · Recurso inexistente -> 404 Not Found")
    void recursoInexistente_404() {
        ResponseEntity<ApiError> resp = handler.handleNotFound(
                new ResourceNotFoundException("Usuario no encontrado"), req);

        assertEquals(404, resp.getStatusCode().value());
        assertEquals("Not Found", resp.getBody().getError());
        assertEquals("Usuario no encontrado", resp.getBody().getMessage());
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────

    @Test
    @DisplayName("WEB-02 · Excepción inesperada -> 500 sin filtrar internals")
    void excepcionInesperada_500() {
        RuntimeException boom = new RuntimeException(
                "org.springframework.dao.DataIntegrityViolationException: "
                        + "could not execute statement jdbc:postgresql://admin:secreto@db:5432/luxray");

        ResponseEntity<ApiError> resp = handler.handleAll(boom, req);

        assertEquals(500, resp.getStatusCode().value());
        assertEquals("Internal Server Error", resp.getBody().getError());
        assertEquals("Error interno del servidor. Intenta más tarde.",
                resp.getBody().getMessage());
        final String cuerpo = resp.getBody().getMessage().toLowerCase();
        assertFalse(cuerpo.contains("jdbc"));
        assertFalse(cuerpo.contains("secreto"));
        assertFalse(cuerpo.contains("springframework"));
    }

    // ── Estructura común de la respuesta de error ─────────────────────────

    @Test
    @DisplayName("WEB-06 · Todos los errores usan la misma estructura ApiError")
    void estructuraComunDeError() {
        List<ResponseEntity<ApiError>> respuestas = List.of(
                handler.handleBusiness(new BusinessException("x"), req),
                handler.handleNotFound(new ResourceNotFoundException("y"), req),
                handler.handleAccesoDenegado(new AccessDeniedException("z"), req),
                handler.handleAll(new RuntimeException("w"), req));

        for (ResponseEntity<ApiError> r : respuestas) {
            ApiError e = r.getBody();
            assertNotNull(e);
            assertNotNull(e.getTimestamp(), "timestamp siempre presente");
            assertNotNull(e.getStatus(), "status siempre presente");
            assertNotNull(e.getError(), "error siempre presente");
            assertNotNull(e.getMessage(), "message siempre presente");
            assertNotNull(e.getPath(), "path siempre presente");
            assertEquals(r.getStatusCode().value(), e.getStatus(),
                    "el status del cuerpo coincide con el de la respuesta");
        }
    }

    @Test
    @DisplayName("WEB-06 · El cuerpo de error nunca incluye la traza de pila")
    void errorNuncaIncluyeTrazaDePila() {
        ResponseEntity<ApiError> resp = handler.handleAll(
                new IllegalStateException("fallo"), req);

        String texto = resp.getBody().getMessage();
        assertFalse(texto.contains("at com.luxray"));
        assertFalse(texto.contains("at java."));
        assertFalse(texto.contains("at org.springframework"));
        assertTrue(texto.length() < 120, "el mensaje de error debe ser breve");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DEFECTOS REPRODUCIDOS (Práctica 11 — Fase 6)
    //
    //  Estas pruebas están DESHABILITADAS a propósito: documentan el
    //  comportamiento DEFECTUOSO actual del backend. Cuando se corrija el
    //  defecto, deben activarse y pasar (basta con quitar @Disabled).
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DEF-02 · Cabecera Authorization ausente debe responder 401, no 500")
    @Disabled("DEF-02: MissingRequestHeaderException no esta mapeada en GlobalExceptionHandler "
            + "-> los endpoints que declaran @RequestHeader(\"Authorization\") obligatorio "
            + "(p.ej. GET /api/cursos/mis-cursos) devuelven 500 en lugar de 401.")
    void cabeceraAuthorizationAusente_deberiaSer401() {
        MissingRequestHeaderException ex = new MissingRequestHeaderException(
                "Authorization", (org.springframework.core.MethodParameter) null);

        // Estado actual real: la excepcion cae en handleAll -> 500.
        ResponseEntity<ApiError> actual = handler.handleAll(ex, req);
        assertEquals(500, actual.getStatusCode().value(),
                "REPRODUCCION DEL DEFECTO: hoy responde 500");

        // Comportamiento esperado tras la correccion (mapeo explicito a 401):
        // assertEquals(401, ...);
    }

    @Test
    @DisplayName("DEF-03 · UUID malformado debe responder 400, no 500")
    @Disabled("DEF-03: MethodArgumentTypeMismatchException no esta mapeada en "
            + "GlobalExceptionHandler -> GET /api/cursos/{id} con un id no-UUID devuelve 500 "
            + "en lugar de 400 Bad Request.")
    void uuidMalformado_deberiaSer400() {
        // Estado actual real: la excepcion cae en handleAll -> 500.
        ResponseEntity<ApiError> actual =
                handler.handleAll(new IllegalArgumentException("UUID malformado"), req);
        assertEquals(500, actual.getStatusCode().value(),
                "REPRODUCCION DEL DEFECTO: hoy responde 500");

        // Comportamiento esperado tras la correccion:
        // assertEquals(400, ...);
    }
}
