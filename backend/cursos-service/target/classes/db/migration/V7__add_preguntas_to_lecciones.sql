-- V7: agregar columna 'preguntas' a lecciones para almacenar preguntas de quiz (JSONB)
-- Solo las lecciones tipo QUIZ tendrán preguntas; las de VIDEO/LECTURA quedan NULL.

ALTER TABLE lecciones
    ADD COLUMN preguntas JSONB;

COMMENT ON COLUMN lecciones.preguntas IS 'Array de preguntas para lecciones tipo QUIZ: [{"id":"q1","texto":"...","opciones":["a","b","c","d"],"respuestaCorrecta":"a"}]';