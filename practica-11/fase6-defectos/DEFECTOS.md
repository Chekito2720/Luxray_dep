# Fase 6 — Integración y registro de defectos

**Proyecto:** LuxRay — plataforma educativa de electricidad (monorepo)
**Rama evaluada:** `practica-pruebas` · commit `2d60917` (`Fase 5: informe de auditoria de pruebas`)
**Componentes:** aplicación móvil Wear OS (Flutter), sitio web PWA (Angular 21),
backend de microservicios (Spring Boot 3.4.1 / Java 21) y PostgreSQL 16.
**Fecha de las mediciones:** 2026-11-11 · **Entorno:** Docker Compose en Windows 11,
cliente = mismo equipo (192.168.1.153) · **Dispositivo móvil:** emulador
Wear OS Large Round API 36 (454×454 dp), Android 16.

Escala de severidad: **Alta** (rompe una funcionalidad o expone información),
**Media** (produce respuestas o comportamientos incorrectos), **Baja** (calidad,
UX o mantenibilidad).

| ID | Componente | Severidad | Estado |
|---|---|---|---|
| DEF-01 | Backend (los 3 servicios con Spring Security) | **Alta** | Abierto |
| DEF-02 | Backend — `GlobalExceptionHandler` | **Alta** | Abierto |
| DEF-03 | Backend — `GlobalExceptionHandler` | **Media** | Abierto |
| DEF-04 | Aplicación móvil — `main.dart` | **Alta** | **Corregido** |
| DEF-05 | Aplicación móvil — `luxray_api.dart` | **Media** | **Corregido** |
| DEF-06 | Entorno / aplicación móvil | **Media** | Documentado |
| DEF-07 | Backend — `DashboardService` | **Media** | Abierto |
| DEF-08 | Backend — `auth-service` | **Media** | Abierto |
| DEF-09 | Backend — `auth-service` | **Media** | Abierto |
| DEF-10 | Backend — `cursos-service` | **Media** | Abierto |
| DEF-11 | Backend — `cursos-service` | **Baja** | Abierto |
| DEF-12 | Sitio web — `busqueda.service.spec.ts` | **Media** | Abierto |
| DEF-13 | Backend — `auth-service` (rendimiento) | **Media** | Abierto |
| DEF-14 | Repositorio (`.gitignore`) | **Alta** | Abierto |
| DEF-15 | Backend — secretos por defecto | **Alta** | Abierto |
| DEF-16 | Backend — `search-service` | **Alta** | Abierto |
| DEF-17 | Backend — `analytics-service` | **Baja** | Abierto |
| DEF-18 | Aplicación móvil — UX de error | **Baja** | Abierto |

---

## DEF-01 · El backend responde **403** cuando debería responder **401**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/common-lib` + `auth-service`, `cursos-service`, `analytics-service` (configuración de Spring Security) |
| **Severidad** | **Alta** |

**Pasos para reproducirlo**

1. Levantar el stack: `docker compose up -d`.
2. `curl -i http://localhost:8080/api/auth/me` (sin cabecera `Authorization`).
3. `curl -i http://localhost:8080/api/auth/admin/usuarios` (sin token).
4. `curl -i -H "Authorization: Bearer token.invalido" http://localhost:8080/api/auth/me`.

**Resultado esperado**

`401 Unauthorized` — la petición no está autenticada. El código `403` debe
reservarse para "identificado pero sin permisos" (`hasRole('ADMIN')`).

**Resultado observado**

`403 Forbidden` con cuerpo de longitud **0 bytes** en los tres casos.

**Evidencia**

* `evidencias/fase3_newman_consola.txt` — assertions 05, 06, 07 y 10 en rojo:
  `expected 403 to deeply equal 401`.
* `evidencias/fase3_sonda_http.json` — 10 respuestas `403` donde se esperaba `401`.
* Causa raíz: **no existe ningún `AuthenticationEntryPoint` configurado** en el
  backend. Búsqueda exhaustiva:
  ```
  grep -r "AuthenticationEntryPoint\|AccessDeniedHandler\|exceptionHandling" backend/ → 0 resultados
  ```
  Con `authorizeHttpRequests(... .anyRequest().authenticated())` y sin punto de
  entrada propio, Spring Security usa el `Http403ForbiddenEntryPoint` por defecto.
  Los tres `SecurityConfig` (`AuthSecurityConfig`, `CursosSecurityConfig`,
  `AnalyticsSecurityConfig`) son idénticos en este punto.

