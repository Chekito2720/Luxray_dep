package com.luxray.common.security;

import org.springframework.stereotype.Component;

/**
 * Marcador de paquetes para que los servicios reconozcan este módulo como
 * propio. Se usa combinado con `scanBasePackages` en el `@SpringBootApplication`.
 *
 * NO define ningún SecurityFilterChain aquí — eso lo hace cada microservicio,
 * de modo que cada uno decide sus rutas públicas sin colisión de beans.
 */
@Component
public class BaseSecurityConfig {}



