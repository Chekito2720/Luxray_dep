#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 12 — FASE 2 — PROFILING Y DIAGNÓSTICO DE LA APLICACIÓN MÓVIL

Instrumenta el wearable LuxRay (Wear OS, emulador Android API 36) y mide, en
escenarios de uso reproducibles:

  * CPU (media, máximo, muestras)      -> `adb shell top -b -n N -d 1 -p <pid>`
  * RAM  (PSS inicial, máximo, final)  -> `adb shell dumpsys meminfo`
  * Frames (jank, percentiles)          -> `adb shell dumpsys gfxinfo`
  * Flujo real de peticiones           -> relay TCP de diagnostico

Escenarios:
  S0 · Reposo            proceso en reposo, sin interaccion
  S1 · Consulta repetida 10 busquedas identicas (chip "ley de ohm")
  S2 · Navegacion        20 transiciones inicio <-> resultados con 2 chips
  S3 · Procesamiento     busquedas con N resultados + apertura del detalle

Uso:
    python perfil_movil.py --etiqueta antes [--apk ruta.apk]
"""
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import threading
import time

SERIAL = "emulator-5554"
PKG = "com.luxray.waer"
ACTIVITY = f"{PKG}/.MainActivity"

# Coordenadas medidas sobre el emulador Wear OS (454 x 454 px).
# El desplazamiento de la pantalla de inicio requiere un gesto LENTO (800 ms):
# con 400 ms el ListView no llega a Interpretar el arrastre.
C = {
    "scroll_home": (227, 380, 227, 120, 800),   # baja hasta las sugerencias
    "chip_ley": (102, 287),                      # chip "ley de ohm"
    "chip_cortocircuito": (238, 287),            # chip "cortocircuito"
    "atras": (82, 65),                           # flecha "volver"
    "resultado_1": (227, 185),                   # primer tile de resultado
}


def adb(args, timeout=180):
    p = subprocess.run(["adb", "-s", SERIAL] + args, capture_output=True,
                       text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (p.stdout or "") + (p.stderr or "")


def shell(cmd, timeout=120):
    return adb(["shell", cmd], timeout=timeout)


def tap(x, y, settle=0.6):
    shell(f"input tap {x} {y}")
    time.sleep(settle)


def swipe(x1, y1, x2, y2, ms=800, settle=0.6):
    shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    time.sleep(settle)


def scroll_home():
    """Coloca la pantalla de inicio en una posicion DETERMINISTA.

    El ListView conserva el desplazamiento al volver de la pagina de
    resultados, de modo que un unico gesto no basta: primero se sube al
    principio y despues se baja hasta las sugerencias.
    """
    swipe(227, 150, 227, 400, 700, settle=0.5)   # ir al principio del ListView
    swipe(*C["scroll_home"], settle=1.0)           # bajar a las sugerencias


# ── Métricas ──────────────────────────────────────────────────────────────
def pid_app():
    out = shell("pidof " + PKG).strip()
    return out.split()[0] if out else None


def leer_ram():
    out = shell(f"dumpsys meminfo {PKG}")
    m = {}
    for clave, pat in (
        ("total_pss_kb", r"TOTAL PSS:\s+(\d+)"),
        ("total_rss_kb", r"TOTAL RSS:\s+(\d+)"),
        ("java_heap_kb", r"Java Heap:\s+(\d+)"),
        ("native_heap_kb", r"Native Heap:\s+(\d+)"),
    ):
        mm = re.search(pat, out)
        m[clave] = int(mm.group(1)) if mm else None
    return m


def cpu_lote(n, intervalo=1):
    """Ejecuta `top` en modo batch: n muestras con `intervalo` segundos entre ellas.

    Con `-o PID,%CPU,RES` la columna ARGS no se imprime, de modo que hay que
    filtrar por PID y no por el nombre del paquete.
    """
    p = pid_app()
    if not p:
        return []
    out = shell(f"top -b -n {n} -d {intervalo} -p {p} -o PID,%CPU,RES", timeout=n * intervalo + 60)
    muestras = []
    for linea in out.splitlines():
        partes = linea.split()
        if len(partes) < 3 or not partes[0].isdigit() or partes[0] != p:
            continue
        try:
            cpu = float(partes[1])
            res = partes[2]
            rss = int(float(res.replace("M", "")) * 1024) if "M" in res else int(res.replace("K", ""))
            muestras.append({"cpu_pct": cpu, "rss_kb": rss})
        except (ValueError, IndexError):
            continue
    return muestras


def frames_finales():
    out = shell(f"dumpsys gfxinfo {PKG}")
    m = {}
    for clave, pat in (
        ("total_frames", r"Total frames rendered:\s+(\d+)"),
        ("janky_frames", r"Janky frames:\s+(\d+)"),
        ("p50_ms", r"50th percentile:\s+(\d+)"),
        ("p90_ms", r"90th percentile:\s+(\d+)"),
        ("p95_ms", r"95th percentile:\s+(\d+)"),
        ("p99_ms", r"99th percentile:\s+(\d+)"),
    ):
        mm = re.search(pat, out)
        m[clave] = int(mm.group(1)) if mm else None
    if m.get("total_frames"):
        m["pct_jank"] = round(100.0 * (m.get("janky_frames") or 0) / m["total_frames"], 2)
    return m


class MedidorRAM(threading.Thread):
    """Muestrea la RAM del proceso durante el escenario."""

    def __init__(self, intervalo=0.5):
        super().__init__(daemon=True)
        self.intervalo = intervalo
        self.pss = []
        self.rss = []
        self.java = []
        self.native = []
        self.stop_flag = threading.Event()

    def run(self):
        while not self.stop_flag.is_set():
            r = leer_ram()
            if r.get("total_pss_kb"):
                self.pss.append(r["total_pss_kb"])
                self.rss.append(r.get("total_rss_kb") or 0)
                self.java.append(r.get("java_heap_kb") or 0)
                self.native.append(r.get("native_heap_kb") or 0)
            time.sleep(self.intervalo)

    def stop(self):
        self.stop_flag.set()
        self.join(timeout=15)

    def resumen(self):
        def mb(v):
            return round(max(v) / 1024, 2) if v else None
        return {
            "muestras_ram": len(self.pss),
            "ram_pss_inicial_mb": round(self.pss[0] / 1024, 2) if self.pss else None,
            "ram_pss_max_mb": mb(self.pss),
            "ram_pss_final_mb": round(self.pss[-1] / 1024, 2) if self.pss else None,
            "ram_rss_max_mb": mb(self.rss),
            "java_heap_max_mb": mb(self.java),
            "native_heap_max_mb": mb(self.native),
        }


class MedidorCPU(threading.Thread):
    """Muestrea la CPU del proceso DURANTE el escenario.

    `adb shell top -b -n N -d 1 -p <pid>` se lanza en paralelo al escenario y
    su salida se recoge al terminar. Medir despues (con la app ya en reposo)
    daria 0 % y no seria representativo.
    """

    def __init__(self, n=120, intervalo=1):
        super().__init__(daemon=True)
        self.n = n
        self.intervalo = intervalo
        self.proc = None
        self.muestras = []

    def run(self):
        p = pid_app()
        if not p:
            return
        self.proc = subprocess.Popen(
            ["adb", "-s", SERIAL, "shell",
             f"top -b -n {self.n} -d {self.intervalo} -p {p} -o PID,%CPU,RES"],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding="utf-8", errors="replace")
        salida, _ = self.proc.communicate()
        for linea in (salida or "").splitlines():
            partes = linea.split()
            if len(partes) < 3 or not partes[0].isdigit():
                continue
            try:
                cpu = float(partes[1])
                res = partes[2]
                rss = int(float(res.replace("M", "")) * 1024) if "M" in res else int(res.replace("K", ""))
                self.muestras.append({"cpu_pct": cpu, "rss_kb": rss})
            except (ValueError, IndexError):
                continue

    def stop(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.proc.kill()
            except Exception:  # noqa: BLE001
                pass
        self.join(timeout=15)


def cpu_jiffies():
    """Tiempo de CPU ACUMULADO del proceso, en jiffies (clock ticks).

    Se lee /proc/<pid>/stat: campos 14 (utime) y 15 (stime). Es una metrica
    exacta y acumulativa, a diferencia del % instantaneo de `top`, que para
    una app Flutter casi siempre ociosa da 0 %.
    """
    p = pid_app()
    if not p:
        return None
    out = shell(f"cat /proc/{p}/stat")
    # El campo 2 (comm) puede contener espacios: se parte por el ultimo ')'
    i = out.rfind(")")
    campos = out[i + 2:].split()
    try:
        utime = int(campos[11])   # campo 14 global
        stime = int(campos[12])   # campo 15 global
        return utime + stime
    except (IndexError, ValueError):
        return None


def rss_hwm_kb():
    """VmHWM: pico de memoria residente del proceso (RSS)."""
    p = pid_app()
    if not p:
        return None
    out = shell(f"cat /proc/{p}/status")
    m = re.search(r"VmHWM:\s+(\d+) kB", out)
    return int(m.group(1)) if m else None


CLK_TCK = os.sysconf("SC_CLK_TCK") if hasattr(os, "sysconf") else 100


def medir_cpu_por_delta(jiffies_antes, segundos):
    """Convierte el delta de jiffies en ms de CPU y en % de un nucleo."""
    if jiffies_antes is None:
        return None, None
    despues = cpu_jiffies()
    if despues is None:
        return None, None
    delta = despues - jiffies_antes
    cpu_ms = delta * 1000.0 / CLK_TCK
    pct = (cpu_ms / 1000.0 / segundos * 100.0) if segundos > 0 else 0.0
    return round(cpu_ms, 1), round(pct, 2)


def cerrar_teclado():
    """Cierra el teclado por software si está abierto.

    Sin esto, un gesto de scroll puede interpretarse como escritura sobre el
    teclado (el texto observado fue "StStSt...") y las coordenadas de las
    sugerencias dejan de coincidir.
    """
    shell("input keyevent 111")
    time.sleep(0.4)


def ir_a_inicio():
    """Deja la app en un estado conocido: pantalla de inicio, sin teclado."""
    cerrar_teclado()
    for _ in range(2):          # detalle -> resultados -> inicio
        tap(*C["atras"], settle=1.0)
    cerrar_teclado()


def peticiones_relay():
    """Número de peticiones HTTP que ha recibido el relay de diagnóstico."""
    f = r"C:\DDI\luxray\practica-12\evidencias\relay_estado.json"
    try:
        with open(f, "r", encoding="utf-8") as fh:
            return json.load(fh).get("peticiones", 0)
    except Exception:  # noqa: BLE001
        return -1


def buscar_en_app(chip, verificado=False, intentos_max=3):
    """Ejecuta una búsqueda y devuelve el nº de peticiones HTTP observadas.

    `verificado=True` exige que la búsqueda llegue al backend (reintenta si no
    es así): se usa para la búsqueda de control que valida el arnés.
    `verificado=False` hace UN SOLO intento y cuenta: es el modo de medición,
    porque la versión optimizada sirve muchas búsquedas desde la CACHÉ (y por
    tanto no generan petición), y un bucle de reintentos falsearía la
    medición del tiempo.
    """
    for intento in range(intentos_max if verificado else 1):
        antes = peticiones_relay()
        ir_a_inicio()
        scroll_home()
        tap(*chip, settle=2.4)
        time.sleep(1.0)
        despues = peticiones_relay()
        nuevas = max(0, despues - antes) if (antes >= 0 and despues >= 0) else 0
        if verificado and nuevas < 1:
            continue
        return nuevas
    return 0


def validar_arnes():
    """Búsqueda de control: DEBE llegar al backend.

    Si esta búsqueda no produce una petición, el arnés no es fiable (el
    gesto no landed sobre el chip) y todas las mediciones serían inválidas.
    """
    global PETICIONES_VALIDACION
    if buscar_en_app(C["chip_ley"], verificado=True) < 1:
        raise RuntimeError(
            "El arnés de profiling no es fiable: la búsqueda de control no "
            "llegó al backend. Se aborta la medición.")
    PETICIONES_VALIDACION = 1
    print("  arnés validado: la búsqueda de control llegó al backend")


# ── Escenarios ────────────────────────────────────────────────────────────
def esc_reposo():
    time.sleep(8)


def esc_consulta_repetida(n=10):
    """10 busquedas IDENTICAS: mide el coste de repetir la misma consulta.

    En la versión optimizada, la 2ª..10ª se sirven de la CACHÉ: por eso se
    cuenta el número de peticiones HTTP realmente emitidas.
    """
    nuevas = 0
    for _ in range(n):
        nuevas += buscar_en_app(C["chip_ley"])
        ir_a_inicio()
    return nuevas


def esc_navegacion(n=20):
    """20 transiciones inicio <-> resultados alternando dos consultas."""
    nuevas = 0
    for i in range(n):
        chip = C["chip_ley"] if i % 2 == 0 else C["chip_cortocircuito"]
        nuevas += buscar_en_app(chip)
        ir_a_inicio()
    return nuevas


def esc_procesamiento(n=8):
    """Busquedas que parsean N resultados y abren el detalle (render pesado)."""
    nuevas = 0
    for _ in range(n):
        nuevas += buscar_en_app(C["chip_ley"])
        tap(*C["resultado_1"], settle=1.8)   # abre el detalle (pagina 2)
        tap(*C["atras"], settle=1.4)          # vuelve a resultados
        ir_a_inicio()                          # vuelve al inicio
    return nuevas


ESCENARIOS = {
    "S0_reposo": (esc_reposo, "Proceso en reposo, sin interaccion"),
    "S1_consulta_repetida": (esc_consulta_repetida, "10 busquedas identicas"),
    "S2_navegacion": (esc_navegacion, "20 transiciones inicio <-> resultados"),
    "S3_procesamiento": (esc_procesamiento, "8 busquedas con parseo y render del detalle"),
}


def preparar_app(apk=None):
    if apk and os.path.exists(apk):
        print(f"  instalando {os.path.basename(apk)} ...")
        adb(["install", "-r", apk], timeout=300)
    shell(f"am force-stop {PKG}")
    time.sleep(1.5)
    shell(f"am start -n {ACTIVITY}")
    time.sleep(16)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", default=None)
    ap.add_argument("--etiqueta", default="antes")
    ap.add_argument("--out", default=r"C:\DDI\luxray\practica-12\evidencias\perfil_movil.json")
    ap.add_argument("--solo", nargs="*", default=None)
    args = ap.parse_args()

    print("=" * 78)
    print(f"PRACTICA 12 - FASE 2 - PROFILING DE LA APLICACION MOVIL [{args.etiqueta.upper()}]")
    print("=" * 78)
    print("  dispositivo : emulador Wear OS API 36 (454 x 454)")
    print(f"  paquete     : {PKG}\n")

    print("[preparacion] arrancando la aplicacion ...")
    preparar_app(args.apk)
    pid = pid_app()
    print(f"  pid = {pid}")
    if not pid:
        print("  ERROR: la app no arranco")
        return 2

    print("[validacion] comprobando que el arnes de interaccion es fiable ...")
    try:
        validar_arnes()
    except RuntimeError as e:
        print(f"  ERROR: {e}")
        return 3

    res_global = {"etiqueta": args.etiqueta, "fecha": time.strftime("%Y-%m-%d %H:%M:%S"),
                  "dispositivo": "emulador Wear OS API 36 (454x454)", "pid": pid,
                  "escenarios": {}}

    for nombre, (fn, desc) in ESCENARIOS.items():
        if args.solo and nombre not in args.solo:
            continue
        print(f"\n[{nombre}] {desc}")
        shell(f"dumpsys gfxinfo {PKG} reset")
        ram_antes = leer_ram()
        hwm_antes = rss_hwm_kb()
        jiffies_antes = cpu_jiffies()
        peticiones_antes = peticiones_relay()
        med = MedidorRAM()
        med.start()
        t0 = time.perf_counter()
        try:
            peticiones_escenario = fn() or 0
        finally:
            med.stop()
        dur = time.perf_counter() - t0
        peticiones_despues = peticiones_relay()
        cpu_ms, cpu_pct = medir_cpu_por_delta(jiffies_antes, dur)
        # Muestreo de % instantaneo con `top` como referencia secundaria
        cpus = cpu_lote(n=5, intervalo=1)
        ram_despues = leer_ram()
        hwm_despues = rss_hwm_kb()
        frames = frames_finales()

        vals = [c["cpu_pct"] for c in cpus]
        r = med.resumen()
        r.update({
            "descripcion": desc,
            "duracion_s": round(dur, 2),
            "peticiones_http": peticiones_escenario,
            "peticiones_http_acumuladas": (peticiones_despues - peticiones_antes)
            if (peticiones_despues >= 0 and peticiones_antes >= 0) else None,
            "cpu_tiempo_ms": cpu_ms,
            "cpu_pct_un_nucleo": cpu_pct,
            "cpu_top_promedio_pct": round(statistics.mean(vals), 2) if vals else None,
            "cpu_top_max_pct": round(max(vals), 2) if vals else None,
            "cpu_muestras_top": len(vals),
            "rss_hwm_mb": round(hwm_despues / 1024, 2) if hwm_despues else None,
            "rss_hwm_inicial_mb": round(hwm_antes / 1024, 2) if hwm_antes else None,
            "ram_pss_antes_mb": round(ram_antes["total_pss_kb"] / 1024, 2) if ram_antes.get("total_pss_kb") else None,
            "ram_pss_despues_mb": round(ram_despues["total_pss_kb"] / 1024, 2) if ram_despues.get("total_pss_kb") else None,
            "frames": frames,
        })
        res_global["escenarios"][nombre] = r
        print(f"    duracion={dur:.1f}s  CPU={cpu_ms} ms ({cpu_pct}% de un nucleo)  "
              f"top prom={r['cpu_top_promedio_pct']}% max={r['cpu_top_max_pct']}%  "
              f"peticiones HTTP={peticiones_escenario}")
        print(f"    RAM PSS inicial={r['ram_pss_inicial_mb']} MB  max={r['ram_pss_max_mb']} MB  "
              f"final={r['ram_pss_final_mb']} MB  (RSS pico {r['rss_hwm_mb']} MB)")
        if frames.get("total_frames"):
            print(f"    frames={frames['total_frames']} janky={frames['janky_frames']} "
                  f"({frames.get('pct_jank')}%) p95={frames.get('p95_ms')} ms")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(res_global, fh, indent=2, ensure_ascii=False)
    print(f"\nInforme escrito en {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
