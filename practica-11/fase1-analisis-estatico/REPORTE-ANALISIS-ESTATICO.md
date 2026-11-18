# Fase 1 — Inspección y análisis estático

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Rama evaluada:** `practica-pruebas` · commit `2d60917`
**Fecha:** 2026-11-11
**Alcance analizado:**

| Componente | Ruta | Tecnología | Volumen |
|---|---|---|---|
| Aplicación móvil | `luxraywaer/` | Flutter 3.44.6 / Dart 3.12.2 (Wear OS, Android) | 10 ficheros Dart, ~1 581 líneas |
| Sitio web | `frontend-web/` | Angular 21.2.18 / TypeScript 5.9 (PWA + Capacitor) | 21 páginas, 8 servicios |
| Backend | `backend/` | Spring Boot 3.4.1 / Java 21, Spring Cloud 2024.0.1 | 6 microservicios + `common-lib` |
| Base de datos | `backend/*/src/main/resources/db/migration/` | PostgreSQL 16 + Flyway 10.20 | 17 migraciones |

---

## 1. Herramientas de análisis estático ejecutadas

| # | Herramienta | Comando | Resultado | Evidencia |
|---|---|---|---|---|
| 1 | **Analizador de Dart** (`dart analyze` vía Flutter) | `flutter analyze` | **0 incidencias** en 52,1 s | `evidencias/fase1_flutter_analyze.txt` |
| 2 | **Compilador Java + Maven** | `mvn clean compile -DskipTests` | **BUILD SUCCESS**, 0 errores, 0 avisos | `evidencias/fase1_mvn_compile.txt` |
| 3 | **Compilador Angular** (ngc + ESLint de plantilla) | `npm run build` | Compila; **9 avisos** | `evidencias/fase1_ng_build.txt` |
| 4 | **Conjunto de pruebas del frontend** | `npx ng test --no-watch` | **44/45** — 1 prueba obsoleta | `evidencias/fase1_ng_test.txt` |
| 5 | **Revisión manual** de los 7 puntos obligatorios | — | **9 hallazgos** | este informe |

> Nota: el analizador de Dart no emite advertencias de *análisis estático
> avanzado* (complejidad, código muerto,vias de flujo ascendentes). Esas carencias se
> compensaron con (a) la revisión manual sobre el código y (b) el arnés de
> mutación de la Fase 2, que sí detecta código inaccesible desde el punto de
> vista de la lógica.

---

## 2. Módulos principales de la aplicación

### 2.1 Aplicación móvil (`luxraywaer`)

| Módulo | Fichero | Responsabilidad |
|---|---|---|
| Raíz y navegación | `lib/main.dart` | `MaterialApp` + `PageView` de 3 páginas (inicio / resultados / detalle) con `NeverScrollableScrollPhysics`; estado en `_RootState` |
| Modelos | `lib/models/busqueda.dart` | Espejo tolerante del DTO `SearchResponse` del backend; 6 clases, parseo *null-safe* |
| Servicios | `lib/services/luxray_api.dart` | **Única** capa de red: `LuxrayApi.buscar()` con endpoint unificado + *fallback* legacy |
| Presentación | `lib/screens/*.dart` (3) | Home (búsqueda + sugerencias), Resultados (agrupación por tipo), Detalle (concepto/FAQ) |
| Tema | `lib/theme/wear_theme.dart` | Paleta, radios, `ThemeData` Material 3 oscuro, badges por tipo |
| Widgets | `lib/widgets/circular_safe_area.dart` | `CircularSafeArea`, `WearScaffold`, `isWearRound` |

**Funciones críticas de acceso a la base de datos (backend):**
`UsuarioRepository`, `CursoRepository`, `LeccionRepository`,
`InscripcionRepository`, `ProgresoLeccionRepository`, `EvidenciaPracticaRepository`,
`AnalyticsRepository` (todos `JpaRepository`).

