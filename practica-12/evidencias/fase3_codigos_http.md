# Fase 3 — Códigos HTTP devueltos (traza por petición)

- **Base URL**: `http://[::1]:8080/api`
- **Método**: `k6 run --out json=...` (traza completa de cada petición)

| Modo | Peticiones | Distribución de códigos HTTP |
|---|---|---|
| lectura | 100 | **HTTP 200**: 100 |
| escritura | 100 | **HTTP 200**: 100 |

En los 12 escenarios de la curva de saturación (3 600 peticiones) el contador `http_req_failed` fue **0 %** y los *checks* de `status === 200` fueron **100 %**, lo que acredita que todas las respuestas fueron HTTP 200.