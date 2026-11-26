# Fase 2 — Cobertura de condiciones (mutacion) — Aplicacion movil

- **Proyecto**: `C:\DDI\luxray\luxraywaer`
- **Suite**: `test/fase2_whitebox_test.dart`
- **Fecha**: 2026-11-11T09:59:34
- **Mutantes aplicados**: 13 (13 aplicables, 0 no aplicables)
- **Mutantes ABATIDOS**: 13
- **Mutantes SOBREVIVIENTES**: 0
- **Score de mutacion**: 100.00 %
- **Condiciones atomicas cubiertas**: 13/13 = **100.00 %**

> Un mutante ABATIDO demuestra que la condicion se evalua de forma discriminante (T/F) en la suite. Un mutante SOBREVIVIENTE es un hueco de cobertura de condiciones.

| ID | Metodo | Condicion atomica | Archivo:Linea | Estado | Cubierta | Test que lo detecta |
|---|---|---|---|---|---|---|
| MUT-C1 | M1 sanitizar | `limpio.length > kMaxQuery` | `lib/services/luxray_api.dart:52` | ABATIDO | SI | - |
| MUT-C2 | M3 buscar | `query.length < 2` | `lib/services/luxray_api.dart:63` | ABATIDO | SI | - |
| MUT-C3 | M3 buscar | `searchResponse.total > 0` | `lib/services/luxray_api.dart:67` | ABATIDO | SI | - |
| MUT-C4 | M3 _buscarEnSearch | `resp.statusCode < 200 || resp.statusCode >= 300` | `lib/services/luxray_api.dart:85` | ABATIDO | SI | - |
| MUT-C5 | M3 _buscarEnSearch | `resp.statusCode < 200` | `lib/services/luxray_api.dart:85` | ABATIDO | SI | - |
| MUT-C6 | M3 _buscarEnSearch | `body is Map<String, dynamic>` | `lib/services/luxray_api.dart:89` | ABATIDO | SI | - |
| MUT-C7 | M3 _buscarEnCursosLegacy | `resp.statusCode < 200 || resp.statusCode >= 300` | `lib/services/luxray_api.dart:108` | ABATIDO | SI | - |
| MUT-C8 | M3 _buscarEnCursosLegacy | `resp.statusCode < 200` | `lib/services/luxray_api.dart:108` | ABATIDO | SI | - |
| MUT-C9 | M3 _buscarEnCursosLegacy | `body is Map<String, dynamic>` | `lib/services/luxray_api.dart:112` | ABATIDO | SI | - |
| MUT-C10 | M2 fromSearchApiResponse | `data is Map<String, dynamic>` | `lib/models/busqueda.dart:49` | ABATIDO | SI | - |
| MUT-C11 | M2 _list | `raw is! List` | `lib/models/busqueda.dart:84` | ABATIDO | SI | - |
| MUT-C12 | M2 _extractSources | `raw is! List` | `lib/models/busqueda.dart:93` | ABATIDO | SI | - |
| MUT-C13 | M2 _extractSources | `hit['source'] as Map<String, dynamic>?` | `lib/models/busqueda.dart:96` | ABATIDO | SI | - |

## Mutantes

### MUT-C1 — M1 sanitizar
- **Condicion**: `limpio.length > kMaxQuery`
- **Ubicacion**: `lib/services/luxray_api.dart:52`
- **Original**: `return limpio.length > kMaxQuery ? limpio.substring(0, kMaxQuery) : limpio;`
- **Mutado**: `return limpio.length > kMaxQuery + 1 ? limpio.substring(0, kMaxQuery) : limpio;`
- **Efecto del mutante**: Condicion de longitud: el recorte deja de activarse al superar 80.
- **Resultado**: **ABATIDO** (rc=1, 16.9 s)

### MUT-C2 — M3 buscar
- **Condicion**: `query.length < 2`
- **Ubicacion**: `lib/services/luxray_api.dart:63`
- **Original**: `if (query.length < 2) return BusquedaResponse.empty();`
- **Mutado**: `if (query.length <= 2) return BusquedaResponse.empty();`
- **Efecto del mutante**: Puerta de longitud minima: una query de 2 caracteres ya se rechaza.
- **Resultado**: **ABATIDO** (rc=1, 20.1 s)

### MUT-C3 — M3 buscar
- **Condicion**: `searchResponse.total > 0`
- **Ubicacion**: `lib/services/luxray_api.dart:67`
- **Original**: `if (searchResponse.total > 0) {`
- **Mutado**: `if (searchResponse.total >= 0) {`
- **Efecto del mutante**: Deteccion de resultados: nunca se dispara el fallback al endpoint legacy.
- **Resultado**: **ABATIDO** (rc=1, 10.5 s)

