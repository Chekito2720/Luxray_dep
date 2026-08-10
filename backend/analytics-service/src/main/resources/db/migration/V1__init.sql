CREATE TABLE IF NOT EXISTS analytics_snapshots (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id               UUID         NOT NULL,
    cursos_inscritos         INTEGER      NOT NULL DEFAULT 0,
    lecciones_completadas    INTEGER      NOT NULL DEFAULT 0,
    horas_estudio            DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    promedio_quizzes         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    fecha                    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_snap_usuario_fecha ON analytics_snapshots (usuario_id, fecha);
