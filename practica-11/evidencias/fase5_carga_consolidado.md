# Fase 5 (NET-07) — Carga controlada (k6)

- **Base URL**: `http://[::1]:8080/api`
- **Iteraciones por usuario concurrente**: 20 (minimo de la practica)
- **Fecha**: 2026-11-11T09:37:36
- **Ejecucion**: escenarios AISLADOS (una invocacion de k6 por nivel de concurrencia) para que la medicion de cada nivel no se contamine.

| Escenario | Modo | VUs | Peticiones | Avg (ms) | Med (ms) | P95 (ms) | P99 (ms) | Max (ms) | Fallidas | Tasa fallo | Checks OK | req/s |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| lectura_1usuarios | lectura | 1 | 20 | 11.6 | 11.18 | 16.64 | 0.0 | 17.25 | 0 | 0 % | 100 % | 4.7 |
| lectura_5usuarios | lectura | 5 | 100 | 13.56 | 12.5 | 20.5 | 0.0 | 24.86 | 0 | 0 % | 100 % | 23.22 |
| lectura_10usuarios | lectura | 10 | 200 | 30.19 | 12.93 | 62.25 | 0.0 | 395.72 | 0 | 0 % | 100 % | 42.4 |
| escritura_1usuarios | escritura | 1 | 20 | 327.5 | 320.93 | 404.28 | 0.0 | 405.62 | 0 | 0 % | 100 % | 1.89 |
| escritura_5usuarios | escritura | 5 | 100 | 423.44 | 413.22 | 492.66 | 0.0 | 507.25 | 0 | 0 % | 100 % | 7.98 |
| escritura_10usuarios | escritura | 10 | 200 | 537.61 | 525.15 | 667.43 | 0.0 | 742.19 | 0 | 0 % | 100 % | 13.3 |

## Cumplimiento de los umbrales de referencia

| Escenario | P95 < 500 ms | Fallo < 5 % | Checks > 95 % |
|---|---|---|---|
| lectura_1usuarios | SI | SI | SI |
| lectura_5usuarios | SI | SI | SI |
| lectura_10usuarios | SI | SI | SI |
| escritura_1usuarios | SI | SI | SI |
| escritura_5usuarios | SI | SI | SI |
| escritura_10usuarios | NO | SI | SI |

## Transferencia

| Escenario | Datos recibidos | Datos enviados |
|---|---|---|
| lectura_1usuarios | 61.3 KB | 2.19 KB |
| lectura_5usuarios | 306.49 KB | 10.94 KB |
| lectura_10usuarios | 612.97 KB | 21.88 KB |
| escritura_1usuarios | 23.91 KB | 6.09 KB |
| escritura_5usuarios | 119.5 KB | 30.46 KB |
| escritura_10usuarios | 239.06 KB | 60.93 KB |