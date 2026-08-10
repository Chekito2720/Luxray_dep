package com.luxray.auth.controller;

import com.luxray.auth.dto.AuthResponse;
import com.luxray.auth.dto.LoginRequest;
import com.luxray.auth.dto.RegisterRequest;
import com.luxray.auth.dto.UsuarioRequest;
import com.luxray.auth.dto.UsuarioResponse;
import com.luxray.auth.service.AuthService;
import com.luxray.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Endpoints de autenticación, registro y administración de usuarios")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.login(req)));
    }

    @PostMapping("/registro")
    @Operation(summary = "Crear nueva cuenta")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.register(req)));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener perfil del token actual")
    public ResponseEntity<ApiResponse<?>> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ResponseEntity.ok(service.me(token));
    }

    /* ───── ADMIN ───── */
    @GetMapping("/admin/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios activos — solo ADMIN")
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listarUsuariosAdmin() {
        return ResponseEntity.ok(service.listarUsuariosAdmin());
    }

    @GetMapping("/admin/usuarios/inactivos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios dados de baja lógica — solo ADMIN")
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listarUsuariosInactivosAdmin() {
        return ResponseEntity.ok(service.listarUsuariosInactivosAdmin());
    }

    @PostMapping("/admin/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear usuario — solo ADMIN")
    public ResponseEntity<ApiResponse<UsuarioResponse>> crearUsuarioAdmin(@Valid @RequestBody UsuarioRequest req) {
        return ResponseEntity.ok(service.crearUsuarioAdmin(req));
    }

    @PutMapping("/admin/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar usuario — solo ADMIN")
    public ResponseEntity<ApiResponse<UsuarioResponse>> actualizarUsuarioAdmin(@PathVariable UUID id, @Valid @RequestBody UsuarioRequest req) {
        return ResponseEntity.ok(service.actualizarUsuarioAdmin(id, req));
    }

    @DeleteMapping("/admin/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Baja lógica de usuario — solo ADMIN")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuarioAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(service.eliminarUsuarioAdmin(id));
    }

    @PatchMapping("/admin/usuarios/{id}/reactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivar usuario dado de baja — solo ADMIN")
    public ResponseEntity<ApiResponse<UsuarioResponse>> reactivarUsuarioAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reactivarUsuarioAdmin(id));
    }

    @PatchMapping("/admin/usuarios/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar rol de usuario — solo ADMIN")
    public ResponseEntity<ApiResponse<UsuarioResponse>> cambiarRolAdmin(@PathVariable UUID id, @RequestParam String rol) {
        return ResponseEntity.ok(service.cambiarRolAdmin(id, rol));
    }
}
