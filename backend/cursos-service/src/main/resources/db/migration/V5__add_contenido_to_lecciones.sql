-- V5: agregar columna 'contenido' a lecciones para textos largos (markdown/HTML)
-- Solo las lecciones tipo LECTURA tendrán contenido extenso; las de VIDEO/QUIZ quedan NULL.

ALTER TABLE lecciones
    ADD COLUMN contenido TEXT;

-- Generador de UUID estable a partir del título para que la migración sea idempotente.
-- (Las inserciones originales en V4 usaron gen_random_uuid, así que aquí también.)

-- ════════ Fundamentos de Electricidad — lecturas ════════
UPDATE lecciones SET contenido = $content$# Cálculo de Resistencias

En electricidad, la **resistencia** es la oposición que un material presenta al flujo de corriente eléctrica. Se mide en **ohmios (Ω)** y se calcula con la **Ley de Ohm**:

$$V = I \times R$$

Donde:
- **V** = voltaje en volts (V)
- **I** = corriente en amperes (A)
- **R** = resistencia en ohmios (Ω)

## Cálculo básico

Para calcular la resistencia de un circuito, simplemente despejamos:

$$R = \frac{V}{I}$$

## Resistencias en serie

Cuando las resistencias se conectan en serie, la resistencia total es la **suma**:

$$R_{total} = R_1 + R_2 + R_3 + \dots + R_n$$

**Ejemplo:** Si tenemos R1=100Ω, R2=200Ω y R3=300Ω en serie:
$$R_{total} = 100 + 200 + 300 = 600\,\Omega$$

## Resistencias en paralelo

En paralelo, la resistencia total se calcula con la fórmula inversa:

$$\frac{1}{R_{total}} = \frac{1}{R_1} + \frac{1}{R_2} + \frac{1}{R_3} + \dots + \frac{1}{R_n}$$

O su equivalente simplificado para dos resistencias:

$$R_{total} = \frac{R_1 \times R_2}{R_1 + R_2}$$

## Código de colores

Las resistencias físicas usan un código de colores para identificar su valor:

| Color | Dígito | Multiplicador |
|-------|--------|---------------|
| Negro | 0 | ×1 |
| Marrón | 1 | ×10 |
| Rojo | 2 | ×100 |
| Naranja | 3 | ×1K |
| Amarillo | 4 | ×10K |
| Verde | 5 | ×100K |
| Azul | 6 | ×1M |
| Violeta | 7 | ×10M |
| Gris | 8 | ×100M |
| Blanco | 9 | ×1G |

## Aplicación práctica

En un circuito doméstico típico, una lámpara incandescente tiene una resistencia de aproximadamente **50–300 Ω** cuando está caliente. Esto limita la corriente que pasa por ella y evita que se queme el filamento.

> ⚠️ **Importante:** Antes de medir resistencias en un circuito, **desconecta la energía**. Medir con el circuito energizado puede dañar tu multímetro o darte lecturas erróneas.$content$
WHERE titulo = 'Cálculo de resistencias';

UPDATE lecciones SET contenido = $content$# EPP — Equipo de Protección Personal

El equipo de protección personal (EPP) es indispensable para cualquier electricista. Su uso correcto puede **salvar tu vida**.

## Elementos básicos

### 1. Casco dieléctrico
Protege la cabeza de impactos y, especialmente, del contacto accidental con conductores energizados. Debe cumplir con la norma **NOM-009-STPS**.

### 2. Gafas de seguridad
Protegen los ojos de:
- Arcos eléctricos
- Proyecciones de partículas
- Radiación UV en soldaduras

### 3. Guantes dieléctricos
Clasificados por **clase** según el voltaje máximo:

| Clase | Voltaje máximo | Color |
|-------|---------------|-------|
| 00 | 500 V AC | Beige |
| 0 | 1,000 V AC | Rojo |
| 1 | 7,500 V AC | Blanco |
| 2 | 17,000 V AC | Amarillo |
| 3 | 26,500 V AC | Verde |
| 4 | 36,000 V AC | Naranja |

