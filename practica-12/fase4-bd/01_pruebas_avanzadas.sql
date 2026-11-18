-- ===========================================================================
-- PRÁCTICA 12 — FASE 4 — PRUEBAS AVANZADAS DE BASE DE DATOS
-- Motor     : PostgreSQL 16 (contenedor luxray-cursos-db, base cursos_db)
-- Consulta crítica elegida : el BUSCADOR del sitio y del wearable
--   SELECT ... FROM cursos
--    WHERE LOWER(titulo) LIKE %q% OR LOWER(descripcion) LIKE %q%
--   (CursoRepository.search / CursosService.buscar)
--
-- Las 6 pruebas obligatorias:
--   1 · Consulta individual por identificador
--   2 · Consulta de un conjunto pequeño de registros
--   3 · Consulta de un conjunto grande de registros
--   4 · Consulta con filtros
--   5 · Actualización de registros concurrentes
--   6 · Escrituras simultáneas sobre un mismo registro
--
-- Se mide además: tiempo, filas recuperadas, uso de índices, bloqueos y
-- esperas, e integridad de los datos tras las operaciones.
-- ===========================================================================

\set ON_ERROR_STOP off
\timing on

\echo ''
\echo '###############################################################################'
\echo '# 0 · VOLUMEN DE DATOS DE REFERENCIA'
\echo '###############################################################################'
SELECT relname AS tabla,
       n_live_tup AS filas_aprox,
       pg_size_pretty(pg_total_relation_size(relid)) AS tamano_total,
       pg_size_pretty(pg_relation_size(relid)) AS solo_datos
  FROM pg_stat_user_tables
 ORDER BY n_live_tup DESC;

SELECT
  (SELECT count(*) FROM cursos)                            AS cursos,
  (SELECT count(*) FROM lecciones)                         AS lecciones,
  (SELECT count(*) FROM inscripciones)                     AS inscripciones,
  (SELECT count(*) FROM progreso_lecciones)                AS progreso,
  (SELECT count(*) FROM evidencias_practica)               AS evidencias;

\echo ''
\echo '###############################################################################'
\echo '# 1 · CONSULTA INDIVIDUAL POR IDENTIFICADOR'
\echo '#     (la mas frecuente del sistema: detalle de curso y de leccion)'
\echo '###############################################################################'
\echo '--- 1a · por clave primaria (curso) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel, semanas, lecciones, estudiantes, rating,
       instructor, icon, color, publicado, creado_en
  FROM cursos WHERE id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590';

\echo '--- 1b · por clave primaria (leccion) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, curso_id, titulo, tipo, duracion, orden
  FROM lecciones WHERE id = (SELECT id FROM lecciones LIMIT 1);

\echo ''
\echo '###############################################################################'
\echo '# 2 · CONJUNTO PEQUEÑO DE REGISTROS (listado de 6 cursos)'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel, publicado FROM cursos WHERE publicado = true;

\echo ''
\echo '###############################################################################'
\echo '# 3 · CONJUNTO GRANDE DE REGISTROS'
\echo '#     (TODAS las lecciones de TODOS los cursos, sin paginacion)'
\echo '###############################################################################'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT l.id, l.curso_id, l.titulo, l.descripcion, l.contenido, l.preguntas,
       l.tipo, l.duracion, l.orden, l.creado_en
  FROM lecciones l;

\echo '--- 3b · el MISMO conjunto, pero solo con los campos que la UI necesita ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT l.id, l.titulo, l.tipo FROM lecciones l;

\echo '--- 3c · volumen de datos que arrastra el conjunto grande ---'
SELECT count(*) AS filas,
       pg_size_pretty(sum(pg_column_size(l))) AS volumen_pg_column_size,
       pg_size_pretty(sum(octet_length(l.contenido::text))) AS solo_contenido_texto
  FROM lecciones l;

\echo ''
\echo '###############################################################################'
\echo '# 4 · CONSULTA CON FILTROS (EL BUSCADOR: la consulta crítica)'
\echo '###############################################################################'
\echo '--- 4a · texto con comodín inicial (usa el buscador) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- 4b · mismo filtro con filtro de nivel ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel
  FROM cursos
 WHERE publicado = true
   AND nivel = 'AVANZADO'
   AND (LOWER(titulo) LIKE '%sistemas%' OR LOWER(descripcion) LIKE '%sistemas%');

\echo '--- 4c · filtro por nivel SIN texto (SI usa indice) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, nivel FROM cursos WHERE nivel = 'AVANZADO';

\echo '--- 4d · inspeccion del indice: tamaño y uso ---'
SELECT s.relname AS tabla, s.indexrelname AS indice,
       c.reltuples AS filas_estimadas,
       pg_size_pretty(pg_relation_size(s.indexrelid)) AS tamano_indice
  FROM pg_stat_user_indexes s
  JOIN pg_class c ON c.oid = s.indexrelid
 WHERE s.relname IN ('cursos','lecciones')
 ORDER BY s.relname, s.indexrelname;

