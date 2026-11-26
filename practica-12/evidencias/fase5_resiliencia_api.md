# Fase 5 — Resiliencia de las escrituras (RES-02, RES-07, RES-06)

- **Fecha**: 2026-11-11 13:27:40
- **API**: `POST /api/auth/registro` a través de un proxy TCP con averías inyectables

| Escenario | HTTP | ms | Filas en BD | Resultado |
|---|---|---|---|---|
| RES-02 · Pérdida de Internet durante una escritura | 0 | 34.8 | 0 | el cliente NO confirma la escritura (correcto) |
| RES-07 · Reintento de una escritura | - | - | 1 | sin duplicados: la restriccion UNIQUE arbitra (correcto) |
| RES-06 · Recuperación de la conexión | 200 | 274.3 | 1 | comunicación restablecida |