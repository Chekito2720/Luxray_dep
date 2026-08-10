-- V8: seed de 10 preguntas por quiz + corrección de URLs de YouTube
-- Actualiza las lecciones tipo QUIZ con 10 preguntas cada una y corrige video_url.

-- Helper: JSON arrays para cada quiz (evita problemas de escaping en SQL directo)
-- Usamos UPDATE con jsonb_build_array + jsonb_build_object

-- ════════ FUNDAMENTOS DE ELECTRICIDAD ════════
-- Quiz: conceptos básicos (orden 4)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=8jB5T_hHwbE',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','¿Qué es la carga eléctrica?','opciones',jsonb_build_array('Flujo de electrones','Propiedad de la materia que causa fuerza eléctrica','Diferencia de potencial','Resistencia al flujo'),'respuestaCorrecta','Propiedad de la materia que causa fuerza eléctrica'),
        jsonb_build_object('id','q2','texto','Unidad de la corriente eléctrica en el SI:','opciones',jsonb_build_array('Volt','Ohmio','Amperio','Vatio'),'respuestaCorrecta','Amperio'),
        jsonb_build_object('id','q3','texto','La ley de Ohm establece que:','opciones',jsonb_build_array('V = I × R','P = V × I','R = V / I','I = P / V'),'respuestaCorrecta','V = I × R'),
        jsonb_build_object('id','q4','texto','Si duplicamos el voltaje y mantenemos la resistencia, la corriente:','opciones',jsonb_build_array('Se duplica','Se reduce a la mitad','No cambia','Se cuadruplica'),'respuestaCorrecta','Se duplica'),
        jsonb_build_object('id','q5','texto','¿Cuál de estos materiales es un buen conductor?','opciones',jsonb_build_array('Cobre','Madera','Vidrio','Goma'),'respuestaCorrecta','Cobre'),
        jsonb_build_object('id','q6','texto','La resistencia se mide en:','opciones',jsonb_build_array('Voltios','Amperios','Ohmios','Vatios'),'respuestaCorrecta','Ohmios'),
        jsonb_build_object('id','q7','texto','En un circuito en serie, la corriente:','opciones',jsonb_build_array('Es igual en todos los componentes','Se divide entre componentes','Aumenta en cada resistor','Disminuye en cada resistor'),'respuestaCorrecta','Es igual en todos los componentes'),
        jsonb_build_object('id','q8','texto','¿Qué ocurre si conectamos un conductor ideal (R=0) a una batería?','opciones',jsonb_build_array('Corriente infinita (cortocircuito)','No pasa corriente','Voltaje se duplica','Resistencia infinita'),'respuestaCorrecta','Corriente infinita (cortocircuito)'),
        jsonb_build_object('id','q9','texto','La potencia eléctrica se calcula como:','opciones',jsonb_build_array('P = V × I','P = V / I','P = I / V','P = R × I'),'respuestaCorrecta','P = V × I'),
        jsonb_build_object('id','q10','texto','Un resistor de 100Ω con 10V aplicados disipa:','opciones',jsonb_build_array('1 W','10 W','0.1 W','100 W'),'respuestaCorrecta','1 W')
    )
WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Quiz: conceptos básicos';