**Propuesta de corrección**

Crear en `common-lib` un `RestAuthenticationEntryPoint` compartido y registrarlo
en cada `SecurityFilterChain`:

```java
http.exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint));
```

con

```java
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException ex) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\","
            + "\"message\":\"Se requiere autenticación.\",\"path\":\""
            + req.getRequestURI() + "\"}");
    }
}
```

El `403` de autorización (rol insuficiente) se conserva intacto, que es
exactamente la distinción que exige la matriz de la práctica.

---

## DEF-02 · Cabecera `Authorization` ausente devuelve **500** en lugar de **401**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/common-lib` — `GlobalExceptionHandler` |
| **Severidad** | **Alta** |

**Pasos para reproducirlo**

```
curl -i http://localhost:8080/api/cursos/mis-cursos
curl -i -X POST http://localhost:8080/api/cursos/<id>/inscripcion
```

**Resultado esperado** — `401 Unauthorized`.

**Resultado observado** — `500 Internal Server Error` con el mensaje genérico
`"Error interno del servidor. Intenta más tarde."`.

**Evidencia**

* `evidencias/fase3_newman_consola.txt` — assertion 08:
  `expected 500 to deeply equal 401`.
* `evidencias/fase3_sonda_http.json` → `"GET /cursos/mis-cursos (sin token)"` → HTTP 500.
* Causa raíz: `CursosController.misCursos` declara
  `@RequestHeader("Authorization") String auth` **obligatorio**. Cuando la
  cabecera falta, Spring lanza `MissingRequestHeaderException`, que **no tiene
  handler** en `GlobalExceptionHandler` y cae en `handleAll(Exception)` → 500.
* Reproducción automatizada: `common-lib/src/test/java/.../GlobalExceptionHandlerHttpStatusTest.java`
  → prueba `cabeceraAuthorizationAusente_deberiaSer401` (marcada `@Disabled`
  porque documenta el defecto).

**Propuesta de corrección**

Añadir el handler explícito:

```java
@ExceptionHandler({MissingRequestHeaderException.class,
                   MissingRequestValueException.class})
public ResponseEntity<ApiError> handleCabeceraObligatoria(Exception ex,
                                                            HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "Unauthorized",
                 "Se requiere autenticación.", req);
}
```

Alternativamente (y mejor): cambiar las firmas de los controladores para que
la cabecera sea opcional (`@RequestHeader(value = "Authorization", required = false)`)
y dejar que sea el filtro de seguridad quien decida el 401. Así el contrato
HTTP del endpoint no filtra el detalle de implementación.

---

## DEF-03 · Identificador malformado devuelve **500** en lugar de **400**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/common-lib` — `GlobalExceptionHandler` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

```
curl -i http://localhost:8080/api/cursos/no-es-uuid
```

**Resultado esperado** — `400 Bad Request` (el cliente ha enviado un dato
inválido; el error es del cliente, no del servidor).

**Resultado observado** — `500 Internal Server Error`.

**Evidencia**

* `evidencias/fase3_newman_consola.txt` — assertion 01:
  `expected 500 to deeply equal 400`.
* `evidencias/fase3_sonda_http.json` → `"GET /cursos/no-es-uuid"` → HTTP 500.
* Causa raíz: `MethodArgumentTypeMismatchException` (conversión de `String` a
  `UUID`) no está mapeada en `GlobalExceptionHandler` → cae en `handleAll` → 500.