**Funciones críticas de comunicación con el servicio:**
`LuxrayApi._buscarEnSearch` (GET `/search`),
`LuxrayApi._buscarEnCursosLegacy` (GET `/cursos/buscar`),
`WebClient` en `DashboardService` (3 llamadas encadenadas),
`StoragePresignService` (S3/MinIO multipart).

### 2.2 Sitio web (`frontend-web`)

| Módulo | Contenido |
|---|---|
| Servicios | `auth`, `cursos`, `analytics`, `admin`, `admin-dashboard`, `busqueda`, `evidencias` |
| Guards | `authGuard`, `guestGuard`, `adminGuard`, `profesorGuard` |
| Interceptor | `authInterceptor` (inyección del `Bearer`, logout + redirección en 401) |
| Configuración | `src/environments/environment.ts` / `.prod.ts` (generado por `scripts/set-env.js`) |

### 2.3 Backend (`backend/`)

| Módulo | Puerto | Paquete | Responsabilidad |
|---|---|---|---|
| `common-lib` | — | `com.luxray.common` | JWT (`JwtTokenProvider`, `JwtAuthenticationFilter`), `ApiResponse`/`ApiError`, `GlobalExceptionHandler` |
| `api-gateway` | 8080 | `com.luxray.gateway` | Enrutamiento + CORS + `X-Request-Id`; **sin** validación de JWT |
| `discovery-service` | 8761 | `com.luxray.discovery` | Eureka |
| `auth-service` | 8081 | `com.luxray.auth` | Registro, login, JWT, CRUD administrativo de usuarios |
| `cursos-service` | 8082 | `com.luxray.cursos` | Catálogo, lecciones, inscripciones, progreso, evidencias (S3 multipart) |
| `analytics-service` | 8083 | `com.luxray.analytics` | Dashboard (agrega auth + cursos vía `WebClient`) |
| `search-service` | 8084 | `com.luxray.search` | Búsqueda unificada en Elasticsearch (4 índices) |

---

## 3. Puntos de revisión obligatorios

### 3.1 Métodos sin manejo de excepciones — **4 hallazgos**

| # | Ubicación | Análisis |
|---|---|---|
| **S-1** | `luxraywaer/lib/services/luxray_api.dart:93` y `:115` | `catch (_)` **sin variable ni registro**: absorbe *todas* las excepciones, incluidas las de programación (`TypeError`, `NullPointerException`). Es un **fail-soft deliberado** (el wearable debe seguir vivo), pero oculta bugs: un `NullPointerException` se presentaría como "sin resultados". **Medido**: la app nunca muestra un falso éxito (§6.5 del informe). Riesgo bajo, mantenibilidad media. |
| **S-2** | `analytics-service/DashboardService.dashboard()` | **3 llamadas `WebClient` encadenadas sin `try/catch`**: si `cursos-service` no responde, la excepción sale del controlador y `GlobalExceptionHandler` la traduce a **500 genérico**. No se distingue "servicio caído" de "error de negocio". |
| **S-3** | `cursos-service/StoragePresignService` (S3 multipart) | Operaciones sobre AWS SDK **sin manejador de excepciones**: un `NoSuchUploadException` o un fallo de red del bucket se propaga como 500. |
| **S-4** | `frontend-web/src/app/services/busqueda.service.ts` | `catchError(() => of(this.empty(query)))`: degrada a vacío **sin registrar la causa**; combinado con DEF-18, el usuario no puede distinguir "sin resultados" de "red caída". |

**Hallazgo positivo:** los servicios `auth-service` y `cursos-service`
sí tienen manejo de excepciones **de negocio** bien estructurado
(`BusinessException`, `ResourceNotFoundException`) con mapeo centralizado en
`GlobalExceptionHandler`, y `EvidenciasService` valida exhaustivamente las
entradas antes de tocar S3 (verificado por sus 14 pruebas unitarias).

