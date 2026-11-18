/**
 * ===========================================================================
 * PRÁCTICA 11 — FASE 5 (NET-07) — PRUEBA DE CARGA CONTROLADA
 *
 * Herramienta : k6 v0.57.0 (https://k6.io) — carga controlada sobre HTTP
 * Objetivo    : medir el comportamiento del API Gateway de LuxRay bajo carga,
 *                con 1, 5 y 10 usuarios concurrentes, sobre un endpoint de
 *                LECTURA y otro de ESCRITURA.
 *
 * Endpoints evaluados:
 *   LECTURA  : GET  /api/cursos          (catalogo publico — cursos-service)
 *   ESCRITURA: POST /api/auth/registro   (alta de usuario  — auth-service)
 *
 * Cada ESCENARIO se ejecuta de forma AISLADA (una invocacion de k6 por
 * nivel de concurrencia) para que la medicion de "1 usuario concurrente" no
 * se contaminatione con el trafico de los escenarios de 5 y 10 usuarios.
 * El orquestador es `ejecutar_carga.ps1` / `ejecutar_carga.py`.
 *
 * Variables de entorno:
 *   BASE_URL      -> por defecto http://[::1]:8080/api
 *                    (en esta maquina el puerto IPv4 127.0.0.1:8080 lo ocupa
 *                     otro servidor local; ver hallazgo DEF-06)
 *   VUS           -> 1 | 5 | 10          (usuarios concurrentes)
 *   ITERACIONES   -> 20 por usuario     (minimo exigido por la practica)
 *   ESCENARIO     -> etiqueta del escenario para el informe
 *
 * Ejecucion aislada:
 *   k6 run --summary-export ../evidencias/fase5_carga_<etiqueta>.json carga_k6.js
 * ===========================================================================
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://[::1]:8080/api') + '/';
const VUS = parseInt(__ENV.VUS || '1', 10);
const ITERACIONES = parseInt(__ENV.ITERACIONES || '20', 10);
const ESCENARIO = __ENV.ESCENARIO || ('carga_' + VUS + '_usuarios');

const latencia = new Trend('wb11_latencia_ms', true);
const tasaError = new Rate('wb11_tasa_error');
const tasaOk = new Rate('wb11_tasa_ok');
const bytesRecibidos = new Counter('wb11_bytes_recibidos');

export function lectura() {
  const res = http.get(BASE_URL + 'cursos', {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'GET /api/cursos', escenario: ESCENARIO },
  });
  latencia.add(res.timings.duration);
  bytesRecibidos.add(res.body ? res.body.length : 0);
  const ok = check(res, {
    'lectura: HTTP 200': (r) => r.status === 200,
    'lectura: devuelve data[]': (r) => {
      try {
        return Array.isArray(r.json('data'));
      } catch (e) {
        return false;
      }
    },
  });
  tasaOk.add(ok);
  tasaError.add(!ok);
  sleep(0.2);
}

export function escritura() {
  const unico = `wb11_k6_${__VU}_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  const payload = JSON.stringify({
    nombre: 'Carga',
    apellido: 'Controlada',
    email: `${unico}@luxray.test`,
    password: 'ClaveCarga123',
    acceptTerms: true,
  });
  const res = http.post(BASE_URL + 'auth/registro', payload, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    tags: { endpoint: 'POST /api/auth/registro', escenario: ESCENARIO },
  });
  latencia.add(res.timings.duration);
  bytesRecibidos.add(res.body ? res.body.length : 0);
  const ok = check(res, {
    'escritura: HTTP 200': (r) => r.status === 200,
    'escritura: devuelve token': (r) => {
      try {
        const t = r.json('data.token');
        return typeof t === 'string' && t.length > 20;
      } catch (e) {
        return false;
      }
    },
  });
  tasaOk.add(ok);
  tasaError.add(!ok);
  sleep(0.2);
}

export const options = {
  scenarios: {
    carga: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERACIONES,
      exec: __ENV.MODO === 'escritura' ? 'escritura' : 'lectura',
      tags: { escenario: ESCENARIO },
    },
  },
  thresholds: {
    // Criterios de referencia de la practica: P95 < 500 ms, fallos < 5 %.
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
};

function n(v, dec) {
  return (v === undefined || v === null || isNaN(v)) ? '-' : Number(v).toFixed(dec);
}

export function handleSummary(data) {
  const m = data.metrics;
  const d = m.http_req_duration ? m.http_req_duration.values : {};
  const f = m.http_req_failed ? m.http_req_failed.values : {};
  const c = m.checks ? m.checks.values : {};

  const out = [];
  out.push(`# Fase 5 (NET-07) — Escenario: ${ESCENARIO}\n`);
  out.push(`- **Modo**: ${__ENV.MODO || 'lectura'}`);
  out.push(`- **Usuarios concurrentes (VUs)**: ${VUS}`);
  out.push(`- **Iteraciones por VU**: ${ITERACIONES}`);
  out.push(`- **Peticiones totales**: ${m.http_reqs ? m.http_reqs.values.count : 0}`);
  out.push(`- **Fecha**: ${new Date().toISOString()}\n`);
  out.push('| Indicador | Valor |');
  out.push('|---|---|');
  out.push(`| Latencia media | ${n(d.avg, 2)} ms |`);
  out.push(`| Latencia mediana | ${n(d.med, 2)} ms |`);
  out.push(`| Latencia P90 | ${n(d['p(90)'], 2)} ms |`);
  out.push(`| Latencia P95 | ${n(d['p(95)'], 2)} ms |`);
  out.push(`| Latencia P99 | ${n(d['p(99)'], 2)} ms |`);
  out.push(`| Latencia mínima | ${n(d.min, 2)} ms |`);
  out.push(`| Latencia máxima | ${n(d.max, 2)} ms |`);
  out.push(`| Peticiones fallidas | ${f.passes !== undefined ? f.passes : '-'} |`);
  out.push(`| Tasa de fallo | ${f.rate !== undefined ? n(f.rate * 100, 2) + ' %' : '-'} |`);
  out.push(`| Checks correctos | ${c.rate !== undefined ? n(c.rate * 100, 2) + ' %' : '-'} |`);
  out.push(`| Tasa de peticiones | ${m.http_reqs ? n(m.http_reqs.values.rate, 2) : '-'} req/s |`);
  out.push(`| Datos recibidos | ${m.data_received ? n(m.data_received.values.count / 1024, 2) : '-'} KB |`);
  out.push(`| Datos enviados | ${m.data_sent ? n(m.data_sent.values.count / 1024, 2) : '-'} KB |`);
  out.push('');
  const th = data.root_group ? null : null;
  out.push('### Umbrales evaluados (criterios de referencia de la practica)\n');
  out.push('- `http_req_duration p(95) < 500 ms`');
  out.push('- `http_req_failed rate < 0.05`');
  out.push('- `checks rate > 0.95`');

  return {
    [(__ENV.RUTA_INFORME || ('fase5_carga_' + ESCENARIO + '.md'))]: out.join('\n'),
    stdout: out.join('\n'),
  };
}
