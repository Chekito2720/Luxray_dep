# Fase 5 (NET-07) — Escenario: escritura_5usuarios

- **Modo**: escritura
- **Usuarios concurrentes (VUs)**: 5
- **Iteraciones por VU**: 20
- **Peticiones totales**: 100
- **Fecha**: 2026-11-11T15:37:19.044Z

| Indicador | Valor |
|---|---|
| Latencia media | 423.44 ms |
| Latencia mediana | 413.22 ms |
| Latencia P90 | 474.04 ms |
| Latencia P95 | 492.66 ms |
| Latencia P99 | - ms |
| Latencia mínima | 370.77 ms |
| Latencia máxima | 507.25 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 7.98 req/s |
| Datos recibidos | 119.50 KB |
| Datos enviados | 30.46 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`