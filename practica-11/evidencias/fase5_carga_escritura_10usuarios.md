# Fase 5 (NET-07) — Escenario: escritura_10usuarios

- **Modo**: escritura
- **Usuarios concurrentes (VUs)**: 10
- **Iteraciones por VU**: 20
- **Peticiones totales**: 200
- **Fecha**: 2026-11-11T15:37:35.326Z

| Indicador | Valor |
|---|---|
| Latencia media | 537.61 ms |
| Latencia mediana | 525.15 ms |
| Latencia P90 | 638.22 ms |
| Latencia P95 | 667.43 ms |
| Latencia P99 | - ms |
| Latencia mínima | 443.31 ms |
| Latencia máxima | 742.19 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 13.30 req/s |
| Datos recibidos | 239.06 KB |
| Datos enviados | 60.93 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`