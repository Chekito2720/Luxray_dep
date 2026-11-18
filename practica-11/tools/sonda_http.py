#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 3 — Sonda de contratos HTTP del API Gateway LuxRay.

Ejecuta una bateria de peticiones contra el stack Docker en ejecucion y anota
el codigo HTTP real, la estructura JSON y el tiempo de respuesta. Sirve como
linea base para:
  * Fase 3  -> verificacion de codigos HTTP y consistencia de respuestas
  * Fase 5  -> latencia y tamano de respuesta
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api"
OUT = sys.argv[2] if len(sys.argv) > 2 else "C:/DDI/luxray/practica-11/evidencias/fase3_sonda_http.json"


def call(method, path, body=None, token=None, raw=False, label=""):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            payload = r.read()
            code = r.status
    except urllib.error.HTTPError as e:
        payload = e.read()
        code = e.code
    except Exception as e:  # noqa: BLE001
        payload = str(e).encode()
        code = 0
    dt = (time.perf_counter() - t0) * 1000
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except Exception:  # noqa: BLE001
        parsed = payload.decode("utf-8", "replace")
    if raw:
        parsed = payload.decode("utf-8", "replace")
    rec = {
        "label": label or f"{method} {path}",
        "method": method, "path": path, "http": code,
        "ms": round(dt, 2), "bytes": len(payload),
        "body": parsed if isinstance(parsed, (dict, list)) else parsed[:400],
    }
    print(f"[{code:>3}] {dt:8.2f} ms  {len(payload):>7} B  {rec['label']}")
    return rec