### 3.2 Credenciales o claves expuestas — **5 hallazgos**

| Secreto | Ubicación | Observación |
|---|---|---|
| `luxray_secret_2026` | `auth-service`, `cursos-service`, `analytics-service` → `application.yml` | Contraseña de PostgreSQL **por defecto** en el código fuente |
| `luxray-jwt-super-secreto-para-desarrollo-min-32caracteres` | `common-lib` → `JwtTokenProvider` | Secreto JWT por defecto. El propio código **detecta y avisa** por log si se usa (`SECRETOS_INSEGUROS`), pero el valor sigue siendo válido |
| `luxray-jwt-secret-para-docker-compose-no-usar-en-produccion` | `docker-compose.yml` | Secreto del stack de desarrollo |
| `S3RVER / S3RVER` | `docker-compose.yml` | Credenciales del emulador S3 |
| `admin@luxray.com` + hash BCrypt de demo | `auth-service/.../V3__seed_admin_and_fix_user.sql` | Cuenta de administrador con contraseña **conocida** que se crea en cualquier despliegue |

→ **DEF-15** (severidad alta).

### 3.3 Validaciones duplicadas o inexistentes — **2 hallazgos**

| # | Ubicación | Análisis |
|---|---|---|
| **V-1** | `luxray_api.dart:145` (`TextField.maxLength: 80`) **y** `luxray_api.dart:52` (`query.length < 2`) | **Duplicación correcta**: la UI limita la longitud y el servicio vuelve a validar. Es defensa en profundidad, no un defecto. La prueba `M3-LIM` verifica que la truncación ocurre **en el servicio** aunque la UI ya la aplique. |
| **V-2** | `search-service/SearchController` (`q` sin límite) | **Validación inexistente**: el backend acepta `q` de **500 caracteres** (medido: `GET /search?q=` + 500 chars → 200 con 646 B). La aplicación móvil trunca a 80, pero **cualquier otro cliente** puede enviar una carga arbitraria. Falta un `@Size(max = …)` en el DTO de entrada. |

Además, la validación del backend **no distingue forma de semántica**
(`@Valid` → 400 con `fieldErrors` para todo), lo que origina DEF-08/DEF-09.

### 3.4 Código inaccesible o redundante — **6 hallazgos**

| # | Elemento | Ubicación | Análisis |
|---|---|---|---|
| **R-1** | `isWearRound(context)` | `widgets/circular_safe_area.dart:70` | Definida pero **nunca invocada** (grep: 0 usos). Ahora sí está cubierta por pruebas, pero sigue sin uso en producción. |
| **R-2** | `class LuxSnippet` | `theme/wear_theme.dart:144` | Widget completo (22 líneas) **nunca instanciado**: `resultados_screen.dart` define su propio `_Snippet` privado, duplicando la lógica de truncado. |
| **R-3** | `CircularSafeArea.keepBottomGap` | `widgets/circular_safe_area.dart:25,33` | Parámetro sin uso. |
| **R-4** | `LuxColors.navbarGradient` / `LuxRadii.pill` | `theme/wear_theme.dart` | Constantes de color/radio sin referencias. |
| **R-5** | `AnalyticsRepository` / `AnalyticsSnapshot` | `analytics-service` | Repositorio y entidad **nunca invocados**: la tabla `analytics_snapshots` está **siempre vacía** (verificado: `count(*) = 0`). → **DEF-17** |
| **R-6** | Refresh token sin endpoint | `auth-service/AuthService.generateTokens()` | Se emite un `refreshToken` (claim `type=refresh`) y **no existe ningún endpoint `POST /auth/refresh`**; además comparte la expiración del access token (1440 min) pese al comentario "Refresh token más largo (7 días)". El frontend lo persiste (`luxray_token`) pero no lo usa: **el refresh es código muerto**. |

**Código muerto adicional en el backend:** `AuthResponse.java` importa
`@Email` y `@NotBlank` sin usarlas.

