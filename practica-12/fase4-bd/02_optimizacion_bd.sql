-- ===========================================================================
-- PRÁCTICA 12 — FASE 4 / FASE 6 — OPTIMIZACIÓN DE LA CONSULTA CRÍTICA
--
-- Consulta crítica : el BUSCADOR (la que usa el wearable y el sitio web)
--     SELECT ... FROM cursos
--      WHERE LOWER(titulo) LIKE %q% OR LOWER(descripcion) LIKE %q%
--
-- Problema medido en la Fase 4: el comodín INICIAL impide el uso de un
-- índice btree y la función LOWER() obliga a evaluar una expresión por fila:
-- el planificador cae siempre en "Seq Scan".
--
-- Técnicas aplicadas (de las propuestas por la práctica):
--   1 · AGREGAR ÍNDICES  -> índice GIN trigram sobre la expresión indexada,
--                            que SÍ soporta el patrón LIKE '%texto%'.
--   2 · RECUPERAR ÚNICAMENTE LOS CAMPOS NECESARIOS -> proyección en el DTO.
--   3 · PAGINACIÓN -> se añade LIMIT/OFFSET al listado del catálogo.
--
-- El benchmark usa una tabla `cursos_bench` con el MISMO esquema y 20 000 filas
-- sintéticas que replican el catálogo real: con 6 filas reales el planificador
-- elude el índice por Razor (correctamente), y la mejora sólo se puede
-- cuantificar a escala de producción.
--
-- Ejecutar:
--   cmd /c "type 02_optimizacion_bd.sql | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
-- ===========================================================================

\set ON_ERROR_STOP off
\timing on

\echo ''
\echo '###############################################################################'
\echo '# PASO 1 · PREPARACION: extension pg_trgm y dataset de benchmark'
\echo '###############################################################################'
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DROP TABLE IF EXISTS cursos_bench;
CREATE TABLE cursos_bench (
  id          uuid PRIMARY KEY,
  titulo      varchar(160) NOT NULL,
  descripcion varchar(500) NOT NULL,
  nivel       varchar(20)  NOT NULL,
  semanas     integer NOT NULL DEFAULT 12,
  lecciones   integer NOT NULL DEFAULT 0,
  estudiantes integer NOT NULL DEFAULT 0,
  rating      double precision NOT NULL DEFAULT 0,
  instructor  varchar(120) NOT NULL,
  icon        text NOT NULL DEFAULT 'pi-bolt',
  color       varchar(32) NOT NULL DEFAULT '#1565c0',
  publicado   boolean NOT NULL DEFAULT true,
  contenido   text,
  preguntas   jsonb,
  creado_en   timestamp NOT NULL DEFAULT now()
);

-- 20 000 cursos sinteticos con词汇 realisticos del giro electrico
INSERT INTO cursos_bench (id, titulo, descripcion, nivel, instructor, estudiantes, contenido, preguntas)
SELECT gen_random_uuid(),
       (ARRAY['Fundamentos','Instalaciones','Sistemas','Normativa','Seguridad',
              'Energia','Mantenimiento','Proteccion','Medicion','Automomatizacion',
              'Eficiencia','Fotovoltaica','Terrestre','Subterraneo','Industrial'])[1 + (g % 15)]
       || ' de ELECTRICIDAD ' || lpad(g::text, 6, '0'),
       'Curso de formacion tecnica sobre el modulo ' || g ||
       ' del giro electrico: instalaciones, normativa y seguridad laboral.',
       (ARRAY['BASICO','INTERMEDIO','AVANZADO'])[1 + (g % 3)],
       'Docente ' || (g % 120),
       (g % 400),
       repeat('Contenido pedagogico del modulo ' || g || '. ', 40),
       jsonb_build_array(jsonb_build_object('p', 'Pregunta ' || g,
                                            'o', jsonb_build_array('a','b','c','d')))
  FROM generate_series(1, 20000) g;

