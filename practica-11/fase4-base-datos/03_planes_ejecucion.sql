-- ===========================================================================
-- PRÁCTICA 11 — FASE 4 — MEDICIÓN DE CONSULTAS Y PLANES DE EJECUCIÓN
--
-- Objetivo: registrar el tiempo de ejecución real de al menos 3 consultas y
-- su plan de ejecución (EXPLAIN ANALYZE) para comprobar si usan índices.
--
-- Consultas evaluadas:
--   Q1 · SELECT por clave primaria (id)                  -> index scan esperado
--   Q2 · SELECT por nivel                                -> idx_cursos_nivel
--   Q3 · SELECT por publicado                            -> idx_cursos_publicado
--   Q4 · SELECT por texto con LIKE '%q%'  (buscador)     -> seq scan (INEFICIENTE)
--   Q5 · JOIN cursos + inscripciones (panel del alumno)  -> index scan + join
--
-- Ejecutar:
--   cmd /c "type 03_planes_ejecucion.sql | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
-- ===========================================================================

\set ON_ERROR_STOP off
\timing on

\echo ''
\echo '###############################################################################'
\echo '# Q1 · SELECT por clave primaria (id) — indice: cursos_pkey'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel, semanas, lecciones, instructor, publicado
  FROM cursos
 WHERE id = '11111111-1111-4111-8111-000000000011';

\echo ''
\echo '###############################################################################'
\echo '# Q2 · SELECT por nivel — indice: idx_cursos_nivel'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel FROM cursos WHERE nivel = 'BASICO' AND publicado = true;

\echo ''
\echo '###############################################################################'
\echo '# Q3 · SELECT por publicado — indice: idx_cursos_publicado'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo FROM cursos WHERE publicado = true ORDER BY titulo;

\echo ''
\echo '###############################################################################'
\echo '# Q4 · Buscador por texto (LIKE con comodin inicial) — SIN indice utilizable'
\echo '#      Esta es la consulta de CursosRepository.search(nivel, query):'
\echo '#      LOWER(titulo) LIKE %q% OR LOWER(descripcion) LIKE %q%'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo FROM cursos
 WHERE LOWER(titulo) LIKE '%ohm%' OR LOWER(descripcion) LIKE '%ohm%';

\echo ''
\echo '###############################################################################'
\echo '# Q5 · JOIN cursos + inscripciones (panel "mis cursos") — FK indexada'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT c.id, c.titulo, i.inscrito_en, i.porcentaje
  FROM inscripciones i
  JOIN cursos c ON c.id = i.curso_id
 WHERE i.usuario_id = '44444444-4444-4444-8444-000000000061';

\echo ''
\echo '###############################################################################'
\echo '# Q6 · Lecciones de un curso — indice: idx_lecciones_curso_orden'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, tipo, orden
  FROM lecciones
 WHERE curso_id = '11111111-1111-4111-8111-000000000011'
 ORDER BY orden;

\echo ''
\echo '###############################################################################'
\echo '# TAMAÑO DE LAS TABLAS (para interpretar los planes)'
\echo '###############################################################################'
SELECT relname AS tabla,
       n_live_tup AS filas_aprox,
       pg_size_pretty(pg_total_relation_size(relid)) AS tamano
  FROM pg_stat_user_tables
 ORDER BY n_live_tup DESC;

\echo ''
\echo '###############################################################################'
\echo '# INDICES DISPONIBLES (verificacion de las restricciones UNIQUE)'
\echo '###############################################################################'
SELECT tablename, indexname, indexdef
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename IN ('cursos','inscripciones','progreso_lecciones','usuarios')
 ORDER BY tablename, indexname;
