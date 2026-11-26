# Fase 3 — Prueba de estrés de la API (k6)

- **Base URL**: `http://[::1]:8080/api`
- **Iteraciones por usuario concurrente**: 20
- **Fecha**: 2026-11-11 13:24:36
- **Ejecución**: cada nivel es una invocación AISLADA de k6

## Curva de carga

| Modo | VUs | Peticiones | RPS | Media | P90 | P95 | P99 | Máx | Fallo % | Checks % | Códigos HTTP |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| lectura | 1 | 20 | 15.74 | 12.17 ms | 14.61 ms | 18.67 ms | 0.0 ms | 45.17 ms | 0 % | 100 % | - |
| lectura | 5 | 100 | 75.35 | 13.03 ms | 17.25 ms | 19.47 ms | 0.0 ms | 89.38 ms | 0 % | 100 % | - |
| lectura | 10 | 200 | 148.56 | 12.91 ms | 19.5 ms | 25.28 ms | 0.0 ms | 113.08 ms | 0 % | 100 % | - |
| lectura | 20 | 400 | 265.58 | 20.85 ms | 38.15 ms | 63.32 ms | 0.0 ms | 106.11 ms | 0 % | 100 % | - |
| lectura | 40 | 800 | 393.29 | 42.08 ms | 93.47 ms | 131.63 ms | 0.0 ms | 232.48 ms | 0 % | 100 % | - |
| lectura | 80 | 1600 | 441.83 | 107.46 ms | 199.99 ms | 256.25 ms | 0.0 ms | 386.52 ms | 0 % | 100 % | - |
| escritura | 1 | 20 | 1.22 | 768.14 ms | 488.16 ms | 915.64 ms | 0.0 ms | 8714.79 ms | 0 % | 100 % | - |
| escritura | 5 | 100 | 8.44 | 537.61 ms | 706.58 ms | 745.35 ms | 0.0 ms | 878.46 ms | 0 % | 100 % | - |
| escritura | 10 | 200 | 12.43 | 731.46 ms | 904.59 ms | 957.07 ms | 0.0 ms | 1203.83 ms | 0 % | 100 % | - |
| escritura | 20 | 400 | 13.67 | 1376.18 ms | 1741.99 ms | 1923.55 ms | 0.0 ms | 2251.85 ms | 0 % | 100 % | - |
| escritura | 40 | 800 | 11.55 | 3215.73 ms | 4534.54 ms | 5979.8 ms | 0.0 ms | 9734.19 ms | 0 % | 100 % | - |
| escritura | 80 | 1600 | 14.82 | 5142.27 ms | 5993.62 ms | 6206.1 ms | 0.0 ms | 15745.38 ms | 0 % | 100 % | - |

## Comportamiento del servidor (API Gateway) durante cada nivel

| Modo | VUs | CPU del contenedor | Memoria | % memoria |
|---|---|---|---|---|
| lectura | 1 | 0.15% | 365.2MiB / 7.606GiB | 4.69% |
| lectura | 5 | 0.11% | 365.4MiB / 7.606GiB | 4.69% |
| lectura | 10 | 0.19% | 365.7MiB / 7.606GiB | 4.70% |
| lectura | 20 | 0.19% | 366.1MiB / 7.606GiB | 4.70% |
| lectura | 40 | 0.63% | 365.3MiB / 7.606GiB | 4.69% |
| lectura | 80 | 24.77% | 367.3MiB / 7.606GiB | 4.72% |
| escritura | 1 | 0.15% | 370.4MiB / 7.606GiB | 4.76% |
| escritura | 5 | 0.19% | 379.6MiB / 7.606GiB | 4.87% |
| escritura | 10 | 0.15% | 388.6MiB / 7.606GiB | 4.99% |
| escritura | 20 | 0.25% | 389.5MiB / 7.606GiB | 5.00% |
| escritura | 40 | 0.25% | 383.2MiB / 7.606GiB | 4.92% |
| escritura | 80 | 0.72% | 383.1MiB / 7.606GiB | 4.92% |

## Transferencia

| Modo | VUs | Recibido | Enviado |
|---|---|---|---|
| lectura | 1 | 61.3 KB | 2.19 KB |
| lectura | 5 | 306.48 KB | 10.94 KB |
| lectura | 10 | 612.97 KB | 21.88 KB |
| lectura | 20 | 1225.9 KB | 43.75 KB |
| lectura | 40 | 2451.64 KB | 87.5 KB |
| lectura | 80 | 4902.83 KB | 175.0 KB |
| escritura | 1 | 23.85 KB | 6.07 KB |
| escritura | 5 | 119.22 KB | 30.37 KB |
| escritura | 10 | 238.46 KB | 60.74 KB |
| escritura | 20 | 477.42 KB | 121.65 KB |
| escritura | 40 | 955.42 KB | 243.49 KB |
| escritura | 80 | 1911.41 KB | 487.15 KB |