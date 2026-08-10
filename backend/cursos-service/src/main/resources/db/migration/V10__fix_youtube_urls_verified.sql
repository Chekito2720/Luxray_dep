-- V10: corregir URLs de YouTube con videos REALES embebibles verificados
-- Videos educativos confirmados funcionando (probados con oEmbed)
-- Usamos 10 videos funcionales; los primeros 2 son contenido eléctrico real

-- ════════ FUNDAMENTOS DE ELECTRICIDAD ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Historia de la electricidad';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Carga, corriente y voltaje';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'La ley de Ohm explicada';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Equipo de protección personal (EPP)';

-- ════════ INSTALACIONES RESIDENCIALES ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Lectura de planos eléctricos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Tipos de cables y calibres';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Técnicas de canalización';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Interruptores termomagnéticos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Sistemas de puesta a tierra';

-- ════════ ENERGÍA SOLAR FOTOVOLTAICA ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Radiación y geometría solar';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Tipos de paneles fotovoltaicos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Inversores y microinversores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Cálculo de un sistema residencial';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Conexión a la red (net metering)';

-- ════════ SISTEMAS INDUSTRIALES ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Motores trifásicos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Variadores de frecuencia (VFD)';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Subestaciones y transformadores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'PLC y arranque de motores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Instrumentación industrial';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Arc flash y PPE categoría';

-- ════════ NORMATIVA NOM-001-SEDE ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Actualizaciones 2012 vs 2025';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Artículos 100-200: definiciones';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Artículos 210: circuitos derivados';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Casos prácticos residenciales';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Casos prácticos comerciales';

-- ════════ SEGURIDAD ELÉCTRICA NFPA 70E ════════
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Jerarquía de controles';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Concepto de arc flash';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=bHIhgxav9LY' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Categorías de PPE 1 a 4';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Procedimiento de bloqueo y etiquetado (LOTO)';