### 3.5 Consultas construidas con datos sin validar — **0 hallazgos**

Se revisaron todas las anotaciones `@Query` de los repositorios:

```java
@Query("SELECT c FROM Curso c WHERE c.publicado = true "
     + "AND (:nivel IS NULL OR c.nivel = :nivel) "
     + "AND (LOWER(c.titulo) LIKE %:q% OR LOWER(c.descripcion) LIKE %:q%)")
```

Todas usan **parámetros vinculados** (named parameters `:nivel`, `:q`), que
Hibernate escapa como *bind parameters*. **No hay concatenación de valores de
entrada en SQL** en ningún punto del backend. La búsqueda por texto del
`cursos-service` es un `LIKE '%q%'` con comodín inicial → **escaneo
secuencial** (no inyección, sí ineficiencia; ver §5 de la Fase 4).

### 3.6 Operaciones de red ejecutadas bloqueando el hilo principal — **0 hallazgos / 1 matiz**

* **Flutter/Dart**: `dart:io` usa **E/S asíncrona real** (no bloquea el hilo
  de UI). La llamada se dispara desde un callback de usuario
  (`HomeScreen._buscar` → `onBuscar`), **nunca desde `build`/`initState`**, por
  lo que no hay riesgo de congelar la interfaz. El estado de carga se gestiona
  con `_cargando` → `CircularProgressIndicator`.
* **Angular**: `HttpClient` sobre `HttpClient` (XHR) es asíncrono;
  `busqueda.service.ts` aplica `debounceTime(220)` + `distinctUntilChanged()` +
  `switchMap()` para no disparar peticiones por pulsación.
* **Spring Boot**: el `WebClient` de `DashboardService` es reactivo y no
  bloquea; pero el `HttpClient` de `EvidenciasService` para las subidas
  multipart **sí** es bloqueante — se ejecuta en el hilo de la petición
  (configurado con `spring.threads.virtual.enabled`, no verificado en esta
  auditoría).
* **Matiz de riesgo real**: la lógica de negocio de `main.dart` usa
  `setState` tras un `await` **sin comprobar `mounted`**. Si el widget se
  desmonta durante la petición, `setState` se invoca sobre un `State` ya
  liberado → excepción *"setState() called after dispose()". En esta app el
  `State` raíz vive toda la vida de la app, por lo que **no se ha
  reproducido**, pero el patrón es frágil.

### 3.7 Recursos que no se liberan correctamente — **1 hallazgo**

| # | Recurso | Estado |
|---|---|---|
| **C-1** | `luxraywaer`: `http.Client` de `LuxrayApi` | **Correcto**: `close()` en `_RootState.dispose()` y verificado por la prueba `M3-EXC · el cliente se cierra sin dejar conexiones abiertas`. |
| **C-2** | `cursos-service/StoragePresignService`: cliente S3 del AWS SDK | El `S3Client` se construye una vez y **no se cierra**. El SDK gestiona su propio *pool* de conexiones y no exige `close()` explícito, pero en despliegues con *scale-down* los sockets quedan abiertos hasta el GC. |
| **C-3** | `common-lib/JwtAuthenticationFilter` | Sin recursos que liberar. |
| **C-4** | `frontend-web`: `auth.service.ts` | `localStorage` (`luxray_token`, `luxray_usuario`); `logout()` los borra correctamente. Sin fugas. |

---

## 4. Advertencias del compilador de Angular

9 avisos, ninguno bloqueante (`evidencias/fase1_ng_build.txt`):

| Código | Ubicación | Análisis |
|---|---|---|
| `NG8107` ×2 | `pages/cursos/cursos.html:56` y similar | *"The left side of this optional chain operation does not include 'null' or 'undefined'"* — se usa `?.` donde el tipo no lo permite: **código muerto defensivo**, indica que el modelo garantiza la no nulidad. Inofensivo, pero denota types defensivos que el compilador puede rechazar. |
| `NG8113` | `pages/expediente/expediente.ts:24` | `RouterLink` importado pero **no usado en la plantilla** → import sobrante. |
| Presupuesto SCSS | `pages/detalle-curso/detalle-curso.scss` | 10,73 kB > 10 kB presupuestados (+731 B). Freno de crecimiento del CSS. |

