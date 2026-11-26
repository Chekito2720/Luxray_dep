-- ===========================================================================
-- PRÁCTICA 11 — FASE 4 — DB-06 · CONCURRENCIA
-- Sesion A del Escenario 1: gana la carrera e inserta la inscripcion.
-- Se mantiene la transaccion abierta 3 s para forzar la concurrencia.
-- ===========================================================================
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO cursos (id, titulo, descripcion, nivel, instructor)
VALUES ('11111111-1111-4111-8111-000000000061',
        'WB11 concurrencia', 'Curso de prueba de concurrencia', 'BASICO', 'Docente WB11');

INSERT INTO inscripciones (id, usuario_id, curso_id)
VALUES ('33333333-3333-4333-8333-000000000061',
        '44444444-4444-4444-8444-000000000061',
        '11111111-1111-4111-8111-000000000061');

SELECT pg_sleep(3);
COMMIT;
