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
    (gen_random_uuid(),
     'Fundamentos de Electricidad',
     'Aprende los conceptos esenciales: voltaje, corriente, resistencia y circuitos básicos. Sin requisitos previos.',
     'BASICO', 6, 24, 2400, 4.8, 'Ing. Roberto Mendoza', 'pi-bolt', '#16a34a', TRUE),
    (gen_random_uuid(),
     'Instalaciones Residenciales',
     'Instalación y mantenimiento de sistemas eléctricos en viviendas, normas y seguridad. Ideal para electricistas en formación.',
     'INTERMEDIO', 10, 38, 1850, 4.7, 'Ing. Claudia Ríos', 'pi-home', '#1565c0', TRUE),
    (gen_random_uuid(),
     'Sistemas Industriales',
     'Control de motores, tableros eléctricos y automatización industrial. Para profesionales en activo.',
     'AVANZADO', 15, 55, 980, 4.9, 'Ing. Alejandro Torres', 'pi-cog', '#37474f', TRUE),
    (gen_random_uuid(),
     'Energía Solar Fotovoltaica',
     'Diseño, instalación y mantenimiento de sistemas solares residenciales y comerciales. Certificación NABCEP.',
     'INTERMEDIO', 12, 32, 1100, 4.85, 'Ing. Patricia Gómez', 'pi-sun', '#f59e0b', TRUE),
    (gen_random_uuid(),
     'Normativa NOM-001-SEDE',
     'Interpretación práctica de la NOM-001-SEDE-2012 con casos reales y actualización 2024.',
     'AVANZADO', 8, 18, 720, 4.6, 'Mtra. Lucía Gómez', 'pi-book', '#dc2626', TRUE),
    (gen_random_uuid(),
     'Seguridad Eléctrica NFPA 70E',
     'Protocolos de seguridad para trabajos eléctricos en tensión, EPP y procedimiento LOTO.',
     'INTERMEDIO', 5, 12, 480, 4.5, 'Ing. Carlos Herrera', 'pi-shield', '#8b5cf6', TRUE);
