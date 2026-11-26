package com.luxray.common.exception;

import com.luxray.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Mensaje genérico para errores internos: nunca se expone al cliente
     * la causa raíz (stacktrace, SQL, rutas de servidor, credenciales).
     * El detalle real solo queda en el log del servidor.
     */
    private static final String MSG_ERROR_INTERNO = "Error interno del servidor. Intenta más tarde.";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNoLegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // JSON malformado: se registra la causa en el servidor, al cliente solo un 400 genérico
        log.warn("Solicitud malformada en {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "La solicitud no es válida.", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> map = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> map.put(fe.getField(), fe.getDefaultMessage()));
        ApiError err = new ApiError(400, "Validation Error", "Uno o más campos son inválidos", req.getRequestURI());
        err.setFieldErrors(map);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccesoDenegado(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest req) {
        // 403 genérico: no se revela qué roles/permisos existen
        log.warn("Acceso denegado en {}", req.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Forbidden", "No tienes permisos para esta acción.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest req) {
        // Detalle completo SOLO en el log del servidor (con stacktrace para depuración)
        log.error("Error no controlado en {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        // Al cliente: mensaje genérico, sin exponer internals (SQL, rutas, credenciales)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", MSG_ERROR_INTERNO, req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), error, message, req.getRequestURI()));
    }
}
