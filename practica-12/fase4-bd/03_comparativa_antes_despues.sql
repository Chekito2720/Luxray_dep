-- ===========================================================================
-- PRÁCTICA 12 — FASE 6 — COMPARATIVA ANTES / DESPUÉS DE LA OPTIMIZACIÓN DE BD
--
-- Medición sobre la MISMA tabla y con las MISMAS estadísticas, para que la
-- diferencia se atribuya únicamente al índice y no al estado del planificador.
--
--   ANTES    : se eliminan los índices GIN trigram y se reanaliza la tabla
--   DESPUÉS  : se crean los índices GIN trigram y se reanaliza
--
-- Se mide además el conjunto grande de registros con y sin paginación, y el
-- volumen de datos que arrastra cada variante.
-- ===========================================================================

\set ON_ERROR_STOP off
\timing on

\echo ''
\echo '###############################################################################'
\echo '# ANTES DE LA OPTIMIZACION (sin indice GIN trigram)'
\echo '###############################################################################'
DROP INDEX IF EXISTS idx_cursos_titulo_trgm;
DROP INDEX IF EXISTS idx_cursos_descripcion_trgm;
ANALYZE cursos;

\echo '--- A1 · consulta del buscador (todos los campos) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel, semanas, lecciones, estudiantes, rating,
       instructor, icon, color, publicado, creado_en
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- A2 · consulta del buscador (solo los campos que la lista necesita) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel, publicado
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- A3 · conjunto grande: las lecciones completas con contenido y preguntas ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, curso_id, titulo, descripcion, contenido, preguntas, tipo, duracion, orden
  FROM lecciones;

\echo '--- A4 · volumen de datos arrastrado por el conjunto grande ---'
SELECT count(*) AS filas,
       pg_size_pretty(sum(pg_column_size(l))) AS volumen_total,
       pg_size_pretty(sum(pg_column_size(l.contenido))) AS solo_contenido,
       pg_size_pretty(sum(pg_column_size(l.preguntas))) AS solo_preguntas
  FROM lecciones l;

\echo ''
\echo '###############################################################################'
\echo '# DESPUES DE LA OPTIMIZACION (con indice GIN trigram)'
\echo '###############################################################################'
CREATE INDEX IF NOT EXISTS idx_cursos_titulo_trgm
  ON cursos USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_cursos_descripcion_trgm
  ON cursos USING GIN (lower(descripcion) gin_trgm_ops);
ANALYZE cursos;

\echo '--- D1 · consulta del buscador (todos los campos) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel, semanas, lecciones, estudiantes, rating,
       instructor, icon, color, publicado, creado_en
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- D2 · consulta del buscador (solo los campos que la lista necesita) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel, publicado
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- D3 · conjunto grande con PAGINACION (solo 20 filas) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, curso_id, titulo, tipo, duracion, orden
  FROM lecciones ORDER BY curso_id, orden LIMIT 20;

\echo '--- D4 · conjunto grande con paginacion E indice de orden ---'
CREATE INDEX IF NOT EXISTS idx_lecciones_curso_orden_bench
  ON lecciones (curso_id, orden);
ANALYZE lecciones;
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, curso_id, titulo, tipo, duracion, orden
  FROM lecciones ORDER BY curso_id, orden LIMIT 20;

\echo '--- D5 · indices finales y su tamano ---'
SELECT indexrelname AS indice,
       pg_size_pretty(pg_relation_size(indexrelid)) AS tamano,
       idx_scan AS usos
  FROM pg_stat_user_indexes
 WHERE relname IN ('cursos','lecciones')
 ORDER BY relname, indexrelname;

\echo ''
\echo '--- Integridad tras la optimizacion ---'
SELECT (SELECT count(*) FROM cursos) AS cursos,
       (SELECT count(*) FROM cursos WHERE titulo IS NULL) AS titulos_nulos,
       (SELECT count(*) FROM lecciones) AS lecciones;
