CREATE TABLE IF NOT EXISTS usuarios (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(80)  NOT NULL,
    apellido        VARCHAR(80)  NOT NULL,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    avatar          VARCHAR(255),
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email       ON usuarios (email);
CREATE INDEX IF NOT EXISTS idx_usuarios_creado_en   ON usuarios (creado_en);

-- Usuario demo: password es "Demo1234!"  hash bcrypt cost 10
-- (se genera en V2 como seed real para evitar inconsistencias)
INSERT INTO usuarios (id, nombre, apellido, email, password_hash, avatar)
VALUES
    (gen_random_uuid(), 'Demo', 'LuxRay', 'demo@luxray.com',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', NULL);