\echo ''
\echo '###############################################################################'
\echo '# 5 · ACTUALIZACION DE REGISTROS CONCURRENTES'
\echo '###############################################################################'
\echo '--- 5a · actualizacion masiva de un conjunto (cursos) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
UPDATE cursos SET descripcion = descripcion WHERE id IN (
  SELECT id FROM cursos LIMIT 3);

\echo '--- 5b · actualizacion de un solo registro (mas frecuente) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
UPDATE cursos SET creado_en = creado_en
 WHERE id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590';

\echo ''
\echo '###############################################################################'
\echo '# 6 · ESCRITURAS SIMULTANEAS SOBRE UN MISMO REGISTRO'
\echo '#     (el curso se inscribe en varios movimientos a la vez)'
\echo '###############################################################################'
\echo '--- 6a · escritura concurrente sobre la MISMA fila desde 4 sesiones ---'
\echo '---     (se ejecuta desde el orquestador Python: 4 procesos psql a la vez) ---'

\echo ''
\echo '###############################################################################'
\echo '# 7 · INTEGRIDAD DE LOS DATOS TRAS LAS OPERACIONES'
\echo '###############################################################################'
SELECT
  (SELECT count(*) FROM cursos WHERE titulo IS NULL)                   AS titulos_nulos,
  (SELECT count(*) FROM cursos WHERE nivel IS NULL)                    AS niveles_nulos,
  (SELECT count(*) FROM cursos WHERE publicado IS NULL)                AS publicado_nulo,
  (SELECT count(*) FROM cursos WHERE creado_en IS NULL)                AS fecha_nula,
  (SELECT count(*) FROM cursos c WHERE NOT EXISTS
      (SELECT 1 FROM lecciones l WHERE l.curso_id = c.id))            AS cursos_sin_lecciones,
  (SELECT count(*) FROM inscripciones i WHERE NOT EXISTS
      (SELECT 1 FROM cursos c WHERE c.id = i.curso_id))               AS inscripciones_huerfanas,
  (SELECT count(*) FROM progreso_lecciones p WHERE NOT EXISTS
      (SELECT 1 FROM lecciones l WHERE l.id = p.leccion_id))          AS progreso_huerfano,
  (SELECT count(*) FROM (
      SELECT usuario_id, curso_id FROM inscripciones
       GROUP BY usuario_id, curso_id HAVING count(*) > 1) d)          AS duplicados_inscripcion;

\echo ''
\echo '###############################################################################'
\echo '# 8 · USO REAL DE LOS INDICES Y CONSULTAS REPETIDAS'
\echo '#     (pg_stat_statements no esta habilitado en el contenedor: se usan los'
\echo '#      contadores de uso de indices y los de la base, ambos nativos)'
\echo '###############################################################################'
SELECT relname AS tabla, indexrelname AS indice,
       idx_scan AS veces_usado,
       idx_tup_read AS filas_leidas,
       idx_tup_fetch AS filas_devueltas
  FROM pg_stat_user_indexes
 WHERE relname IN ('cursos','lecciones','inscripciones','progreso_lecciones')
 ORDER BY relname, idx_scan DESC;

\echo ''
\echo '--- Contadores globales de la base (lecturas devueltas por consulta) ---'
SELECT datname, xact_commit, xact_rollback, tup_returned, tup_fetched,
       tup_inserted, tup_updated, blks_read, blks_hit,
       round(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct
  FROM pg_stat_database
 WHERE datname IN ('cursos_db','auth_db');

\echo ''
\echo '--- Consultas repetidas: el patron check-then-act del servicio ---'
\echo '--- (AuthService.register hace existsByEmail() y despues save(); la tabla ---'
\echo '---  usuarios vive en auth_db y se verifica alli) ---'
SELECT count(*) AS inscriptions_con_duplicado
  FROM (SELECT usuario_id, curso_id FROM inscripciones
        GROUP BY usuario_id, curso_id HAVING count(*) > 1) d;

\echo ''
\echo '###############################################################################'
\echo '# 9 · ESPERAS Y BLOQUEOS OBSERVADOS'
\echo '###############################################################################'
SELECT pid, state, wait_event_type, wait_event, now() - query_start AS duracion,
       left(query, 80) AS query
  FROM pg_stat_activity
 WHERE datname = 'cursos_db' AND pid <> pg_backend_pid()
 ORDER BY query_start;

SELECT locktype, mode, granted, count(*)
  FROM pg_locks
 GROUP BY locktype, mode, granted
 ORDER BY locktype, mode;
