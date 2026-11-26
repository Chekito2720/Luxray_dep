# Informe de Auditoría de Pruebas — LuxRay (Rama `practica-pruebas`)

Funcionalidad crítica evaluada: **registro y login con JWT en `auth-service` (puerto 8081)** y su consumo desde el frontend (AuthService, `authInterceptor`, `authGuard` + área privada `/mis-cursos`).

## Fase 1 — Diagnóstico

| Nivel | Cantidad | ¿Automático? | Archivos |
|---|---|---|---|
| Unitarias (backend) | 11 | Sí (Maven Surefire) | `backend/auth-service/.../AuthServiceTest.java` (8), `backend/common-lib/.../GlobalExceptionHandlerTest.java` (3) |
| Integración (backend) | 2 | Sí (Maven Surefire) | `backend/auth-service/.../AuthControllerIntegrationTest.java` |
| E2E | 0 | No | Ninguna (Playwright no instalado inicialmente) |

Hallazgo (no problema): casi no había pruebas antes de la práctica. Los `.spec.ts` del frontend mayormente son `should create` sin valor real. Solo `auth.service.spec.ts` (6) y `auth.interceptor.spec.ts` (5) tienen aserciones reales. No hay flujos de CI que ejecuten pruebas (`.github` solo contiene modernización Java).

Evidencia de ejecución base: `mvn -pl auth-service -am test` → `Tests run: 7, Failures: 0` (4 login + 3 handler).

## Fase 2 — Unitarias backend (auth-service)

Se agregaron 4 pruebas de registro a `AuthServiceTest` (mockeando `UsuarioRepository`, `PasswordEncoder` y usando el `JwtTokenProvider` real):

1. `registro_valido_guarda_usuario_con_password_hasheada` — persiste y hashea con BCrypt.
2. `password_de_8_caracteres_limite_es_aceptada` — límite inferior de `@Size(min = 8)`.
3. `email_duplicado_lanza_excepcion_y_no_guarda` — `BusinessException` y `verify(never()).save()`.
4. `terminos_no_aceptados_lanza_excepcion_y_no_guarda` — validación de términos y sin persistencia.

Resultado: `Tests run: 8, Failures: 0, Errors: 0` en `AuthServiceTest`; total backend `Tests run: 11, Failures: 0`.

## Fase 3 — Integración backend (H2)

- `backend/auth-service/pom.xml`: se agregó `com.h2database:h2` (scope test).
- `backend/auth-service/src/test/resources/application-test.properties`: H2 en memoria (`jdbc:h2:mem:auth_test_db`), Flyway deshabilitado, Eureka deshabilitado, JWT secreto de prueba. **Nunca se usa `auth_db` (Postgres).**
- `backend/auth-service/.../AuthControllerIntegrationTest.java`: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`.
  - `registro_persiste_en_bd_con_password_hasheada`: POST `/auth/registro` → 200 y password en BD empieza con `$2a$` (no está en claro).
  - `registro_duplicado_devuelve_400`: segundo registro del mismo email → 400 (`BusinessException` mapea a 400 por `GlobalExceptionHandler`, no 409).

Resultado: `Tests run: 10, Failures: 0, Errors: 0` (8 unitarias + 2 integración).

Limitación documentada: H2 en memoria no es Postgres real; se deja como nota frente a Testcontainers. El api-gateway queda fuera de estas pruebas (se prueba `auth-service` directo).

## Fase 4 — E2E (Playwright) — LIMITACIÓN

Se escribió la automatización pero **no se pudo ejecutar** contra una pila levantada:

- `frontend-web/e2e/auth-flow.spec.ts`: flujo registro → login → `/mis-cursos`, plus prueba de control de acceso (sin sesión el guard redirige a `/auth/login`).
- `frontend-web/playwright.config.ts`: Chromium, `baseURL http://localhost:4201`.

Bloqueador: el puerto 4200 lo ocupa nginx (404), no el dev server de Angular, y `ng serve --port 4201` no quedó listo en el tiempo disponible. Por tanto el E2E está **escrito pero no automatizado ejecutado**, según el fallback de la guía.

Procedimiento para ejecutar cuando el stack esté disponible:
1. Levantar `auth-service` (8081), `api-gateway` (8080) y `discovery-service` (8761) con Postgres `auth_db`.
2. `cd frontend-web && npm run start -- --port 4201`
3. `cd frontend-web && npx playwright test` y `npx playwright show-report`.

## Fase 5 — Auditoría consolidada

| Nivel | Total | Pasaron | Fallaron | % del total |
|---|---|---|---|---|
| Unitarias | 22 | 22 | 0 | ~92 % |
| Integración | 2 | 2 | 0 | ~8 % |
| E2E | 0 | 0 | 0 | 0 % |
| **Total** | **24** | **24** | **0** | **100 %** |

(*) Frontend: 17 `.spec.ts`, pero solo ~11 aserciones con valor real (`auth.service` 6 + `auth.interceptor` 5); el resto son `should create`.

### Comparación con la pirámide ideal (~70/20/10)

- **Actual:** Unitarias ~92 %, Integración ~8 %, E2E 0 %.
- **Objetivo:** Unitarias ~70 %, Integración ~20 %, E2E ~10 %.

La pirámide actual está invertida respecto al ideal: exceso de unitarias y ausencia total de E2E. No hay un desbalance hacia integración porque apenas hay 2.

### Capa más débil

**E2E (0 %)** y, secundariamente, el **frontend** (solo 2 de 17 `.spec.ts` con aserciones reales; el resto son `should create`). Justificación: no hay pruebas end-to-end ejecutables ni pruebas de navegación/guards en el navegador real.

### Dos mejoras por riesgo

1. **Autorización por rol y validez del JWT** (impacto alto: seguridad). Probar que `/admin/usuarios` rechaza a rol `USER` (403), que un token expirado da 401 y que el interceptor hace logout + redirección a `/auth/login`. Cubre el control de acceso que el guard `authGuard`/`adminGuard` implementa.
2. **Contrato entre gateway y servicios** (riesgo alto: 6 microservicios). Pruebas de contrato / integradas `gateway → auth-service` para asegurar que el routing `/api/auth/*` llega al servicio correcto y respeta los códigos de estado.

### Plan de ejecución recomendado

| Momento | Qué corre |
|---|---|
| Cada commit | Unitarias (`mvn test` backend, `ng test` frontend) |
| Cada PR | Unitarias + integración |
| Antes de entrega | Todo + E2E + cobertura (JaCoCo backend) |

### Limitaciones
- Integración con H2, no con Postgres real (`auth_db`).
- E2E no ejecutado (falta dev server de Angular disponible).
- Sin cobertura JaCoCo configurada aún.
- Sin flujos de CI que ejecuten las pruebas automáticamente.
