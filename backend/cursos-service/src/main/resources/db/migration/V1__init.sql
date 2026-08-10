CREATE TABLE IF NOT EXISTS cursos (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo          VARCHAR(160) NOT NULL,
    descripcion     VARCHAR(500) NOT NULL,
    nivel           VARCHAR(20)  NOT NULL CHECK (nivel IN ('BASICO','INTERMEDIO','AVANZADO')),
    semanas         INTEGER      NOT NULL DEFAULT 12,
    lecciones       INTEGER      NOT NULL DEFAULT 0,
    estudiantes     INTEGER      NOT NULL DEFAULT 0,
    rating              DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    instructor      VARCHAR(120) NOT NULL,
    icon            VARCHAR(16)  NOT NULL DEFAULT 'pi-bolt',
    color           VARCHAR(16)  NOT NULL DEFAULT '#1565c0',
    publicado       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cursos_nivel     ON cursos (nivel);
CREATE INDEX IF NOT EXISTS idx_cursos_publicado ON cursos (publicado);

INSERT INTO cursos (id, titulo, descripcion, nivel, semanas, lecciones, estudiantes, rating, instructor, icon, color, publicado) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'Fundamentos de Electricidad',
     'Aprende los conceptos esenciales: voltaje, corriente, resistencia y circuitos básicos. Sin requisitos previos.',
     'BASICO', 6, 24, 2400, 4.8, 'Ing. Roberto Mendoza', 'pi-bolt', '#16a34a', TRUE),
    ('22222222-2222-2222-2222-222222222222',
     'Instalaciones Residenciales',
     'Instalación y mantenimiento de sistemas eléctricos en viviendas, normas y seguridad. Ideal para electricistas en formación.',
     'INTERMEDIO', 10, 38, 1850, 4.7, 'Ing. Claudia Ríos', 'pi-home', '#1565c0', TRUE),
    ('33333333-3333-3333-3333-333333333333',
     'Sistemas Industriales',
     'Control de motores, tableros eléctricos y automatización industrial. Para profesionales en activo.',
     'AVANZADO', 15, 55, 980, 4.9, 'Ing. Alejandro Torres', 'pi-cog', '#37474f', TRUE),
    ('44444444-4444-4444-4444-444444444444',
     'Energía Solar Fotovoltaica',
     'Diseño, instalación y mantenimiento de sistemas solares residenciales y comerciales. Certificación NABCEP.',
     'INTERMEDIO', 12, 32, 1100, 4.85, 'Ing. Patricia Gómez', 'pi-sun', '#f59e0b', TRUE),
    ('55555555-5555-5555-5555-555555555555',
     'Normativa NOM-001-SEDE',
     'Interpretación práctica de la NOM-001-SEDE-2012 con casos reales y actualización 2024.',
     'AVANZADO', 8, 18, 720, 4.6, 'Mtra. Lucía Gómez', 'pi-book', '#dc2626', TRUE),
    ('66666666-6666-6666-6666-666666666666',
     'Seguridad Eléctrica NFPA 70E',
     'Protocolos de seguridad para trabajos eléctricos en tensión, EPP y procedimiento LOTO.',
     'INTERMEDIO', 5, 12, 480, 4.5, 'Ing. Carlos Herrera', 'pi-shield', '#8b5cf6', TRUE);
