-- ===========================================================================
-- PRÁCTICA 11 — FASE 4 — DB-06 · CONCURRENCIA
-- Sesion B del Escenario 1: intenta insertar la MISMA inscripcion.
-- Debe FALLAR con unique_violation sobre el indice
-- (usuario_id, curso_id) — la base de datos arbitra la unicidad.
-- ===========================================================================
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO inscripciones (id, usuario_id, curso_id)
VALUES ('33333333-3333-4333-8333-000000000062',
        '44444444-4444-4444-8444-000000000061',
        '11111111-1111-4111-8111-000000000061');
COMMIT;
