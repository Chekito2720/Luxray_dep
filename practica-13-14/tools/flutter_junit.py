#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 13/14 — FASE 4 · Convertidor de la salida de `flutter test --machine`
al formato JUnit XML.

`flutter test` no publica un informe JUnit de forma nativa; el reporter
`--machine` emite JSON por líneas con el evento de cada prueba. Este script
convierte esa traza en JUnit XML para que GitHub Actions pueda publicar los
resultados en la pestaña «Checks» y archivarlos como artefacto.

Uso:
    flutter test --machine > flutter_test.json
    python flutter_junit.py flutter_test.json flutter_test.xml

Formato de entrada (una línea JSON por evento):
    {"type":"suite","suite":{"id":0,"path":"test/x.dart"},...}
    {"type":"testStart","test":{"id":1,"name":"...","suiteID":0},...}
    {"type":"testDone","testID":1,"result":"success","hidden":false,...}
    {"type":"error","testID":1,"error":"...","stackTrace":"...",...}
    {"type":"done","success":true,...}
"""
import json
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def convertir(origen, destino):
    suites = {}          # suiteID -> path
    tests = {}           # testID -> {name, suiteID, result, error, stack, time}
    orden = []
    exitoso = True

    with open(origen, "r", encoding="utf-8", errors="replace") as fh:
        for linea in fh:
            linea = linea.strip()
            if not linea:
                continue
            try:
                ev = json.loads(linea)
            except json.JSONDecodeError:
                continue
            # Alguna version del reporter emite arrays o escalares; solo
            # interesan los objetos con "type".
            if not isinstance(ev, dict):
                continue
            tipo = ev.get("type")
            if tipo == "suite":
                s = ev.get("suite", {})
                suites[s.get("id")] = s.get("path", "?")
            elif tipo == "testStart":
                t = ev.get("test", {})
                tests[t.get("id")] = {
                    "name": t.get("name", "?"),
                    "suiteID": t.get("suiteID"),
                    "result": None, "error": None, "stack": None,
                    "time": 0, "start": ev.get("time", 0),
                }
                orden.append(t.get("id"))
            elif tipo == "testDone":
                t = tests.get(ev.get("testID"))
                if t is not None:
                    t["result"] = ev.get("result")
                    t["time"] = ev.get("time", 0) - t.get("start", 0)
            elif tipo == "error":
                t = tests.get(ev.get("testID"))
                if t is not None:
                    t["error"] = ev.get("error")
                    t["stack"] = ev.get("stackTrace")
            elif tipo == "done":
                exitoso = bool(ev.get("success"))

    # JUnit
    raiz = ET.Element("testsuites")
    por_suite = defaultdict(list)
    for tid in orden:
        t = tests[tid]
        por_suite[t["suiteID"]].append(t)

    for sid, lista in por_suite.items():
        ruta = suites.get(sid, "?")
        fallos = sum(1 for t in lista if t["result"] == "failure")
        errores = sum(1 for t in lista if t["result"] == "error")
        saltadas = sum(1 for t in lista if t["result"] == "skipped")
        ts = ET.SubElement(raiz, "testsuite", {
            "name": ruta,
            "tests": str(len(lista)),
            "failures": str(fallos),
            "errors": str(errores),
            "skipped": str(saltadas),
        })
        for t in lista:
            tc = ET.SubElement(ts, "testcase", {
                "name": t["name"],
                "classname": ruta,
                "time": f"{max(0, t['time']) / 1000.0:.3f}",
            })
            if t["result"] in ("failure", "error"):
                f = ET.SubElement(tc, "failure", {
                    "message": (t["error"] or "fallo")[:500],
                    "type": "test_failure",
                })
                f.text = (t["stack"] or t["error"] or "")[:8000]
            elif t["result"] == "skipped":
                ET.SubElement(tc, "skipped")

    ET.indent(raiz, space="  ")
    with open(destino, "w", encoding="utf-8") as fh:
        fh.write(ET.tostring(raiz, encoding="unicode"))

    total = len(tests)
    fallos = sum(1 for t in tests.values() if t["result"] in ("failure", "error"))
    print(f"convertido: {total} pruebas, {fallos} fallos, "
          f"{'OK' if exitoso else 'FALLO'} -> {destino}")
    return 0 if exitoso else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Uso: flutter_junit.py <entrada.json> <salida.xml>")
        sys.exit(2)
    sys.exit(convertir(sys.argv[1], sys.argv[2]))
