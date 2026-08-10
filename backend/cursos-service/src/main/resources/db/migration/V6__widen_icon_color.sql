-- V6: ampliar columnas icon y color para permitir URLs reales de imágenes.
-- icon: VARCHAR(16) → TEXT (puede ser URL completa https://...)
-- color: VARCHAR(16) → VARCHAR(32) (sigue siendo hex, pero da margen para gradientes)

ALTER TABLE cursos
    ALTER COLUMN icon TYPE TEXT,
    ALTER COLUMN color TYPE VARCHAR(32);
