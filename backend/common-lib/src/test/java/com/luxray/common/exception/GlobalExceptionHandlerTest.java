package com.luxray.common.exception;

import com.luxray.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Comprobación de las medidas de seguridad en el manejo de errores:
 * ningún detalle interno (SQL, credenciales, stacktrace) sale al cliente.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest req = new MockHttpServletRequest();

    @Test
    void errorInternoNoExponeDetallesAlCliente() {
        // Excepción con información MUY sensible en su mensaje
        RuntimeException boom = new RuntimeException(
                "Connection refused: jdbc:postgresql://admin:s3creto@prod-db:5432/luxray");

        ResponseEntity<ApiError> resp = handler.handleAll(boom, req);

        assertEquals(500, resp.getStatusCode().value());
        assertEquals("Error interno del servidor. Intenta más tarde.",
                resp.getBody().getMessage());
        // El mensaje original JAMÁS llega al cliente
        assertNotEquals(boom.getMessage(), resp.getBody().getMessage());
        assertFalse(resp.getBody().getMessage().contains("s3creto"),
                "el mensaje al cliente no debe contener credenciales internas");
    }

    @Test
    void jsonMalformadoDevuelve400Generico() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error: Unexpected char 'd'",
                        new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> resp = handler.handleNoLegible(ex, req);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("La solicitud no es válida.", resp.getBody().getMessage());
        assertFalse(resp.getBody().getMessage().contains("JSON parse error"),
                "el detalle del parser no debe exponerse al cliente");
    }

    @Test
    void errorNegocioSiExplicaElMotivoAlUsuario() {
        // Los errores de negocio (mensajes pensados para el usuario) sí se propagan
        ResponseEntity<ApiError> resp =
                handler.handleBusiness(new BusinessException("El correo ya está registrado."), req);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("El correo ya está registrado.", resp.getBody().getMessage());
    }
}
