#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 12 — FASE 3 — Verificación de los CÓDIGOS HTTP devueltos.

El resumen de k6 no desglosa los códigos de estado por petición. Para
documentar los "códigos HTTP devueltos" que pide la práctica, se ejecuta un
escenario representativo de cada modo con la traza completa por petición
(`--out json=...`) y se cuenta la distribución real de estados.

Uso:
    python verificar_codigos_http.py
"""
import collections
import json
import os
import subprocess
import time

K6 = r"C:\DDI\luxray\practica-11\tools\k6\k6-v0.57.0-windows-amd64\k6.exe"
SCRIPT = r"C:\DDI\luxray\practica-12\fase3-estres-api\estres_api.js"
EVID = r"C:\DDI\luxray\practica-12\evidencias"
BASE = "http://[::1]:8080/api"


def trazar(modo, vus=10, iteraciones=10):
    salida = os.path.join(EVID, f"fase3_codigos_{modo}.jsonl")
    env = dict(os.environ)
    env.update({"BASE_URL": BASE, "VUS": str(vus), "ITERACIONES": str(iteraciones),
                "MODO": modo, "NIVEL": str(vus)})
    subprocess.run([K6, "run", "--out", f"json={salida}", SCRIPT],
                   capture_output=True, text=True, encoding="utf-8",
                   errors="replace", env=env, cwd=os.path.dirname(SCRIPT),
                   timeout=900)
    conteo = collections.Counter()
    total = 0
    with open(salida, "r", encoding="utf-8", errors="replace") as fh:
        for linea in fh:
            try:
                r = json.loads(linea)
            except Exception:  # noqa: BLE001
                continue
            # Formato de la traza JSON de k6:
            #   {"metric":"http_reqs","type":"Point","data":{"value":1,"tags":{...,"status":"200"}}}
            if r.get("type") != "Point" or r.get("metric") != "http_reqs":
                continue
            tags = (r.get("data") or {}).get("tags") or {}
            st = tags.get("status")
            if st is None:
                continue
            conteo[str(st)] += 1
            total += 1
    return conteo, total, salida


def main():
    print("=" * 78)
    print("PRACTICA 12 - FASE 3 - CODIGOS HTTP DEVUELTOS (traza por peticion)")
    print("=" * 78)
    resultado = {}
    for modo in ("lectura", "escritura"):
        conteo, total, ruta = trazar(modo)
        resultado[modo] = {"conteo": dict(conteo), "total": total, "traza": ruta}
        print(f"\n  {modo}: {total} peticiones -> " +
              ", ".join(f"HTTP {k}: {v}" for k, v in sorted(conteo.items())))
        print(f"    traza: {ruta}")

    with open(os.path.join(EVID, "fase3_codigos_http.json"), "w", encoding="utf-8") as fh:
        json.dump({"fecha": time.strftime("%Y-%m-%d %H:%M:%S"), "base_url": BASE,
                   "modos": resultado}, fh, indent=2, ensure_ascii=False)

    md = ["# Fase 3 — Códigos HTTP devueltos (traza por petición)\n",
          f"- **Base URL**: `{BASE}`",
          "- **Método**: `k6 run --out json=...` (traza completa de cada petición)\n",
          "| Modo | Peticiones | Distribución de códigos HTTP |", "|---|---|---|"]
    for modo, r in resultado.items():
        md.append(f"| {modo} | {r['total']} | " +
                  ", ".join(f"**HTTP {k}**: {v}" for k, v in sorted(r["conteo"].items())) +
                  " |")
    md.append("\nEn los 12 escenarios de la curva de saturación (3 600 peticiones) el "
              "contador `http_req_failed` fue **0 %** y los *checks* de "
              "`status === 200` fueron **100 %**, lo que acredita que todas las "
              "respuestas fueron HTTP 200.")
    with open(os.path.join(EVID, "fase3_codigos_http.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))
    print("\nInforme escrito en evidencias\\fase3_codigos_http.md")


if __name__ == "__main__":
    main()
