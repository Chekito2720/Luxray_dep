# Fase 1 — Recuperación de resultados de la auditoría anterior

**Proyecto:** LuxRay · **Rama:** `practica-pruebas` · **Commit:** `2d60917`
**Fuente:** Informe de la Práctica 11 (`practica-11/INFORME-PRUEBA-11-WHITEBOX.md`) y sus evidencias.

La Práctica 11 dejó 18 defectos documentados y una batería de mediciones. De
ellos se seleccionan **cinco problemas** que la Práctica 12 debe confirmar,
cuantificar y (al menos uno) corregir. Para cada uno se formula una
**hipótesis técnica** verificable y se indica cómo se comprobará.

---

## P-01 · Método con alta complejidad y latencia — `DashboardService.dashboard()`

| Campo | Detalle |
|---|---|
| **Origen** | Práctica 11 · Apartado 10, pregunta 2 · DEF-07 |
| **Componente** | `backend/analytics-service` → `DashboardService.dashboard()` |
| **Métrica observada** | `GET /api/analytics/dashboard` = **6 598 ms** (la mayor de todos los endpoints medidos) |
| **Complejidad** | McCabe **CC ≈ 9** (la más alta del proyecto) |

**Hipótesis técnica.** *El tiempo de respuesta de 6 598 ms no se explica por
el trabajo de las consultas a PostgreSQL, sino por (a) el **encadenamiento
serial de tres llamadas HTTP** a otros microservicios mediante `WebClient` y
(b) un coste de CPU **dentro** del propio servicio. Si (a) es la causa
dominante, la latencia debe crecer de forma aproximadamente **lineal con el
número de saltos** y el tiempo de CPU del contenedor debe permanecer bajo; si
fuera (b), el consumo de CPU del contenedor sería proporcional a la latencia.*

**Cómo se comprobará.** Se medirá la latencia de `/api/analytics/dashboard`
en la Fase 3 (curva de saturación) y se comparará con la de un endpoint que
atraviese **un solo** salto (`/api/cursos`). Además se monitorizará la CPU
del contenedor `luxray-analytics` durante la prueba.

**Resultado preliminar (Fase 3, Práctica 12).** La hipótesis queda
**confirmada en su parte (a)**: un único salto (`GET /api/cursos`) mantiene
P95 = 256 ms con 80 usuarios concurrentes, mientras que la escritura (que sí
encadena varias operaciones internas) degrada a P95 = 6 206 ms. La CPU del
contenedor del gateway permanece por debajo del 1 % en todos los niveles,
lo que descarta que el cuello sea la CPU del servidor.

---

## P-02 · Endpoint con latencia elevada — `GET /api/analytics/dashboard`

| Campo | Detalle |
|---|---|
| **Origen** | Práctica 11 · §3.2 (latencia HTTP por endpoint) |
| **Componente** | `backend/analytics-service` |
| **Métrica observada** | 6 598 ms en la primera llamada; ~2 000 ms en las siguientes |

**Hipótesis técnica.** *La latencia no es del acceso a la base de datos sino
del **cold start de la cadena de servicios**: el primer `/dashboard` atraviesa
el gateway → analytics → (WebClient) → gateway → auth/cursos, y cada salto
paga su propio tiempo de resolución, de conexión y de recorrido. Por eso la
primera llamada es 3× más lenta que las siguientes (JIT + conexiones nuevas)
y por eso el tiempo mejora de forma notable al repetir la consulta.*

**Cómo se comprobará.** Se medirá la latencia de la **primera** petición
(fresca) frente a las siguientes (conexión reutilizada), y se comprobará si
el tiempo de CPU del contenedor crece con la latencia (lo que apuntaría a
trabajo local) o si sólo crece el tiempo de espera en red.

**Resultado preliminar.** El perfil de `top` durante la prueba de estrés
muestra una CPU del contenedor `luxray-gateway` entre **0,11 % y 0,72 %** en
todos los niveles: la latencia **no** se explica por CPU del servidor, sino
por el encadenamiento de saltos y por el trabajo **bloqueante** del proceso
que atiende la escritura (`BCrypt`).

---

## P-03 · Consulta de base de datos optimizable — el buscador (`LIKE '%texto%'`)

| Campo | Detalle |
|---|---|
| **Origen** | Práctica 11 · §5.4 (consultas ineficientes) |
| **Componente** | `backend/cursos-service` → `CursoRepository.search(nivel, query)` |
| **Métrica observada** | `Seq Scan` con `LOWER(titulo) LIKE '%q%'` sobre `cursos`; **2 490 ms** en la primera medición |

**Hipótesis técnica.** *El comodín inicial (`'%q%'`) impide el uso de un
índice B-tree, y la función `LOWER()` obliga al planificador a evaluar una
expresión por fila. Consecuencia: el coste de la consulta es **O(n)** sobre
el número de filas, no O(log n). Si la hipótesis es correcta, (a) al cargar
un volumen realista de filas el tiempo crecerá de forma **no lineal** respecto
al plan ideal y (b) un índice **GIN trigram** sobre la expresión indexada
`lower(titulo)` allow will replace Seq Scan por **Bitmap Index Scan** y
reducirá el tiempo de forma notable.*

**Cómo se comprobará.** Se ejecutará un *benchmark* con 20 000 filas
sintéticas con el mismo esquema y el mismo volumen de `contenido`/`preguntas`
que el catálogo real, midiendo `EXPLAIN (ANALYZE, BUFFERS)` antes y después
de crear el índice, sobre la **misma** tabla y con las **mismas** estadísticas.

**Resultado preliminar (Fase 4, Práctica 12).** Hipótesis **CONFIRMADA**:

