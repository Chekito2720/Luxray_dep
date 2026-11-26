import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';

import 'package:luxraywaer/screens/resultados_screen.dart';
import 'package:luxraywaer/services/luxray_api.dart';

/// ===========================================================================
/// PRÁCTICA 13/14 — FASE 3 · AUTOMATIZACIÓN DE PRUEBAS DE INTEGRACIÓN
///
/// Validan la COMUNICACIÓN ENTRE COMPONENTES (no la lógica aislada):
///
///   I-01 · La aplicación envía información al servicio y recibe una
///          respuesta válida (cliente Wear OS ↔ gateway).
///   I-02 · La API devuelve el código HTTP esperado.
///   I-03 · La aplicación muestra un mensaje adecuado cuando el servidor
///          no responde (mapeo presentación ↔ estado de comunicación).
///   I-04 · Contrato de petición: lo que el cliente envía es exactamente lo
///          que el servicio necesita.
///
/// La persistencia («la operación de registro almacena correctamente la
/// información») se verifica en el backend:
///     backend/auth-service/src/test/.../RegistroIntegracionTest.java
///
/// Adaptación justificada: el wearable no puede recibir en tiempo de
/// ejecución una URL de backend distinta a la de compilación
/// (`--dart-define`), de modo que el extremo del servicio se simula con un
/// `HttpServer` real en localhost que implementa el contrato del gateway.
/// El cliente bajo prueba es SIEMPRE el código de producción.
/// ===========================================================================

/// Gateway simulado con el contrato real de LuxRay.
class GatewaySimulado {
  GatewaySimulado(this.estado);

  /// `ok` · `error500` · `sinRespuesta`
  String estado;

  HttpServer? _srv;
  int puerto = 0;

  /// Peticiones recibidas (método + path + query).
  final List<Map<String, String>> peticiones = <Map<String, String>>[];

  Future<void> arrancar() async {
    _srv = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    puerto = _srv!.port;
    _srv!.listen(_atender);
  }

  Future<void> _atender(HttpRequest req) async {
    peticiones.add({
      'method': req.method,
      'path': req.uri.path,
      'q': req.uri.queryParameters['q'] ?? '',
      'page': req.uri.queryParameters['page'] ?? '',
      'size': req.uri.queryParameters['size'] ?? '',
      'accept': req.headers.value('Accept') ?? '',
    });
    try {
      switch (estado) {
        case 'sinRespuesta':
          // Acepta y no responde: fuerza el timeout del cliente.
          // (1,5 s: el cliente abandona a los 500 ms; el retardo sólo sirve
          // para que el servidor no cierre la conexión antes de tiempo.)
          await Future<void>.delayed(const Duration(milliseconds: 1500));
          return;
        case 'error500':
          req.response.statusCode = 503;
          await req.response.close();
          return;
        case 'ok':
          req.response
            ..statusCode = 200
            ..headers.contentType = ContentType.json
            ..write(jsonEncode({
              'data': {
                'query': req.uri.queryParameters['q'] ?? '',
                'total': 1,
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
              },
              'success': true,
            }));
          await req.response.close();
          return;
      }
    } catch (_) {
      // El cliente se desconectó.
    }
  }

  Future<void> parar() async {
    await _srv?.close(force: true);
    _srv = null;
  }
}

