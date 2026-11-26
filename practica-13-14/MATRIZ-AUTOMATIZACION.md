# Prácticas 13 y 14 — Automatización de pruebas y validación continua
## Matriz de automatización

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Componentes:** aplicación móvil **LuxRay Wear OS** (Flutter 3.44.6 / Dart 3.12.2) · backend de **microservicios Spring Boot 3.4.1** (Java 21) · **PostgreSQL 16** (producción) / **H2** (perfil `test`)
**Rama evaluada:** `practica-pruebas` · **Remoto:** `github.com/Chekito2720/Luxray_dep`
**Pipeline:** `.github/workflows/ci-luxray.yml` (GitHub Actions)

---

## Matriz de trazabilidad AUT-01 … AUT-08

| ID | Requisito de la práctica | Dónde se resuelve | Evidencia | Estado |
|---|---|---|---|---|
| **AUT-01** | **≥ 5 pruebas unitarias** sobre la función móvil crítica | `luxraywaer/test/practica13_unitarias_test.dart` — **11 pruebas** (U-01 … U-07) contra un `HttpServer` real | [`evidencias/ci/artifacts/flutter/flutter_test.xml`](evidencias/ci/artifacts/flutter/flutter_test.xml) · logs de CI | ✅ |
| **AUT-02** | Casos de **entrada vacía/nula** y **fuera de rango** | U-02 (vacía, nula, espacios, caracteres de control → **cero peticiones HTTP**) · U-03 (500 caracteres → truncado a 80) | JUnit XML de la CI | ✅ |
| **AUT-03** | **Excepciones**: red caída, timeout, error de servidor, acceso denegado | U-05, U-05b, U-05c, U-05d — cada fallo se clasifica en un estado **distinto** | JUnit XML de la CI | ✅ |
| **AUT-04** | **≥ 3 pruebas de integración** entre componentes (comunicación y **códigos HTTP**) | `practica13_integracion_test.dart` — I-01 (cliente↔servicio), I-02 (códigos HTTP), I-04 (contrato) | JUnit XML de la CI | ✅ |
| **AUT-05** | **Persistencia del registro** comprobada en la base de datos | `backend/.../RegistroIntegracionTest.java` — I-05 (fila persistida y legible por el repositorio), I-05b (**no se duplica la fila**) | [`evidencias/ci/artifacts/backend/.../TEST-...RegistroIntegracionTest.xml`](evidencias/ci/artifacts/backend) | ✅ |
| **AUT-06** | **Mensaje adecuado ante servidor que no responde** | I-03 — la app muestra **«Sin conexión»** y **no** «Sin coincidencias» | JUnit XML de la CI | ✅ |
| **AUT-07** | **Pipeline CI** en **push** y **pull_request** | `.github/workflows/ci-luxray.yml` — jobs `flutter` y `backend`, informe JUnit en «Checks» + artefacto | [ejecuciones reales](#ejecuciones-reales-del-pipeline) | ✅ |
| **AUT-08** | **Ejecución automática con evidencia** y **nuevo push** que re-dispara el CI | 4 ejecuciones reales en GitHub Actions (2 fallidas diagnosticadas, 2 verdes **verificadas**) | [`evidencias/ci/`](evidencias/ci/) — logs y artefactos descargados | ✅ |

---

## Fase 1 · Selección de componentes críticos

| # | Componente | Tipo | Justificación | Defecto/riesgo previo que la automatización previene |
|---|---|---|---|---|
| **C-1** | `LuxrayApi.buscarDetallado()` (`luxraywaer/lib/services/luxray_api.dart`) | **Función crítica del móvil** | Es la función central del wearable: validación de entrada, caché, reintento con *backoff*, respaldo al endpoint legacy y clasificación del estado real | **DEF-18 / D12-05** — un fallo de red se mostraba como «Sin coincidencias». Las pruebas **U-02, U-05 y U-06** fallan contra el código original |
| **C-2** | `GET /api/search` (search-service) y respaldo `GET /api/cursos/buscar` (cursos-service) | **Servicio / endpoint / mecanismo de comunicación** | El wearable consume **dos servicios distintos con dos formas de respuesta diferentes**: es el contrato con más riesgo de deriva | **E-01 (caja roja)** — deriva de contrato enmascarada como «vacío»; **DEF-08** en el lado escritura |
| **C-3** | Alta de usuario en `auth-service` → **PostgreSQL** (`usuarios`, `UNIQUE (email)`) | **Operación de persistencia / almacenamiento** | Es la única escritura con clave natural del sistema y la que sostiene la sesión | **D12-09 / D12-10** — patrón *check-then-act* con ventana de carrera y sin clave de idempotencia |

**Riesgo preventivo principal.** El que **aún no está corregido** y que la automatización impide reintroducir es el de **clasificación del error** (C-1): la versión original colapsaba «sin resultados», «sin red» y «error 500» en la misma pantalla. **U-02, U-05 y U-06 son pruebas de regresión**: fallan contra el código original y pasan contra el corregido.

---

## Fase 2 · Pruebas unitarias — **11 pruebas**

**Fichero:** `luxraywaer/test/practica13_unitarias_test.dart`
**Entorno:** las pruebas apuntan a un **`HttpServer` real** en `localhost`, no a un mock del objeto `http.Client`: se valida el cableado real (cabeceras, query string, cuerpo y códigos de respuesta).

| ID | Caso exigido | Descripción | Resultado |
|---|---|---|---|
| **U-01** | **Entrada válida** | Query «ley de ohm» → estado `ok`, HTTP 200 y datos del contrato | ✔ pasa |
| **U-02** | **Entrada vacía o nula** | `''`, `'   '` y solo caracteres de control → **no se genera ni una petición HTTP** | ✔ pasa |
| **U-03** | **Valores fuera de rango** | Query de 500 caracteres → se **trunca al límite** (`kMaxQuery` = 80) antes de enviar | ✔ pasa |
| **U-04** | **Resultado esperado** | Respaldo al endpoint legacy y parseo fiel (id, `nivelKey` normalizado) | ✔ pasa |
| **U-04b** | *Resultado inesperado* | Envelope con forma distinta → se **clasifica**, no se traga | ✔ pasa |
| **U-05** | **Manejo de excepciones** | Red caída (puerto cerrado) → **no propaga** la excepción; estado `sinConexion` | ✔ pasa |
| **U-05b** | *Excepción por timeout* | Servidor que no responde → el presupuesto global corta la espera | ✔ pasa |
| **U-05c** | *Excepción HTTP 5xx* | Estado `errorServidor`, **distinto** de `sinConexion` | ✔ pasa |
| **U-05d** | *Excepción HTTP 401/403* | Estado `accesoRechazado`, **distinto** de un fallo de red | ✔ pasa |
| **U-06** | **Regresión DEF-18** | Un fallo de red **no** debe decir «sin resultados» | ✔ pasa |
| **U-07** | **Caché** | La segunda búsqueda idéntica se sirve de la caché local | ✔ pasa |

---

## Fase 3 · Pruebas de integración — **11 pruebas** (4 móvil + 7 backend)

### 3.1 Móvil ↔ servicio ↔ presentación — `luxraywaer/test/practica13_integracion_test.dart`

| ID | Componentes que se comunican | Descripción | Resultado |
|---|---|---|---|
| **I-01** | **Aplicación ↔ servicio** | El cliente envía la petición con el contrato exacto (`GET /api/search?q=&page=0&size=10`, `Accept: application/json`) y recibe una respuesta válida | ✔ pasa |
| **I-02** | **Servicio ↔ cliente** | La API devuelve el **código HTTP esperado**: 200 en éxito, **503** ante caída (nunca 200) | ✔ pasa |
| **I-03** | **Aplicación ↔ presentación** | Cuando el servidor no responde, la app muestra **«Sin conexión»** y no «Sin coincidencias» | ✔ pasa |
| **I-04** | **Contrato de petición** | Lo que el cliente envía es exactamente lo que consume el servicio | ✔ pasa |

### 3.2 API → servicio → repositorio → base de datos — `backend/auth-service/src/test/java/com/luxray/auth/controller/RegistroIntegracionTest.java`

| ID | Componentes que se comunican | Descripción | Resultado |
|---|---|---|---|
| **I-05** | **API → servicio → repositorio → BD** | El **registro almacena correctamente la información**: email en minúsculas, rol `USER`, activo, fecha, y **contraseña hasheada** (`$2a$`) | ✔ pasa |
| **I-05b** | **Persistencia** | Un segundo registro con el mismo email **no crea una segunda fila** (verificado en la base de datos, no solo por código HTTP) | ✔ pasa |
| **I-06** | **API ↔ entrada** | Cada situación de entrada devuelve su código: 200 válido, 400 email inválido, 400 password corta, 400 cuerpo incompleto, **400 JSON malformado (no 500)** | ✔ pasa |
| **I-07** | **Autenticación** | `/auth/me` con token válido → 200 y **sin exponer el hash** de la contraseña | ✔ pasa |

**Pruebas deshabilitadas — reproducción de defectos abiertos (specificación ejecutable del cierre):**

| ID | Defecto que documenta | Por qué está deshabilitada |
|---|---|---|
| **I-06b** | **DEF-08** — email duplicado devuelve 400 en lugar de **409 Conflict** | Al corregirlo basta con quitar `@Disabled` |
| **I-07b** | **DEF-02** — cabecera `Authorization` obligatoria → `MissingRequestHeaderException` sin mapear → **500** en lugar de 401 | Ídem |
| **I-06c** | **DEF-03** — UUID malformado → **500** en lugar de 400 | Ídem |

---

## Fase 4 · Integración continua

**Configuración:** `.github/workflows/ci-luxray.yml`

| Aspecto | Valor |
|---|---|
| Disparadores | `push` y `pull_request` sobre **cualquier rama**, más `workflow_dispatch` manual |
| Concurrencia | Un push nuevo cancela la ejecución anterior de la misma rama (`cancel-in-progress: true`) |
| **Job `flutter`** | `ubuntu-latest` · Flutter **3.44.6** pinado (`subosito/flutter-action@v2`) · `flutter pub get` → `flutter analyze` → `flutter test --machine` → conversión a **JUnit XML** → publicación en «Checks» (`dorny/test-reporter@v1`) y artefacto |
| **Job `backend`** | `ubuntu-latest` · **Temurin JDK 21** · **`./mvnw -B test`** (wrapper de Maven **3.9.16** versionado en el repo) → informes Surefire → «Checks» + artefacto |
| Caché | Flutter (pub + artefactos) y Maven |
| Informe JUnit | `practica-13-14/tools/flutter_junit.py` (convierte la traza `--machine` de Flutter, que no emite JUnit de forma nativa) |
| Diagnóstico | Paso previo que imprime `JAVA_HOME`, `java -version` y `./mvnw -v`; paso posterior que, **si el build falla**, vuelca las líneas `[ERROR]` de Maven como anotaciones `::error::` del check |
| Integridad del resultado | **`set -o pipefail`** antes de `./mvnw -B test \| tee mvn.log` — sin esta línea el pipeline puede declarar «verde» con Maven en rojo (ver **E-6**) |

---

## Fase 5 · Validación y análisis de resultados

### 5.1 Ejecuciones reales del pipeline (GitHub Actions)

Todas las ejecuciones están en la pestaña **Actions** del repositorio y sus logs y artefactos están descargados en [`evidencias/ci/`](evidencias/ci/).

| # | Ejecución | Commit | Evento | Resultado | Detalle verificado |
|---|---|---|---|---|---|
| 1 | [`44239354054`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44239354054) | `3b5ae8b` | `push` | ❌ **failure** | Job Flutter ✔ (36 s) · **Job backend ✘** en «Compilar y ejecutar pruebas» (30 s) |
| 2 | [`44241518237`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44241518237) | `cf9d7d9` | `push` | ⚠️ **«verde» falso** | El paso `mvn ... \| tee` devolvía el código de `tee` (0): **Maven estaba en rojo** y el pipeline lo dabă por bueno (ver **E-6**) |
| 3 | [`44347435232`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232) | `999306d` | `push` | ✅ **success real** | `set -o pipefail` + tests de backend versionados. **BUILD SUCCESS** en el log (no enmascarado) · Flutter: *«No issues found!»* + *«185 passed»* |
| 4 | [`44365052085`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365052085) | `e194c7d` | `push` | ✅ **success real** | reedición tras documentar: `BUILD SUCCESS` + *«185 passed»*. Demuestra que **cada push re-dispara el pipeline** |
| 5 | [`44365378884`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365378884) | `e194c7d` | **`pull_request`** ([PR #1](https://github.com/Chekito2720/Luxray_dep/pull/1)) | ✅ **success real** | **`BUILD SUCCESS`** + *«No issues found!»* + *«185 passed»*. Demuestra el **disparador `pull_request`** |

> **AUT-07 verificado con los dos eventos que exige la práctica:** `push` (ejecuciones 1–4) y `pull_request` (ejecución 5, sobre el PR #1).

### 5.2 Resultados verificados de la ejecución 3 (la verde real)

**Job «App móvil (Flutter)» — 83 s en total**

| Paso | Duración | Resultado |
|---|---|---|
| Configurar Flutter 3.44.6 | 25 s | ✔ |
| `flutter pub get` | 1 s | ✔ |
| `flutter analyze` | 8 s | ✔ **«No issues found!»** |
| `flutter test --machine` | 37 s | ✔ **174 pruebas, 0 fallos** |
| Convertir a JUnit | < 1 s | ✔ **185 entradas, 0 fallos, 11 suites** |
| Publicar / archivar | 1 s | ✔ informe en «Checks» + artefacto `flutter-test-results` |

**Job «Backend (Spring Boot)» — 48 s en total**

| Paso | Duración | Resultado |
|---|---|---|
| Configurar Java 21 | 3 s | ✔ |
| Diagnóstico del entorno | 2 s | ✔ `JAVA_HOME`, `java -version`, `./mvnw -v` |
| `./mvnw -B test` | 29 s | ✔ **BUILD SUCCESS** (exit 0 real, sin enmascarar) |
| Publicar / archivar | 2 s | ✔ informe en «Checks» + artefacto `backend-test-results` |

### 5.3 Desglose de pruebas (ejecución real del CI)

| Suite Flutter | Pruebas reales | Fallos | Saltadas |
|---|---|---|---|
| `practica13_unitarias_test.dart` | **11** | 0 | 0 |
| `practica13_integracion_test.dart` | **4** | 0 | 0 |
| `fase2_whitebox_test.dart` | 44 | 0 | 0 |
| `fase2_whitebox_tema_test.dart` | 42 | 0 | 0 |
| `practica12_optimizacion_test.dart` | 25 | 0 | 0 |
| `fase2_whitebox_detalle_test.dart` | 14 | 0 | 0 |
| `practica12_resiliencia_test.dart` | 10 | 0 | 0 |
| `seguridad_test.dart` | 10 | 0 | 0 |
| `widget_test.dart` | 7 | 0 | 0 |
| `fase2_whitebox_navegacion_test.dart` | 5 | 0 | 0 |
| `regresion_def04_navegacion_test.dart` | 2 | 0 | 0 |
| **Total móvil** | **174** | **0** | **0** |

> El XML de JUnit contiene **185 entradas** porque el conversor añade una entrada `loading` por fichero de prueba (11 ficheros): 174 pruebas + 11 = 185.

| Suite backend (versionada) | Pruebas | Fallos | Saltadas |
|---|---|---|---|
| `AuthServiceTest` (unitarias, preexistentes) | 8 | 0 | 0 |
| `AuthControllerIntegrationTest` (integración, preexistente) | 2 | 0 | 0 |
| **`RegistroIntegracionTest` (nueva, 13/14)** | **7** | **0** | **3** (`@Disabled`: DEF-02/03/08) |
| **Total backend en CI** | **17** | **0** | **3** |

> En el árbol de trabajo local existen además **57 pruebas backend** de otras prácticas (`common-lib`, `cursos-service`, `AuthServiceRamasTest`) que **aún no están versionadas**; la build local completa da **74 pruebas, 0 fallos, 5 saltadas**. El CI de esta entrega ejecuta las **17 versionadas**.

### 5.4 Errores encontrados y correcciones realizadas

| # | Error | Causa | Corrección |
|---|---|---|---|
| **E-1** | Las pruebas de red fallaban con *«will actually be made…»* y devolvían 400 | `flutter_test` instala un `HttpOverrides` que **bloquea todo el HTTP real**, y ese override es **global al archivo**: basta un `testWidgets` para que los `test()` dejen de poder abrir sockets | Desactivar el override en `setUp` y restaurarlo en `tearDown` (**ambos ficheros de prueba**) |
| **E-2** | La prueba I-03 **se colgaba 10 minutos** | `testWidgets` ejecuta el cuerpo en una zona **fake-async**: los `Future` reales (una petición HTTP) nunca se completan | Ejecutar la E/S real dentro de `tester.runAsync(...)` |
| **E-3** | Un envelope con `data` como array se clasificaba como **«vacío»** | El respaldo legacy enmascaraba la deriva de contrato: el servidor respondía con forma inesperada y la app lo mostraba como «sin resultados» | Validación de forma en `_buscarEnSearch` y `_buscarEnCursosLegacy` → estado `errorPeticion` (mejora alineada con **E-01** de la caja roja) |
| **E-4** | El convertidor JUnit fallaba con `'list' object has no attribute 'get'` | El reporter `--machine` emite alguna línea que no es un objeto JSON | Filtrar las líneas que no sean `dict` |
| **E-5** | `luxraywaer/` **no estaba versionada** | `.gitignore` raíz excluía la carpeta: sin ella **el pipeline no puede ejecutar las pruebas de la app** | Eliminar la exclusión y excluir solo los artefactos de compilación (`/build/`, `.dart_tool/`, `android/build/`) |
| **E-6** | El pipeline se declaraba **«verde» con Maven en rojo** | `./mvnw … \| tee mvn.log` devuelve el código de salida de `tee` (siempre 0): el fallo de Maven se perdía | **`set -o pipefail`** antes del pipeline + `exit` propagado al paso |
| **E-7** | Un test **ya versionado** estaba en rojo (`AuthServiceTest.usuarioInexistente_ejecutaBcryptDeTodasFormas`) | Exigía el *hash cebo* del login, cuyo código no estaba versionado | Versionar `AuthService.java` con el *hash cebo* |
| **E-8** | La prueba de integración **I-06** fallaba en CI con 500 en vez de 400 | El JSON malformado lo capturaba el manejador genérico de errores (500) | Versionar `GlobalExceptionHandler` con el manejador de `HttpMessageNotReadableException` → **400** |

---

## Fase 6 · Conclusiones

La automatización ha servido, en este proyecto, para **convertir en evidencia reproducible** tres hallazgos que antes solo existían como observaciones:

1. **Un defecto de comportamiento quedó fijado por una prueba.** DEF-18 (un fallo de red se presentaba como «sin resultados») ya no es una opinión: **U-02, U-05 y U-06 fallan contra el código original** y pasan contra el corregido. Eso es lo que evita que una corrección futura lo reintroduzca.
2. **Dos defectos abiertos quedaron convertidos en pruebas ejecutables.** DEF-02, DEF-03 y DEF-08 están documentados como `@Disabled` con su identificador: corregirlos es **activar la prueba**, no reescribirla.
3. **El pipeline destapó dos fallos de infraestructura de prueba** —el bloqueo de HTTP real (`HttpOverrides`) y la zona fake-async de `testWidgets`— que no eran defectos de la aplicación pero que **impedían que las pruebas existieran**. Un pipeline rojo por esas causas desacredita la automatización; por eso se corrigieron antes del primer push.
4. **Y destapó un fallo del propio pipeline** (E-6): la segunda ejecución «verde» era **falsa**. Sin `set -o pipefail`, un `tee` convierte cualquier fallo de Maven en un check verde. La automatización no solo detecta fallos del producto: también **debe detectar sus propios fallos**, o no sirve de nada.

El efecto sobre la calidad tras modificaciones es directo: **cualquier cambio que rompa la búsqueda, la clasificación del error, la persistencia del registro o los códigos HTTP deja de ser «un cambio que a mí me funciona»** y pasa a ser un fallo visible en el commit, con el nombre de la prueba y el archivo implicado. La suite móvil pasó de **159 a 174 pruebas** y el backend versionado de **10 a 17**, todas en verde, con el análisis estático en **cero incidencias** y con una ejecución automática verificada por log y artefacto descargables.

**Limitaciones honestas.** El backend se prueba contra **H2 en memoria**, no contra PostgreSQL real, y la comunicación móvil↔servicio se valida contra un servidor HTTP local que implementa el contrato, no contra el gateway desplegado. Ambas adaptaciones están justificadas en los propios ficheros de prueba; la verificación contra PostgreSQL real se hizo con scripts SQL en la Fase 4 de la Práctica 12 y el extremo real se validó con Newman en la Práctica 11.
