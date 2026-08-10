-- V4: agregar columna 'activo' para soportar baja lógica de usuarios.
-- Todos los usuarios existentes quedan activos por defecto.

ALTER TABLE usuarios
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_usuarios_activo ON usuarios(activo);
