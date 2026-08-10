package com.luxray.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 80) String nombre,
        @NotBlank @Size(min = 2, max = 80) String apellido,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        Boolean acceptTerms
) { }
