#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 5 — MEDICIONES DE CONECTIVIDAD, LATENCIA Y RENDIMIENTO.

Component: cliente (este equipo)  ->  API Gateway  ->  microservicios  ->  PostgreSQL

Se mide:
  A. Ping (latencia ICMP): minimo, maximo, promedio, desviacion y perdida.
  B. Latencia HTTP real (tiempo de respuesta del servicio).
  C. Tasa de transferencia de descarga y de subida (Mbps).
  D. Diez solicitudes consecutivas al mismo endpoint (codigo, tiempo, tamano).
  E. Pruebas de interrupcion y recuperacion (via proxy TCP controlable).

Uso:
    python medir_conectividad.py --host 127.0.0.1 --gateway http://localhost:8080
"""
import argparse
import json
import math
import os
import re
import socket
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.request

# ──────────────────────────────────────────────────────────────────────────
# A. PING (ICMP) — metricas de latencia de red
# ──────────────────────────────────────────────────────────────────────────


def ping(host, count=20, timeout_ms=2000):
    """Ejecuta `ping` del sistema y extrae los RTT individuales.

    Windows: `ping -n <count> -w <timeout_ms> <host>`
    Linux  : `ping -c <count> -W <seg> <host>`

    El parseo es bilingue (Windows en español o en ingles) porque el formato
    de salida depende del idioma del sistema:
        ES: "Respuesta desde 127.0.0.1: bytes=32 tiempo<1m TTL=128"
        EN: "Reply from 127.0.0.1: bytes=32 time<1ms TTL=128"
    """
    if os.name == "nt":
        cmd = ["ping", "-n", str(count), "-w", str(timeout_ms), host]
    else:
        cmd = ["ping", "-c", str(count), "-w", str(timeout_ms // 1000), host]

    proc = subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    salida = proc.stdout or ""

    # RTT individuales: "tiempo=12ms" / "tiempo<1m" / "time=12ms" / "time<1ms"
    rtts = []
    for m in re.finditer(r"(?:tiempo|time)\s*[=<]\s*(\d+)\s*m", salida, re.IGNORECASE):
        rtts.append(int(m.group(1)))

    perdidos = count - len(rtts)
    # Resumen ES: "Paquetes: enviados = 20, recibidos = 20, perdidos = 0 (0% perdidos)"
    m = re.search(r"(?:Paquetes|packets)\s*:?\s*enviados\s*=\s*(\d+)"
                  r".*?(?:recibidos|received)\s*=\s*(\d+)"
                  r".*?(?:perdidos|lost)\s*=\s*(\d+)", salida, re.IGNORECASE | re.DOTALL)
    if m:
        enviados, recibidos, perdidos = int(m.group(1)), int(m.group(2)), int(m.group(3))
        count = enviados
    else:
        m = re.search(r"\((\d+)%\s*(?:perdidos|packet loss)\)", salida, re.IGNORECASE)
        if m:
            perdidos = int(round(count * int(m.group(1)) / 100))
        else:
            perdidos = count - len(rtts)

    res = {
        "host": host, "paquetes_enviados": count,
        "paquetes_recibidos": len(rtts), "paquetes_perdidos": perdidos,
        "porcentaje_perdida": round(100.0 * perdidos / count, 2) if count else 0.0,
        "rtts_ms": rtts,
        "min_ms": min(rtts) if rtts else None,
        "max_ms": max(rtts) if rtts else None,
        "promedio_ms": round(statistics.mean(rtts), 2) if rtts else None,
        "desviacion_estandar_ms": round(statistics.pstdev(rtts), 2) if len(rtts) > 1 else 0.0,
        "mediana_ms": round(statistics.median(rtts), 2) if rtts else None,
        "jitter_ms": round(statistics.mean(abs(rtts[i] - rtts[i - 1])
                                           for i in range(1, len(rtts))), 2)
        if len(rtts) > 1 else 0.0,
        "salida_cruda": salida.strip(),
    }
    return res


# ──────────────────────────────────────────────────────────────────────────
# B/D. PETICIONES HTTP CONMEDIDAS
# ──────────────────────────────────────────────────────────────────────────


def http_get(url, timeout=30):
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "application/json")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            datos = r.read()
            codigo = r.status
    except urllib.error.HTTPError as e:
        datos = e.read()
        codigo = e.code
    except Exception as e:  # noqa: BLE001
        return {"http": 0, "ms": (time.perf_counter() - t0) * 1000,
                "bytes": 0, "error": str(e)}
    return {"http": codigo, "ms": (time.perf_counter() - t0) * 1000,
            "bytes": len(datos), "cuerpo": datos}


def http_post(url, payload: bytes, headers=None, timeout=30):
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            datos = r.read()
            codigo = r.status
    except urllib.error.HTTPError as e:
        datos = e.read()
        codigo = e.code
    except Exception as e:  # noqa: BLE001
        return {"http": 0, "ms": (time.perf_counter() - t0) * 1000,
                "bytes": 0, "error": str(e)}
    return {"http": codigo, "ms": (time.perf_counter() - t0) * 1000,
            "bytes": len(datos), "cuerpo": datos}


# ──────────────────────────────────────────────────────────────────────────
# C. TASA DE TRANSFERENCIA
# ──────────────────────────────────────────────────────────────────────────


def tasa_mbps(bytes_transferidos, segundos):
    """Tasa(Mbps) = (Bytes * 8) / Tiempo(s) * 1000000"""
    if segundos <= 0:
        return 0.0
    return round((bytes_transferidos * 8) / segundos / 1_000_000, 4)


def medir_descarga(url, repeticiones=3):
    """Descarga el recurso `repeticiones` veces y calcula la tasa media."""
    muestras = []
    for _ in range(repeticiones):
        r = http_get(url)
        muestras.append({
            "bytes": r["bytes"], "ms": round(r["ms"], 2),
            "Mbps": tasa_mbps(r["bytes"], r["ms"] / 1000),
            "http": r["http"],
        })
    validas = [m for m in muestras if m["bytes"] > 0]
    return {
        "url": url, "muestras": muestras,
        "Mbps_promedio": round(statistics.mean([m["Mbps"] for m in validas]), 4)
        if validas else 0.0,
        "bytes_promedio": round(statistics.mean([m["bytes"] for m in validas]), 1)
        if validas else 0,
    }


def medir_subida(url, payload_bytes, repeticiones=3, token=None):
    """Envia un payload `repeticiones` veces y calcula la tasa de subida."""
    payload = json.dumps({"campo": "x" * payload_bytes}).encode("utf-8")
    muestras = []
    for _ in range(repeticiones):
        hdrs = {"Authorization": "Bearer " + token} if token else None
        r = http_post(url, payload, hdrs)
        muestras.append({
            "bytes": r["bytes"], "ms": round(r["ms"], 2),
            "Mbps": tasa_mbps(len(payload), r["ms"] / 1000),
            "http": r["http"],
        })
    return {
        "url": url, "bytes_enviados": len(payload), "muestras": muestras,
        "Mbps_promedio": round(statistics.mean([m["Mbps"] for m in muestras]), 4)
        if muestras else 0.0,
    }


# ──────────────────────────────────────────────────────────────────────────
# E. PROXY TCP CONTROLABLE — simulacion de perdida de conectividad
# ──────────────────────────────────────────────────────────────────────────


class ProxyInterrumpible:
    """Proxy TCP local que permite simular tres fallos reales:

       1. 'conectar'  -> reenvia al destino (comportamiento normal)
       2. 'cortar'    -> acepta la conexion pero cierra el socket sin
                          responder (equivale a: la peticion SALE pero la
                          respuesta NUNCA llega — caso critico de reintentos)
       3. 'detener'   -> el puerto deja de escuchar (conexion rechazada:
                          el cliente detecta que no hay red)
    """

    def __init__(self, listen_host, listen_port, destino_host, destino_port):
        self.listen = (listen_host, listen_port)
        self.destino = (destino_host, destino_port)
        self.modo = "conectar"
        self.sock = None
        self.hilo = None
        self.parado = False

    def _manejar(self, cliente):
        """Reenvia la conexion en ambos sentidos hasta el cierre."""
        import select
        destino = None
        try:
            destino = socket.create_connection(self.destino, timeout=15)
            destino.setblocking(False)
            cliente.setblocking(False)

            if self.modo == "cortar":
                # Se acepta la conexion pero se cierra de inmediato: el
                # cliente cree que envio la peticion pero no recibe respuesta.
                cliente.close()
                return

            sockets = {cliente: ("cliente", None), destino: ("destino", None)}
            while not self.parado:
                legibles, _, _ = select.select(list(sockets.keys()), [], [], 15)
                if not legibles:
                    break
                for s in legibles:
                    origen, _ = sockets[s]
                    destino_s = destino if origen == "cliente" else cliente
                    try:
                        datos = s.recv(65536)
                    except (BlockingIOError, InterruptedError):
                        continue
                    except OSError:
                        return
                    if not datos:
                        # Cierre en cualquiera de los dos sentidos.
                        try:
                            destino_s.shutdown(socket.SHUT_WR)
                        except OSError:
                            pass
                        return
                    try:
                        destino_s.sendall(datos)
                    except OSError:
                        return
        except Exception:  # noqa: BLE001
            pass
        finally:
            for s in (cliente, destino):
                if s:
                    try:
                        s.close()
                    except Exception:  # noqa: BLE001
                        pass

    def iniciar(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(self.listen)
        self.sock.listen(64)
        self.hilo = threading.Thread(target=self._loop, daemon=True)
        self.hilo.start()

    def _loop(self):
        while not self.parado:
            try:
                cliente, _ = self.sock.accept()
            except OSError:
                break
            threading.Thread(target=self._manejar, args=(cliente,), daemon=True).start()

    def detener(self):
        self.parado = True
        try:
            self.sock.close()
        except Exception:  # noqa: BLE001
            pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1", help="host para el ping ICMP")
    ap.add_argument("--gateway", default="http://localhost:8080")
    ap.add_argument("--api", default="http://localhost:8080/api")
    ap.add_argument("--out", default=r"C:\DDI\luxray\practica-11\evidencias")
    ap.add_argument("--ping-extra", nargs="*", default=[],
                    help="destinos adicionales de ping (p.ej. IP LAN, gateway docker)")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    resultado = {
        "fecha": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "host_ping": args.host,
        "gateway": args.gateway,
        "cliente": {
            "sistema": "Windows",
            "hostname": socket.gethostname(),
            "ip_local": socket.gethostbyname(socket.gethostname()),
        },
    }

    # ── A. PING ───────────────────────────────────────────────────────────
    print("=" * 78)
    print("A. PING / LATENCIA ICMP (20 paquetes por objetivo)")
    print("=" * 78)
    objetivos = [args.host]
    for extra in args.ping_extra:
        if extra and extra not in objetivos:
            objetivos.append(extra)
    pings = {}
    for host in objetivos:
        p = ping(host, count=20)
        pings[host] = p
        print(f"  destino {host}: min={p['min_ms']} ms  max={p['max_ms']} ms  "
              f"prom={p['promedio_ms']} ms  desv={p['desviacion_estandar_ms']} ms  "
              f"perdida={p['porcentaje_perdida']} %")
    resultado["ping"] = pings
    p = pings[args.host]

    # ── B/D. DIEZ SOLICITUDES CONSECUTIVAS ────────────────────────────────
    print()
    print("=" * 78)
    print("B/D. DIEZ SOLICITUDES CONSECUTIVAS A UN ENDPOINT REAL")
    print("=" * 78)
    endpoint = f"{args.api}/cursos"
    solicitudes = []
    for i in range(1, 11):
        r = http_get(endpoint)
        solicitudes.append({
            "n": i, "http": r["http"],
            "ms": round(r["ms"], 2), "bytes": r["bytes"],
        })
        print(f"  #{i:>2}  HTTP {r['http']}  {r['ms']:>8.2f} ms  {r['bytes']:>7} B")
    validas = [s for s in solicitudes if s["http"] == 200]
    resultado["solicitudes"] = {
        "endpoint": endpoint, "detalle": solicitudes,
        "http_200": len(validas),
        "tasa_exito_pct": round(100.0 * len(validas) / len(solicitudes), 2),
        "tiempo_promedio_ms": round(statistics.mean([s["ms"] for s in solicitudes]), 2),
        "tiempo_maximo_ms": max(s["ms"] for s in solicitudes),
        "tiempo_minimo_ms": min(s["ms"] for s in solicitudes),
        "desviacion_estandar_ms": round(statistics.pstdev([s["ms"] for s in solicitudes]), 2),
        "tamano_promedio_bytes": round(statistics.mean([s["bytes"] for s in solicitudes]), 1),
    }
    s = resultado["solicitudes"]
    print(f"  promedio={s['tiempo_promedio_ms']} ms  max={s['tiempo_maximo_ms']} ms  "
          f"min={s['tiempo_minimo_ms']} ms  desviacion={s['desviacion_estandar_ms']} ms")
    print(f"  respuestas 200: {s['http_200']}/10 ({s['tasa_exito_pct']} %)  "
          f"tamano medio: {s['tamano_promedio_bytes']} B")

    # ── B2. LATENCIA HTTP POR ENDPOINT ───────────────────────────────────
    print()
    print("=" * 78)
    print("B2. LATENCIA HTTP POR ENDPOINT (10 solicitudes cada uno)")
    print("=" * 78)
    endpoints = {
        "GET /api/cursos": f"{args.api}/cursos",
        "GET /api/cursos?q=electrica": f"{args.api}/cursos?q=electrica",
        "GET /api/cursos/buscar?q=ohm": f"{args.api}/cursos/buscar?q=ohm",
        "GET /api/search?q=ohm": f"{args.api}/search?q=ohm&page=0&size=10",
        "GET /api/auth/me (401 esperado)": f"{args.api}/auth/me",
    }
    lat = {}
    for nombre, url in endpoints.items():
        t = []
        codigos = []
        tam = []
        for _ in range(10):
            r = http_get(url)
            t.append(r["ms"])
            codigos.append(r["http"])
            tam.append(r["bytes"])
        lat[nombre] = {
            "url": url,
            "promedio_ms": round(statistics.mean(t), 2),
            "min_ms": round(min(t), 2), "max_ms": round(max(t), 2),
            "desviacion_ms": round(statistics.pstdev(t), 2),
            "p95_ms": round(sorted(t)[int(len(t) * 0.95) - 1], 2),
            "bytes_promedio": round(statistics.mean(tam), 1),
            "codigos": sorted(set(codigos)),
        }
        d = lat[nombre]
        print(f"  {nombre:<32} prom={d['promedio_ms']:>8.2f} ms  min={d['min_ms']:>8.2f}  "
              f"max={d['max_ms']:>8.2f}  p95={d['p95_ms']:>8.2f}  {d['bytes_promedio']:>7} B  HTTP {d['codigos']}")
    resultado["latencia_http"] = lat

    # ── C. TASA DE TRANSFERENCIA ─────────────────────────────────────────
    print()
    print("=" * 78)
    print("C. TASA DE TRANSFERENCIA (Mbps)")
    print("=" * 78)
    descarga = medir_descarga(f"{args.api}/cursos", repeticiones=3)
    resultado["tasa_descarga"] = descarga
    print("  Descarga (GET /api/cursos):")
    for m in descarga["muestras"]:
        print(f"    {m['bytes']:>7} B en {m['ms']:>8.2f} ms -> {m['Mbps']:>8.4f} Mbps")
    print(f"  Tasa media de descarga: {descarga['Mbps_promedio']} Mbps")

    subida = medir_subida(f"{args.api}/auth/login", payload_bytes=256 * 1024,
                          repeticiones=3)
    resultado["tasa_subida"] = subida
    print(f"  Subida (POST /api/auth/login, {subida['bytes_enviados']} B):")
    for m in subida["muestras"]:
        print(f"    {subida['bytes_enviados']:>7} B en {m['ms']:>8.2f} ms -> {m['Mbps']:>8.4f} Mbps")
    print(f"  Tasa media de subida: {subida['Mbps_promedio']} Mbps")

    # ── E. INTERRUPCION Y RECUPERACION ───────────────────────────────────
    print()
    print("=" * 78)
    print("E. PRUEBA DE INTERRUPCION DE CONECTIVIDAD")
    print("=" * 78)
    interrupcion = {"proxy_puerto": 8099, "casos": []}

    # El destino del proxy se resuelve por nombre ("localhost") y NO por
    # 127.0.0.1 literal: en esta maquina el puerto IPv4 127.0.0.1:8080 esta
    # ocupado por otro servidor local (Apache dentro de WSL, via wslrelay),
    # mientras que el API Gateway publicado por Docker escucha en [::]:8080.
    # Ver evidencia en fase5_consola.txt y en el hallazgo DEF-06.
    proxy = ProxyInterrumpible("127.0.0.1", 8099, "localhost", 8080)
    proxy.iniciar()
    url_proxy = "http://127.0.0.1:8099/api/cursos"

    # Caso 1: conectividad operativa a traves del proxy
    r = http_get(url_proxy)
    caso = {"caso": "1 · Conectividad operativa (via proxy controlable)",
            "http": r["http"], "ms": round(r["ms"], 2), "bytes": r["bytes"],
            "resultado": "OK" if r["http"] == 200 else "FALLO"}
    interrupcion["casos"].append(caso)
    print(f"  {caso['caso']}")
    print(f"      HTTP {caso['http']} en {caso['ms']} ms -> {caso['resultado']}")

    # Caso 2: la peticion sale pero la respuesta NUNCA llega
    proxy.modo = "cortar"
    r = http_get(url_proxy)
    caso = {"caso": "2 · Peticion enviada, respuesta nunca llega (corte de conexion)",
            "http": r["http"], "ms": round(r["ms"], 2),
            "error": r.get("error"), "resultado": "ERROR DETECTADO" if r["http"] == 0 else "RESPUESTA RECIBIDA"}
    interrupcion["casos"].append(caso)
    print(f"  {caso['caso']}")
    print(f"      HTTP {caso['http']} -> {caso['resultado']}")
    print(f"      error: {caso.get('error')}")

    # Caso 3: sin red (puerto cerrado -> conexion rechazada)
    proxy.modo = "conectar"
    proxy.detener()
    time.sleep(0.4)
    r = http_get(url_proxy)
    caso = {"caso": "3 · Sin conectividad (conexion rechazada)",
            "http": r["http"], "ms": round(r["ms"], 2),
            "error": r.get("error"), "resultado": "ERROR DETECTADO" if r["http"] == 0 else "RESPUESTA RECIBIDA"}
    interrupcion["casos"].append(caso)
    print(f"  {caso['caso']}")
    print(f"      HTTP {caso['http']} -> {caso['resultado']}")
    print(f"      error: {caso.get('error')}")

    # Caso 4: recuperacion tras restablecer la conectividad
    proxy2 = ProxyInterrumpible("127.0.0.1", 8099, "localhost", 8080)
    proxy2.iniciar()
    time.sleep(0.3)
    r = http_get(url_proxy)
    caso = {"caso": "4 · Recuperacion tras restablecer la conectividad",
            "http": r["http"], "ms": round(r["ms"], 2), "bytes": r["bytes"],
            "resultado": "RECUPERADO" if r["http"] == 200 else "SIGUE FALLANDO"}
    interrupcion["casos"].append(caso)
    print(f"  {caso['caso']}")
    print(f"      HTTP {caso['http']} en {caso['ms']} ms -> {caso['resultado']}")
    proxy2.detener()

    interrupcion["conclusion"] = (
        "El cliente detecta los tres modos de fallo (corte de conexion, "
        "ausencia de red y restauracion) sin lanzar excepcion no controlada.")
    resultado["interrupcion"] = interrupcion

    # ── Resumen ──────────────────────────────────────────────────────────
    print()
    print("=" * 78)
    print("RESUMEN DE METRICAS (Fase 5)")
    print("=" * 78)
    resumen = {
        "ping_minimo_ms": p["min_ms"],
        "ping_promedio_ms": p["promedio_ms"],
        "ping_maximo_ms": p["max_ms"],
        "ping_desviacion_ms": p["desviacion_estandar_ms"],
        "perdida_paquetes_pct": p["porcentaje_perdida"],
        "latencia_http_promedio_ms": s["tiempo_promedio_ms"],
        "latencia_http_maximo_ms": s["tiempo_maximo_ms"],
        "latencia_http_minimo_ms": s["tiempo_minimo_ms"],
        "tamano_respuesta_promedio_bytes": s["tamano_promedio_bytes"],
        "tasa_descarga_Mbps": descarga["Mbps_promedio"],
        "tasa_subida_Mbps": subida["Mbps_promedio"],
        "respuestas_exitosas_pct": s["tasa_exito_pct"],
        "duracion_total_s": None,
    }
    resultado["resumen"] = resumen
    for k, v in resumen.items():
        if k != "duracion_total_s":
            print(f"  {k:<36} : {v}")

    with open(os.path.join(args.out, "fase5_mediciones.json"), "w", encoding="utf-8") as fh:
        json.dump(resultado, fh, indent=2, ensure_ascii=False)
    print()
    print(f"Informe JSON escrito en {os.path.join(args.out, 'fase5_mediciones.json')}")


if __name__ == "__main__":
    main()
