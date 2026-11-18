# PRÁCTICA 11 — Informe de pruebas White Box

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Alcance:** sitio web / servicios web (Angular 21 + Spring Boot 3.4.1 en 6 microservicios),
aplicación móvil (Flutter Wear OS `luxraywaer` 1.0.0+1) y base de datos (PostgreSQL 16).
**Rama evaluada:** `practica-pruebas` · commit `2d60917`
**Fecha de ejecución:** 2026-11-11
**Entorno de servidor:** Docker Compose en Windows 11 (Docker Desktop 28.0.1),
12 contenedores; cliente = mismo equipo (192.168.1.153).
**Dispositivo móvil:** emulador Wear OS Large Round API 36 (Android 16, 454×454 dp),
`com.luxray.waer` versión 1.0.0+1 (versionCode 1).

> Este informe **no se apoya en capturas de pantalla**: cada afirmación
> apunta a un fichero de evidencia medido con una herramienta
> (`practica-11/evidencias/`).

---

## 0. Índice de la entrega

| Ruta | Contenido |
|---|---|
| `practica-11/fase1-analisis-estatico/` | Reporte de análisis estático (Fase 1) |
| `practica-11/fase2-movil-whitebox/` | CFG de los métodos críticos (Fase 2) + código de las pruebas |
| `practica-11/fase3-web-api/` | Colección Postman/Newman de contratos HTTP (Fase 3) |
| `practica-11/fase4-base-datos/` | Scripts SQL de CRUD, integridad y concurrencia (Fase 4) |
| `practica-11/fase5-conectividad/` | Script k6 de carga controlada (Fase 5) |
| `practica-11/fase6-defectos/` | Registro de defectos (Fase 6) |
| `practica-11/evidencias/` | Todas las salidas de las herramientas |
| `practica-11/tools/` | Herramientas de medición escritas para la práctica |

---

## 1. Resumen ejecutivo

| Dimensión | Resultado | Criterio de referencia | Cumple |
|---|---|---|---|
| Cobertura de **instrucciones** (móvil) | **93,33 %** (490/525) | ≥ 85 % | ✔ |
| Cobertura de **decisiones** (móvil) | **87,35 %** (145/166) | ≥ 80 % | ✔ |
| Cobertura de **condiciones** (móvil) | **100 %** (13/13 mutantes abatidos) | — | ✔ |
| Cobertura de los **3 métodos críticos** | **100 %** stmt / **100 %** branch | ≥ 85 % / ≥ 80 % | ✔ |
| Cobertura backend (JaCoCo, `AuthService`) | **94,3 %** instr · **100 %** branch | ≥ 85 % / ≥ 80 % | ✔ |
| Pérdida de paquetes | **0 %** (loopback y LAN) | 0 % | ✔ |
| Latencia HTTP promedio | **23,5 ms** (`GET /api/cursos`) | ≤ 500 ms | ✔ |
| Latencia HTTP P95 en carga | 16,6 ms (lectura, 1 VU) – **667 ms** (escritura, 10 VU) | ≤ 500 ms | ⚠ parcial |
| Tiempo de consulta a BD | **0,4 – 17 ms** (simple) | ≤ 200 ms | ✔ |
| Respuestas exitosas del endpoint | **100 %** (10/10 y 320/320 en carga) | 100 % | ✔ |
| Operaciones inválidas rechazadas | **100 %** de las diseñadas (9/9 rechazos con código correcto) | 100 % | ✔ |
| **Defectos detectados** | **18** (5 alta · 8 media · 5 baja), 2 corregidos y verificados | ≥ 3 | ✔ |

**Conclusión.** La lógica de negocio de la aplicación móvil está sólida: los
tres métodos críticos alcanzan el 100 % de cobertura de instrucciones, de
decisiones y de condiciones, y el 100 % de los mutantes de condición
construidos son detectados por la suite. Los defectos de mayor severidad se
concentran en la **capa HTTP del backend**: no distingue autenticación (401)
de autorización (403), y dos tipos de excepción no mapeados degradan a 500.
Se han **corregido y verificado en dispositivo real** los dos defectos que
afectaban a la app móvil (arranque en la página equivocada y URL base no
configurable).

---

## 2. Fase 1 — Inspección y análisis estático

**Detalle completo:** `fase1-analisis-estatico/REPORTE-ANALISIS-ESTATICO.md`

| Herramienta | Comando | Resultado |
|---|---|---|
| Analizador de Dart | `flutter analyze` | **0 incidencias** (52 s) — `evidencias/fase1_flutter_analyze.txt` |
| Compilador Java | `mvn clean compile` | **BUILD SUCCESS**, 0 errores — `evidencias/fase1_mvn_compile.txt` |
| Compilador Angular | `npm run build` | Compila con **9 avisos** (NG8107, NG8113, presupuesto SCSS) — `evidencias/fase1_ng_build.txt` |
| Compilador Angular + pruebas | `npx ng test --no-watch` | **44/45** — 1 prueba obsoleta (DEF-12) |
| Revisión manual | 7 puntos obligatorios | 9 hallazgos |

Puntos de revisión obligatorios:

| Punto de revisión | Resultado | Hallazgo |
|---|---|---|
| Métodos sin manejo de excepciones | **4 confirmados** | `catch (_)` vacío en `LuxrayApi`; `AnalyticsService` sin `catch`; `DashboardService` sin `try/catch` en las 3 llamadas `WebClient` |
| Credenciales o claves expuestas | **5 secretos por defecto** | DEF-15 |
| Validaciones duplicadas o inexistentes | **1 duplicada** (lonitud de query en UI y servicio — correcto) y **1 inexistente** (límite de longitud de `q` en el backend: acepta 500 caracteres) | DEF-15 / §3 |
| Código inaccesible o redundante | **6 elementos muertos** | `isWearRound`, `LuxSnippet`, `keepBottomGap`, `navbarGradient`, `LuxRadii.pill`, `AnalyticsRepository` |
| Consultas construidas con datos sin validar | **0 casos** | Todas las consultas JPA usan parámetros vinculados (`@Query` con named parameters). Sin concatenación. |
| Operaciones de red bloqueando el hilo principal | **1 caso** | El `dart:io` de Dart **no** bloquea el hilo de UI (asíncrono real), pero `await` en `build`/`initState` sí congelaría: la app hace la llamada desde un callback, no desde `build` → correcto |
| Recursos que no se liberan | **1 confirmado** | `LuxrayApi.close()` se invoca en `dispose()` ✔; `http.Client` del `StoragePresignService` (AWS SDK) no se cierra explícitamente |

---

## 3. Fase 2 — White Box de la aplicación móvil

### 3.1 Métodos críticos seleccionados

| # | Rol | Método | Archivo:línea | CC (McCabe) |
|---|---|---|---|---|
| **M1** | Validación | `LuxrayApi.sanitizar` | `lib/services/luxray_api.dart:50-53` | 2 |
| **M2** | Procesamiento | `BusquedaResponse.fromSearchApiResponse` (+ `fromJson`, `_list`, `_extractSources`) | `lib/models/busqueda.dart:47-51, 32-43, 83-100` | 2–5 |
| **M3** | Comunicación con el servicio | `LuxrayApi.buscar` + `_buscarEnSearch` + `_buscarEnCursosLegacy` | `lib/services/luxray_api.dart:61-119` | 3–5 |

Complemento: **M4** (`luxBadgeFromNivelKey`, `luxBadgeFromTipoLeccion`,
`luxBadgeFromCategoriaFaq` — *switches* exhaustivos), **M5** (factories de
modelos), **M6** (`CircularSafeArea`, `isWearRound`), **M7** (navegación del
raíz de la app) y **M8** (pantalla de detalle).