**Propuesta de corrección**

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiError> handleTipoInvalido(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest req) {
    log.warn("Parámetro con tipo inválido en {}: {}", req.getRequestURI(), ex.getName());
    return build(HttpStatus.BAD_REQUEST, "Bad Request",
                 "El valor de '" + ex.getName() + "' no tiene un formato válido.", req);
}
```

Mismo tratamiento para `ConstraintViolationException` (validación de
parámetros de método), que hoy también degenera en 500.

---

## DEF-04 · La aplicación móvil **arranca en la pantalla de resultados** en vez de en la de inicio

| Campo | Detalle |
|---|---|
| **Componente** | `luxraywaer/lib/main.dart` → `_RootState._buildDetalle()` (línea ~157) |
| **Severidad** | **Alta** — la función principal de la app (buscar) es inaccesible al arrancar |

**Pasos para reproducirlo**

1. `flutter build apk --debug` e instalar en el emulador Wear OS.
2. `adb shell am force-stop com.luxray.waer`
3. `adb shell am start -n com.luxray.waer/.MainActivity`
4. Capturar pantalla: **aparece directamente "Sin coincidencias dentro del giro
   eléctrico"**, nunca la pantalla de inicio con el campo de búsqueda.

**Resultado esperado** — la app arranca en la pantalla de inicio (página 0 del
`PageView`), con el campo de búsqueda y el botón BUSCAR.

**Resultado observado** — la app arranca en la página 1 (resultados) con un
mensaje de "sin resultados" para una consulta que el usuario nunca ha escrito.

**Evidencia**

* `evidencias/wb11_disp_estado_inicial.png` — estado inicial **defectuoso**
  (pantalla de resultados).
* `evidencias/wb11_disp_home_corregido.png` — estado inicial **correcto**
  tras la corrección.
* Reproducción automatizada: `luxraywaer/test/regresion_def04_navegacion_test.dart`
  — falla con el código original (`Found 0 widgets with text "LuxRay"`,
  `Found 1 widget with text containing Sin coincidencias`) y pasa tras corregir.
* Causa raíz: un `PageView` construye **todos** sus hijos desde el primer
  fotograma, no sólo la página visible. Al arrancar, `_conceptoDetalle` y
  `_faqDetalle` son `null`, de modo que `_buildDetalle()` entra en su rama de
  "fallback" y encola `_irA(1)`:
  ```dart
  WidgetsBinding.instance.addPostFrameCallback((_) => _irA(1));
  ```
  La condición "si llegamos a la página 2" nunca se comprobaba.

**Propuesta de corrección** — aplicada y verificada

```dart
WidgetsBinding.instance.addPostFrameCallback((_) {
  if (_pager.hasClients && _pager.page != null && _pager.page!.round() == 2) {
    _irA(1);
  }
});
```

Verificación: la prueba de regresión pasa y la captura del dispositivo muestra
la pantalla de inicio.

---

## DEF-05 · La URL base del API está **codificada en el código** y no es configurable

| Campo | Detalle |
|---|---|
| **Componente** | `luxraywaer/lib/services/luxray_api.dart` → `LuxrayApi._kDefaultBase` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

1. `grep -n "_kDefaultBase" luxraywaer/lib/services/luxray_api.dart` →
   `http://10.0.2.2:8080/api`, constante de compilación.
2. Intentar apuntar la app a un backend desplegado: hay que **editar el código
   fuente y recompilar**; no existe `--dart-define`, ni `.env`, ni flavor de
   build. Búsqueda: `grep -r "LUXRAY_API_BASE\|\.env\|dart-define" luxraywaer/`
   → 0 resultados.

**Resultado esperado** — la URL base debe ser configurable por entorno de
compilación, de modo que la misma APK sirva para emulador, dispositivo físico
y producción.

**Resultado observado** — el valor está fijado a `10.0.2.2`, que es el alias
del **host del emulador**. En un reloj físico, `10.0.2.2` no existe: la app
nunca podrá consultar al servidor.

**Evidencia**

* `evidencias/fase6_defectos.md` §DEF-05 · análisis estático (Fase 1).
* Consecuencia medida en el dispositivo: la búsqueda desde el wearable devuelve
  siempre "Sin coincidencias" (ver DEF-06).

**Propuesta de corrección** — aplicada y verificada

```dart
static const String _kDefaultBase = String.fromEnvironment(
  'LUXRAY_API_BASE',
  defaultValue: 'http://10.0.2.2:8080/api',
);
```

Con el valor por defecto **idéntico** al anterior (comportamiento sin cambios),
pero conmutando en compilación:

```
flutter build apk --release --dart-define=LUXRAY_API_BASE=https://api.luxray.app/api
```

Evidencia de funcionamiento con la variante configurable: APK de diagnóstico
`evidencias/wb11_app-diagnostico-relay.apk`; la búsqueda en el dispositivo
devuelve resultados reales (`evidencias/wb11_disp_resultado_real.png`).

---

## DEF-06 · Conflicto de puerto: la ruta por defecto del wearable **no llega al gateway**

| Campo | Detalle |
|---|---|
| **Componente** | Entorno de ejecución + aplicación móvil |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

1. `docker compose up -d` (el gateway se publica en `0.0.0.0:8080`).
2. Desde el emulador: `adb shell "printf 'GET /api/cursos HTTP/1.0\r\n\r\n' | toybox nc 10.0.2.2 8080"`.
3. Desde el host: `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/cursos`.

**Resultado esperado** — `10.0.2.2:8080` (alias del loopback del host) debe
alcanzar el API Gateway de LuxRay.

**Resultado observado**

```
HTTP/1.1 404 Not Found
Server: Apache
Content-Length: 196
```

El puerto IPv4 `127.0.0.1:8080` está ocupado por **otro servidor local**
(Apache dentro de WSL, exposure por `wslrelay`, PID 10456), mientras que el
gateway publicado por Docker responde únicamente por IPv6 en `[::1]:8080`:

| Dirección | Servidor que responde | Código |
|---|---|---|
| `http://127.0.0.1:8080/api/cursos` | Apache (WSL) | 404 |
| `http://192.168.1.153:8080/api/cursos` | Apache (WSL) | 404 |
| `http://[::1]:8080/api/cursos` | **LuxRay API Gateway** | 200 |

**Evidencia**

* `adb shell nc 10.0.2.2 8080` → `Server: Apache`, 404 (capturado arriba).
* `evidencias/relay_consola.txt` — el relay TCP de diagnóstico (IPv4 8099 →
  IPv6 8080) sí alcanza el gateway: la búsqueda desde el wearable devuelve
  resultados reales (`evidencias/wb11_disp_resultado_real.png`).
* `docker logs luxray-gateway` — **no registra ninguna petición** del emulador:
  la petición nunca llega al gateway.

**Propuesta de corrección**

1. Liberar el puerto IPv4 `8080` en el host (o mover el gateway a otro puerto
   publicado, p.ej. `8090:8080`), de modo que `10.0.2.2:8080` resuelva al
   gateway en el emulador.
2. Con DEF-05 corregido, documentar el valor por entorno para cada escenario.
3. En el emulador, la app debería **distinguir "sin resultados" de "error de
   red"** (ver DEF-18).

---

## DEF-07 · El dashboard devuelve **métricas no deterministas**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/analytics-service` → `DashboardService` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

```
curl -s -H "Authorization: Bearer <token ADMIN>" http://localhost:8080/api/analytics/dashboard
```

Ejecutar dos veces con el mismo token y comparar `distribucion.niveles` y
`progreso.promedio`.

**Resultado esperado** — el mismo usuario y el mismo instante deben devolver
datos idénticos (el endpoint es de lectura).

**Resultado observado** — dos de las series difieren entre llamadas.

**Evidencia**

* Código: `DashboardService.calcularInscripcionesGlobalesPorMes()` y
  `calcularProgresoPromedioGlobal()` construyen sus valores con
  `new Random()` **dentro del método**.
* `evidencias/fase3_sonda_http.json` → `"GET /analytics/dashboard"` con
  **6 598 ms** de latencia, la mayor de todos los endpoints medidos.
* `evidencias/fase5_carga_consolidado.md` — el encadenamiento de llamadas
  `WebClient` (3 servicios en serie) es el principal cuello de botella.

**Propuesta de corrección**

1. Sustituir `new Random()` por datos reales leídos de `cursos_db`
   (`inscripciones.inscrito_en`, `progreso_lecciones.completado_en`), que es lo
   que el modelo `AnalyticsSnapshot` ya contempla y que hoy **nunca se
   escribe** (ver DEF-17).
