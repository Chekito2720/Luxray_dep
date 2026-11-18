# PRÁCTICA 12 — Optimización, pruebas de estrés y resiliencia
## LuxRay — aplicación móvil Wear OS (Flutter) + API + PostgreSQL

Auditoría de rendimiento, escalabilidad y capacidad de recuperación de la
aplicación móvil **LuxRay Wear OS**, con estrés del API, pruebas avanzadas de
base de datos, faults controlados de red y **una optimización real** medida
antes y después.

---

## 📄 Documentos

| Documento | Contenido |
|---|---|
| **[INFORME-PRACTICA-12.md](INFORME-PRACTICA-12.md)** | 🏆 **Informe principal** — los 12 apartados, el registro PER-01…PER-11, la tabla comparativa antes/después y las conclusiones técnicas |
| [fase1-diagnostico/DIAGNOSTICO-HEREDADO.md](fase1-diagnostico/DIAGNOSTICO-HEREDADO.md) | Fase 1 · Los 5 problemas heredados de la Práctica 11 y su hipótesis técnica |
| [fase5-resiliencia/MATRIZ-RESILIENCIA.md](fase5-resiliencia/MATRIZ-RESILIENCIA.md) | Fase 5 · Matriz RES-01 … RES-08 con el análisis exigido |
| [fase3-estres-api/estres_api.js](fase3-estres-api/estres_api.js) | Fase 3 · Script de estrés k6 (curva de saturación) |
| [fase4-bd/01_pruebas_avanzadas.sql](fase4-bd/01_pruebas_avanzadas.sql) | Fase 4 · Las 6 pruebas obligatorias de base de datos |
| [fase4-bd/02_optimizacion_bd.sql](fase4-bd/02_optimizacion_bd.sql) | Fase 4 · Benchmark de 20 000 filas antes/después del índice |
| [fase4-bd/03_comparativa_antes_despues.sql](fase4-bd/03_comparativa_antes_despues.sql) | Fase 4 · Comparativa sobre la tabla real |

---

## 📊 Resultados en un vistazo

| Métrica | Antes | Después | Variación |
|---|---|---|---|
| Peticiones HTTP · 10 búsquedas idénticas | 9 (9 en 2 ejecuciones) | **2 – 3** | **−67 % a −78 %** |
| Peticiones HTTP · 20 navegaciones | 27 – 30 | **15** | **−44 % a −50 %** |
| Peticiones HTTP · 8 búsquedas + detalle | 8 | **4** | **−50 %** |
| CPU · 10 búsquedas idénticas | 33 970 – 37 110 ms | **28 900 – 32 910 ms** | **−15 % a −22 %** |
| RAM PSS máxima | 86,37 – 88,07 MB | **84,54 – 84,55 MB** | **−4,0 %** |
| Latencia del endpoint de búsqueda (A/B intercalado) | 27,25 ms (P95 43,44) | **23,36 ms** (P95 35,33) | **−14,3 %** |
| Consulta del buscador (20 000 filas) | 30,73 ms (Seq Scan) | **2,16 ms** (Bitmap Index Scan) | **×14,2** |
| RPS lectura (80 VUs) | 441,83 | 441,83 | 0 % (la API no cambió) |
| RPS escritura (80 VUs) | 14,82 | 14,82 | 0 % (la API no cambió) |
| Errores HTTP (3 600 peticiones) | 0 % | 0 % | 0 % |

### Curva de saturación

| VUs | Lectura: RPS / P95 | Escritura: RPS / P95 |
|---|---|---|
| 1 | 15,74 / 18,67 ms | 1,22 / 915,64 ms |
| 10 | 148,56 / 25,28 ms | 12,43 / 957,07 ms |
| 20 | 265,58 / 63,32 ms | **13,67** / 1 923,55 ms |
| 40 | 393,29 / 131,63 ms | 11,55 / 5 979,80 ms |
| 80 | **441,83** / 256,25 ms | 14,82 / 6 206,10 ms |

**Lectura:** satura de forma suave entre **20 y 40 usuarios** (techo ~440 RPS).
**Escritura:** satura a **10 usuarios** (RPS estancado en ~13) con la latencia
creciendo linealmente — la firma de un recurso bloqueante compartido
(`BCrypt(12)` dentro de la petición).

---

## 🧪 Qué se optimizó

### A) Aplicación móvil — `luxraywaer/lib/services/luxray_api.dart`

| Medida | Antes | Después |
|---|---|---|
| **P12-01** | Sin caché: cada búsqueda repetida descargaba y reparseaba todo | **Caché LRU con TTL (30 s, 20 entradas)** + **coalescencia** de peticiones en vuelo |
| **P12-02** | Sin cancelación: un resultado tardío pisaba al más reciente | **Token de generación**: el resultado obsoleto se descarta |
| **P12-03** | Sin reintento ante fallos transitorios | **Backoff exponencial 250 → 500 ms con jitter ±20 %**, acotado por un presupuesto global de 12 s |
| **P12-04** | `catch-all`: sin resultados = sin red = error 500 | **Seis estados explícitos** con su mensaje y su decisión |
| **P12-05** | Cabeceras dispersas | Cabeceras centralizadas (`Accept`, `User-Agent`) |

