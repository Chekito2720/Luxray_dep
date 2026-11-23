-- Revisión docente: retroalimentación del profesor y expediente del alumno.
-- Estados de la evidencia:
--   PENDIENTE → subida registrada, esperando revisión del profesor
--   APROBADA  → práctica completada (el profesor la validó)
--   RECHAZADA → requiere reintentar (siempre con retroalimentación)
ALTER TABLE evidencias_practica
    ADD COLUMN IF NOT EXISTS retroalimentacion TEXT,
    ADD COLUMN IF NOT EXISTS revisada_por      UUID,
    ADD COLUMN IF NOT EXISTS revisada_en      TIMESTAMP;

-- Las evidencias ya subidas quedan en cola de revisión docente
UPDATE evidencias_practica SET estado = 'PENDIENTE' WHERE estado = 'COMPLETADA';

CREATE INDEX IF NOT EXISTS idx_evid_estado ON evidencias_practica (estado);