> 🧤 **Inspección:** Antes de cada uso, infla los guantes y verifica que no haya fugas de aire.

### 4. Calzado dieléctrico
Botas con suela aislante que evitan el paso de corriente a tierra a través del cuerpo.

### 5. Ropa ignífuga
Indumentaria tratada para resistir la ignición y autoextinguirse. En categoría 2 o superior según **NFPA 70E**.

## Procedimiento de bloqueo y etiquetado (LOTO)

Antes de cualquier trabajo:

1. **Notifica** a los afectados que se interrumpirá el servicio.
2. **Aísla** la fuente de energía (interruptor principal).
3. **Bloquea** el interruptor en posición abierta con un candado personal.
4. **Etiqueta** con tu nombre y la fecha.
5. **Verifica** el cero energía con multímetro.
6. **Trabaja** con confianza.
7. **Retira** tus candados y etiquetas al terminar.

## Cinco reglas de oro (NOM-001-SEDE)

1. **Abre** todas las fuentes de tensión.
2. **Bloquea** los dispositivos de apertura para evitar reconexión accidental.
3. **Verifica** ausencia de tensión.
4. **Pon** a tierra y en cortocircuito los conductores.
5. **Delimita** y señaliza la zona de trabajo.

> ⚠️ **Recuerda:** Ningún EPP sustituye un procedimiento seguro. El EPP es tu **última línea de defensa**, no la primera.$content$
WHERE titulo = 'Equipo de protección personal (EPP)';

-- ════════ Instalaciones Residenciales — lecturas ════════
UPDATE lecciones SET contenido = $content$# Cálculo de Cargas en Instalaciones Residenciales

Calcular correctamente las **cargas eléctricas** de una vivienda es el primer paso para diseñar una instalación segura y eficiente.

## ¿Qué es una carga?

Es la **potencia** que consumen los aparatos eléctricos conectados. Se mide en **watts (W)** o **kilowatts (kW)**.

$$P = V \times I \times \cos(\varphi)$$

Para cargas resistivas (lámparas, calentadores), $\cos(\varphi) = 1$, así que simplemente:

$$P = V \times I$$

## Demanda máxima

No todos los aparatos funcionan al mismo tiempo ni a máxima potencia. Por eso la **NOM-001-SEDE** permite aplicar **factores de demanda**:

### Tabla de factores de demanda (residencial)

| Tipo de carga | Demanda |
|---------------|---------|
| Primeros 3,000 W | 100% |
| De 3,001 a 12,000 W | 35% |
| Más de 12,000 W | 25% |

## Ejemplo práctico

Una vivienda tiene los siguientes aparatos:

- 10 lámparas de 100 W = **1,000 W**
- 1 refrigerador = **400 W**
- 1 microondas = **1,200 W**
- 1 lavadora = **500 W**
- 1 aire acondicionado = **1,500 W**
- Varios enchufes (estimado) = **2,000 W**

**Total nominal:** 6,600 W

**Aplicando factores:**
- Primeros 3,000 W al 100% → 3,000 W
- Resto (3,600 W) al 35% → 1,260 W
- **Demanda máxima:** 4,260 W

## Cálculo del conductor

Con una demanda de 4,260 W a 127 V monofásico:

$$I = \frac{P}{V} = \frac{4{,}260}{127} = 33.5\,\text{A}$$

Esto requiere un conductor calibre **8 AWG** (capaz de manejar 40 A) con protección termomagnética de 40 A.

> 📐 **Tip:** Siempre redondea hacia arriba y deja un **margen del 25%** para futuras ampliaciones.$content$
WHERE titulo = 'Cálculo de cargas';

UPDATE lecciones SET contenido = $content$# Pruebas de Aislamiento y Continuidad

