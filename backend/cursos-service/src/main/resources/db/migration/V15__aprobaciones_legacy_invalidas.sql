-- Política de compatibilidad para revisiones anteriores al modelo versionado:
-- una aprobación histórica sin foto y PDF no conserva validez. La entrega
-- vuelve a INCOMPLETA para que el alumno complete los archivos y el profesor
-- la revise de nuevo; además se revierte el progreso que se había derivado
-- de esa aprobación.
CREATE TEMP TABLE aprobaciones_legacy_invalidas ON COMMIT DROP AS
SELECT
    d.id,
    d.usuario_id,
    d.leccion_id,
    d.curso_id
FROM entregas_practica d
WHERE d.estado = 'APROBADA'
  AND NOT (
      EXISTS (
          SELECT 1
          FROM evidencias_practica f
          WHERE f.entrega_id = d.id AND f.tipo_archivo = 'FOTO'
      )
      AND EXISTS (
          SELECT 1
          FROM evidencias_practica f
          WHERE f.entrega_id = d.id AND f.tipo_archivo = 'PDF'
      )
  );

UPDATE evidencias_practica e
SET estado = 'PENDIENTE'
FROM aprobaciones_legacy_invalidas i
WHERE e.entrega_id = i.id
  AND e.estado IN ('APROBADA', 'RECHAZADA');

UPDATE entregas_practica d
SET estado = 'INCOMPLETA',
    retroalimentacion = NULL,
    revisada_por = NULL,
    revisada_en = NULL,
    criterios_evaluacion = NULL,
    puntuacion = NULL,
    actualizado_en = NOW()
FROM aprobaciones_legacy_invalidas i
WHERE d.id = i.id;

UPDATE progreso_lecciones p
SET completada = FALSE,
    puntuacion = NULL,
    completado_en = NULL
FROM aprobaciones_legacy_invalidas i
WHERE p.usuario_id = i.usuario_id
  AND p.leccion_id = i.leccion_id;

WITH afectadas AS (
    SELECT DISTINCT usuario_id, curso_id
    FROM aprobaciones_legacy_invalidas
)
UPDATE inscripciones i
SET lecciones_completadas = (
        SELECT COUNT(*)::INTEGER
        FROM progreso_lecciones p
        JOIN lecciones l ON l.id = p.leccion_id
        WHERE p.usuario_id = i.usuario_id
          AND l.curso_id = i.curso_id
          AND p.completada = TRUE
    ),
    porcentaje = (
        SELECT CASE
            WHEN COUNT(*) = 0 THEN 0
            ELSE ROUND(
                COUNT(*) FILTER (WHERE p.completada IS TRUE)::NUMERIC * 100.0
                / COUNT(*)::NUMERIC
            )::INTEGER
        END
        FROM lecciones l
        LEFT JOIN progreso_lecciones p
          ON p.leccion_id = l.id
         AND p.usuario_id = i.usuario_id
        WHERE l.curso_id = i.curso_id
    ),
    proxima_leccion = (
        SELECT l.titulo
        FROM lecciones l
        WHERE l.curso_id = i.curso_id
          AND NOT EXISTS (
              SELECT 1
              FROM progreso_lecciones p
              WHERE p.usuario_id = i.usuario_id
                AND p.leccion_id = l.id
                AND p.completada = TRUE
          )
        ORDER BY l.orden
        LIMIT 1
    )
FROM afectadas a
WHERE i.usuario_id = a.usuario_id
  AND i.curso_id = a.curso_id;

DROP TABLE aprobaciones_legacy_invalidas;
