-- ===========================================================================
-- PRÁCTICA 11 — FASE 4 — WHITE BOX DE LA BASE DE DATOS (PostgreSQL)
-- Base de datos : cursos_db  (contenedor luxray-cursos-db)
-- Componente    : cursos-service (Spring Data JPA + Flyway)
-- Objetivo      : CRUD, integridad, consistencia, planes de ejecucion.
--
-- Nota de entorno: se ejecuta sobre el stack Docker de la practica
-- (docker compose up -d). Todos los datos de prueba se crean y eliminan
-- dentro de la transaccion de prueba y se identifican con el prefijo
-- 'WB11' para poder localizarlos y limpiarlos.
--
-- Ejecutar:
--   cmd /c "type 01_crud_integridad.sql | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
-- ===========================================================================

\set ON_ERROR_STOP off
\timing on

\echo '==============================================================='
\echo ' DB-01 · INSERCION DE UN REGISTRO VALIDO (cursos)'
\echo '==============================================================='
INSERT INTO cursos (id, titulo, descripcion, nivel, semanas, lecciones,
                    estudiantes, rating, instructor, icon, color, publicado)
VALUES ('11111111-1111-4111-8111-000000000011',
        'WB11 Curso de auditoria', 'Descripcion valida para la practica 11',
        'BASICO', 8, 24, 0, 4.5, 'Docente WB11', 'pi-bolt', '#1565c0', true);

SELECT id, titulo, nivel, publicado, creado_en IS NOT NULL AS tiene_fecha
  FROM cursos WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '==============================================================='
\echo ' DB-01b · INSERCION CON DATOS INCOMPLETOS -> NOT NULL'
\echo '==============================================================='
\echo '--- titulo NULL (debe fallar con not_null_violation) ---'
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000012', NULL, 'sin titulo', 'BASICO', 'X');

\echo '--- instructor NULL (debe fallar con not_null_violation) ---'
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000013', 'WB11 sin instructor', 'x', 'BASICO', NULL);

\echo '--- nivel fuera del CHECK (debe fallar con check_violation) ---'
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000014', 'WB11 nivel invalido', 'x', 'EXPERTO', 'X');

\echo '--- titulo > 160 caracteres (debe fallar con value_too_long) ---'
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000015', repeat('X', 161), 'x', 'BASICO', 'X');

\echo '--- PK duplicada (debe fallar con unique_violation) ---'
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000011', 'WB11 duplicado', 'x', 'BASICO', 'X');

\echo '--- comprobacion: no se ha filtrado ninguna fila invalida ---'
SELECT count(*) AS filas_wb11_creadas FROM cursos
 WHERE id IN ('11111111-1111-4111-8111-000000000012',
              '11111111-1111-4111-8111-000000000013',
              '11111111-1111-4111-8111-000000000014',
              '11111111-1111-4111-8111-000000000015');

\echo '==============================================================='
\echo ' DB-03 · CONSULTA POR IDENTIFICADOR (existente / inexistente)'
\echo '==============================================================='
\echo '--- existente: debe devolver 1 fila ---'
SELECT count(*) AS filas_encontradas FROM cursos
 WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '--- inexistente: debe devolver 0 filas ---'
SELECT count(*) AS filas_encontradas FROM cursos
 WHERE id = '99999999-9999-4999-8999-999999999999';

\echo '==============================================================='
\echo ' DB-02 · INTEGRIDAD REFERENCIAL (FK) Y UNIQUE'
\echo '==============================================================='
\echo '--- FK: leccion con curso_id inexistente (debe fallar con foreign_key_violation) ---'
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo,
                       duracion, tipo)
VALUES ('22222222-2222-4222-8222-000000000021',
        '99999999-9999-4999-8999-999999999999', 'WB11-S1', 'Seccion WB11',
        'WB11 leccion huerfana', '10 min', 'LECTURA');

\echo '--- FK valida: leccion con curso_id existente (debe funcionar) ---'
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo,
                       duracion, tipo, orden)
VALUES ('22222222-2222-4222-8222-000000000022',
        '11111111-1111-4111-8111-000000000011', 'WB11-S1', 'Seccion WB11',
        'WB11 leccion valida', '10 min', 'LECTURA', 1);

\echo '--- tipo fuera del CHECK (debe fallar con check_violation) ---'
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo,
                       duracion, tipo)
VALUES ('22222222-2222-4222-8222-000000000023',
        '11111111-1111-4111-8111-000000000011', 'WB11-S1', 'Seccion WB11',
        'WB11 tipo invalido', '10 min', 'PODCAST');

