# Informe técnico — Prácticas 13 y 14
## Automatización de pruebas y validación continua de la aplicación móvil LuxRay Wear OS

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Componentes:** aplicación móvil **LuxRay Wear OS** (Flutter 3.44.6 / Dart 3.12.2) · backend de **microservicios Spring Boot 3.4.1** (Java 21) · **PostgreSQL 16** (producción) / **H2** (perfil `test`)
**Rama:** `practica-pruebas` · **Remoto:** `github.com/Chekito2720/Luxray_dep`
**Pipeline:** `.github/workflows/ci-luxray.yml` — GitHub Actions

---

## 1. Componentes críticos seleccionados

| # | Componente | Tipo | Defecto previo que la automatización previene |
|---|---|---|---|
| **C-1** | `LuxrayApi.buscarDetallado()` — búsqueda del wearable | Función crítica del móvil | **DEF-18 / D12-05**: un fallo de red se mostraba como «Sin coincidencias» |
| **C-2** | `GET /api/search` (search-service) y respaldo `GET /api/cursos/buscar` (cursos-service) | Servicio / endpoint / comunicación | **E-01** (deriva de contrato enmascarada) y **DEF-08** (400 en lugar de 409) |
| **C-3** | Alta de usuario en `auth-service` → PostgreSQL (`usuarios`, `UNIQUE (email)`) | Persistencia | **D12-09 / D12-10**: patrón *check-then-act* con ventana de carrera y sin clave de idempotencia |

Se eligieron porque concentran los fallos que el usuario final **sufre** y que las auditorías anteriores documentaron: la búsqueda es la única función del wearable, el contrato entre los dos servicios de búsqueda es el que más riesgo de deriva tiene (dos formas de respuesta distintas) y el alta de usuario es la única escritura con clave natural del sistema.

---

## 2. Pruebas automatizadas (22 pruebas nuevas)

### 2.1 Unitarias — `luxraywaer/test/practica13_unitarias_test.dart` (11)

El cliente bajo prueba se apunta a un **`HttpServer` real** en localhost que implementa el contrato del gateway: no se simula el objeto `http.Client`, de modo que se validan cabeceras, query string, cuerpo y códigos de respuesta de verdad.

| ID | Caso exigido | Resultado |
|---|---|---|
| U-01 | Entrada válida → estado `ok`, HTTP 200, datos del contrato | ✔ |
| U-02 | Entrada vacía (`''`, espacios, solo caracteres de control) → **ni una petición HTTP** | ✔ |
| U-03 | Fuera de rango (500 caracteres) → se trunca al límite de 80 | ✔ |
| U-04 | Resultado esperado → respaldo al endpoint legacy y parseo fiel | ✔ |
| U-04b | Resultado inesperado → envelope con forma distinta se clasifica | ✔ |
| U-05 | Excepción de red → **no propaga**, estado `sinConexion` | ✔ |
| U-05b | Timeout → el presupuesto global corta la espera | ✔ |
| U-05c | HTTP 500 → estado `errorServidor` (no `sinConexion`) | ✔ |
| U-05d | HTTP 401/403 → estado `accesoRechazado` (no fallo de red) | ✔ |
| U-06 | Regresión DEF-18 → un fallo de red no dice «sin resultados» | ✔ |
| U-07 | Caché → la segunda búsqueda idéntica no vuelve a descargar | ✔ |

### 2.2 Integración — 4 (móvil) + 7 (backend)

