#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 5 (NET-05/NET-06) — RELAY TCP CONTROLABLE PARA LA PRUEBA
DE INTERRUPCION EN DISPOSITIVO REAL (Wear OS emulado).

Problema que resuelve
---------------------
En la maquina de pruebas, el puerto IPv4 127.0.0.1:8080 esta ocupado por un
servidor Apache (WSL) y el API Gateway de LuxRay (publicado por Docker)
responde unicamente por IPv6 en [::1]:8080. El emulador de Android usa
10.0.2.2 -> 127.0.0.1 (IPv4), de modo que el wearable NO puede alcanzar el
gateway por la ruta por defecto. Evidencia: `adb shell nc 10.0.2.2 8080`
responde "HTTP/1.1 404 Not Found - Server: Apache".

Este relay escucha en 0.0.0.0:8099 (IPv4, alcanzable desde el emulador como
10.0.2.2:8099) y reenvia el trafico a [::1]:8080, que si es el gateway real.
Permite:
  * ejecutar la prueba de interrupcion REAL en el dispositivo (parando y
    arrancando el relay), en lugar de simularla con dobles de prueba;
  * comprobar que la app detecta el fallo y se recupera.

Uso:
    python relay_dispositivo.py --escucha 0.0.0.0:8099 --destino [::1]:8080
"""
import argparse
import json
import os
import select
import socket
import sys
import threading
import time

estado = {"conexiones": 0, "peticiones": 0, "bytes_cliente_a_destino": 0,
          "bytes_destino_a_cliente": 0, "inicio": time.strftime("%Y-%m-%dT%H:%M:%S"),
          "rutas": []}

# Fichero de estado que consume el arnés de profiling para VERIFICAR que cada
# búsqueda llegó al backend (ruta absoluta: no depende del cwd).
ESTADO_FICHERO = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "practica-12", "evidencias", "relay_estado.json")


def volcar_estado():
    try:
        with open(ESTADO_FICHERO, "w", encoding="utf-8") as fh:
            json.dump(estado, fh, indent=2, ensure_ascii=False)
    except Exception as exc:  # noqa: BLE001
        print(f"[RELAY] no se pudo volcar el estado: {exc}", flush=True)


def parse_destino(d):
    """Acepta 'host:puerto', '[::1]:8080' o 'puerto'."""
    if d.startswith("["):
        i = d.index("]")
        return d[1:i], int(d[i + 2:])
    if ":" in d:
        h, p = d.rsplit(":", 1)
        return h, int(p)
    return "127.0.0.1", int(d)


def pump(cliente, destino, etiqueta):
    """Reenvia la conexion en ambos sentidos y CONTABILIZA las peticiones HTTP.

    El cliente HTTP de Dart reutiliza la conexion (keep-alive), de modo que el
    numero de conexiones NO es el numero de peticiones: hay que contar las
    lineas de peticion HTTP ("GET /ruta HTTP/1.1") que travels de cliente a
    destino.
    """
    import select
    sockets = {cliente: (destino, "c2d"), destino: (cliente, "d2c")}
    buffer_c2d = b""
    try:
        while True:
            legibles, _, _ = select.select(list(sockets.keys()), [], [], 30)
            if not legibles:
                return
            for s in legibles:
                otro, sentido = sockets[s]
                try:
                    datos = s.recv(65536)
                except OSError:
                    return
                if not datos:
                    try:
                        otro.shutdown(socket.SHUT_WR)
                    except OSError:
                        pass
                    return
                try:
                    otro.sendall(datos)
                except OSError:
                    return
                if sentido == "c2d":
                    estado["bytes_cliente_a_destino"] += len(datos)
                    # Contabilizacion de peticiones HTTP
                    buffer_c2d += datos
                    while b"\r\n" in buffer_c2d:
                        linea, buffer_c2d = buffer_c2d.split(b"\r\n", 1)
                        try:
                            texto = linea.decode("utf-8", "replace")
                        except Exception:  # noqa: BLE001
                            break
                        if texto.startswith(("GET ", "POST ", "PUT ", "DELETE ",
                                            "PATCH ", "HEAD ", "OPTIONS ")):
                            partes = texto.split()
                            ruta = partes[1] if len(partes) > 1 else "?"
                            estado["peticiones"] += 1
                            estado["rutas"].append(f"{partes[0]} {ruta}")
                            print(f"[RELAY] peticion #{estado['peticiones']} "
                                  f"{partes[0]} {ruta}", flush=True)
                            volcar_estado()
                else:
                    estado["bytes_destino_a_cliente"] += len(datos)
    finally:
        for s in (cliente, destino):
            try:
                s.close()
            except OSError:
                pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--escucha", default="0.0.0.0:8099")
    ap.add_argument("--destino", default="[::1]:8080")
    args = ap.parse_args()

    h_esc, p_esc = parse_destino(args.escucha)
    h_des, p_des = parse_destino(args.destino)

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((h_esc, p_esc))
    srv.listen(128)
    print(f"[RELAY] escuchando en {h_esc}:{p_esc} -> {h_des}:{p_des}", flush=True)

    # Hilo dedicado al volcado periodico del estado (el bucle de aceptacion
    # es infinito y no debe bloquearlo).
    def volcar_periodicamente():
        while True:
            time.sleep(1)
            volcar_estado()

    threading.Thread(target=volcar_periodicamente, daemon=True).start()
    volcar_estado()

    while True:
        cli, _ = srv.accept()
        estado["conexiones"] += 1
        n = estado["conexiones"]

        def h(cli=cli, n=n):
            try:
                des = socket.create_connection((h_des, p_des), timeout=15)
            except OSError as e:
                print(f"[RELAY] conexion #{n}: destino inalcanzable ({e})", flush=True)
                cli.close()
                return
            print(f"[RELAY] conexion #{n} establecida", flush=True)
            pump(cli, des, n)

        threading.Thread(target=h, daemon=True).start()

    print(f"[RELAY] listo en {h_esc}:{p_esc} -> {h_des}:{p_des}", flush=True)
    volcar_estado()
    while True:
        time.sleep(1)
        volcar_estado()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