\echo '--- UNIQUE (usuario_id, curso_id) en inscripciones ---'
INSERT INTO inscripciones (id, usuario_id, curso_id)
VALUES ('33333333-3333-4333-8333-000000000031',
        '44444444-4444-4444-8444-000000000041',
        '11111111-1111-4111-8111-000000000011');

\echo '--- duplicado exacto: debe fallar con unique_violation ---'
INSERT INTO inscripciones (id, usuario_id, curso_id)
VALUES ('33333333-3333-4333-8333-000000000032',
        '44444444-4444-4444-8444-000000000041',
        '11111111-1111-4111-8111-000000000011');

\echo '--- el indice UNIQUE realmente esta definido ---'
SELECT indexdef FROM pg_indexes
 WHERE tablename = 'inscripciones' AND indexdef ILIKE '%UNIQUE%';

\echo '==============================================================='
\echo ' DB-04 · ACTUALIZACION DE UN REGISTRO'
\echo '==============================================================='
UPDATE cursos
   SET titulo = 'WB11 Curso actualizado', publicado = false, semanas = 10
 WHERE id = '11111111-1111-4111-8111-000000000011';

SELECT id, titulo, publicado, semanas FROM cursos
 WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '--- actualizacion de 0 filas (id inexistente) ---'
UPDATE cursos SET titulo = 'no existe'
 WHERE id = '99999999-9999-4999-8999-999999999999';

\echo '--- una actualizacion que viola el CHECK debe fallar y ROLLBACK ---'
UPDATE cursos SET nivel = 'SUPER'
 WHERE id = '11111111-1111-4111-8111-000000000011';
SELECT nivel AS nivel_tras_intento_fallido FROM cursos
 WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '==============================================================='
\echo ' DB-05 · ELIMINACION Y VERIFICACION DE PERSISTENCIA'
\echo '==============================================================='
\echo '--- antes de borrar ---'
SELECT (SELECT count(*) FROM cursos WHERE id='11111111-1111-4111-8111-000000000011') AS curso,
       (SELECT count(*) FROM lecciones WHERE curso_id='11111111-1111-4111-8111-000000000011') AS lecciones,
       (SELECT count(*) FROM inscripciones WHERE curso_id='11111111-1111-4111-8111-000000000011') AS inscripciones;

DELETE FROM cursos WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '--- despues de borrar (curso=0, y ON DELETE CASCADE debe limpiar las filas hijas) ---'
SELECT (SELECT count(*) FROM cursos WHERE id='11111111-1111-4111-8111-000000000011') AS curso,
       (SELECT count(*) FROM lecciones WHERE curso_id='11111111-1111-4111-8111-000000000011') AS lecciones,
       (SELECT count(*) FROM inscripciones WHERE curso_id='11111111-1111-4111-8111-000000000011') AS inscripciones;

\echo '--- lectura posterior de confirmacion: 0 filas ---'
SELECT count(*) AS filas_tras_borrado FROM cursos
 WHERE id = '11111111-1111-4111-8111-000000000011';

\echo '==============================================================='
\echo ' TRANSACCIONES · OPERACIONES PARCIALMENTE COMPLETADAS'
\echo '==============================================================='
\echo '--- ROLLBACK: el INSERT debe deshacerse por completo ---'
BEGIN;
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000051', 'WB11 rollback', 'x', 'BASICO', 'X');
SELECT count(*) AS dentro_de_transaccion FROM cursos
 WHERE id='11111111-1111-4111-8111-000000000051';
ROLLBACK;
SELECT count(*) AS tras_rollback FROM cursos
 WHERE id='11111111-1111-4111-8111-000000000051';

\echo '--- COMMIT: el INSERT debe persistir ---'
BEGIN;
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000052', 'WB11 commit', 'x', 'BASICO', 'X');
COMMIT;
SELECT count(*) AS tras_commit FROM cursos
 WHERE id='11111111-1111-4111-8111-000000000052';
DELETE FROM cursos WHERE id='11111111-1111-4111-8111-000000000052';

\echo '==============================================================='
\echo ' CONSISTENCIA SERVIDOR <-> APLICACION (misma tabla que usa JPA)'
\echo '==============================================================='
SELECT
  (SELECT count(*) FROM cursos WHERE publicado = true)  AS cursos_publicados,
  (SELECT count(*) FROM lecciones)                       AS lecciones,
  (SELECT count(*) FROM cursos WHERE published IS NULL) AS cursos_con_publicado_null,
  (SELECT count(*) FROM cursos WHERE creado_en IS NULL) AS cursos_sin_fecha;