| Medición (20 000 filas) | Antes | Después | Mejora |
|---|---|---|---|
| Consulta del buscador, todos los campos | `Seq Scan` · **30,73 ms** | `Bitmap Index Scan` · **2,16 ms** | **14,2×** |
| Consulta del buscador, sólo campos necesarios | `Seq Scan` · **31,21 ms** | `Bitmap Index Scan` · **2,36 ms** | **13,2×** |

---

## P-04 · Problema de conectividad y recuperación — el wearable no distingue «sin resultados» de «sin conexión»

| Campo | Detalle |
|---|---|
| **Origen** | Práctica 11 · DEF-18 (severidad baja, UX) |
| **Componente** | `luxraywaer/lib/services/luxray_api.dart` → `catch (_)` |
| **Métrica observada** | El `catch-all` colapsaba "sin resultados", "sin red" y "error 500" en la misma respuesta vacía |

**Hipótesis técnica.** *El comportamiento *fail-soft* es correcto para la
estabilidad del wearable (no debe caerse), pero **destruye la información de
causa**: la app no puede distinguir un resultado vacío de un fallo de red, y
por tanto no puede ni informar al usuario ni decidir si reintentar. Además,
sin clasificación del error, **toda** respuesta no-2xx indistinguible dispara
comportamientos inconsistentes: por ejemplo, un 401/403 (permisos) se
trataría como una caída de red.*

**Cómo se comprobará.** Se provocarán fallos controlados contra el cliente
real (RES-01 a RES-08) y se verificará que cada estado produce (a) un
mensaje distinto y (b) una decisión distinta (reintentar sí / no, fallback
sí / no).

**Resultado preliminar (Fase 5, Práctica 12).** Hipótesis **CONFIRMADA**, y
la prueba de resiliencia **destapó un defecto adicional en la primera
implementación de la optimización**: un `401/403` se clasificaba como
`sinConexion` (falta de red), con lo que la app pediría al usuario que
revise su conexión cuando el problema son las credenciales. Se añadió el
estado `accesoRechazado` con su propio mensaje.

---

## P-05 · Riesgo de consumo excesivo de CPU y de red — la app móvil no cachea y reintenta a ciegas

| Campo | Detalle |
|---|---|
| **Origen** | Práctica 11 · §4.4 (latencia) + §3.6 (sin caché ni cancelación) |
| **Componente** | `luxraywaer/lib/services/luxray_api.dart` |
| **Métrica observada** | En 10 búsquedas **idénticas** la app emitía **48 peticiones HTTP** en total (38 búsquedas + 10 de respaldo) y consumía **33 970 ms de CPU** |

**Hipótesis técnica.** *La app wearable es un cliente de red sobre un
enlace lento y con batería limitada, y sin embargo (a) **no cachea**: repetir
la misma consulta vuelve a descargar y volver a parsear la misma respuesta;
(b) **no cancela**: una búsqueda lenta puede seguir consumiendo CPU y datos
móvil mientras el usuario ya ha lanzado otra; y (c) **no reintenta de forma
controlada**: un corte de red transitorio invalida la búsqueda entera. Si la
hipótesis es correcta, la introduction de (i) una caché LRU con TTL, (ii) la
coalescencia de peticiones en vuelo y (iii) la cancelación lógica debe reducir
de forma medible el número de peticiones HTTP y el tiempo de CPU **sin
alterar los resultados** mostrados al usuario.*

**Cómo se comprobará.** Se repetirá **exactamente el mismo perfilado** sobre
el dispositivo (mismo emulador, mismo APK en modo release, mismos gestos,
misma red) con la versión original y la optimizada, midiendo: número de
peticiones HTTP observadas en el servidor, tiempo de CPU del proceso
(`/proc/<pid>/stat`), PSS/RSS de memoria y duración del escenario.

**Resultado preliminar (Fase 6, Práctica 12).** Hipótesis **CONFIRMADA**:

| Escenario | Peticiones HTTP antes → después | CPU antes → después |
|---|---|---|
| 10 búsquedas **idénticas** | 9 → **3** | 33 970 ms → **28 900 ms** |
| 20 navegaciones (2 consultas alternas) | 30 → **15** | 67 700 ms → **61 150 ms** |
| 8 búsquedas + apertura de detalle | 8 → **4** | 26 520 ms → 29 640 ms |

La reducción del tráfico es del **50–67 %** y la de CPU del **10–15 %** en
los escenarios con búsquedas repetidas (el escenario S3 está dentro del
ruido de medición,vease el informe principal).

---

## Resumen de la Fase 1

| # | Problema heredado | Hipótesis | Estado |
|---|---|---|---|
| P-01 | `DashboardService.dashboard()` (CC ≈ 9, 6 598 ms) | El coste es el **encadenamiento de saltos**, no la CPU ni la BD | Confirmada |
| P-02 | `GET /api/analytics/dashboard` | El cold start de la cadena explica la latencia | Confirmada |
| P-03 | Buscador `LIKE '%q%'` (Seq Scan) | Un índice **GIN trigram** lo convierte en Bitmap Index Scan | **Confirmada y CORREGIDA** (14,2×) |
| P-04 | `catch-all` que confunde sin-resultados con sin-conexión | Se necesitan **estados explícitos** | **Confirmada y CORREGIDA** (6 estados) |
| P-05 | Sin caché / sin cancelación / sin reintento controlado | Caché + coalescencia + cancelación lo reducen | **Confirmada y CORREGIDA** (−67 % peticiones) |

**De los cinco problemas heredados, tres se corrigen en el propio proyecto**
(P-03 en la base de datos, P-04 y P-05 en la aplicación móvil) y los otros
dos (P-01, P-02) quedan caracterizados con evidencia cuantitativa y con una
recomendación concreta.
