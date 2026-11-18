#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 12 — FASE 3 — Orquestador de la prueba de estrés de la API.

Ejecuta cada nivel de concurrencia como una invocación AISLADA de k6 y
consolida los resultados en una tabla con la curva de saturación.

Para cada nivel se registra: RPS, latencia media/P90/P95/máxima, % de error,
códigos HTTP devueltos y el comportamiento del servidor (CPU y memoria del
contenedor del gateway).

Uso:
    python ejecutar_estres.py --niveles 1 5 10 20 40 80 --iteraciones 20
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time

K6 = r"C:\DDI\luxray\practica-11\tools\k6\k6-v0.57.0-windows-amd64\k6.exe"
SCRIPT = r"C:\DDI\luxray\practica-12\fase3-estres-api\estres_api.js"
EVID = r"C:\DDI\luxray\practica-12\evidencias"
GATEWAY = "luxray-gateway"


def docker_stats(contenedor):
    """CPU y memoria del contenedor del API Gateway en este instante."""
    p = subprocess.run(
        ["docker", "stats", "--no-stream", "--format",
         "{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}", contenedor],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
        timeout=60)
    line = (p.stdout or "").strip().splitlines()
    if not line:
        return {}
    partes = line[0].split("\t")
    return {"cpu_pct": partes[0].strip() if len(partes) > 0 else None,
            "mem_usage": partes[1].strip() if len(partes) > 1 else None,
            "mem_pct": partes[2].strip() if len(partes) > 2 else None}


