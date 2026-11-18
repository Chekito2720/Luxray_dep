#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 5 (NET-07) — Orquestador de la prueba de carga k6.

Ejecuta los 6 escenarios AISLADOS (3 niveles de concurrencia x 2 endpoints)
y consolida los resultados en una tabla Markdown con:
  - tiempo promedio, P95, P99, maximo
  - solicitudes exitosas / fallidas
  - tasa de transferencia
  - cumplimiento de los umbrales de referencia de la practica

Uso:
    python ejecutar_carga.py
"""
import json
import os
import subprocess
import sys
import time

K6 = r"C:\DDI\luxray\practica-11\tools\k6\k6-v0.57.0-windows-amd64\k6.exe"
SCRIPT = r"C:\DDI\luxray\practica-11\fase5-conectividad\carga_k6.js"
EVID = r"C:\DDI\luxray\practica-11\evidencias"
BASE_URL = os.environ.get("BASE_URL", "http://[::1]:8080/api")

ESCENARIOS = []
for modo in ("lectura", "escritura"):
    for vus in (1, 5, 10):
        etiqueta = f"{modo}_{vus}usuarios"
        ESCENARIOS.append((modo, vus, etiqueta))


def main():
    os.makedirs(EVID, exist_ok=True)
    resultados = []
    for modo, vus, etiqueta in ESCENARIOS:
        print("=" * 78)
        print(f"ESCENARIO {etiqueta}  (modo={modo}, VUs={vus}, 20 iteraciones por VU)")
        print("=" * 78)
        summary = os.path.join(EVID, f"fase5_carga_{etiqueta}.json")
        env = dict(os.environ)
        env["BASE_URL"] = BASE_URL
        env["VUS"] = str(vus)
        env["MODO"] = modo
        env["ITERACIONES"] = "20"
        env["ESCENARIO"] = etiqueta
        env["RUTA_INFORME"] = os.path.join(EVID, f"fase5_carga_{etiqueta}.md")
        cmd = [K6, "run", "--summary-export", summary, SCRIPT]
        proc = subprocess.run(cmd, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", env=env,
                              cwd=os.path.dirname(SCRIPT))
        salida = (proc.stdout or "") + (proc.stderr or "")
        with open(os.path.join(EVID, f"fase5_carga_{etiqueta}_consola.txt"), "w",
                  encoding="utf-8") as fh:
            fh.write(salida)

        # k6 v0.57 exporta el summary con las metricas en plano:
        #   metrics.http_reqs.count
        #   metrics.http_req_duration["p(95)"]
        #   metrics.http_req_failed.value  (0..1)
        #   metrics.checks.value           (0..1)
        m = {}
        if os.path.exists(summary):
            with open(summary, "r", encoding="utf-8") as fh:
                m = json.load(fh).get("metrics", {})

        d = m.get("http_req_duration", {})
        f = m.get("http_req_failed", {})
        c = m.get("checks", {})
        reqs = m.get("http_reqs", {})
        rec = m.get("data_received", {})
        sent = m.get("data_sent", {})

        def g(dic, clave, defecto=0.0):
            v = dic.get(clave, defecto)
            return v if isinstance(v, (int, float)) else defecto

        res = {
            "escenario": etiqueta, "modo": modo, "vus": vus,
            "peticiones": int(g(reqs, "count")),
            "avg_ms": round(g(d, "avg"), 2),
            "med_ms": round(g(d, "med"), 2),
            "p90_ms": round(g(d, "p(90)"), 2),
            "p95_ms": round(g(d, "p(95)"), 2),
            "p99_ms": round(g(d, "p(99)"), 2),
            "min_ms": round(g(d, "min"), 2),
            "max_ms": round(g(d, "max"), 2),
            "fallidas": int(g(f, "passes")),
            "tasa_fallo_pct": round(g(f, "value") * 100, 2),
            "checks_ok_pct": round(g(c, "value") * 100, 2),
            "req_por_s": round(g(reqs, "rate"), 2),
            "kb_recibidos": round(g(rec, "count") / 1024, 2),
            "kb_enviados": round(g(sent, "count") / 1024, 2),
            "cumple_p95_500": g(d, "p(95)", 1e9) < 500,
            "cumple_fallo_5pct": g(f, "value", 1) < 0.05,
            "cumple_checks_95pct": g(c, "value", 0) > 0.95,
        }
        resultados.append(res)
        print(f"  peticiones={res['peticiones']}  avg={res['avg_ms']} ms  "
              f"P95={res['p95_ms']} ms  max={res['max_ms']} ms  "
              f"fallo={res['tasa_fallo_pct']} %  checks={res['checks_ok_pct']} %")
        time.sleep(1)

    with open(os.path.join(EVID, "fase5_carga_consolidado.json"), "w", encoding="utf-8") as fh:
        json.dump({"base_url": BASE_URL, "fecha": time.strftime("%Y-%m-%dT%H:%M:%S"),
                   "iteraciones_por_vu": 20, "escenarios": resultados},
                  fh, indent=2, ensure_ascii=False)

    md = []
    md.append("# Fase 5 (NET-07) — Carga controlada (k6)\n")
    md.append(f"- **Base URL**: `{BASE_URL}`")
    md.append(f"- **Iteraciones por usuario concurrente**: 20 (minimo de la practica)")
    md.append(f"- **Fecha**: {time.strftime('%Y-%m-%dT%H:%M:%S')}")
    md.append("- **Ejecucion**: escenarios AISLADOS (una invocacion de k6 por nivel "
              "de concurrencia) para que la medicion de cada nivel no se contamine.\n")
    md.append("| Escenario | Modo | VUs | Peticiones | Avg (ms) | Med (ms) | P95 (ms) | "
              "P99 (ms) | Max (ms) | Fallidas | Tasa fallo | Checks OK | req/s |")
    md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in resultados:
        md.append(f"| {r['escenario']} | {r['modo']} | {r['vus']} | {r['peticiones']} | "
                  f"{r['avg_ms']} | {r['med_ms']} | {r['p95_ms']} | {r['p99_ms']} | "
                  f"{r['max_ms']} | {r['fallidas']} | {r['tasa_fallo_pct']} % | "
                  f"{r['checks_ok_pct']} % | {r['req_por_s']} |")
    md.append("\n## Cumplimiento de los umbrales de referencia\n")
    md.append("| Escenario | P95 < 500 ms | Fallo < 5 % | Checks > 95 % |")
    md.append("|---|---|---|---|")
    for r in resultados:
        md.append(f"| {r['escenario']} | {'SI' if r['cumple_p95_500'] else 'NO'} | "
                  f"{'SI' if r['cumple_fallo_5pct'] else 'NO'} | "
                  f"{'SI' if r['cumple_checks_95pct'] else 'NO'} |")
    md.append("\n## Transferencia\n")
    md.append("| Escenario | Datos recibidos | Datos enviados |")
    md.append("|---|---|---|")
    for r in resultados:
        md.append(f"| {r['escenario']} | {r['kb_recibidos']} KB | {r['kb_enviados']} KB |")

    with open(os.path.join(EVID, "fase5_carga_consolidado.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))

    print()
    print("=" * 78)
    print("RESUMEN DE CARGA")
    print("=" * 78)
    for r in resultados:
        print(f"  {r['escenario']:<24} P95={r['p95_ms']:>8} ms  max={r['max_ms']:>8} ms  "
              f"fallo={r['tasa_fallo_pct']:>6} %  checks={r['checks_ok_pct']:>6} %  "
              f"{'OK' if (r['cumple_p95_500'] and r['cumple_fallo_5pct'] and r['cumple_checks_95pct']) else 'REVISAR'}")
    print()
    print("Informe: fase5_carga_consolidado.md / .json")


if __name__ == "__main__":
    sys.exit(main())
