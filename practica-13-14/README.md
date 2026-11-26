# Prácticas 13 y 14 — Automatización de pruebas y validación continua

**LuxRay** · plataforma educativa de cursos de electricidad
**Rama:** `practica-pruebas` · **Remoto:** `github.com/Chekito2720/Luxray_dep`

---

## Qué contiene este entregable

| Fichero | Descripción |
|---|---|
| [`MATRIZ-AUTOMATIZACION.md`](MATRIZ-AUTOMATIZACION.md) | **Matriz AUT-01 … AUT-08** con las 6 fases, los componentes críticos, todas las pruebas, los resultados y las correcciones |
| [`INFORME-TECNICO.md`](INFORME-TECNICO.md) | Informe técnico de 1–2 cuartillas con la **conclusión de la Fase 6** |
| [`tools/flutter_junit.py`](tools/flutter_junit.py) | Convierte la traza `flutter test --machine` (JSONL) a **JUnit XML**, que es lo que consume el reporter de GitHub |
| [`evidencias/`](evidencias/) | Evidencias de ejecución local y automática |

### Código de pruebas

| Fichero | Pruebas | Ámbito |
|---|---|---|
| `luxraywaer/test/practica13_unitarias_test.dart` | 12 | Unitarias del móvil contra un `HttpServer` real |
| `luxraywaer/test/practica13_integracion_test.dart` | 4 | Integración móvil ↔ servicio ↔ presentación |
| `backend/auth-service/src/test/java/com/luxray/auth/controller/RegistroIntegracionTest.java` | 7 (3 `@Disabled`) | Integración API → servicio → repositorio → base de datos |

### Configuración de CI

`.github/workflows/ci-luxray.yml` — **GitHub Actions**, disparado en `push` y `pull_request` (cualquier rama) y de forma manual.

```yaml
jobs:
  flutter:   # ubuntu-latest · Flutter 3.44.6 pinado
    #   flutter pub get → flutter analyze → flutter test --machine → JUnit XML
    #   → dorny/test-reporter → artefacto
  backend:   # ubuntu-latest · Temurin JDK 21
    #   ./mvnw -B test (Maven 3.9.16 versionado) → Surefire
    #   → dorny/test-reporter → artefacto
```

---

## Cómo ejecutar las pruebas

```bash
# ── Móvil ──────────────────────────────────────────────────────────────
cd luxraywaer
flutter pub get
flutter analyze                     # debe terminar en "No issues found!"
flutter test                       # 174 pruebas
flutter test --machine > ../flutter_test.json
python ../practica-13-14/tools/flutter_junit.py \
       ../flutter_test.json ../flutter_test.xml

# ── Backend ────────────────────────────────────────────────────────────
cd backend
./mvnw -B test                     # 74 pruebas (3 microservicios con tests)
```

---

## Cómo ver la evidencia de la ejecución automática

1. **Pestaña «Actions» del repositorio** → workflow *CI LuxRay*.
2. Cada ejecución corresponde a un `push` o a un `pull_request`; el evento y
   el commit aparecen en la cabecera de la ejecución.
3. En cada job, la pestaña **«Checks»** muestra el resultado de cada prueba
   (JUnit) y el paso *«Archivar resultados»** deja el informe descargable.
4. URL de las ejecuciones de esta práctica:
   * [`44239354054`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44239354054) — ❌ fallo real del backend (diagnosticado y corregido)
   * [`44241518237`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44241518237) — ⚠️ verde **falso** (`tee` enmascaraba el fallo de Maven)
   * [`44347435232`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44347435232) — ✅ verde **real** (con `pipefail` y los tests de backend versionados)
   * [`44365052085`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365052085) — ✅ evento **`push`** (reedición de la documentación)
   * [`44365378884`](https://github.com/Chekito2720/Luxray_dep/actions/runs/44365378884) — ✅ evento **`pull_request`** ([PR #1](https://github.com/Chekito2720/Luxray_dep/pull/1))

---

## Errores encontrados durante la automatización

| # | Error | Causa | Corrección |
|---|---|---|---|
| E-1 | Las pruebas de red devolvían 400 con *«will actually be made…»* | `flutter_test` instala un `HttpOverrides` **global** que bloquea el HTTP real | Desactivarlo en `setUp` y restaurarlo en `tearDown` |
| E-2 | La prueba I-03 se colgaba | `testWidgets` usa una zona **fake-async**: los `Future` de red no se completan | `tester.runAsync(...)` para la E/S real |
| E-3 | Envelope con `data` como array → «sin resultados» | El respaldo legacy enmascaraba la deriva de contrato | Validación de forma → `errorPeticion` |
| E-4 | El convertidor JUnit fallaba | `--machine` emite alguna línea que no es objeto JSON | Filtrar las líneas no `dict` |
| E-5 | `luxraywaer/` no estaba versionada | `.gitignore` raíz la excluía: el CI no podía probar la app | Versionar la app; excluir solo artefactos de build |
| E-6 | El pipeline se declaraba **verde con Maven en rojo** | `./mvnw ... \| tee` devuelve el código de salida de `tee` (0) | `set -o pipefail` antes del pipeline |
| E-7 | Un test **ya trackeado** estaba en rojo (`AuthServiceTest`) | El test exigía el *hash cebo* del login, que no estaba versionado | Versionar `AuthService.java` |

---

## Limitaciones declaradas

* El backend se prueba contra **H2 en memoria** (perfil `test`), no contra
  PostgreSQL 16 real — adaptación documentada en las Prácticas 11 y 12.
* La comunicación móvil ↔ servicio se valida contra un servidor HTTP local
  que implementa el contrato del gateway, no contra el gateway desplegado.
* Las 3 pruebas `@Disabled` **documentan defectos abiertos** (DEF-02, DEF-03,
  DEF-08): no son pruebas que «se pasan», son la especificación ejecutable
  del cierre de cada defecto.
