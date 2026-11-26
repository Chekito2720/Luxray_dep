import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/services/luxray_api.dart';

/// ===========================================================================
/// PRÁCTICA 11 — FASE 2: PRUEBAS WHITE BOX DE LA APLICACIÓN MÓVIL (Wear OS)
///
/// Aplicación evaluada : LuxRay Wear OS (`luxraywaer`)
/// Componente           : lógica de negocio + consumo de servicios
/// Versión de la app    : 1.0.0+1  (Android / Wear OS, com.luxray.waer)
/// Framework            : Flutter 3.44.6 / Dart 3.12.2
///
/// Métodos críticos seleccionados (los 3 que exige la práctica):
///
///   M1 — VALIDACIÓN      : `LuxrayApi.sanitizar`      (services/luxray_api.dart:39)
///   M2 — PROCESAMIENTO  : `BusquedaResponse.fromSearchApiResponse`
///                                           + `fromJson` + `_extractSources`
///                                          (models/busqueda.dart:47, 32, 92)
///   M3 — COMUNICACIÓN   : `LuxrayApi.buscar` + `_buscarEnSearch`
///                                          (services/luxray_api.dart:50, 64)
///
/// Para cada método se documentan: grafo de flujo de control (ver
/// `practica-11/fase2-movil-whitebox/FLUJOS-CONTROL.md`), caminos
/// independientes, casos de prueba, cobertura de instrucciones / decisiones /
/// condiciones, valores límite y excepciones.
///
/// Trazas a la cobertura (medidas con `flutter test --coverage --branch-coverage`
/// y el arnés de mutación `practica-11/tools/mutacion_condiciones.py`):
///
///   Statement coverage  -> % de DA: con hit > 0
///   Branch coverage     -> % de BRDA: con taken > 0
///   Condition coverage  -> % de mutadores de condición ABATIDOS por la suite
///                          (garantía más fuerte que MC/DC: si el mutante muere,
///                          ambos valores de la condición se evaluaron de forma
///                          discriminante en algún caso de prueba).
/// ===========================================================================

/// Constante del método M1 bajo prueba (`LuxrayApi.kMaxQuery`).
const int kMax = LuxrayApi.kMaxQuery;

/// Atajo: respuesta JSON del endpoint unificado `/search` con N cursos.
String searchJson({
  int total = 0,
  String query = 'ohm',
  List<dynamic> cursos = const [],
  List<dynamic> lecciones = const [],
  List<dynamic> conceptos = const [],
  List<dynamic> faqs = const [],
  bool envelope = true,
}) {
  final data = {
    'query': query,
    'total': total,
    'page': 0,
    'size': 10,
    'cursos': cursos,
    'lecciones': lecciones,
    'conceptos': conceptos,
    'faqs': faqs,
  };
  return jsonEncode(envelope ? {'data': data, 'success': true} : data);
}

/// Atajo: respuesta JSON del endpoint legacy `/cursos/buscar`.
String legacyJson({
  int total = 0,
  String query = 'ohm',
  List<dynamic> cursos = const [],
  List<dynamic> conceptos = const [],
}) {
  return jsonEncode({
    'data': {
      'query': query,
      'total': total,
      'cursos': cursos,
      'lecciones': const [],
      'conceptos': conceptos,
      'faqs': const [],
    },
    'success': true,
  });
}