### MUT-C4 — M3 _buscarEnSearch
- **Condicion**: `resp.statusCode < 200 || resp.statusCode >= 300`
- **Ubicacion**: `lib/services/luxray_api.dart:85`
- **Original**: `if (resp.statusCode < 200 || resp.statusCode >= 300) {`
- **Mutado**: `if (resp.statusCode < 200 && resp.statusCode >= 300) {`
- **Efecto del mutante**: Rango de status: los 3xx/4xx/5xx dejan de tratarse como error.
- **Resultado**: **ABATIDO** (rc=1, 16.7 s)

### MUT-C5 — M3 _buscarEnSearch
- **Condicion**: `resp.statusCode < 200`
- **Ubicacion**: `lib/services/luxray_api.dart:85`
- **Original**: `if (resp.statusCode < 200 || resp.statusCode >= 300) {`
- **Mutado**: `if (resp.statusCode <= 200 || resp.statusCode >= 300) {`
- **Efecto del mutante**: Limite inferior del rango 2xx: el 200 pasa a considerarse error.
- **Resultado**: **ABATIDO** (rc=1, 16.6 s)

### MUT-C6 — M3 _buscarEnSearch
- **Condicion**: `body is Map<String, dynamic>`
- **Ubicacion**: `lib/services/luxray_api.dart:89`
- **Original**: `if (body is Map<String, dynamic>) {`
- **Mutado**: `if (body is! Map<String, dynamic>) {`
- **Efecto del mutante**: Tipo del cuerpo JSON: nunca se parsea una respuesta valida.
- **Resultado**: **ABATIDO** (rc=1, 16.4 s)

### MUT-C7 — M3 _buscarEnCursosLegacy
- **Condicion**: `resp.statusCode < 200 || resp.statusCode >= 300`
- **Ubicacion**: `lib/services/luxray_api.dart:108`
- **Original**: `if (resp.statusCode < 200 || resp.statusCode >= 300) {`
- **Mutado**: `if (resp.statusCode < 200 && resp.statusCode >= 300) {`
- **Efecto del mutante**: Rango de status del fallback: los errores dejan de tratarse como error.
- **Resultado**: **ABATIDO** (rc=1, 16.0 s)

### MUT-C8 — M3 _buscarEnCursosLegacy
- **Condicion**: `resp.statusCode < 200`
- **Ubicacion**: `lib/services/luxray_api.dart:108`
- **Original**: `if (resp.statusCode < 200 || resp.statusCode >= 300) {`
- **Mutado**: `if (resp.statusCode <= 200 || resp.statusCode >= 300) {`
- **Efecto del mutante**: Limite inferior del rango 2xx del fallback.
- **Resultado**: **ABATIDO** (rc=1, 16.3 s)

### MUT-C9 — M3 _buscarEnCursosLegacy
- **Condicion**: `body is Map<String, dynamic>`
- **Ubicacion**: `lib/services/luxray_api.dart:112`
- **Original**: `if (body is Map<String, dynamic>) {`
- **Mutado**: `if (body is! Map<String, dynamic>) {`
- **Efecto del mutante**: Tipo del cuerpo JSON del fallback.
- **Resultado**: **ABATIDO** (rc=1, 16.5 s)

### MUT-C10 — M2 fromSearchApiResponse
- **Condicion**: `data is Map<String, dynamic>`
- **Ubicacion**: `lib/models/busqueda.dart:49`
- **Original**: `if (data is Map<String, dynamic>) return BusquedaResponse.fromJson(data);`
- **Mutado**: `if (data is! Map<String, dynamic>) return BusquedaResponse.fromJson(data);`
- **Efecto del mutante**: Envelope: una respuesta valida se descarta como invalida.
- **Resultado**: **ABATIDO** (rc=1, 15.9 s)

### MUT-C11 — M2 _list
- **Condicion**: `raw is! List`
- **Ubicacion**: `lib/models/busqueda.dart:84`
- **Original**: `if (raw is! List) return [];`
- **Mutado**: `if (raw is List) return [];`
- **Efecto del mutante**: Guarda de tipo de _list: una lista legitima se descarta.
- **Resultado**: **ABATIDO** (rc=1, 3.0 s)

### MUT-C12 — M2 _extractSources
- **Condicion**: `raw is! List`
- **Ubicacion**: `lib/models/busqueda.dart:93`
- **Original**: `if (raw is! List) return [];`
- **Mutado**: `if (raw is List) return [];`
- **Efecto del mutante**: Guarda de tipo de _extractSources: una lista legitima se descarta.
- **Resultado**: **ABATIDO** (rc=1, 3.8 s)

### MUT-C13 — M2 _extractSources
- **Condicion**: `hit['source'] as Map<String, dynamic>?`
- **Ubicacion**: `lib/models/busqueda.dart:96`
- **Original**: `.map((hit) => hit['source'] as Map<String, dynamic>?)`
- **Mutado**: `.map((hit) => hit['source'] as Map<String, dynamic>)`
- **Efecto del mutante**: source nullable: un hit sin source revienta el parseo completo.
- **Resultado**: **ABATIDO** (rc=1, 16.4 s)
