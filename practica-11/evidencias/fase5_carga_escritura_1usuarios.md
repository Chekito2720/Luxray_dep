# Fase 5 (NET-07) — Escenario: escritura_1usuarios

- **Modo**: escritura
- **Usuarios concurrentes (VUs)**: 1
- **Iteraciones por VU**: 20
- **Peticiones totales**: 20
- **Fecha**: 2026-11-11T15:37:05.208Z

| Indicador | Valor |
|---|---|
| Latencia media | 327.50 ms |
| Latencia mediana | 320.93 ms |
| Latencia P90 | 381.56 ms |
| Latencia P95 | 404.28 ms |
| Latencia P99 | - ms |
| Latencia mínima | 283.75 ms |
| Latencia máxima | 405.62 ms |
| Peticiones fallidas | 0 |
| Tasa de fallo | 0.00 % |
| Checks correctos | 100.00 % |
| Tasa de peticiones | 1.89 req/s |
| Datos recibidos | 23.91 KB |
| Datos enviados | 6.09 KB |

### Umbrales evaluados (criterios de referencia de la practica)

- `http_req_duration p(95) < 500 ms`
- `http_req_failed rate < 0.05`
- `checks rate > 0.95`