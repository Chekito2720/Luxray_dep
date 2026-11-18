#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 2 — Analizador de cobertura (lcov) de la aplicación móvil.

Lee el lcov.info generado por:
    flutter test --coverage --branch-coverage --coverage-path=coverage/lcov.info

y calcula:
  * COBERTURA DE INSTRUCCIONES : líneas ejecutables (DA) con hit > 0
  * COBERTURA DE DECISIONES    : registros BRDA con 'taken' > 0
Reporta además el detalle línea a línea de lo NO cubierto, que es la
información que permite justificar las rutas que no se ejercitan.

Salida: JSON + Markdown en practica-11/evidencias/
"""
import json
import os
import sys
from collections import defaultdict

LCOV = sys.argv[1] if len(sys.argv) > 1 else r"C:\DDI\luxray\luxraywaer\coverage\lcov.info"
OUT_DIR = sys.argv[2] if len(sys.argv) > 2 else r"C:\DDI\luxray\practica-11\evidencias"

# Metodos criticos evaluados en la Fase 2 (se reportan por separado)
METODOS_CRITICOS = {
    "lib/services/luxray_api.dart": [
        (50, 53, "M1 · LuxrayApi.sanitizar (VALIDACION)"),
        (61, 73, "M3 · LuxrayApi.buscar (COMUNICACION)"),
        (75, 96, "M3 · LuxrayApi._buscarEnSearch (COMUNICACION)"),
        (98, 119, "M3 · LuxrayApi._buscarEnCursosLegacy (COMUNICACION)"),
    ],
    "lib/models/busqueda.dart": [
        (32, 43, "M2 · BusquedaResponse.fromJson (PROCESAMIENTO)"),
        (47, 51, "M2 · BusquedaResponse.fromSearchApiResponse (PROCESAMIENTO)"),
        (54, 70, "M2 · BusquedaResponse.fromApiResponse (PROCESAMIENTO)"),
        (83, 89, "M2 · BusquedaResponse._list (PROCESAMIENTO)"),
        (92, 100, "M2 · BusquedaResponse._extractSources (PROCESAMIENTO)"),
    ],
}


def parse_lcov(ruta):
    archivos = defaultdict(lambda: {"lineas": {}, "branches": []})
    actual = None
    with open(ruta, "r", encoding="utf-8") as fh:
        for raw in fh:
            linea = raw.strip()
            if linea.startswith("SF:"):
                actual = linea[3:]
            elif linea.startswith("DA:"):
                num, hits = linea[3:].split(",")[:2]
                archivos[actual]["lineas"][int(num)] = int(hits)
            elif linea.startswith("BRDA:"):
                partes = linea[5:].split(",")
                # BRDA:<linea>,<bloque>,<rama>,<tomada>
                archivos[actual]["branches"].append(
                    {"linea": int(partes[0]), "bloque": partes[1],
                     "rama": partes[2], "tomada": partes[3]})
    return archivos


def calc(archivos):
    global_rows = []
    tot_l = hit_l = 0
    tot_b = hit_b = 0
    por_archivo = {}

    for archivo, datos in sorted(archivos.items()):
        lineas = datos["lineas"]
        branches = datos["branches"]
        l_tot, l_hit = len(lineas), sum(1 for v in lineas.values() if v > 0)
        b_tot = len(branches)
        b_hit = sum(1 for b in branches if b["tomada"] not in ("0", "-", ""))
        tot_l += l_tot
        hit_l += l_hit
        tot_b += b_tot
        hit_b += b_hit
        por_archivo[archivo] = {
            "lineas_total": l_tot, "lineas_cubiertas": l_hit,
            "stmt_pct": round(100.0 * l_hit / l_tot, 2) if l_tot else 0.0,
            "branches_total": b_tot, "branches_cubiertas": b_hit,
            "branch_pct": round(100.0 * b_hit / b_tot, 2) if b_tot else 0.0,
            "lineas_no_cubiertas": sorted([k for k, v in lineas.items() if v == 0]),
            "branches_no_cubiertas": [
                f"L{b['linea']}/b{b['bloque']}/r{b['rama']}"
                for b in branches if b["tomada"] in ("0", "-", "")],
        }
        global_rows.append((archivo, l_hit, l_tot, b_hit, b_tot))

    return {
        "por_archivo": por_archivo,
        "totales": {
            "lineas_total": tot_l, "lineas_cubiertas": hit_l,
            "stmt_pct": round(100.0 * hit_l / tot_l, 2) if tot_l else 0.0,
            "branches_total": tot_b, "branches_cubiertas": hit_b,
            "branch_pct": round(100.0 * hit_b / tot_b, 2) if tot_b else 0.0,
        },
    }


def criticos(archivos, resumen):
    """Detalla la cobertura de cada metodo critico (rango de lineas)."""
    # Normaliza las claves: el lcov de Windows usa backslashes
    norm = {k.replace("\\", "/"): v for k, v in archivos.items()}
    filas = []
    for archivo, metodos in METODOS_CRITICOS.items():
        if archivo not in norm:
            continue
        lineas = norm[archivo]["lineas"]
        branches = norm[archivo]["branches"]
        for ini, fin, nombre in metodos:
            ls = {n: h for n, h in lineas.items() if ini <= n <= fin}
            bs = [b for b in branches if ini <= b["linea"] <= fin]
            l_hit = sum(1 for v in ls.values() if v > 0)
            b_hit = sum(1 for b in bs if b["tomada"] not in ("0", "-", ""))
            filas.append({
                "metodo": nombre, "archivo": archivo, "rango": f"L{ini}-L{fin}",
                "lineas_total": len(ls), "lineas_cubiertas": l_hit,
                "stmt_pct": round(100.0 * l_hit / len(ls), 2) if ls else 0.0,
                "branches_total": len(bs), "branches_cubiertas": b_hit,
                "branch_pct": round(100.0 * b_hit / len(bs), 2) if bs else 0.0,
                "lineas_no_cubiertas": sorted([n for n, v in ls.items() if v == 0]),
                "branches_no_cubiertas": [
                    f"L{b['linea']}/b{b['bloque']}/r{b['rama']}"
                    for b in bs if b["tomada"] in ("0", "-", "")],
            })
    return filas


def main():
    if not os.path.exists(LCOV):
        print(f"No existe {LCOV}")
        return 2
    archivos = parse_lcov(LCOV)
    resumen = calc(archivos)
    crit = criticos(archivos, resumen)

    out = {"lcov": LCOV, "totales": resumen["totales"],
           "por_archivo": resumen["por_archivo"], "metodos_criticos": crit}
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "fase2_cobertura_movil.json"), "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent=2, ensure_ascii=False)

    md = []
    md.append("# Fase 2 — Cobertura de la aplicacion movil (Flutter / Wear OS)\n")
    md.append(f"- **Fuente de datos**: `{LCOV}` (generado por `flutter test --coverage --branch-coverage`)")
    t = resumen["totales"]
    md.append(f"- **Cobertura de INSTRUCCIONES (statements)**: "
              f"**{t['stmt_pct']} %** ({t['lineas_cubiertas']}/{t['lineas_total']} lineas ejecutables)")
    md.append(f"- **Cobertura de DECISIONES (branches)**: "
              f"**{t['branch_pct']} %** ({t['branches_cubiertas']}/{t['branches_total']} ramas)\n")

    md.append("## Detalle por archivo\n")
    md.append("| Archivo | Instrucciones | Branch | Lineas sin cubrir | Ramas sin cubrir |")
    md.append("|---|---|---|---|---|")
    for a, d in resumen["por_archivo"].items():
        md.append(f"| `{a}` | {d['lineas_cubiertas']}/{d['lineas_total']} "
                  f"({d['stmt_pct']} %) | {d['branches_cubiertas']}/{d['branches_total']} "
                  f"({d['branch_pct']} %) | {d['lineas_no_cubiertas'] or '-'} | "
                  f"{d['branches_no_cubiertas'] or '-'} |")

    md.append("\n## Metodos criticos evaluados (Fase 2)\n")
    md.append("| Metodo | Archivo | Rango | Statements | Branches | Sin cubrir |")
    md.append("|---|---|---|---|---|---|")
    for c in crit:
        md.append(f"| {c['metodo']} | `{c['archivo']}` | {c['rango']} | "
                  f"{c['lineas_cubiertas']}/{c['lineas_total']} ({c['stmt_pct']} %) | "
                  f"{c['branches_cubiertas']}/{c['branches_total']} ({c['branch_pct']} %) | "
                  f"L{c['lineas_no_cubiertas'] or '-'} / {c['branches_no_cubiertas'] or '-'} |")

    with open(os.path.join(OUT_DIR, "fase2_cobertura_movil.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))

    print(f"Statements: {t['stmt_pct']} % ({t['lineas_cubiertas']}/{t['lineas_total']})")
    print(f"Branches  : {t['branch_pct']} % ({t['branches_cubiertas']}/{t['branches_total']})")
    for c in crit:
        print(f"  {c['metodo']:<62} stmt {c['stmt_pct']:>6} %  branch {c['branch_pct']:>6} %"
              + (f"   SIN CUBRIR L{c['lineas_no_cubiertas']}" if c["lineas_no_cubiertas"] else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())

