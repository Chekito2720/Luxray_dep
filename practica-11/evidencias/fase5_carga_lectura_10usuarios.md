# Fase 5 (NET-07) — Escenario: lectura_10usuarios

- **Modo**: lectura
- **Usuarios concurrentes (VUs)**: 10
- **Iteraciones por VU**: 20
- **Peticiones totales**: 200
- **Fecha**: 2026-11-11T15:36:53.358Z

| Indicador | Valor |
|---|---|
| Latencia media | 30.19 ms |
| Latencia mediana | 12.93 ms |
| Latencia P90 | 30.86 ms |
| Latencia P95 | 62.25 ms |
| Latencia P99 | - ms |
| Latencia mínima | 5.70 ms |
| Latencia máxima | 395.72 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 42.40 req/s |
| Datos recibidos | 612.97 KB |
| Datos enviados | 21.88 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`