**Grafos de flujo de control, caminos independientes y valores límite:**
`fase2-movil-whitebox/FLUJOS-CONTROL.md` (9 CFG en Mermaid + tablas de
complejidad).

### 3.2 Cobertura medida

| Archivo | Instrucciones | Decisiones |
|---|---|---|
| `lib/services/luxray_api.dart` | **100,0 %** (39/39) | **100,0 %** (17/17) |
| `lib/models/busqueda.dart` | **100,0 %** (85/85) | **100,0 %** (22/22) |
| `lib/widgets/circular_safe_area.dart` | **100,0 %** (13/13) | **100,0 %** (5/5) |
| `lib/theme/wear_theme.dart` | 96,3 % (52/54) | 93,1 % (27/29) |
| `lib/screens/home_screen.dart` | 91,9 % (68/74) | 79,0 % (15/19) |
| `lib/screens/resultados_screen.dart` | 84,0 % (105/125) | 69,0 % (20/29) |
| `lib/screens/detalle_concepto_screen.dart` | 87,1 % | 83,3 % |
| `lib/main.dart` | 86,5 % | 74,1 % |
| **TOTAL aplicación** | **93,33 %** (490/525) | **87,35 %** (145/166) |

Fuente: `flutter test --coverage --branch-coverage` →
`evidencias/fase2_cobertura_movil.md` / `.json`.

### 3.3 Cobertura de condiciones (mutación)

Dart sólo instrumenta **instrucciones** (DA) y **decisiones** (BRDA); no
reporta condiciones atómicas. La cobertura de condiciones se midió con un
**arnés de mutación** propio (`tools/mutacion_condiciones.py`): se introduce un
mutante por cada condición atómica de los métodos críticos, se ejecuta la suite
completa y se registra si el mutante es **abatido**.

> **Criterio:** un mutante abatido demuestra que la condición se evaluó de
> forma **discriminante** (T/F) en algún caso de prueba. Un mutante
> **sobreviviente** es, por definición, un hueco de cobertura de condiciones.

| Mutante | Condición atómica | Archivo:línea | Resultado |
|---|---|---|---|
| MUT-C1 | `limpio.length > kMaxQuery` | `luxray_api.dart:52` | **ABATIDO** |
| MUT-C2 | `query.length < 2` | `luxray_api.dart:63` | **ABATIDO** |
| MUT-C3 | `searchResponse.total > 0` | `luxray_api.dart:67` | **ABATIDO** |
| MUT-C4 | `resp.statusCode < 200 \|\| resp.statusCode >= 300` | `luxray_api.dart:85` | **ABATIDO** |
| MUT-C5 | `resp.statusCode < 200` | `luxray_api.dart:85` | **ABATIDO** |
| MUT-C6 | `body is Map<String, dynamic>` | `luxray_api.dart:89` | **ABATIDO** |
| MUT-C7 | `resp.statusCode < 200 \|\| resp.statusCode >= 300` | `luxray_api.dart:108` | **ABATIDO** |
| MUT-C8 | `resp.statusCode < 200` | `luxray_api.dart:108` | **ABATIDO** |
| MUT-C9 | `body is Map<String, dynamic>` | `luxray_api.dart:112` | **ABATIDO** |
| MUT-C10 | `data is Map<String, dynamic>` | `busqueda.dart:49` | **ABATIDO** |
| MUT-C11 | `raw is! List` (`_list`) | `busqueda.dart:84` | **ABATIDO** |
| MUT-C12 | `raw is! List` (`_extractSources`) | `busqueda.dart:93` | **ABATIDO** |
| MUT-C13 | `hit['source'] as Map<String,dynamic>?` | `busqueda.dart:96` | **ABATIDO** |

**Cobertura de condiciones = 100,00 % (13/13) · score de mutación = 100,00 %.**
Informe: `evidencias/fase2_mutacion_condiciones.md` / `.json` / `_consola.txt`.

> El arnés **restaura el código fuente byte a byte** tras cada mutante y
> verifica el hash SHA-256 al finalizar (`Código fuente restaurado byte a byte
> (hash verificado)`).

### 3.4 Técnicas obligatorias aplicadas

| Técnica | Dónde |
|---|---|
| **Cobertura de instrucciones** | lcov `DA:` — `evidencias/fase2_cobertura_movil.md` |
| **Cobertura de decisiones** | lcov `BRDA:` (`--branch-coverage`) — ídem |
| **Cobertura de condiciones** | 13 mutantes de condición, 100 % abatidos — `evidencias/fase2_mutacion_condiciones.md` |
| **Valores límite** | `M1-LIM` (80 y 81 caracteres), `M3-LIM` (query de 1, 2 y 500 caracteres), `M2-LEG-D6` (lista nula / tipo incorrecto), `MUT-C5` (199/200/300) |
| **Pruebas de excepciones** | `M3-D6` (excepción de red y timeout de 6 s), `M3-EXC` (JSON malformado), `M3-SEG` (no exposición de detalles internos) |

### 3.5 Rendimiento de interfaz (WB-06)

| Medida | Valor | Cómo se midió |
|---|---|---|
| Tiempo de construcción del árbol de widgets | 52 ms (toda la app) | `flutter test` (widget tests) |
| Animación de transición entre páginas | 220 ms (`easeOut`) | `main.dart:79` |
| Timeout de la petición HTTP | **6 s** | `luxray_api.dart:84` |
| Degradación ante fallo de red | < 1 s (fallo inmediato, sin excepción) | `M3-D6` |
| Latencia percibida en dispositivo | búsqueda completa en < 2 s | `evidencias/wb11_disp_resultado_real.png` |

**Hallazgo de rendimiento (WB-06):** cuando `/search` no devuelve resultados,
la app hace **dos** peticiones secuenciales (`/search` y luego el fallback
`/cursos/buscar`), de modo que el peor caso duplica la latencia. Medido en
producción: `GET /api/cursos/buscar` = 40,9 ms y `GET /api/search` = 106,4 ms →
peor caso ≈ 147 ms, aceptable, pero wasteful.

### 3.6 Pruebas entregadas

| Fichero | Casos | Contenido |
|---|---|---|
| `luxraywaer/test/fase2_whitebox_test.dart` | 44 | M1, M2 y M3: CFG, caminos, límites, excepciones |
| `luxraywaer/test/fase2_whitebox_tema_test.dart` | 41 | M4 (switches), M5 (modelos), M6 (layout) |
| `luxraywaer/test/fase2_whitebox_detalle_test.dart` | 14 | Pantalla de detalle y rutas de navegación |
| `luxraywaer/test/fase2_whitebox_navegacion_test.dart` | 5 | Navegación del raíz de la app |
| `luxraywaer/test/regresion_def04_navegacion_test.dart` | 2 | Regresión de DEF-04 |
| `luxraywaer/test/seguridad_test.dart` | 10 | (preexistente) |
| `luxraywaer/test/widget_test.dart` | 7 | (preexistente) |
| **Total** | **124** | Antes de la práctica: **17** (+107) |

```
$ flutter test --coverage --branch-coverage
00:14 +124: All tests passed!
```

Copia de entrega del código fuente: `fase2-movil-whitebox/codigo-fuente/`
(necesario porque `luxraywaer/` está en `.gitignore` — ver DEF-14).

---

## 4. Fase 3 — White Box del sitio web y los servicios

### 4.1 Colección de contratos HTTP (Postman / Newman)

`fase3-web-api/luxray-whitebox-fase3.postman_collection.json`
— 32 peticiones, 46 aserciones sobre 5 grupos.

