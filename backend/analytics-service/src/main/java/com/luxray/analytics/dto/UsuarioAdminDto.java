package com.luxray.analytics.dto;

import java.util.UUID;

public record UsuarioAdminDto(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String avatar,
        String rol,
        String creadoEn
) {}