### B) Base de datos — índice GIN trigram

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_cursos_titulo_trgm      ON cursos USING GIN (lower(titulo) gin_trgm_ops);
CREATE INDEX idx_cursos_descripcion_trgm ON cursos USING GIN (lower(descripcion) gin_trgm_ops);
```

Convierte el `Seq Scan` del buscador (`LOWER(titulo) LIKE '%texto%'`) en un
`Bitmap Index Scan`: **14,2× más rápido** a escala de producción.

### C) Interfaz — `main.dart` y `resultados_screen.dart`

Token de generación + comprobación de `mounted`; mensajes e iconos distintos
por estado (`Sin conexión…`, `Sin permisos…`, `El servidor no pudo responder…`).

---

## 🛠️ Herramientas escritas para la práctica

En `tools/`:

| Script | Para qué |
|---|---|
| `perfil_movil.py` | Profiling del dispositivo: CPU exacta por `/proc/<pid>/stat`, RAM por `dumpsys meminfo`, frames por `gfxinfo`, peticiones HTTP por relay, y **auto-validación del arnés** antes de medir |
| `ejecutar_estres.py` | Orquestador de k6: 12 escenarios aislados, curva de saturación y estado del contenedor |
| `resiliencia_api.py` | Proxy con averías inyectables (cortar / retener / normal) + verificación de duplicados en PostgreSQL |
| `comparar_api_bd.py` | Comparativa de latencia de la API con y sin el índice |
| `practica-11/tools/relay_dispositivo.py` | Relay TCP **con conteo de peticiones** (ampliado en esta práctica) |

---

## ▶️ Cómo reproducir

```powershell
cd C:\DDI\luxray
docker compose up -d                      # 12 contenedores

# Fase 2 — Profiling del dispositivo (release, emulador Wear OS)
cd luxraywaer; flutter build apk --release --dart-define=LUXRAY_API_BASE=http://10.0.2.2:8099/api
cd ..; python practica-12\tools\perfil_movil.py --etiqueta despues `
        --apk practica-12\evidencias\app-DESPUES-release.apk

# Fase 3 — Estrés de la API
python practica-12\tools\ejecutar_estres.py --niveles 1 5 10 20 40 80 --iteraciones 20 `
        --base-url "http://[::1]:8080/api"

# Fase 4 — Base de datos
cmd /c "type practica-12\fase4-bd\01_pruebas_avanzadas.sql | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
cmd /c "type practica-12\fase4-bd\02_optimizacion_bd.sql     | docker exec -i luxray-cursos-db psql -U luxray -d cursos_db"
python practica-12\tools\comparar_api_bd.py

# Fase 5 — Resiliencia
cd luxraywaer; flutter test test/practica12_resiliencia_test.dart
cd ..; python practica-12\tools\resiliencia_api.py

# Fase 6 — Comparativa antes/después
python practica-12\tools\comparar_api_bd.py
```

---

## 📁 Pruebas añadidas al proyecto

| Fichero | Casos | Contenido |
|---|---|---|
| `luxraywaer/test/practica12_optimizacion_test.dart` | 25 | Caché (TTL, LRU, fallos no memorizados), coalescencia, cancelación, backoff, presupuesto, los 6 estados, cabeceras y **regresión del comportamiento original** |
| `luxraywaer/test/practica12_resiliencia_test.dart` | 10 | RES-01, RES-03, RES-04, RES-05, RES-06, RES-07, RES-08 contra un servidor HTTP con averías **reales** (sockets) |

**Suite completa: 159 pruebas, 159 en verde** · `flutter analyze`: **0 incidencias**

---

## 📁 Evidencias

`evidencias/` contiene la salida literal de cada herramienta:

| Fichero | Contenido |
|---|---|
| `perfil_movil_antes_release.json` / `_consola` | Perfilado de la versión original |
| `perfil_movil_despues_release.json` / `_consola` | Perfilado de la versión optimizada |
| `fase3_estres_consolidado.md` / `.json` | Curva de saturación (12 escenarios) |
| `fase3_estres_*_consola.txt` | Salida de k6 por escenario |
| `fase4_pruebas_avanzadas.txt` | Las 6 pruebas de BD con sus planes de ejecución |
| `fase4_optimizacion_bd.txt` | Benchmark de 20 000 filas antes/después |
| `fase4_comparativa_bd.txt` | Comparativa sobre la tabla real |
| `fase5_resiliencia_api.md` / `.json` | RES-02, RES-07, RES-06 contra la API real |
| `fase6_comparativa_api_bd.*` | Latencia de la API con y sin el índice |
| `relay_8099.txt` / `relay_estado.json` | Peticiones HTTP observadas en el dispositivo |
| `wb11_disp_sin_conectividad.png` · `wb11_disp_recuperacion.png` | Capturas del wearable durante el corte y la recuperación |
| `codigo-original/` · `codigo-optimizado/` | Código fuente de ambas versiones |
| `app-ANTES*.apk` · `app-DESPUES*.apk` | APKs de ambas versiones (debug y release) |

---

## ⚠️ Nota sobre el entorno

En esta máquina el puerto **IPv4 `127.0.0.1:8080` está ocupado por otro
servidor local** (Apache en WSL) y el API Gateway de Docker responde
únicamente por **IPv6 en `[::1]:8080`**. Por eso:

* `k6` debe apuntar a `http://[::1]:8080/api` (resuelve `localhost` a IPv4).
* El wearable, que usa `10.0.2.2` (alias del loopback IPv4 del host), necesita
  un **relay TCP** (`practica-11/tools/relay_dispositivo.py`) para alcanzar el
  gateway.

Es el mismo hallazgo **DEF-06** documentado en la Práctica 11.