Una vez instalada la red eléctrica, es indispensable **verificar** que todo funcione correctamente y sea seguro.

## Prueba de continuidad

Verifica que el conductor esté **completo** sin interrupciones.

### Procedimiento

1. **Desconecta** la energía del circuito.
2. **Separa** ambos extremos del conductor a probar.
3. Configura el multímetro en modo **continuidad** (símbolo de diodo o zumbador).
4. Toca ambos extremos con las puntas.
5. **Lectura esperada:** 0–0.5 Ω con zumbido continuo.

> ❌ Si no suena el zumbido o la lectura es **OL** (circuito abierto), hay una interrupción.

## Prueba de aislamiento (Megger)

Mide la **resistencia de aislamiento** entre los conductores y tierra. Se usa un **megóhmetro** que aplica 500 V DC (para baja tensión).

### Valores mínimos según NOM-001-SEDE

| Tensión del circuito | Resistencia mínima |
|---------------------|-------------------|
| 127 V | 0.5 MΩ |
| 220 V | 0.5 MΩ |
| 480 V | 1.0 MΩ |

### Procedimiento

1. Desconecta la energía y **aísla** los aparatos conectados.
2. Conecta el megóhmetro entre:
   - **L1 y tierra**
   - **L2 y tierra** (si existe)
   - **Neutro y tierra**
   - **L1 y L2** (entre conductores)
3. Aplica voltaje durante **1 minuto**.
4. Lee el valor.

> ⚠️ **Peligro:** Nunca realices esta prueba con el circuito **energizado**.

## Prueba de polaridad

Verifica que:
- **Fase** llegue al contacto estrecho del contacto (chico).
- **Neutro** al ancho.
- **Tierra física** al borne de tierra.

### Cómo probarlo con multímetro

1. Energiza el circuito.
2. Mide voltaje entre **fase y tierra** → debe ser ~127 V.
3. Mide voltaje entre **neutro y tierra** → debe ser ~0 V.

> ⚡ **Si fase y neutro están invertidos**, el contacto queda energizado aunque el aparato esté "apagado", representando un riesgo grave de descarga.

## Reporte de pruebas

Documenta todas las mediciones en una bitácora que incluya:

- Fecha y responsable
- Identificación del circuito
- Valores obtenidos
- Observaciones
- Firmas

Esta bitácora es **requerida** por la NOM-001-SEDE para el dictamen de verificación.$content$
WHERE titulo = 'Pruebas de aislamiento y continuidad';

-- ════════ Energía Solar — lecturas ════════
UPDATE lecciones SET contenido = $content$# Estructuras de Montaje Fotovoltaico

La estructura de montaje es el esqueleto que sostiene los paneles solares. Su correcta selección es clave para **durabilidad** y **seguridad**.

## Tipos principales

### 1. Montaje en techo inclinado

- **Ventaja:** Integración arquitectónica, no ocupa espacio adicional.
- **Desventaja:** Requiere acceso al techo y orientación adecuada.
- **Material:** Aluminio anodizado o acero galvanizado.

### 2. Montaje en techo plano

- Estructuras inclinadas (~10–30°) con contrapesos de hormigón.
- Sin perforaciones: ideal para techos donde no se puede taladrar.

### 3. Montaje en suelo

- Mayor flexibilidad de orientación y ángulo.
- Requiere terreno disponible y cimentación.
- Ideal para sistemas de gran escala.

### 4. Seguidores solares

- Siguen la trayectoria del sol en uno o dos ejes.
- Aumentan la producción entre **15–40%**.
- Mayor costo inicial y mantenimiento.

## Criterios de selección

| Criterio | Consideración |
|----------|--------------|
| **Carga de viento** | Zona geográfica, altura, exposición |
| **Carga de nieve** | Regiones frías con acumulación |
| **Material** | Resistencia a corrosión (costera = acero inoxidable) |
| **Ángulo** | Latitud local ± 10° |
| **Orientación** | Sur verdadero (hemisferio norte) |

