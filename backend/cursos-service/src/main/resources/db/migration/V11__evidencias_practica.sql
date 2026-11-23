-- Evidencias de práctica subidas desde la app móvil (foto/video)
-- mediante Multipart Upload con URLs prefirmadas (S3/MinIO)
CREATE TABLE IF NOT EXISTS evidencias_practica (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID         NOT NULL,
    leccion_id      UUID         NOT NULL REFERENCES lecciones(id) ON DELETE CASCADE,
    nombre_archivo  VARCHAR(260) NOT NULL,
    content_type    VARCHAR(120) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    clave           VARCHAR(500) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETADA',
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evid_usuario  ON evidencias_practica (usuario_id);
CREATE INDEX IF NOT EXISTS idx_evid_leccion  ON evidencias_practica (leccion_id);
CREATE INDEX IF NOT EXISTS idx_evid_usuario_leccion ON evidencias_practica (usuario_id, leccion_id);
