-- Sesiones de upload multipart: el servidor conserva los metadatos de la
-- subida iniciada y exige que coincidan al registrar la entrega.
CREATE TABLE IF NOT EXISTS subidas_multipart (
    upload_id       VARCHAR(512) PRIMARY KEY,
    usuario_id      UUID         NOT NULL,
    leccion_id      UUID         NOT NULL REFERENCES lecciones(id) ON DELETE CASCADE,
    clave           VARCHAR(500) NOT NULL,
    nombre_archivo  VARCHAR(200) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL CHECK (tamano_bytes > 0),
    tipo_archivo    VARCHAR(20)  NOT NULL CHECK (tipo_archivo IN ('FOTO', 'PDF', 'VIDEO')),
    partes_esperadas INTEGER     NOT NULL CHECK (partes_esperadas > 0),
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW(),
    expira_en       TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_subidas_usuario_leccion
    ON subidas_multipart (usuario_id, leccion_id);
CREATE INDEX IF NOT EXISTS idx_subidas_expira
    ON subidas_multipart (expira_en);

-- La columna hija de la FK necesita índice propio para consultar los archivos
-- de cada entrega sin recorrer toda la tabla.
CREATE INDEX IF NOT EXISTS idx_evidencias_entrega
    ON evidencias_practica (entrega_id);

-- Nombre y apellido pueden sumar hasta 161 caracteres.
ALTER TABLE mensajes_foro
    ALTER COLUMN autor_nombre TYPE VARCHAR(200);
