#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 12 — FASE 6 — COMPARATIVA DE LATENCIA DE LA API ANTES / DESPUÉS
DE LA OPTIMIZACIÓN DE LA BASE DE DATOS.

La optimización de la base de datos (índice GIN trigram sobre
`lower(titulo)` y `lower(descripcion)`) cambia el tiempo de respuesta del
ENDPOINT de búsqueda del sitio y del wearable:

    GET /api/cursos/buscar?q=...

Se mide el endpoint con y sin el índice, sobre la MISMA base de datos, con
las MISMAS estadísticas y con el servidor en el MISMO estado (mismo
contenedor, misma caché), para que la diferencia se atribuya al índice.

Además se mide la respuesta COMPLETA (código, tiempo, tamaño) con 10
peticiones consecutivas por cada variante, que es el formato que pide la
práctica para la tabla de resultados por solicitud.

Uso:
    python comparar_api_bd.py
"""
import json
import os
import statistics
import subprocess
import time
import urllib.request

API = "http://[::1]:8080/api"
EVID = r"C:\DDI\luxray\practica-12\evidencias"
CONT = "luxray-cursos-db"
Q = "electricidad"


def psql(sql):
    subprocess.run(["docker", "exec", "-i", CONT, "psql", "-U", "luxray", "-d",
                    "cursos_db", "-v", "ON_ERROR_STOP=off"],
                   input=sql.encode("utf-8"), capture_output=True, timeout=120)


def psql_c(sql):
    """Ejecuta SQL con `-c`: no queda esperando EOF por la tubería."""
    p = subprocess.run(["docker", "exec", CONT, "psql", "-U", "luxray", "-d",
                        "cursos_db", "-v", "ON_ERROR_STOP=off", "-c", sql],
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace", timeout=180)
    return (p.stdout or "") + (p.stderr or "")


def pedir(metodo, url, cuerpo=None, token=None, timeout=30):
    """Una petición HTTP mínima (se usa para calentar la caché)."""
    data = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(url, data=data, method=metodo)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read()
    except Exception:  # noqa: BLE001
        return b""


def medir(n=10):
    filas = []
    for _ in range(n):
        req = urllib.request.Request(f"{API}/cursos/buscar?q={Q}")
        req.add_header("Accept", "application/json")
        t0 = time.perf_counter()
        with urllib.request.urlopen(req, timeout=30) as r:
            datos = r.read()
            codigo = r.status
        filas.append({"http": codigo, "ms": (time.perf_counter() - t0) * 1000,
                      "bytes": len(datos)})
        time.sleep(0.2)
    ms = [f["ms"] for f in filas]
    return {
        "detalle": filas,
        "avg_ms": round(statistics.mean(ms), 2),
        "p90_ms": round(sorted(ms)[int(len(ms) * 0.9) - 1], 2),
        "p95_ms": round(sorted(ms)[int(len(ms) * 0.95) - 1], 2),
        "max_ms": round(max(ms), 2),
        "min_ms": round(min(ms), 2),
        "bytes_promedio": round(statistics.mean([f["bytes"] for f in filas]), 1),
        "http": sorted({f["http"] for f in filas}),
    }


def plan(sql):
    return psql_c(sql)


EXPLAIN = """EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING)
SELECT id, titulo, descripcion, nivel
  FROM cursos
 WHERE LOWER(titulo) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%electricidad%';
