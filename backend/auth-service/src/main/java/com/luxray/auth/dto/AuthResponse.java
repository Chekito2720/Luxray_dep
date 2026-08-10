package com.luxray.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AuthResponse(String token, String refreshToken, long expiresInMs, UsuarioResponse usuario) {
    public static AuthResponse of(String token, String refreshToken, long expiresInMs, UUID id, String nombre, String apellido, String email, String avatar, String rol, java.time.LocalDateTime creadoEn) {
        return new AuthResponse(
                token,
                refreshToken,
                expiresInMs,
                new UsuarioResponse(id, nombre, apellido, email, avatar, rol, Boolean.TRUE, creadoEn)
        );
    }
}
