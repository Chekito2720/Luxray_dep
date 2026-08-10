package com.luxray.auth.dto;

import com.luxray.auth.model.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String avatar,
        String rol,
        Boolean activo,
        LocalDateTime creadoEn
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombre(),
                u.getApellido(),
                u.getEmail(),
                u.getAvatar(),
                u.getRol(),
                u.getActivo(),
                u.getCreadoEn()
        );
    }
}