> Ninguno de los 9 avisos afecta a la corrección funcional; se documentan
> como deuda técnica de baja prioridad.

---

## 5. Dependencias desactualizadas

| Dependencia | Versión declarada | Observación |
|---|---|---|
| `spring-boot` | 3.4.1 | Rama 3.4.x, sin parches de seguridad publicados en la serie 3.4 (la 3.5.x es la successors). **Actualizable.** |
| `spring-cloud` | 2024.0.1 | Coherente con Spring Boot 3.4.x. |
| `jjwt` | 0.12.6 | Actual. |
| `postgresql` (driver) | 42.7.4 | Actual (la serie 42.7.x es la vigente para PG16). |
| `flyway` | 10.20.0 | Actual. |
| `lombok` | 1.18.32 | Actual (1.18.30+ soporta Java 21). |
| `aws-sdk-java-v2` | 2.31.16 | **Desactualizado**: la serie 2.31 es de finales de 2023; la actual es 2.3x/2.4x. Se actualiza importando el BOM de AWS en el POM raíz (hoy se importa de forma local en `cursos-service`). |
| `search-service`: `spring-cloud-starter-netflix-eureka-client` | **4.1.0** (fijada a mano) | **Inconsistente**: el BOM `spring-cloud-dependencies` 2024.0.1 gestiona esa versión. La fijación manual a 4.1.0 es undesacoplamiento divergente → **riesgo de incompatibilidad** y de que Maven no resuelva de forma reproducible. |
| Angular | 21.2.18 | Actual. |
| TypeScript | ~5.9.2 | Actual. |
| PrimeNG / PrimeFlex / PrimeIcons | 21.2.9 / 8.0.0 / 7.0.0 | Alineados con Angular 21. |
| `express` | 5.1.0 | Actual (SSR). |
| Dart / Flutter | 3.12.2 / 3.44.6 | Actuales. |
| `http` (Dart) | 1.6.0 | Actual. |
| `flutter_lints` | 6.0.0 | Actual. |

**Transversal:** no existe ninguna configuración de Dependabot/Renovate ni
fichero `.snyk`; `npm audit` y `mvn dependency-check` no están integrados en
ningún flujo.

---

## 6. Otros hallazgos del análisis

