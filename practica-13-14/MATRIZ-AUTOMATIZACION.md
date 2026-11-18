# Prácticas 13 y 14 — Automatización de pruebas y validación continua
## Matriz de automatización

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Componentes:** aplicación móvil **LuxRay Wear OS** (Flutter 3.44.6 / Dart 3.12.2) · backend de **6 microservicios Spring Boot 3.4.1** (Java 21) · base de datos **PostgreSQL 16**
**Rama evaluada:** `practica-pruebas` · **Remoto:** `github.com/Chekito2720/Luxray_dep`
**Fecha:** 17 de noviembre de 2026
**Pipeline:** `.github/workflows/ci-luxray.yml` (GitHub Actions)

---

## Fase 1 · Selección de componentes críticos

| # | Componente | Tipo | Justificación | Defecto/riesgo previo que la automatización previene |
|---|---|---|---|---|
| **C-1** | `LuxrayApi.buscarDetallado()` (`luxraywaer/lib/services/luxray_api.dart`) | **Función crítica del móvil** | Es la función central del wearable: validación de entrada, caché, reintento con backoff, respaldo al endpoint legacy y clasificación del estado real | **DEF-18 / D12-05** — antes, un fallo de red se mostraba como «Sin coincidencias». Las pruebas **U-02, U-05, U-06** fallan contra el código original |
| **C-2** | `GET /api/search` (search-service) y respaldo `GET /api/cursos/buscar` (cursos-service) | **Servicio / endpoint / mecanismo de comunicación** | El wearable consume ambos; son dos servicios distintos con **dos formas de respuesta diferentes** | **E-01 (caja roja)** — deriva de contrato enmascarada como «vacío»; **DEF-08** en el lado escritura |
| **C-3** | Alta de usuario en `auth-service` → **PostgreSQL** (`usuarios`, `UNIQUE (email)`) y token en `localStorage` del PWA | **Operación de persistencia / almacenamiento** | Es la única escritura con clave natural del sistema y la que sostiene la sesión | **D12-09 / D12-10** — el patrón *check-then-act* deja ventana de carrera y **no hay clave de idempotencia** |

**Riesgo preventivo principal.** De los tres, el que **aún no está corregido** y que una prueba automatizada impide que se reintroduzca es el de **clasificación del error** (C-1): la versión original colapsaba «sin resultados», «sin red» y «error 500» en la misma pantalla. Las pruebas U-02 y U-05 son **pruebas de regresión**: fallan contra el código original y pasan contra el corregido.

---

## Fase 2 · Pruebas unitarias (≥ 5)

**Fichero:** `luxraywaer/test/practica13_unitarias_test.dart` — **12 pruebas** en 8 grupos.
**Cliente real:** las pruebas apuntan a un `HttpServer` **real** en localhost, no a un mock del objeto `http.Client`.

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

## Fase 3 · Pruebas de integración (≥ 3)

**Ficheros:**
* `luxraywaer/test/practica13_integracion_test.dart` — **4 pruebas** (móvil ↔ servicio).
* `backend/auth-service/src/test/java/com/luxray/auth/controller/RegistroIntegracionTest.java` — **7 pruebas** (API ↔ base de datos), 3 de ellas deshabilitadas a propósito por ser reproducción de defectos abiertos.

| ID | Componentes que se comunican | Descripción | Resultado |
|---|---|---|---|
| **I-01** | **Aplicación ↔ servicio** | El cliente envía la petición con el contrato exacto (`GET /api/search?q=&page=0&size=10`, `Accept: application/json`) y recibe una respuesta válida | ✔ pasa |
| **I-02** | **Servicio ↔ cliente** | La API devuelve el **código HTTP esperado**: 200 en éxito, **503** ante caída (nunca 200) | ✔ pasa |
| **I-03** | **Aplicación ↔ presentación** | Cuando el servidor no responde, la app muestra **«Sin conexión»** y no «Sin coincidencias» | ✔ pasa |
| **I-04** | **Contrato de petición** | Lo que el cliente envía es exactamente lo que consume el servicio | ✔ pasa |
| **I-05** | **API → servicio → repositorio → BD** | El **registro almacena correctamente la información**: email en minúsculas, rol `USER`, activo, fecha, y **contraseña hasheada** (`$2a$`) | ✔ pasa |
| **I-05b** | **Persistencia** | Un segundo registro con el mismo email **no crea una segunda fila** (verificado en base de datos, no solo por código HTTP) | ✔ pasa |
| **I-06** | **API ↔ entrada** | Cada situación de entrada devuelve su código: 200 válido, 400 email inválido, 400 password corta, 400 cuerpo incompleto, **400 JSON malformado (no 500)** | ✔ pasa |
| **I-07** | **Autenticación** | `/auth/me` con token válido → 200 y **sin exponer el hash** de la contraseña | ✔ pasa |

**Pruebas deshabilitadas (reproducción de defectos abiertos):**

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
| Concurrencia | Un push nuevo cancela la ejecución anterior de la misma rama |
| **Job `flutter`** | `ubuntu-latest` · Flutter **3.44.6** (pinado) · `flutter pub get` → `flutter analyze` → `flutter test --machine` → conversión a **JUnit XML** → publicación en «Checks» y archivo adjunto |
| **Job `backend`** | `ubuntu-latest` · **Temurin JDK 21** · `mvn -B test` → informes Surefire → publicación en «Checks» y archivo adjunto |
| Caché | Flutter (pub + artefactos) y Maven |
| Informe JUnit | `practica-13-14/tools/flutter_junit.py` (convierte la traza `--machine` de Flutter, que no emite JUnit de forma nativa) |

---

## Fase 5 · Validación y análisis de resultados