2. Si se mantiene la simulación, usar una semilla fija
   (`new Random(usuarioId.hashCode())`) para que la respuesta sea reproducible.
3. Mover el cálculo a un `@Scheduled` que persista el snapshot y servirlo desde
   la tabla: elimina el encadenamiento de 3 llamadas HTTP del camino crítico.

---

## DEF-08 · Email duplicado devuelve **400** en lugar de **409 Conflict**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/auth-service` → `AuthService.register` / `GlobalExceptionHandler` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

1. `POST /api/auth/registro` con un correo válido → `200`.
2. Repetir la misma petición → se espera `409 Conflict`.

**Resultado esperado** — `409 Conflict` (el recurso ya existe).

**Resultado observado** — `400 Bad Request` con
`{"message":"El correo ya está registrado. Intenta iniciar sesión."}`.

**Evidencia** — `evidencias/fase3_newman_consola.txt` assertion 02:
`expected 400 to deeply equal 409`.

**Propuesta de corrección** — introducir una excepción específica y su mapeo:

```java
public class ConflictException extends RuntimeException { /* ... */ }

@ExceptionHandler(ConflictException.class)
public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
    return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
}
```

y en `AuthService.register` lanzar `ConflictException` en lugar de
`BusinessException`. Además, la restricción `UNIQUE (email)` de PostgreSQL debe
traducirse a `409` (y no a 500) mediante un handler de
`DataIntegrityViolationException`, para cubrir también la condición de carrera
entre dos registros simultáneos con el mismo correo.

---

## DEF-09 · Credenciales incorrectas devuelven **400** en lugar de **401**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/auth-service` → `AuthService.login` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

```
POST /api/auth/login  {"email":"<existente>","password":"incorrecta"}
POST /api/auth/login  {"email":"<inexistente>","password":"incorrecta"}
```

**Resultado esperado** — `401 Unauthorized` en ambos casos (y el **mismo**
mensaje, para no permitir enumerar correos).

**Resultado observado** — `400 Bad Request` en ambos casos.

**Evidencia** — `evidencias/fase3_newman_consola.txt` assertions 03 y 04:
`expected 400 to deeply equal 401`. El mensaje sí es uniforme (verificado por
`AuthServiceTest.usuarioInexistente_mismoMensajeQuePasswordIncorrecta`), por lo
que el defecto es **sólo de código HTTP**, no de fuga de información.

**Propuesta de corrección** — mapear el fallo de autenticación a 401 sin tocar
el mensaje:

```java
public class UnauthorizedException extends RuntimeException { /* ... */ }

// en AuthService.login:
throw new UnauthorizedException("Credenciales inválidas");
```

```java
@ExceptionHandler(UnauthorizedException.class)
public ResponseEntity<ApiError> handleNoAutorizado(UnauthorizedException ex,
                                                    HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
}
```

---

## DEF-10 · Inscripción duplicada devuelve **200** en lugar de **409**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/cursos-service` → `CursosService.inscribir` |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

1. `POST /api/cursos/{id}/inscripcion` con token válido → `200`.
2. Repetir la misma inscripción con el mismo usuario → se espera `409 Conflict`.

**Resultado esperado** — `409 Conflict` (o un `200` con una respuesta que
distinga explícitamente "ya estás inscrito" mediante un campo dedicado).

**Resultado observado** — `200` con el **mismo cuerpo** que la primera
inscripción: el cliente no puede distinguir "recién inscrito" de "ya lo estaba".

**Evidencia** — `evidencias/fase3_newman_consola.txt` assertion 09:
`expected 200 to deeply equal 409`. La base de datos **sí** garantiza la
unicidad (`inscripciones_usuario_id_curso_id_key`, ver
`evidencias/fase4_crud_integridad.txt`), pero la capa de servicio no lo
traduce.

**Propuesta de corrección** — comprobar la inscripción previa y lanzar
`ConflictException` (mismo handler propuesto en DEF-08), o bien devolver
`{...,"yaInscrito": true}` para que el frontend pueda messagingar
"ya estás inscrito en este curso".

---