| # | Hallazgo | Severidad |
|---|---|---|
| **A-1** | **El gateway no valida el JWT.** `api-gateway` no depende de `common-lib`: reenvía la cabecera `Authorization` sin inspeccionarla. Cada servicio valida por su cuenta, lo que es válido, pero el gateway es el punto natural para el *rate limiting* o el *blacklisting* de tokens revocados: hoy no existe ninguna de las dos cosas. | Media |
| **A-2** | **`spring.cloud.gateway.discovery.locator.enabled=true`** en el gateway: cualquier servicio registrado en Eureka queda **enrutable por su nombre** (`/{serviceId}/**`). La exposición real medida fue `404` (el prefijo `/api` no casa con el patrón), pero el mecanismo sigue activo y es una superficie de ataque innecesaria. | Media |
| **A-3** | `backend/api-gateway/src/main/resources/application.yml`: CORS con origen literal `http://*:4200` y `allowCredentials: true`. Un origen comodín combinado con credenciales es una configuración que **no debe existir**; además el entorno de desarrollo usa `:4300` mientras el Playwright usa `:4201` → el E2E falla por CORS. | Media |
| **A-4** | El `api-gateway` no expone OpenAPI: el README afirma que `/api/auth/swagger-ui.html` funciona a través del gateway, pero **no hay ruta** para `/v3/api-docs` ni para `swagger-ui`. Sólo es accesible directamente contra cada servicio. | Baja |
| **A-5** | `cursos-service` define `spring.mvc.cors` en `application.yml` **y** el `SecurityFilterChain` **no** llama a `.cors(...)`: la configuración CORS del servicio es inoperante (el comentario del propio código dice que "lo gestiona exclusivamente el gateway"). Configuración contradictoria. | Baja |
| **A-6** | `cursos-service` **no** tiene `cors` deshabilitado en la cadena de seguridad (a diferencia de `auth-service` y `analytics-service`): inconsistencia entre microservicios hermanos. | Baja |
| **A-7** | `cursos-service` usa `@RequestHeader("Authorization")` **obligatorio** en 6 métodos: la ausencia de la cabecera provoca `MissingRequestHeaderException` → **500** en lugar de 401 (→ **DEF-02**). | Alta |
| **A-8** | `DashboardService` usa `new Random()` dentro del método → respuestas **no deterministas** (→ **DEF-07**). | Media |
| **A-9** | Las migraciones `V9__fix_youtube_urls.sql` y `V10__fix_youtube_urls_verified.sql` actualizan UUIDs (`34d4d37b-…`, `b5265365-…`) que **no existen** en `V1__init.sql` (que siembra `11111111-…`): sobre una base de datos recién creada, ambas migraciones son **no-ops silenciosos**. | Media |
| **A-10** | `auth-service` usa `@Transactional` en `register()`, pero la comprobación `existsByEmail()` + `save()` es un patrón *check-then-act*: la restricción `UNIQUE` cierra la ventana, pero la `DataIntegrityViolationException` no está mapeada → 500 en lugar de 409 (→ **DEF-08**). | Media |
| **A-11** | `AnalyticsSecurityConfig` deshabilita CORS, `CursosSecurityConfig` no (A-6). | Baja |
| **A-12** | `luxraywaer`: el deep-link `app://luxray/leccion/{id}` está declarado en `AndroidManifest.xml` (líneas 36-42) pero **no hay ningún manejador en Dart** (grep de `app://`, `uni_links`, `MethodChannel`: 0 resultados). El enlace declarado nunca hace nada. | Media |
| **A-13** | `luxraywaer`: la compilación *release* firma con la configuración **debug** (`signingConfigs.getByName("debug")` en `android/app/build.gradle.kts`): no hay keystore de producción. | Media |
| **A-14** | `luxraywaer`: `isMinifyEnabled = false` y `shrinkResources = false` en release → APK de release sin ofuscación ni reducción de recursos. | Baja |
| **A-15** | `luxraywaer`: la URL base está **codificada en el código** (`10.0.2.2`, el host del emulador) sin ninguna forma de configurarla para un dispositivo físico o producción (→ **DEF-05**). | Media |
| **A-16** | `frontend-web`: `environment.prod.ts` está **generado** por `scripts/set-env.js` y **versionado**: cualquier edición manual se pierde en el siguiente `npm run build:prod`, y puede divergir del generador. | Media |
| **A-17** | `frontend-web`: existen **dos servicios de dashboard** solapados (`analytics.service.ts` y `admin-dashboard.service.ts`) que llaman a la misma URL con **DTO duplicados y divergentes**. | Media |
| **A-18** | `frontend-web`: `ngsw-config.json` cachea `**/api/cursos*` y `**/api/search*` con estrategia `freshness` y `maxAge: 30m`: el catálogo puede mostrar datos de hasta 30 minutos obsoletos. | Baja |
| **A-19** | `frontend-web`: `environment.ts` usa `apiUrl: 'http://localhost:8080/api'` **en desarrollo**: si el bundle se sirve en otro equipo, todas las llamadas apuntan al `localhost` del navegador del usuario. | Baja |
| **A-20** | `luxraywaer`: `luxraywaer/` y `frontend-web/` están **excluidos del control de versiones** por `.gitignore` (→ **DEF-14**). | Alta |

