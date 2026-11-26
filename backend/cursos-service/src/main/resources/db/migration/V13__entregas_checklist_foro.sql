-- Entregas versionadas de prácticas: una versión contiene los archivos de un
-- alumno (foto + PDF) y conserva el resultado de la revisión docente.
CREATE TABLE IF NOT EXISTS entregas_practica (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          UUID         NOT NULL,
    leccion_id          UUID         NOT NULL REFERENCES lecciones(id) ON DELETE CASCADE,
    curso_id            UUID         NOT NULL REFERENCES cursos(id) ON DELETE CASCADE,
    version             INTEGER      NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'INCOMPLETA',
    retroalimentacion   TEXT,
    revisada_por        UUID,
    revisada_en         TIMESTAMP,
    criterios_evaluacion JSONB,
    puntuacion          INTEGER,
    creado_en           TIMESTAMP    NOT NULL DEFAULT NOW(),
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_entrega_version UNIQUE (usuario_id, leccion_id, version)
);

CREATE INDEX IF NOT EXISTS idx_entregas_usuario_leccion
    ON entregas_practica (usuario_id, leccion_id, version DESC);
CREATE INDEX IF NOT EXISTS idx_entregas_curso_estado
    ON entregas_practica (curso_id, estado, creado_en);

-- Lista de cotejo configurable por actividad/lección.
CREATE TABLE IF NOT EXISTS lista_cotejo_items (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    leccion_id  UUID         NOT NULL REFERENCES lecciones(id) ON DELETE CASCADE,
    descripcion VARCHAR(300) NOT NULL,
    puntos      INTEGER      NOT NULL CHECK (puntos > 0),
    orden       INTEGER      NOT NULL DEFAULT 0,
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cotejo_leccion_orden
    ON lista_cotejo_items (leccion_id, orden);

-- Profesores asignados a cada clase/curso.
CREATE TABLE IF NOT EXISTS curso_profesores (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    curso_id    UUID         NOT NULL REFERENCES cursos(id) ON DELETE CASCADE,
    profesor_id UUID         NOT NULL,
    asignado_en TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_curso_profesor UNIQUE (curso_id, profesor_id)
);

CREATE INDEX IF NOT EXISTS idx_curso_profesores_profesor
    ON curso_profesores (profesor_id, curso_id);

-- Mensajes persistentes del foro en tiempo real, un canal por curso.
CREATE TABLE IF NOT EXISTS mensajes_foro (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    curso_id      UUID         NOT NULL REFERENCES cursos(id) ON DELETE CASCADE,
    usuario_id    UUID         NOT NULL,
    autor_nombre  VARCHAR(160) NOT NULL,
    autor_rol     VARCHAR(20)  NOT NULL,
    contenido     VARCHAR(2000) NOT NULL,
    creado_en     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_foro_curso_creado
    ON mensajes_foro (curso_id, creado_en);

-- Los archivos existentes se convierten en la versión 1 de una entrega.
-- Se agrupan por alumno + lección para no violar la versión única cuando ya
-- había más de un archivo histórico para la misma actividad.
ALTER TABLE evidencias_practica
    ADD COLUMN IF NOT EXISTS entrega_id UUID,
    ADD COLUMN IF NOT EXISTS tipo_archivo VARCHAR(20) NOT NULL DEFAULT 'FOTO',
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Clasifica los archivos históricos por su MIME real; antes de esta versión
-- todos serían FOTO aunque algunos legados ya fueran PDF o video.
UPDATE evidencias_practica
SET tipo_archivo = CASE
    WHEN LOWER(content_type) = 'application/pdf' THEN 'PDF'
    WHEN LOWER(content_type) LIKE 'video/%' THEN 'VIDEO'
    ELSE 'FOTO'
END
WHERE entrega_id IS NULL;

WITH pares AS (
    SELECT DISTINCT usuario_id, leccion_id
    FROM evidencias_practica
    WHERE entrega_id IS NULL
), creadas AS (
    INSERT INTO entregas_practica (
        id, usuario_id, leccion_id, curso_id, version, estado,
        retroalimentacion, revisada_por, revisada_en, creado_en, actualizado_en
    )
    SELECT
        gen_random_uuid(),
        p.usuario_id,
        p.leccion_id,
        l.curso_id,
        1,
        CASE
            WHEN bool_or(e.estado = 'APROBADA') THEN 'APROBADA'
            WHEN bool_or(e.estado = 'RECHAZADA') THEN 'RECHAZADA'
            ELSE 'INCOMPLETA'
        END,
        max(e.retroalimentacion) FILTER (
            WHERE e.estado IN ('APROBADA', 'RECHAZADA')
        ),
        (array_agg(e.revisada_por ORDER BY e.revisada_en DESC NULLS LAST)
            FILTER (WHERE e.revisada_por IS NOT NULL))[1],
        max(e.revisada_en),
        min(e.creado_en),
        max(COALESCE(e.revisada_en, e.creado_en))
    FROM pares p
    JOIN lecciones l ON l.id = p.leccion_id
    JOIN evidencias_practica e
      ON e.usuario_id = p.usuario_id AND e.leccion_id = p.leccion_id
    GROUP BY p.usuario_id, p.leccion_id, l.curso_id
    RETURNING id, usuario_id, leccion_id
)
UPDATE evidencias_practica e
SET entrega_id = c.id
FROM creadas c
WHERE e.entrega_id IS NULL
  AND e.usuario_id = c.usuario_id
  AND e.leccion_id = c.leccion_id;

-- Una entrega heredada con foto + PDF queda disponible para revisión. Las
-- revisiones históricas APROBADA/RECHAZADA se conservan intactas.
UPDATE entregas_practica d
SET estado = CASE
    WHEN d.estado IN ('APROBADA', 'RECHAZADA') THEN d.estado
    WHEN EXISTS (
        SELECT 1 FROM evidencias_practica f
        WHERE f.entrega_id = d.id AND f.tipo_archivo = 'FOTO'
    ) AND EXISTS (
        SELECT 1 FROM evidencias_practica f
        WHERE f.entrega_id = d.id AND f.tipo_archivo = 'PDF'
    ) THEN 'PENDIENTE'
    ELSE 'INCOMPLETA'
END
WHERE d.id IN (SELECT DISTINCT entrega_id FROM evidencias_practica WHERE entrega_id IS NOT NULL);

ALTER TABLE evidencias_practica
    ALTER COLUMN entrega_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_evidencia_entrega'
    ) THEN
        ALTER TABLE evidencias_practica
            ADD CONSTRAINT fk_evidencia_entrega
            FOREIGN KEY (entrega_id) REFERENCES entregas_practica(id) ON DELETE CASCADE;
    END IF;
END $$;