## Ángulo óptimo

La regla simplificada:

$$\text{Ángulo} = \text{Latitud local} \pm 10°$$

Para una instalación en CDMX (latitud 19.4°), el ángulo óptimo es de **9° a 29°** según la temporada.

## Fijaciones

- **Grapas intermedias:** sujetan entre dos paneles.
- **Grapas finales:** en los extremos de cada fila.
- **Rieles:** soportan las grapas y se anclan a la estructura.
- **Anclajes:** a techo, suelo o lastre.

> ⚠️ **Nunca** utilices estructuras sin certificación o con materiales que se corroan fácilmente. Una estructura mal instalada puede colapsar con el viento.$content$
WHERE titulo = 'Estructuras de montaje';

UPDATE lecciones SET contenido = $content$# Pruebas y Mantenimiento Fotovoltaico

Una vez instalado el sistema, las pruebas de puesta en marcha y el mantenimiento preventivo son **esenciales** para garantizar producción y durabilidad.

## Puesta en marcha

### 1. Inspección visual
- Verifica que **todos los paneles** estén firmemente sujetos.
- Comprueba que no haya **cables sueltos** o dañados.
- Revisa el estado de las **conexiones** y prensaestopas.

### 2. Pruebas eléctricas
Con el sistema desconectado del inversor:

- **Voc (tensión de circuito abierto)** de cada string → comparar con la ficha técnica (±5%).
- **Isc (corriente de cortocircuito)** → medir con multímetro de pinza DC.
- **Aislamiento** entre conductores y tierra (megóhmetro a 500–1000 V DC).

### 3. Primera conexión
1. Energiza el **inversor** siguiendo el manual.
2. Verifica que el display muestre **tensión de string** correcta.
3. Confirma que el sistema sincronice con la red (LED verde).
4. Registra la **potencia instantánea** de arranque.

## Mantenimiento preventivo

### Mensual
- Lectura del contador de producción.
- Verificación visual de funcionamiento.

### Trimestral
- Limpieza de paneles con agua y trapo suave.
- Inspección de cableado expuesto.

### Anual
- **Termografía** de paneles y conexiones (cámara IR).
- Par de apriete de tornillería.
- Revisión de **diodos de bypass**.
- Prueba de aislamiento completa.

## Limpieza de paneles

- **Frecuencia:** cada 3–6 meses según clima.
- **Método:** agua desmineralizada + esponja suave.
- **Evita:** productos abrasivos, caminar sobre los paneles.
- **Hora ideal:** temprano en la mañana o al atardecer (paneles fríos).

> ☀️ **Dato:** Un panel con **5% de sombra** puede perder hasta **30% de producción** por el efecto de mismatch.

## Bitácora

Mantén un registro detallado:

| Fecha | Actividad | Responsable | Observaciones |
|-------|-----------|-------------|---------------|
| 2025-01-15 | Limpieza | Juan Pérez | Sin novedades |
| 2025-04-20 | Termografía | Ana López | Panel #12 a 68°C (normal) |

Esta bitácora es invaluable para **reclamaciones de garantía** y diagnóstico de problemas futuros.$content$
WHERE titulo = 'Pruebas y mantenimiento';

-- ════════ Sistemas Industriales — lecturas ════════
UPDATE lecciones SET contenido = $content$# Cálculo de Corrientes de Cortocircuito

El cálculo de corrientes de cortocircuito es **fundamental** para la correcta selección de protecciones en sistemas industriales.

## ¿Por qué importa?

- Determina la **capacidad interruptiva** de los dispositivos de protección.
- Asegura que el equipo pueda **abrir** sin destruirse.
- Protege a las personas y los bienes.

## Método de impedancias

La corriente de cortocircuito trifásica se calcula como:

$$I_{cc} = \frac{V}{\sqrt{3} \times Z_{eq}}$$

