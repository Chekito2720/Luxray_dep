-- Estado de inicialización del modo de compatibilidad para profesores.
-- Mientras no se haya guardado ninguna configuración de asignaciones, un
-- profesor puede operar todas las clases (instalación nueva). En cuanto un
-- administrador guarda asignaciones, el indicador queda activo para siempre:
-- borrar la última asignación no debe devolver el acceso global.
CREATE TABLE IF NOT EXISTS asignaciones_estado (
    id            INTEGER PRIMARY KEY CHECK (id = 1),
    inicializado  BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO asignaciones_estado (id, inicializado)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

-- Las aprobaciones históricas sin lista de cotejo evaluada, sin nota o sin
-- foto+PDF no conservan validez. V15 ya había devuelto a INCOMPLETA las que
-- faltaban archivos; aquí se completa la reconciliación.
UPDATE entregas_practica e
SET estado             = 'INCOMPLETA',
    retroalimentacion  = NULL,
    revisada_por       = NULL,
    revisada_en        = NULL,
    criterios_evaluacion = NULL,
    puntuacion         = NULL,
    actualizado_en     = NOW()
WHERE e.estado = 'APROBADA'
  AND (
      e.puntuacion IS NULL
      OR e.criterios_evaluacion IS NULL
      OR jsonb_typeof(e.criterios_evaluacion) <> 'array'
      OR jsonb_array_length(e.criterios_evaluacion) = 0
      OR jsonb_array_length(e.criterios_evaluacion) <> (
          SELECT COUNT(*)::int FROM lista_cotejo_items l WHERE l.leccion_id = e.leccion_id)
      OR EXISTS (
          SELECT 1 FROM jsonb_array_elements(e.criterios_evaluacion) c
          WHERE (c ->> 'cumplido')::boolean IS DISTINCT FROM TRUE)
      OR NOT EXISTS (
          SELECT 1 FROM evidencias_practica f
          WHERE f.entrega_id = e.id AND f.tipo_archivo = 'FOTO')
      OR NOT EXISTS (
          SELECT 1 FROM evidencias_practica f
          WHERE f.entrega_id = e.id AND f.tipo_archivo = 'PDF')
  );

-- El progreso heredado sólo es válido si existe una versión vigente aprobada
-- con foto, PDF, lista de cotejo completa y nota. Cualquier otro progreso se
-- reversa para que la actividad vuelva a quedar pendiente.
UPDATE progreso_lecciones p
SET completada    = FALSE,
    puntuacion    = NULL,
    completado_en = NULL
WHERE (p.completada IS TRUE OR p.puntuacion IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM entregas_practica e
      WHERE e.usuario_id = p.usuario_id
        AND e.leccion_id = p.leccion_id
        AND e.version = (
            SELECT MAX(e2.version) FROM entregas_practica e2
            WHERE e2.usuario_id = p.usuario_id AND e2.leccion_id = p.leccion_id)
        AND e.estado = 'APROBADA'
        AND e.puntuacion IS NOT NULL
        AND e.criterios_evaluacion IS NOT NULL
        AND jsonb_typeof(e.criterios_evaluacion) = 'array'
        AND jsonb_array_length(e.criterios_evaluacion) > 0
        AND jsonb_array_length(e.criterios_evaluacion) = (
            SELECT COUNT(*)::int FROM lista_cotejo_items l WHERE l.leccion_id = e.leccion_id)
        AND NOT EXISTS (
            SELECT 1 FROM jsonb_array_elements(e.criterios_evaluacion) c
            WHERE (c ->> 'cumplido')::boolean IS DISTINCT FROM TRUE)
        AND EXISTS (
            SELECT 1 FROM evidencias_practica f
            WHERE f.entrega_id = e.id AND f.tipo_archivo = 'FOTO')
        AND EXISTS (
            SELECT 1 FROM evidencias_practica f
            WHERE f.entrega_id = e.id AND f.tipo_archivo = 'PDF')
  );

-- Recalcular inscripciones después de la reconciliación.
WITH resumen AS (
    SELECT i.id AS inscripcion_id,
           i.usuario_id,
           i.curso_id,
           COUNT(l.id)::int AS total,
           COUNT(l.id) FILTER (WHERE p.completada IS TRUE)::int AS completadas,
           (SELECT l2.titulo
              FROM lecciones l2
             WHERE l2.curso_id = i.curso_id
               AND NOT EXISTS (
                   SELECT 1 FROM progreso_lecciones p2
                    WHERE p2.usuario_id = i.usuario_id
                      AND p2.leccion_id = l2.id
                      AND p2.completada IS TRUE)
             ORDER BY l2.orden
            LIMIT 1) AS proxima
      FROM inscripciones i
      LEFT JOIN lecciones l ON l.curso_id = i.curso_id
      LEFT JOIN progreso_lecciones p
             ON p.usuario_id = i.usuario_id AND p.leccion_id = l.id
     GROUP BY i.id, i.usuario_id, i.curso_id
)
UPDATE inscripciones i
SET lecciones_completadas = r.completadas,
    porcentaje = CASE WHEN r.total = 0 THEN 0
                      ELSE ROUND(r.completadas * 100.0 / r.total)::int END,
    proxima_leccion = r.proxima
FROM resumen r
WHERE r.inscripcion_id = i.id;
