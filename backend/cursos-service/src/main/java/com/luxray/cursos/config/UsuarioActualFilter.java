package com.luxray.cursos.config;

import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.service.UsuarioActualService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Revalida en cada petición HTTP que lleva JWT que la cuenta siga activa y
 * conserve el rol emitido en el token. Así una baja o un cambio de rol surte
 * efecto antes de que caduque el token, incluso en endpoints administrativos
 * protegidos únicamente por Spring Security.
 */
@Component
public class UsuarioActualFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwt;
    private final UsuarioActualService usuariosActuales;

    public UsuarioActualFilter(JwtTokenProvider jwt,
                               UsuarioActualService usuariosActuales) {
        this.jwt = jwt;
        this.usuariosActuales = usuariosActuales;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final UUID usuarioId;
        final String rol;
        try {
            var claims = jwt.parse(authorization.substring(7));
            String uid = claims.get("uid", String.class);
            if (uid == null || uid.isBlank()) throw new IllegalArgumentException("uid ausente");
            usuarioId = UUID.fromString(uid);
            String claimRol = claims.get("role", String.class);
            rol = claimRol == null || claimRol.isBlank() ? "USER" : claimRol.toUpperCase();
        } catch (RuntimeException ex) {
            // JwtAuthenticationFilter ya dejó la petición sin autenticar. En
            // rutas públicas puede continuar; en rutas protegidas Spring
            // Security responderá con 401/403.
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        try {
            UsuarioActualService.UsuarioActual actual =
                    usuariosActuales.consultar(authorization);
            if (!actual.activo()
                    || !usuarioId.equals(actual.id())
                    || !rol.equalsIgnoreCase(actual.rol())) {
                reject(response, "La cuenta ya no está activa o su rol cambió");
                return;
            }
        } catch (RuntimeException ex) {
            reject(response, "No se pudo validar el usuario actual");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"ACCOUNT_REVALIDATION_FAILED\",\"message\":\""
                        + message + "\"}");
    }
}