Donde $Z_{eq}$ es la impedancia equivalente desde la fuente hasta el punto de falla.

### Componentes de la impedancia

1. **Red de alimentación** (transformador de la compañía).
2. **Transformador** de la subestación propia.
3. **Conductores** (cables).
4. **Barras** y conexiones.
5. **Motores** (contribuyen durante los primeros ciclos).

## Método por unidad (p.u.)

Es el método preferido para sistemas complejos. Se elige una base:

$$S_{base} = 100\,\text{MVA (típico)}$$
$$V_{base} = V_{nominal\ del\ sistema}$$

Las impedancias se expresan en por unidad sobre esta base:

$$Z_{pu} = Z_{\Omega} \times \frac{S_{base}}{V_{base}^2}$$

## Ejemplo simplificado

Un sistema en 480 V, con transformador de 1,000 kVA y Z=5.5%:

$$Z_{transformador} = 0.055\,\text{p.u.}$$

$$I_{base} = \frac{1{,}000{,}000}{\sqrt{3} \times 480} = 1{,}202\,\text{A}$$

$$I_{cc} = \frac{I_{base}}{Z_{pu}} = \frac{1{,}202}{0.055} = 21{,}855\,\text{A} \approx 22\,\text{kA}$$

## Selección de protecciones

El dispositivo de protección debe tener una **capacidad interruptiva** ≥ Icc en ese punto.

En nuestro ejemplo, los interruptores termomagnéticos deben ser de al menos **25 kA** (con margen del 15%).

> ⚠️ **Norma:** El cálculo debe seguir **IEC 60909** o **ANSI/IEEE C37.010** según la región.$content$
WHERE titulo = 'Cálculo de corrientes de cortocircuito';

-- ════════ Normativa NOM-001-SEDE — lecturas ════════
UPDATE lecciones SET contenido = $content$# ¿Qué es la NOM-001-SEDE?

La **Norma Oficial Mexicana NOM-001-SEDE** es la regulación que rige las **instalaciones eléctricas** en México. Su cumplimiento es **obligatorio** en todo el país.

## Objetivo

Establecer las **especificaciones** y **condiciones de seguridad** que deben cumplir las instalaciones eléctricas para:

- Proteger la **vida** de las personas.
- Prevenir **incendios** y accidentes.
- Garantizar la **continuidad** del servicio.
- Facilitar el **uso eficiente** de la energía.

## Campo de aplicación

Aplica a todas las instalaciones eléctricas:

- **Residenciales** (viviendas)
- **Comerciales** (tiendas, oficinas)
- **Industriales** (fábricas, plantas)
- **Institucionales** (hospitales, escuelas)
- **De servicios públicos** (alumbrado, bombeo)

## Vigencia

La edición vigente es la **NOM-001-SEDE-2012**, con modificaciones posteriores. Las nuevas ediciones se actualizan cada 5–10 años para incorporar avances tecnológicos.

## Estructura de la norma

La norma se organiza en **artículos numerados**, agrupados por temas:

- **Artículos 1–99:** Disposiciones generales.
- **Artículos 100–199:** Definiciones.
- **Artículos 200–299:** Cableado y métodos de alambrado.
- **Artículos 300–399:** Métodos de instalación.
- **Artículos 400–499:** Conductores y equipos.
- **Artículos 500–599:** Instalaciones especiales.

## Autoridad reguladora

La **Secretaría de Energía (SENER)** emite la norma. La **Comisión Nacional para el Uso Eficiente de la Energía (CONUEE)** y la **Unidad de Verificación** acreditada son las encargadas de verificar su cumplimiento.

## Sanciones por incumplimiento

El incumplimiento puede generar:

- Multas económicas.
- Clausura de la instalación.
- Responsabilidad civil y penal en caso de accidentes.
- Invalidación de pólizas de seguro.

## Unidades de Verificación

