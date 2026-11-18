# Fase 5 (NET-07) — Escenario: lectura_5usuarios

- **Modo**: lectura
- **Usuarios concurrentes (VUs)**: 5
- **Iteraciones por VU**: 20
- **Peticiones totales**: 100
- **Fecha**: 2026-11-11T15:36:47.394Z

| Indicador | Valor |
|---|---|
| Latencia media | 13.56 ms |
| Latencia mediana | 12.50 ms |
| Latencia P90 | 17.65 ms |
| Latencia P95 | 20.50 ms |
| Latencia P99 | - ms |
| Latencia mínima | 8.07 ms |
| Latencia máxima | 24.86 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 23.22 req/s |
| Datos recibidos | 306.49 KB |
| Datos enviados | 10.94 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`