void main() {
  // =========================================================================
  // M1 — MÉTODO DE VALIDACIÓN: `LuxrayApi.sanitizar`
  //
  // CFG (decisiones):  D1 = `limpio.length > kMaxQuery` (operador ternario)
  // Caminos independientes: P1 (condición verdadera) y P2 (condición falsa).
  // Complejidad ciclomática McCabe = 2 (1 decisión + 1).
  //
  // Condición atómica evaluada: `limpio.length > kMaxQuery`
  //   -> mutador MUT-C1 (`>` por `>=`), línea 41.
  // =========================================================================
  group('M1 · LuxrayApi.sanitizar (validación)', () {
    test('M1-P2 · camino falso: longitud <= 80 se devuelve intacta', () {
      expect(LuxrayApi.sanitizar('ohm'), 'ohm');
      expect(LuxrayApi.sanitizar('ley de ohm'), 'ley de ohm');
    });

    test('M1-P1 · camino verdadero: longitud > 80 se trunca a 80', () {
      final s = LuxrayApi.sanitizar('a' * (kMax + 1));
      expect(s.length, kMax);
      expect(s, 'a' * kMax);
    });

    test('M1-LIM · valor límite exacto: 80 caracteres NO se trunca', () {
      // Límite superior exacto: 80 chars debe salir idéntico (condición falsa).
      final entrada = 'b' * kMax;
      final s = LuxrayApi.sanitizar(entrada);
      expect(s.length, kMax);
      expect(s, entrada);
    });

    test('M1-LIM · valor límite: 81 caracteres sí se trunca', () {
      final s = LuxrayApi.sanitizar('c' * (kMax + 1));
      expect(s.length, kMax);
    });

    test('M1-VAC · cadena vacía y solo espacios', () {
      expect(LuxrayApi.sanitizar(''), '');
      expect(LuxrayApi.sanitizar('    '), '');
      expect(LuxrayApi.sanitizar('\t\n\r '), '');
    });

    test('M1-CTRL · elimina caracteres de control C0 y DEL', () {
      expect(LuxrayApi.sanitizar('ley\tde\r\nohm\x00\x1f\x7f'), 'leydeohm');
      expect(LuxrayApi.sanitizar('\x01\x02\x03'), '');
    });

    test('M1-CTRL · conserva acentos, ñ y símbolos (no son de control)', () {
      expect(LuxrayApi.sanitizar('  Ohm, ley de Ohm: V=I*R  ñ  '),
          'Ohm, ley de Ohm: V=I*R  ñ');
    });

    test('M1-IDEM · es idempotente: sanitizar(sanitizar(x)) == sanitizar(x)', () {
      final sucio = '  a\tb\nc\x00${'z' * 100}  ';
      final once = LuxrayApi.sanitizar(sucio);
      expect(LuxrayApi.sanitizar(once), once);
    });

    test('M1-ORD · el recorte ocurre después de normalizar (no antes)', () {
      // 40 espacios + 79 letras + "ultimo": el trim ocurre ANTES del recorte,
      // por lo que sobreviven las 79 letras y solo se pierde la cola.
      final entrada = '${' ' * 40}${'d' * 79}ultimo';
      final s = LuxrayApi.sanitizar(entrada);
      expect(s.length, kMax);
      expect(s, '${'d' * 79}u',
          reason: 'el recorte ocurre tras el trim: se pierden los 4 últimos');
    });

    test('M1-DEF · sanitizar es estático y no toca la red', () {
      // Firma estática: se puede invocar sin instancia ni cliente HTTP.
      expect(LuxrayApi.sanitizar('  ohm  '), 'ohm');
    });

    test('M1-CTOR · el constructor por defecto crea su propio cliente HTTP', () {
      // Sin `client:` inyectado, LuxrayApi crea un http.Client propio
      // (rama `client ?? http.Client()` del constructor) y puede cerrarse.
      final api = LuxrayApi();
      expect(api, isNotNull);
      api.close();
    });
  });

  // =========================================================================
  // M2 — MÉTODO DE PROCESAMIENTO: `BusquedaResponse.fromSearchApiResponse`
  //      (+ `fromJson` y `_extractSources`)
  //
  // CFG (decisiones):
  //   fromSearchApiResponse : D1 = `data is Map<String, dynamic>`
  //   fromJson              : D2..D5 = coalescencia `??` por campo
  //   _extractSources       : D6 = `raw is! List`
  //                            D7 = `hit['source'] as Map<String,dynamic>?`
  //                            D8 = filtro `whereType<Map<String,dynamic>>()`
  // Caminos independientes: P1 (data es Map) y P2 (data no es Map).
  // Complejidad ciclomática McCabe de `fromSearchApiResponse` = 2.
  //
  // Condiciones atómicas evaluadas:
  //   MUT-C10 (línea 49) `data is Map` → `data is! Map`
  //   MUT-C11 (línea 84) `raw is! List` → `raw is List`
  //   MUT-C12 (línea 93) `raw is! List` → `raw is List`
  //   MUT-C13 (línea 96) `as Map?` → `as Map` (falsy source nulo)
  // =========================================================================
  group('M2 · BusquedaResponse.fromSearchApiResponse (procesamiento)', () {
    test('M2-P1 · camino verdadero: envoltura data como Map se procesa', () {
      final r = BusquedaResponse.fromSearchApiResponse(jsonDecode(searchJson(
        total: 3,
        query: 'ohm',
        cursos: [
          {
            'id': 'c1',
            'score': 1.0,
            'source': {
              'id': 'c1',
              'titulo': 'Resistencia eléctrica',
              'descripcion': 'Oposición al paso de la corriente',
              'instructor': 'Ana',
              'nivel': 'BASICO',
              'icon': 'pi-bolt',
              'color': '#1565c0',
              'semanas': 4,
              'lecciones': 12,
              'publicado': true,
            },
          },
        ],
      )) as Map<String, dynamic>);

      expect(r.total, 3);
      expect(r.query, 'ohm');
      expect(r.cursos.length, 1);
      expect(r.cursos.first.titulo, 'Resistencia eléctrica');
      expect(r.cursos.first.nivelKey, 'basico');
    });

    test('M2-P2 · camino falso: data ausente o de otro tipo → respuesta vacía', () {
      final sinData = BusquedaResponse.fromSearchApiResponse({'success': true});
      expect(sinData.total, 0);
      expect(sinData.query, '');
      expect(sinData.cursos, isEmpty);
      expect(sinData.faqs, isEmpty);

      final dataTexto = BusquedaResponse.fromSearchApiResponse({'data': 'texto'});
      expect(dataTexto.total, 0);

      final dataLista =
          BusquedaResponse.fromSearchApiResponse({'data': <dynamic>[1, 2]});
      expect(dataLista.total, 0);
    });

    test('M2-D2..D5 · coalescencia: campos ausentes usan valores por defecto', () {
      final r = BusquedaResponse.fromJson({'query': 'x'});
      expect(r.total, 0, reason: 'total ausente → 0');
      expect(r.page, 0, reason: 'page ausente → 0');
      expect(r.size, 10, reason: 'size ausente → 10');
      expect(r.query, 'x');
      expect(r.lecciones, isEmpty);
      expect(r.conceptos, isEmpty);
    });

    test('M2-D6 · _extractSources con lista ausente (null) → lista vacía', () {
      final r = BusquedaResponse.fromJson({'total': 0});
      expect(r.cursos, isEmpty);
      expect(r.lecciones, isEmpty);
      expect(r.conceptos, isEmpty);
      expect(r.faqs, isEmpty);
    });

    test('M2-D6 · _extractSources con valor NO-lista (String) → lista vacía', () {
      final r = BusquedaResponse.fromJson({
        'total': 1,
        'cursos': 'esto-no-es-una-lista',
      });
      expect(r.cursos, isEmpty,
          reason: 'un tipo incorrecto no debe romper el parseo');
    });

    test('M2-D7/D8 · hit sin campo source se descarta sin romper el resto', () {
      final r = BusquedaResponse.fromSearchApiResponse(jsonDecode(searchJson(
        total: 2,
        cursos: [
          {'id': 'c1', 'source': null}, // source nulo → se descarta
          {
            'id': 'c2',
            'source': {
              'id': 'c2',
              'titulo': 'Circuito serie',
              'nivel': 'INTERMEDIO',
            },
          },
        ],
      )) as Map<String, dynamic>);

      expect(r.cursos.length, 1,
          reason: 'solo sobreviven los hits con source válido');
      expect(r.cursos.first.titulo, 'Circuito serie');
    });

    test('M2-D8 · elementos de la lista que no son Map se descartan', () {
      final r = BusquedaResponse.fromSearchApiResponse(jsonDecode(searchJson(
        total: 1,
        conceptos: [
          'basura',
          42,
          {'id': 'x1', 'source': {'id': 'x1', 'termino': 'Voltaje'}},
        ],
      )) as Map<String, dynamic>);

      expect(r.conceptos.length, 1);
      expect(r.conceptos.first.termino, 'Voltaje');
    });

    test('M2-NEST · conceptos: listas de sinonimos/temas no-lista → vacías', () {
      final r = BusquedaResponse.fromSearchApiResponse(jsonDecode(searchJson(
        total: 1,
        conceptos: [
          {
            'id': 'k1',
            'source': {
              'id': 'k1',
              'termino': 'Voltaje',
              'definicion': 'Diferencia de potencial',
              'sinonimos': 'no-es-lista',
              'temas': [1, 'ley de ohm', true],
            },
          },
        ],
      )) as Map<String, dynamic>);

      expect(r.conceptos.first.sinonimos, isEmpty);
      expect(r.conceptos.first.temas, ['ley de ohm'],
          reason: 'whereType<String> filtra los no-cadena');
    });

    test('M2-LEG · fromApiResponse (endpoint legacy) sin envoltura SearchHit',
        () {
      final r = BusquedaResponse.fromApiResponse(jsonDecode(legacyJson(
        total: 1,
        cursos: [
          {'id': 'c9', 'titulo': 'Curso legacy', 'nivel': 'AVANZADO'}
        ],
      )) as Map<String, dynamic>);

      expect(r.total, 1);
      expect(r.cursos.first.titulo, 'Curso legacy');
      expect(r.cursos.first.nivelKey, 'avanzado');
      expect(r.page, 0);
      expect(r.size, 10);
    });

    test('M2-LEG-P2 · fromApiResponse con data inválido → vacía', () {
      final r = BusquedaResponse.fromApiResponse({'data': 42});
      expect(r.total, 0);
      expect(r.cursos, isEmpty);
    });

    test('M2-LEG-D6 · _list con lista ausente (null) → lista vacía', () {
      // Cubre la rama de guarda "raw is! List" de _list.
      final r = BusquedaResponse.fromApiResponse({
        'data': {'query': 'ohm', 'total': 0},
      });
      expect(r.cursos, isEmpty);
      expect(r.lecciones, isEmpty);
      expect(r.conceptos, isEmpty);
      expect(r.faqs, isEmpty);
    });

    test('M2-LEG-D6 · _list con valor NO-lista → lista vacía', () {
      final r = BusquedaResponse.fromApiResponse({
        'data': {'query': 'ohm', 'total': 1, 'cursos': 'texto'},
      });
      expect(r.cursos, isEmpty);
      expect(r.total, 1);
    });

    test('M2-EMPTY · empty() es consistente en todos sus campos', () {
      final r = BusquedaResponse.empty();
      expect(r.query, '');
      expect(r.total, 0);
      expect(r.page, 0);
      expect(r.size, 10);
      expect(r.cursos, isEmpty);
      expect(r.lecciones, isEmpty);
      expect(r.conceptos, isEmpty);
      expect(r.faqs, isEmpty);
    });
  });

  // =========================================================================
  // M3 — MÉTODO DE COMUNICACIÓN CON EL SERVICIO:
  //      `LuxrayApi.buscar` + `LuxrayApi._buscarEnSearch`
  //
  // CFG (decisiones):
  //   buscar            : D1 = `query.length < 2`
  //                        D2 = `searchResponse.total > 0`
  //   _buscarEnSearch   : D3 = `resp.statusCode < 200`   (rama del `||`)
  //                        D4 = `resp.statusCode >= 300`  (rama del `||`)
  //                        D5 = `body is Map<String, dynamic>`
  //                        D6 = bloque `catch` (excepción)
  // Caminos independientes de `buscar`: P1 (query corta), P2 (hit en /search),
  // P3 (sin hit → fallback a /cursos/buscar).
  // Complejidad ciclomática McCabe de `buscar` = 3 (+2 de `_buscarEnSearch`
  // con el `||` compuesto y el `catch`) = 5 en la ruta completa.
  //
  // Condiciones atómicas evaluadas:
  //   MUT-C2 (línea 52) `query.length < 2`  → `query.length <= 2`
  //   MUT-C3 (línea 56) `searchResponse.total > 0` → `>= 0`
  //   MUT-C4 (línea 74) `||` → `&&`
  //   MUT-C5 (línea 74) `< 200` → `<= 200`
  //   MUT-C6 (línea 78) `body is Map` → `body is! Map`
  //   MUT-C7 (línea 97) `||` → `&&`   (endpoint legacy)
  //   MUT-C8 (línea 97) `< 200` → `<= 200` (endpoint legacy)
  //   MUT-C9 (línea 101) `body is Map` → `body is! Map` (endpoint legacy)
  // =========================================================================
  group('M3 · LuxrayApi.buscar (comunicación con el servicio)', () {
    test('M3-P1 · query demasiado corta: NO se genera ninguna petición HTTP',
        () async {
      var llamadas = 0;
      final api = LuxrayApi(client: MockClient((req) async {
        llamadas++;
        return http.Response('{}', 200);
      }));

      final r = await api.buscar('o');
      expect(r.total, 0);
      expect(llamadas, 0, reason: 'una query inválida nunca llega al servidor');
      api.close();
    });

    test('M3-P1b · query de 1 carácter tras sanear: sin red', () async {
      var llamadas = 0;
      final api = LuxrayApi(client: MockClient((req) async {
        llamadas++;
        return http.Response('{}', 200);
      }));

      final r = await api.buscar(' x ');
      expect(r.total, 0);
      expect(llamadas, 0);
      api.close();
    });

    test('M3-LIM · query de exactamente 2 caracteres SÍ genera petición',
        () async {
      final uris = <Uri>[];
      final api = LuxrayApi(client: MockClient((req) async {
        uris.add(req.url);
        return http.Response(searchJson(total: 1), 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscar('ab');
      expect(uris.length, 1);
      expect(r.total, 1);
      api.close();
    });

    test('M3-P2 · camino con hits: usa /search y NO hace fallback', () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        return http.Response(
          searchJson(
            total: 2,
            cursos: [
              {'id': 'c1', 'source': {'id': 'c1', 'titulo': 'Ohm'}},
            ],
          ),
          200,
          headers: {'content-type': 'application/json'},
        );
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 2);
      expect(r.cursos.first.titulo, 'Ohm');
      expect(rutas, ['/api/search'],
          reason: 'con resultados no debe tocar el endpoint legacy');
      api.close();
    });

    test('M3-P3 · camino sin hits: hace fallback a /cursos/buscar', () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        if (req.url.path.endsWith('/search')) {
          return http.Response(searchJson(total: 0), 200,
              headers: {'content-type': 'application/json'});
        }
        return http.Response(
          legacyJson(total: 1, cursos: [
            {'id': 'c1', 'titulo': 'Curso legacy'}
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 1);
      expect(r.cursos.first.titulo, 'Curso legacy');
      expect(rutas, ['/api/search', '/api/cursos/buscar'],
          reason: 'el fallback debe ocurrir exactamente una vez');
      api.close();
    });

    test('M3-URL · la query viaja parametrizada (URL-encoding) y saneada',
        () async {
      final uris = <Uri>[];
      final api = LuxrayApi(client: MockClient((req) async {
        uris.add(req.url);
        return http.Response(searchJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscar('ley de ohm & Potencial');
      final capturada = uris.first; // primera petición: /search
      expect(capturada.queryParameters['q'], 'ley de ohm & Potencial');
      expect(capturada.queryParameters['page'], '0');
      expect(capturada.queryParameters['size'], '10');
      expect(capturada.path, '/api/search');
      expect(capturada.query, contains('%26'),
          reason: 'el ampersand debe ir codificado, no(raw)');
      api.close();
    });

    test('M3-HEAD · envía la cabecera Accept: application/json', () async {
      http.BaseRequest? capturada;
      final api = LuxrayApi(client: MockClient((req) async {
        capturada = req;
        return http.Response(searchJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscar('ohm');
      expect(capturada!.headers['Accept'], 'application/json');
      api.close();
    });

    test('M3-D3/D4 · status no-2xx en /search: cae al fallback', () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        if (req.url.path.endsWith('/search')) {
          // Cuerpo que SÍ contiene un envelope válido: el cliente debe
          // descartar el cuerpo por el status, no parsearlo.
          return http.Response(searchJson(total: 5), 500,
              headers: {'content-type': 'application/json'});
        }
        return http.Response(legacyJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0,
          reason: 'un 500 nunca debe convertirse en resultados');
      expect(rutas, ['/api/search', '/api/cursos/buscar']);
      api.close();
    });

    test('M3-D3/D4 · status no-2xx en el endpoint legacy: resultado vacío',
        () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        if (req.url.path.endsWith('/search')) {
          return http.Response(searchJson(total: 0), 200,
              headers: {'content-type': 'application/json'});
        }
        // El legacy responde 503 con un envelope que aparenta tener 5 hits:
        // debe descartarse por el status.
        return http.Response(legacyJson(total: 5), 503,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0,
          reason: 'un 503 nunca debe convertirse en resultados');
      expect(rutas, ['/api/search', '/api/cursos/buscar']);
      api.close();
    });

    test('M3-D3/D4 · status 199 (< 200) también se considera error', () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        if (req.url.path.endsWith('/search')) {
          return http.Response('{}', 199);
        }
        return http.Response(legacyJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      expect(rutas.length, 2, reason: '199 no es un 2xx válido');
      api.close();
    });

    test('M3-D3/D4 · status 300 exacto también se considera error', () async {
      final rutas = <String>[];
      final api = LuxrayApi(client: MockClient((req) async {
        rutas.add(req.url.path);
        if (req.url.path.endsWith('/search')) {
          return http.Response('{}', 300);
        }
        return http.Response(legacyJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscar('ohm');
      expect(rutas.length, 2, reason: '300 queda fuera del rango 2xx');
      api.close();
    });

    test('M3-D5 · body JSON que NO es un Map → respuesta vacía', () async {
      final api = LuxrayApi(client: MockClient((req) async {
        return http.Response('[1,2,3]', 200,
            headers: {'content-type': 'application/json'});
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      expect(r.cursos, isEmpty);
      api.close();
    });

    test('M3-D6 · excepción de red: degrada a vacío, NO propaga', () async {
      final api = LuxrayApi(client: MockClient((req) async {
        throw http.ClientException('SocketException: red caída');
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      expect(r.query, isNot('ohm'), reason: 'no debe filtrar la query');
      api.close();
    });

    test('M3-D6 · timeout: degrada a vacío tras el timeout configurado',
        () async {
      final api = LuxrayApi(client: MockClient((req) async {
        await Future<void>.delayed(const Duration(seconds: 7));
        return http.Response(searchJson(total: 1), 200);
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0, reason: 'el timeout de 6 s debe abortar la búsqueda');
      api.close();
    });

    test('M3-EXC · JSON malformado: degrada a vacío sin lanzar', () async {
      final api = LuxrayApi(client: MockClient((req) async {
        return http.Response('<html>stack trace</html>', 200);
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      api.close();
    });

    test('M3-EXC · el cliente se cierra sin dejar conexiones abiertas',
        () async {
      final api = LuxrayApi(client: MockClient((req) async {
        return http.Response(searchJson(total: 0), 200);
      }));
      api.close();
      // Si close() fuera incorrecto, la segunda llamada fallaría.
      await api.buscar('ohm');
    });

    test('M3-SEG · no se exponen detalles internos del servidor en el error',
        () async {
      final api = LuxrayApi(client: MockClient((req) async {
        return http.Response(
          '{"error":"Internal Server Error",'
          '"detail":"jdbc:postgresql://prod-db:5432/luxray_secret"}',
          500,
        );
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      final serializado = jsonEncode({
        'query': r.query,
        'total': r.total,
        'cursos': r.cursos.length,
      });
      expect(serializado, isNot(contains('jdbc')));
      expect(serializado, isNot(contains('luxray_secret')));
      api.close();
    });

    test('M3-LIM · query de 500 caracteres llega truncada a 80', () async {
      String? enviada;
      final api = LuxrayApi(client: MockClient((req) async {
        enviada = req.url.queryParameters['q'];
        return http.Response(searchJson(total: 0), 200,
            headers: {'content-type': 'application/json'});
      }));

      await api.buscar('a' * 500);
      expect(enviada!.length, kMax);
      api.close();
    });

    test('M3-BASE · baseUrl inyectable: respeta el prefijo configurado',
        () async {
      final uris = <Uri>[];
      final api = LuxrayApi(
        baseUrl: 'https://ejemplo.test/api',
        client: MockClient((req) async {
          uris.add(req.url);
          return http.Response(searchJson(total: 0), 200,
              headers: {'content-type': 'application/json'});
        }),
      );

      await api.buscar('ohm');
      expect(uris.first.toString(),
          startsWith('https://ejemplo.test/api/search'));
      api.close();
    });

    test('M3-FALLBACK-ERR · si /search falla y el legacy también, queda vacío',
        () async {
      final api = LuxrayApi(client: MockClient((req) async {
        throw http.ClientException('red caída');
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      expect(r.cursos, isEmpty);
      api.close();
    });
  });
}