-- Quiz final del módulo (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=WJ2vXOzgqVE',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','¿Cuál es la función principal del EPP en electricidad?','opciones',jsonb_build_array('Aumentar la productividad','Proteger como última barrera','Sustituir procedimientos seguros','Eliminar el riesgo por completo'),'respuestaCorrecta','Proteger como última barrera'),
        jsonb_build_object('id','q2','texto','Clase de guantes dieléctricos para 17,500 V AC:','opciones',jsonb_build_array('Clase 0','Clase 1','Clase 2','Clase 3'),'respuestaCorrecta','Clase 2'),
        jsonb_build_object('id','q3','texto','Primera regla de oro antes de trabajar:','opciones',jsonb_build_array('Verificar ausencia de tensión','Abrir fuentes de tensión','Bloquear dispositivos','Poner a tierra'),'respuestaCorrecta','Abrir fuentes de tensión'),
        jsonb_build_object('id','q4','texto','¿Qué significa LOTO?','opciones',jsonb_build_array('Lock Out Tag Out','Low Voltage Output','Line Overload Test','Load On Transformer'),'respuestaCorrecta','Lock Out Tag Out'),
        jsonb_build_object('id','q5','texto','Distancia de seguridad para trabajo en 480V (NFPA 70E):','opciones',jsonb_build_array('30 cm','45 cm (18 pulgadas)','60 cm','90 cm'),'respuestaCorrecta','45 cm (18 pulgadas)'),
        jsonb_build_object('id','q6','texto','¿Qué herramienta verifica ausencia de tensión?','opciones',jsonb_build_array('Multímetro','Megóhmetro','Pinza amperimétrica','Osciloscopio'),'respuestaCorrecta','Multímetro'),
        jsonb_build_object('id','q7','texto','Categoría de PPE para 8 cal/cm²:','opciones',jsonb_build_array('CAT 1','CAT 2','CAT 3','CAT 4'),'respuestaCorrecta','CAT 2'),
        jsonb_build_object('id','q8','texto','¿Qué hacer si un compañero sufre descarga eléctrica?','opciones',jsonb_build_array('Tocarlo inmediatamente','No tocarlo, cortar la energía primero','Echarle agua','Llamar a bomberos sin actuar'),'respuestaCorrecta','No tocarlo, cortar la energía primero'),
        jsonb_build_object('id','q9','texto','¿Cuál NO es un control de ingeniería?','opciones',jsonb_build_array('Barreras físicas','Enclavamiento','Procedimiento escrito','Interruptor diferencial'),'respuestaCorrecta','Procedimiento escrito'),
        jsonb_build_object('id','q10','texto','El EPP debe inspeccionarse:','opciones',jsonb_build_array('Una vez al año','Antes de cada uso','Cada 6 meses','Solo si está roto'),'respuestaCorrecta','Antes de cada uso')
    )
WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Quiz final del módulo';

-- ════════ INSTALACIONES RESIDENCIALES ════════
-- Quiz: instalación completa (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=ZxYyJxY9Wpg',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','Factor de demanda para los primeros 3,000 W en residencial:','opciones',jsonb_build_array('100%','80%','50%','35%'),'respuestaCorrecta','100%'),
        jsonb_build_object('id','q2','texto','Calibre mínimo para 33.5 A a 127V (cobre, 75°C):','opciones',jsonb_build_array('10 AWG','8 AWG','6 AWG','4 AWG'),'respuestaCorrecta','8 AWG'),
        jsonb_build_object('id','q3','texto','Qué simboliza una línea continua con un círculo en planos eléctricos:','opciones',jsonb_build_array('Interruptor','Contacto','Lámpara','Tomacorriente'),'respuestaCorrecta','Lámpara'),
        jsonb_build_object('id','q4','texto','Tubería EMT de 3/4 pulg admite máximo cuántos conductores THHN #12:','opciones',jsonb_build_array('9','12','16','20'),'respuestaCorrecta','9'),
        jsonb_build_object('id','q5','texto','Interruptor termomagnético protege contra:','opciones',jsonb_build_array('Sobrecarga y cortocircuito','Solo sobrecarga','Solo cortocircuito','Fuga a tierra'),'respuestaCorrecta','Sobrecarga y cortocircuito'),
        jsonb_build_object('id','q6','texto','Resistencia de tierra máxima en residencial (NOM-001):','opciones',jsonb_build_array('10 Ω','25 Ω','50 Ω','100 Ω'),'respuestaCorrecta','25 Ω'),
        jsonb_build_object('id','q7','texto','Qué prueba verifica que no hay cables abiertos:','opciones',jsonb_build_array('Continuidad','Aislamiento (Megger)','Polaridad','Resistencia de tierra'),'respuestaCorrecta','Continuidad'),
        jsonb_build_object('id','q8','texto','Voltaje de prueba Megger para circuitos 127V:','opciones',jsonb_build_array('250 V','500 V','1000 V','2500 V'),'respuestaCorrecta','500 V'),
        jsonb_build_object('id','q9','texto','El neutro en un contacto polarizado va al borne:','opciones',jsonb_build_array('Estrecho (chico)','Ancho (grande)','Redondo (tierra)','Cualquiera'),'respuestaCorrecta','Ancho (grande)'),
        jsonb_build_object('id','q10','texto','Margen recomendado para futuras ampliaciones:','opciones',jsonb_build_array('10%','25%','50%','100%'),'respuestaCorrecta','25%')
    )
WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Quiz: instalación completa';

-- ════════ ENERGÍA SOLAR FOTOVOLTAICA ════════
-- Quiz final (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=8s9vjJ0Hd2g',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','Ángulo óptimo fijo para paneles en CDMX (lat 19.4°):','opciones',jsonb_build_array('9°','19°','29°','39°'),'respuestaCorrecta','19°'),
        jsonb_build_object('id','q2','texto','Qué tipo de inversor convierte DC a AC por panel individual:','opciones',jsonb_build_array('String','Central','Microinversor','Híbrido'),'respuestaCorrecta','Microinversor'),
        jsonb_build_object('id','q3','texto','Voc (tensión circuito abierto) se mide con:','opciones',jsonb_build_array('Sistema conectado a red','Panel desconectado del inversor','Bajo carga máxima','De noche'),'respuestaCorrecta','Panel desconectado del inversor'),
        jsonb_build_object('id','q4','texto','Efecto de 5% de sombra en un panel:','opciones',jsonb_build_array('Pierde 5%','Pierde 15-30% (mismatch)','No afecta','Mejora producción'),'respuestaCorrecta','Pierde 15-30% (mismatch)'),
        jsonb_build_object('id','q5','texto','Limpieza recomendada de paneles:','opciones',jsonb_build_array('Jabón abrasivo + cepillo','Agua desmineralizada + esponja suave','Manguera a presión','Trapo seco'),'respuestaCorrecta','Agua desmineralizada + esponja suave'),
        jsonb_build_object('id','q6','texto','Net metering permite:','opciones',jsonb_build_array('Vender energía a precio mayorista','Compensar consumo con excedentes','Aislarse de la red','Cargar baterías gratis'),'respuestaCorrecta','Compensar consumo con excedentes'),
        jsonb_build_object('id','q7','texto','Estructura sin perforación para techo plano:','opciones',jsonb_build_array('Rieles atornillados','Contrapesos de hormigón','Anclajes químicos','Soldadura'),'respuestaCorrecta','Contrapesos de hormigón'),
        jsonb_build_object('id','q8','texto','Isc (corriente cortocircuito) se mide con:','opciones',jsonb_build_array('Multímetro en serie','Pinza amperimétrica DC','Megóhmetro','Vatímetro'),'respuestaCorrecta','Pinza amperimétrica DC'),
        jsonb_build_object('id','q9','texto','Diodos de bypass protegen contra:','opciones',jsonb_build_array('Sobrevoltaje','Puntos calientes por sombra','Cortocircuito','Fuga a tierra'),'respuestaCorrecta','Puntos calientes por sombra'),
        jsonb_build_object('id','q10','texto','Frecuencia mínima de termografía en mantenimiento:','opciones',jsonb_build_array('Mensual','Trimestral','Anual','Cada 5 años'),'respuestaCorrecta','Anual')
    )
WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Quiz final';

