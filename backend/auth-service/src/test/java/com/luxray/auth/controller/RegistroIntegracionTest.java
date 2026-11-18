package com.luxray.auth.controller;

import com.luxray.auth.model.Usuario;
import com.luxray.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRÁCTICA 13/14 — FASE 3 · PRUEBAS DE INTEGRACIÓN DEL BACKEND.
 *
 * Validan la COMUNICACIÓN ENTRE COMPONENTES del lado servidor:
 *
 *   I-05 · La operación de registro ALMACENA correctamente la información
 *          (API → servicio → repositorio → base de datos).
 *   I-06 · La API devuelve el código HTTP esperado para cada situación.
 *   I-07 · Autenticación y autorización: el código refleja el motivo real.
 *
 * Sobre la base de datos: el proyecto usa PostgreSQL 16 en producción y
 * **H2 en memoria** para las pruebas (perfil `test`,
 * `src/test/resources/application-test.properties`). Es la adaptación ya
 * documentada en las Prácticas 11 y 12; la verificación contra PostgreSQL
 * real se hizo con scripts SQL en la Fase 4 de la Práctica 12.
 *
 * Alcance: se prueba directo contra `auth-service`; el api-gateway queda
 * fuera (ya se verificó por separado que enruta `/api/auth/**`).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroIntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository repo;

    @BeforeEach
    void limpiar() {
        repo.deleteAll();
    }

    private static final String CUERPO =
            "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"email\":\"%s\","
                    + "\"password\":\"Password123!\",\"acceptTerms\":true}";

    // ── I-05 · La operación de registro almacena correctamente la información ─
    @Test
    @DisplayName("I-05 · el registro persiste y se puede leer de la base de datos")
    void registro_almacena_correctamente() throws Exception {
        mvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted("persiste@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString());

        Usuario u = repo.findByEmail("persiste@test.com").orElseThrow();

        // Lo que se pidió es lo que se guardó.
        assertEquals("Ana", u.getNombre());
        assertEquals("Pérez", u.getApellido());
        assertEquals("persiste@test.com", u.getEmail(), "el email se guarda en minúsculas");
        assertEquals("USER", u.getRol(), "rol por defecto");
        assertNotNull(u.getCreadoEn(), "fecha de creación");
        assertTrue(Boolean.TRUE.equals(u.getActivo()), "activo desde el alta");

        // La contraseña NO se guarda en claro.
        assertTrue(u.getPasswordHash().startsWith("$2a$"),
                "la contraseña debe guardarse hasheada con BCrypt");
    }

    @Test
    @DisplayName("I-05b · un segundo registro con el mismo email NO crea una segunda fila")
    void registro_duplicado_no_duplica_filas() throws Exception {
        String body = CUERPO.formatted("dup@test.com");

        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        // Comprobación de PERSISTENCIA, no sólo de código HTTP: la base de datos
        // es la que garantiza que no hay duplicados.
        assertEquals(1, repo.findByEmail("dup@test.com").stream().count(),
                "debe existir exactamente una fila para ese email");
        assertEquals(1, repo.findAll().size(), "no se ha colado ninguna fila extra");
    }

    // ── I-06 · La API devuelve el código HTTP esperado ─────────────────────
    @Test
    @DisplayName("I-06 · cada situación de entrada devuelve su código HTTP")
    void codigos_http_esperados() throws Exception {
        // Datos válidos -> 200
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted("valido@test.com")))
                .andExpect(status().isOk());

        // Email con formato inválido -> 400 con detalle de campo
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted("esto-no-es-un-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        // Password de 3 caracteres (fuera del rango 8-64) -> 400
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"A\",\"apellido\":\"B\",\"email\":\"corta@test.com\","
                                + "\"password\":\"123\",\"acceptTerms\":true}"))
                .andExpect(status().isBadRequest());

        // Cuerpo inexistente (falta acceptTerms) -> 400, no 500
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"A\",\"apellido\":\"B\",\"email\":\"x@test.com\","
                                + "\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest());

        // JSON malformado -> 400, no 500
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json "))
                .andExpect(status().isBadRequest());
    }

    // ── I-07 · Autenticación y autorización ───────────────────────────────
    @Test
    @DisplayName("I-07 · /auth/me con token válido devuelve 200 y el perfil")
    void me_con_token_valido() throws Exception {
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted("token@test.com")))
                .andExpect(status().isOk());

        // Se reutiliza el token emitido por el propio registro.
        String token = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"token@test.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();

        String jwt = com.jayway.jsonpath.JsonPath.read(token, "$.data.token");

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("token@test.com"))
                // El perfil NUNCA debe exponer el hash de la contraseña.
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    // ── Defectos documentados (la caja roja los cierra) ───────────────────
    //
    //  Estas pruebas están DESHABILITADAS a propósito: documentan el
    //  comportamiento DEFECTUOSO actual. Cuando se corrija el defecto basta
    //  con quitar @Disabled para que pasen.

    @Test
    @Disabled("DEF-08: el email duplicado devuelve 400 en lugar de 409 Conflict. "
            + "Al corregirlo (ConflictException -> 409) esta prueba debe activarse.")
    @DisplayName("I-06b · email duplicado debe devolver 409 Conflict")
    void duplicado_deberia_devolver_409() throws Exception {
        String body = CUERPO.formatted("conflicto@test.com");
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @Disabled("DEF-02: la cabecera Authorization obligatoria provoca "
            + "MissingRequestHeaderException sin mapear -> 500 en lugar de 401.")
    @DisplayName("I-07b · sin cabecera de autorización debe devolver 401")
    void sin_cabecera_deberia_devolver_401() throws Exception {
        mvc.perform(get("/cursos/mis-cursos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Disabled("DEF-03: un UUID malformado provoca MethodArgumentTypeMismatchException "
            + "sin mapear -> 500 en lugar de 400.")
    @DisplayName("I-06c · identificador malformado debe devolver 400")
    void uuid_malformado_deberia_devolver_400() throws Exception {
        mvc.perform(get("/cursos/no-es-uuid"))
                .andExpect(status().isBadRequest());
    }
}