---

## 7. Listado consolidado de defectos detectados en la Fase 1

| # | Severidad | Hallazgo | Destino |
|---|---|---|---|
| DEF-14 | Alta | Código de la app móvil y del sitio web fuera del control de versiones | A-20 |
| DEF-15 | Alta | 5 secretos por defecto en el código y en la configuración | §3.2 |
| DEF-02 | Alta | `MissingRequestHeaderException` sin handler → 500 | A-7 |
| DEF-01 | Alta | Ausencia de `AuthenticationEntryPoint` → 403 en vez de 401 | §3.1, A-1 |
| DEF-16 | Alta | `POST /api/search/reindex` público | §2.3 |
| DEF-05 | Media | URL base codificada, no configurable | A-15 |
| DEF-07 | Media | Métricas del dashboard no deterministas (`new Random()`) | A-8 |
| DEF-08 | Media | Email duplicado → 400 en vez de 409 | A-10 |
| — | Media | Validación de longitud ausente en `q` del backend | V-2 |
| — | Media | `||` de `eureka-client` fijado a mano fuera del BOM | §5 |
| — | Media | Migraciones V9/V10 son no-ops por UUID incorrectos | A-9 |
| — | Media | Deep-link declarado sin manejador en Dart | A-12 |
| — | Media | Release firmado con la configuración debug | A-13 |
| — | Media | `discovery.locator` expone servicios por nombre | A-2 |
| — | Media | CORS con origen comodín + `allowCredentials` | A-3 |
| — | Media | `environment.prod.ts` generado **y** versionado | A-16 |
| — | Media | Dos servicios de dashboard solapados con DTO divergentes | A-17 |
| — | Baja | 6 elementos de código muerto | §3.4 |
| — | Baja | 4 recursos no liberados explícitamente | §3.7 |
| — | Baja | 9 avisos del compilador Angular | §4 |
| — | Baja | Inconsistencias de CORS entre microservicios | A-5, A-6, A-11 |
| — | Baja | OpenAPI no agregado en el gateway | A-4 |
| — | Baja | Caché de 30 min en el catálogo del PWA | A-18 |
| — | Baja | `minify`/`shrinkResources` desactivados en release | A-14 |

**Total: 24 hallazgos** (6 de severidad alta, 8 media, 10 baja), de los que
**18** se elevan a la Fase 6 como defectos numerados (los 6 restantes son
recomendaciones de mantenimiento sin impacto funcional demostrado).

---

## 8. Conclusiones de la Fase 1

1. **El analizador estático no encuentra los defectos más graves.** Los tres
   analizadores (Dart, Java, Angular) compilan sin errores — la única
   incidencia de las suites es una prueba obsoleta (DEF-12). Los defectos de
   mayor severidad (401 vs 403, 500 por cabecera ausente, endpoint de
   escritura público, secretos por defecto) **sólo** aparecen al revisar el
   código y al ejecutar las pruebas de contrato.
2. **El proyecto no tiene red de seguridad de análisis estático avanzado**:
   sin PMD/SpotBugs, sin `dart analyze --fatal-infos`, sin
   `dependency-check`, sin Dependabot y sin CI. La configuración de JaCoCo
   añadida en esta práctica es el primer peldaño.
3. **La validación está bien planteada en la app móvil** (defensa en
   profundidad: UI + servicio) pero **ausente en el backend** para la longitud
   de la query, y **mal tipificada** al no separar validación de forma (400)
   y de semántica (409/422).
4. **No hay inyección SQL**: todas las consultas usan parámetros vinculados.
5. El coste de la operación más pesada del sistema (BCrypt) está **dentro**
   del camino crítico de la petición → DEF-13.

---

*Detalle de los defectos y su reproducción: `practica-11/fase6-defectos/DEFECTOS.md`.*