Las **UVIE** (Unidades de Verificación de Instalaciones Eléctricas) son las entidades privadas acreditadas para:

1. Inspeccionar la instalación.
2. Emitir un **dictamen de verificación**.
3. Verificar el cumplimiento normativo.

> 📜 **Dato histórico:** La primera NOM-001-SEDE se publicó en **1975** y ha tenido múltiples actualizaciones para adaptarse al crecimiento del sector eléctrico mexicano.$content$
WHERE titulo = '¿Qué es la NOM-001-SEDE?';

UPDATE lecciones SET contenido = $content$# Proceso de Cumplimiento Verificable

Para acreditar que una instalación eléctrica cumple con la NOM-001-SEDE, se sigue un proceso formal.

## Memorial descriptivo

Es el documento técnico que describe la instalación. Debe incluir:

### 1. Datos generales
- Ubicación exacta (dirección, coordenadas).
- Tipo de instalación (residencial, comercial, industrial).
- Fecha de diseño y construcción.
- Responsable del proyecto (DRO o perito).

### 2. Cálculos eléctricos
- **Cargas** por circuito y totales.
- **Caídas de tensión** (≤ 3% en cualquier circuito, ≤ 5% total).
- **Corrientes de cortocircuito**.
- **Coordinación de protecciones**.

### 3. Diagramas
- Unifilar general y por tablero.
- Isométrico de canalizaciones.
- Diagrama de conexiones a tierra.

### 4. Especificaciones
- Calibres y tipos de conductores.
- Capacidad de protecciones.
- Materiales utilizados.

## Pruebas a realizar

Antes de solicitar la verificación:

- [x] **Continuidad** de todos los conductores.
- [x] **Aislamiento** (megger).
- [x] **Resistencia de tierra** (≤ 25 Ω en residencial, ≤ 10 Ω en industrial).
- [x] **Polaridad** correcta.
- [x] **Funcionamiento** de protecciones diferenciales (GFCI).
- [x] **Balanceo de fases** (en trifásico).

## Bitácora de pruebas

Documenta cada medición con:

| Prueba | Valor obtenido | Cumple (Sí/No) |
|--------|----------------|----------------|
| Aislamiento L1-Tierra | 250 MΩ | ✓ |
| Continuidad Neutro | 0.2 Ω | ✓ |
| Resistencia de tierra | 8.5 Ω | ✓ |

## Dictamen de verificación

La **UVIE** emite un dictamen con:

- **Folio** de verificación.
- **Vigencia** (típicamente 1 año para industriales, 3 años para comerciales).
- **Observaciones** (si las hay).
- **Resultado:** APROBADO o NO APROBADO.

## Mantenimiento del cumplimiento

- Renovación del dictamen antes del vencimiento.
- Bitácora de mantenimiento al día.
- Actualización ante cualquier modificación.

> 📋 **Tip profesional:** Mantén tu memorial descriptivo **siempre actualizado**. Cualquier modificación sin documentar invalida el dictamen.$content$
WHERE titulo = 'Proceso de cumplimiento verificable';

-- ════════ Seguridad NFPA 70E — lecturas ════════
UPDATE lecciones SET contenido = $content$# ¿Por qué NFPA 70E?

La norma **NFPA 70E** (*Standard for Electrical Safety in the Workplace*) es el estándar de referencia en América del Norte para la **seguridad eléctrica laboral**.

## Origen

Publicada por la **National Fire Protection Association (NFPA)** desde 1979, ha evolucionado con cada edición para abordar nuevas tecnologías y métodos de trabajo.

## Objetivo

Reducir la exposición a **riesgos eléctricos** mediante:

- Procedimientos de trabajo seguros.
- Selección correcta de **EPP**.
- Análisis de **arc flash**.
- Entrenamiento del personal.

## Estadísticas clave

Datos de la OSHA (*Occupational Safety and Health Administration*) de EE. UU.:

