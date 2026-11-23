-- Desactiva las cuentas conocidas en producción sin cambiar el comportamiento
-- de desarrollo. El placeholder Flyway se resuelve con DISABLE_SEED_ACCOUNTS
-- (true por defecto; Docker Compose lo configura como false).

UPDATE usuarios
   SET activo = FALSE
 WHERE email IN ('demo@luxray.com', 'admin@luxray.com')
   AND ${disableseedaccounts};