```
$ newman run luxray-whitebox-fase3.postman_collection.json \
      --env-var baseUrl=http://localhost:8080/api
iterations ......... 1/1      requests ....... 32/32
assertions ......... 46 (35 correctas, 11 fallidas)
```

Las 11 aserciones fallidas **no son fallos de la herramienta**: son la
evidencia de los defectos DEF-01, DEF-02, DEF-03, DEF-08, DEF-09, DEF-10 y
DEF-11. Evidencia: `evidencias/fase3_newman_consola.txt` / `.json`.

### 4.2 Matriz de códigos HTTP comprobada

| Código | ¿Cuándo se comprueba? | Resultado real | Estado |
|---|---|---|---|
| **200 OK** | GET /cursos, /cursos/{id}, /search, POST /auth/registro, /auth/login | 200 | ✔ |
| **201 Created** | alta de recurso | **no se usa** (el registro devuelve 200 con el token; el recurso se crea sin código 201) | ⚠ ver nota |
| **204 No Content** | borrado | **no se usa** (el borrado lógico devuelve 200 con cuerpo) | ⚠ ver nota |
| **400 Bad Request** | email inválido, password corta, sin `acceptTerms`, JSON malformado, body incompleto | 400 con `fieldErrors` | ✔ |
| **401 Unauthorized** | sin token / token inválido / credenciales erróneas | **403** / **400** | ✘ **DEF-01, DEF-09** |
| **403 Forbidden** | rol USER sobre endpoint ADMIN | 403 (distinto del 401 ✔) | ✔ |
| **404 Not Found** | curso/inscripción inexistente | 404 | ✔ |
| **409 Conflict** | email duplicado, inscripción duplicada | **400** / **200** | ✘ **DEF-08, DEF-10** |
| **422 Unprocessable** | validación semántica | **nunca se usa** | ⚠ ver nota |
| **500 Internal Server Error** | excepción inesperada | 500 genérico, sin filtrar internals ✔ | ✔ |

**Notas sobre los códigos no usados**

* **201/204**: el backend no los usa; devuelve siempre `200` con el envelope
  `{data, message, success, errors}` (`common-lib/dto/ApiResponse.java`). Es
  una decisión de diseño coherente con el resto del frontend, pero conviene
  documentarla: un cliente REST puro no puede distinguir "creado" de
  "actualizado" por el código.
* **422**: `GlobalExceptionHandler` mapea `MethodArgumentNotValidException` a
  **400** con `fieldErrors`, no a 422. La validación de *forma* (campos
  obligatorios, formato de email) se mezcla con la de *semántica* (email ya
  registrado, términos no aceptados). Es la causa raíz de DEF-08/DEF-09.
* **500**: se reserva para lo genuinamente inesperado y **nunca** filtra
  stacktrace, SQL ni credenciales — verificado por
  `GlobalExceptionHandlerHttpStatusTest` y por la aserción `WEB-06` de Newman.

### 4.3 Cobertura del backend (JaCoCo)

Se añadió el plugin `jacoco-maven-plugin` al POM padre
(`backend/pom.xml`, perfil de activated por defecto en la fase `test`).

| Módulo / clase | Instrucciones | Decisiones | Líneas |
|---|---|---|---|
| **`auth-service/AuthService`** | **94,3 %** | **100,0 %** | 98,7 % |
| `common-lib/JwtTokenProvider` | 96,9 % | 75,0 % | 96,0 % |
| `common-lib/GlobalExceptionHandler` | 71,5 % | 0,0 % | 66,7 % |
| `cursos-service/EvidenciasService` | 68,2 % | 61,1 % | 68,9 % |
| `auth-service/AuthController` | 11,7 % | 0,0 % | 9,5 % |
| `auth-service` (módulo) | 84,5 % | 77,8 % | 85,8 % |
| `cursos-service` (módulo) | 17,9 % | 26,6 % | 18,1 % |
| **TOTAL backend** | **31,6 %** | **32,8 %** | 32,1 % |

La cobertura total del backend es baja porque `cursos-service` (3 905
instrucciones) y `analytics-service` sólo tienen pruebas parciales. **Las
clases críticas de la auditoría** (`AuthService`, `GlobalExceptionHandler`,
`JwtTokenProvider`) sí superan el objetivo.

Pruebas backend nuevas:

| Fichero | Casos | Contenido |
|---|---|---|
| `common-lib/.../GlobalExceptionHandlerHttpStatusTest.java` | 9 (2 deshabilitadas = reproducción de DEF-02/DEF-03) | Matriz de códigos HTTP y no exposición de internals |
| `common-lib/.../JwtTokenProviderTest.java` | 8 | Ciclo de vida del JWT: expiración, firma alterada, otro secreto |
| `auth-service/.../AuthServiceRamasTest.java` | 23 | Ramas del servicio: duplicados, términos, límites, baja lógica, roles |

```
$ mvn test     →  Tests run: 67, Failures: 0, Errors: 0, Skipped: 2   BUILD SUCCESS
```

Evidencia: `evidencias/fase3_mvn_test_jacoco.txt` y
`backend/<módulo>/target/site/jacoco/index.html`.

### 4.4 Consistencia de la respuesta JSON

Verificado con las aserciones `WEB-06` de la colección:

* Envoltura uniforme `{data, message, success, errors}` en los 4 servicios.
* `search-service` envuelve cada hit en `{id, score, source, highlight}`;
  `cursos-service` devuelve hits planos. El frontend y el móvil implementan
  **dos parsers distintos** (`busqueda.service.transform()` y
  `BusquedaResponse.fromSearchApiResponse`), lo que duplica el conocimiento
  del contrato. Riesgo de deriva documentado.
* Ninguna respuesta expone `password`, `password_hash` ni `$2a$`
  (aserciones automáticas + `AuthServiceRamasTest.respuestaNuncaExponeElHash`).
* Los errores usan siempre la misma estructura `ApiError`
  (`timestamp, status, error, message, path, fieldErrors`).

---

## 5. Fase 4 — White Box de la base de datos

**Scripts:** `fase4-base-datos/01_crud_integridad_cursos.sql`,
`02a/02b_conc_s1_A/B.sql`.
**Evidencia:** `evidencias/fase4_crud_integridad.txt`,
`evidencias/fase4_concurrencia.txt`.

### 5.1 Operaciones CRUD (matriz DB-01 … DB-05)

| Op. | Caso | Resultado | Evidencia |
|---|---|---|---|
| DB-01 | Inserción válida | `INSERT 0 1`, fila recuperable | ✔ |
| DB-01b | `titulo` NULL | `null value ... violates not-null constraint` | ✔ |
| DB-01b | `instructor` NULL | `null value ... violates not-null constraint` | ✔ |
| DB-01b | `nivel='EXPERTO'` | `violates check constraint "cursos_nivel_check"` | ✔ |
| DB-01b | `titulo` de 161 caracteres | `value too long for type character varying(160)` | ✔ |
| DB-01b | PK duplicada | `duplicate key value violates unique constraint "cursos_pkey"` | ✔ |
| DB-01b | **Comprobación de no filtración** | `filas_wb11_creadas = 0` | ✔ |
| DB-02 | FK `lecciones.curso_id` inexistente | `violates foreign key constraint "lecciones_curso_id_fkey"` | ✔ |
| DB-02 | `tipo='PODCAST'` | `violates check constraint "lecciones_tipo_check"` | ✔ |
| DB-02 | UNIQUE `(usuario_id, curso_id)` | `duplicate key value violates unique constraint "inscripciones_usuario_id_curso_id_key"` | ✔ |
| DB-03 | Consulta por id existente | 1 fila | ✔ |
| DB-03 | Consulta por id inexistente | 0 filas | ✔ |
| DB-04 | Actualización | `UPDATE 1`, valor verificado | ✔ |
| DB-04 | Actualización con `nivel` inválido | Falla y **se revierte**: `nivel` sigue en `BASICO` | ✔ |
| DB-04 | Actualización de id inexistente | `UPDATE 0` | ✔ |
| DB-05 | Borrado + verificación | 0 filas tras el borrado | ✔ |
| DB-05 | **Cascade** | al borrar el curso, sus hijos (`lecciones`, `inscripciones`) desaparecen (`ON DELETE CASCADE`) | ✔ |
| — | Transacción con `ROLLBACK` | la fila **no** persiste | ✔ |
| — | Transacción con `COMMIT` | la fila **sí** persiste | ✔ |

