# PRÁCTICA 11 — Pruebas de White Box · LuxRay

Auditoría técnica de la aplicación móvil (**LuxRay Wear OS**, Flutter), del
sitio web y sus servicios (**Angular 21 + Spring Boot 3.4.1**) y de la base de
datos (**PostgreSQL 16**), aplicando técnicas de pruebas White Box.

---

## 📄 Documentos

| Documento | Contenido |
|---|---|
| **[ENTREGABLE-PRACTICA-11.md](ENTREGABLE-PRACTICA-11.md)** | 🏆 **Documento único de entrega** — los 12 apartados con todos los resultados, las capturas del dispositivo embebidas, la matriz de pruebas completa, el registro de métricas y las 10 preguntas individuales |
| [INFORME-PRUEBA-11-WHITEBOX.md](INFORME-PRUEBA-11-WHITEBOX.md) | Informe técnico detallado (misma información, formato de auditoría interna con referencias a ficheros) |
| [fase1-analisis-estatico/REPORTE-ANALISIS-ESTATICO.md](fase1-analisis-estatico/REPORTE-ANALISIS-ESTATICO.md) | Fase 1 · 5 herramientas ejecutadas + 7 puntos de revisión obligatorios + 24 hallazgos |
| [fase2-movil-whitebox/FLUJOS-CONTROL.md](fase2-movil-whitebox/FLUJOS-CONTROL.md) | Fase 2 · 9 CFG en Mermaid, caminos independientes, valores límite y complejidad ciclomática |
| [fase3-web-api/luxray-whitebox-fase3.postman_collection.json](fase3-web-api/luxray-whitebox-fase3.postman_collection.json) | Fase 3 · Colección Postman v2.1 (32 peticiones, 46 aserciones) |
| [fase4-base-datos/01_crud_integridad_cursos.sql](fase4-base-datos/01_crud_integridad_cursos.sql) · [03_planes_ejecucion.sql](fase4-base-datos/03_planes_ejecucion.sql) | Fase 4 · CRUD, restricciones, transacciones, planes de ejecución |
| [fase5-conectividad/carga_k6.js](fase5-conectividad/carga_k6.js) | Fase 5 · Prueba de carga k6 (1 / 5 / 10 VUs, lectura y escritura) |
| [fase6-defectos/DEFECTOS.md](fase6-defectos/DEFECTOS.md) | Fase 6 · 18 defectos con reproducción, severidad, evidencia y corrección |

---

## 📊 Resultados en un vistazo

| Métrica | Resultado | Objetivo |
|---|---|---|
| Cobertura de instrucciones (móvil) | **93,33 %** | ≥ 85 % ✔ |
| Cobertura de decisiones (móvil) | **87,35 %** | ≥ 80 % ✔ |
| Cobertura de condiciones (móvil) | **100 %** (13/13 mutantes abatidos) | — ✔ |
| Cobertura de `AuthService` (backend) | **94,3 %** instr · **100 %** branch | ≥ 85 % / ≥ 80 % ✔ |
| Pérdida de paquetes | **0 %** | 0 % ✔ |
| Latencia HTTP media | **23,5 ms** | ≤ 500 ms ✔ |
| Latencia P95 en carga (escritura, 10 VUs) | **667 ms** | ≤ 500 ms ⚠ |
| Tiempo de consulta a BD | **0,5 – 17 ms** | ≤ 200 ms ✔ |
| Respuestas exitosas | **100 %** (10/10 y 320/320) | 100 % ✔ |
| Defectos detectados | **18** (2 corregidos y verificados) | ≥ 3 ✔ |

---

## 🧪 Código fuente de las pruebas

| Ubicación | Contenido |
|---|---|
| `luxraywaer/test/fase2_whitebox_test.dart` | 44 casos · M1 (validación), M2 (procesamiento), M3 (comunicación) |
| `luxraywaer/test/fase2_whitebox_tema_test.dart` | 41 casos · M4 (*switches* de badges), M5 (modelos), M6 (layout) |
| `luxraywaer/test/fase2_whitebox_detalle_test.dart` | 14 casos · Pantalla de detalle y rutas de navegación |
| `luxraywaer/test/fase2_whitebox_navegacion_test.dart` | 5 casos · Navegación del raíz de la aplicación |
| `luxraywaer/test/regresion_def04_navegacion_test.dart` | 2 casos · Regresión de DEF-04 |
| `backend/common-lib/src/test/.../GlobalExceptionHandlerHttpStatusTest.java` | 9 casos (2 deshabilitados = reproducción de DEF-02/03) |
| `backend/common-lib/src/test/.../JwtTokenProviderTest.java` | 8 casos · Ciclo de vida del JWT |
| `backend/auth-service/src/test/.../AuthServiceRamasTest.java` | 23 casos · Ramas del servicio de autenticación |
| `fase2-movil-whitebox/codigo-fuente/` | Copia de entrega de las pruebas móviles (por DEF-14, `luxraywaer/` está en `.gitignore`) |

**Total: 124 pruebas móviles** (antes: 17) + **40 pruebas backend** nuevas
(antes: 24) = **164**.

---

## 🛠️ Herramientas escritas para la práctica

En `tools/`:

| Script | Para qué |
|---|---|
| `mutacion_condiciones.py` | Mide la **cobertura de condiciones** (que Dart no instrumenta) mediante mutación; restaura el código byte a byte y verifica el hash SHA-256 |
| `analizar_cobertura.py` | Convierte el `lcov.info` en tablas de cobertura por archivo y por método crítico |
| `sonda_http.py` | Batería de 47 peticiones de contrato contra el API Gateway con código HTTP, tiempo y tamaño |
| `medir_conectividad.py` | Ping (20 paquetes, 4 destinos), latencia HTTP, tasa de transferencia y prueba de interrupción con proxy TCP controlable |
| `ejecutar_carga.py` | Orquestador de los 6 escenarios aislados de carga con k6 |
| `ejecutar_concurrencia.py` | Los 3 escenarios de concurrencia de la base de datos con sesiones psql simultáneas |
| `relay_dispositivo.py` | Relay TCP controlable para las pruebas de interrupción sobre el **dispositivo real** |

---

## ▶️ Cómo reproducir la práctica

```powershell
# 0. Entorno
cd C:\DDI\luxray
docker compose up -d                    # 12 contenedores (3 PostgreSQL, Eureka, gateway, 4 microservicios, S3, ES, PWA)

# 1. Fase 1 — Analisis estatico
cd luxraywaer; flutter analyze
cd ..\backend; mvn clean compile -DskipTests
cd ..\frontend-web; npm run build; npx ng test --no-watch

# 2. Fase 2 — White Box movil (cobertura + mutacion)
cd ..\luxraywaer; flutter test --coverage --branch-coverage --coverage-path=coverage/lcov.info
cd ..; python practica-11\tools\analizar_cobertura.py
python practica-11\tools\mutacion_condiciones.py          # ~5 min

# 3. Fase 3 — White Box del backend y del API
cd backend; mvn test                                      # 67 pruebas + informe JaCoCo
cd ..; newman run practica-11\fase3-web-api\luxray-whitebox-fase3.postman_collection.json `
        --env-var baseUrl=http://localhost:8080/api --reporters cli,json
python practica-11\tools\sonda_http.py

# 4. Fase 4 — White Box de la base de datos
cmd /c "type practica-11\fase4-base-datos\01_crud_integridad_cursos.sql | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
python practica-11\tools\ejecutar_concurrencia.py

# 5. Fase 5 — Conectividad, rendimiento y carga
python practica-11\tools\medir_conectividad.py --host 127.0.0.1 --ping-extra 192.168.1.153 172.24.0.1 8.8.8.8
python practica-11\tools\ejecutar_carga.py

# 6. Informe
#    practica-11\INFORME-PRUEBA-11-WHITEBOX.md
```

---

## 📁 Evidencias

`evidencias/` contiene la salida **literal** de cada herramienta:

| Fichero | Contenido |
|---|---|
| `fase1_flutter_analyze.txt` | `flutter analyze` → 0 incidencias |
| `fase1_mvn_compile.txt` | `mvn clean compile` → BUILD SUCCESS |
| `fase1_ng_build.txt` | `npm run build` → 9 avisos del compilador |
| `fase1_ng_test.txt` | `ng test` → 44/45 (la prueba obsoleta de DEF-12) |
| `fase2_cobertura_movil.md` / `.json` | Cobertura de instrucciones y decisiones por archivo y por método crítico |
| `fase2_mutacion_condiciones.md` / `.json` / `_consola.txt` | Los 13 mutantes de condición y su resultado |
| `fase3_newman_consola.txt` / `.json` | 32 peticiones, 46 aserciones, 11 en rojo (= defectos) |
| `fase3_sonda_http.json` | 47 peticiones de contrato con código, tiempo y tamaño |
| `fase3_mvn_test_jacoco.txt` | 67 pruebas backend + informe JaCoCo |
| `fase4_crud_integridad.txt` | Salida de los scripts SQL de CRUD e integridad |
| `fase4_concurrencia.txt` | Los 3 escenarios de concurrencia con sus valores verificados |
| `fase5_mediciones.json` / `fase5_consola.txt` | Ping, latencia HTTP, tasa de transferencia e interrupción |
| `fase5_carga_consolidado.md` / `.json` | Los 6 escenarios de carga k6 |
| `wb11_disp_*.png` | Capturas del **dispositivo real** (Wear OS emulado): estado inicial defectuoso, home corregido, resultado real, sin conectividad y recuperación |

---

## ⚙️ Cambios aplicados al proyecto

| Cambio | Motivo | Estado |
|---|---|---|
| `luxraywaer/lib/main.dart` → guarda de `_pager.page == 2` en `_buildDetalle()` | **DEF-04**: la app arrancaba en la pantalla de resultados | Corregido y verificado |
| `luxraywaer/lib/services/luxray_api.dart` → `_kDefaultBase` con `String.fromEnvironment('LUXRAY_API_BASE')` | **DEF-05**: URL base no configurable (valor por defecto **idéntico**) | Corregido y verificado |
| `backend/pom.xml` → plugin `jacoco-maven-plugin` | Medir la cobertura del backend | Añadido |
| `luxraywaer/test/*.dart` (5 ficheros nuevos) | Fase 2 | Añadido |
| `backend/*/src/test/...` (3 clases nuevas) | Fase 3 | Añadido |
| `practica-11/**` | Toda la práctica | Añadido |