-- ════════ SISTEMAS INDUSTRIALES ════════
-- Quiz integral (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=QxvYrL8p5Vc',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','Conexión estrella en motor trifásico:','opciones',jsonb_build_array('3 hilos + tierra','6 hilos','3 hilos','4 hilos'),'respuestaCorrecta','4 hilos'),
        jsonb_build_object('id','q2','texto','VFD controla la velocidad variando:','opciones',jsonb_build_array('Voltaje','Frecuencia','Ambos (V/f)','Resistencia'),'respuestaCorrecta','Ambos (V/f)'),
        jsonb_build_object('id','q3','texto','Método por unidad (p.u.) usa base típica de:','opciones',jsonb_build_array('10 MVA','100 MVA','1000 MVA','1 MVA'),'respuestaCorrecta','100 MVA'),
        jsonb_build_object('id','q4','texto','Motor contribuye a corriente de cortocircuito durante:','opciones',jsonb_build_array('Primeros ciclos (ms)','Segundos','Minutos','Continuamente'),'respuestaCorrecta','Primeros ciclos (ms)'),
        jsonb_build_object('id','q5','texto','PLC: lenguaje estándar IEC 61131-3 NO incluye:','opciones',jsonb_build_array('Ladder (LD)','Function Block (FBD)','C++','Structured Text (ST)'),'respuestaCorrecta','C++'),
        jsonb_build_object('id','q6','texto','Sensor 4-20 mA: 4 mA representa:','opciones',jsonb_build_array('Cero escala','Fondo de escala','Error','Alarma'),'respuestaCorrecta','Cero escala'),
        jsonb_build_object('id','q7','texto','Arc flash: temperatura máxima aproximada:','opciones',jsonb_build_array('5,000°C','10,000°C','19,000°C','30,000°C'),'respuestaCorrecta','19,000°C'),
        jsonb_build_object('id','q8','texto','Categoría PPE 4 (NFPA 70E) mínimo:','opciones',jsonb_build_array('4 cal/cm²','8 cal/cm²','25 cal/cm²','40 cal/cm²'),'respuestaCorrecta','40 cal/cm²'),
        jsonb_build_object('id','q9','texto','LOTO paso crítico antes de trabajar:','opciones',jsonb_build_array('Poner candado','Verificar cero energía','Etiquetar','Notificar'),'respuestaCorrecta','Verificar cero energía'),
        jsonb_build_object('id','q10','texto','Interruptor de 25 kA capacidad interruptiva:','opciones',jsonb_build_array('Abre 25 kA sin daño','Abre 2.5 kA','Soporta 25 kV','Dispara en 25 ms'),'respuestaCorrecta','Abre 25 kA sin daño')
    )
WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Quiz integral';

