# Fase 2 — Cobertura de la aplicacion movil (Flutter / Wear OS)

- **Fuente de datos**: `C:\DDI\luxray\luxraywaer\coverage\lcov.info` (generado por `flutter test --coverage --branch-coverage`)
- **Cobertura de INSTRUCCIONES (statements)**: **93.33 %** (490/525 lineas ejecutables)
- **Cobertura de DECISIONES (branches)**: **87.35 %** (145/166 ramas)

## Detalle por archivo

| Archivo | Instrucciones | Branch | Lineas sin cubrir | Ramas sin cubrir |
|---|---|---|---|---|
| `lib\main.dart` | 46/67 (68.66 %) | 15/27 (55.56 %) | [17, 18, 83, 85, 86, 87, 90, 91, 92, 95, 96, 97, 98, 100, 145, 146, 147, 151, 152, 153, 166] | ['L17/b0/r0', 'L83/b0/r0', 'L85/b0/r0', 'L86/b0/r0', 'L90/b0/r0', 'L91/b0/r0', 'L95/b0/r0', 'L96/b0/r0', 'L126/b0/r0', 'L144/b0/r0', 'L150/b0/r0', 'L165/b0/r0'] |
| `lib\models\busqueda.dart` | 85/85 (100.0 %) | 22/22 (100.0 %) | - | - |
| `lib\screens\detalle_concepto_screen.dart` | 67/68 (98.53 %) | 16/18 (88.89 %) | [80] | ['L36/b0/r0', 'L80/b0/r0'] |
| `lib\screens\home_screen.dart` | 73/74 (98.65 %) | 18/19 (94.74 %) | [147] | ['L147/b0/r0'] |
| `lib\screens\resultados_screen.dart` | 115/125 (92.0 %) | 25/29 (86.21 %) | [137, 138, 139, 140, 141, 151, 152, 153, 154, 155] | ['L136/b0/r0', 'L141/b0/r0', 'L150/b0/r0', 'L155/b0/r0'] |
| `lib\services\luxray_api.dart` | 39/39 (100.0 %) | 17/17 (100.0 %) | - | - |
| `lib\theme\wear_theme.dart` | 52/54 (96.3 %) | 27/29 (93.1 %) | [6, 41] | ['L6/b0/r0', 'L41/b0/r0'] |
| `lib\widgets\circular_safe_area.dart` | 13/13 (100.0 %) | 5/5 (100.0 %) | - | - |

## Metodos criticos evaluados (Fase 2)

| Metodo | Archivo | Rango | Statements | Branches | Sin cubrir |
|---|---|---|---|---|---|
| M1 · LuxrayApi.sanitizar (VALIDACION) | `lib/services/luxray_api.dart` | L50-L53 | 3/3 (100.0 %) | 1/1 (100.0 %) | L- / - |
| M3 · LuxrayApi.buscar (COMUNICACION) | `lib/services/luxray_api.dart` | L61-L73 | 6/6 (100.0 %) | 3/3 (100.0 %) | L- / - |
| M3 · LuxrayApi._buscarEnSearch (COMUNICACION) | `lib/services/luxray_api.dart` | L75-L96 | 13/13 (100.0 %) | 5/5 (100.0 %) | L- / - |
| M3 · LuxrayApi._buscarEnCursosLegacy (COMUNICACION) | `lib/services/luxray_api.dart` | L98-L119 | 13/13 (100.0 %) | 5/5 (100.0 %) | L- / - |
| M2 · BusquedaResponse.fromJson (PROCESAMIENTO) | `lib/models/busqueda.dart` | L32-L43 | 10/10 (100.0 %) | 1/1 (100.0 %) | L- / - |
| M2 · BusquedaResponse.fromSearchApiResponse (PROCESAMIENTO) | `lib/models/busqueda.dart` | L47-L51 | 4/4 (100.0 %) | 2/2 (100.0 %) | L- / - |
| M2 · BusquedaResponse.fromApiResponse (PROCESAMIENTO) | `lib/models/busqueda.dart` | L54-L70 | 11/11 (100.0 %) | 2/2 (100.0 %) | L- / - |
| M2 · BusquedaResponse._list (PROCESAMIENTO) | `lib/models/busqueda.dart` | L83-L89 | 5/5 (100.0 %) | 2/2 (100.0 %) | L- / - |
| M2 · BusquedaResponse._extractSources (PROCESAMIENTO) | `lib/models/busqueda.dart` | L92-L100 | 7/7 (100.0 %) | 3/3 (100.0 %) | L- / - |