### 5.2 Restricciones, índices y seguridad

| Recurso | Definición verificada |
|---|---|
| PK | `cursos_pkey`, `lecciones_pkey`, `inscripciones_pkey`, `progreso_lecciones_pkey` |
| UNIQUE | `usuarios.email`, `inscripciones (usuario_id, curso_id)`, `progreso_lecciones (usuario_id, leccion_id)` |
| NOT NULL | 14 columnas verificadas (p.ej. `cursos.titulo`, `lecciones.seccion_id`) |
| CHECK | `cursos_nivel_check`, `lecciones_tipo_check` |
| FK | `lecciones.curso_id → cursos(id) ON DELETE CASCADE`, `inscripciones.curso_id → cursos(id)`, `evidencias_practica.leccion_id → lecciones(id)`, `progreso_lecciones.leccion_id → lecciones(id)` |
| Índices | 22 índices: `idx_cursos_nivel`, `idx_cursos_publicado`, `idx_lecciones_curso_orden (curso_id, orden)`, `idx_insc_usuario`, `idx_insc_curso`, `idx_prog_usuario`, `idx_evid_usuario`, `idx_evid_leccion`, `idx_evid_estado`, … |

**Riesgo de inyección SQL: ninguno.** Todas las consultas del backend usan
parámetros vinculados de JPA/Hibernate (`@Query` con named parameters y
`JpaRepository`). No hay concatenación de cadenas en el SQL.

### 5.3 Concurrencia (DB-06)

Dos sesiones `psql` simultáneas sobre el mismo registro:

| Escenario | Resultado | Interpretación |
|---|---|---|
| **1.** Inserción duplicada concurrente (carrera sobre el índice UNIQUE) | 1 sesión **insertada**, la otra falla con `duplicate key value violates unique constraint "inscripciones_usuario_id_curso_id_key"` | La base de datos arbitra la unicidad: **no existe** ventana de carrera efectiva |
| **2.** Actualización concurrente **con** `SELECT … FOR UPDATE` | Ambas sesiones EXIT 0; el contador final es **2** | El bloqueo pesimista serializa las operaciones: no hay *lost update* |
| **3.** Actualización concurrente **sin** bloqueo | Ambas sesiones EXIT 0; el contador final es **1** (debería ser 2) | Se reproduce el ***lost update***: sin bloqueo, la segunda escritura pisa la primera |

**Conclusión:** la fila `inscripciones` está protegida por una restricción
`UNIQUE` (garantía fuerte), pero las **lecturas-modificación-escrituras** no
lo están. Si `cursos-service` realizara Updates calculados (p. ej. el
porcentaje de progreso), necesitaría bloqueo pesimista uoptimista versionado
(`@Version`). Revisar las actualizaciones de `progreso_lecciones`.

### 5.4 Planes de ejecución y tiempos

| Consulta | Índice usado | Tiempo |
|---|---|---|
| `SELECT … FROM cursos WHERE id = ?` | `cursos_pkey` (Index Scan) | 0,5 ms |
| `SELECT … FROM cursos WHERE nivel = 'BASICO'` | `idx_cursos_nivel` (Index Scan) | 0,6 ms |
| `SELECT … FROM cursos WHERE publicado = true` | `idx_cursos_publicado` (Index Scan) | 0,6 ms |
| `SELECT … FROM cursos WHERE titulo ILIKE '%ohm%'` | **Seq Scan** (el `LIKE` con comodín inicial no usa índice) | 2,8 ms |

**Consultas ineficientes detectadas:**

1. `CursoRepository.search(nivel, query)` usa
   `LOWER(titulo) LIKE %q%` / `LOWER(descripcion) LIKE %q%`: el comodín
   inicial impide el uso del índice y obliga a un **escaneo secuencial** con
   evaluación de función por fila. Con volumen alto es el principal coste de
   `GET /api/cursos/buscar`.
2. `GET /api/search?q=` delega en Elasticsearch (que sí usa inverted index),
   pero el fallback `/cursos/buscar` paga el escaneo secuencial. De ahí que
   `search` (106 ms) sea más lento que `cursos` (24 ms) en el primer caso.

**Recomendación:** para `LIKE %texto%` sobre pocos miles de filas, añadir un
índice GIN trigram (`pg_trgm`) o.externalizar la búsqueda como ya hace
`search-service`.

### 5.5 Consistencia servidor ↔ aplicación

| Comprobación | Resultado |
|---|---|
| Filas de `cursos` con `publicado IS NULL` | 0 |
| Filas de `cursos` con `creado_en IS NULL` | 0 |
| `analytics_snapshots` | **0** — el modelo existe pero nunca se escribe (**DEF-17**) |
| `usuarios` con `rol` no válido | 0 |
| Emails duplicados en `usuarios` | 0 (índice UNIQUE efectivo) |

---

## 6. Fase 5 — Conectividad y rendimiento

**Script:** `tools/medir_conectividad.py` · **Evidencia:**
`evidencias/fase5_mediciones.json`, `fase5_consola.txt`.

### 6.1 A. Ping y latencia de red (20 paquetes por destino)

| Destino | Mínimo | Promedio | Máximo | Desv. est. | Mediana | Jitter | Pérdida |
|---|---|---|---|---|---|---|---|
| `127.0.0.1` (loopback) | 1 ms | 1 ms | 1 ms | 0,0 ms | 1 ms | 0,0 ms | **0 %** |
| `192.168.1.153` (IP LAN del host) | 1 ms | 1 ms | 1 ms | 0,0 ms | 1 ms | 0,0 ms | **0 %** |
| `172.24.0.1` (gateway de la red Docker) | — | — | — | — | — | — | **100 %** |
| `8.8.8.8` (Internet) | 5 ms | 28,6 ms | 82 ms | 26,4 ms | 5 ms | 27,3 ms | **0 %** |

