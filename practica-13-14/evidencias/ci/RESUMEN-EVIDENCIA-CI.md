# Evidencia de la ejecución automática (GitHub Actions)

Todo el contenido de esta carpeta se descargó **del propio GitHub Actions** de la
ejecución indicada, no se generó localmente.

**Repositorio:** `Chekito2720/Luxray_dep` · **Rama:** `practica-pruebas`
**Workflow:** `.github/workflows/ci-luxray.yml` («CI LuxRay»)

---

## Ejecución verde verificada — `44347435232`

* **URL:** https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232
* **Evento:** `push` · **Commit:** `999306d` · **Resultado:** ✅ **success**

### Contenido de esta carpeta

| Fichero | Origen | Qué demuestra |
|---|---|---|
| `logs/0_App móvil (Flutter).txt` | log del job Flutter de la CI | `flutter analyze` → *«No issues found!»*; `flutter test` → 174 pruebas; conversión JUnit → *«185 pruebas, 0 fallos»* |
| `logs/1_Backend (Spring Boot).txt` | log del job backend de la CI | `AuthServiceTest` 8/0/0/0 · `AuthControllerIntegrationTest` 2/0/0/0 · `RegistroIntegracionTest` 7/0/0/**3 saltadas** · **BUILD SUCCESS** |
| `artifacts/flutter/flutter_test.xml` | artefacto `flutter-test-results` | JUnit de las 11 suites Flutter: 185 entradas, 0 fallos |
| `artifacts/flutter/flutter_test.json` | artefacto `flutter-test-results` | traza original `flutter test --machine` |
| `artifacts/backend/…/TEST-…RegistroIntegracionTest.xml` | artefacto `backend-test-results` | `tests="7" errors="0" skipped="3" failures="0"` |
| `artifacts/backend/…/TEST-…AuthServiceTest.xml` | artefacto `backend-test-results` | `tests="8" … failures="0"` |
| `artifacts/backend/…/TEST-…AuthControllerIntegrationTest.xml` | artefacto `backend-test-results` | `tests="2" … failures="0"` |
| `artifacts/backend/mvn.log` | artefacto `backend-test-results` | salida completa de Maven tal y como la dejó el runner |
| `run_44347435232_logs.zip` | descarga de `/actions/runs/{id}/logs` | zip original con los logs de ambos jobs |
| `artifact_*.zip` | descarga de `/actions/artifacts/{id}/zip` | zips originales de los artefactos |

### Tiempos de la ejecución (API de GitHub)

| Job | Inicio | Fin | Duración |
|---|---|---|---|
| Backend (Spring Boot) | 16:27:27Z | 16:28:15Z | **48 s** (build y pruebas: 29 s) |
| App móvil (Flutter) | 16:27:27Z | 16:28:50Z | **83 s** (análisis: 8 s · pruebas: 37 s) |

---

## Historial completo de ejecuciones

| # | Ejecución | Commit | Resultado | Evidencia del resultado |
|---|---|---|---|---|
| 1 | [`44239354054`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44239354054) | `3b5ae8b` | ❌ **failure** | Job Flutter ✔ (36 s) · job backend ✘ en «Compilar y ejecutar pruebas» (30 s) |
| 2 | [`44241518237`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44241518237) | `cf9d7d9` | ⚠️ **«verde» falso** | El paso `mvn … \| tee` devolvía el código de `tee` (0): Maven estaba en **rojo** y el pipeline lo daba por bueno |
| 3 | [`44347435232`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232) | `999306d` | ✅ **success real** | Logs y artefactos de esta carpeta |
| 4 | [`44365052085`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365052085) | `e194c7d` | `push` | ✅ **success real** | Logs en `logs_run_44365052085/` — `BUILD SUCCESS` + *«185 passed»*. Cada `push` re-dispara el pipeline |
| 5 | [`44365378884`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365378884) | `e194c7d` | **`pull_request`** ([PR #1](https://github.com/Chekito2720/Luxray_dep/pull/1)) | ✅ **success real** | Logs en `logs_run_44365378884/` — `BUILD SUCCESS` + *«No issues found!»* + *«185 passed»*. Verifica el **disparador `pull_request`** |

> Los **dos disparadores exigidos por la práctica están verificados con ejecuciones reales**:
> `push` (ejecuciones 1–4) y `pull_request` (ejecución 5).

### Cómo se diagnosticó el fallo de la ejecución 1

No fue posible descargar el log desde la API pública (GitHub lo restringe a
administradores del repositorio: `403 Must have admin rights to Repository`),
así que el diagnóstico se hizo por **diferencia con el entorno local**:

1. Se reprodujo la build en un **worktree limpio de HEAD** con `git worktree add --detach`:
   `./mvnw -B clean test` → **BUILD FAILURE**, con dos fallos concretos:
   * `AuthServiceTest.usuarioInexistente_ejecutaBcryptDeTodasFormas`
   * `RegistroIntegracionTest.codigos_http_esperados` — *«Status expected:<400> but was:<500>»*
2. Ambos se explicaban por **código de producción sin versionar** (el *hash cebo* del
   login y el manejador de `HttpMessageNotReadableException`).
3. Se versionó ese código y se añadió **`set -o pipefail`** para que un fallo de
   Maven no pueda volver a disfrazarse de check verde.
4. La **ejecución 3** confirmó el diagnóstico: `BUILD SUCCESS` en el log del runner.

> Moraleja: la ejecución 2 era un pipeline «verde» que en realidad estaba **rojo**.
> Sin `pipefail`, `tee` se come el código de salida de Maven.