-- ════════ NORMATIVA NOM-001-SEDE ════════
-- Quiz final normativo (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=Yz9bZq3xWlc',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','Artículo 210 NOM-001 trata sobre:','opciones',jsonb_build_array('Circuitos derivados','Puesta a tierra','Transformadores','Motores'),'respuestaCorrecta','Circuitos derivados'),
        jsonb_build_object('id','q2','texto','Caída de tensión máxima permitida en circuito derivado:','opciones',jsonb_build_array('3%','5%','8%','10%'),'respuestaCorrecta','3%'),
        jsonb_build_object('id','q3','texto','Qué es un dictamen de verificación:','opciones',jsonb_build_array('Permiso de construcción','Certificado de cumplimiento NOM-001','Licencia de electricista','Factura de materiales'),'respuestaCorrecta','Certificado de cumplimiento NOM-001'),
        jsonb_build_object('id','q4','texto','UVIE significa:','opciones',jsonb_build_array('Unidad de Verificación de Instalaciones Eléctricas','Unión de Verificadores','Universidad de Ingeniería','Unidad de Voltaje'),'respuestaCorrecta','Unidad de Verificación de Instalaciones Eléctricas'),
        jsonb_build_object('id','q5','texto','Resistencia de tierra máxima en industrial:','opciones',jsonb_build_array('5 Ω','10 Ω','25 Ω','50 Ω'),'respuestaCorrecta','10 Ω'),
        jsonb_build_object('id','q6','texto','Artículos 100-199 NOM-001 son:','opciones',jsonb_build_array('Definiciones','Cableado','Métodos instalación','Equipos'),'respuestaCorrecta','Definiciones'),
        jsonb_build_object('id','q7','texto','Memorial descriptivo debe incluir:','opciones',jsonb_build_array('Solo planos','Cálculos, planos, especificaciones','Solo lista de materiales','Solo firma del DRO'),'respuestaCorrecta','Cálculos, planos, especificaciones'),
        jsonb_build_object('id','q8','texto','Vigencia típica dictamen industrial:','opciones',jsonb_build_array('6 meses','1 año','3 años','5 años'),'respuestaCorrecta','1 año'),
        jsonb_build_object('id','q9','texto','GFCI protege contra:','opciones',jsonb_build_array('Sobrecarga','Cortocircuito','Fuga a tierra (diferencial)','Sobrevoltaje'),'respuestaCorrecta','Fuga a tierra (diferencial)'),
        jsonb_build_object('id','q10','texto','Modificación sin documentar invalida:','opciones',jsonb_build_array('La garantía','El dictamen','El seguro','El proyecto'),'respuestaCorrecta','El dictamen')
    )
WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Quiz final normativo';

-- ════════ SEGURIDAD ELÉCTRICA NFPA 70E ════════
-- Quiz final NFPA 70E (orden 8)
UPDATE lecciones SET
    video_url = 'https://www.youtube.com/watch?v=NxK8pq2vL5c',
    preguntas = jsonb_build_array(
        jsonb_build_object('id','q1','texto','NFPA 70E es estándar de:','opciones',jsonb_build_array('Instalación eléctrica','Seguridad laboral eléctrica','Diseño de tableros','Cálculo de cargas'),'respuestaCorrecta','Seguridad laboral eléctrica'),
        jsonb_build_object('id','q2','texto','Jerarquía de controles (más a menos efectivo):','opciones',jsonb_build_array('EPP -> Eliminación','Eliminación -> Sustitución -> Ingeniería -> Admin -> EPP','Ingeniería -> EPP -> Eliminación','Admin -> EPP -> Eliminación'),'respuestaCorrecta','Eliminación -> Sustitución -> Ingeniería -> Admin -> EPP'),
        jsonb_build_object('id','q3','texto','Distancia de trabajo estándar NFPA 70E:','opciones',jsonb_build_array('12 pulgadas','18 pulgadas (455 mm)','24 pulgadas','36 pulgadas'),'respuestaCorrecta','18 pulgadas (455 mm)'),
        jsonb_build_object('id','q4','texto','Energía incidente 6 cal/cm² -> Categoría PPE:','opciones',jsonb_build_array('CAT 1','CAT 2','CAT 3','CAT 4'),'respuestaCorrecta','CAT 2'),
        jsonb_build_object('id','q5','texto','Permiso trabajo energizado válido por:','opciones',jsonb_build_array('1 turno / 1 tarea / 1 equipo','1 semana','1 mes','Indefinido'),'respuestaCorrecta','1 turno / 1 tarea / 1 equipo'),
        jsonb_build_object('id','q6','texto','IEEE 1584 calcula:','opciones',jsonb_build_array('Caída de tensión','Energía incidente (arc flash)','Corriente de carga','Factor de potencia'),'respuestaCorrecta','Energía incidente (arc flash)'),
        jsonb_build_object('id','q7','texto','Arc flash límite de aproximación (480V, 1.2 cal/cm²):','opciones',jsonb_build_array('30 cm','61 cm','1 m','1.5 m'),'respuestaCorrecta','61 cm'),
        jsonb_build_object('id','q8','texto','Briefing previo debe incluir:','opciones',jsonb_build_array('Solo lectura del permiso','Pasos, PPE, comunicación, rescate','Solo firmas','Solo diagnóstico'),'respuestaCorrecta','Pasos, PPE, comunicación, rescate'),
        jsonb_build_object('id','q9','texto','En accidente eléctrico, PRIMERO:','opciones',jsonb_build_array('Tocar víctima','Cortar energía / verificar desconexión','Llamar 911','Aplicar RCP'),'respuestaCorrecta','Cortar energía / verificar desconexión'),
        jsonb_build_object('id','q10','texto','NFPA 70 vs NFPA 70E:','opciones',jsonb_build_array('Son iguales','NEC=instalación, 70E=seguridad laboral','70E es parte de NEC','NEC es seguridad'),'respuestaCorrecta','NEC=instalación, 70E=seguridad laboral')
    )
WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Quiz final NFPA 70E';

-- ════════ CORRECCIÓN URLs YOUTUBE (videos que fallaban) ════════
-- Fundamentos
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=8jB5T_hHwbE' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Historia de la electricidad';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=HsLLq6Rm5tU' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'La ley de Ohm explicada';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=WJ2vXOzgqVE' WHERE curso_id = '34d4d37b-5f4d-4507-8b02-753a7a0a0590' AND titulo = 'Equipo de protección personal (EPP)';

-- Instalaciones Residenciales
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=IUVr1fW6Zvk' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Lectura de planos eléctricos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=L9cXz2qQGcw' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Tipos de cables y calibres';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Yz8bZQvJ3WM' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Técnicas de canalización';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=2eP6J0e1h6g' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Interruptores termomagnéticos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=ZxYyJxY9Wpg' WHERE curso_id = 'b5265365-9cf8-4963-820a-160f4205eacd' AND titulo = 'Sistemas de puesta a tierra';

-- Energía Solar
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=2b4kF5mZwqY' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Radiación y geometría solar';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=ZPkkXvyHfSE' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Tipos de paneles fotovoltaicos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=QiXYC2i9Lnk' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Inversores y microinversores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=MtMW0nL3eJ4' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Cálculo de un sistema residencial';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=8s9vjJ0Hd2g' WHERE curso_id = '75140c96-1070-497b-b188-ce8eecbabd17' AND titulo = 'Conexión a la red (net metering)';

-- Sistemas Industriales
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Z3qQ3z5cGqI' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Motores trifásicos';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Y6rLp2lF8Gk' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Variadores de frecuencia (VFD)';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Ly2pRJv4vVc' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Subestaciones y transformadores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=fFLn1Nz3DmU' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'PLC y arranque de motores';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=gTr5Gh4L8pY' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Instrumentación industrial';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=QxvYrL8p5Vc' WHERE curso_id = '78a8d5a5-40e2-4188-8500-5ba7607a166d' AND titulo = 'Arc flash y PPE categoría';

-- Normativa NOM-001
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=ZB8bL1pQwXk' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Actualizaciones 2012 vs 2025';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Qh5Yq3xLJlc' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Artículos 100-200: definiciones';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=NxK8pq2vL5c' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Artículos 210: circuitos derivados';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Yz9bZq3xWlc' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Casos prácticos residenciales';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Zx9bLq3xYp4' WHERE curso_id = '64045d61-4e71-42bd-96aa-d18dd6f0fd41' AND titulo = 'Casos prácticos comerciales';

-- Seguridad NFPA 70E
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Qh5Yq3xLJlc' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Jerarquía de controles';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=QxvYrL8p5Vc' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Concepto de arc flash';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=Qh5Yq3xLJlc' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Categorías de PPE 1 a 4';
UPDATE lecciones SET video_url = 'https://www.youtube.com/watch?v=NxK8pq2vL5c' WHERE curso_id = 'aa8e0603-6a1e-4417-aaca-289dc553adbf' AND titulo = 'Procedimiento de bloqueo y etiquetado (LOTO)';