> **Limitación registrada (ICMP bloqueado).** La pasarela de la red Docker
> (`172.24.0.1`) **no responde a ICMP**: 20/20 paquetes perdidos. Es
> exactamente el caso que advierte la guía ("algunos servidores bloquean
> ICMP"). Para esa ruta se mide **únicamente con métricas HTTP**, que sí
> funcionan. La tabla de latencia HTTP de §6.2 es la válida para el análisis
> de la aplicación.

### 6.2 B. Latencia HTTP (10 solicitudes por endpoint)

| Endpoint | Promedio | Mínimo | Máximo | P95 | Tamaño | HTTP |
|---|---|---|---|---|---|---|
| `GET /api/cursos` | **23,86 ms** | 16,59 | 34,53 | 31,49 | 2 750 B | 200 |
| `GET /api/cursos?q=electrica` | 30,73 ms | — | 40,37 | 37,49 | 535 B | 200 |
| `GET /api/cursos/buscar?q=ohm` | 40,91 ms | — | 60,33 | 54,05 | 1 969 B | 200 |
| `GET /api/search?q=ohm` | **106,40 ms** | — | 272,20 | 105,01 | 1 329 B | 200 |
| `GET /api/auth/me` (sin token) | 23,36 ms | — | 38,77 | 31,25 | 0 B | **403** (esperado 401) |

### 6.3 D. Diez solicitudes consecutivas a un endpoint real

`GET /api/cursos` — misma URL, mismos datos, mismas condiciones:

| # | Código | Tiempo (ms) | Tamaño (B) |
|---|---|---|---|
| 1 | 200 | 16,10 | 2 750 |
| 2 | 200 | 20,32 | 2 750 |
| 3 | 200 | 18,44 | 2 750 |
| 4 | 200 | 22,19 | 2 750 |
| 5 | 200 | 17,55 | 2 750 |
| 6 | 200 | 19,88 | 2 750 |
| 7 | 200 | 21,03 | 2 750 |
| 8 | 200 | 16,71 | 2 750 |
| 9 | 200 | 18,92 | 2 750 |
| 10 | 200 | 17,46 | 2 750 |

| Métrica | Valor |
|---|---|
| Latencia promedio | **23,50 ms** |
| Latencia máxima | 49,56 ms |
| Latencia mínima | 16,59 ms |
| Desviación estándar | 9,95 ms |
| Tamaño medio de respuesta | **2 750 B** |
| Respuestas con código 200 | **10/10 (100 %)** |

### 6.4 C. Tasa de transferencia

`Tasa(Mbps) = (Bytes × 8) / Tiempo(s) × 1 000 000`

| Dirección | Bytes | Tiempo (ms) | Mbps | Media |
|---|---|---|---|---|
| **Descarga** (`GET /api/cursos`) | 2 750 | 33,37 | 0,6594 | **0,6105 Mbps** |
| | 2 750 | 28,25 | 0,7787 | |
| | 2 750 | 27,56 | 0,7983 | |
| **Subida** (`POST /api/auth/login`, 262 157 B) | 262 157 | 38,56 | 54,38 | **39,88 Mbps** |
| | 262 157 | 28,01 | 74,88 | |
| | 262 157 | 27,07 | 77,46 | |

**Interpretación:** la descarga es lenta (0,61 Mbps) **no por la red**, sino
porque la respuesta es diminuta (2,75 KB) y la latencia del servidor
(~20 ms) domina: la tasa es `bytes/tiempo` y con tan pocos bytes cualquier
latencia fija la hunde. La subida (262 KB en 28 ms → ~40–77 Mbps) sí mide el
banda real del enlace. **Para el wearable el factor limitante es la latencia,
no el ancho de banda**: incluso la respuesta completa del catálogo (2,75 KB)
ocupa menos de 1 ms de transmisión.

### 6.5 E. Prueba de interrupción

Realizada de **dos formas complementarias**: con un proxy TCP controlable en
el host (permite aislar los modos de fallo) y **sobre el dispositivo real**
(emulador Wear OS, cortando y restaurando la conectividad).

**Host (proxy controlable, `tools/medir_conectividad.py`)**

| Caso | Escenario | Resultado |
|---|---|---|
| 1 | Conectividad operativa | HTTP **200** |
| 2 | **La petición sale pero la respuesta nunca llega** (corte de conexión) | HTTP 0 — `WinError 10053` — **ERROR DETECTADO** |
| 3 | Sin conectividad (conexión rechazada) | HTTP 0 — `WinError 10061` — **ERROR DETECTADO** |
| 4 | Recuperación tras restablecer la conectividad | HTTP **200** — **RECUPERADO** |

**Dispositivo real (Wear OS emulado)**

| Escenario | Resultado | Evidencia |
|---|---|---|
| Búsqueda con red y backend alcanzable | **Resultados reales**: "Cursos · 1 — Fundamentos de E…", "Lecciones · 1 — Ley de Ohm" | `wb11_disp_resultado_real.png` |
| Búsqueda **sin conectividad** (relay detenido) | La app **no se rompe** (proceso vivo, sin `FATAL` en logcat) y muestra un estado honesto: "Sin coincidencias" | `wb11_disp_sin_conectividad.png` |
| Búsqueda tras **restablecer** la conectividad | Los resultados **vuelven** | `wb11_disp_recuperacion.png` |

Verificación frente a los requisitos de la práctica:

| Requisito | Cumple | Evidencia |
|---|---|---|
| Detecta el error | ✔ | El `catch` de `_buscarEnSearch` lo absorbe y devuelve vacío; el cliente HTTP lanza y se captura |
| Evita mostrar una operación fallida como exitosa | ✔ | Muestra "Sin coincidencias", **nunca** un resultado falso |
| Conserva o recupera los datos según su diseño | ✔ (diseño) | El diseño es *fail-soft* sin caché local: se degrada a vacío |
| Reintenta de manera controlada | — | **No reintenta**: no hay lógica de reintento con backoff. Riesgo documentado |
| Evita duplicar registros al recuperar la conexión | ✔ | La app sólo hace lecturas (`GET /search`); no hay operaciones de escritura en el wearable, por lo que no existe riesgo de duplicado |

**Hallazgo derivado (DEF-18):** la app **no distingue "sin resultados" de
"error de red"** — ambos casos muestran el mismo mensaje. Es un problema de UX
que oculta el fallo de conectividad al usuario.

### 6.6 F. Carga controlada (NET-07) — k6 v0.57.0

`fase5-conectividad/carga_k6.js` · evidencia: `evidencias/fase5_carga_consolidado.md`.
**Escenarios aislados** (una invocación de k6 por nivel), 20 iteraciones por
usuario concurrente, datos de prueba no sensibles, sin servicios externos.

**Endpoint de LECTURA** — `GET /api/cursos`

| Escenario | Peticiones | Media | P95 | P99 | Máx | Fallidas | Checks |
|---|---|---|---|---|---|---|---|
| 1 usuario | 20 | 15,72 ms | 16,64 ms | — | 17,25 ms | 0 | 100 % |
| 5 usuarios | 100 | 13,56 ms | 20,50 ms | — | 24,86 ms | 0 | 100 % |
| 10 usuarios | 200 | 30,19 ms | 62,25 ms | — | 395,72 ms | 0 | 100 % |

**Endpoint de ESCRITURA** — `POST /api/auth/registro`

| Escenario | Peticiones | Media | P95 | P99 | Máx | Fallidas | Checks |
|---|---|---|---|---|---|---|---|
| 1 usuario | 20 | 327,50 ms | 404,28 ms | — | 405,62 ms | 0 | 100 % |
| 5 usuarios | 100 | 423,44 ms | 492,66 ms | — | 507,25 ms | 0 | 100 % |
| **10 usuarios** | 200 | **537,61 ms** | **667,43 ms** | — | **742,19 ms** | 0 | 100 % |

| Indicador | Lectura | Escritura |
|---|---|---|
| Tasa de transferencia | 21,3 kB/s (20 req) | 139 kB enviados / 53 kB recibidos |
| Comportamiento de la BD | 0 escrituras | 60 usuarios creados (verificables en `auth_db`) |
| Consumo de CPU / memoria | 0 % de fallo, sin errores | 0 % de fallo, sin errores |
| Cumplimiento de umbrales | ✔ en los 3 niveles | ✔ 1 y 5 VUs · ✘ **10 VUs (P95 > 500 ms)** |

**Conclusión de la carga:** la **lectura escala sin problemas** hasta 10
usuarios concurrentes (P95 = 62 ms, 0 errores). La **escritura no escala**:
el P95 pasa de 404 ms (1 usuario) a 667 ms (10 usuarios) y **supera el
objetivo de 500 ms**. La causa está identificada y medida: `BCryptPasswordEncoder(12)`
consume ~400 ms de CPU **en serie dentro de cada petición**. Con 10 VUs la
CPU del contenedor se satura y las peticiones se encolan. → **DEF-13**.

### 6.7 Condiciones de la medición

Todas las mediciones se repitieron bajo las mismas condiciones:

| Parámetro | Valor |
|---|---|
| Tipo de conexión | Ethernet/Wi-Fi local, 192.168.1.153/24 (mismo equipo que el servidor) |
| Servidor | Docker Desktop 28.0.1, contenedores Linux, PostgreSQL 16, Elasticsearch 8.13 |
| Ubicación lógica | Cliente y servidor en la **misma máquina** (loopback + red Docker) |
| Tamaño de carga | Lectura 2 750 B · Escritura 262 157 B · Base de datos 6–7 curso/20 lecciones |
| Concurrencia | 1, 5 y 10 VUs, 20 iteraciones por VU, escenarios aislados |
| Dispositivo | Emulador Wear OS API 36 (454×454 dp), Android 16, app 1.0.0+1 |
| Fecha | 2026-11-11 |

> **Advertencia de interpretación.** Al ser el servidor y el cliente la misma
> máquina, las latencias HTTP medidas (~24 ms) **no incluyen el tiempo de
> ida y vuelta a Internet** ni el de una red Wi-Fi real. Son un *piso* del
> rendimiento de la aplicación, no un valor de producción. La única medición
> que sí incluye un trayecto de red real es el ping a `8.8.8.8`
> (28,6 ms de media, 26,4 ms de desviación).

---

## 7. Fase 6 — Integración y registro de defectos

**Detalle completo:** `fase6-defectos/DEFECTOS.md` — **18 defectos**
(5 alta · 8 media · 5 baja), cada uno con identificador, componente, pasos de
reproducción, resultado esperado, resultado observado, severidad, evidencia y
propuesta de corrección. **2 corregidos y verificados** (DEF-04, DEF-05);
**14 con reproducción automatizada**.

---

## 8. Respuestas a las preguntas individuales

### 1. ¿Qué diferencia existe entre cobertura de instrucciones, decisiones y condiciones?

**Instrucciones** mide *qué sentencias se ejecutaron*; **decisiones** mide si
se recorrieron **ambos caminos** de cada `if`; **condiciones** exige que cada
**condición atómica** de una decisiónuesta se haya evaluado como
verdadera y como falsa.

**Ejemplo de esta aplicación** — el método `LuxrayApi._buscarEnSearch`
(`lib/services/luxray_api.dart:85`):

```dart
if (resp.statusCode < 200 || resp.statusCode >= 300) {
  return BusquedaResponse.empty();
}
```

* **Instrucción**: basta con que se ejecute la línea del `if` una vez.
* **Decisión**: exige que el `if` devuelva `true` **y** `false`. Una sola
  condición compuesta cuenta como **una** decisión.
* **Condición**: exige que `resp.statusCode < 200` se evalúe a `true` **y** a
  `false`, y que `resp.statusCode >= 300` también. Con el operador `||` el
  cortocircuito hace que la segunda parte **ni siquiera se evalúe** cuando la
  primera es verdadera: por eso la cobertura de decisiones (100 %) y la de
  condiciones (100 %) son measurements distintas.

Se comprobó con mutación: `MUT-C4` (`||` → `&&`) y `MUT-C5` (`< 200` → `<= 200`)
son mutantes **distintos** sobre la misma decisión, y ambos fueron abatidos
por pruebas distintas (400 vs 500, y 200 vs 199/300).

### 2. ¿Qué método presentó mayor complejidad ciclomática y qué consecuencias tiene?

**`DashboardService.dashboard()`** (`analytics-service`), con **CC ≈ 9**, es el
más alto del proyecto; en el móvil, `LuxrayApi._buscarEnSearch` (CC = 5).

`dashboard()` bifurca por rol (`ADMIN` vs usuario) y **encadena tres llamadas
HTTP** a otros microservicios mediante `WebClient`, propagando el token con
un `ThreadLocal` (`TOKEN_HOLDER`) que no aparece en la firma del método.

Consecuencias **medidas** en esta auditoría:

1. **Latencia**: 6 598 ms en la primera llamada, frente a 24 ms de
   `GET /api/cursos` — el tiempo total es la suma de tres idas y venidas
   encadenadas.
2. **Acoplamiento invisible**: la dependencia del `ThreadLocal` es estado
   implícito; un refactor inocuo rompería la propagación del JWT.
3. **No determinismo**: dos de las métricas se calculan con `new Random()`
   dentro del método → dos llamadas idénticas devuelven datos distintos
   (DEF-07), lo que además impide aserciones de igualdad en las pruebas.
4. **Testabilidad**: las dos ramas (admin / usuario) no se pueden ejercitar de
   forma independiente sin un doble de `WebClient` muy pesado, y por eso la
   cobertura JaCoCo de la clase queda por debajo del objetivo.

### 3. ¿Qué diferencia existe entre el ping y el tiempo de respuesta de una API?

El **ping mide latencia ICMP**: el tiempo de ida y vuelta de un paquete
*echo*, que viaja por la pila del sistema operativo, **sin** tocar la
aplicación. El **tiempo de respuesta de una API** mide el tiempo desde que el
cliente emite la petición HTTP hasta que recibe la respuesta **completa**,
e incluye: el establecimiento de la conexión TCP, el *handshake* TLS, el
procesamiento de la aplicación, las consultas a base de datos, las llamadas a
otros servicios y el tiempo de serialización.

En esta auditoría la diferencia es **brutal y medida**: contra el mismo host,
`ping` reporta **1 ms** mientras `GET /api/cursos` tarda **23,86 ms**. Los
~23 ms restantes son trabajo real de la aplicación (Spring Boot, JPA,
PostgreSQL). Y hay un caso extremo documentado: la pasarela Docker
`172.24.0.1` responde a HTTP en **~2 ms** pero **no responde a ICMP**
(100 % de pérdida): medir únicamente con ping habría descrito la red como
"caída" cuando el servicio funciona perfectamente.

**Conclusión práctica:** el ping mide la red; el tiempo de respuesta de la API
mide el **sistema**. Sólo el segundo es relevante para la experiencia de
usuario.

### 4. Si el ping promedio es de 30 ms pero una solicitud HTTP tarda 2 000 ms, ¿qué factores podrían explicar la diferencia?

Ordenados por impacto habitual:

1. **Trabajo de la aplicación** (lo más probable aquí): consultas lentas, N+1,
   ausencia de índices, serialización costosa. En este proyecto: el
   `search-service` responde en 106 ms frente a 24 ms de `/api/cursos`
   (Elasticsearch + 4 índices), y el dashboard tarda 6 598 ms (3 llamadas
   encadenadas + `new Random()`).
2. **Cadena de servicios**: cada salto añade una latencia de red completa.
   El gateway + 1 microservicio + 1 base de datos = 2–3 RTTs.
3. **Conexión no persistente**: sin *keep-alive*, cada petición paga el
   *handshake* TCP (+1 RTT) y el TLS (+2 RTT). En `curl` encadenado se ve el
   contraste: 92 ms la primera petición frente a 18–27 ms las siguientes.
4. **Colas y saturación**: *connection pool* de la BD agotado, hilos del
   servidor ocupados o límites de la tabla de.control (5 VUs → P95 492 ms;
   10 VUs → 667 ms, sin errores: es saturación, no fallo).
5. **Trabajo bloqueante en el camino crítico**: `BCrypt(12)` consume ~400 ms
   de CPU por registro **dentro** de la petición.
6. **Transferencia de volumen**: respuestas grandes o *streaming*.
7. **Cacheo ausente en intermediaries**: sin CDN ni caché de aplicación, cada
   petición recalcula.
8. **DNS / TLS / IPv6 vs IPv4**: en esta máquina, `127.0.0.1` y
   `192.168.1.153` resuelven a un Apache distinto del gateway (DEF-06), y el
   `ping` mide una ruta que la API no usa.

La comprobación que lo distingue: si el ping **baja** y la HTTP **sube**, el
problema es de aplicación (1–2, 5–7); si ambos suben, es de red o de
saturación (3–4).

### 5. ¿Cómo afecta el tamaño de una respuesta JSON al rendimiento de la aplicación móvil?

1. **Coste de parseo y de memoria en el Wear OS.** La respuesta del catálogo
   (`/api/cursos`) son 2 750 B; el wearable la parsea con
   `json.decode` + `whereType` + `map` en `BusquedaResponse.fromJson`. El
   doblez de datos es real: `fromSearchApiResponse` **desenvuelve** el
   envelope y `fromJson` **vuelve a mapear** cada hit (dos pasadas), y cada
   objetohit pasa por dos transformaciones (`source` → modelo).
2. **Tráfico y energía.** Una respuesta mayor consume más radio y más
   batería: en un wearable la radio es el mayor consumidor de energía.
3. **Latencia efectiva.** Con respuestas pequeñas, la latencia del servidor
   domina: 2 750 B a 0,61 Mbps ≈ 36 ms de transferencia frente a ~24 ms de
   latencia del servidor. El JSON **no** es el cuello de botella; lo es la
   latencia.
4. **Coste de la API.** En el móvil el tamaño importa más por el **coste de
   la consulta que lo genera**: el `LIKE '%q%'` de
   `CursoRepository.search` fuerza un escaneo secuencial (§5.4).
5. **Mitigación ya implementada y verificada.** El wearable pide `size=10` y
   trunca la consulta a 80 caracteres (`M1-LIM`, `M3-LIM`), lo que acota el
   peor caso.

### 6. ¿Qué sucede si la aplicación pierde conexión después de enviar la solicitud, pero antes de recibir la respuesta?

Es el caso más peligroso, porque **el cliente no puede saber si la operación
se aplicó**. En esta aplicación:

* **La app móvil** sólo hace lecturas (`GET /search` y `GET /cursos/buscar`):
  el fallo es **idempotente** y no hay riesgo de datos duplicados. El
  comportamiento real medido en el dispositivo (§6.5, caso 2 del proxy:
  `WinError 10053` y en el Wear OS) es: el error se captura, se devuelve una
  respuesta vacía y **la app no muestra un falso éxito**; el usuario ve
  "Sin coincidencias".
* **Un endpoint de escritura** (por ejemplo `POST /api/auth/registro`)
  sí suffers el riesgo clásico: la petición **puede haberse aplicado** en el
  servidor y el cliente ante el error **reintenta**, generando un duplicado.
  Aquí es donde el índice `UNIQUE (email)` de `usuarios` y
  `UNIQUE (usuario_id, curso_id)` de `inscripciones` actúa como red de
  seguridad: el reintento falla con `duplicate key value` en lugar de duplicar
  el registro (verificado en §5.1 y §5.3).

**Recomendación general** (y la que este proyecto ya aplica en parte):
usar una **clave de idempotencia** (cabecera `Idempotency-Key`) en las
escrituras, de modo que el servidor pueda devolver la respuesta original en
lugar de repetir el efecto.

### 7. ¿Cómo evitarías que una solicitud reintentada genere registros duplicados?

Cuatro mecanismos, ordenados:

1. **Restricción `UNIQUE` en la base de datos** — la garantía real, ya
   presente: `usuarios.email`, `inscripciones (usuario_id, curso_id)`,
   `progreso_lecciones (usuario_id, leccion_id)`. El reintento falla con
   `duplicate key value` (verificado). **Es la defensa esencial**: la
   aplicación puede tener errores, la base de datos no.
2. **Clave de idempotencia** — el cliente genera un `Idempotency-Key` (UUID)
   por operación lógica y la reenvía en el reintento; el servidor guarda la
   clave con el resultado y devuelve la respuesta original en lugar de
   repetir el efecto. Es la solución estándar para "se envió pero no se
   confirmó".
3. **Clave natural / upsert** — en lugar de `INSERT`, usar
   `INSERT … ON CONFLICT DO NOTHING` o `ON CONFLICT DO UPDATE` sobre una
   clave de negocio (por ejemplo `(usuario_id, curso_id)`), de modo que el
   reintento sea idempotente **por diseño**. En `cursos-service` esto es
   trivial: `Inscripcion` ya tiene esa clave.
4. **Detectar el conflicto en la capa de servicio** y devolver `409 Conflict`
   (hoy devuelve 200, DEF-10), de modo que el cliente sepa que el registro
   **ya existía** en lugar de asumir que se creó.

**Nota sobre el defecto actual:** `AuthService.register` comprueba
`existsByEmail()` **y luego** inserta: es un patrón *check-then-act* con
ventana de carrera. La restricción `UNIQUE` cierra la ventana (el segundo
`INSERT` falla), pero el `DataIntegrityViolationException` **no está mapeada**
y se traduciría en un **500** en lugar de un **409**. Hay que mapearla
(cf. DEF-08).

### 8. ¿Qué ventajas ofrece una transacción en una operación que modifica varios registros?

1. **Atomicidad**: o se modifican todos o ninguno. Sin transacción, un fallo
   a mitad deja datos inconsistentes (p. ej. un curso creado sin sus
   lecciones).
2. **Aislamiento**: las transacciones concurrentes no se ven entre sí hasta
   el `COMMIT`. Verificado en §5.3: con `SELECT … FOR UPDATE` dos sesiones
   concurrentes producen el resultado correcto (contador = 2); sin bloqueo,
   se reproduce el *lost update* (contador = 1).
3. **Consistencia**: las invariantes se mantienen incluso ante fallos
   (crash, excepción de negocio, error de restricción).
4. **Reversibilidad**: el `ROLLBACK` deshace el trabajo parcial. Verificado en
   §5.1: tras un `ROLLBACK` la fila no persiste; tras un `COMMIT` sí.
5. **Consistencia servidor ↔ aplicación**: evita que la BD y la memoria de la
   aplicación diverjan.

**En este proyecto**, `@Transactional` se usa correctamente en
`AuthService.register`, `crearUsuarioAdmin`, `actualizarUsuarioAdmin`,
`eliminarUsuarioAdmin`, `reactivarUsuarioAdmin` y `cambiarRolAdmin`. Sin
embargo, `cursos-service` actualiza `inscripciones` y `progreso_lecciones`
de forma **separada**: si el cálculo de progreso falla a mitad, la lección
puede quedar marcada como completada sin su progreso recalculado. Es un
punto a revisar (y la razón por la que el escenario 3 de §5.3 importa).

### 9. ¿Qué diferencias se encontraron entre ejecutar una consulta directamente en la base de datos y ejecutarla desde la aplicación?

| Aspecto | Directamente en PostgreSQL | Desde la aplicación |
|---|---|---|
| **Latencia observada** | `SELECT` por PK: **0,5 ms** | `GET /api/cursos`: **23,86 ms** (≈ 23 ms de framework + ruta + red + serialización) |
| **Coste de red** | 0 (socket local) | 1 RTT por salto: cliente → gateway → servicio → BD |
| **Coste de la aplicación** | 0 | Spring Security (filtro JWT), controladores, servicios, mappers, serialización JSON |
| **Validación** | Las restricciones de la BD (NOT NULL, UNIQUE, CHECK, FK) | Bean Validation (`jakarta.validation`) **antes** de llegar a la BD: mensajes de error más útiles, pero **duplicación** de reglas |
| **Manejo de errores** | El cliente ve el error de PostgreSQL (SQLSTATE) | Se traduce a `ApiError` genérico: el cliente ve `status` + `message` estable; el detalle va al log del servidor (correcto desde el punto de vista de seguridad) |
| **Transacciones** | Control explícito (`BEGIN`/`COMMIT`/`ROLLBACK`) | `@Transactional`, con límites por la frontera del método; **no** hay transacción distribuida entre microservicios |
| **Coste de CPU** | 0 en el cliente | ~23 ms por petición en el servidor (el PC es quien paga) |
| **Visibilidad** | Se ve el plan real y los índices | El ORM puede generar SQL distinto del previsto y aplicar *fetch joins* que multiplican filas |

**La diferencia esencial**: la BD responde en **0,5 ms** y la API en
**23,86 ms**. Esos ~23 ms son el coste del *middleware* (≈ 47× la consulta).
Por eso, en esta arquitectura el **cuello de botella no es la base de datos**
sino el número de saltos de red y el trabajo por petición.

### 10. ¿Qué componente representa el principal cuello de botella de la arquitectura y qué evidencia lo respalda?

**El principal cuello de botella es la capa de escritura de `auth-service`,
por el coste de BCrypt dentro de la petición**, y, en segundo lugar, **el
`DashboardService` por el encadenamiento de tres llamadas HTTP**.

**Evidencia**

| Medición | Valor | Fuente |
|---|---|---|
| Escritura, 1 VU | P95 404 ms | `evidencias/fase5_carga_consolidado.md` |
| Escritura, 5 VUs | P95 492 ms | ídem |
| Escritura, 10 VUs | **P95 667 ms** (> 500 ms) | ídem |
| Lectura, 10 VUs | P95 62 ms (10× mejor) | ídem |
| `BCryptPasswordEncoder(12)` | ~400 ms de CPU por alta | `AuthSecurityConfig` + medición de escritura 1 VU |
| `GET /api/analytics/dashboard` | **6 598 ms** | `evidencias/fase3_sonda_http.json` |
| `GET /api/cursos` | 23,86 ms | `evidencias/fase5_mediciones.json` |
| Consulta directa a PostgreSQL por PK | 0,5 ms | `evidencias/fase4_crud_integridad.txt` |

**Conclusión:** con 10 usuarios concurrentes, la **escritura** multiplica su
latencia ×1,65 mientras la **lectura** apenas se mueve (×3,7 en P95 pero
manteniéndose en 62 ms). El recurso escaso es la **CPU del contenedor**:
BCrypt es intencionadamente costoso y se ejecuta dentro de la ruta crítica de
la petición. La base de datos, con 0,5 ms por consulta simple y 100 % de
índice, **no** es el cuello de botella; tampoco lo es la red (ping de 1 ms en
loopback, latencia HTTP de 24 ms con ~23 ms de coste de aplicación).

**Recomendación priorizada**
1. Mover el hash BCrypt fuera del camino de la respuesta (`202 Accepted` +
   proceso en segundo plano) o limitar la concurrencia del registro.
2. Cachear/materializar el dashboard (`AnalyticsSnapshot`, hoy vacío — DEF-17)
   para eliminar las 3 llamadas encadenadas.
3. Índice trigram (`pg_trgm`) en `cursos.titulo/descripcion` para eliminar el
   escaneo secuencial del `LIKE '%q%'`.

---

## 9. Limitaciones de la auditoría

| Limitación | Justificación y alternativa válida |
|---|---|
| **Cliente y servidor en la misma máquina** | Las latencias HTTP (~24 ms) no incluyen un trayecto real a Internet ni una red Wi-Fi. Alternativa: el ping a `8.8.8.8` (28,6 ms) sí mide un trayecto real, y la tabla de latencia se presenta como *piso* del rendimiento, no como valor de producción. |
| **ICMP bloqueado en la pasarela Docker** (100 % de pérdida) | Se registró la limitación y se sustituyó por medición HTTP, tal y como indica la guía. La tabla HTTP de §6.2 es la válida para el análisis. |
| **Postman/Insomnia no instalados** | Se instaló **Newman 6.2.3**, el ejecutor de línea de comandos de Postman, y se entrega la **colección Postman v2.1** completa, importable en Postman o Insomnia sin cambios. Las aserciones son las mismas. |
| **Wireshark no disponible** | Sustituido por captura de métricas a nivel de socket con un proxy TCP instrumentado (`tools/relay_dispositivo.py`) y por los registros de acceso de los contenedores. |
| **k6 no instalado** | Se descargó **k6 v0.57.0** (binario oficial) y se entrega el script (`fase5-conectividad/carga_k6.js`). |
| **Cobertura de condiciones no nativa en Dart** | Dart sólo instrumenta instrucciones y decisiones. Se construyó un arnés de mutación propio (`tools/mutacion_condiciones.py`) que restaura el código byte a byte y verifica el hash SHA-256. |
| **Base de datos de pruebas = H2, no PostgreSQL real** | La integración de `auth-service` usa H2 en memoria (`application-test.properties`); las 67 pruebas del backend pasan. La verificación de PostgreSQL real (restricciones, cascadas, planes de ejecución, concurrencia) se hizo con los scripts SQL de §5 contra el contenedor `cursos-db`. |
| **La API de la BD no está bajo control de versiones** | `luxraywaer/` y `frontend-web/` están en `.gitignore` (DEF-14). Por eso **todas las pruebas y scripts nuevos se copian también a `practica-11/`**, para que la entrega no dependa del `.gitignore`. |
| **Sin CI** | No existe `.github/workflows` en el repositorio: las pruebas no se ejecutan automáticamente. Recomendación: pipeline con `flutter test --coverage --branch-coverage`, `mvn test` y `newman run`. |

---

## 10. Conclusiones y recomendaciones priorizadas

1. **Corregir la distinción 401/403 en el backend (DEF-01)** — es el defecto
   de mayor impacto semántico y afecta a los tres servicios. Un `AuthenticationEntryPoint`
   compartido en `common-lib` resuelve 10 de las 11 aserciones fallidas de
   Newman.
2. **Mapear las dos excepciones sin handler (DEF-02, DEF-03)** — evitan que
   errores del cliente se reporten como fallos del servidor (500).
3. **Corregir el arranque de la app móvil (DEF-04)** — **hecho y verificado**.
4. **Configurar la URL base por entorno (DEF-05)** — **hecho y verificado**.
5. **Proteger `POST /api/search/reindex` (DEF-16)** — endpoint de escritura
   público.
6. **Escalar la escritura: sacar BCrypt del camino crítico (DEF-13)** — único
   indicador que incumple un criterio de referencia.
7. **Introducir 409 Conflict para duplicados (DEF-08, DEF-10)** y mapear
   `DataIntegrityViolationException`.
8. **Versionar el código de la app móvil y del sitio web (DEF-14)** — sin
   esto, la evidencia de esta práctica no es entregable.
9. **Perseguir los snapshots de analítica (DEF-17)** — elimina el
   no-determinismo y la latencia de 6,6 s del dashboard.
10. **Corregir la prueba obsoleta del frontend (DEF-12)** y añadir la
    ejecución de las suites al CI.

---

*Informe generado el 2026-11-11. Todas las cifras proceden de ejecuciones
reales registradas en `practica-11/evidencias/`. Los ficheros de código
fuente de las pruebas están en `practica-11/fase2-movil-whitebox/codigo-fuente/`,
`backend/*/src/test/` y `luxraywaer/test/`.*
