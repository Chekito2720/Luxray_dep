# Fase 5 (NET-07) — Escenario: lectura_1usuarios

- **Modo**: lectura
- **Usuarios concurrentes (VUs)**: 1
- **Iteraciones por VU**: 20
- **Peticiones totales**: 20
- **Fecha**: 2026-11-11T15:36:41.904Z

| Indicador | Valor |
|---|---|
| Latencia media | 11.60 ms |
| Latencia mediana | 11.18 ms |
| Latencia P90 | 16.56 ms |
| Latencia P95 | 16.64 ms |
| Latencia P99 | - ms |
| Latencia mínima | 6.51 ms |
| Latencia máxima | 17.25 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 4.70 req/s |
| Datos recibidos | 61.30 KB |
| Datos enviados | 2.19 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`