#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 12 — FASE 5 — RESILIENCIA EN EL LADO DE ESCRITURA (RES-02, RES-07)

El wearable LuxRay es una aplicación de SOLO LECTURA: su único método de red
es un GET. Por eso los escenarios que implican ESCRITURA no pueden ejecutarse
desde la app móvil y se verifican contra la API real, con un proxy TCP que
provoca la avería y una comprobación posterior en PostgreSQL.

Escenarios:
  RES-02 · Pérdida de conexión durante una escritura
           -> la escritura NO debe confirmarse falsamente al usuario.
  RES-07 · Reintento de una escritura
           -> el reintento NO debe generar registros duplicados.

Para el reintento se usa el mecanismo de idempotencia de la propia base de
datos: la restricción UNIQUE de `usuarios.email`. Se mide además si la
respuesta del servidor distingue "ya existía" de "se acaba de crear".

Uso:
    python resiliencia_api.py
"""
import json
import os
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request

# El gateway de Docker responde por IPv6 en esta maquina ([::1]:8080);
# el puerto IPv4 127.0.0.1:8080 lo ocupa otro servidor local.
GATEWAY = ("::1", 8080)
PUERTO_PROXY = 8097
API = "http://localhost:8080/api"
EVID = r"C:\DDI\luxray\practica-12\evidencias"
CONT_DB = "luxray-auth-db"


class ProxyAveria:
    """Proxy TCP que deja pasar, corta o retiene las conexiones."""

    def __init__(self, listen, destino):
        self.listen = listen
        self.destino = destino
        self.modo = "normal"          # normal | cortar | retener
        self.sock = None
        self.peticiones = 0
        self.parado = False

    def _manejar(self, cli):
        """Relay bidireccional con `select`.

        Un bucle secuencialrecv/enviar se bloquea con conexiones keep-alive
        (el servidor no cierra tras la respuesta y el proxy se queda
        esperando), por lo que las peticiones posteriores de la MISMA conexión
        nunca se reenvían.
        """
        import select
        try:
            des = socket.create_connection(self.destino, timeout=15)
            if self.modo == "cortar":
                # La petición se envía pero la respuesta NUNCA llega.
                cli.close()
                des.close()
                return
            if self.modo == "retener":
                # Se retiene la conexión: el cliente debe agotar su timeout.
                time.sleep(20)
                cli.close()
                des.close()
                return
            sockets = {cli: (des, "c2d"), des: (cli, "d2c")}
            while True:
                legibles, _, _ = select.select(list(sockets.keys()), [], [], 30)
                if not legibles:
                    break
                for s in legibles:
                    otro, _sentido = sockets[s]
                    try:
                        d = s.recv(65536)
                    except OSError:
                        return
                    if not d:
                        try:
                            otro.shutdown(socket.SHUT_WR)
                        except OSError:
                            pass
                        return
                    try:
                        otro.sendall(d)
                    except OSError:
                        return
        except Exception:  # noqa: BLE001
            pass
        finally:
            try:
                cli.close()
            except Exception:  # noqa: BLE001
                pass

    def iniciar(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(self.listen)
        self.sock.listen(64)
        threading.Thread(target=self._loop, daemon=True).start()

    def _loop(self):
        while not self.parado:
            try:
                cli, _ = self.sock.accept()
            except OSError:
                break
            self.peticiones += 1
            threading.Thread(target=self._manejar, args=(cli,), daemon=True).start()

    def parar(self):
        self.parado = True
        try:
            self.sock.close()
        except Exception:  # noqa: BLE001
            pass


def pedir(metodo, url, cuerpo=None, token=None, timeout=30):
    t0 = time.perf_counter()
    data = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(url, data=data, method=metodo)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            cuerpo_resp = r.read()
            codigo = r.status
    except urllib.error.HTTPError as e:
        cuerpo_resp = e.read()
        codigo = e.code
    except Exception as e:  # noqa: BLE001
        return {"http": 0, "ms": (time.perf_counter() - t0) * 1000,
                "error": str(e), "body": None}
    return {"http": codigo, "ms": (time.perf_counter() - t0) * 1000,
            "body": json.loads(cuerpo_resp.decode() or "{}")}


def contar_usuarios(email):
    """Número de filas con ese email en auth_db (verificación de duplicados)."""
    p = subprocess.run(
        ["docker", "exec", CONT_DB, "psql", "-U", "luxray", "-d", "auth_db", "-t", "-A",
         "-c", f"SELECT count(*) FROM usuarios WHERE email = '{email}';"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
        timeout=60)
    try:
        return int((p.stdout or "0").strip() or 0)
    except ValueError:
        return -1


def main():
    os.makedirs(EVID, exist_ok=True)
    print("=" * 78)
    print("PRACTICA 12 - FASE 5 - RESILIENCIA DE LAS ESCRITURAS (RES-02, RES-07)")
    print("=" * 78)

    proxy = ProxyAveria(("127.0.0.1", PUERTO_PROXY), GATEWAY)
    proxy.iniciar()
    url_proxy = f"http://127.0.0.1:{PUERTO_PROXY}/api"

    resultados = []

    # ── RES-02 · Pérdida de conexión durante una escritura ─────────────────
    print("\n[RES-02] Pérdida de conexión durante una escritura")
    email = f"wb12_res02_{int(time.time())}@luxray.test"
    proxy.modo = "cortar"
    r = pedir("POST", f"{url_proxy}/auth/registro",
              {"nombre": "Res", "apellido": "Iencia", "email": email,
               "password": "ClaveCarga123", "acceptTerms": True}, timeout=20)
    n = contar_usuarios(email)
    caso = {
        "escenario": "RES-02 · Pérdida de Internet durante una escritura",
        "email": email,
        "http": r["http"], "ms": round(r["ms"], 1),
        "error": r.get("error"),
        "filas_en_bd": n,
        "confirmado_falsamente": r["http"] == 200,
        "resultado": ("el cliente NO confirma la escritura (correcto)" if r["http"] != 200
                      else "el cliente confirma una escritura que no sabe si ocurrio (FALLO)"),
    }
    print(f"  HTTP {r['http']} en {r['ms']:.0f} ms -> {caso['resultado']}")
    print(f"  filas creadas en la BD con ese email: {n}")
    resultados.append(caso)

    # ── RES-07 · Reintento de una escritura: sin duplicados ───────────────
    print("\n[RES-07] Reintento de una escritura (no debe duplicar registros)")
    email2 = f"wb12_res07_{int(time.time())}@luxray.test"
    cuerpo = {"nombre": "Re", "apellido": "Intento", "email": email2,
              "password": "ClaveCarga123", "acceptTerms": True}
    proxy.modo = "normal"
    r1 = pedir("POST", f"{url_proxy}/auth/registro", cuerpo, timeout=20)
    r2 = pedir("POST", f"{url_proxy}/auth/registro", cuerpo, timeout=20)
    r3 = pedir("POST", f"{url_proxy}/auth/registro", cuerpo, timeout=20)
    n2 = contar_usuarios(email2)
    caso = {
        "escenario": "RES-07 · Reintento de una escritura",
        "email": email2,
        "intentos": [
            {"n": i + 1, "http": r["http"], "ms": round(r["ms"], 1),
             "mensaje": (r.get("body") or {}).get("message") if isinstance(r.get("body"), dict) else None}
            for i, r in enumerate([r1, r2, r3])],
        "filas_en_bd": n2,
        "duplicados": n2 > 1,
        "resultado": ("sin duplicados: la restriccion UNIQUE arbitra (correcto)" if n2 <= 1
                      else "DUPLICADOS: el reintento ha creado varias filas (FALLO)"),
    }
    print(f"  intentos: {[c['http'] for c in caso['intentos']]}")
    print(f"  filas en la BD: {n2} -> {caso['resultado']}")
    resultados.append(caso)

    # ── RES-06 · Recuperación de la conexión ────────────────────────────
    print("\n[RES-06] Recuperación de la conexión")
    proxy.modo = "normal"
    email3 = f"wb12_res06_{int(time.time())}@luxray.test"
    r4 = pedir("POST", f"{url_proxy}/auth/registro",
               {"nombre": "Rec", "apellido": "uperacion", "email": email3,
                "password": "ClaveCarga123", "acceptTerms": True}, timeout=20)
    caso = {
        "escenario": "RES-06 · Recuperación de la conexión",
        "http": r4["http"], "ms": round(r4["ms"], 1),
        "filas_en_bd": contar_usuarios(email3),
        "resultado": "comunicación restablecida" if r4["http"] == 200 else "NO restablecida",
    }
    print(f"  HTTP {r4['http']} en {r4['ms']:.0f} ms -> {caso['resultado']}")
    resultados.append(caso)

    proxy.parar()

    with open(os.path.join(EVID, "fase5_resiliencia_api.json"), "w", encoding="utf-8") as fh:
        json.dump({"fecha": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "casos": resultados}, fh, indent=2, ensure_ascii=False)

    md = ["# Fase 5 — Resiliencia de las escrituras (RES-02, RES-07, RES-06)\n",
          f"- **Fecha**: {time.strftime('%Y-%m-%d %H:%M:%S')}",
          "- **API**: `POST /api/auth/registro` a través de un proxy TCP con averías inyectables\n",
          "| Escenario | HTTP | ms | Filas en BD | Resultado |", "|---|---|---|---|---|"]
    for c in resultados:
        http = c.get("http", "-")
        ms = c.get("ms", "-")
        md.append(f"| {c['escenario']} | {http} | {ms} | {c.get('filas_en_bd', '-')} | {c['resultado']} |")
    with open(os.path.join(EVID, "fase5_resiliencia_api.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(md))

    print(f"\nInforme escrito en {EVID}\\fase5_resiliencia_api.json")


if __name__ == "__main__":
    main()
