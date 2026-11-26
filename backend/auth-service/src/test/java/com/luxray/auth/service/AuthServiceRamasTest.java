package com.luxray.auth.service;

import com.luxray.auth.dto.AuthResponse;
import com.luxray.auth.dto.UsuarioRequest;
import com.luxray.auth.model.Usuario;
import com.luxray.auth.repository.UsuarioRepository;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PRÁCTICA 11 — FASE 3 — WHITE BOX DE `auth-service` (rama de servicio).
 *
 * Cubre las ramas de `AuthService` que la matriz de la practica exige:
 *   * Validación del backend (WEB-03): email duplicado, términos no aceptados,
 *     cuerpo con campos inválidos.
 *   * Autenticación (WEB-04): usuario inexistente, contraseña incorrecta,
 *     usuario desactivado (baja lógica).
 *   * Autorización y CRUD administrativo: alta, actualización con y sin
 *     cambio de contraseña, baja lógica, reactivación y cambio de rol.
 *   * Casos límite: `resumenPorIds` con lista nula, vacía y por encima del
 *     máximo de 500 identificadores.
 */
class AuthServiceRamasTest {

    private static final String SECRETO = "secreto-de-pruebas-para-jwt-de-al-menos-32-caracteres";

    private UsuarioRepository usuarios;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        encoder = spy(new BCryptPasswordEncoder(4)); // coste bajo: la prueba no mide rendimiento
        service = new AuthService(usuarios, encoder, new JwtTokenProvider(SECRETO, 60));
    }

    private Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNombre("Ana");
        u.setApellido("Pérez");
        u.setEmail(email);
        u.setRol("USER");
        u.setActivo(Boolean.TRUE);
        u.setPasswordHash(encoder.encode("ClaveOriginal123"));
        return u;
    }

    /* ── Registro ─────────────────────────────────────────────────────── */

    @Test
    @DisplayName("WEB-03 · Registro normaliza el email a minúsculas")
    void registro_normalizaEmailAMinusculas() {
        when(usuarios.existsByEmail("ana@test.com")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        service.register(new com.luxray.auth.dto.RegisterRequest(
                "Ana", "Pérez", "ANA@Test.COM", "Clave1234", true));

        ArgumentCaptor<Usuario> cap = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(cap.capture());
        assertEquals("ana@test.com", cap.getValue().getEmail(),
                "el email se guarda siempre en minúsculas para no duplicar cuentas");
    }

    @Test
    @DisplayName("WEB-03 · Registro duplicado no persiste nada")
    void registro_duplicado_noPersiste() {
        when(usuarios.existsByEmail("dup@test.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(new com.luxray.auth.dto.RegisterRequest(
                        "Ana", "Pérez", "dup@test.com", "Clave1234", true)));

        assertTrue(ex.getMessage().toLowerCase().contains("ya está registrado"));
        verify(usuarios, never()).save(any(Usuario.class));
    }

    /* ── Login ─────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("WEB-04 · Usuario desactivado (baja lógica) no puede iniciar sesión")
    void login_usuarioDesactivado_falla() {
        // findByEmailAndActivoTrue no lo devuelve -> el usuario esta dado de baja
        when(usuarios.findByEmailAndActivoTrue("baja@test.com")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> service.login(new com.luxray.auth.dto.LoginRequest(
                        "baja@test.com", "ClaveOriginal123", false)));
    }

    @Test
    @DisplayName("WEB-04 · Credenciales inválidas devuelven SIEMPRE el mismo mensaje")
    void login_mensajeUniforme() {
        when(usuarios.findByEmailAndActivoTrue("nadie@test.com")).thenReturn(Optional.empty());
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> service.login(new com.luxray.auth.dto.LoginRequest(
                        "nadie@test.com", "Clave1234", false)));

        Usuario u = usuario("alumno@test.com");
        when(usuarios.findByEmailAndActivoTrue("alumno@test.com")).thenReturn(Optional.of(u));
        BusinessException e2 = assertThrows(BusinessException.class,
                () -> service.login(new com.luxray.auth.dto.LoginRequest(
                        "alumno@test.com", "OtraClave9", false)));

        assertEquals(e1.getMessage(), e2.getMessage(),
                "el mensaje no permite enumerar qué correos existen");
    }

    @Test
    @DisplayName("WEB-01 · Login correcto devuelve token, refreshToken y expiración")
    void login_exitoso_devuelveTokens() {
        Usuario u = usuario("alumno@test.com");
        when(usuarios.findByEmailAndActivoTrue("alumno@test.com")).thenReturn(Optional.of(u));

        AuthResponse resp = service.login(
                new com.luxray.auth.dto.LoginRequest("alumno@test.com", "ClaveOriginal123", false));

        assertNotNull(resp.token());
        assertNotNull(resp.refreshToken());
        assertTrue(resp.expiresInMs() > 0);
        assertNotEquals(resp.token(), resp.refreshToken(),
                "el access token y el refresh token deben ser distintos");
        assertFalse(resp.token().contains("ClaveOriginal123"),
                "la contraseña en claro nunca viaja en el token");
    }

    /* ── Perfil ────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("WEB-02 · `me` con usuario inexistente -> ResourceNotFoundException (404)")
    void me_usuarioInexistente_404() {
        // `me` recibe el TOKEN: el subject (email) se extrae del JWT.
        String token = new JwtTokenProvider(SECRETO, 60)
                .generateToken("fantasma@test.com", Map.of("uid", UUID.randomUUID().toString(),
                        "role", "USER"));
        when(usuarios.findByEmailAndActivoTrue("fantasma@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.me(token));
    }

    /* ── Resumen por ids (casos límite) ────────────────────────────────── */

    @Test
    @DisplayName("WEB-03 · resumenPorIds con lista nula devuelve lista vacía")
    void resumenPorIds_nulo() {
        var resp = service.resumenPorIds(null);
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
        verify(usuarios, never()).findAllById(any());
    }

    @Test
    @DisplayName("WEB-03 · resumenPorIds con lista vacía devuelve lista vacía")
    void resumenPorIds_vacia() {
        var resp = service.resumenPorIds(List.of());
        assertTrue(resp.getData().isEmpty());
        verify(usuarios, never()).findAllById(any());
    }

    @Test
    @DisplayName("WEB-03 · resumenPorIds con más de 500 ids se rechaza")
    void resumenPorIds_superaElMaximo() {
        List<UUID> muchos = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> UUID.randomUUID()).toList();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resumenPorIds(muchos));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("WEB-03 · resumenPorIds filtra los usuarios desactivados")
    void resumenPorIds_filtraInactivos() {
        UUID idActivo = UUID.randomUUID();
        UUID idInactivo = UUID.randomUUID();

        Usuario activo = usuario("activo@test.com");
        activo.setId(idActivo);
        Usuario inactivo = usuario("inactivo@test.com");
        inactivo.setId(idInactivo);
        inactivo.setActivo(Boolean.FALSE);

        when(usuarios.findAllById(any())).thenReturn(List.of(activo, inactivo));

        var resp = service.resumenPorIds(List.of(idActivo, idInactivo));

        assertEquals(1, resp.getData().size());
        assertEquals("activo@test.com", resp.getData().get(0).email());
    }

    /* ── CRUD administrativo ───────────────────────────────────────────── */

    @Test
    @DisplayName("WEB-03 · Crear usuario admin con email duplicado se rechaza")
    void crearAdmin_duplicado() {
        when(usuarios.existsByEmail("dup@test.com")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.crearUsuarioAdmin(new UsuarioRequest(
                        "Ana", "Pérez", "dup@test.com", "Clave1234", "USER")));
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("WEB-03 · Crear usuario admin hashea la contraseña")
    void crearAdmin_hasheaPassword() {
        when(usuarios.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        service.crearUsuarioAdmin(new UsuarioRequest(
                "Nuevo", "Usuario", "nuevo@test.com", "Clave1234", "PROFESOR"));

        ArgumentCaptor<Usuario> cap = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(cap.capture());
        assertTrue(cap.getValue().getPasswordHash().startsWith("$2"),
                "la contraseña se guarda hasheada");
        assertEquals("PROFESOR", cap.getValue().getRol());
    }

    @Test
    @DisplayName("WEB-03 · Actualizar sin contraseña NO cambia el hash almacenado")
    void actualizar_sinPassword_noCambiaHash() {
        Usuario u = usuario("edit@test.com");
        String hashOriginal = u.getPasswordHash();
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.actualizarUsuarioAdmin(u.getId(), new UsuarioRequest(
                "Ana Editada", "Pérez", "edit@test.com", null, "USER"));

        assertEquals(hashOriginal, u.getPasswordHash(),
                "sin contraseña nueva el hash debe permanecer intacto");
    }

    @Test
    @DisplayName("WEB-03 · Actualizar con contraseña en blanco NO cambia el hash")
    void actualizar_passwordEnBlanco_noCambiaHash() {
        Usuario u = usuario("edit2@test.com");
        String hashOriginal = u.getPasswordHash();
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.actualizarUsuarioAdmin(u.getId(), new UsuarioRequest(
                "Ana", "Pérez", "edit2@test.com", "   ", "USER"));

        assertEquals(hashOriginal, u.getPasswordHash());
    }

    @Test
    @DisplayName("WEB-03 · Actualizar con contraseña nueva la rehashea")
    void actualizar_conPassword_rehashea() {
        Usuario u = usuario("edit3@test.com");
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.actualizarUsuarioAdmin(u.getId(), new UsuarioRequest(
                "Ana", "Pérez", "edit3@test.com", "ClaveNueva123", "USER"));

        assertTrue(encoder.matches("ClaveNueva123", u.getPasswordHash()),
                "la nueva contraseña debe quedar hasheada con BCrypt");
    }

    @Test
    @DisplayName("WEB-02 · Actualizar un id inexistente -> ResourceNotFoundException")
    void actualizar_inexistente_404() {
        UUID id = UUID.randomUUID();
        when(usuarios.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizarUsuarioAdmin(id, new UsuarioRequest(
                        "A", "B", "a@b.com", null, "USER")));
    }

    @Test
    @DisplayName("DB-05 · La baja de usuario es LÓGICA (activo=false), no física")
    void eliminar_bajaLogica() {
        Usuario u = usuario("baja@test.com");
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.eliminarUsuarioAdmin(u.getId());

        assertFalse(u.getActivo(), "el usuario se marca como inactivo");
        verify(usuarios).save(u);
        verify(usuarios, never()).delete(any(Usuario.class));
    }

    @Test
    @DisplayName("WEB-01 · Reactivar vuelve a activar el usuario")
    void reactivar_activaElUsuario() {
        Usuario u = usuario("reactivo@test.com");
        u.setActivo(Boolean.FALSE);
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.reactivarUsuarioAdmin(u.getId());

        assertTrue(u.getActivo());
    }

    @Test
    @DisplayName("WEB-04 · Cambiar rol normaliza a mayúsculas")
    void cambiarRol_normalizaAMayusculas() {
        Usuario u = usuario("rol@test.com");
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));

        service.cambiarRolAdmin(u.getId(), "admin");

        assertEquals("ADMIN", u.getRol(),
                "el rol se guarda en mayúsculas para que @PreAuthorize(\"hasRole('ADMIN')\") funcione");
    }

    @Test
    @DisplayName("WEB-01 · Listar usuarios devuelve solo los activos")
    void listar_devuelveSoloActivos() {
        // El usuario se construye ANTES del stubbing: dentro de when(...) solo
        // puede evaluarse la llamada al repositorio (regla de Mockito).
        Usuario u = usuario("a@test.com");
        when(usuarios.findAllByActivoTrue()).thenReturn(List.of(u));

        var resp = service.listarUsuariosAdmin();

        assertEquals(1, resp.getData().size());
        assertEquals("a@test.com", resp.getData().get(0).email());
    }

    @Test
    @DisplayName("WEB-01 · Listar inactivos devuelve la lista complementaria")
    void listarInactivos() {
        Usuario u = usuario("b@test.com");
        when(usuarios.findAllByActivoFalse()).thenReturn(List.of(u));

        var resp = service.listarUsuariosInactivosAdmin();

        assertEquals(1, resp.getData().size());
    }

    @Test
    @DisplayName("WEB-06 · La respuesta de usuario nunca incluye el hash")
    void respuestaNuncaExponeElHash() {
        Usuario u = usuario("seguro@test.com");
        when(usuarios.findAllByActivoTrue()).thenReturn(List.of(u));

        var resp = service.listarUsuariosAdmin();

        String json = resp.getData().get(0).toString();
        assertFalse(json.contains("$2a$"), "el hash BCrypt no debe viajar al cliente");
        assertFalse(json.toLowerCase().contains("password"));
    }

    @Test
    @DisplayName("WEB-06 · UsuarioResponse no expone datos nulos innecesarios")
    void usuarioInactivoExponeElCampoActivo() {
        Usuario u = usuario("inactivo@test.com");
        u.setActivo(Boolean.FALSE);
        when(usuarios.findAllByActivoFalse()).thenReturn(List.of(u));

        var resp = service.listarUsuariosInactivosAdmin();

        assertFalse(resp.getData().get(0).activo());
        assertNull(resp.getData().get(0).avatar(),
                "el avatar puede ser null y no debe romperse la serializacion");
    }
}