package com.luxray.auth.service;

import com.luxray.auth.dto.AuthResponse;
import com.luxray.auth.dto.LoginRequest;
import com.luxray.auth.dto.RegisterRequest;
import com.luxray.auth.model.Usuario;
import com.luxray.auth.repository.UsuarioRepository;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprobación del manejo seguro de credenciales en el login:
 *   - Mensaje idéntico para usuario inexistente y contraseña incorrecta
 *     (sin enumeración de correos).
 *   - Se ejecuta BCrypt incluso cuando el usuario no existe (anti timing).
 *   - La contraseña nunca viaja ni se guarda en claro en la respuesta.
 */
class AuthServiceTest {

    private UsuarioRepository usuarios;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        encoder = spy(new BCryptPasswordEncoder());
        service = new AuthService(usuarios, encoder,
                new JwtTokenProvider("secreto-de-pruebas-para-jwt-de-al-menos-32-caracteres", 60));
    }

    @Test
    void usuarioInexistente_mismoMensajeQuePasswordIncorrecta() {
        when(usuarios.findByEmailAndActivoTrue("nadie@x.com")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("nadie@x.com", "password123", false)));

        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void passwordIncorrecta_mensajeIdentico_alDeUsuarioInexistente() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setEmail("alumno@x.com");
        u.setPasswordHash(encoder.encode("laCorrecta123"));
        when(usuarios.findByEmailAndActivoTrue("alumno@x.com")).thenReturn(Optional.of(u));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alumno@x.com", "incorrecta123", false)));

        // Indistinguible del caso "usuario no existe": no se revela qué falló
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void usuarioInexistente_ejecutaBcryptDeTodasFormas() {
        // Mitigación anti-timing: se compara contra el hash cebo aunque el
        // email no exista, para igualar el tiempo de respuesta.
        when(usuarios.findByEmailAndActivoTrue(anyString())).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("nadie@x.com", "password123", false)));

        verify(encoder).matches(eq("password123"), anyString());
    }

    @Test
    void loginExitoso_laPasswordNuncaApareceEnLaRespuesta() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNombre("Alumno");
        u.setApellido("Test");
        u.setEmail("alumno@x.com");
        u.setPasswordHash(encoder.encode("laCorrecta123"));
        when(usuarios.findByEmailAndActivoTrue("alumno@x.com")).thenReturn(Optional.of(u));

        AuthResponse resp = service.login(new LoginRequest("alumno@x.com", "laCorrecta123", false));

        assertFalse(resp.token().contains("laCorrecta123"));
        assertFalse(resp.refreshToken().contains("laCorrecta123"));
        assertEquals(0, u.getPasswordHash().indexOf("$2"), "en BD solo vive el hash BCrypt");
    }

    /* ───── Registro ───── */

    @Test
    void registro_valido_guarda_usuario_con_password_hasheada() {
        RegisterRequest req = new RegisterRequest("Ana", "Pérez", "ana@test.com", "Password123!", true);
        when(usuarios.existsByEmail("ana@test.com")).thenReturn(false);
        final Usuario[] guardado = new Usuario[1];
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            guardado[0] = u;
            return u;
        });

        AuthResponse resp = service.register(req);

        assertEquals("ana@test.com", resp.usuario().email());
        assertNotNull(guardado[0], "se debe persistir el usuario");
        assertTrue(guardado[0].getPasswordHash().startsWith("$2a$"), "la contraseña debe guardarse hasheada con BCrypt");
        assertNotEquals("Password123!", guardado[0].getPasswordHash(), "la contraseña nunca viaja ni se guarda en claro");
    }

    @Test
    void password_de_8_caracteres_limite_es_aceptada() {
        RegisterRequest req = new RegisterRequest("Ana", "Pérez", "limite@test.com", "Abcd123!", true);
        when(usuarios.existsByEmail("limite@test.com")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        assertDoesNotThrow(() -> service.register(req));
    }

    @Test
    void email_duplicado_lanza_excepcion_y_no_guarda() {
        when(usuarios.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Ana", "Pérez", "dup@test.com", "Password123!", true)));

        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void terminos_no_aceptados_lanza_excepcion_y_no_guarda() {
        when(usuarios.existsByEmail("terminos@test.com")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Ana", "Pérez", "terminos@test.com", "Password123!", false)));

        verify(usuarios, never()).save(any(Usuario.class));
    }
}