## DEF-11 · Método HTTP no permitido devuelve **403** en lugar de **405**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/cursos-service` — `CursosSecurityConfig` |
| **Severidad** | **Baja** |

**Pasos para reproducirlo**

```
curl -i -X DELETE http://localhost:8080/api/cursos
```

**Resultado esperado** — `405 Method Not Allowed`.

**Resultado observado** — `403 Forbidden` (cuerpo vacío).

**Evidencia** — `evidencias/fase3_newman_consola.txt` assertion 11:
`expected 403 to deeply equal 405`.

**Propuesta de corrección** — con la corrección de DEF-01 (punto de entrada
propio) el `DELETE` anónimo pasará a `401`, que es semánticamente correcto;
para el usuario autenticado sin rol basta con declarar explícitamente
`requestMatchers(HttpMethod.DELETE, "/cursos").denyAll()` o, preferiblemente,
devolver `405` desde el `AccessDeniedHandler` cuando el método no está
permitido para el recurso.

---

## DEF-12 · La prueba del sitio web **afirma una URL que la implementación no usa**

| Campo | Detalle |
|---|---|
| **Componente** | `frontend-web/src/app/services/busqueda.service.spec.ts` |
| **Severidad** | **Media** (suite en rojo ⇒ información falsa sobre el estado del proyecto) |

**Pasos para reproducirlo**

1. `cd frontend-web && npx ng test --no-watch`
2. Fallo: `Expected one matching request for criteria "Match URL:
   http://localhost:8080/api/cursos/buscar?q=ohm", found none. Requests
   received are: GET http://localhost:8080/api/search?q=ohm.`

**Resultado esperado** — la prueba debe reflejar el contrato real.

**Resultado observado** — la implementación usa `environment.services.busqueda`
(`/api/search`) y la prueba sigue esperando `/api/cursos/buscar`.

**Evidencia** — `evidencias/fase1_ng_test.txt` y salida de `ng test`:
`Test Files 1 failed | 14 passed (15)`, `Tests 1 failed | 44 passed (45)`.

**Propuesta de corrección** — actualizar la aserción al contrato vigente
(`${environment.services.busqueda}?q=ohm`) y añadir una prueba que verifique
que la envoltura `{id, score, source, highlight}` del `search-service` se
transforma a la lista plana que consume la UI (`transform()`).

---

## DEF-13 · El endpoint de escritura **incumple el objetivo de latencia** con 10 usuarios concurrentes

| Campo | Detalle |
|---|---|
| **Componente** | `backend/auth-service` (BCrypt con factor de coste 12) |
| **Severidad** | **Media** |

**Pasos para reproducirlo**

```
k6 run --summary-export ... carga_k6.js   # con VUS=10, MODO=escritura
```

**Resultado esperado** — criterio de referencia de la práctica: latencia
HTTP P95 ≤ 500 ms.

**Resultado observado**

| Escenario | P95 (ms) | Máx (ms) | Objetivo ≤ 500 ms |
|---|---|---|---|
| escritura · 1 usuario | 404,28 | 405,62 | ✔ cumple |
| escritura · 5 usuarios | 492,66 | 507,25 | ✔ cumple (al límite) |
| escritura · 10 usuarios | **667,43** | **742,19** | ✘ **incumple** |

**Evidencia** — `evidencias/fase5_carga_consolidado.md` y
`evidencias/fase5_carga_consolidado.json` (20 iteraciones por VU, escenarios
aislados).

**Propuesta de corrección**

El coste viene de `BCryptPasswordEncoder(12)` en `AuthSecurityConfig`: cada
alta de usuario consume ~400 ms de CPU **en serie dentro de la petición**.
Opciones, por orden de recomendación:

1. **Hacer el hash asíncrono y fuera del camino de la respuesta**: devolver
   `202 Accepted` con un token de estado y completar el hash en segundo plano
   (el alta no necesita ser inmediata para el usuario).
2. Bajar el factor de coste a 10 (coste ≈ 100 ms) **y** aceptar el coste de
   seguridad evaluando el volumen real de altas.
3. Poner un límite de concurrencia (semáforo) para el registro: encolar las
   altas en vez de saturar la CPU.