- Promedio de **cuatro años** entre accidentes eléctricos graves.
- **Electrocución** es la 4ª causa de muerte en construcción.
- **Arc flash** puede generar temperaturas de hasta **19,000°C** (4× la superficie del sol).

> 📊 El cumplimiento de NFPA 70E reduce los accidentes eléctricos en un **60–70%**.

## Jerarquía de controles

NFPA 70E aplica la jerarquía de la NIOSH (de mayor a menor efectividad):

1. **Eliminación** — quitar el peligro (apagar el equipo).
2. **Sustitución** — usar voltajes más bajos.
3. **Controles de ingeniería** — barreras, enclavamientos.
4. **Controles administrativos** — procedimientos, permisos.
5. **EPP** — última línea de defensa.

> ⚠️ El **EPP nunca es la primera opción**. Es el último recurso cuando los demás controles fallan.

## Alcance de la norma

NFPA 70E aplica a:

- Trabajadores que operan equipos eléctricos.
- Personal de mantenimiento eléctrico.
- Cualquier persona expuesta a riesgos eléctricos en su trabajo.

## Diferencia con NEC (NFPA 70)

Es importante no confundir:

| NFPA 70 (NEC) | NFPA 70E |
|---------------|----------|
| Código de **instalación** | Estándar de **seguridad laboral** |
| Cómo **instalar** | Cómo **trabajar** de forma segura |
| Diseño y construcción | Operación y mantenimiento |

Ambas normas se **complementan**.

## Adopción internacional

Aunque es una norma estadounidense, la NFPA 70E se aplica en:

- México (referencia en muchas industrias).
- Canadá.
- Latinoamérica.
- Empresas multinacionales.

> 🌍 La NOM-001-SEDE y la NFPA 70E son **compatibles y complementarias**. La NOM aplica al diseño, la NFPA 70E al trabajo diario.$content$
WHERE titulo = '¿Por qué NFPA 70E?';

UPDATE lecciones SET contenido = $content$# Cálculo de Energía Incidente

El **cálculo de energía incidente** es el procedimiento para determinar la energía liberada en un arco eléctrico y, con ella, seleccionar el EPP adecuado.

## ¿Qué es la energía incidente?

Es la cantidad de **energía térmica** que recibe una superficie (generalmente la cara del trabajador) a una **distancia de trabajo** determinada. Se mide en **cal/cm²**.

## Método simplificado (tabla NFPA 70E)

Para tareas comunes, la NFPA 70E ofrece valores tabulados:

### Tabla 130.7(C)(15)(c) — Categorías de PPE típicas

| Tarea | 208 V | 240 V | 480 V |
|-------|-------|-------|-------|
| Lectura de panel | 0.06 cal/cm² | 0.07 cal/cm² | 0.13 cal/cm² |
| Trabajo en panel energizado | 0.36 cal/cm² | 0.43 cal/cm² | 0.76 cal/cm² |
| Maniobra de interruptor | 0.27 cal/cm² | 0.32 cal/cm² | 0.57 cal/cm² |

> 📋 Estos valores asumen una **distancia de trabajo de 18 pulgadas** (455 mm).

## Categorías de PPE según energía incidente

| Categoría | Rango de energía | EPP requerido |
|-----------|------------------|---------------|
| 1 | 1.2 – 4 cal/cm² | Mínimo: traje AR, guantes, protección facial |
| 2 | 4.1 – 8 cal/cm² | + traje AR de 8 cal mínimo |
| 3 | 8.1 – 25 cal/cm² | + traje AR de 25 cal mínimo |
| 4 | 25.1 – 40 cal/cm² | + traje AR de 40 cal mínimo |

## Método detallado (IEEE 1584)

Para mayor precisión se usa la fórmula de IEEE 1584:

$$\log_{10}(E) = K_1 + K_2 + K_3 + K_4 + K_5 + K_6 + K_7$$