| ID | Componentes que se comunican | Resultado |
|---|---|---|
| I-01 | Aplicación ↔ servicio: petición con el contrato exacto y respuesta válida | ✔ |
| I-02 | Servicio ↔ cliente: 200 en éxito, **503** ante caída (nunca 200) | ✔ |
| I-03 | Aplicación ↔ presentación: sin respuesta del servidor muestra «Sin conexión» | ✔ |
| I-04 | Contrato de petición: lo que el cliente envía es lo que consume el servicio | ✔ |
| I-05 | API → servicio → repositorio → **base de datos**: email en minúsculas, rol `USER`, activo, fecha y **contraseña hasheada** | ✔ |
| I-05b | Persistencia: un segundo registro con el mismo email **no crea segunda fila** | ✔ |
| I-06 | Códigos HTTP por situación: 200, 400 (email inválido, password corta, cuerpo incompleto) y **400 en JSON malformado, no 500** | ✔ |
| I-07 | Autenticación: `/auth/me` con token → 200 y **sin exponer el hash** | ✔ |
| I-06b · I-07b · I-06c | Reproducción de **DEF-08, DEF-02 y DEF-03** | ⏸ deshabilitadas a propósito |

---

## 3. Pipeline de integración continua

| Aspecto | Configuración |
|---|---|
| Disparadores | `push` y `pull_request` en **cualquier rama**, más ejecución manual (`workflow_dispatch`) |
| Concurrencia | Un push nuevo cancela la ejecución anterior de la misma rama |
| Job móvil | `ubuntu-latest` · Flutter **3.44.6** pinado · `pub get` → `analyze` → `test --machine` → JUnit XML → «Checks» + artefacto |
| Job backend | `ubuntu-latest` · **Temurin 21** · `./mvnw -B test` (wrapper **Maven 3.9.16** versionado) → Surefire → «Checks» + artefacto |
| Caché | Flutter (pub + artefactos) y Maven |
| Informe | `practica-13-14/tools/flutter_junit.py` (Flutter no emite JUnit de forma nativa) |
| Diagnóstico | Imprime `JAVA_HOME`, `java -version` y `./mvnw -v` antes de construir; si el build falla, vuelca las líneas `[ERROR]` de Maven como anotaciones `::error::` del check |
| Integridad | **`set -o pipefail`** antes de `./mvnw -B test \| tee mvn.log`, sin lo cual un fallo de Maven produce un check «verde» falso |

---

## 4. Resultados de la ejecución (medidos, no estimados)

### 4.1 Ejecución automática verificada — [run `44347435232`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232) (evento `push`, commit `999306d`)

| Métrica | Valor |
|---|---|
| Resultado del pipeline | ✅ **success** (ambos jobs) |
| Pruebas móviles | **174 ejecutadas · 0 fallos** |
| Entradas JUnit generadas | **185** (174 pruebas + 1 entrada `loading` por fichero, 11 ficheros) · 11 suites · 0 fallos |
| Pruebas backend (versionadas) | **17 ejecutadas · 0 fallos · 3 saltadas** · `BUILD SUCCESS` en el log |
| Análisis estático | **0 incidencias** («No issues found!») |
| Duración job Flutter | **83 s** (análisis 8 s · pruebas 37 s) |
| Duración job backend | **48 s** (build y pruebas 29 s) |
| Duración total del pipeline | **≈ 90 s** |
| Artefactos | `flutter-test-results` (16 KB) y `backend-test-results` (30 KB) |
| Evidencia descargada | `practica-13-14/evidencias/ci/` — logs de ambos jobs + artefactos con los XML de Surefire y JUnit |

> En el árbol de trabajo local existen **57 pruebas backend adicionales** de otras prácticas que aún no están versionadas; la build local completa da **74 pruebas, 0 fallos, 5 saltadas**. El CI de esta entrega ejecuta las **17 versionadas**.

### 4.2 Historial de ejecuciones del pipeline

| # | Ejecución | Resultado | Qué aportado |
|---|---|---|---|
| 1 | [`44239354054`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44239354054) | ❌ failure | Fallo **real** del job backend (30 s) |
| 2 | [`44241518237`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44241518237) | ⚠️ «verde» falso | El `tee` enmascaraba el fallo de Maven |
| 3 | [`44347435232`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232) | ✅ success real | Con `pipefail` y los tests de backend versionados |
| 4 | _reedición de documentación_ | ✅ success | Nuevo `push` → **el pipeline se re-dispara solo** |

