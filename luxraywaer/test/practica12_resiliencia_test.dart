import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/services/luxray_api.dart';

/// ===========================================================================
/// PRÁCTICA 12 — FASE 5 — PRUEBAS DE RESILIENCIA Y RECUPERACIÓN
///
/// Se provocan fallos CONTROLADOS de red y de servidor contra el cliente REAL
/// (`LuxrayApi`, la implementación optimizada) y se comprueba que:
///
///   RES-01 · Pérdida de conexión durante una consulta  -> error controlado
///   RES-02 · Pérdida de conexión durante una escritura  -> no confirmar
///   RES-03 · Servidor que no responde                  -> timeout controlado
///   RES-04 · Respuesta HTTP 500                         -> error + estabilidad
///   RES-05 · Respuesta HTTP 401 / 403                   -> authn vs authz
///   RES-06 · Recuperación de la conexión               -> se restablece
///   RES-07 · Reintento de una escritura                -> sin duplicados
///   RES-08 · Respuesta tardía                         -> sin inconsistencia
///
/// El servidor de pruebas es un `HttpServer` real en localhost que sabe
/// comportarse como un gateway con averías, de modo que se ejercitan los
/// sockets, los timeouts y los códigos HTTP reales (no dobles de prueba).
///
/// NOTA sobre RES-02 y RES-07: el wearable LuxRay es una aplicación de
/// SOLO LECTURA (su único método de red es un GET), por lo que no puede
/// provocar por sí mismo una escritura. Esos dos escenarios se ejecutan
/// contra la API real en `practica-12/tools/resiliencia_api.py`, que
/// comprueba además la ausencia de duplicados en PostgreSQL.
/// ===========================================================================

/// Modos de avería del servidor de pruebas.
enum Averia {
  /// Comportamiento normal.
  normal,

  /// Cierra la conexión sin responder: la petición SALE pero la respuesta
  /// NUNCA llega (RES-01 / RES-07).
  cortar,

  /// Acepta la conexión y no responde nunca: fuerza el timeout (RES-03).
  sinResponder,

  /// Responde 500 (RES-04).
  error500,

  /// Responde 401 (RES-05).
  error401,

  /// Responde 403 (RES-05).
  error403,

  /// Responde con retraso (RES-08).
  lento,
}

/// Servidor HTTP de pruebas con modos de avería inyectables.
class ServidorAveria {
  ServidorAveria(this.averia, {this.retraso = const Duration(seconds: 3)});

  Averia averia;
  final Duration retraso;

  HttpServer? _srv;
  int peticiones = 0;
  final List<String> rutas = <String>[];

  Future<int> arrancar() async {
    _srv = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _srv!.listen(_atender);
    return _srv!.port;
  }

  void cambiarAveria(Averia a) => averia = a;

  Future<void> _atender(HttpRequest req) async {
    peticiones++;
    rutas.add('${req.method} ${req.uri.path}');
    try {
      switch (averia) {
        case Averia.cortar:
          // Acepta y cierra de golpe: el cliente ve "connection reset".
          await req.response.detachSocket().then((s) => s.destroy());
          return;
        case Averia.sinResponder:
          // No responde: el cliente debe agotar su timeout.
          await Future<void>.delayed(const Duration(seconds: 30));
          return;
        case Averia.lento:
          await Future<void>.delayed(retraso);
          req.response
            ..statusCode = 200
            ..headers.contentType = ContentType.json
            ..write('{"data":{"query":"ohm","total":0,"cursos":[],"lecciones":[],'
                '"conceptos":[],"faqs":[]},"success":true}');
          await req.response.close();
          return;
        case Averia.error500:
          req.response
            ..statusCode = 500
            ..headers.contentType = ContentType.json
            ..write('{"error":"Internal Server Error"}');
          await req.response.close();
          return;
        case Averia.error401:
          req.response
            ..statusCode = 401
            ..headers.contentType = ContentType.json
            ..write('{"status":401,"error":"Unauthorized"}');
          await req.response.close();
          return;
        case Averia.error403:
          req.response
            ..statusCode = 403
            ..headers.contentType = ContentType.json
            ..write('{"status":403,"error":"Forbidden"}');
          await req.response.close();
          return;
        case Averia.normal:
          req.response
            ..statusCode = 200
            ..headers.contentType = ContentType.json
            ..write('{"data":{"query":"ohm","total":1,"cursos":[{"id":"c1","source":'
                '{"id":"c1","titulo":"Curso"}}],"lecciones":[],"conceptos":[],'
                '"faqs":[]},"success":true}');
          await req.response.close();
          return;
      }
    } catch (_) {
      // Cliente desconectado durante la avería.
    }
  }

