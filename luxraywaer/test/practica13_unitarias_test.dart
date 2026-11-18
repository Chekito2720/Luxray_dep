import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';

import 'package:luxraywaer/services/luxray_api.dart';

/// ===========================================================================
/// PRÁCTICA 13/14 — FASE 2 · AUTOMATIZACIÓN DE PRUEBAS UNITARIAS
///
/// Componente crítico del móvil seleccionado en la Fase 1:
///     `LuxrayApi.buscarDetallado()` — la función crítica de búsqueda del
///     wearable LuxRay (validación de entrada, caché, reintento con backoff,
///     respaldo al endpoint legacy y clasificación del estado real).
///
/// Las cinco pruebas exigidas por la práctica:
///   U-01 · Entrada válida                       -> estado ok y datos correctos
///   U-02 · Entrada vacía o nula                 -> no toca la red, estado vacío
///   U-03 · Valores fuera de rango (query larga) -> se trunca al límite
///   U-04 · Resultado esperado                   -> parseo fiel del contrato
///   U-05 · Manejo de excepciones                -> no propaga, estado explícito
///
/// Defecto que estas pruebas impiden que se reintroduzca:
///     DEF-18 / D12-05 — antes, un fallo de red se presentaba como
///     «Sin coincidencias». U-02 y U-06 fallan contra el código original.
/// ===========================================================================

/// Servidor HTTP local con el contrato real del gateway de LuxRay.
///
/// No es un doble de prueba: es un socket real en localhost que devuelve
/// (o no) respuestas HTTP, de modo que la prueba ejercita el cliente contra
/// un servicio y no contra un mock del objeto `http.Client`.
class ServicioLuxRay {
  ServicioLuxRay(this.modo);

  /// `ok` · `vacioEnSearch` · `malformado` · `error500` · `error401`
  /// `error403` · `sinRespuesta`
  String modo;

  HttpServer? _srv;
  final List<String> rutas = <String>[];

  /// Respuestas 200 servidas por el endpoint unificado.
  int servidas = 0;

  Future<int> arrancar() async {
    _srv = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _srv!.listen(_atender);
    return _srv!.port;
  }

  Future<void> _atender(HttpRequest req) async {
    rutas.add('${req.method} ${req.uri}');
    try {
      switch (modo) {
        case 'sinRespuesta':
          await Future<void>.delayed(const Duration(seconds: 20));
          return;
        case 'error500':
          req.response.statusCode = 500;
          await req.response.close();
          return;
        case 'error401':
          req.response.statusCode = 401;
          await req.response.close();
          return;
        case 'error403':
          req.response.statusCode = 403;
          await req.response.close();
          return;
        case 'malformado':
          req.response
            ..statusCode = 200
            ..headers.contentType = ContentType.json
            ..write('{"data": [1, 2, 3]}'); // `data` como array, no como objeto
          await req.response.close();
          return;
        case 'vacioEnSearch':
          if (req.uri.path.endsWith('/cursos/buscar')) {
            await _escribir(req, total: 1, legacy: true);
          } else {
            await _escribir(req, total: 0, legacy: false);
          }
          return;
        case 'ok':
          if (req.uri.path.endsWith('/cursos/buscar')) {
            await _escribir(req, total: 1, legacy: true);
          } else {
            servidas++;
            await _escribir(req, total: 2, legacy: false);
          }
          return;
      }
    } catch (_) {
      // El cliente se desconectó.
    }
  }

  Future<void> _escribir(HttpRequest req,
      {required int total, required bool legacy}) async {
    final q = req.uri.queryParameters['q'] ?? '';
    final data = legacy
        ? {
            'query': q,
            'total': total,
            'cursos': [
              {'id': 'c-legado', 'titulo': 'Curso legacy', 'nivel': 'BASICO'}
            ],
            'lecciones': const [],
            'conceptos': const [],
            'faqs': const [],
          }
        : {
            'query': q,
            'total': total,
            'cursos': [
              {
                'id': 'c1',
                'score': 1.0,
                'source': {
                  'id': 'c1',
                  'titulo': 'Fundamentos de Electricidad',
                  'descripcion': 'Curso de iniciación',
                  'instructor': 'Prof. García',
                  'nivel': 'BASICO',
                  'icon': 'pi-bolt',
                  'color': '#1565c0',
                  'semanas': 8,
                  'lecciones': 20,
                  'publicado': true,
                },
              }
            ],
            'lecciones': const [],
            'conceptos': const [],
            'faqs': const [],
          };

    req.response
      ..statusCode = 200
      ..headers.contentType = ContentType.json
      ..write(jsonEncode({'data': data, 'success': true}));
    await req.response.close();
  }