ANALYZE cursos_bench;
SELECT count(*) AS filas_benchmark, pg_size_pretty(pg_total_relation_size('cursos_bench')) AS tamano
  FROM cursos_bench;

\echo ''
\echo '###############################################################################'
\echo '# PASO 2 · ANTES DE LA OPTIMIZACION (sin indice trigram)'
\echo '###############################################################################'
\echo '--- 2a · consulta del buscador sobre 20 000 filas ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel
  FROM cursos_bench
 WHERE LOWER(titulo) LIKE '%sistemas%' OR LOWER(descripcion) LIKE '%sistemas%';

\echo '--- 2b · version con solo los campos necesarios (proyeccion) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo FROM cursos_bench
 WHERE LOWER(titulo) LIKE '%sistemas%' OR LOWER(descripcion) LIKE '%sistemas%';

\echo '--- 2c · conjunto grande SIN paginacion (48 lecciones reales equivalent) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, contenido FROM cursos_bench;

\echo '--- 2d · conjunto grande CON paginacion (LIMIT/OFFSET) ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion FROM cursos_bench ORDER BY titulo LIMIT 20 OFFSET 0;

\echo ''
\echo '###############################################################################'
\echo '# PASO 3 · OPTIMIZACION: indice GIN trigram sobre la expresion indexada'
\echo '###############################################################################'
CREATE INDEX IF NOT EXISTS idx_cursos_bench_titulo_trgm
  ON cursos_bench USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_cursos_bench_descripcion_trgm
  ON cursos_bench USING GIN (lower(descripcion) gin_trgm_ops);
ANALYZE cursos_bench;

\echo '--- 3a · DESPUES: la misma consulta del buscador ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel
  FROM cursos_bench
 WHERE LOWER(titulo) LIKE '%sistemas%' OR LOWER(descripcion) LIKE '%sistemas%';

\echo '--- 3b · DESPUES: con proyeccion ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo FROM cursos_bench
 WHERE LOWER(titulo) LIKE '%sistemas%' OR LOWER(descripcion) LIKE '%sistemas%';

\echo '--- 3c · DESPUES: conjunto grande con paginacion ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion FROM cursos_bench ORDER BY titulo LIMIT 20 OFFSET 0;

\echo ''
\echo '###############################################################################'
\echo '# PASO 4 · APLICAR LA MISMA OPTIMIZACION A LA TABLA REAL (cursos)'
\echo '###############################################################################'
CREATE INDEX IF NOT EXISTS idx_cursos_titulo_trgm
  ON cursos USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_cursos_descripcion_trgm
  ON cursos USING GIN (lower(descripcion) gin_trgm_ops);
ANALYZE cursos;

\echo '--- 4a · plan real sobre la tabla de la aplicacion ---'
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';

\echo '--- 4b · indice creado y su tamano ---'
SELECT indexrelname AS indice,
       pg_size_pretty(pg_relation_size(indexrelid)) AS tamano,
       idx_scan AS usos
  FROM pg_stat_user_indexes
 WHERE relname = 'cursos'
 ORDER BY indexrelname;

\echo ''
\echo '###############################################################################'
\echo '# PASO 5 · INTEGRIDAD TRAS LA OPTIMIZACION (los indices no alteran datos)'
\echo '###############################################################################'
SELECT
  (SELECT count(*) FROM cursos)                             AS cursos,
  (SELECT count(*) FROM cursos WHERE titulo IS NULL)        AS titulos_nulos,
  (SELECT count(*) FROM cursos WHERE nivel IS NULL)         AS niveles_nulos,
  (SELECT count(*) FROM cursos_bench)                       AS filas_benchmark,
  (SELECT count(*) FROM cursos_bench WHERE titulo IS NULL)  AS bench_nulos;

\echo ''
\echo '###############################################################################'
\echo '# PASO 6 · LIMPIEZA DEL DATASET DE BENCHMARK (no forma parte de la app)'
\echo '###############################################################################'
DROP TABLE IF EXISTS cursos_bench;
SELECT count(*) AS cursos_tras_limpieza FROM cursos;
