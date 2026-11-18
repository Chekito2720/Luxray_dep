/**
 * ===========================================================================
 * PRÁCTICA 12 — FASE 3 — PRUEBAS DE ESTRÉS DEL SITIO WEB Y LA API
 *
 * Herramienta : k6 v0.57.0
 * Objetivo    : evaluar la ESTABILIDAD y localizar el PUNTO DE SATURACIÓN del
 *               API Gateway de LuxRay bajo carga creciente de usuarios.
 *
 * Endpoints evaluados (uno de LECTURA y uno de ESCRITURA):
 *   LECTURA  : GET  /api/cursos          (catalogo — cursos-service)
 *   ESCRITURA: POST /api/auth/registro   (alta de usuario — auth-service)
 *
 * DISEÑO
 * ------
 * Cada nivel de concurrencia se ejecuta como una invocación AISLADA de k6
 * (controlada por `ejecutar_estres.py`), de modo que la curva de saturación
 * no se contamine con el tráfico de otros niveles.
 *
 * Cada petición lleva la etiqueta `nivel` con el número de usuarios
 * concurrentes, lo que permite comparar medias, P90, P95 y máximos etapa a
 * etapa y responder a la pregunta técnica de la práctica:
 *
 *     ¿el aumento de usuarios provoca un incremento PROPORCIONAL del tiempo
 *      de respuesta, o existe un PUNTO DE SATURACIÓN?
 *
 * El escenario de escritura usa correos ÚNICOS por iteración: si no, la
 * restricción UNIQUE de `usuarios.email` falsearía la medición.
 *
 * Variables de entorno:
 *   BASE_URL     -> http://localhost:8080/api
 *   VUS          -> usuarios concurrentes de esta etapa
 *   ITERACIONES  -> iteraciones por usuario
 *   MODO         -> lectura | escritura
 *   NIVEL        -> etiqueta numérica del nivel (para las sub-métricas)
 *
 * Ejecución (la hace el orquestador):
 *   k6 run --summary-export ../evidencias/...json estres_api.js
 * ===========================================================================
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080/api') + '/';
const VUS = parseInt(__ENV.VUS || '1', 10);
const ITERACIONES = parseInt(__ENV.ITERACIONES || '20', 10);
const MODO = __ENV.MODO || 'lectura';
const NIVEL = __ENV.NIVEL || String(VUS);

const lat = new Trend('wb12_latencia_ms', true);
const ok = new Rate('wb12_ok');
const fallos = new Counter('wb12_fallos');
const bytesResp = new Trend('wb12_bytes_respuesta', true);
const codigos = new Counter('wb12_codigo_http');

export const options = {
  scenarios: {
    carga: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERACIONES,
      exec: MODO === 'escritura' ? 'escritura' : 'lectura',
      tags: { nivel: NIVEL, modo: MODO },
    },
  },
  thresholds: {
    // Umbrales informativos: se miden, no bloquean el informe.
    http_req_failed: ['rate<0.20'],
    checks: ['rate>0.80'],
  },
};

export function lectura() {
  const res = http.get(BASE_URL + 'cursos', {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'GET /api/cursos' },
  });
  lat.add(res.timings.duration);
  bytesResp.add(res.body ? res.body.length : 0);
  codigos.add(String(res.status));
  const good = check(res, {
    'lectura: HTTP 200': (r) => r.status === 200,
    'lectura: data[]': (r) => {
      try {
        return Array.isArray(r.json('data'));
      } catch (e) {
        return false;
      }
    },
  });
  ok.add(good);
  if (!good) fallos.add(1);
  sleep(0.05);
}

export function escritura() {
  const unico = `wb12_${__VU}_${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
  const payload = JSON.stringify({
    nombre: 'Carga',
    apellido: 'Practica12',
    email: `${unico}@luxray.test`,
    password: 'ClaveCarga123',
    acceptTerms: true,
  });
  const res = http.post(BASE_URL + 'auth/registro', payload, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    tags: { endpoint: 'POST /api/auth/registro' },
  });
  lat.add(res.timings.duration);
  bytesResp.add(res.body ? res.body.length : 0);
  codigos.add(String(res.status));
  const good = check(res, {
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
  ok.add(good);
  if (!good) fallos.add(1);
  sleep(0.05);
}

function n(v, dec) {
  return (v === undefined || v === null || isNaN(v)) ? '-' : Number(v).toFixed(dec);
}

export function handleSummary(data) {
  const m = data.metrics;
  const d = m.http_req_duration ? m.http_req_duration.values : {};
  const f = m.http_req_failed ? m.http_req_failed.values : {};
  const c = m.checks ? m.checks.values : {};
  const r = m.http_reqs ? m.http_reqs.values : {};
  const out = [];

  out.push(`# Fase 3 — Escenario de estres: ${MODO} con ${VUS} usuarios concurrentes\n`);
  out.push(`- **Iteraciones por usuario**: ${ITERACIONES}`);
  out.push(`- **Peticiones totales**: ${r.count || 0}`);
  out.push(`- **RPS (tasa de peticiones)**: ${n(r.rate, 2)} req/s`);
  out.push(`- **Latencia media**: ${n(d.avg, 2)} ms`);
  out.push(`- **Latencia P90**: ${n(d['p(90)'], 2)} ms`);
  out.push(`- **Latencia P95**: ${n(d['p(95)'], 2)} ms`);
  out.push(`- **Latencia P99**: ${n(d['p(99)'], 2)} ms`);
  out.push(`- **Latencia máxima**: ${n(d.max, 2)} ms`);
  out.push(`- **Latencia mínima**: ${n(d.min, 2)} ms`);
  out.push(`- **Tasa de fallo HTTP**: ${n(f.rate * 100, 2)} %`);
  out.push(`- **Checks correctos**: ${n(c.rate * 100, 2)} %`);
  out.push(`- **Datos recibidos**: ${n(m.data_received ? m.data_received.values.count / 1024 : 0, 2)} KB`);
  out.push(`- **Datos enviados**: ${n(m.data_sent ? m.data_sent.values.count / 1024 : 0, 2)} KB`);

  return {
    stdout: out.join('\n'),
  };
}