def main():
    results = []
    suffix = str(int(time.time()))
    email = f"wb11_{suffix}@luxray.test"
    email2 = f"wb11b_{suffix}@luxray.test"

    print("=" * 78)
    print("A. LECTURA PUBLICA (sin autenticacion)")
    print("=" * 78)
    results.append(call("GET", "/cursos", label="GET /cursos (lista publica)"))
    results.append(call("GET", "/cursos?nivel=BASICO", label="GET /cursos?nivel=BASICO"))
    results.append(call("GET", "/cursos?q=electrica", label="GET /cursos?q=electrica"))
    results.append(call("GET", "/cursos/buscar?q=ohm", label="GET /cursos/buscar?q=ohm"))
    results.append(call("GET", "/search?q=ohm&page=0&size=10", label="GET /search?q=ohm"))
    results.append(call("GET", "/search", label="GET /search (sin q)"))
    results.append(call("GET", "/search?q=", label="GET /search?q= (q vacio)"))
    results.append(call("GET", "/cursos/00000000-0000-0000-0000-000000000000",
                        label="GET /cursos/{uuid inexistente}"))
    results.append(call("GET", "/cursos/no-es-uuid", label="GET /cursos/no-es-uuid"))

    print()
    print("=" * 78)
    print("B. REGISTRO / LOGIN  (validacion, duplicados, credenciales)")
    print("=" * 78)
    results.append(call("POST", "/auth/registro",
                        {"nombre": "Al", "apellido": "Uno", "email": email,
                         "password": "clave1234", "acceptTerms": True},
                        label="POST /auth/registro (valido)"))
    results.append(call("POST", "/auth/registro",
                        {"nombre": "Al", "apellido": "Uno", "email": email,
                         "password": "clave1234", "acceptTerms": True},
                        label="POST /auth/registro (DUPLICADO)"))
    results.append(call("POST", "/auth/registro",
                        {"nombre": "A", "apellido": "B", "email": "no-valido",
                         "password": "12345678", "acceptTerms": True},
                        label="POST /auth/registro (email invalido)"))
    results.append(call("POST", "/auth/registro",
                        {"nombre": "A", "apellido": "B", "email": f"x_{suffix}@l.test",
                         "password": "123", "acceptTerms": True},
                        label="POST /auth/registro (password corta)"))
    results.append(call("POST", "/auth/registro",
                        {"nombre": "Al", "apellido": "Uno", "email": f"y_{suffix}@l.test",
                         "password": "clave1234", "acceptTerms": False},
                        label="POST /auth/registro (sin aceptar terminos)"))
    results.append(call("POST", "/auth/registro", {"email": email},
                        label="POST /auth/registro (body incompleto)"))
    results.append(call("POST", "/auth/registro", raw=True,
                        body="{'esto': no es json}",
                        label="POST /auth/registro (JSON malformado)"))

    results.append(call("POST", "/auth/login",
                        {"email": email, "password": "clave1234"},
                        label="POST /auth/login (correcto)"))
    results.append(call("POST", "/auth/login",
                        {"email": email, "password": "incorrecta"},
                        label="POST /auth/login (password incorrecta)"))
    results.append(call("POST", "/auth/login",
                        {"email": "noexiste@luxray.test", "password": "clave1234"},
                        label="POST /auth/login (usuario inexistente)"))
    results.append(call("POST", "/auth/login", {"email": email},
                        label="POST /auth/login (body incompleto)"))

    print()
    print("=" * 78)
    print("C. AUTENTICACION / AUTORIZACION")
    print("=" * 78)
    r = call("POST", "/auth/login", {"email": email, "password": "clave1234"},
             label="POST /auth/login (para obtener token)")
    token = None
    if isinstance(r["body"], dict):
        token = (r["body"].get("data") or {}).get("token")
    print(f"      token obtenido: {'SI' if token else 'NO'}")

    results.append(call("GET", "/auth/me", token=token, label="GET /auth/me (con token)"))
    results.append(call("GET", "/auth/me", label="GET /auth/me (SIN token)"))
    results.append(call("GET", "/auth/me", token="token.invalido.fake",
                        label="GET /auth/me (token invalido)"))
    results.append(call("GET", "/auth/admin/usuarios", token=token,
                        label="GET /auth/admin/usuarios (rol USER -> 403)"))
    results.append(call("GET", "/auth/admin/usuarios",
                        label="GET /auth/admin/usuarios (sin token -> 401)"))
    results.append(call("GET", "/cursos/mis-cursos", token=token,
                        label="GET /cursos/mis-cursos (con token)"))
    results.append(call("GET", "/cursos/mis-cursos", label="GET /cursos/mis-cursos (sin token)"))
    results.append(call("GET", "/analytics/dashboard", token=token,
                        label="GET /analytics/dashboard (con token)"))
    results.append(call("GET", "/analytics/dashboard", label="GET /analytics/dashboard (sin token)"))
    results.append(call("GET", "/cursos/admin/todos", token=token,
                        label="GET /cursos/admin/todos (rol USER -> 403)"))
    results.append(call("POST", "/cursos/admin", {"titulo": "T", "descripcion": "D", "nivel": "BASICO"},
                        token=token, label="POST /cursos/admin (rol USER -> 403)"))
    results.append(call("POST", "/cursos/admin", {"titulo": "T", "descripcion": "D", "nivel": "BASICO"},
                        label="POST /cursos/admin (sin token -> 401)"))

    print()
    print("=" * 78)
    print("D. ESCRITURA (inscripcion) Y VALIDACION DE CURSO")
    print("=" * 78)
    # Se usa un curso real del catalogo
    r = call("GET", "/cursos", label="GET /cursos (para obtener id)")
    curso_id = None
    if isinstance(r["body"], dict):
        data = r["body"].get("data") or []
        if data:
            curso_id = data[0].get("id")
    print(f"      curso_id: {curso_id}")

    if curso_id:
        results.append(call("POST", f"/cursos/{curso_id}/inscripcion", token=token,
                            label="POST /cursos/{id}/inscripcion (con token)"))
        results.append(call("POST", f"/cursos/{curso_id}/inscripcion", token=token,
                            label="POST /cursos/{id}/inscripcion (REPETIDA -> 409?)"))
        results.append(call("POST", f"/cursos/{curso_id}/inscripcion",
                            label="POST /cursos/{id}/inscripcion (sin token -> 401)"))
    results.append(call("POST", "/cursos/00000000-0000-0000-0000-000000000000/inscripcion",
                        token=token, label="POST /cursos/{inexistente}/inscripcion -> 404?"))
    results.append(call("POST", "/cursos/lecciones/00000000-0000-0000-0000-000000000000/completar",
                        token=token, label="POST /cursos/lecciones/{id}/completar (inexistente)"))

    print()
    print("=" * 78)
    print("E. RUTAS INEXISTENTES Y METODOS NO PERMITIDOS")
    print("=" * 78)
    results.append(call("GET", "/ruta/que/no/existe", label="GET /ruta inexistente -> 404"))
    results.append(call("DELETE", "/cursos", label="DELETE /cursos (metodo no permitido)"))
    results.append(call("GET", "/search?q=" + "a" * 500, label="GET /search?q= (500 chars)"))

    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"base": BASE, "fecha": time.strftime("%Y-%m-%dT%H:%M:%S"),
                   "resultados": results}, fh, indent=2, ensure_ascii=False)
    print()
    print(f"Informe escrito en {OUT}")
    print("Resumen de codigos HTTP:")
    from collections import Counter
    c = Counter(r["http"] for r in results)
    for k, v in sorted(c.items()):
        print(f"   HTTP {k}: {v} respuestas")


if __name__ == "__main__":
    main()
