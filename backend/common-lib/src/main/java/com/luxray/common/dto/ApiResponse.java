package com.luxray.common.dto;

import java.util.List;

/**
 * Wrapper genérico para respuestas tipo "ApiResponse<T>" que el frontend ya espera.
 * Se usa {@code data} para el payload y opcionalmente metadata.
 */
public class ApiResponse<T> {
    private T data;
    private String message;
    private boolean success = true;
    private List<String> errors;

    public ApiResponse() {}

    public ApiResponse(T data) { this.data = data; }

    public ApiResponse(T data, String message) {
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(data); }
    public static <T> ApiResponse<T> ok(T data, String message) { return new ApiResponse<>(data, message); }
    public static ApiResponse<Void> ok() {
        ApiResponse<Void> r = new ApiResponse<>();
        r.success = true;
        return r;
    }
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
