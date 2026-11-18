#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 2 — Medición de COBERTURA DE CONDICIONES por mutación.

Justificación metodológica
--------------------------
La cobertura de condiciones (McCabe / condición-atómica) exige que CADA
condición atómica de una decisión se evalúe como Verdadera y como Falsa.
Dart sólo reporta cobertura de INSTRUCCIONES (DA) y de DECISIONES (BRDA);
no instrumenta condiciones atómicas de tipo `a || b`, `x < n`, `is Map`, etc.

Para medirla con rigor y de forma reproducible se utiliza MUTACIÓN DE
CONDICIONES (equivalente operativo de la cobertura de condiciones, y garantia
más fuerte que MC/DC):

  * Se genera un mutante por cada condición atómica, altering SÓLO el operando
    evaluado (mutaciones que conservan el número de líneas del archivo).
  * Se ejecuta la suite White Box completa.
  * Si el mutante ABRE (la suite falla)  -> la condición está cubierta: algún
    caso de prueba la evaluó de forma discriminante (T/F).
  * Si el mutante SOBREVIVE (la suite pasa) -> la condición NO está cubierta.

Un mutante que sobrevive es, por definición, un hueco de cobertura. Por eso
este arnés es también un detector de defectos de validación.

Uso:
    python mutacion_condiciones.py [--proyecto RUTA] [--salida RUTA]
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time

# ──────────────────────────────────────────────────────────────────────────
# Catálogo de mutantes de condición.
#   archivo -> línea (1-based) -> texto original esperado -> texto mutado
# ──────────────────────────────────────────────────────────────────────────
MUTANTES = [
    # ── M1 · LuxrayApi.sanitizar ────────────────────────────────────────
    dict(id="MUT-C1", metodo="M1 sanitizar", archivo="lib/services/luxray_api.dart",
         linea=52, cond="limpio.length > kMaxQuery",
         original="return limpio.length > kMaxQuery ? limpio.substring(0, kMaxQuery) : limpio;",
         mutado="return limpio.length > kMaxQuery + 1 ? limpio.substring(0, kMaxQuery) : limpio;",
         descripcion="Condicion de longitud: el recorte deja de activarse al superar 80."),
    # ── M3 · LuxrayApi.buscar ────────────────────────────────────────────
    dict(id="MUT-C2", metodo="M3 buscar", archivo="lib/services/luxray_api.dart",
         linea=63, cond="query.length < 2",
         original="if (query.length < 2) return BusquedaResponse.empty();",
         mutado="if (query.length <= 2) return BusquedaResponse.empty();",
         descripcion="Puerta de longitud minima: una query de 2 caracteres ya se rechaza."),
    dict(id="MUT-C3", metodo="M3 buscar", archivo="lib/services/luxray_api.dart",
         linea=67, cond="searchResponse.total > 0",
         original="if (searchResponse.total > 0) {",
         mutado="if (searchResponse.total >= 0) {",
         descripcion="Deteccion de resultados: nunca se dispara el fallback al endpoint legacy."),
    dict(id="MUT-C4", metodo="M3 _buscarEnSearch", archivo="lib/services/luxray_api.dart",
         linea=85, cond="resp.statusCode < 200 || resp.statusCode >= 300",
         original="if (resp.statusCode < 200 || resp.statusCode >= 300) {",
         mutado="if (resp.statusCode < 200 && resp.statusCode >= 300) {",
         descripcion="Rango de status: los 3xx/4xx/5xx dejan de tratarse como error."),
    dict(id="MUT-C5", metodo="M3 _buscarEnSearch", archivo="lib/services/luxray_api.dart",
         linea=85, cond="resp.statusCode < 200",
         original="if (resp.statusCode < 200 || resp.statusCode >= 300) {",
         mutado="if (resp.statusCode <= 200 || resp.statusCode >= 300) {",
         descripcion="Limite inferior del rango 2xx: el 200 pasa a considerarse error."),
    dict(id="MUT-C6", metodo="M3 _buscarEnSearch", archivo="lib/services/luxray_api.dart",
         linea=89, cond="body is Map<String, dynamic>",
         original="if (body is Map<String, dynamic>) {",
         mutado="if (body is! Map<String, dynamic>) {",
         descripcion="Tipo del cuerpo JSON: nunca se parsea una respuesta valida."),
    dict(id="MUT-C7", metodo="M3 _buscarEnCursosLegacy", archivo="lib/services/luxray_api.dart",
         linea=108, cond="resp.statusCode < 200 || resp.statusCode >= 300",
         original="if (resp.statusCode < 200 || resp.statusCode >= 300) {",
         mutado="if (resp.statusCode < 200 && resp.statusCode >= 300) {",
         descripcion="Rango de status del fallback: los errores dejan de tratarse como error."),
    dict(id="MUT-C8", metodo="M3 _buscarEnCursosLegacy", archivo="lib/services/luxray_api.dart",
         linea=108, cond="resp.statusCode < 200",
         original="if (resp.statusCode < 200 || resp.statusCode >= 300) {",
         mutado="if (resp.statusCode <= 200 || resp.statusCode >= 300) {",
         descripcion="Limite inferior del rango 2xx del fallback."),
    dict(id="MUT-C9", metodo="M3 _buscarEnCursosLegacy", archivo="lib/services/luxray_api.dart",
         linea=112, cond="body is Map<String, dynamic>",
         original="if (body is Map<String, dynamic>) {",
         mutado="if (body is! Map<String, dynamic>) {",
         descripcion="Tipo del cuerpo JSON del fallback."),
    # ── M2 · BusquedaResponse.fromSearchApiResponse ───────────────────────
    dict(id="MUT-C10", metodo="M2 fromSearchApiResponse", archivo="lib/models/busqueda.dart",
         linea=49, cond="data is Map<String, dynamic>",
         original="if (data is Map<String, dynamic>) return BusquedaResponse.fromJson(data);",
         mutado="if (data is! Map<String, dynamic>) return BusquedaResponse.fromJson(data);",
         descripcion="Envelope: una respuesta valida se descarta como invalida."),
    dict(id="MUT-C11", metodo="M2 _list", archivo="lib/models/busqueda.dart",
         linea=84, cond="raw is! List",
         original="if (raw is! List) return [];",
         mutado="if (raw is List) return [];",
         descripcion="Guarda de tipo de _list: una lista legitima se descarta."),
    dict(id="MUT-C12", metodo="M2 _extractSources", archivo="lib/models/busqueda.dart",
         linea=93, cond="raw is! List",
         original="if (raw is! List) return [];",
         mutado="if (raw is List) return [];",
         descripcion="Guarda de tipo de _extractSources: una lista legitima se descarta."),
    dict(id="MUT-C13", metodo="M2 _extractSources", archivo="lib/models/busqueda.dart",
         linea=96, cond="hit['source'] as Map<String, dynamic>?",
         original=".map((hit) => hit['source'] as Map<String, dynamic>?)",
         mutado=".map((hit) => hit['source'] as Map<String, dynamic>)",
         descripcion="source nullable: un hit sin source revienta el parseo completo."),
]


