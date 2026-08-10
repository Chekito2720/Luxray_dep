CREATE TABLE IF NOT EXISTS lecciones (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    curso_id        UUID         NOT NULL REFERENCES cursos(id) ON DELETE CASCADE,
    seccion_id      VARCHAR(120) NOT NULL,
    seccion_titulo  VARCHAR(200) NOT NULL,
    titulo          VARCHAR(200) NOT NULL,
    duracion        VARCHAR(20)  NOT NULL,
    tipo            VARCHAR(16)  NOT NULL CHECK (tipo IN ('VIDEO','QUIZ','LECTURA')),
    video_url       VARCHAR(500),
    descripcion     VARCHAR(1000),
    orden           INTEGER      NOT NULL DEFAULT 0,
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lecciones_curso_orden ON lecciones (curso_id, orden);
CREATE INDEX IF NOT EXISTS idx_lecciones_seccion    ON lecciones (curso_id, seccion_id);
