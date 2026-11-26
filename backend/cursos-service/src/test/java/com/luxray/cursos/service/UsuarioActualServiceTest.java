package com.luxray.cursos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UsuarioActualServiceTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000501");

    private MockRestServiceServer authServer;
    private UsuarioActualService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        authServer = MockRestServiceServer.bindTo(builder).build();
        service = new UsuarioActualService(builder, "http://auth.test");
    }

    @Test
    void consultaElEstadoVigenteDelUsuario() {
        authServer.expect(requestTo("http://auth.test/auth/me"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"data\":{\"id\":\"" + USUARIO
                                + "\",\"rol\":\"PROFESOR\",\"activo\":true}}",
                        MediaType.APPLICATION_JSON));

        var actual = service.consultar("Bearer token");

        assertEquals(USUARIO, actual.id());
        assertEquals("PROFESOR", actual.rol());
        assertEquals(true, actual.activo());
        authServer.verify();
    }

    @Test
    void unaCuentaInactivaOinexistenteProduceAccesoDenegado() {
        authServer.expect(requestTo("http://auth.test/auth/me"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(AccessDeniedException.class,
                () -> service.consultar("Bearer token"));
    }
}