  Future<void> parar() async {
    await _srv?.close(force: true);
    _srv = null;
  }
}

void main() {
  late ServicioLuxRay srv;

  // PRÁCTICA 13/14 · GOTCHA DE CI
  // `flutter_test` instala un `HttpOverrides` que devuelve 400 a cualquier
  // petición HTTP real y es global al archivo: si en el futuro se añade un
  // `testWidgets` a este archivo, las pruebas de red dejarían de funcionar.
  // Se desactiva durante la ejecución y se restaura después.
  HttpOverrides? overrideAnterior;

  setUp(() async {
    overrideAnterior = HttpOverrides.current;
    HttpOverrides.global = null;
    srv = ServicioLuxRay('ok');
    await srv.arrancar();
  });

  tearDown(() async {
    await srv.parar();
    HttpOverrides.global = overrideAnterior;
  });

  LuxrayApi apiContraServicio({bool cache = false}) => LuxrayApi(
        baseUrl: 'http://127.0.0.1:${srv._srv!.port}/api',
        client: IOClient(HttpClient()),
        cacheActivo: cache,
        reintentosMax: 1,
        backoffBase: const Duration(milliseconds: 10),
        timeoutPeticion: const Duration(milliseconds: 400),
        timeoutTotal: const Duration(seconds: 2),
      );

  // ── U-01 · ENTRADA VÁLIDA ──────────────────────────────────────────────
  test('U-01 · entrada válida: responde ok con los datos del contrato',
      () async {
    final api = apiContraServicio();

    final r = await api.buscarDetallado('ley de ohm');

    expect(r.estado, LuxEstadoBusqueda.ok,
        reason: 'una query válida debe resolver ok');
    expect(r.httpStatus, 200);
    expect(r.respuesta.total, 2);
    expect(r.respuesta.cursos.length, 1);
    expect(r.respuesta.cursos.first.titulo, 'Fundamentos de Electricidad');
    expect(r.respuesta.cursos.first.instructor, 'Prof. García');
    expect(srv.rutas.first, contains('/api/search'));
    api.close();
  });

  // ── U-02 · ENTRADA VACÍA O NULA ─────────────────────────────────────────
  test('U-02 · entrada vacía o nula: no toca la red y queda en estado vacío',
      () async {
    final api = apiContraServicio();

    final r1 = await api.buscarDetallado('');
    final r2 = await api.buscarDetallado('   ');
    final r3 = await api.buscarDetallado('\t\r\n'); // solo caracteres de control

    for (final r in [r1, r2, r3]) {
      expect(r.estado, LuxEstadoBusqueda.vacio);
      expect(r.respuesta.total, 0);
      expect(r.httpStatus, isNull, reason: 'no hubo comunicación');
    }
    expect(srv.rutas, isEmpty,
        reason: 'una query vacía no debe generar ni una petición HTTP');
    api.close();
  });

  // ── U-03 · VALORES FUERA DE RANGO ───────────────────────────────────────
  test('U-03 · query de 500 caracteres: se trunca al límite antes de enviar',
      () async {
    final api = apiContraServicio();

    final r = await api.buscarDetallado('a' * 500);

    expect(r.estado, LuxEstadoBusqueda.ok);
    expect(srv.rutas, isNotEmpty);
    final uri = Uri.parse(srv.rutas.first);
    expect(uri.queryParameters['q']!.length, LuxrayApi.kMaxQuery,
        reason: 'la query enviada nunca supera el máximo configurado');
    api.close();
  });

  // ── U-04 · RESULTADO ESPERADO ───────────────────────────────────────────
  test('U-04 · el resultado se ajusta al contrato: respaldo y parseo fiel',
      () async {
    srv.modo = 'vacioEnSearch';
    final api = apiContraServicio();

    final r = await api.buscarDetallado('inexistente');

    expect(r.estado, LuxEstadoBusqueda.ok);
    expect(r.respuesta.cursos.first.id, 'c-legado');
    expect(r.respuesta.cursos.first.nivelKey, 'basico',
        reason: 'el modelo normaliza el nivel a minúsculas');
    expect(srv.rutas, contains(contains('/api/cursos/buscar')),
        reason: 'sin resultados en el unificado debe usarse el respaldo');
    api.close();
  });

  test('U-04b · respuesta con forma inesperada: se clasifica, no se traga',
      () async {
    srv.modo = 'malformado';
    final api = apiContraServicio();

    final r = await api.buscarDetallado('ohm');

    expect(r.estado, isNot(LuxEstadoBusqueda.ok),
        reason: 'un contrato distinto NO debe presentarse como resultados');
    expect(r.estado, LuxEstadoBusqueda.errorPeticion);
    expect(r.respuesta.total, 0);
    api.close();
  });

  // ── U-05 · MANEJO DE EXCEPCIONES ────────────────────────────────────────
  test('U-05 · red caída: no propaga la excepción y el estado es explícito',
      () async {
    final api = LuxrayApi(
      baseUrl: 'http://127.0.0.1:1/api', // puerto cerrado: conexión rechazada
      client: IOClient(HttpClient()),
      reintentosMax: 0,
      timeoutTotal: const Duration(seconds: 1),
    );

    final futuro = api.buscarDetallado('ohm');
    expect(futuro, completes,
        reason: 'la excepción de red debe capturarse, no propagarse');

    final r = await futuro;
    expect(r.estado, LuxEstadoBusqueda.sinConexion);
    expect(r.hayErrorDeRed, isTrue);
    expect(r.respuesta.total, 0);
    api.close();
  });

  test('U-05b · servidor que no responde: el timeout corta la espera', () async {
    srv.modo = 'sinRespuesta';
    final api = apiContraServicio();

    final t0 = DateTime.now();
    final r = await api.buscarDetallado('ohm');
    final ms = DateTime.now().difference(t0).inMilliseconds;

    expect(r.estado, LuxEstadoBusqueda.sinConexion);
    expect(ms, lessThan(5000), reason: 'el presupuesto global acota la espera');
    api.close();
  });

  test('U-05c · HTTP 500: estado de error de servidor, no de red', () async {
    srv.modo = 'error500';
    final api = apiContraServicio();

    final r = await api.buscarDetallado('ohm');

    expect(r.httpStatus, 500);
    expect(r.estado, LuxEstadoBusqueda.errorServidor);
    expect(r.hayErrorDeRed, isFalse,
        reason: 'un 5xx es un fallo del servidor, no una caída de red');
    api.close();
  });

  test('U-05d · HTTP 401 y 403: no se confunden con un fallo de red', () async {
    for (final (modo, esperado) in [('error401', 401), ('error403', 403)]) {
      srv.modo = modo;
      final api = apiContraServicio();

      final r = await api.buscarDetallado('ohm');

      expect(r.estado, LuxEstadoBusqueda.accesoRechazado);
      expect(r.httpStatus, esperado);
      expect(r.hayErrorDeRed, isFalse);
      api.close();
    }
  });

  // ── Extras: el defecto que la automatización impide reintroducir ────────
  test('U-06 · regresión DEF-18: un fallo de red NO dice «sin resultados»',
      () async {
    final api = LuxrayApi(
      baseUrl: 'http://127.0.0.1:1/api',
      client: IOClient(HttpClient()),
      reintentosMax: 0,
    );

    final r = await api.buscarDetallado('ohm');

    expect(r.estado, isNot(LuxEstadoBusqueda.vacio),
        reason: '«sin resultados» y «sin conexión» son cosas distintas (DEF-18)');
    expect(r.estado, isNot(LuxEstadoBusqueda.ok));
    api.close();
  });

  test('U-07 · la caché evita repetir la descarga de la misma consulta',
      () async {
    final api = apiContraServicio(cache: true);

    final r1 = await api.buscarDetallado('ohm');
    final antes = srv.servidas;
    final r2 = await api.buscarDetallado('ohm');

    expect(r1.desdeCache, isFalse);
    expect(r2.desdeCache, isTrue);
    expect(srv.servidas, antes,
        reason: 'la segunda búsqueda debe servirse de la caché local');
    api.close();
  });
}
