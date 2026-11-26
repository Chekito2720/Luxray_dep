package com.luxray.cursos.service;

import com.luxray.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * Consulta el estado vigente del usuario en auth-service. Un JWT válido no
 * implica que la cuenta siga activa o conserve el mismo rol, por lo que las
 * operaciones sensibles revalidan estos datos en lugar de confiar sólo en los
 * claims del token.
 */
@Service
public class UsuarioActualService {

    private final RestClient authClient;

    public UsuarioActualService(
            RestClient.Builder restClientBuilder,
            @Value("${luxray.auth.url:http://localhost:8081}") String authUrl) {
        this.authClient = restClientBuilder.baseUrl(authUrl).build();
    }

    public record UsuarioActual(UUID id, String rol, boolean activo) { }

    public UsuarioActual consultar(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token de autorización requerido");
        }
        try {
            ApiResponse<Map<String, Object>> response = authClient.get()
                    .uri("/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });

            Map<String, Object> data = response == null ? null : response.getData();
            if (response == null || !response.isSuccess() || data == null) {
                throw new AccessDeniedException("No se pudo validar el usuario actual");
            }
            UUID id = UUID.fromString(String.valueOf(data.get("id")));
            String rol = data.get("rol") == null ? "" : String.valueOf(data.get("rol"));
            boolean activo = Boolean.parseBoolean(String.valueOf(data.get("activo")));
            return new UsuarioActual(id, rol.toUpperCase(), activo);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new AccessDeniedException("No se pudo validar el usuario actual");
        }
    }
}
