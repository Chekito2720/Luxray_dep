# Fase 2 — Grafos de flujo de control (CFG) de los métodos críticos

**Aplicación evaluada:** LuxRay Wear OS (`luxraywaer`) — Flutter 3.44.6 / Dart 3.12.2
**Versión de la app:** 1.0.0+1 · Android / Wear OS · `com.luxray.waer`
**Módulo bajo prueba:** `lib/services/luxray_api.dart` y `lib/models/busqueda.dart`

Los grafos se expresan en **Mermaid** (renderizables en GitHub, VS Code, Obsidian
y mermaid.live) y se acompaña la tabla decomplejidad ciclomática de McCabe.

---

## Convenciones

| Símbolo | Significado |
|---|---|
| ⬭ (elipse) | Entrada / salida del método |
| ▭ (rectángulo) | Instrucción (sentencia) |
| ◇ (rombo) | Decisión (condición) |
| ▱ (rombo partido) | Condición compuesta (`||`, `&&`) |
| ▭ con etiqueta `EX` | Bloque de gestión de excepción (`catch`) |

---

## M1 — `LuxrayApi.sanitizar(String q)` · VALIDACIÓN

**Ubicación:** `lib/services/luxray_api.dart:39-42`
**Complejidad ciclomática (McCabe):** `CC = 1 + decisiones = 1 + 1 = 2`

```mermaid
flowchart TD
    A([INICIO<br/>sanitizar(q)]) --> B["q.replaceAll(_ctrl, '')<br/>.trim()"]
    B --> C{"limpio.length > kMaxQuery ?"}
    C -->|VERDADERO| D["substring(0, kMaxQuery)"]
    C -->|FALSO| E["limpio (sin tocar)"]
    D --> F([FIN<br/>String])
    E --> F
```

**Caminos independientes**

| ID | Condición | Camino | Caso de prueba |
|---|---|---|---|
| M1-P1 | `limpio.length > kMaxQuery` = V | Normaliza y **trunca** | `M1-P1 · camino verdadero` |
| M1-P2 | `limpio.length > kMaxQuery` = F | Normaliza y **devuelve intacto** | `M1-P2 · camino falso` |

**Valores límite ejecutados**

| Entrada | Longitud | Resultado | Caso |
|---|---|---|---|
| `'b' * 80` | 80 (límite exacto) | 80 chars, sin truncar | `M1-LIM · valor límite exacto` |
| `'c' * 81` | 81 (límite + 1) | 80 chars, truncado | `M1-LIM · valor límite` |
| `''` | 0 | `''` | `M1-VAC` |
| `'    '` | 4 | `''` (trim) | `M1-VAC` |
| `'  a\tb\nc\x00' + 'z'*100` | >80 | normalizado y truncado a 80 | `M1-IDEM` |

**Condición atómica y su mutador**

| Condición | Archivo:línea | Mutador | Resultado del mutador |
|---|---|---|---|
| `limpio.length > kMaxQuery` | `luxray_api.dart:41` | `MUT-C1` → `> kMaxQuery + 1` | **ABATIDO** (la suite detecta el recorte incorrecto) |

> Observación: el mutador equivalente `> kMaxQuery` → `>= kMaxQuery` **no** puede
> abatirse porque es semánticamente equivalente (`substring(0, 80)` sobre una cadena
> de 80 caracteres devuelve la misma cadena). Se documenta como *mutante equivalente*,
> no como un hueco de cobertura.

---

## M2 — `BusquedaResponse.fromSearchApiResponse(...)` · PROCESAMIENTO

**Ubicación:** `lib/models/busqueda.dart:47-51` (+ `fromJson` 32-43, `_list` 83-89, `_extractSources` 92-100)
**Complejidad ciclomática de `fromSearchApiResponse`:** `CC = 2`

```mermaid
flowchart TD
    A([INICIO<br/>fromSearchApiResponse(json)]) --> B["data = json['data']"]
    B --> C{"data is Map&lt;String, dynamic&gt; ?"}
    C -->|VERDADERO| D["fromJson(data)"]
    D --> E{"json['cursos'] is! List ?"}
    D --> F{"json['lecciones'] is! List ?"}
    D --> G{"json['conceptos'] is! List ?"}
    D --> H{"json['faqs'] is! List ?"}
    E -->|VERDADERO| E1["[]"] 
    E -->|FALSO| E2["whereType&lt;Map&gt;() → map(source) → whereType&lt;Map&gt;() → map(fromJson)"]
    F -->|VERDADERO| F1["[]"]
    F -->|FALSO| F2["idem"]
    G -->|VERDADERO| G1["[]"]
    G -->|FALSO| G2["idem"]
    H -->|VERDADERO| H1["[]"]
    H -->|FALSO| H2["idem"]
    E1 --> Z([FIN])
    E2 --> Z
    F1 --> Z
    F2 --> Z
    G1 --> Z
    G2 --> Z
    H1 --> Z
    H2 --> Z
    C -->|FALSO| I["BusquedaResponse.empty()"]
    I --> Z
```