### 4.3 Errores encontrados y correcciones

| # | Error | Causa | Corrección |
|---|---|---|---|
| E-1 | Las pruebas de red devolvían 400 con *«will actually be made…»* | `flutter_test` instala un `HttpOverrides` **global** que bloquea el HTTP real | Desactivarlo en `setUp` y restaurarlo en `tearDown` |
| E-2 | La prueba I-03 **se colgaba** | `testWidgets` usa una zona **fake-async**: los `Future` de red real nunca se completan | `tester.runAsync(...)` para la E/S real |
| E-3 | Envelope con `data` como array → «vacío» | El respaldo legacy enmascaraba la deriva de contrato | Validación de forma → `errorPeticion` (mejora **E-01** de la caja roja) |
| E-4 | El convertidor JUnit fallaba | `--machine` emite alguna línea que no es objeto JSON | Filtrar las líneas no `dict` |
| E-5 | `luxraywaer/` **no estaba versionada** | `.gitignore` raíz la excluía: el CI no podía probar la app | Versionar la app; excluir solo artefactos de build |
| E-6 | El pipeline «verde» con Maven en rojo | `mvn … \| tee` devuelve el código de `tee` (0) | **`set -o pipefail`** |
| E-7 | Un test **ya versionado** estaba en rojo | Exigía el *hash cebo* del login, no versionado | Versionar `AuthService.java` |
| E-8 | I-06 recibía 500 en vez de 400 con JSON malformado | Lo capturaba el manejador genérico de errores | Versionar el manejador de `HttpMessageNotReadableException` |

---

## 5. Conclusiones (Fase 6)

La automatización ha convertido cuatro hallazgos que antes eran observaciones en **evidencia reproducible**:

1. **Un defecto de comportamiento quedó fijado por una prueba.** DEF-18 (un fallo de red presentado como «sin resultados») ya no es una opinión: **U-02, U-05 y U-06 fallan contra el código original** y pasan contra el corregido. Eso es lo que impide reintroducirlo.
2. **Los defectos abiertos quedaron como pruebas ejecutables.** DEF-02, DEF-03 y DEF-08 están documentados como `@Disabled` con su identificador; corregirlos es **activar la prueba**, no reescribirla.
3. **El pipeline destapó dos fallos de infraestructura de prueba** —el bloqueo de HTTP real (`HttpOverrides`) y la zona fake-async de `testWidgets`— que no eran defectos de la aplicación pero que **impedían que las pruebas existieran**.
4. **Y destapó un fallo del propio pipeline** (E-6): la segunda ejecución «verde» era **falsa**. Sin `set -o pipefail`, un `tee` convierte cualquier fallo de Maven en un check verde. La automatización no solo detecta fallos del producto: también **debe detectar sus propios fallos**, o no sirve de nada.

El efecto sobre la calidad tras modificaciones es directo: un cambio que rompa la búsqueda, la clasificación del error, la persistencia del registro o los códigos HTTP deja de ser «un cambio que a mí me funciona» y pasa a ser un fallo visible en el commit, con el nombre de la prueba y el archivo implicado. La suite móvil pasó de **159 a 174 pruebas** y el backend versionado de **10 a 17**, todas en verde, con el análisis estático en **cero incidencias** y con una ejecución automática verificada por log y artefactos descargables del propio GitHub Actions.

**Limitaciones honestas.** El backend se prueba contra **H2 en memoria**, no contra PostgreSQL real, y la comunicación móvil↔servicio se valida contra un servidor HTTP local que implementa el contrato, no contra el gateway desplegado. Ambas adaptaciones están justificadas en los propios ficheros de prueba; la verificación contra PostgreSQL real se hizo con scripts SQL en la Fase 4 de la Práctica 12 y el extremo real se validó con Newman en la Práctica 11.