### Ejecución local (equivalente exacto del pipeline)

| Paso | Comando | Resultado |
|---|---|---|
| Dependencias | `flutter pub get` | ✔ |
| Análisis estático | `flutter analyze` | **0 incidencias** |
| Pruebas móviles | `flutter test --machine` | **174 pruebas, 0 fallos** · salida **exit 0** |
| Conversión JUnit | `flutter_junit.py` | **185 entradas, 0 fallos, 11 suites** |
| Backend | `mvn -B test` | **74 pruebas, 0 fallos, 3 saltadas** · **BUILD SUCCESS** |

### Desglose por suite

| Suite | Pruebas | Fallos | Saltadas |
|---|---|---|---|
| `practica13_unitarias_test.dart` | 12 | 0 | 0 |
| `practica13_integracion_test.dart` | 5 | 0 | 0 |
| `fase2_whitebox_test.dart` | 45 | 0 | 0 |
| `fase2_whitebox_tema_test.dart` | 43 | 0 | 0 |
| `practica12_optimizacion_test.dart` | 26 | 0 | 0 |
| `fase2_whitebox_detalle_test.dart` | 15 | 0 | 0 |
| `practica12_resiliencia_test.dart` | 11 | 0 | 0 |
| `regresion_def04_navegacion_test.dart` | 3 | 0 | 0 |
| `fase2_whitebox_navegacion_test.dart` | 6 | 0 | 0 |
| `widget_test.dart` | 8 | 0 | 0 |
| `seguridad_test.dart` | 11 | 0 | 0 |
| **Total móvil** | **185** | **0** | **0** |

| Suite backend | Pruebas | Fallos | Saltadas |
|---|---|---|---|
| `common-lib` (3 clases) | 20 | 0 | 2 |
| `auth-service` (4 clases) | 40 | 0 | 3 |
| `cursos-service` (1 clase) | 14 | 0 | 0 |
| **Total backend** | **74** | **0** | **5** |

### Errores encontrados y correcciones realizadas

| # | Error | Causa | Corrección |
|---|---|---|---|
| **E-1** | Las pruebas de red fallaban con *«will actually be made…»* y devolvían 400 | `flutter_test` instala un `HttpOverrides` que **bloquea todo el HTTP real**, y ese override es **global al archivo**: basta un `testWidgets` para que los `test()` dejen de poder abrir sockets | Desactivar el override en `setUp` y restaurarlo en `tearDown` (**ambos ficheros de prueba**) |
| **E-2** | La prueba I-03 **se colgaba 10 minutos** | `testWidgets` ejecuta el cuerpo en una zona **fake-async**: los `Future` reales (una petición HTTP) nunca se completan | Ejecutar la E/S real dentro de `tester.runAsync(...)` |
| **E-3** | Un envelope con `data` como array se clasificaba como **«vacío»** | El respaldo legacy enmascaraba la deriva de contrato: el servidor respondía con forma inesperada y la app lo mostraba como «sin resultados» | Añadir validación de forma en `_buscarEnSearch` y `_buscarEnCursosLegacy` → estado `errorPeticion` (mejora alineada con **E-01** de la caja roja) |
| **E-4** | El convertidor JUnit fallaba con `'list' object has no attribute 'get'` | El reporter `--machine` emite alguna línea que no es un objeto JSON | Filtrar líneas que no sean `dict` |
| **E-5** | `luxraywaer/` **no estaba versionada** | `.gitignore` raíz excluía la carpeta: sin ella **el pipeline no puede ejecutar las pruebas de la app** | Eliminar la exclusión y excluir solo los artefactos de compilación (`/build/`, `.dart_tool/`, `android/build/`) |

---

## Fase 6 · Conclusiones

La automatización ha servido, en este proyecto, para **convertir en evidencia reproducible** tres hallazgos que antes solo existían como observaciones:

1. **Un defecto de comportamiento quedó fijado por una prueba.** DEF-18 (un fallo de red se presentaba como «sin resultados») no es ya una opinión: las pruebas **U-02, U-05 y U-06 fallan contra el código original** y pasan contra el corregido. Eso es lo que evita que una corrección futura lo reintroduzca.
2. **Dos defectos abiertos quedaron convertidos en pruebas ejecutables.** DEF-02, DEF-03 y DEF-08 están documentados como `@Disabled` con su identificador: cuando se corrijan, activar la prueba es el acto de cierre. Sin automatización, esos defectos dependen de que alguien los recuerde.
3. **El pipeline ha destapado dos fallos de infraestructura de prueba** (el bloqueo de HTTP real y la zona fake-async) que no eran defectos de la aplicación pero que **impiden que las pruebas existan**. Un pipeline rojo por estas causas desacredita la automatización; por eso se corrigieron antes del primer push.

El efecto sobre la calidad tras modificaciones es directo: **cualquier cambio que rompa la búsqueda, la clasificación del error, la persistencia del registro o los códigos HTTP deja de ser un cambio «que a mí me funciona»** y pasa a ser un fallo visible en el commit, con el nombre de la prueba y el archivo implicado. La suite pasó de 159 a **185 pruebas móviles** y de 40 a **74 backend**, todas en verde, con el análisis estático en cero incidencias.

Como limitación honesta: el backend se prueba contra **H2 en memoria**, no contra PostgreSQL real, y la comunicación móvil↔servicio se valida contra un servidor HTTP local que implementa el contrato, no contra el gateway desplegado. Ambas adaptaciones están justificadas en los propios ficheros de prueba; la verificación contra PostgreSQL real se hizo con scripts SQL en la Fase 4 de la Práctica 12, y el extremo real se validó con Newman en la Práctica 11.