**Caminos independientes**

| ID | Condición | Resultado | Caso de prueba |
|---|---|---|---|
| M2-P1 | `data is Map` = V | Parseo completo del envelope | `M2-P1` |
| M2-P2 | `data is Map` = F | `empty()` (total 0, sin datos) | `M2-P2` |
| M2-D6 | `raw is! List` = V (lista ausente / tipo incorrecto) | Lista vacía, sin excepción | `M2-D6` (x2) |
| M2-D6' | `raw is! List` = F (lista válida) | Lista filtrada y mapeada | `M2-P1`, `M2-LEG` |
| M2-D8 | hit sin `source` / `source` nulo | Hit descartado, el resto sobrevive | `M2-D7/D8` |

**Condiciones atómicas y sus mutadores**

| Condición | Archivo:línea | Mutador | Resultado |
|---|---|---|---|
| `data is Map<String, dynamic>` | `busqueda.dart:49` | `MUT-C10` → `data is! Map` | **ABATIDO** |
| `raw is! List` (`_list`) | `busqueda.dart:84` | `MUT-C11` → `raw is List` | **ABATIDO** |
| `raw is! List` (`_extractSources`) | `busqueda.dart:93` | `MUT-C12` → `raw is List` | **ABATIDO** |
| `hit['source'] as Map<String,dynamic>?` | `busqueda.dart:96` | `MUT-C13` → `as Map<String,dynamic>` | **ABATIDO** |

---

## M3 — `LuxrayApi.buscar(String q)` + `_buscarEnSearch` · COMUNICACIÓN

**Ubicación:** `lib/services/luxray_api.dart:50-62` y `64-85`
**Complejidad ciclomática:** `buscar` → `CC = 3`; `_buscarEnSearch` → `CC = 5`
(rama del `||` compuesto = 2, `is Map` = 1, `catch` = 1, `+1` base)

```mermaid
flowchart TD
    A([INICIO<br/>buscar(q)]) --> B["query = sanitizar(q)"]
    B --> C{"query.length &lt; 2 ?"}
    C -->|VERDADERO| D["BusquedaResponse.empty()<br/>(NO se genera petición HTTP)"]
    C -->|FALSO| E["await _buscarEnSearch(query)"]

    subgraph SEARCH ["_buscarEnSearch"]
        E1["GET {base}/search?q=..&page=0&size=10<br/>Accept: application/json<br/>timeout 6 s"] --> E2{"resp.statusCode &lt; 200<br/>O resp.statusCode &ge; 300 ?"}
        E2 -->|VERDADERO| E3["empty()"]
        E2 -->|FALSO| E4["body = json.decode(resp.body)"]
        E4 --> E5{"body is Map&lt;String, dynamic&gt; ?"}
        E5 -->|VERDADERO| E6["fromSearchApiResponse(body)"]
        E5 -->|FALSO| E3
        E1 -.->|excepción / timeout| EX[["catch (_) → empty()"]]
        E2 -.->|excepción / timeout| EX
        E4 -.->|JSON inválido| EX
        E5 -.->|excepción / timeout| EX
    end

    E6 --> F{"searchResponse.total &gt; 0 ?"}
    E3 --> F
    F -->|VERDADERO| G([FIN<br/>devuelve searchResponse])
    F -->|FALSO| H["await _buscarEnCursosLegacy(query)<br/>GET {base}/cursos/buscar?q=..<br/>timeout 6 s"]
    H --> H2{"mismo rango de status y de tipo"}
    H2 -->|error| I["empty()"]
    H2 -->|2xx + Map| J["fromApiResponse(body)"]
    H -.->|excepción| I
    D --> Z([FIN])
    I --> Z
    J --> Z
```

**Caminos independientes de `buscar`**

| ID | Condición | Comportamiento observable | Caso de prueba |
|---|---|---|---|
| M3-P1 | `query.length < 2` = V | **0 peticiones HTTP** | `M3-P1`, `M3-P1b` |
| M3-P2 | `total > 0` = V | Usa `/search` y **no** hace fallback | `M3-P2` |
| M3-P3 | `total > 0` = F | Hace fallback a `/cursos/buscar` | `M3-P3` |
| M3-D3/D4 | status fuera de `[200,300)` | Descarta la respuesta y cae al fallback | `M3-D3/D4` (x3: 500, 503, 199, 300) |
| M3-D5 | `body is! Map` | Respuesta vacía | `M3-D5` |
| M3-D6 | excepción / timeout | Respuesta vacía, **no propaga** | `M3-D6` (x2) |