def sha(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def leer_texto_exacto(path):
    """Lee preservando EXACTAMENTE los finales de linea del archivo.

    Se lee en binario y se decodifica sin traducción de newlines, de modo que
    un archivo LF permanece LF y uno CRLF permanece CRLF. La restauracion
    posterior debe ser byte a byte identica.
    """
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8")


def escribir_texto_exacto(path, texto):
    """Escribe sin traducción de newlines (newline='')."""
    with open(path, "wb") as fh:
        fh.write(texto.encode("utf-8"))


def run_suite(proyecto, test_file="test/fase2_whitebox_test.dart"):
    """Ejecuta la suite White Box. Devuelve (codigo, salida_tail).

    Se decodifica en UTF-8 con 'replace' porque la salida de `flutter test`
    incluye caracteres Unicode (box-drawing) que no son validos en la
    codificacion de consola cp1252 de Windows; con 'replace' el proceso
    nunca se interrumpe por un error de decodificacion.
    """
    cmd = ["flutter", "test", test_file, "--reporter", "compact"]
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    # En Windows `flutter` es un .bat: CreateProcess no puede ejecutarlo
    # directamente, de modo que se invoca a traves de cmd.exe (shell=True).
    proc = subprocess.run(cmd, cwd=proyecto, capture_output=True, text=True,
                          encoding="utf-8", errors="replace",
                          env=env, timeout=900, shell=True)
    salida = (proc.stdout or "") + (proc.stderr or "")
    return proc.returncode, salida


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--proyecto", default=r"C:\DDI\luxray\luxraywaer")
    ap.add_argument("--salida", default=r"C:\DDI\luxray\practica-11\evidencias")
    args = ap.parse_args()

    proyecto = os.path.abspath(args.proyecto)
    salida_dir = os.path.abspath(args.salida)
    os.makedirs(salida_dir, exist_ok=True)

    # ── Verificación de línea base (suite verde antes de mutar) ──────────
    print("[1/3] Verificando linea base (suite sin mutaciones)...")
    t0 = time.time()
    rc, out = run_suite(proyecto)
    base_seg = time.time() - t0
    if rc != 0:
        print("    ERROR: la suite base NO pasa; se aborta el analisis.")
        print(out[-4000:])
        return 2
    print(f"    Linea base OK en {base_seg:.1f}s")

    # ── Respaldo de los archivos a mutar ─────────────────────────────────
    backups = {}
    for m in MUTANTES:
        ruta = os.path.join(proyecto, m["archivo"])
        if m["archivo"] not in backups:
            backups[m["archivo"]] = leer_texto_exacto(ruta)
    hashes_original = {a: sha(os.path.join(proyecto, a)) for a in backups}

    resultados = []
    try:
        for i, m in enumerate(MUTANTES, 1):
            print(f"[2/3] ({i}/{len(MUTANTES)}) {m['id']}  {m['metodo']}")
            ruta = os.path.join(proyecto, m["archivo"])
            contenido = leer_texto_exacto(ruta)
            lineas = contenido.splitlines(keepends=True)

            idx = m["linea"] - 1
            if idx >= len(lineas):
                raise RuntimeError(f"{m['id']}: linea {m['linea']} fuera de rango")
            actual = lineas[idx].rstrip("\r\n")
            if actual.strip() != m["original"].strip():
                raise RuntimeError(
                    f"{m['id']}: el texto en la linea {m['linea']} no coincide.\n"
                    f"  esperado: {m['original'].strip()}\n"
                    f"  real    : {actual.strip()}")

            # Aplicar mutacion (mismo numero de lineas)
            eol = "\r\n" if lineas[idx].endswith("\r\n") else "\n"
            lineas[idx] = actual.replace(m["original"].strip(),
                                         m["mutado"].strip()) + eol
            escribir_texto_exacto(ruta, "".join(lineas))

            t1 = time.time()
            rc, out = run_suite(proyecto)
            dur = time.time() - t1

            # Restaurar inmediatamente
            escribir_texto_exacto(ruta, backups[m["archivo"]])

            # Un mutante ABRE si la suite falla (rc != 0).
            # rc == 1 => test fallo. rc >= 2 => error de compilacion: el
            # mutante no es valido y se descarta como "no aplicable".
            if rc == 0:
                estado = "SOBREVIVE"
                cub = "NO"
            elif rc == 1:
                estado = "ABATIDO"
                cub = "SI"
            else:
                estado = "NO_APLICABLE"
                cub = "INDETERMINADO"

            linea_fallo = ""
            m2 = re.search(r"(test[\\/][^\s:]+_test\.dart:\d+:\d+)", out)
            if m2:
                linea_fallo = m2.group(1)

            resultados.append(dict(
                id=m["id"], metodo=m["metodo"], archivo=m["archivo"],
                linea=m["linea"], condicion=m["cond"], descripcion=m["descripcion"],
                original=m["original"].strip(), mutado=m["mutado"].strip(),
                estado=estado, cubierta=cub, rc=rc,
                duracion_s=round(dur, 1), test_detectores=linea_fallo,
            ))
            print(f"      -> {estado} ({dur:.1f}s) {linea_fallo}")
    finally:
        # Restaurar SIEMPRE los archivos originales (byte a byte)
        for arch, contenido in backups.items():
            escribir_texto_exacto(os.path.join(proyecto, arch), contenido)
        for arch, h in hashes_original.items():
            h2 = sha(os.path.join(proyecto, arch))
            if h != h2:
                raise RuntimeError(f"RESTAURACION FALLIDA en {arch}")
        print("    Codigo fuente restaurado byte a byte (hash verificado).")

    # ── Informe ──────────────────────────────────────────────────────────
    aplicables = [r for r in resultados if r["estado"] != "NO_APLICABLE"]
    abatidos = [r for r in aplicables if r["estado"] == "ABATIDO"]
    sobreviven = [r for r in aplicables if r["estado"] == "SOBREVIVE"]

    # Una condicion atomica se considera cubierta si TODOS sus mutantes
    # fueron abatidos. (Si un mutante sobrevive, la condicion no esta cubierta.)
    por_cond = {}
    for r in resultados:
        key = (r["archivo"], r["linea"], r["condicion"])
        por_cond.setdefault(key, []).append(r)
    condiciones_cubiertas = sum(1 for k, v in por_cond.items()
                                if all(x["estado"] == "ABATIDO" for x in v))
    total_cond = len(por_cond)
    pct_cond = 100.0 * condiciones_cubiertas / total_cond if total_cond else 0.0
    pct_mut = 100.0 * len(abatidos) / len(aplicables) if aplicables else 0.0

    resumen = dict(
        proyecto=proyecto,
        suite="test/fase2_whitebox_test.dart",
        fecha=time.strftime("%Y-%m-%dT%H:%M:%S"),
        base_segundos=round(base_seg, 1),
        mutantes_total=len(resultados),
        mutantes_aplicables=len(aplicables),
        mutantes_abatidos=len(abatidos),
        mutantes_sobrevivientes=len(sobreviven),
        mutantes_no_aplicables=len(resultados) - len(aplicables),
        score_mutacion_pct=round(pct_mut, 2),
        condiciones_atomicas=total_cond,
        condiciones_cubiertas=condiciones_cubiertas,
        cobertura_condiciones_pct=round(pct_cond, 2),
        detalle=resultados,
    )

    with open(os.path.join(salida_dir, "fase2_mutacion_condiciones.json"),
              "w", encoding="utf-8") as fh:
        json.dump(resumen, fh, indent=2, ensure_ascii=False)

    # Markdown legible
    md = []
    md.append("# Fase 2 — Cobertura de condiciones (mutacion) — Aplicacion movil\n")
    md.append(f"- **Proyecto**: `{proyecto}`")
    md.append(f"- **Suite**: `{resumen['suite']}`")
    md.append(f"- **Fecha**: {resumen['fecha']}")
    md.append(f"- **Mutantes aplicados**: {len(resultados)} "
              f"({len(aplicables)} aplicables, {len(resultados)-len(aplicables)} no aplicables)")
    md.append(f"- **Mutantes ABATIDOS**: {len(abatidos)}")
    md.append(f"- **Mutantes SOBREVIVIENTES**: {len(sobreviven)}")
    md.append(f"- **Score de mutacion**: {pct_mut:.2f} %")
    md.append(f"- **Condiciones atomicas cubiertas**: {condiciones_cubiertas}/{total_cond} "
              f"= **{pct_cond:.2f} %**\n")
    md.append("> Un mutante ABATIDO demuestra que la condicion se evalua de forma "
              "discriminante (T/F) en la suite. Un mutante SOBREVIVIENTE es un hueco "
              "de cobertura de condiciones.\n")
    md.append("| ID | Metodo | Condicion atomica | Archivo:Linea | Estado | Cubierta | Test que lo detecta |")
    md.append("|---|---|---|---|---|---|---|")
    for r in resultados:
        md.append(f"| {r['id']} | {r['metodo']} | `{r['condicion']}` | "
                  f"`{r['archivo']}:{r['linea']}` | {r['estado']} | {r['cubierta']} | "
                  f"{r['test_detectores'] or '-'} |")
    md.append("\n## Mutantes详细\n")
    for r in resultados:
        md.append(f"### {r['id']} — {r['metodo']}")
        md.append(f"- **Condicion**: `{r['condicion']}`")
        md.append(f"- **Ubicacion**: `{r['archivo']}:{r['linea']}`")
        md.append(f"- **Original**: `{r['original']}`")
        md.append(f"- **Mutado**: `{r['mutado']}`")
        md.append(f"- **Efecto del mutante**: {r['descripcion']}")
        md.append(f"- **Resultado**: **{r['estado']}** (rc={r['rc']}, {r['duracion_s']} s)")
        if r["test_detectores"]:
            md.append(f"- **Detectado por**: `{r['test_detectores']}`")
        md.append("")
    with open(os.path.join(salida_dir, "fase2_mutacion_condiciones.md"),
              "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))

    print(f"[3/3] Informe escrito. Cobertura de condiciones = {pct_cond:.2f} % "
          f"({condiciones_cubiertas}/{total_cond}); score de mutacion = {pct_mut:.2f} %")
    if sobreviven:
        print("      Mutantes sobrevivientes (huecos de cobertura): " +
              ", ".join(r["id"] for r in sobreviven))
    return 0


if __name__ == "__main__":
    sys.exit(main())
