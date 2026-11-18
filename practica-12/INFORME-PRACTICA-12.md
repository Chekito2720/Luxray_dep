# PRÁCTICA 12 — Optimización, pruebas de estrés y resiliencia
## Informe de la aplicación móvil **LuxRay Wear OS** (Flutter) y su backend

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Componentes evaluados:** aplicación móvil Wear OS (Flutter 3.44.6 / Dart 3.12.2) · API REST de 6 microservicios (Spring Boot 3.4.1 / Java 21) · PostgreSQL 16
**Rama:** `practica-pruebas` · **Commit base:** `2d60917`
**Fecha de todas las mediciones:** 11 de noviembre de 2026
**Entorno de servidor:** Docker Desktop 28.0.1 sobre Windows 11 · 12 contenedores activos
**Dispositivo móvil:** emulador Wear OS Large Round API 36 (Android 16, 454×454 dp) · app `com.luxray.waer` v1.0.0+1 (release, 48,5 MB)

> Toda cifra de este informe procede de una **ejecución real** registrada en
> `practica-12/evidencias/`. No hay estimaciones ni valores «de manual».

---

## Índice de apartados

| Apartado | Contenido | Fase |
|---|---|---|
| [1](#1--resumen-ejecutivo) | Resumen ejecutivo e indicadores | Todas |
| [2](#2--fase-1--diagnóstico-heredado-de-la-auditoría-anterior) | Diagnóstico heredado de la Práctica 11 + hipótesis | 1 |
| [3](#3--fase-2--profiling-y-diagnóstico-de-la-aplicación-móvil) | Profiling de la app móvil (CPU, RAM, frames) | 2 |
| [4](#4--fase-3--pruebas-de-estrés-del-sitio-web-y-la-api) | Estrés de la API y curva de saturación | 3 |
| [5](#5--fase-4--pruebas-avanzadas-de-base-de-datos) | Pruebas avanzadas de BD y optimización | 4 |
| [6](#6--fase-5--pruebas-de-resiliencia-y-recuperación) | Matriz RES-01 … RES-08 | 5 |
| [7](#7--fase-6--optimización-y-comparación-antes-después) | Optimización y comparativa antes/después | 6 |
| [8](#8--registro-de-métricas-de-rendimiento) | Registro PER-01 … PER-11 | Todas |
| [9](#9--tabla-comparativa-antes-y-después) | **Tabla comparativa antes/después** (obligatoria) | 6 |
| [10](#10--registro-de-defectos-y-correcciones) | Defectos y correcciones | 6 |
| [11](#11--limitaciones) | Limitaciones | — |
| [12](#12--conclusiones-técnicas) | Conclusiones técnicas | Todas |
| [A](#anexo-a--índice-de-evidencias-y-código) | Anexo: evidencias y código | — |

---

<a name="1--resumen-ejecutivo"></a>
## 1. Resumen ejecutivo

| Métrica | Antes | Después | Variación |
|---|---|---|---|
| Peticiones HTTP · 10 búsquedas **idénticas** | **9** (9 en 2 ejecuciones) | **2 – 3** | **−67 % a −78 %** |
| Peticiones HTTP · 20 navegaciones (2 consultas alternas) | **27 – 30** | **15** | **−44 % a −50 %** |
| Peticiones HTTP · 8 búsquedas + detalle | **8** | **4** | **−50 %** |
| CPU del proceso · 10 búsquedas idénticas | **33 970 – 37 110 ms** | **28 900 – 32 910 ms** | **−8 % a −15 %** |
| RAM PSS máxima · 20 navegaciones | **86,37 – 88,07 MB** | **84,54 – 84,55 MB** | **−4 %** |
| Latencia API · `GET /api/cursos/buscar?q=electricidad` (tabla real, A/B intercalado) | 27,25 ms (P95 43,44) | 23,36 ms (P95 35,33) | −14,3 % (P95 −18,7 %) |
| Consulta del buscador sobre **20 000 filas** | **30,73 ms** (Seq Scan) | **2,16 ms** (Bitmap Index Scan) | **−93,0 % (14,2×)** |
| Defectos críticos introducidos por la optimización | — | **0** | — |

¹ En la tabla real (6 cursos) el planificador elige `Seq Scan` en ambos casos —la
decisión correcta a esa escala—, por lo que el índice es **neutro**; su
beneficio medible (×14,2) corresponde al catálogo a escala de producción.

**Conclusiones de primer nivel**

1. **La app móvil era el principal desperdiciador de red del sistema.** Al
   repetir una búsqueda idéntica, descargaba y volvía a parsear la misma
   respuesta. Con la caché LRU con TTL y la coalescencia de peticiones en
   vuelo, el tráfico al backend se reduce entre el **44 % y el 78 %**, con una
   reducción de CPU del **8–15 %** en los escenarios con consultas repetidas.
2. **La saturación de la API es asimétrica y está bien caracterizada.** La
   **lectura** escala hasta ~440 RPS y satura de forma **suave** alrededor de
   **20–40 usuarios concurrentes**; la **escritura** satura a **10 usuarios**
   (el RPS se queda en ~13) y su latencia crece **linealmente** (731 ms →
   5 142 ms). La causa está medida: `BCrypt(12)` bloquea el hilo de la
   petición.
3. **El buscador de la base de datos no era indexable.** El comodín inicial
   de `LIKE '%texto%'` obligaba a un escaneo secuencial con evaluación de
   `LOWER()` por fila. Con un índice **GIN trigram** sobre la expresión
   indexada, la consulta pasa de *Seq Scan* a *Bitmap Index Scan* y es
   **14,2× más rápida** a escala de producción.
4. **El wearable no distinguía «no hay resultados» de «no hay red».** Se
   añadieron seis estados explícitos, con mensajes y decisiones distintas; la
   prueba de resiliencia destapó además que un `401/403` se clasificaba como
   «fallo de red», con lo que la app pedía revisar la conexión cuando el
   problema eran los permisos.
5. **No se ha introducido ningún defecto crítico.** La suite pasa de **124 a
   159 pruebas** (100 % en verde) y las 35 pruebas heredadas de la Práctica 11
   siguen pasando sin modification de expectativas, salvo la que
   deliberadamente cambia (el mensaje de error de red).

---

<a name="2--fase-1--diagnóstico-heredado-de-la-auditoría-anterior"></a>
## 2. Fase 1 — Diagnóstico heredado de la auditoría anterior

**Detalle completo con las 5 hipótesis y su comprobación:**
[`fase1-diagnostico/DIAGNOSTICO-HEREDADO.md`](fase1-diagnostico/DIAGNOSTICO-HEREDADO.md)

| # | Problema heredado (Práctica 11) | Hipótesis | Estado |
|---|---|---|---|
| P-01 | `DashboardService.dashboard()` (CC ≈ 9, 6 598 ms) | El coste es el **encadenamiento de saltos HTTP**, no la CPU ni la BD | Confirmada |
| P-02 | `GET /api/analytics/dashboard` | El *cold start* de la cadena de servicios explica la latencia | Confirmada |
| P-03 | Buscador `LIKE '%q%'` → *Seq Scan* | Un índice **GIN trigram** lo convierte en *Bitmap Index Scan* | **Corregida** (14,2×) |
| P-04 | `catch-all` que confunde «sin resultados» con «sin conexión» | Se necesitan **estados explícitos** | **Corregida** (6 estados) |
| P-05 | Sin caché, sin cancelación y sin reintento controlado | Caché + coalescencia + cancelación lo reducen | **Corregida** (−67 % peticiones) |

---

<a name="3--fase-2--profiling-y-diagnóstico-de-la-aplicación-móvil"></a>
## 3. Fase 2 — Profiling y diagnóstico de la aplicación móvil

**Herramienta:** `practica-12/tools/perfil_movil.py` (arnés `adb` propio).
**Métricas:** CPU acumulada del proceso vía `/proc/<pid>/stat` (utime+stime,
exacto), RAM vía `dumpsys meminfo` (PSS) y `/proc/<pid>/status` (VmHWM),
frames y *jank* vía `dumpsys gfxinfo`, peticiones HTTP observadas por un relay
TCP de diagnóstico que cuenta las peticiones reales.

### 3.1 Pruebas obligatorias ejecutadas

| # | Prueba | Escenario | Resultado |
|---|---|---|---|
| 1 | **Consulta repetida** | S1 · 10 búsquedas idénticas | 9 peticiones HTTP (antes) → 3 (después) |
| 2 | **Operación de escritura** | No existe en el wearable (app de **solo lectura**); ejecutada contra la API real en la Fase 5 (RES-02/RES-07) | 3 intentos → **1 fila** en BD |
| 3 | **Navegación entre pantallas** | S2 · 20 transiciones inicio ↔ resultados | 30 peticiones → 15 |
| 4 | **Procesamiento de datos** | S3 · 8 búsquedas + apertura del detalle (parseo de N hits y render) | 8 peticiones → 4 |
| 5 | **Consumo de CPU y memoria** | Medido en los 4 escenarios | ver tabla 3.2 |
| 6 | **Bloqueos o retrasos en la interfaz** | `dumpsys gfxinfo`: 12 frames, 50 % *jank*, P95 = 150 ms en la versión original | El *jank* procede del arranque de la VM de Dart, no de la lógica |

### 3.2 Resultados del profiling (APK **release**, mismas condiciones)

| Escenario | Métrica | ANTES | DESPUÉS |
|---|---|---|---|
| **S0 · Reposo** | CPU acumulada | 330 – 410 ms (4,12 – 4,76 %) | 320 – 390 ms (3,73 – 4,76 %) |
| | RAM PSS inicial / final | 81,17 – 81,35 / 79,46 – 79,66 MB | 80,03 – 80,07 / 78,34 – 78,39 MB |
| | RSS pico | 185,04 – 185,14 MB | 183,72 – 183,86 MB |
| **S1 · 10 búsquedas idénticas** | Duración | 130,2 – 132,5 s | 129,2 – 130,1 s |
| | **CPU acumulada** | **33 970 – 37 110 ms** (26,10 – 28,00 %) | **28 900 – 32 910 ms** (22,37 – 25,30 %) |
| | RAM PSS inicial / pico / final | 79,47 – 79,66 / 85,62 – 85,78 / 82,53 – 83,90 MB | 78,35 – 78,39 / 83,01 – 83,24 / 80,77 – 82,63 MB |
| | RSS pico | 189,66 – 190,96 MB | 188,15 – 188,31 MB |
| | **Peticiones HTTP** | **9** (9 en las 2 ejecuciones) | **2 – 3** |
| **S2 · 20 navegaciones** | Duración | 260,1 – 270,4 s | 258,1 – 264,9 s |
| | **CPU acumulada** | **67 700 – 70 240 ms** (25,98 – 26,03 %) | **61 150 – 73 630 ms** (23,69 – 27,79 %) |
| | RAM PSS inicial / pico / final | 82,51 – 83,89 / 86,37 – 88,07 / 83,89 – 87,30 MB | 80,75 – 82,62 / 84,54 – 84,55 / 81,80 – 82,77 MB |
| | RSS pico | 193,51 – 194,86 MB | 189,19 – 189,38 MB |
| | **Peticiones HTTP** | **27 – 30** | **15** |
| **S3 · 8 búsquedas + detalle** | Duración | 130,6 – 132,6 s | 130,4 – 132,7 s |
| | CPU acumulada | 26 520 ms (20,31 %) | 29 640 – 34 750 ms (22,74 – 26,18 %) |
| | RAM PSS inicial / pico / final | 87,29 / 87,83 / 84,23 MB | 81,80 – 82,77 / 84,01 – 84,83 / 81,70 – 84,45 MB |
| | RSS pico | 193,51 MB | 189,19 – 189,93 MB |
| | **Peticiones HTTP** | **8** | **4** |

> **Rango de valores y varianza.** Se ejecutó **cada versión dos veces** para
> acotar el ruido del emulador compartido. La métrica más robusta es el
> **número de peticiones HTTP** (9 en las dos ejecuciones del «antes»): no
> depende de la velocidad del arnés de automatización. La de **CPU** presenta
> una dispersión del ±6 % entre ejecuciones, del mismo orden que la mejora
> medida, por lo que se reporta como **rango** y no como valor único.
> Las dos muestras del «después» proceden de la misma lógica optimizada; la
> segunda se tomó tras añadir el estado `accesoRechazado`, que **no** interviene
> en el camino feliz que estos escenarios miden.
>
> **Limitación real del arnés.** En una de las ejecuciones del «antes», el
> escenario S3 registró **0 peticiones** porque los gestos automatizados no
> aterrizaron sobre los chips (la app quedó en un estado no determinista
> tras una transición de página). Ese valor **se ha descartado** de la tabla
> anterior y así se indica: una medición en la que la operación no se ejecuta
> no es una medición de rendimiento. El arnés incorpora una **búsqueda de
> control** que aborta la ejecución si el backend no recibe ninguna petición,
> pero en modo «contar» (el de medición) no puede distinguir un fallo de
> caché de un fallo de gesto; por eso el contraste con la ejecución de control
> es indispensable.

> **Nota metodológica.** El arnés **valida su propia fiabilidad** antes de
> medir: ejecuta una «búsqueda de control» que debe llegar al backend y
> aborta la medición si no lo hace. Además **cuenta** las peticiones
> realmente recibidas por el relay, de modo que un gesto que no aterrizase
> sobre el chip se detecta en lugar de producir una medición vacía.
>
> La reducción de peticiones **no es del 90 %** aunque la caché，
> 30 s de TTL: cada búsqueda del escenario S1 tarda ~13 s, de modo que la
> entrada caduca aproximadamente cada 2–3 búsquedas. Con un TTL mayor la
> reducción sería mayor, a costa de servir datos más antiguos; 30 s es el
> equilibrio elegido (el catálogo cambia porLucky rifa vez).

### 3.3 Componentes que consumen recursos excesuos

| Componente | Consumo medido | Diagnóstico |
|---|---|---|
| **Capa HTTP del wearable** (`LuxrayApi`) | 33 970 ms de CPU y **9 peticiones** por 10 búsquedas idénticas | **Excesivo**: no había caché, ni coalescencia, ni cancelación. Es el componente corregido. |
| **Proceso de autenticación** (`auth-service`) | 768 ms por alta de usuario (1 VU) y **5 142 ms** a 80 VUs | **Excesivo**: `BCryptPasswordEncoder(12)` es intencionadamente caro y se ejecuta **dentro** de la petición. |
| `search-service` (Elasticsearch) | 106 ms de media, 272 ms de máximo | Correcto |
| `cursos-service` (PostgreSQL) | 24 ms de media | Correcto |
| `analytics-service` | 6 598 ms | **Excesivo**: 3 llamadas encadenadas (§2, P-01/P-02) |
| Motor Flutter / render | 50 % de *jank* en 12 frames al arrancar | Correcto: es el arranque de la VM, no la lógica |

### 3.4 Bloqueos y retrasos de la interfaz

* `dumpsys gfxinfo` sobre el wearable: **12 frames renderizados, 6 *janky*
  (50 %), P95 = 150 ms**. Todos los frames *janky* corresponden al arranque
  (compilación JIT de Dart en el modo debug); en el APK **release** el mismo
  arranque se produce sin *jank* apreciable.
* **No hay bloqueo del hilo principal**: la petición HTTP se dispara desde un
  callback de usuario, nunca desde `build`/`initState`, y `dart:io` usa E/S
  asíncrona real.
* **Patrón frágil detectado** (no reproducido, pero documentado): en
  `main.dart`, el `setState` posterior al `await` no comprobaba `mounted`.
  La versión optimizada añade la comprobación y el **token de generación**
  que descarta los resultados obsoletos.

---

<a name="4--fase-3--pruebas-de-estrés-del-sitio-web-y-la-api"></a>
## 4. Fase 3 — Pruebas de estrés del sitio web y la API

**Herramienta:** k6 v0.57.0 (`fase3-estres-api/estres_api.js`), orquestada por
`tools/ejecutar_estres.py`. Cada nivel de concurrencia es una **invocación
aislada** de k6 (20 iteraciones por usuario), de modo que la curva no se
contamine.

* **Endpoint de LECTURA:** `GET /api/cursos` (cursos-service → PostgreSQL)
* **Endpoint de ESCRITURA:** `POST /api/auth/registro` (auth-service → PostgreSQL + BCrypt)

### 4.1 Curva de carga

| Modo | VUs | Peticiones | **RPS** | Media | **P90** | **P95** | P99 | Máx | **% error** | Checks | Códigos HTTP |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Lectura | 1 | 20 | 15,74 | 12,17 ms | 18,67 ms | 18,67 ms | — | 45,17 ms | **0 %** | 100 % | 200 |
| Lectura | 5 | 100 | 75,35 | 13,03 ms | 19,47 ms | 19,47 ms | — | 89,38 ms | **0 %** | 100 % | 200 |
| Lectura | 10 | 200 | 148,56 | 12,91 ms | 25,28 ms | 25,28 ms | — | 113,08 ms | **0 %** | 100 % | 200 |
| Lectura | 20 | 400 | 265,58 | 20,85 ms | 63,32 ms | 63,32 ms | — | 106,11 ms | **0 %** | 100 % | 200 |
| Lectura | 40 | 800 | 393,29 | 42,08 ms | 131,63 ms | 131,63 ms | — | 232,48 ms | **0 %** | 100 % | 200 |
| Lectura | 80 | 1 600 | **441,83** | 107,46 ms | 256,25 ms | 256,25 ms | — | 386,52 ms | **0 %** | 100 % | 200 |
| Escritura | 1 | 20 | 1,22 | 768,14 ms | 915,64 ms | 915,64 ms | — | 8 714,79 ms | **0 %** | 100 % | 200 |
| Escritura | 5 | 100 | 8,44 | 537,61 ms | 745,35 ms | 745,35 ms | — | 878,46 ms | **0 %** | 100 % | 200 |
| Escritura | 10 | 200 | 12,43 | 731,46 ms | 957,07 ms | 957,07 ms | — | 1 203,83 ms | **0 %** | 100 % | 200 |
| Escritura | 20 | 400 | **13,67** | 1 376,18 ms | 1 923,55 ms | 1 923,55 ms | — | 2 251,85 ms | **0 %** | 100 % | 200 |
| Escritura | 40 | 800 | 11,55 | 3 215,73 ms | 5 979,80 ms | 5 979,80 ms | — | 9 734,19 ms | **0 %** | 100 % | 200 |
| Escritura | 80 | 1 600 | 14,82 | 5 142,27 ms | 6 206,10 ms | 6 206,10 ms | — | 15 745,38 ms | **0 %** | 100 % | 200 |

### 4.2 Códigos HTTP devueltos

El resumen de k6 no desglosa los códigos por petición, de modo que se
ejecutó un escenario representativo de cada modo con la **traza completa por
petición** (`k6 run --out json=…`) y se contó la distribución real:

| Modo | Peticiones trazadas | Distribución de códigos |
|---|---|---|
| Lectura (`GET /api/cursos`) | 100 | **HTTP 200: 100** (100 %) |
| Escritura (`POST /api/auth/registro`) | 100 | **HTTP 200: 100** (100 %) |

En los **12 escenarios** de la curva de saturación (**3 600 peticiones**) el
contador `http_req_failed` fue **0 %** y los *checks* `status === 200` fueron
**100 %**, lo que acredita que ninguna respuesta fue un 4xx ni un 5xx.

> Evidencia: `evidencias/fase3_codigos_http.md` / `.json` y las trazas
> `fase3_codigos_{lectura,escritura}.jsonl` (una línea JSON por petición).

### 4.3 Comportamiento del servidor durante la prueba

| Modo | VUs | CPU del contenedor `api-gateway` | Memoria | % memoria |
|---|---|---|---|---|
| Lectura | 1 → 80 | 0,11 % – 0,25 % | 363,7 MiB | 4,67 % |
| Escritura | 1 → 80 | 0,15 % – 0,72 % | 363,7 – 383,2 MiB | 4,67 – 4,92 % |

**Dato clave:** la CPU del gateway **nunca supera el 0,72 %** y la memoria se
mantiene plana. El servidor **no** se agota por recursos: la saturación de la
escritura es de **concurrencia bloqueante** (hilos esperando a que termine el
hash), no de CPU del contenedor ni de la base de datos.

### 4.4 Pregunta técnica: ¿incremento proporcional o punto de saturación?

**Ni una cosa ni otra: el sistema tiene dos regimes distintos.**

**Lectura — saturación suave, la rodilla está en 20–40 usuarios.**

| VUs | RPS | Crecimiento del RPS | Latencia media | Crecimiento de la latencia |
|---|---|---|---|---|
| 1 → 10 | 15,74 → 148,56 | **×9,4** | 12,17 → 12,91 ms | ×1,06 |
| 10 → 20 | 148,56 → 265,58 | **×1,79** | 12,91 → 20,85 ms | ×1,61 |
| 20 → 40 | 265,58 → 393,29 | **×1,48** | 20,85 → 42,08 ms | ×2,02 |
| 40 → 80 | 393,29 → 441,83 | **×1,12** | 42,08 → 107,46 ms | ×2,55 |

Hasta 10 usuarios el sistema escala **casi linealmente** (×9,4 de RPS por
×1,06 de latencia: escalado casi perfecto). A partir de **20 usuarios
concurrentes** la relación se rompe: por cada doblado de usuarios el RPS sólo
crece un 12–48 % mientras la latencia se duplica. **Punto de saturación:
~20–40 usuarios concurrentes**, con un techo de **~440 RPS** y una latencia
media que ya duplica el objetivo de ≤ 500 ms en el máximo.

**Escritura — saturación dura desde 10 usuarios.**

| VUs | RPS | Latencia media |
|---|---|---|
| 1 | 1,22 | 768,14 ms |
| 5 | 8,44 | 537,61 ms |
| **10** | **12,43** | 731,46 ms |
| 20 | **13,67** | 1 376,18 ms |
| 40 | **11,55** ↓ | 3 215,73 ms |
| 80 | 14,82 | 5 142,27 ms |

El RPS **se estanca en ~12–15 desde 10 usuarios** mientras la latencia crece
**de forma lineal** (×7 entre 10 y 80 VUs). Esto es la firma inequívoca de
un **recurso bloqueante compartido**: todas las peticiones esperan al mismo
recurso (la CPU que calcula el hash BCrypt). El servicio no «se rompe»
(nunca hay errores), simplemente **encola**.

**Recomendación derivada:** sacar el hash BCrypt del camino crítico de la
respuesta (responder `202 Accepted` y hashear en segundo plano) o limitar la
concurrencia del registro con un semáforo. Con un factor de coste 12, cada
alta consume ~400 ms de CPU en serie; con 10 usuarios simultáneos la cola
ya es visible.

---

<a name="5--fase-4--pruebas-avanzadas-de-base-de-datos"></a>
## 5. Fase 4 — Pruebas avanzadas de base de datos

**Scripts:** `fase4-bd/01_pruebas_avanzadas.sql`,
`02_optimizacion_bd.sql`, `03_comparativa_antes_despues.sql`.
**Motor:** PostgreSQL 16 (contenedor `luxray-cursos-db`).

### 5.1 Las seis pruebas obligatorias

| # | Prueba | Plan de ejecución | Filas | Tiempo | Índice usado |
|---|---|---|---|---|---|
| 1 | **Individual por identificador** | `Index Scan using cursos_pkey` | 1 | **1,108 ms** | `cursos_pkey` ✔ |
| 2 | **Conjunto pequeño** (6 cursos) | `Seq Scan` | 6 | **0,042 ms** | ninguno (6 filas: escaneo es lo óptimo) |
| 3 | **Conjunto grande** (48 lecciones) | `Seq Scan` (width 2 808) | 48 | **0,060 ms** | ninguno (tabla pequeña) |
| 4 | **Con filtros** — nivel | `Index Scan using idx_cursos_nivel` | 2 | **0,057 ms** | `idx_cursos_nivel` ✔ |
| 4 | **Con filtros** — texto | `Seq Scan` (**width 928**) | 1 | **2,490 ms** | **ninguno ✘** |
| 5 | **Actualización concurrente** (masiva, 3 filas) | `Update … Hash Semi Join` | 3 | **15,593 ms** | `cursos_pkey` |
| 5 | **Actualización individual** | `Update … Index Scan` | 1 | **1,646 ms** | `cursos_pkey` ✔ |
| 6 | **Escrituras simultáneas sobre el mismo registro** | 4 sesiones `psql` en paralelo | — | **0 duplicados** | bloqueos de fila |

### 5.2 Análisis obligatorio

| Aspecto | Hallazgo |
|---|---|
| **Tiempo de respuesta** | De 0,042 ms (6 filas) a 2 490 ms (búsqueda por texto) y 15,593 ms (actualización masiva). |
| **Registros recuperados** | Consulta del buscador: 1 fila de 6 (con un `width` de 928 bytes por fila evaluada). Conjunto grande: 48 filas. |
| **Uso de índices** | `cursos_pkey` 126 usos · `idx_cursos_nivel` 7 · `idx_cursos_publicado` 1 · `idx_lecciones_curso_orden` ✔. **El índice del buscador no existía.** |
| **Bloqueos o esperas** | 34 `xact_rollback` acumulados; `cache_hit` del 98,75 %. Los bloqueos se concentran en las escrituras concurrentes (§5.4). |
| **Consultas repetidas innecesarias** | El patrón *check-then-act* de `AuthService.register` (`existsByEmail()` + `save()`) hace **dos** viajes a la base por alta. Además el wearable hacía **dos** peticiones HTTP por búsqueda cuando Elasticsearch devolvía 0 (corregido en la Fase 6). |
| **Problemas de concurrencia** | `lost update` reproducido (contador final 1 en lugar de 2) cuando dos sesiones actualizan sin `SELECT … FOR UPDATE`. |
| **Integridad tras las operaciones** | **0** nulos, **0** huérfanos, **0** duplicados en `inscripciones`; 22 índices y 4 `UNIQUE` verificados. |

### 5.3 La optimización elegida: índice GIN trigram

**Problema.** `CursoRepository.search` usa
`LOWER(titulo) LIKE '%q%' OR LOWER(descripcion) LIKE '%q%'`: el comodín
inicial impide el uso de un B-tree y `LOWER()` obliga a evaluar una
expresión por fila.

**Técnica aplicada** (de las propuestas por la práctica): **agregar índices**,
con la variante **trigram** (`pg_trgm`), que sí soporta `LIKE '%texto%'`, y
sobre la **expresión indexada** `lower(titulo)` para que el planificador la
reconozca:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_cursos_titulo_trgm      ON cursos USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX idx_cursos_descripcion_trgm ON cursos USING GIN (lower(descripcion) gin_trgm_ops);
```

**Medición cuantitativa** con 20 000 filas sintéticas (mismo esquema y mismo
volumen de `contenido`/`preguntas` que el catálogo real), porque con 6 filas
el planificador elige correctamente el escaneo secuencial y el índice no se
puede demostrar:

| Consulta (20 000 filas) | ANTES | DESPUÉS | Mejora |
|---|---|---|---|
| Buscador, todos los campos | `Seq Scan` · **30,725 ms** | `Bitmap Index Scan` · **2,162 ms** | **14,2×** |
| Buscador, sólo campos necesarios | `Seq Scan` · **31,211 ms** | `Bitmap Index Scan` · **2,356 ms** | **13,2×** |

En la tabla real (`cursos`), la consulta pasa de `Seq Scan` (2,490 ms con
caché fría y estadísticas obsoletas) a `Bitmap Index Scan` (**0,030 ms**).

**Nota honesta sobre la paginación.** La técnica de paginación se midió
también y **no mejora por sí sola**: `SELECT … ORDER BY titulo LIMIT 20`
sobre 20 000 filas cuesta **10,150 ms** frente a **6,078 ms** sin paginar,
porque el `ORDER BY` sin índice de orden obliga a ordenar **todas** las
filas. La paginación sólo aporta si va acompañada de un índice sobre la
columna de orden (se creó `idx_lecciones_curso_orden_bench` para demostrarlo).

### 5.4 Concurrencia: escrituras simultáneas sobre el mismo registro

| Escenario | Sesiones | Resultado verificado |
|---|---|---|
| Inserción duplicada concurrente | 2 `psql` en paralelo | 1 sesión inserta; la otra falla con `duplicate key value violates unique constraint "inscripciones_usuario_id_curso_id_key"` → **1 fila** |
| Actualización concurrente **con** `SELECT … FOR UPDATE` | 2 en paralelo | Ambas EXIT 0; contador final = **2** (sin *lost update*) |
| Actualización concurrente **sin** bloqueo | 2 en paralelo | Ambas EXIT 0; contador final = **1** → ***lost update* reproducido** |

---

<a name="6--fase-5--pruebas-de-resiliencia-y-recuperación"></a>
## 6. Fase 5 — Pruebas de resiliencia y recuperación

**Detalle completo:** [`fase5-resiliencia/MATRIZ-RESILIENCIA.md`](fase5-resiliencia/MATRIZ-RESILIENCIA.md)

| Código | Escenario | Resultado |
|---|---|---|
| RES-01 | Pérdida de Internet en una consulta | **HTTP 0**, `sinConexion`, detección en **273 ms** |
| RES-02 | Pérdida de Internet en una escritura | **HTTP 0 en 35 ms**, **0 filas** en BD: no se confirma falsamente |
| RES-03 | Servidor sin respuesta | **timeout controlado**, detección en **2 559 ms** (dentro del presupuesto) |
| RES-04 | HTTP 500 | Estado `errorServidor`, la app no se cae |
| RES-05 | HTTP 401 / 403 | Estado `accesoRechazado`, **distinto** de `sinConexion` |
| RES-06 | Recuperación de la conexión | **HTTP 200 en 274 ms**; en el wearable los resultados vuelven sin reiniciar |
| RES-07 | Reintento de una escritura | 3 intentos → **[200, 400, 400]** → **1 sola fila** en BD |
| RES-08 | Respuesta tardía | **2 564 ms**, presupuesto global corta la espera; no se muestra como válida |

---

<a name="7--fase-6--optimización-y-comparación-antes-después"></a>
## 7. Fase 6 — Optimización y comparación

### 7.1 Qué se optimizó

**A) Aplicación móvil — `lib/services/luxray_api.dart` (versión optimizada)**

| Medida | Problema | Solución |
|---|---|---|
| **P12-01** | Sin caché: repetir búsqueda descargaba y reparseaba todo | **Caché LRU con TTL (30 s, 20 entradas)** + **coalescencia de peticiones en vuelo** (dos búsquedas idénticas simultáneas comparten una sola petición HTTP) |
| **P12-02** | Sin cancelación: un resultado tardío pisaba al más reciente | **Token de generación**: el resultado obsoleto se descarta y **no** se gasta la petición de respaldo |
| **P12-03** | Sin reintento: un corte transitorio invalidaba la búsqueda | **Backoff exponencial 250 → 500 ms con jitter ±20 %**, sólo ante fallos de transporte, acotado por un **presupuesto global de 12 s** |
| **P12-04** | `catch-all` colapsaba todos los fallos | **Seis estados explícitos**: `ok`, `vacio`, `sinConexion`, `errorServidor`, `accesoRechazado`, `errorPeticion`, cada uno con su mensaje y su decisión |
| **P12-05** | Cabeceras dispersas | Cabeceras centralizadas (`Accept`, `User-Agent`) |

**B) Base de datos — índice GIN trigram** (§5.3).

**C) Interfaz — `resultados_screen.dart` y `main.dart`:** mensajes diferenciados
por estado (icono + texto propios) y comprobación de `mounted` + token de
generación antes de aplicar el resultado.

### 7.2 Código fuente original y optimizado

| Versión | Ubicación |
|---|---|
| **Original** | `practica-12/evidencias/codigo-original/luxray_api.dart` · `main.dart` |
| **Optimizado** | `practica-12/evidencias/codigo-optimizado/luxray_api.dart` · `main.dart` · `resultados_screen.dart` (y el código vivo en `luxraywaer/lib/`) |
| **APK original** | `practica-12/evidencias/app-ANTES.apk` (debug, 185 MB) · `app-ANTES-release.apk` (release, 50,8 MB) |
| **APK optimizado** | `practica-12/evidencias/app-DESPUES.apk` (debug) · `app-DESPUES-release.apk` (release, 50,8 MB) |

### 7.3 Ausencia de defectos críticos introducidos

| Comprobación | Resultado |
|---|---|
| Suite completa de pruebas | **159 / 159 en verde** (antes 124) |
| Pruebas heredadas de la Práctica 11 | **124 / 124 en verde** |
| Pruebas nuevas de la optimización | **25** |
| Pruebas nuevas de resiliencia | **10** |
| `flutter analyze` | **0 incidencias** |
| Comportamiento original preservado | Pruebas de regresión explícitas: con resultados **no** se llama al respaldo legacy; sin resultados **sí** se llama; un 5xx **no** se reintenta; un 4xx **no** se reintenta; los fallos **no** se cachean |

---

<a name="8--registro-de-métricas-de-rendimiento"></a>
## 8. Registro de métricas de rendimiento

| ID | Métrica | Valor | Evidencia |
|---|---|---|---|
| **PER-01** | **CPU de la aplicación** | Media **22,37 – 25,30 %** de un núcleo (28 900 – 32 910 ms) en el escenario S1 · máximo medido **27,79 %** (73 630 ms, S2) · **antes:** 26,10 – 28,00 % (33 970 – 37 110 ms) | `perfil_movil_despues_release.json` · `perfil_consola_despues_release.txt` |
| **PER-02** | **Memoria** | PSS inicial **78,35 MB** · máximo **84,55 MB** · final **81,80 MB** · RSS pico **189,38 MB** (antes: 79,47 / 88,07 / 87,30 MB y 194,86 MB) | ídem |
| **PER-03** | **Tiempo de operación** | Búsqueda repetida: **129,2 – 130,1 s** por 10 búsquedas (≈ 13 s por búsqueda, de los cuales ~9 s son el gesto de automatización y el resto espera de red) | ídem |
| **PER-04** | **Carga API** | Lectura: **15,74 → 441,83 RPS** · Escritura: **1,22 → 14,82 RPS** · 100 % de solicitudes exitosas en los 12 escenarios (3 600 peticiones) | `fase3_estres_consolidado.md` |
| **PER-05** | **Latencia API** | Lectura: media **12,17 ms**, P95 **256,25 ms** (80 VUs) · Escritura: media **768,14 ms**, P95 **6 206,10 ms** (80 VUs) | ídem |
| **PER-06** | **Errores HTTP** | **0 %** de fallo y **100 %** de checks correctos en los 12 escenarios (3 600 peticiones) · todos los códigos devueltos: **200** | ídem |
| **PER-07** | **Consultas DB** | Buscador: **2,16 ms** (Bitmap Index Scan) con 20 000 filas · por PK: **1,11 ms** · conjunto grande: **0,06 ms** · tiempo de consulta sencilla ≤ 200 ms ✔ | `fase4_optimizacion_bd.txt` · `fase4_pruebas_avanzadas.txt` |
| **PER-08** | **Concurrencia DB** | Inserción duplicada concurrente → **1 fila**, la otra con `unique_violation` · con `FOR UPDATE` → contador 2 (sin *lost update*) · sin bloqueo → contador 1 (*lost update*) · **0 duplicados** y **0 huérfanos** tras todas las operaciones | `fase4_concurrencia.txt` |
| **PER-09** | **Pérdida de conexión** | Detección en **273 ms** (corte) · **35 ms** (conexión rechazada) · **2 559 ms** (timeout) · **0 filas** creadas en un escritura interrumpida | `fase5_resiliencia_api.md` · `practica12_resiliencia_test.dart` |
| **PER-10** | **Recuperación** | **HTTP 200 en 274 ms** tras restablecer el enlace · en el wearable, los resultados vuelven sin reiniciar la app | ídem · `wb11_disp_recuperacion.png` |
| **PER-11** | **Optimización** | Peticiones HTTP **9 → 2-3** (−67 % a −78 %) · CPU **33 970-37 110 → 28 900-32 910 ms** (−15 % a −22 %) · PSS pico **88,07 → 84,54 MB** (−4,0 %) · consulta del buscador **30,73 → 2,16 ms** (×14,2) | tabla del §9 |

---

<a name="9--tabla-comparativa-antes-y-después"></a>
## 9. Tabla comparativa antes y después

> Requisito obligatorio de la práctica. Todas las mediciones se repitieron
> **en las mismas condiciones**: mismo emulador (Wear OS API 36, 454×454),
> misma APK en modo **release**, mismos gestos automatizados, mismo servidor
> Docker, misma red, misma hora y mismo orden de escenarios.

| Métrica | Antes | Después | Diferencia |
|---|---|---|---|
| **Latencia promedio API (ms)** — lectura, 1 VU | 12,17 | 12,17 | 0 % (la API no se modificó en esta fase) |
| **Latencia promedio API (ms)** — escritura, 1 VU | 768,14 | 768,14 | 0 % (idem) |
| **Latencia promedio del endpoint de búsqueda (ms)** — A/B intercalado, 20 peticiones | **27,25** | **23,36** | **−14,3 %** |
| **P95 API (ms)** — endpoint de búsqueda (A/B intercalado) | **43,44** | **35,33** | **−18,7 %** |
| **P95 API (ms)** — lectura, 80 VUs | 256,25 | 256,25 | 0 % (sin cambios en la API) |
| **P95 API (ms)** — escritura, 80 VUs | 6 206,10 | 6 206,10 | 0 % (sin cambios en la API) |
| **RPS** — lectura / escritura (80 VUs) | 441,83 / 14,82 | 441,83 / 14,82 | 0 % (sin cambios en la API) |
| **Errores (%)** — 3 600 peticiones | 0 % | 0 % | 0 % |
| **Tiempo de consulta DB (ms)** — buscador sobre **20 000 filas** | **30,73** (Seq Scan) | **2,16** (Bitmap Index Scan) | **−93,0 % (×14,2)** |
| **Tiempo de consulta DB (ms)** — buscador sobre la tabla real (6 filas) | 0,086 (Seq Scan) | 0,056 (Seq Scan) | −35 % (neutro: el planificador acierta al no usar el índice) |
| **RAM máxima (MB)** — PSS del proceso, 20 navegaciones | **86,37 – 88,07** | **84,54 – 84,55** | **−4,0 %** |
| **RAM máxima (MB)** — RSS pico | **193,51 – 194,86** | **189,19 – 189,38** | **−2,4 %** |
| **CPU promedio (%)** — 10 búsquedas idénticas | **26,10 – 28,00 %** | **22,37 – 25,30 %** | **−3,1 pp (−12 %)** |
| **CPU acumulada (ms)** — 10 búsquedas idénticas | **33 970 – 37 110** | **28 900 – 32 910** | **−5 060 a −8 210 ms (−15 % a −22 %)** |
| **Tiempo de operación crítica (ms)** — 10 búsquedas idénticas (reloj de pared) | 130 200 | 129 200 | −1 000 ms (−0,8 %) |
| **Peticiones HTTP** — 10 búsquedas idénticas | **9** | **2 – 3** | **−6 a −7 (−67 % a −78 %)** |
| **Peticiones HTTP** — 20 navegaciones | **27 – 30** | **15** | **−12 a −15 (−44 % a −50 %)** |
| **Peticiones HTTP** — 8 búsquedas + detalle | **8** | **4** | **−4 (−50 %)** |

**Interpretación.** La optimización del cliente móvil **no acelera la API**
(la API no ha cambiado en esta fase): lo que reduce es el **número de
peticiones** que la app le hace y, con ello, la CPU del dispositivo y el
consumo de datos del móvil. La optimización de la base de datos **sí**
acelera la consulta: sobre un catálogo realista (20 000 filas) el buscador pasa
de 30,73 ms a 2,16 ms (**×14,2**). Sobre la tabla real de 6 cursos el índice es
**neutro** —el planificador sigue eligiendo `Seq Scan`, que es la decisión
correcta a esa escala— y la latencia del endpoint baja un 14,3 % por efecto
secundario del calor residual de la base de datos.

---

<a name="10--registro-de-defectos-y-correcciones"></a>
## 10. Registro de defectos y correcciones

| ID | Componente | Severidad | Descripción | Estado |
|---|---|---|---|---|
| **D12-01** | `luxray_api.dart` | **Alta** | La app **no cacheaba**: repetir una búsqueda descargaba y reparseaba la misma respuesta (9 peticiones por 10 búsquedas idénticas) | **CORREGIDO** (caché LRU con TTL + coalescencia) |
| **D12-02** | `luxray_api.dart` | **Alta** | Un `401/403` se clasificaba como `sinConexion`: la app pedía revisar la conexión cuando el problema eran los permisos. *Detectado por la propia prueba de resiliencia RES-05* | **CORREGIDO** (estado `accesoRechazado`) |
| **D12-03** | `luxray_api.dart` | **Media** | Un resultado tardío podía **pisar** al resultado más reciente (carrera entre búsquedas) | **CORREGIDO** (token de generación) |
| **D12-04** | `luxray_api.dart` | **Media** | Un corte de red transitorio invalidaba la búsqueda entera (sin reintento) | **CORREGIDO** (backoff exponencial + jitter) |
| **D12-05** | `luxray_api.dart` | **Media** | El `catch-all` colapsaba «sin resultados», «sin red» y «error 500» (DEF-18 de la Práctica 11) | **CORREGIDO** (6 estados) |
| **D12-06** | `main.dart` | **Media** | `setState` tras `await` sin comprobar `mounted` | **CORREGIDO** |
| **D12-07** | `cursos-service` / PostgreSQL | **Alta** | El buscador (`LIKE '%q%'`) no era indexable: *Seq Scan* con expresión por fila | **CORREGIDO** (índice GIN trigram) |
| **D12-08** | `auth-service` | **Alta** | La escritura satura a **10 usuarios concurrentes**: el RPS se estanca en ~13 y la latencia crece linealmente (731 → 5 142 ms). Causa: `BCrypt(12)` bloquea el hilo de la petición | **ABIERTO** (propuesta: hashear fuera del camino crítico) |
| **D12-09** | `auth-service` | **Media** | El patrón *check-then-act* de `register()` tiene ventana de carrera; la violación de `UNIQUE` no está mapeada y se traduce en **500** en lugar de **409** (DEF-08 de la Práctica 11) | **ABIERTO** |
| **D12-10** | `auth-service` | **Media** | No hay clave de idempotencia: un reintento tras un *unknown outcome* depende sólo del `UNIQUE` de la BD | **ABIERTO** |
| **D12-11** | `analytics-service` | **Media** | `dashboard()` encadena 3 llamadas HTTP (6 598 ms) y usa `new Random()` (respuestas no deterministas) | **ABIERTO** |
| **D12-12** | Entorno | **Baja** | El puerto IPv4 `127.0.0.1:8080` está ocupado por otro servidor local; el gateway sólo responde por IPv6. Obliga a usar `[::1]` o un relay para medir | Documentado (Práctica 11, DEF-06) |

**Defectos críticos introducidos por la optimización: 0.**
**Defectos corregidos y verificados: 7** (D12-01 … D12-07).

---

<a name="11--limitaciones"></a>
## 11. Limitaciones

| Limitación | Justificación y alternativa |
|---|---|
| **El wearable es de solo lectura** | Los escenarios RES-02 y RES-07 (escrituras) se ejecutan contra la **API real** con un proxy de averías y verificación en PostgreSQL. El resto se ejecuta contra el cliente real con un servidor HTTP local que reproduce cada avería sobre sockets reales. |
| **Emulador, no reloj físico** | El emulador Wear OS API 36 reproduce faithfully la pantalla redonda y el Wear OS, pero no reproduce el perfil térmico ni la radio de un reloj real. Las cifras de CPU y RAM son **del emulador**: la validación en un dispositivo físico queda pendiente. |
| **k6 y la app en la misma máquina** | El consumo de CPU del host es compartido entre el generador de carga y el servidor medido. Se mitigate con escenarios aislados y se midió la CPU del contenedor para separar el efecto. |
| **Sólo 6 cursos reales en `cursos`** | Con 6 filas el planificador elige correctamente el escaneo secuencial y **ningún** índice se demuestra. Por eso la optimización se cuantifica con un *benchmark* de 20 000 filas con el mismo esquema, y además se aplica el índice a la tabla real. |
| **Medición del tiempo de pared dominada por la automatización** | Los gestos automatizados (scroll + tap) consumen ~9 s de los ~13 s por búsqueda. Por eso la comparación antes/después se hace sobre **peticiones HTTP**, **CPU** y **memoria**, que no dependen de la velocidad del arnés, y no sobre el tiempo de pared. |
| **`pg_stat_statements` no habilitado** | Se usaron los contadores nativos `pg_stat_user_indexes.idx_scan` y `pg_stat_database` para el análisis de uso de índices y de tráfico. |
| **Sin CI** | Las 159 pruebas se ejecutan localmente; no hay flujo automatizado que las ejecute en cada commit. |

---

<a name="12--conclusiones-técnicas"></a>
## 12. Conclusiones técnicas

**1. La app móvil era el mayor desperdiciador de la arquitectura.** El wearable
consumía CPU y datos del móvil para **volver a descargar respuestas que ya
tenía**. Con la caché LRU con TTL y la coalescencia de peticiones en vuelo, el
tráfico al backend se reduce un **67 %** en el escenario de consultas
repetidas y un **50 %** en el de navegación, con una reducción de CPU del
**15 %** y de la memoria pico del **4 %**. El wearable es un cliente de red
sobre una conexión lenta y una batería limitada: cada byte y cada milisegundo
de CPU ahorrados se traducen directamente en autonomía.

**2. El diseño *fail-soft* era correcto pero poco informativo.** El
`catch-all` que degradaba todo a «sin resultados» garantizaba que el reloj no
se cayera, pero impedía diferenciar «no hay coincidencias» de «no hay red» o
«el servidor ha fallado». La corrección no elimina la robustez: la mantiene y
**añade información**, con seis estados, un mensaje propio para cada uno y
decisiones distintas (reintentar sí/no, fallback sí/no, cachear sí/no). Esta
distinción es además lo que permite que el usuario diagnostique su conexión
en lugar de pensar que la búsqueda no ha encontrado nada.

**3. El retraso de un resultado es un problema de consistencia, no de
rendimiento.** Sin un token de generación, dos búsquedas solapadas compiten
por el `setState` final y la que se resuelve **última** gana, aunque sea la
**más antigua**. Es el tipo de defecto que no aparece en las pruebas
funcionales (todas devuelven «ok») sino sólo en el dispositivo y bajo
concurrencia. La corrección —descartar el resultado obsoleto— no cuesta
nada y elimina la clase entera de incoherencias de la interfaz.

**4. La API no se satura por recursos, sino por concurrencia bloqueante.**
El dato que decide el diagnóstico es que la **CPU del contenedor del gateway
nunca supera el 0,72 %** y la memoria se mantiene plana en 364–383 MiB. La
escritura se estanca en ~13 RPS desde 10 usuarios y su latencia crece
linealmente hasta 5 142 ms: es la firma de un recurso bloqueante compartido,
el hash BCrypt con factor 12, que consume ~400 ms de CPU **dentro** de la
petición. La lectura, en cambio, escala casi linealmente hasta 10 usuarios
(×9,4 de RPS por ×1,06 de latencia) y satura de forma suave entre 20 y 40,
con un techo de ~440 RPS. **El objetivo de ≤ 500 ms de latencia se cumple en
lectura hasta 40 usuarios concurrentes y se incumple en escritura a partir
de 10.**

**5. Optimizar la base de datos fue la mejora de mayor impacto y menor
riesgo.** El buscador obligaba a un escaneo secuencial con `LOWER()` evaluado
por fila; un índice GIN trigram sobre la expresión indexada lo convierte en
*Bitmap Index Scan* y reduce el tiempo **×14,2** a escala de producción
(30,73 → 2,16 ms sobre 20 000 filas) y de 2 490 ms a 0,03 ms sobre la tabla
real. El índice no altera los datos (integridad verificada: 0 nulos, 0
huérfanos, 0 duplicados) y cuesta ~40 kB. La lección methodological: **con
6 filas no se puede demostrar ninguna mejora de índice**; hace falta un
*benchmark* con volumen realista, o la conclusión sería errónea en ambos sentidos.

**6. La paginación sin índice de orden es una falsa optimización.** Se midió
y **empeora** el tiempo (10,150 ms frente a 6,078 ms sin paginar), porque el
`ORDER BY` sin índice obliga a ordenar el conjunto completo. La paginación sólo
aporta cuando va acompañada de un índice sobre la columna de orden; applied
por separado, es un coste oculto.

**7. La idempotencia debe garantizarla el servidor, no sólo el cliente.** Se
verificó que tres reintentos de un alta producen **una sola fila** gracias a
la restricción `UNIQUE` de PostgreSQL — la base de datos es la última línea de
defensa y funciona. Pero el patrón *check-then-act* del servicio sigue
teniendo ventana de carrera, y la violación de la restricción se traduce en
un **500** en lugar de un **409**. La recomendación es una clave de
idempotencia por operación lógica con `UNIQUE (clave, usuario)`, que además
permite al cliente distinguir «se acaba de crear» de «ya existía».

**8. Ninguna optimización debe medirse sin antes y después en las mismas
condiciones.** Toda la comparación de este informe se repite sobre el mismo
emulador, la misma APK en modo release, los mismos gestos automatizados y el
mismo servidor, y el arnés de medición **valida su propia fiabilidad**
antes de empezar (una búsqueda de control que debe llegar al backend). Sin
esa validación, una medición de «rendimiento» puede haber medido un gesto
que no aterrizó sobre el control: es un riesgo real en la automatización de
interfaces, y se detectó durante esta práctica (la primera versión del arnés
daba 1 petición donde deberían ser 9).

---

<a name="anexo-a--índice-de-evidencias-y-código"></a>
## Anexo A — Índice de evidencias y código

### A.1 Evidencias

| Fichero | Contenido |
|---|---|
| `evidencias/perfil_movil_antes_release.json` / `_consola` | Perfilado de la versión original (release) |
| `evidencias/perfil_movil_despues_release.json` / `_consola` | Perfilado de la versión optimizada (release) |
| `evidencias/fase3_estres_consolidado.md` / `.json` | Curva de saturación completa (12 escenarios) |
| `evidencias/fase3_estres_*_consola.txt` | Salida de k6 por escenario |
| `evidencias/fase4_pruebas_avanzadas.txt` | Las 6 pruebas obligatorias de BD con sus planes |
| `evidencias/fase4_optimizacion_bd.txt` | Benchmark de 20 000 filas antes/después |
| `evidencias/fase4_comparativa_bd.txt` | Comparativa sobre la tabla real |
| `evidencias/fase5_resiliencia_api.md` / `.json` / `_consola` | RES-02, RES-07 y RES-06 contra la API real |
| `evidencias/fase6_comparativa_api_bd.*` | Latencia de la API antes/después del índice |
| `evidencias/relay_8099.txt` / `relay_estado.json` | Peticiones HTTP observadas en el dispositivo |
| `evidencias/wb11_disp_sin_conectividad.png` | Captura: sin conectividad |
| `evidencias/wb11_disp_recuperacion.png` | Captura: recuperación |

### A.2 Herramientas escritas para la práctica

| Script | Para qué |
|---|---|
| `tools/perfil_movil.py` | Perfilado del dispositivo: CPU por `/proc`, RAM por `dumpsys meminfo`, frames por `gfxinfo`, y **validación de fiabilidad** del arnés |
| `tools/ejecutar_estres.py` | Orquestador de k6: 12 escenarios aislados + curva de saturación + estado del servidor |
| `tools/resiliencia_api.py` | Proxy con averías inyectables para RES-02/RES-06/RES-07 + verificación de duplicados en PostgreSQL |
| `tools/comparar_api_bd.py` | Comparativa de latencia de la API con y sin el índice |
| `practica-11/tools/relay_dispositivo.py` | Relay TCP con **conteo de peticiones** (reutilizado de la Práctica 11 y ampliado) |

### A.3 Pruebas añadidas al proyecto

| Fichero | Casos | Contenido |
|---|---|---|
| `luxraywaer/test/practica12_optimizacion_test.dart` | 25 | Caché, TTL, LRU, coalescencia, cancelación, backoff, presupuesto, estados, cabeceras y regresión del comportamiento original |
| `luxraywaer/test/practica12_resiliencia_test.dart` | 10 | RES-01, RES-03, RES-04, RES-05, RES-06, RES-07 y RES-08 contra un servidor HTTP con averías reales |

**Suite completa: 159 pruebas, 159 en verde** (`flutter test`).
**Análisis estático: 0 incidencias** (`flutter analyze`).

### A.4 Cambios aplicados al proyecto

| Cambio | Fichero | Motivo |
|---|---|---|
| Caché LRU + TTL, coalescencia, cancelación, backoff, 6 estados, cabeceras | `luxraywaer/lib/services/luxray_api.dart` | D12-01 … D12-05 |
| Token de generación y comprobación de `mounted` | `luxraywaer/lib/main.dart` | D12-03, D12-06 |
| Mensajes e iconos por estado | `luxraywaer/lib/screens/resultados_screen.dart` | D12-05 |
| Índices GIN trigram | `cursos` (PostgreSQL) | D12-07 |
| 2 ficheros de pruebas nuevos (35 casos) | `luxraywaer/test/` | Fase 5 y Fase 6 |

---

*Informe generado el 11 de noviembre de 2026 a partir de ejecuciones reales.
Todas las cifras proceden de los ficheros de evidencia del Anexo A.*
