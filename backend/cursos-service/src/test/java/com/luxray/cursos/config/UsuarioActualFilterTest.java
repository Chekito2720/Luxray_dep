package com.luxray.cursos.config;

import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.service.UsuarioActualService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioActualFilterTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000501");

    private JwtTokenProvider jwt;
    private UsuarioActualService usuariosActuales;
    private UsuarioActualFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        jwt = mock(JwtTokenProvider.class);
        usuariosActuales = mock(UsuarioActualService.class);
        filter = new UsuarioActualFilter(jwt, usuariosActuales);
        request = new MockHttpServletRequest("GET", "/cursos/mis-cursos");
        request.addHeader("Authorization", "Bearer token");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);

        Claims claims = mock(Claims.class);
        when(jwt.parse("token")).thenReturn(claims);
        when(claims.get("uid", String.class)).thenReturn(USUARIO.toString());
        when(claims.get("role", String.class)).thenReturn("USER");
    }

    @Test
    void permiteContinuarCuandoLaCuentaActivaConservaElRol() throws Exception {
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(USUARIO, "USER", true));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void bloqueaUnaCuentaDesactivada() throws Exception {
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(USUARIO, "USER", false));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_REVALIDATION_FAILED"));
    }

    @Test
    void bloqueaUnTokenCuyoRolYaNoCoincide() throws Exception {
        when(usuariosActuales.consultar("Bearer token"))
                .thenReturn(new UsuarioActualService.UsuarioActual(USUARIO, "PROFESOR", true));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(403, response.getStatus());
    }
}