  Future<void> parar() async {
    await _srv?.close(force: true);
    _srv = null;
  }
}

void main() {
  group('Fase 5 · Resiliencia del cliente (LuxrayApi optimizado)', () {
    late ServidorAveria srv;

    LuxrayApi apiContra(ServidorAveria s, {int reintentos = 2}) => LuxrayApi(
          baseUrl: 'http://127.0.0.1:${s._srv!.port}/api',
          client: IOClient(HttpClient()),
          cacheActivo: false,
          reintentosMax: reintentos,
          backoffBase: const Duration(milliseconds: 50),
          timeoutPeticion: const Duration(milliseconds: 600),
          timeoutTotal: const Duration(seconds: 4),
        );

    setUp(() async {
      srv = ServidorAveria(Averia.normal);
      await srv.arrancar();
    });

    tearDown(() async {
      await srv.parar();
    });

    // ── RES-01 · Pérdida de Internet durante una consulta ────────────────
    test('RES-01 · corte de conexión durante la consulta -> error controlado',
        () async {
      srv.cambiarAveria(Averia.cortar);
      final api = apiContra(srv);

      final t0 = DateTime.now();
      final r = await api.buscarDetallado('ohm');
      final ms = DateTime.now().difference(t0).inMilliseconds;

      expect(r.estado, LuxEstadoBusqueda.sinConexion,
          reason: 'el fallo de red debe clasificarse como tal');
      expect(r.hayErrorDeRed, isTrue);
      expect(r.respuesta.total, 0);
      expect(ms, lessThan(5000),
          reason: 'el fallo debe detectarse rápido, no dejar colgada la UI');
      debugPrint('  RES-01 · deteccion en $ms ms, intentos=${r.intentos}');
      api.close();
    });

    // ── RES-03 · Servidor sin respuesta -> timeout controlado ───────────
    test('RES-03 · servidor que no responde -> timeout controlado', () async {
      srv.cambiarAveria(Averia.sinResponder);
      final api = apiContra(srv);

      final t0 = DateTime.now();
      final r = await api.buscarDetallado('ohm');
      final ms = DateTime.now().difference(t0).inMilliseconds;

      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      expect(r.respuesta.total, 0);
      expect(ms, lessThan(6000),
          reason: 'el timeout debe abortar la operación dentro del presupuesto');
      debugPrint('  RES-03 · timeout y deteccion en $ms ms');
      api.close();
    });

    // ── RES-04 · Respuesta HTTP 500 ─────────────────────────────────────
    test('RES-04 · HTTP 500 -> estado de error de servidor, sin caída',
        () async {
      srv.cambiarAveria(Averia.error500);
      final api = apiContra(srv);

      final r = await api.buscarDetallado('ohm');

      expect(r.estado, LuxEstadoBusqueda.errorServidor,
          reason: 'un 5xx NO es un fallo de red: es un error del servidor');
      expect(r.httpStatus, 500);
      expect(r.respuesta.total, 0);
      // La app NO se cae: el resultado es una respuesta válida y vacía.
      expect(r.respuesta, isA<BusquedaResponse>());
      api.close();
    });

    // ── RES-05 · HTTP 401 / 403 ─────────────────────────────────────────
    test('RES-05 · HTTP 401 -> no se confunde con 403', () async {
      srv.cambiarAveria(Averia.error401);
      final api = apiContra(srv);

      final r = await api.buscarDetallado('ohm');

      expect(r.httpStatus, 401);
      expect(r.estado, isNot(LuxEstadoBusqueda.ok));
      expect(r.estado, isNot(LuxEstadoBusqueda.sinConexion),
          reason: '401/403 es un rechazo de autorización, no una caída de red');
      api.close();
    });

    test('RES-05 · HTTP 403 -> no se confunde con 401', () async {
      srv.cambiarAveria(Averia.error403);
      final api = apiContra(srv);

      final r = await api.buscarDetallado('ohm');

      expect(r.httpStatus, 403);
      expect(r.estado, isNot(LuxEstadoBusqueda.ok));
      expect(r.estado, isNot(LuxEstadoBusqueda.sinConexion));
      api.close();
    });

    // ── RES-06 · Recuperación de la conexión ────────────────────────────
    test('RES-06 · tras restaurar la conexión la búsqueda vuelve a funcionar',
        () async {
      final api = apiContra(srv);

      srv.cambiarAveria(Averia.cortar);
      final fallo = await api.buscarDetallado('ohm');
      expect(fallo.estado, LuxEstadoBusqueda.sinConexion);

      srv.cambiarAveria(Averia.normal);
      final ok = await api.buscarDetallado('ohm');
      expect(ok.estado, LuxEstadoBusqueda.ok,
          reason: 'tras la recuperación la app debe volver a funcionar');
      expect(ok.respuesta.total, 1);
      api.close();
    });

    // ── RES-08 · Respuesta tardía ───────────────────────────────────────
    test('RES-08 · respuesta tardía -> se respeta el presupuesto y no se duplica',
        () async {
      srv.cambiarAveria(Averia.lento);
      final api = apiContra(srv);

      final t0 = DateTime.now();
      final r = await api.buscarDetallado('ohm');
      final ms = DateTime.now().difference(t0).inMilliseconds;

      // La respuesta llega tarde: el presupuesto global puede cortarla.
      expect(r.estado, isNot(LuxEstadoBusqueda.ok),
          reason: 'una respuesta fuera de presupuesto no debe presentarse como correcta');
      expect(ms, lessThan(8000),
          reason: 'el presupuesto global acota la espera del usuario');
      debugPrint('  RES-08 · respuesta tardia gestionada en $ms ms, estado=${r.estado.name}');
      api.close();
    });

    // ── RES-07 (parcial) · Reintento: no duplica la operación ────────────
    test('RES-07 · el reintento NO duplica peticiones ni resultados', () async {
      srv.cambiarAveria(Averia.normal);
      final api = apiContra(srv, reintentos: 2);

      final r = await api.buscarDetallado('ohm');

      expect(r.estado, LuxEstadoBusqueda.ok);
      expect(r.intentos, 1,
          reason: 'sin avería NO debe reintentarse: cada reintento es una '
              'petición extra al servidor');
      expect(r.respuesta.total, 1);
      api.close();
    });

    test('RES-07 · ante avería, el reintento está acotado (no infinito)', () async {
      srv.cambiarAveria(Averia.cortar);
      final api = apiContra(srv, reintentos: 2);

      final r = await api.buscarDetallado('ohm');

      expect(r.intentos, lessThanOrEqualTo(3),
          reason: '1 intento + 2 reintentos, y el respaldo legacy: acotado');
      expect(r.estado, LuxEstadoBusqueda.sinConexion);
      api.close();
    });

    // ── Estabilidad global tras todas las averías ───────────────────────
    test('tras una secuencia de averías, la app sigue respondiendo', () async {
      final api = apiContra(srv);

      for (final a in [
        Averia.cortar,
        Averia.sinResponder,
        Averia.error500,
        Averia.error401,
        Averia.error403,
        Averia.lento,
      ]) {
        srv.cambiarAveria(a);
        final r = await api.buscarDetallado('ohm');
        expect(r.respuesta, isNotNull,
            reason: 'con la avería $a la app no debe romperse');
      }

      srv.cambiarAveria(Averia.normal);
      final ok = await api.buscarDetallado('ohm');
      expect(ok.estado, LuxEstadoBusqueda.ok,
          reason: 'tras seis averías distintas la app debe seguir operativa');
      api.close();
    });
  });
}