def ejecutar(modo, vus, iteraciones, base_url):
    etiqueta = f"{modo}_{vus}usuarios"
    summary = os.path.join(EVID, f"fase3_estres_{etiqueta}.json")
    env = dict(os.environ)
    env.update({"BASE_URL": base_url, "VUS": str(vus), "ITERACIONES": str(iteraciones),
                "MODO": modo, "NIVEL": str(vus)})
    t0 = time.perf_counter()
    proc = subprocess.run([K6, "run", "--summary-export", summary, SCRIPT],
                          capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=env, cwd=os.path.dirname(SCRIPT),
                          timeout=1800)
    dur = time.perf_counter() - t0
    salida = (proc.stdout or "") + (proc.stderr or "")
    with open(os.path.join(EVID, f"fase3_estres_{etiqueta}_consola.txt"), "w",
              encoding="utf-8") as fh:
        fh.write(salida)

    m = {}
    if os.path.exists(summary):
        with open(summary, "r", encoding="utf-8") as fh:
            m = json.load(fh).get("metrics", {})

    def g(dic, clave, defecto=0.0):
        v = dic.get(clave, defecto)
        return v if isinstance(v, (int, float)) else defecto

    d = m.get("http_req_duration", {})
    f = m.get("http_req_failed", {})
    c = m.get("checks", {})
    r = m.get("http_reqs", {})
    rec = m.get("data_received", {})
    sent = m.get("data_sent", {})

    # Codigos HTTP devueltos: se extraen del log de k6
    codigos = sorted(set(re.findall(r"status=(\d{3})", salida)))

    return {
        "modo": modo, "vus": vus, "iteraciones": iteraciones,
        "peticiones": int(g(r, "count")),
        "rps": round(g(r, "rate"), 2),
        "avg_ms": round(g(d, "avg"), 2),
        "p90_ms": round(g(d, "p(90)"), 2),
        "p95_ms": round(g(d, "p(95)"), 2),
        "p99_ms": round(g(d, "p(99)"), 2),
        "max_ms": round(g(d, "max"), 2),
        "min_ms": round(g(d, "min"), 2),
        "fallo_pct": round(g(f, "value", g(f, "rate")) * 100, 2),
        "checks_ok_pct": round(g(c, "value", g(c, "rate")) * 100, 2),
        "kb_recibidos": round(g(rec, "count") / 1024, 2),
        "kb_enviados": round(g(sent, "count") / 1024, 2),
        "duracion_s": round(dur, 2),
        "codigos_http": codigos,
        "servidor": docker_stats(GATEWAY),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--niveles", nargs="*", type=int, default=[1, 5, 10, 20, 40, 80])
    ap.add_argument("--iteraciones", type=int, default=20)
    ap.add_argument("--modos", nargs="*", default=["lectura", "escritura"])
    ap.add_argument("--base-url", default="http://localhost:8080/api")
    ap.add_argument("--out", default=os.path.join(EVID, "fase3_estres_consolidado.json"))
    args = ap.parse_args()

    print("=" * 78)
    print("PRACTICA 12 - FASE 3 - PRUEBA DE ESTRES DE LA API (k6)")
    print("=" * 78)
    print(f"  base  : {args.base_url}")
    print(f"  niveles: {args.niveles}  |  iteraciones/usuario: {args.iteraciones}")
    print(f"  modos  : {args.modos}\n")

    resultados = []
    for modo in args.modos:
        for vus in args.niveles:
            print(f"  >> {modo} con {vus} usuarios concurrentes ...", flush=True)
            r = ejecutar(modo, vus, args.iteraciones, args.base_url)
            resultados.append(r)
            print(f"     RPS={r['rps']:>8}  avg={r['avg_ms']:>8} ms  P95={r['p95_ms']:>8} ms  "
                  f"max={r['max_ms']:>8} ms  fallo={r['fallo_pct']:>6} %  "
                  f"checks={r['checks_ok_pct']:>6} %  codigos={r['codigos_http']}")
            print(f"     servidor: {r['servidor']}")
            time.sleep(3)

    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump({"base_url": args.base_url, "iteraciones": args.iteraciones,
                   "fecha": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "escenarios": resultados}, fh, indent=2, ensure_ascii=False)

    md = []
    md.append("# Fase 3 — Prueba de estrés de la API (k6)\n")
    md.append(f"- **Base URL**: `{args.base_url}`")
    md.append(f"- **Iteraciones por usuario concurrente**: {args.iteraciones}")
    md.append(f"- **Fecha**: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    md.append("- **Ejecución**: cada nivel es una invocación AISLADA de k6\n")
    md.append("## Curva de carga\n")
    md.append("| Modo | VUs | Peticiones | RPS | Media | P90 | P95 | P99 | Máx | Fallo % | Checks % | Códigos HTTP |")
    md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in resultados:
        md.append(f"| {r['modo']} | {r['vus']} | {r['peticiones']} | {r['rps']} | "
                  f"{r['avg_ms']} ms | {r['p90_ms']} ms | {r['p95_ms']} ms | {r['p99_ms']} ms | "
                  f"{r['max_ms']} ms | {r['fallo_pct']} % | {r['checks_ok_pct']} % | "
                  f"{', '.join(r['codigos_http']) or '-'} |")
    md.append("\n## Comportamiento del servidor (API Gateway) durante cada nivel\n")
    md.append("| Modo | VUs | CPU del contenedor | Memoria | % memoria |")
    md.append("|---|---|---|---|---|")
    for r in resultados:
        s = r["servidor"]
        md.append(f"| {r['modo']} | {r['vus']} | {s.get('cpu_pct', '-')} | "
                  f"{s.get('mem_usage', '-')} | {s.get('mem_pct', '-')} |")
    md.append("\n## Transferencia\n")
    md.append("| Modo | VUs | Recibido | Enviado |")
    md.append("|---|---|---|---|")
    for r in resultados:
        md.append(f"| {r['modo']} | {r['vus']} | {r['kb_recibidos']} KB | {r['kb_enviados']} KB |")
    with open(os.path.join(EVID, "fase3_estres_consolidado.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))

    print()
    print("=" * 78)
    print("CURVA DE SATURACION")
    print("=" * 78)
    for modo in args.modos:
        print(f"\n  {modo.upper()}")
        print("    VUs   RPS      avg(ms)   P95(ms)   max(ms)   fallo%   checks%")
        for r in [x for x in resultados if x["modo"] == modo]:
            print(f"    {r['vus']:>4}  {r['rps']:>8}  {r['avg_ms']:>9}  {r['p95_ms']:>8}  "
                  f"{r['max_ms']:>8}  {r['fallo_pct']:>7}  {r['checks_ok_pct']:>8}")
    print(f"\nInforme: {args.out}")


if __name__ == "__main__":
    sys.exit(main())
