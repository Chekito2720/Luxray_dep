-- V4: Seed de lecciones para los 6 cursos existentes.
-- Cada curso tiene 4 secciones (módulos) con 1-2 lecciones cada uno.

-- ════════ Fundamentos de Electricidad (BASICO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's1', 'Introducción',            'Bienvenida al curso',                       '5:00',  'LECTURA', NULL,                              'Panorama general de la electricidad y objetivos del curso.', 1),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's1', 'Introducción',            'Historia de la electricidad',                '12:00', 'VIDEO',   'https://www.youtube.com/watch?v=McTgMTw1HBk', 'De Tales de Mileto a Edison y Tesla.', 2),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's2', 'Conceptos fundamentales', 'Carga, corriente y voltaje',                '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=8jB5T_hHwbE', 'Definiciones clave y diferencias prácticas.', 3),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's2', 'Conceptos fundamentales', 'Quiz: conceptos básicos',                   '5:00',  'QUIZ',    NULL,                              'Repaso de carga, corriente y voltaje.', 4),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's3', 'Ley de Ohm',              'La ley de Ohm explicada',                    '15:00', 'VIDEO',   'https://www.youtube.com/watch?v=HsLLq6Rm5tU', 'V = I × R, con ejemplos prácticos.', 5),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's3', 'Ley de Ohm',              'Cálculo de resistencias',                    '10:00', 'LECTURA', NULL,                              'Ejercicios resueltos paso a paso.', 6),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's4', 'Seguridad básica',        'Equipo de protección personal (EPP)',        '8:00',  'VIDEO',   'https://www.youtube.com/watch?v=WJ2vXOzgqVE', 'Elementos indispensables para electricistas.', 7),
  (gen_random_uuid(), '34d4d37b-5f4d-4507-8b02-753a7a0a0590', 's4', 'Seguridad básica',        'Quiz final del módulo',                      '10:00', 'QUIZ',    NULL,                              'Evaluación integral de los temas vistos.', 8);

-- ════════ Instalaciones Residenciales (INTERMEDIO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's1', 'Planeación',          'Lectura de planos eléctricos',              '20:00', 'VIDEO',   'https://www.youtube.com/watch?v=IUVr1fW6Zvk', 'Simbología y convenciones.', 1),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's1', 'Planeación',          'Cálculo de cargas',                         '15:00', 'LECTURA', NULL,                              'Demanda máxima y factores de uso.', 2),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's2', 'Cableado',            'Tipos de cables y calibres',                '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=L9cXz2qQGcw', 'THHN, Romex y más.', 3),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's2', 'Cableado',            'Técnicas de canalización',                  '12:00', 'VIDEO',   'https://www.youtube.com/watch?v=Yz8bZQvJ3WM', 'Tubos, ductos y charolas.', 4),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's3', 'Protecciones',        'Interruptores termomagnéticos',             '14:00', 'VIDEO',   'https://www.youtube.com/watch?v=2eP6J0e1h6g', 'Selección y curva de disparo.', 5),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's3', 'Protecciones',        'Sistemas de puesta a tierra',               '16:00', 'VIDEO',   'https://www.youtube.com/watch?v=ZxYyJxY9Wpg', 'Tierras físicas y de equipo.', 6),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's4', 'Pruebas',             'Pruebas de aislamiento y continuidad',      '10:00', 'LECTURA', NULL,                              'Procedimiento y valores esperados.', 7),
  (gen_random_uuid(), 'b5265365-9cf8-4963-820a-160f4205eacd', 's4', 'Pruebas',             'Quiz: instalación completa',                '15:00', 'QUIZ',    NULL,                              'Caso integrador.', 8);

-- ════════ Energía Solar Fotovoltaica (INTERMEDIO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's1', 'Fundamentos solares', 'Radiación y geometría solar',          '16:00', 'VIDEO',   'https://www.youtube.com/watch?v=2b4kF5mZwqY', 'Cómo se posicionan los paneles.', 1),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's1', 'Fundamentos solares', 'Tipos de paneles fotovoltaicos',     '14:00', 'VIDEO',   'https://www.youtube.com/watch?v=ZPkkXvyHfSE', 'Mono, poli y thin-film.', 2),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's2', 'Componentes',         'Inversores y microinversores',         '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=QiXYC2i9Lnk', 'Comparativa técnica.', 3),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's2', 'Componentes',         'Estructuras de montaje',               '10:00', 'LECTURA', NULL,                              'Techo, suelo y seguidores.', 4),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's3', 'Diseño',              'Cálculo de un sistema residencial',    '20:00', 'VIDEO',   'https://www.youtube.com/watch?v=MtMW0nL3eJ4', 'Ejemplo paso a paso.', 5),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's3', 'Diseño',              'Conexión a la red (net metering)',     '12:00', 'VIDEO',   'https://www.youtube.com/watch?v=8s9vjJ0Hd2g', 'Trámites y normativa.', 6),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's4', 'Puesta en marcha',    'Pruebas y mantenimiento',              '14:00', 'LECTURA', NULL,                              'Plan de mantenimiento preventivo.', 7),
  (gen_random_uuid(), '75140c96-1070-497b-b188-ce8eecbabd17', 's4', 'Puesta en marcha',    'Quiz final',                           '10:00', 'QUIZ',    NULL,                              'Evaluación completa.', 8);