**Condiciones atómicas y sus mutadores**

| Condición | Archivo:línea | Mutador | Resultado |
|---|---|---|---|
| `query.length < 2` | `luxray_api.dart:52` | `MUT-C2` → `<= 2` | **ABATIDO** |
| `searchResponse.total > 0` | `luxray_api.dart:56` | `MUT-C3` → `>= 0` | **ABATIDO** |
| `resp.statusCode < 200 \|\| resp.statusCode >= 300` | `luxray_api.dart:74` | `MUT-C4` → `\|\|` por `&&` | **ABATIDO** |
| `resp.statusCode < 200` | `luxray_api.dart:74` | `MUT-C5` → `<= 200` | **ABATIDO** |
| `body is Map<String, dynamic>` | `luxray_api.dart:78` | `MUT-C6` → `body is! Map` | **ABATIDO** |
| `resp.statusCode < 200 \|\| resp.statusCode >= 300` | `luxray_api.dart:97` | `MUT-C7` → `\|\|` por `&&` | **ABATIDO** |
| `resp.statusCode < 200` | `luxray_api.dart:97` | `MUT-C8` → `<= 200` | **ABATIDO** |
| `body is Map<String, dynamic>` | `luxray_api.dart:101` | `MUT-C9` → `body is! Map` | **ABATIDO** |

---

## Complejidad ciclomática de los métodos (McCabe)

Método de conteo: `CC = 1 + número de decisiones` (if, switch/case, `catch`,
operadores `&&` / `||` que multiplican caminos, ternario `? :`).

| Método | Decisiones | `CC` | Consecuencia |
|---|---|---|---|
| `LuxrayApi.sanitizar` | 1 (ternario) | **2** | Bajo. Sin riesgo. |
| `BusquedaResponse.fromSearchApiResponse` | 1 (`is Map`) | **2** | Bajo. |
| `BusquedaResponse.fromJson` | 4 (`??` por campo) | **5** | Bajo; los `??` son coalescencia, no ramas de control. |
| `BusquedaResponse._extractSources` | 3 | **4** | Bajo. |
| `LuxrayApi.buscar` | 2 | **3** | Bajo. |
| `LuxrayApi._buscarEnSearch` | 2 + `catch` | **5** | Medio: concentra validación de status, parseo y manejo de excepción en un solo método. |
| **`AuthService.resumenPorIds`** (backend) | 2 (`||`, `>`) | **3** | Bajo. |
| **`CursosService.buscar`** (backend) | 4 fuentes + filtros + `max` | **≈ 7** | **El más alto del backend**: mezcla 4 fuentes de datos (cursos, lecciones, glosario, FAQ) con normalización y truncado. |
| **`DashboardService.dashboard`** (backend) | 2 ramas (ADMIN vs usuario) × agregaciones | **≈ 9** | **El más alto del proyecto**: bifurca por rol y encadena 3 llamadas HTTP a otros servicios (`WebClient`) con `ThreadLocal` para el token. |

### Método con mayor complejidad y sus consecuencias

**`DashboardService.dashboard()`** (backend, `analytics-service`) es el método
con mayor complejidad ciclomática del proyecto, y **`AuthService.login()` /
`CursosService.buscar()`** le siguen en el backend móvil.

Consecuencias medibles en este proyecto:

1. **Acoplamiento por rol dentro del método.** `dashboard()` decide entre
   `buildAdminDashboard()` y `buildUserDashboard()` en el mismo cuerpo. Un cambio
   en una rama obliga a revisar la otra: los dos caminos no se pueden probar de
   forma independiente sin mocks pesado (se observa en la cobertura JaCoCo: la
   clase `DashboardService` queda por debajo del objetivo por esta razón).
2. **Dependencias ocultas.** `DashboardService` usa `WebClient` + `ThreadLocal`
   para propagar el JWT a tres llamadas encadenadas. El método depende de
   estado implícito (`TOKEN_HOLDER`) que no aparece en su firma: es un
   acoplamiento invisible que un `refactor` innocuo rompería.
3. **Latencia multiplicada.** Medido en la Fase 5:
   `GET /api/analytics/dashboard` tarda **≈ 6 600 ms** en la primera llamada
   (ver `evidencias/fase3_sonda_http.json`), frente a **≈ 25 ms** de
   `GET /api/cursos`. El método encadena 3 llamadas de red en serie: el tiempo
   total es la suma de las tres.
4. **Datos no deterministas.** Dos de las métricas administrativas se calculan
   con `new Random()` dentro del método, de modo que dos llamadas idénticas
   devuelven valores distintos. Esto impide aserciones de igualdad en pruebas
   y hace que el endpoint no sea idempotente desde el punto de vista del
   cliente (ver hallazgo **DEF-07**).