Nota: bajar el coste BCrypt sin medir es contraproducente; el análisis debe
repetirse con el valor final.

---

## DEF-14 · La aplicación móvil y el sitio web **no están bajo control de versiones**

| Campo | Detalle |
|---|---|
| **Componente** | `.gitignore` de la raíz |
| **Severidad** | **Alta** (impide entregar el código fuente de los casos de prueba) |

**Pasos para reproducirlo**

```
git ls-files luxraywaer | wc -l        → 0
git ls-files frontend-web | wc -l      → 0
```

**Resultado esperado** — todo el código fuente del proyecto, incluidas las
pruebas, debe estar versionado para que la evidencia sea trazable a un commit.

**Resultado observado** — `.gitignore` excluye `frontend-web/` y `luxraywaer/`
en bloque. Los 124 casos de prueba nuevos de la aplicación móvil y los
scripts de la práctica **no podrían entregarse** en el repositorio.

**Evidencia** — `git check-ignore -v luxraywaer/lib/main.dart` →
`.gitignore:3:luxraywaer/`.

**Propuesta de corrección** — dejar de ignorar el código fuente y excluir
sólo los artefactos de compilación:

```gitignore
frontend-web/node_modules/
frontend-web/dist/
frontend-web/.angular/
luxraywaer/build/
luxraywaer/.dart_tool/
```

---

## DEF-15 · **Secretos por defecto** en el código y en los ficheros de configuración

| Campo | Detalle |
|---|---|
| **Componente** | `backend/*/src/main/resources/application.yml`, `docker-compose.yml` |
| **Severidad** | **Alta** |

**Evidencia**

| Secreto | Ubicación |
|---|---|
| `luxray_secret_2026` | contraseña de PostgreSQL en los tres `application.yml` |
| `luxray-jwt-super-secreto-para-desarrollo-min-32caracteres` | secreto JWT por defecto en `common-lib` |
| `luxray-jwt-secret-para-docker-compose-no-usar-en-produccion` | `docker-compose.yml` |
| `S3RVER / S3RVER` | credenciales de s3rver (`docker-compose.yml`) |
| `admin@luxray.com` + hash de demo | `auth-service/.../db/migration/V3__seed_admin_and_fix_user.sql` |

`JwtTokenProvider` ya detecta y avisa por log de los dos secretos de
desarrollo conocidos (comportamiento correcto), pero el valor por defecto
**sigue siendo válido**: cualquier despliegue que olvide `JWT_SECRET` queda
con una clave pública.

**Propuesta de corrección**

1. Eliminar el valor por defecto del secreto JWT: hacer la propiedad
   **obligatoria** (`@Value("${luxray.security.jwt.secret}")` sin `default:`)
   y fallar el arranque si no está definida.
2. Sustituir las contraseñas de los `application.yml` por marcadores sin valor
   (`${DB_PASSWORD}`) sin `default:`.
3. Rotar los datos de semilla: la cuenta `admin@luxray.com` con contraseña
   conocida no debe existir en un entorno desplegado (usar un perfil de
   `local`).

---

## DEF-16 · `search-service` **no tiene autenticación** y expone un endpoint de escritura al público

| Campo | Detalle |
|---|---|
| **Componente** | `backend/search-service` + `backend/api-gateway` |
| **Severidad** | **Alta** |

**Pasos para reproducirlo**

```
curl -i -X POST http://localhost:8080/api/search/reindex
```

**Resultado esperado** — `401`/`403` (reescribir el índice es una operación
de escritura y debe exigir permisos de administrador).

**Resultado observado**

```
HTTP/1.1 200 OK
{"data":{"cursos":1,"faqs":1,"lecciones":1,"conceptos":6,
          "message":"Datos de prueba insertados"},"success":true}
```

**Evidencia**

* `evidencias/fase3_sonda_http.json` → `POST /api/search/reindex` → HTTP 200.
* Causa raíz: `SearchServiceApplication` excluye la autoconfiguración de
  Spring Security (`exclude = {SecurityAutoConfiguration.class, ...}`) y el
  gateway enruta `/api/search/**` **sin ninguna condición de autorización**.
  En consecuencia, **toda** la superficie del `search-service` es pública.