-- ════════ Sistemas Industriales (AVANZADO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's1', 'Componentes',     'Motores trifásicos',                       '22:00', 'VIDEO',   'https://www.youtube.com/watch?v=Z3qQ3z5cGqI', 'Principio de operación y conexiones.', 1),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's1', 'Componentes',     'Variadores de frecuencia (VFD)',            '20:00', 'VIDEO',   'https://www.youtube.com/watch?v=Y6rLp2lF8Gk', 'Configuración y protección.', 2),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's2', 'Distribución',    'Subestaciones y transformadores',          '24:00', 'VIDEO',   'https://www.youtube.com/watch?v=Ly2pRJv4vVc', 'Selección y conexión.', 3),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's2', 'Distribución',    'Cálculo de corrientes de cortocircuito',    '18:00', 'LECTURA', NULL,                              'Método de impedancias y por unidad.', 4),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's3', 'Control',         'PLC y arranque de motores',                 '20:00', 'VIDEO',   'https://www.youtube.com/watch?v=fFLn1Nz3DmU', 'Lógica de control industrial.', 5),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's3', 'Control',         'Instrumentación industrial',                '16:00', 'VIDEO',   'https://www.youtube.com/watch?v=gTr5Gh4L8pY', 'Sensores y transmisores.', 6),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's4', 'Seguridad',       'Arc flash y PPE categoría',                 '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=QxvYrL8p5Vc', 'Riesgos y protección.', 7),
  (gen_random_uuid(), '78a8d5a5-40e2-4188-8500-5ba7607a166d', 's4', 'Seguridad',       'Quiz integral',                             '12:00', 'QUIZ',    NULL,                              'Evaluación del módulo.', 8);

-- ════════ Normativa NOM-001-SEDE (AVANZADO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's1', 'Introducción',     '¿Qué es la NOM-001-SEDE?',           '12:00', 'LECTURA', NULL,                              'Marco regulatorio y alcance.', 1),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's1', 'Introducción',     'Actualizaciones 2012 vs 2025',        '15:00', 'VIDEO',   'https://www.youtube.com/watch?v=ZB8bL1pQwXk', 'Cambios principales.', 2),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's2', 'Articulado',       'Artículos 100-200: definiciones',     '20:00', 'VIDEO',   'https://www.youtube.com/watch?v=Qh5Yq3xLJlc', 'Términos fundamentales.', 3),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's2', 'Articulado',       'Artículos 210: circuitos derivados', '22:00', 'VIDEO',   'https://www.youtube.com/watch?v=NxK8pq2vL5c', 'Dimensionamiento.', 4),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's3', 'Aplicación',       'Casos prácticos residenciales',      '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=Yz9bZq3xWlc', 'Aplicación en vivienda.', 5),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's3', 'Aplicación',       'Casos prácticos comerciales',        '18:00', 'VIDEO',   'https://www.youtube.com/watch?v=Zx9bLq3xYp4', 'Aplicación en comercios.', 6),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's4', 'Certificación',    'Proceso de cumplimiento verificable', '14:00', 'LECTURA', NULL,                              'Memorial descriptivo y dictamen.', 7),
  (gen_random_uuid(), '64045d61-4e71-42bd-96aa-d18dd6f0fd41', 's4', 'Certificación',    'Quiz final normativo',                '12:00', 'QUIZ',    NULL,                              'Repaso integral.', 8);

-- ════════ Seguridad Eléctrica NFPA 70E (INTERMEDIO) ════════
INSERT INTO lecciones (id, curso_id, seccion_id, seccion_titulo, titulo, duracion, tipo, video_url, descripcion, orden) VALUES
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's1', 'Introducción',  '¿Por qué NFPA 70E?',                          '10:00', 'LECTURA', NULL,                              'Estadísticas de accidentes eléctricos.', 1),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's1', 'Introducción',  'Jerarquía de controles',                      '12:00', 'VIDEO',   'https://www.youtube.com/watch?v=Qh5Yq3xLJlc', 'Eliminar, sustituir, controlar.', 2),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's2', 'Arc flash',     'Concepto de arc flash',                       '16:00', 'VIDEO',   'https://www.youtube.com/watch?v=QxvYrL8p5Vc', 'Energía incidente y límites.', 3),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's2', 'Arc flash',     'Cálculo de energía incidente',                '18:00', 'LECTURA', NULL,                              'Métodos simplificados.', 4),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's3', 'EPP',           'Categorías de PPE 1 a 4',                     '14:00', 'VIDEO',   'https://www.youtube.com/watch?v=Qh5Yq3xLJlc', 'Selección por nivel de riesgo.', 5),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's3', 'EPP',           'Procedimiento de bloqueo y etiquetado (LOTO)', '15:00', 'VIDEO',   'https://www.youtube.com/watch?v=NxK8pq2vL5c', 'Pasos formales.', 6),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's4', 'Procedimiento', 'Permiso de trabajo energizado',               '12:00', 'LECTURA', NULL,                              'Cuándo es válido.', 7),
  (gen_random_uuid(), 'aa8e0603-6a1e-4417-aaca-289dc553adbf', 's4', 'Procedimiento', 'Quiz final NFPA 70E',                         '10:00', 'QUIZ',    NULL,                              'Repaso general.', 8);
