package com.luxray.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRÁCTICA 11 — FASE 3 — WHITE BOX DEL MECANISMO DE AUTENTICACIÓN (JWT).
 *
 * El token JWT es la credencial que separa "autenticado" (401) de
 * "autorizado" (403) en todos los microservicios del proyecto, de modo que
 * su ciclo de vida es funcionalidad critica de seguridad.
 *
 * Se verifica:
 *   * El token generado contiene el sujeto y los claims esperados.
 *   * La expiracion se calcula a partir de los minutos configurados.
 *   * Un token alterado (firma invalida) se RECHAZA.
 *   * Un token expirado se RECHAZA.
 *   * Un token con firma de otro secreto se RECHAZA.
 *   * El token no contiene la contraseña ni datos del hash.
 */
class JwtTokenProviderTest {

    private static final String SECRETO = "secreto-de-pruebas-para-jwt-de-al-menos-32-caracteres";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-para-jwt-de-al-menos-32-chars";

    @Test
    @DisplayName("WEB-04 · El token generado contiene el sujeto y los claims")
    void tokenContieneSujetoYClaims() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 60);

        String token = jwt.generateToken("alumno@luxray.com",
                Map.of("uid", "11111111-1111-4111-8111-111111111111", "role", "USER"));

        Claims claims = jwt.parse(token);
        assertEquals("alumno@luxray.com", claims.getSubject());
        assertEquals("11111111-1111-4111-8111-111111111111", claims.get("uid"));
        assertEquals("USER", claims.get("role"));
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("WEB-01 · getSubject devuelve el email del usuario")
    void getSubjectDevuelveElEmail() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 60);

        String token = jwt.generateToken("profesor@luxray.com", Map.of("role", "PROFESOR"));

        assertEquals("profesor@luxray.com", jwt.getSubject(token));
    }

    @Test
    @DisplayName("WEB-01 · La expiración se deriva de los minutos configurados")
    void expiracionSeCalculaDesdeLosMinutos() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 30);

        assertEquals(30L * 60_000L, jwt.getExpirationMs(),
                "30 minutos deben equivaler a 1 800 000 ms");
    }

    @Test
    @DisplayName("WEB-04 · Un token con la firma alterada se rechaza")
    void tokenAlteradoSeRechaza() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 60);
        String token = jwt.generateToken("alumno@luxray.com", Map.of("role", "USER"));

        // Se altera el ultimo caracter de la firma (base64url)
        String alterado = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThrows(JwtException.class, () -> jwt.parse(alterado),
                "un token manipulado NUNCA debe validarse");
    }

    @Test
    @DisplayName("WEB-04 · Un token firmado con otro secreto se rechaza")
    void tokenDeOtroSecretoSeRechaza() {
        JwtTokenProvider emisor = new JwtTokenProvider(SECRETO, 60);
        JwtTokenProvider verificador = new JwtTokenProvider(OTRO_SECRETO, 60);

        String token = emisor.generateToken("alumno@luxray.com", Map.of("role", "USER"));

        assertThrows(JwtException.class, () -> verificador.parse(token),
                "un token firmado con otra clave no puede aceptarse");
    }

    @Test
    @DisplayName("WEB-04 · Un token expirado se rechaza")
    void tokenExpiradoSeRechaza() {
        // -1 minuto => ya caduca en el momento de emitirlo
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, -1);

        String token = jwt.generateToken("alumno@luxray.com", Map.of("role", "USER"));

        assertThrows(JwtException.class, () -> jwt.parse(token),
                "un token expirado debe rechazarse");
    }

    @Test
    @DisplayName("WEB-04 · Una cadena arbitraria no es un token válido")
    void cadenaArbitrariaSeRechaza() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 60);

        // Cadena con formato de JWT pero sin firma valida
        assertThrows(JwtException.class, () -> jwt.parse("no-soy-un-jwt"));

        // Cadena vacia: jjwt lanza IllegalArgumentException (no JwtException)
        // porque no puede ni construir el parser. El JwtAuthenticationFilter
        // la captura como JwtException|IllegalArgumentException, por lo que
        // el token invalido NUNCA llega a la aplicacion.
        assertThrows(IllegalArgumentException.class, () -> jwt.parse(""));
    }

    @Test
    @DisplayName("WEB-06 · El token no transporta la contraseña ni el hash")
    void tokenNoTransportaLaContrasena() {
        JwtTokenProvider jwt = new JwtTokenProvider(SECRETO, 60);

        String token = jwt.generateToken("alumno@luxray.com",
                Map.of("uid", "11111111-1111-4111-8111-111111111111", "role", "USER"));

        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        assertTrue(!payload.toLowerCase().contains("password"));
        assertTrue(!payload.contains("$2a$"));
        assertTrue(!payload.toLowerCase().contains("secret"));
    }
}
