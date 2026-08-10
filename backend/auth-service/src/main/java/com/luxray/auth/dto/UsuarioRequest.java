package com.luxray.auth.dto;

import com.luxray.auth.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank @Size(min = 2, max = 80) String nombre,
        @NotBlank @Size(min = 2, max = 80) String apellido,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        String rol
) {
    public Usuario toEntity() {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(password);
        u.setRol(rol != null && !rol.isBlank() ? rol.toUpperCase() : "USER");
        return u;
    }

    public void applyTo(Usuario u) {
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email.toLowerCase());
        if (password != null && !password.isBlank()) u.setPasswordHash(password);
        if (rol != null && !rol.isBlank()) u.setRol(rol.toUpperCase());
    }
}