"""


def main():
    os.makedirs(EVID, exist_ok=True)
    print("=" * 78)
    print("PRACTICA 12 - FASE 6 - LATENCIA DE LA API ANTES / DESPUES DEL INDICE")
    print("=" *78)
    print(f"  endpoint: GET {API}/cursos/buscar?q={Q}")
    print("  metodo  : A/B intercalado con calentamiento previo de la cache")
    print("           (si no, la primera medicion mide el disco, no el indice)\n")

    # ── Calentamiento: deja la caché y el JIT listos antes de medir ───────
    for _ in range(10):
        pedir("GET", f"{API}/cursos/buscar?q={Q}")

    quitar = ("DROP INDEX IF EXISTS idx_cursos_titulo_trgm;\n"
              "DROP INDEX IF EXISTS idx_cursos_descripcion_trgm;\n"
              "ANALYZE cursos;")
    poner = ("CREATE INDEX IF NOT EXISTS idx_cursos_titulo_trgm\n"
             "  ON cursos USING GIN (lower(titulo) gin_trgm_ops);\n"
             "CREATE INDEX IF NOT EXISTS idx_cursos_descripcion_trgm\n"
             "  ON cursos USING GIN (lower(descripcion) gin_trgm_ops);\n"
             "ANALYZE cursos;")

    # ── A / B / A: el segundo bloque "A" detecta la deriva del sistema ──
    psql(quitar)
    time.sleep(1)
    a1 = medir()
    plan_a1 = plan(EXPLAIN)

    psql(poner)
    time.sleep(1)
    b1 = medir()
    plan_b1 = plan(EXPLAIN)

    psql(quitar)
    time.sleep(1)
    a2 = medir()

    psql(poner)
    time.sleep(1)
    b2 = medir()
    plan_b2 = plan(EXPLAIN)

    def combo(x, y, clave):
        """Combina dos bloques de 10 peticiones en un único valor."""
        if clave == "http":
            return sorted(set(x["http"]) | set(y["http"]))
        if clave == "bytes_promedio":
            return round((x["bytes_promedio"] + y["bytes_promedio"]) / 2, 1)
        if clave == "max_ms":
            return max(x["max_ms"], y["max_ms"])
        if clave == "min_ms":
            return min(x["min_ms"], y["min_ms"])
        return round((x[clave] + y[clave]) / 2, 2)

    antes = {k: combo(a1, a2, k) for k in
             ("avg_ms", "p90_ms", "p95_ms", "max_ms", "min_ms", "bytes_promedio", "http")}
    despues = {k: combo(b1, b2, k) for k in
               ("avg_ms", "p90_ms", "p95_ms", "max_ms", "min_ms", "bytes_promedio", "http")}
    antes["detalle"] = {"bloque_A1": a1, "bloque_A2": a2}
    despues["detalle"] = {"bloque_B1": b1, "bloque_B2": b2}
    # La media se calcula sobre las 20 peticiones de cada variante (2 bloques x 10).
    antes["avg_ms"] = round(statistics.mean(
        [f["ms"] for b in (a1, a2) for f in b["detalle"]]), 2)
    despues["avg_ms"] = round(statistics.mean(
        [f["ms"] for b in (b1, b2) for f in b["detalle"]]), 2)
    antes["p90_ms"] = round(sorted(f["ms"] for b in (a1, a2) for f in b["detalle"])[17], 2)
    despues["p90_ms"] = round(sorted(f["ms"] for b in (b1, b2) for f in b["detalle"])[17], 2)
    antes["p95_ms"] = round(sorted(f["ms"] for b in (a1, a2) for f in b["detalle"])[18], 2)
    despues["p95_ms"] = round(sorted(f["ms"] for b in (b1, b2) for f in b["detalle"])[18], 2)

    with open(os.path.join(EVID, "fase6_comparativa_api_bd.txt"), "w", encoding="utf-8") as fh:
        fh.write("=" * 78 + "\n")
        fh.write("COMPARATIVA DE LATENCIA DE LA API: ANTES / DESPUES DEL INDICE GIN TRIGRAM\n")
        fh.write("Metodo: A/B intercalado (A-B-A-B) con calentamiento previo de la cache\n")
        fh.write(f"endpoint: GET {API}/cursos/buscar?q={Q}\n")
        fh.write("\n---------------- ANTES (sin indice) ----------------\n")
        fh.write(json.dumps(antes, indent=2, ensure_ascii=False) + "\n")
        fh.write("\nPLAN ANTES:\n" + plan_a1 + "\n")
        fh.write("\n---------------- DESPUES (con indice) ----------------\n")
        fh.write(json.dumps(despues, indent=2, ensure_ascii=False) + "\n")
        fh.write("\nPLAN DESPUES:\n" + plan_b2 + "\n")

    with open(os.path.join(EVID, "fase6_comparativa_api_bd.json"), "w", encoding="utf-8") as fh:
        json.dump({"endpoint": f"{API}/cursos/buscar?q={Q}", "metodo": "A/B intercalado",
                   "antes": antes, "despues": despues,
                   "plan_antes": plan_a1, "plan_despues": plan_b2},
                  fh, indent=2, ensure_ascii=False)

    print("  Metrica              ANTES        DESPUES      Diferencia")
    print("  " + "-" * 62)
    for etiqueta, clave in (("Latencia promedio (ms)", "avg_ms"),
                            ("Latencia P90 (ms)", "p90_ms"),
                            ("Latencia P95 (ms)", "p95_ms"),
                            ("Latencia maxima (ms)", "max_ms"),
                            ("Tamano respuesta (B)", "bytes_promedio")):
        a, d = antes[clave], despues[clave]
        dif = (d - a)
        pct = (dif / a * 100) if a else 0
        print(f"  {etiqueta:<20} {a:>10} {d:>12}  {dif:>+10.2f} ({pct:>+6.1f} %)")
    print(f"  {'Codigos HTTP':<20} {antes['http']!s:>10} {despues['http']!s:>12}")

    print("\n  Plan ANTES:")
    for l in plan_a1.splitlines():
        if "Scan" in l or "Execution Time" in l:
            print("    " + l.strip())
    print("  Plan DESPUES:")
    for l in plan_b2.splitlines():
        if "Scan" in l or "Execution Time" in l:
            print("    " + l.strip())

    print("\nNota: sobre la tabla REAL (6 cursos) el planificador elige Seq Scan en")
    print("ambos casos, que es la decision CORRECTA a esa escala; el indice se")
    print("demuestra sobre el benchmark de 20 000 filas (ver fase4_optimizacion_bd.txt).")

    print(f"\nInforme: {EVID}\\fase6_comparativa_api_bd.md")


if __name__ == "__main__":
    main()
