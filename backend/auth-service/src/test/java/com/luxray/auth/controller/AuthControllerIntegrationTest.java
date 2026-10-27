package com.luxray.auth.controller;

import com.luxray.auth.model.Usuario;
import com.luxray.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de auth-service contra H2 (BD en memoria, nunca auth_db).
 * Se prueba directo contra auth-service (puerto 8081); el api-gateway queda
 * fuera de este alcance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository repo;

    @BeforeEach
    void limpiar() {
        repo.deleteAll();
    }

    @Test
    void registro_persiste_en_bd_con_password_hasheada() throws Exception {
        mvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"email\":\"int@test.com\",\"password\":\"Password123!\",\"acceptTerms\":true}"))
                .andExpect(status().isOk());

        var u = repo.findByEmail("int@test.com").orElseThrow();
        assertNotEqualsPassword(u);
    }

    @Test
    void registro_duplicado_devuelve_400() throws Exception {
        String body = "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"email\":\"dup@test.com\",\"password\":\"Password123!\",\"acceptTerms\":true}";
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private static void assertNotEqualsPassword(Usuario u) {
        boolean hashed = u.getPasswordHash() != null
                && u.getPasswordHash().startsWith("$2a$")
                && !"Password123!".equals(u.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(hashed, "el password debe guardarse hasheado con BCrypt");
    }
}
