import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/services/luxray_api.dart';

/// ===========================================================================
/// PRÁCTICA 12 — FASE 6 — PRUEBAS DE LA OPTIMIZACIÓN (versión optimizada)
///
/// Verifica cada una de las cinco medidas introducidas en la versión
/// optimizada de `LuxrayApi` (el código original se conserva como evidencia
/// en `practica-12/evidencias/codigo-original/`):
///
///   P12-01 · CACHÉ LRU CON TTL + COALESCENCIA DE PETICIONES EN VUELO
///   P12-02 · CANCELACIÓN LÓGICA (descartar el resultado obsoleto)
///   P12-03 · REINTENTO CON BACKOFF EXPONENCIAL (sólo fallos de transporte)
///   P12-04 · ESTADO EXPLÍCITO (ok / vacío / sinConexión / errorServidor)
///   P12-05 · CABECERAS HTTP CENTRALIZADAS
///
/// Además se comprueba que NO se ha introducido ningún defecto crítico:
///
///   · una búsqueda con resultados NO hace la llamada de respaldo;
///   · una búsqueda sin resultados SÍ la hace (comportamiento original);
///   · un 5xx NO se reintenta (el respaldo legacy es mejor respuesta);
///   · un fallo de red SÍ se reintenta con backoff;
///   · los fallos NO se memorizan en la caché.
void main() {
  // ── Utilidades ─────────────────────────────────────────────────────────
  String cuerpoOk({int total = 0, String query = 'ohm'}) => jsonEncode({
        'data': {
          'query': query,
          'total': total,
          'page': 0,
          'size': 10,
          'cursos': total > 0
              ? [
                  {
                    'id': 'c1',
                    'score': 1.0,
                    'source': {'id': 'c1', 'titulo': 'Curso'},
                  }
                ]
              : [],
          'lecciones': const [],
          'conceptos': const [],
          'faqs': const [],
        },
        'success': true,
      });

  /// Crea una API con la caché desactivada (para observar cada petición).
  LuxrayApi apiMock(http.Client client, {int reintentos = 2}) => LuxrayApi(
        client: client,
        cacheActivo: false,
        reintentosMax: reintentos,
        backoffBase: const Duration(milliseconds: 1),
        timeoutPeticion: const Duration(milliseconds: 500),
        timeoutTotal: const Duration(seconds: 5),
      );

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-01 · Caché LRU con TTL', () {
    test('la segunda búsqueda idéntica NO genera peticiones HTTP', () async {
      var llamadas = 0;
      final api = LuxrayApi(
        client: MockClient((req) async {
          llamadas++;
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      final r1 = await api.buscarDetallado('ohm');
      final r2 = await api.buscarDetallado('ohm');

      expect(llamadas, 1, reason: 'la segunda debe servirse de la caché');
      expect(r1.desdeCache, isFalse);
      expect(r2.desdeCache, isTrue);
      expect(r2.respuesta.total, 1, reason: 'el resultado cacheado es equivalente');
      expect(api.entradasCache, 1);
      api.close();
    });

    test('la caché es insensible a mayúsculas y espacios sobrantes', () async {
      var llamadas = 0;
      final api = LuxrayApi(
        client: MockClient((req) async {
          llamadas++;
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      await api.buscarDetallado('Ohm');
      await api.buscarDetallado('  ohm  ');

      expect(llamadas, 1);
      api.close();
    });

    test('una consulta DISTINTA sí genera una petición', () async {
      var llamadas = 0;
      final api = LuxrayApi(
        client: MockClient((req) async {
          llamadas++;
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      await api.buscarDetallado('ohm');
      await api.buscarDetallado('voltaje');

      expect(llamadas, 2);
      api.close();
    });

    test('la entrada de caché CADUCA pasado el TTL', () async {
      var llamadas = 0;
      final api = LuxrayApi(
        client: MockClient((req) async {
          llamadas++;
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
        cacheTTL: const Duration(milliseconds: 120),
      );

      await api.buscarDetallado('ohm');
      await Future<void>.delayed(const Duration(milliseconds: 200));
      await api.buscarDetallado('ohm');

      expect(llamadas, 2, reason: 'tras el TTL se vuelve a pedir');
      api.close();
    });

    test('la caché se recorta al máximo de entradas (LRU)', () async {
      final api = LuxrayApi(
        client: MockClient((req) async => http.Response(cuerpoOk(total: 1), 200,
            headers: {'content-type': 'application/json'})),
        cacheMaxEntradas: 3,
      );

      for (final q in ['a1', 'a2', 'a3', 'a4']) {
        await api.buscarDetallado(q);
      }

      expect(api.entradasCache, 3, reason: 'nunca supera el máximo');
      api.close();
    });

    test('los FALLOS de red NO se memorizan', () async {
      final api = LuxrayApi(
        client: MockClient((req) async => throw http.ClientException('sin red')),
        reintentosMax: 0,
        backoffBase: const Duration(milliseconds: 1),
      );

      final r = await api.buscarDetallado('ohm');
      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      expect(api.entradasCache, 0,
          reason: 'un fallo no debe quedar cacheado (se repetiría el error)');
      api.close();
    });

    test('limpiarCache() vacía la caché', () async {
      final api = LuxrayApi(
        client: MockClient((req) async => http.Response(cuerpoOk(total: 1), 200,
            headers: {'content-type': 'application/json'})),
      );

      await api.buscarDetallado('ohm');
      expect(api.entradasCache, 1);
      api.limpiarCache();
      expect(api.entradasCache, 0);
      api.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-01 · Coalescencia de peticiones en vuelo', () {
    test('dos búsquedas idénticas SIMULTÁNEAS comparten una petición', () async {
      var llamadas = 0;
      final api = LuxrayApi(
        client: MockClient((req) async {
          llamadas++;
          await Future<void>.delayed(const Duration(milliseconds: 120));
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      final resultados = await Future.wait([
        api.buscarDetallado('ohm'),
        api.buscarDetallado('ohm'),
        api.buscarDetallado('ohm'),
      ]);

      expect(llamadas, 1,
          reason: 'las tres búsquedas concurrentes comparten la petición');
      expect(resultados.every((r) => r.respuesta.total == 1), isTrue);
      api.close();
    });

    test('búsquedas CONCURRENTES de texto distinto NO se coalescen', () async {
      final queries = <String>[];
      final api = LuxrayApi(
        client: MockClient((req) async {
          queries.add(req.url.queryParameters['q'] ?? '');
          await Future<void>.delayed(const Duration(milliseconds: 60));
          return http.Response(cuerpoOk(total: 1), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      await Future.wait([
        api.buscarDetallado('ohm'),
        api.buscarDetallado('voltaje'),
      ]);

      expect(queries.toSet().length, 2);
      api.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-02 · Cancelación lógica del resultado obsoleto', () {
    test('una búsqueda posterior invalida la anterior (no hay fallback extra)',
        () async {
      final rutas = <String>[];
      final api = apiMock(MockClient((req) async {
        rutas.add(req.url.path);
        await Future<void>.delayed(const Duration(milliseconds: 200));
        // Respuesta 200 pero SIN resultados: dispararía el fallback legacy.
        return http.Response(cuerpoOk(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      final primera = api.buscarDetallado('primera');
      await Future<void>.delayed(const Duration(milliseconds: 40));
      final segunda = api.buscarDetallado('segunda');
      await Future.wait([primera, segunda]);

      expect(rutas.where((r) => r.contains('/cursos/buscar')).length, 1,
          reason: 'sólo la búsqueda vigente (la última) gasta el fallback');
      api.close();
    });

    test('la búsqueda vigente sí usa el fallback legacy', () async {
      final rutas = <String>[];
      final api = apiMock(MockClient((req) async {
        rutas.add(req.url.path);
        return http.Response(cuerpoOk(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscarDetallado('ohm');

      expect(rutas, ['/api/search', '/api/cursos/buscar']);
      api.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-03 · Reintento con backoff exponencial', () {
    test('un fallo de TRANSPORTE se reintenta (hasta reintentosMax)', () async {
      final rutas = <String>[];
      final api = apiMock(
          MockClient((req) async {
            rutas.add(req.url.path);
            throw http.ClientException('sin cobertura');
          }),
          reintentos: 2);

      final r = await api.buscarDetallado('ohm');

      expect(rutas.where((x) => x.contains('/search')).length, 3,
          reason: '1 intento + 2 reintentos sobre el endpoint unificado');
      expect(rutas.where((x) => x.contains('/cursos/buscar')).length, 1,
          reason: 'agotados los reintentos se prueba el respaldo legacy');
      expect(r.intentos, 3);
      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      api.close();
    });

    test('un 5xx NO se reintenta: el respaldo legacy es mejor respuesta',
        () async {
      final rutas = <String>[];
      final api = apiMock(MockClient((req) async {
        rutas.add(req.url.path);
        // El endpoint unificado falla; el respaldo responde bien.
        if (req.url.path.endsWith('/search')) {
          return http.Response('{"error":"boom"}', 500);
        }
        return http.Response(cuerpoOk(total: 2), 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscarDetallado('ohm');

      expect(rutas.where((x) => x.contains('/search')).length, 1,
          reason: 'el 5xx no se insiste: se pasa al respaldo');
      expect(rutas, ['/api/search', '/api/cursos/buscar']);
      expect(r.estado, LuxEstadoBusqueda.ok);
      expect(r.respuesta.total, 2);
      api.close();
    });

    test('un 4xx NO se reintenta', () async {
      final rutas = <String>[];
      final api = apiMock(
          MockClient((req) async {
            rutas.add(req.url.path);
            return http.Response('{"error":"bad request"}', 400);
          }),
          reintentos: 2);

      await api.buscarDetallado('ohm');
      expect(rutas.where((x) => x.contains('/search')).length, 1,
          reason: 'un 4xx es definitivo: no se reintenta');
      expect(rutas.length, 2, reason: 'un intento + el respaldo legacy');
      api.close();
    });

    test('el reintento respeta el presupuesto global de tiempo', () async {
      final api = LuxrayApi(
        client: MockClient((req) async {
          await Future<void>.delayed(const Duration(milliseconds: 400));
          throw http.ClientException('timeout');
        }),
        reintentosMax: 5,
        backoffBase: const Duration(milliseconds: 1),
        timeoutPeticion: const Duration(milliseconds: 500),
        timeoutTotal: const Duration(milliseconds: 900),
      );

      final t0 = DateTime.now();
      final r = await api.buscarDetallado('ohm');
      final dur = DateTime.now().difference(t0);

      expect(dur.inMilliseconds, lessThan(3000),
          reason: 'el presupuesto global corta la cadena de reintentos');
      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      api.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-04 · Estado explícito de la búsqueda', () {
    test('2xx con resultados -> ok', () async {
      final api = apiMock(MockClient((req) async => http.Response(
          cuerpoOk(total: 3), 200,
          headers: {'content-type': 'application/json'})));

      final r = await api.buscarDetallado('ohm');
      expect(r.estado, LuxEstadoBusqueda.ok);
      expect(r.httpStatus, 200);
      expect(r.hayErrorDeRed, isFalse);
      api.close();
    });

    test('2xx sin resultados (y sin respaldo) -> vacio', () async {
      final api = apiMock(MockClient((req) async => http.Response(
          cuerpoOk(total: 0), 200,
          headers: {'content-type': 'application/json'})));

      final r = await api.buscarDetallado('ohm');
      expect(r.estado, LuxEstadoBusqueda.vacio);
      expect(r.respuesta.total, 0);
      api.close();
    });

    test('fallo de red -> sinConexion', () async {
      final api = apiMock(
          MockClient((req) async => throw http.ClientException('sin red')),
          reintentos: 0);

      final r = await api.buscarDetallado('ohm');
      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      expect(r.hayErrorDeRed, isTrue);
      api.close();
    });

    test('5xx en AMBOS endpoints -> errorServidor', () async {
      final api = apiMock(MockClient((req) async =>
          http.Response('{"error":"boom"}', 503)));

      final r = await api.buscarDetallado('ohm');
      expect(r.estado, LuxEstadoBusqueda.errorServidor);
      expect(r.hayErrorDeServidor, isTrue);
      api.close();
    });

    test('query demasiado corta -> vacio sin tocar la red', () async {
      var llamadas = 0;
      final api = apiMock(MockClient((req) async {
        llamadas++;
        return http.Response('{}', 200);
      }));

      final r = await api.buscarDetallado('x');
      expect(llamadas, 0);
      expect(r.estado, LuxEstadoBusqueda.vacio);
      api.close();
    });

    test('el estado differentiate "vacio" de "sinConexion" (DEF-18)', () async {
      final vacio = apiMock(MockClient((req) async => http.Response(
          cuerpoOk(total: 0), 200,
          headers: {'content-type': 'application/json'})));
      final sinRed = apiMock(
          MockClient((req) async => throw http.ClientException('sin red')),
          reintentos: 0);

      final r1 = await vacio.buscarDetallado('ohm');
      final r2 = await sinRed.buscarDetallado('ohm');

      expect(r1.estado, isNot(r2.estado),
          reason: 'la UI puede distinguir ambos casos (mensajes distintos)');
      vacio.close();
      sinRed.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('P12-05 · Cabeceras HTTP centralizadas', () {
    test('las peticiones incluyen Accept y User-Agent', () async {
      http.BaseRequest? capturada;
      final api = apiMock(MockClient((req) async {
        capturada = req;
        return http.Response(cuerpoOk(total: 1), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscarDetallado('ohm');

      expect(capturada!.headers['Accept'], 'application/json');
      expect(capturada!.headers['User-Agent'], contains('LuxRay'));
      api.close();
    });
  });

  // ═══════════════════════════════════════════════════════════════════════
  group('Regresión · el comportamiento original se conserva', () {
    test('con resultados NO se llama al respaldo legacy', () async {
      final rutas = <String>[];
      final api = apiMock(MockClient((req) async {
        rutas.add(req.url.path);
        return http.Response(cuerpoOk(total: 5), 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscarDetallado('ohm');

      expect(rutas, ['/api/search']);
      expect(r.respuesta.total, 5);
      api.close();
    });

    test('sin resultados SÍ se llama al respaldo legacy', () async {
      final rutas = <String>[];
      final api = apiMock(MockClient((req) async {
        rutas.add(req.url.path);
        return http.Response(cuerpoOk(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscarDetallado('ohm');
      expect(rutas, ['/api/search', '/api/cursos/buscar']);
      api.close();
    });

    test('la firma original `buscar()` sigue devolviendo la respuesta',
        () async {
      final api = apiMock(MockClient((req) async => http.Response(
          cuerpoOk(total: 2), 200,
          headers: {'content-type': 'application/json'})));

      final r = await api.buscar('ohm');
      expect(r, isA<BusquedaResponse>());
      expect(r.total, 2);
      api.close();
    });
  });
}
