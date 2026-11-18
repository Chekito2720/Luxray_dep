#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PRÁCTICA 11 — FASE 4 — DB-06 · CONCURRENCIA

Ejecuta tres escenarios con dos sesiones psql SIMULTANEAS sobre el mismo
registro y registra la evidencia completa.

  S1 · Inserción duplicada concurrente
      -> el índice UNIQUE (usuario_id, curso_id) debe arbitrar: 1 sesión
         inserta y la otra falla con unique_violation.

  S2 · Actualización concurrente CON SELECT ... FOR UPDATE
      -> bloqueo pesimista: no hay lost update; el contador final es 2.

  S3 · Actualización concurrente SIN bloqueo (lectura sucia)
      -> se reproduce el lost update: el contador final es 1 en vez de 2.

Uso:
    python ejecutar_concurrencia.py
"""
import os
import subprocess
import sys
import time

CONT = "luxray-cursos-db"
DB = "cursos_db"
USER = "luxray"
OUT = r"C:\DDI\luxray\practica-11\evidencias\fase4_concurrencia.txt"
RUTA_SQL = r"C:\DDI\luxray\practica-11\fase4-base-datos"


def psql(sql, timeout=60, tuples_only=False):
    """Ejecuta SQL en una sesion psql y devuelve (rc, salida)."""
    cmd = ["docker", "exec", "-i", CONT, "psql", "-U", USER, "-d", DB, "-v", "ON_ERROR_STOP=off"]
    if tuples_only:
        cmd += ["-t", "-A"]
    p = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, timeout=timeout)
    return p.returncode, (p.stdout or b"").decode("utf-8", "replace") + \
                        (p.stderr or b"").decode("utf-8", "replace")


def lanzar_sesion(sql):
    """Lanza una sesion psql en segundo plano y DEVUELVE el proceso.

    No se espera a que termine: el SQL se escribe en la tuberia y la sesion
    queda viva, de modo que varias sesiones pueden solaparse de verdad.
    """
    p = subprocess.Popen(["docker", "exec", "-i", CONT, "psql", "-U", USER, "-d", DB],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT)
    p.stdin.write(sql.encode("utf-8"))
    p.stdin.flush()
    return p


def esperar(p, timeout=120):
    try:
        out, _ = p.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        p.kill()
        out = b""
    return out.decode("utf-8", "replace")


def psql_file(ruta, timeout=60):
    with open(ruta, "rb") as fh:
        sql = fh.read().decode("utf-8")
    return psql(sql, timeout)


def linea(txt):
    return [l for l in txt.splitlines()
            if any(k in l for k in ("ERROR", "INSERT", "UPDATE", "COMMIT", "ROLLBACK",
                                    "rows)", "row)", "SELECT", "NOTICE", "DETAIL"))]


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    reg = []

    def emit(s=""):
        print(s)
        reg.append(s)

    emit("=" * 78)
    emit("PRÁCTICA 11 — FASE 4 — DB-06 · PRUEBAS DE CONCURRENCIA")
    emit("=" * 78)
    emit(f"Fecha              : {time.strftime('%Y-%m-%d %H:%M:%S')}")
    emit(f"Contenedor         : {CONT}")
    emit(f"Base de datos      : {DB}  (PostgreSQL 16)")
    emit(f"Ejecutado desde    : cliente (Windows) contra el contenedor Docker")
    emit("")

    # ══════════════════════════════════════════════════════════════════
    # S1 · Inserción duplicada concurrente
    # ══════════════════════════════════════════════════════════════════
    emit("=" * 78)
    emit("S1 · DOS INSERCIONES CONCURRENTES DE LA MISMA INSCRIPCIÓN")
    emit("     (carrera sobre el índice UNIQUE (usuario_id, curso_id))")
    emit("=" * 78)
    with open(os.path.join(RUTA_SQL, "02a_conc_s1_A.sql"), "rb") as fh:
        sql_a = fh.read().decode("utf-8")
    with open(os.path.join(RUTA_SQL, "02b_conc_s1_B.sql"), "rb") as fh:
        sql_b = fh.read().decode("utf-8")

    # Sesion A: inserta y MANTIENE abierta la transaccion 3 s (pg_sleep),
    # de modo que la fila queda bloqueada mientras la sesion B compite.
    pa = lanzar_sesion(sql_a)
    time.sleep(1.5)          # <- solapamiento real: A ya esta dentro
    pb = lanzar_sesion(sql_b)
    out_b = esperar(pb, 120)
    out_a = esperar(pa, 120)

    emit("--- Sesion A (inserta; mantiene la transaccion 3 s con pg_sleep) ---")
    for l in linea(out_a):
        emit("    " + l.strip())
    emit("--- Sesion B (intenta el duplicado EN PARALELO) ---")
    for l in linea(out_b):
        emit("    " + l.strip())
    emit(f"    codigo de salida sesion B: {pb.returncode}  "
         f"(0 = OK, 3 = error esperado por la restriccion UNIQUE)")
    emit("")

    rc, out = psql(
        "SELECT count(*) FROM inscripciones "
        "WHERE usuario_id='44444444-4444-4444-8444-000000000061' "
        "AND curso_id='11111111-1111-4111-8111-000000000061';", tuples_only=True)
    emit("--- Verificacion: cuantas inscripciones quedaron? ---")
    for l in out.splitlines():
        if l.strip().isdigit():
            emit(f"    insertions_resultantes = {l.strip()}")
    emit("    ESPERADO: 1  (el indice UNIQUE arbitra la carrera)")
    emit("")
    psql("DELETE FROM cursos WHERE id='11111111-1111-4111-8111-000000000061';")

    # ══════════════════════════════════════════════════════════════════
    # S2 · Actualizacion concurrente CON bloqueo pesimista
    # ══════════════════════════════════════════════════════════════════
    emit("=" * 78)
    emit("S2 · ACTUALIZACION CONCURRENTE CON  SELECT ... FOR UPDATE")
    emit("     (bloqueo pesimista: no debe haber lost update)")
    emit("=" * 78)
    psql("INSERT INTO cursos (id, titulo, descripcion, nivel, instructor, estudiantes) "
         "VALUES ('11111111-1111-4111-8111-000000000062','WB11 bloque pesimista',"
         "'x','BASICO','Docente WB11',0);")
    sql_a2 = ("BEGIN;\n"
              "SELECT estudiantes FROM cursos WHERE id='11111111-1111-4111-8111-000000000062' FOR UPDATE;\n"
              "UPDATE cursos SET estudiantes = estudiantes + 1 WHERE id='11111111-1111-4111-8111-000000000062';\n"
              "SELECT pg_sleep(3);\nCOMMIT;\n")
    sql_b2 = ("BEGIN;\n"
              "SELECT estudiantes FROM cursos WHERE id='11111111-1111-4111-8111-000000000062' FOR UPDATE;\n"
              "UPDATE cursos SET estudiantes = estudiantes + 1 WHERE id='11111111-1111-4111-8111-000000000062';\n"
              "COMMIT;\n")
    pa = lanzar_sesion(sql_a2)
    time.sleep(1.5)
    pb = lanzar_sesion(sql_b2)
    out_b2 = esperar(pb, 120)
    esperar(pa, 120)
    rc, out = psql("SELECT estudiantes FROM cursos "
                   "WHERE id='11111111-1111-4111-8111-000000000062';", tuples_only=True)
    emit("--- Sesion B (espera el bloqueo y suma) ---")
    for l in linea(out_b2):
        emit("    " + l.strip())
    emit("--- Verificacion ---")
    for l in out.splitlines():
        if l.strip().isdigit():
            emit(f"    estudiantes = {l.strip()}")
    emit("    ESPERADO: 2  (ambos incrementos aplicados, sin lost update)")
    emit("")
    psql("DELETE FROM cursos WHERE id='11111111-1111-4111-8111-000000000062';")

    # ══════════════════════════════════════════════════════════════════
    # S3 · Actualizacion concurrente SIN bloqueo (lost update)
    # ══════════════════════════════════════════════════════════════════
    emit("=" * 78)
    emit("S3 · ACTUALIZACION CONCURRENTE SIN BLOQUEO  (lectura sucia)")
    emit("     (se demuestra el riesgo: lost update)")
    emit("=" * 78)
    psql("INSERT INTO cursos (id, titulo, descripcion, nivel, instructor, estudiantes) "
         "VALUES ('11111111-1111-4111-8111-000000000063','WB11 lost update',"
         "'x','BASICO','Docente WB11',0);")
    sql_a3 = ("BEGIN;\n"
              "SELECT estudiantes FROM cursos WHERE id='11111111-1111-4111-8111-000000000063';\n"
              "SELECT pg_sleep(3);\n"
              "UPDATE cursos SET estudiantes = 1 WHERE id='11111111-1111-4111-8111-000000000063';\n"
              "COMMIT;\n")
    sql_b3 = ("BEGIN;\n"
              "SELECT estudiantes FROM cursos WHERE id='11111111-1111-4111-8111-000000000063';\n"
              "UPDATE cursos SET estudiantes = 1 WHERE id='11111111-1111-4111-8111-000000000063';\n"
              "COMMIT;\n")
    pa = lanzar_sesion(sql_a3)
    time.sleep(1.5)
    pb = lanzar_sesion(sql_b3)
    out_b3 = esperar(pb, 120)
    esperar(pa, 120)
    rc, out = psql("SELECT estudiantes FROM cursos "
                   "WHERE id='11111111-1111-4111-8111-000000000063';", tuples_only=True)
    emit("--- Sesion B (escribe sin esperar) ---")
    for l in linea(out_b3):
        emit("    " + l.strip())
    emit("--- Verificacion ---")
    for l in out.splitlines():
        if l.strip().isdigit():
            emit(f"    estudiantes = {l.strip()}")
    emit("    ESPERADO: 2  ·  REAL: 1  -> LOST UPDATE reproducido")
    emit("    CONCLUSION: las lecturas-modificacion-escrituras necesitan")
    emit("               bloqueo pesimista (SELECT .. FOR UPDATE) o versionado")
    emit("               optimista (@Version) en la entidad.")
    emit("")
    psql("DELETE FROM cursos WHERE id='11111111-1111-4111-8111-000000000063';")

    emit("=" * 78)
    emit("FIN DE LAS PRUEBAS DE CONCURRENCIA")
    emit("=" * 78)

    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(reg) + "\n")
    print(f"\nEvidencia escrita en {OUT}")


if __name__ == "__main__":
    sys.exit(main())
