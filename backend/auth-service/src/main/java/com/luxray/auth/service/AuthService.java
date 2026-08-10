package com.luxray.auth.service;

import com.luxray.auth.dto.AuthResponse;
import com.luxray.auth.dto.LoginRequest;
import com.luxray.auth.dto.RegisterRequest;
import com.luxray.auth.dto.UsuarioRequest;
import com.luxray.auth.dto.UsuarioResponse;
import com.luxray.auth.model.Usuario;
import com.luxray.auth.repository.UsuarioRepository;
import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtTokenProvider jwt) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!Boolean.TRUE.equals(req.acceptTerms())) {
            throw new BusinessException("Debes aceptar los términos y condiciones.");
        }
        if (usuarios.existsByEmail(req.email().toLowerCase())) {
            throw new BusinessException("El correo ya está registrado. Intenta iniciar sesión.");
        }
        Usuario u = new Usuario();
        u.setNombre(req.nombre());
        u.setApellido(req.apellido());
        u.setEmail(req.email().toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        usuarios.save(u);
        return generateTokens(u);
    }

    public AuthResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmailAndActivoTrue(req.email().toLowerCase())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BusinessException("Credenciales inválidas");
        }
        return generateTokens(u);
    }

    public ApiResponse<?> me(String token) {
        String subject = jwt.getSubject(token);
        Usuario u = usuarios.findByEmailAndActivoTrue(subject)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return ApiResponse.ok(com.luxray.auth.dto.UsuarioResponse.from(u));
    }

    /* ───── CRUD Admin ───── */
    @Transactional
    public ApiResponse<UsuarioResponse> crearUsuarioAdmin(UsuarioRequest req) {
        if (usuarios.existsByEmail(req.email().toLowerCase())) {
            throw new BusinessException("El correo ya está registrado.");
        }
        Usuario u = req.toEntity();
        u.setPasswordHash(passwordEncoder.encode(u.getPasswordHash()));
        u.setActivo(Boolean.TRUE);
        usuarios.save(u);
        return ApiResponse.ok(UsuarioResponse.from(u));
    }

    public ApiResponse<List<UsuarioResponse>> listarUsuariosAdmin() {
        List<Usuario> lista = usuarios.findAllByActivoTrue();
        List<UsuarioResponse> data = lista.stream().map(UsuarioResponse::from).toList();
        return ApiResponse.ok(data);
    }

    public ApiResponse<List<UsuarioResponse>> listarUsuariosInactivosAdmin() {
        List<Usuario> lista = usuarios.findAllByActivoFalse();
        List<UsuarioResponse> data = lista.stream().map(UsuarioResponse::from).toList();
        return ApiResponse.ok(data);
    }

    @Transactional
    public ApiResponse<UsuarioResponse> actualizarUsuarioAdmin(UUID id, UsuarioRequest req) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        req.applyTo(u);
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        usuarios.save(u);
        return ApiResponse.ok(UsuarioResponse.from(u));
    }

    /** Baja lógica: marca al usuario como inactivo en lugar de eliminarlo. */
    @Transactional
    public ApiResponse<Void> eliminarUsuarioAdmin(UUID id) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        u.setActivo(Boolean.FALSE);
        usuarios.save(u);
        return ApiResponse.ok();
    }

    /** Reactiva un usuario previamente dado de baja. */
    @Transactional
    public ApiResponse<UsuarioResponse> reactivarUsuarioAdmin(UUID id) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        u.setActivo(Boolean.TRUE);
        usuarios.save(u);
        return ApiResponse.ok(UsuarioResponse.from(u));
    }

    @Transactional
    public ApiResponse<UsuarioResponse> cambiarRolAdmin(UUID id, String nuevoRol) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        u.setRol(nuevoRol.toUpperCase());
        usuarios.save(u);
        return ApiResponse.ok(UsuarioResponse.from(u));
    }

    private AuthResponse generateTokens(Usuario u) {
        String access = jwt.generateToken(u.getEmail(),
                Map.of("uid", u.getId().toString(), "role", u.getRol()));
        // Refresh token más largo (7 días)
        String refresh = jwt.generateToken(u.getEmail(),
                Map.of("type", "refresh")) ;
        return AuthResponse.of(access, refresh, jwt.getExpirationMs(),
                u.getId(), u.getNombre(), u.getApellido(),
                u.getEmail(), u.getAvatar(), u.getRol(), u.getCreadoEn());
    }
}
