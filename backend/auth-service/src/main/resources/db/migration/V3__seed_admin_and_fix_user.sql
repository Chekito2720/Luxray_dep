-- Corrige el hash bcrypt del seed inicial (V1 usaba un hash público incorrecto)
-- y agrega un usuario administrador.
-- Passwords:
--   demo@luxray.com  -> Demo1234!
--   admin@luxray.com -> Admin1234!

UPDATE usuarios
   SET password_hash = '$2b$10$PR.yT8dNvPzL7wbTFzhC4uckRsD4R1X/pQzsnx3ObwjHloECKV7QK',
       rol = 'USER'
 WHERE email = 'demo@luxray.com';

INSERT INTO usuarios (nombre, apellido, email, password_hash, rol, avatar)
VALUES ('Admin', 'LuxRay', 'admin@luxray.com',
        '$2b$10$b1mYWGICsUt4ADJfn46WlO8YQ8i2za4CRy7D7tLu9Zr39penx./m2',
        'ADMIN', NULL)
ON CONFLICT (email) DO UPDATE
   SET password_hash = EXCLUDED.password_hash,
       rol = EXCLUDED.rol;