void main() {
  late GatewaySimulado gw;

  // PRÁCTICA 13/14 · GOTCHA DE CI
  // `flutter_test` instala un `HttpOverrides` que devuelve 400 a CUALQUIER
  // petición HTTP real («will actually be made...»), y ese override es
  // global al archivo: basta con que UN `testWidgets` se ejecute en el mismo
  // archivo para que también los `test()` dejen de poder abrir sockets.
  // Como estas pruebas validan justamente la comunicación real con un
  // servicio, se desactiva el override durante la ejecución y se restaura
  // después. Sin esto el pipeline de CI falla siempre.
  HttpOverrides? overrideAnterior;

  setUp(() async {
    overrideAnterior = HttpOverrides.current;
    HttpOverrides.global = null; // HttpClient() usa la implementación real
    gw = GatewaySimulado('ok');
    await gw.arrancar();
  });

  tearDown(() async {
    await gw.parar();
    HttpOverrides.global = overrideAnterior;
  });

  LuxrayApi cliente() => LuxrayApi(
        baseUrl: 'http://127.0.0.1:${gw.puerto}/api',
        client: IOClient(HttpClient()),
        cacheActivo: false,
        reintentosMax: 0,
        timeoutPeticion: const Duration(milliseconds: 500),
        timeoutTotal: const Duration(seconds: 2),
      );

  // ── I-01 · La app envía al servicio y recibe una respuesta válida ───────
  test('I-01 · comunicación cliente↔servicio: petición válida y respuesta válida',
      () async {
    final api = cliente();

    final r = await api.buscarDetallado('ley de ohm');

    // El servicio recibió la petición con el contrato esperado.
    expect(gw.peticiones, isNotEmpty);
    final p = gw.peticiones.first;
    expect(p['method'], 'GET');
    expect(p['path'], '/api/search');
    expect(p['q'], 'ley de ohm');
    expect(p['page'], '0');
    expect(p['size'], '10');
    expect(p['accept'], 'application/json');

    // Y el cliente interpretó la respuesta correctamente.
    expect(r.estado, LuxEstadoBusqueda.ok);
    expect(r.httpStatus, 200);
    expect(r.respuesta.total, 1);
    expect(r.respuesta.cursos.first.titulo, 'Fundamentos de Electricidad');
    api.close();
  });

  // ── I-02 · La API devuelve el código HTTP esperado ─────────────────────
  test('I-02 · el código HTTP se corresponde con el resultado real', () async {
    final api = cliente();

    gw.estado = 'ok';
    final ok = await api.buscarDetallado('ohm');
    expect(ok.httpStatus, 200, reason: 'operación correcta -> 200');
    expect(ok.estado, LuxEstadoBusqueda.ok);

    gw.estado = 'error500';
    final error = await api.buscarDetallado('ohm');
    expect(error.httpStatus, 503, reason: 'servidor caído -> 5xx, nunca 200');
    expect(error.estado, LuxEstadoBusqueda.errorServidor,
        reason: 'un 5xx no debe presentarse como éxito ni como caída de red');
    api.close();
  });

  // ── I-03 · La app muestra un mensaje adecuado si el servidor no responde ─
  testWidgets('I-03 · sin respuesta del servidor la app muestra un aviso claro',
      (tester) async {
    // El estado `sinRespuesta` fuerza el timeout del cliente.
    gw.estado = 'sinRespuesta';
    final api = cliente();

    // `testWidgets` ejecuta el cuerpo en una zona FAKE-ASYNC: los `Future`
    // reales (como una petición HTTP) nunca se completan ahí y el test se
    // queda colgado. `tester.runAsync` ejecuta el callback en una zona real,
    // que es lo que necesita una comunicación de red.
    final LuxResultadoBusqueda? r = await tester.runAsync(() async {
      return api.buscarDetallado('ohm');
    });
    api.close();

    expect(r, isNotNull, reason: 'la petición debe completarse en la zona real');
    expect(r!.estado, LuxEstadoBusqueda.sinConexion);

    // Y la pantalla presenta el mensaje correspondiente a ESE estado.
    await tester.pumpWidget(MaterialApp(
      home: ResultadosScreen(
        query: 'ohm',
        resultado: r.respuesta,
        onVolver: () {},
        onAbrirConcepto: (_) {},
        onAbrirFaq: (_) {},
        estado: r.estado,
      ),
    ));

    expect(find.textContaining('Sin conexión'), findsOneWidget,
        reason: 'el usuario debe enterarse de que falló la red');
    expect(find.textContaining('Sin coincidencias'), findsNothing,
        reason: 'no debe presentarse un fallo de red como «sin resultados»');
  });

  // ── I-04 · El contrato de petición es exactamente el que el servicio usa ─
  test('I-04 · el contrato de petición coincide con lo que consume el servicio',
      () async {
    final api = cliente();

    await api.buscarDetallado('  ley de ohm  ');

    final p = gw.peticiones.first;
    expect(p['q'], 'ley de ohm', reason: 'la query viaja saneada');
    expect(p['path'], '/api/search');
    expect(p['method'], 'GET');
    api.close();
  });
}
