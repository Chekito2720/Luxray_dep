CREATE TABLE IF NOT EXISTS inscripciones (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id               UUID         NOT NULL,
    curso_id                 UUID         NOT NULL REFERENCES cursos(id) ON DELETE CASCADE,
    lecciones_completadas    INTEGER      NOT NULL DEFAULT 0,
    proxima_leccion          VARCHAR(200),
    porcentaje               INTEGER      NOT NULL DEFAULT 0,
    inscrito_en              TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, curso_id)
);

CREATE INDEX IF NOT EXISTS idx_insc_usuario ON inscripciones (usuario_id);
CREATE INDEX IF NOT EXISTS idx_insc_curso   ON inscripciones (curso_id);

CREATE TABLE IF NOT EXISTS progreso_lecciones (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID         NOT NULL,
    leccion_id      UUID         NOT NULL REFERENCES lecciones(id) ON DELETE CASCADE,
    completada      BOOLEAN      NOT NULL DEFAULT FALSE,
    puntuacion      INTEGER,
    completado_en   TIMESTAMP,
    UNIQUE (usuario_id, leccion_id)
);

CREATE INDEX IF NOT EXISTS idx_prog_usuario    ON progreso_lecciones (usuario_id);
CREATE INDEX IF NOT EXISTS idx_prog_leccion    ON progreso_lecciones (leccion_id);
CREATE INDEX IF NOT EXISTS idx_prog_completada ON progreso_lecciones (usuario_id, completada);