**Propuesta de corrección**

1. Separar la lectura de la escritura: `GET /api/search` público,
   `POST /api/search/reindex` con `@PreAuthorize("hasRole('ADMIN')")`.
2. Restringir el `POST` en el propio `search-service` (restaurando Spring
   Security) o, como mínimo, protegerlo en el gateway con un filtro que
   exija `Authorization` y el rol `ADMIN`.

---

## DEF-17 · La base de datos `analytics_db` **nunca se escribe**

| Campo | Detalle |
|---|---|
| **Componente** | `backend/analytics-service` → `AnalyticsRepository` / `AnalyticsSnapshot` |
| **Severidad** | **Baja** |

**Pasos para reproducirlo**

```
docker exec luxray-analytics-db psql -U luxray -d analytics_db \
  -c "select count(*) from analytics_snapshots;"
```

**Resultado esperado** — la tabla de snapshots debería alimentarse (es el
modelo que justifica la existencia de la base de datos y de la tabla
`idx_snap_usuario_fecha`).

**Resultado observado** — `snapshots = 0` **siempre**, incluso después de
múltiples visitas al dashboard.

**Evidencia** — `AnalyticsRepository` (`findByUsuarioIdOrderByFechaDesc`) está
declarado pero **nunca se invoca** en `DashboardService`; el servicio calcula
todo en memoria y devuelve el resultado sin persistirlo.

**Propuesta de corrección**

Añadir un `@Scheduled` diario que compute y persista el snapshot por usuario
(usando el modelo ya existente), y hacer que `DashboardService` lea del
snapshot en lugar de recalcular. Beneficios: elimina DEF-07 (datos
deterministas) y reduce la latencia del endpoint (DEF-07).

---

## DEF-18 · La app no distingue "sin resultados" de "error de red"

| Campo | Detalle |
|---|---|
| **Componente** | `luxraywaer/lib/services/luxray_api.dart` → `buscar()` |
| **Severidad** | **Baja** (UX) |

**Pasos para reproducirlo**

1. Con el wearable en línea: buscar "ley de ohm" → se muestran resultados.
2. Cortar la conectividad (o apuntar a una URL inalcanzable) y repetir la
   búsqueda → la app muestra **exactamente la misma pantalla** de "Sin
   coincidencias".

**Resultado esperado** — mensajes distintos: "sin resultados" frente a
"sin conexión, reintentando".

**Resultado observado** — el mismo mensaje en ambos casos. El `catch-all` de
`_buscarEnSearch` (línea ~93) degrada **todo** a `BusquedaResponse.empty()`,
que es indistinguible de "no había coincidencias".

**Evidencia**

* `evidencias/wb11_disp_resultado_real.png` (con red) frente a
  `evidencias/wb11_disp_sin_conectividad.png` (sin red): el resultado visible
  es el mismo salvo por la ausencia de hits.
* `luxraywaer/test/fase2_whitebox_test.dart` → `M3-D6 · excepción de red:
  degrada a vacío, NO propaga`: la degradación es correcta desde el punto de
  vista de la estabilidad, pero se pierde la información de causa.

**Propuesta de corrección**

Introducir un estado de error explícito en el modelo de respuesta (por ejemplo
un campo `error` o un `enum LuxrayEstado { ok, vacio, sinRed }`) en lugar de
colapsar todos los fallos en `empty()`, y differentiating la UI en consecuencia.
Mantener la degradación sin excepción (requisito de diseño del wearable) pero
**conservando el motivo** para poder informar al usuario.

---

## Resumen cuantitativo

| Severidad | Cantidad | Con corrección aplicada | Con reproducción automatizada |
|---|---|---|---|
| Alta | 5 | 1 (DEF-04) | 4 |
| Media | 8 | 1 (DEF-05) | 6 |
| Baja | 5 | 0 | 4 |
| **Total** | **18** | **2** | **14** |

Defectos detectados por la auditoría: **18** (se exigían 3 como mínimo).
Dos han sido **corregidos y verificados** durante la práctica (DEF-04 y
DEF-05); el resto se documenta con su causa raíz, su reproducción
automatizada y una propuesta de corrección concreta.