Donde:
- **E** = energía incidente (cal/cm²)
- **K1** = constante según tipo de electrodos
- **K2** = factor de tensión
- **K3** = factor de distancia de trabajo
- **K4** = factor de corriente de cortocircuito
- **K5** = factor de tiempo de despeje
- **K6** = factor de modo de operación
- **K7** = factor de orientación

El cálculo requiere software especializado.

## Distancia de trabajo

A menor distancia, mayor energía incidente. La distancia típica es:

- **Operaciones de rutina:** 18–24 pulgadas
- **Mantenimiento:** 12–18 pulgadas
- **Trabajo cercano:** < 12 pulgadas (mayor riesgo)

## Software recomendado

Para cálculos detallados:

- **EasyPower** (comercial)
- **ETAP** (comercial)
- **IEEE 1584 calc spreadsheets** (gratuitos, básicos)

> ⚠️ **Importante:** La energía incidente calculada debe **documentarse** en cada procedimiento de trabajo energizado.$content$
WHERE titulo = 'Cálculo de energía incidente';

UPDATE lecciones SET contenido = $content$# Permiso de Trabajo Energizado

El **permiso de trabajo energizado** es un documento formal que autoriza trabajos en equipos **energizados** cuando no es posible desenergizarlos.

## ¿Cuándo se requiere?

Solo cuando es **técnicamente inviable** desenergizar el equipo, por ejemplo:

- Procesos continuos donde una interrupción cause pérdidas críticas (hospitales, data centers).
- Diagnóstico que requiere el equipo en operación.
- Pruebas que necesitan energización.

> ⚠️ **Nunca** es válido argumentar "por comodidad" o "rapidez". El trabajo energizado siempre es el último recurso.

## Contenido del permiso

Todo permiso debe incluir:

### 1. Descripción del trabajo
- Ubicación exacta.
- Equipo afectado.
- Tarea específica.

### 2. Justificación
- Por qué no se puede desenergizar.
- Alternativas consideradas y descartadas.

### 3. Análisis de riesgos
- Energía incidente estimada (cal/cm²).
- Tensión y corriente presentes.
- Distancia de trabajo.
- Categoría de PPE requerida.

### 4. Medidas de control
- **Barreras** físicas.
- **Lockout/Tagout** de fuentes alternas.
- **Procedimiento** paso a paso.
- **Equipo de rescate** disponible.

### 5. Personal autorizado
- Nombres y calificaciones.
- Capacitación NFPA 70E vigente.

### 6. Firmas de autorización
- Supervisor del área.
- Ingeniero de seguridad.
- Trabajador autorizado.

## Vigencia

El permiso es válido solo para:

- **Una** tarea específica.
- **Una** fecha (no más de un turno).
- **Un** equipo.

Al terminar, se **archiva** por al menos 3 años.

## Proceso

```
1. Solicitud
   ↓
2. Análisis de riesgos
   ↓
3. Determinación de PPE
   ↓
4. Briefing con el equipo
   ↓
5. Autorización (firmas)
   ↓
6. Ejecución del trabajo
   ↓
7. Cierre y archivo
```

## Reunión de briefing

Antes del trabajo:

- Todo el equipo lee el permiso.
- Se confirman los **pasos** del procedimiento.
- Se verifica el **PPE**.
- Se establece la **comunicación** (radio, voz, señales).
- Se identifica el **plan de rescate**.

> 📝 **Documenta todo:** cualquier desviación del permiso requiere **suspensión** del trabajo y un nuevo permiso.

## Plan de rescate

Siempre debe existir:

- Persona capacitada en **rescate eléctrico**.
- Equipo de rescate disponible.
- Ruta de evacuación clara.
- Contacto con servicios de emergencia.

> ⚡ **Regla de oro:** En un accidente eléctrico, **NO toques** a la víctima hasta verificar que la fuente está desconectada.$content$
WHERE titulo = 'Permiso de trabajo energizado';
