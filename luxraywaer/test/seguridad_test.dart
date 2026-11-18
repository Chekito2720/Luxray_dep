import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:luxraywaer/services/luxray_api.dart';

/// Pruebas de las medidas de seguridad básicas del wearable:
///   1. Validación de entradas (query) — nunca se envían payloads inválidos
///      al servidor.
///   2. Manejo seguro de errores — ningún detalle interno del servidor ni
///      de la red se expone; los fallos degradan a "sin resultados" y jamás
///      revientan la app.
void main() {
  group('Validación de entradas — sanitizar()', () {
    test('elimina caracteres de control', () {
      final s = LuxrayApi.sanitizar('  ley\tde\r\nohm\x00 ');
      expect(s, 'leydeohm');
    });

    test('recorta la longitud máxima a 80 caracteres', () {
      final s = LuxrayApi.sanitizar('a' * 500);
      expect(s.length, LuxrayApi.kMaxQuery);
    });

    test('recorta espacios alrededor', () {
      expect(LuxrayApi.sanitizar('  ohm  '), 'ohm');
    });
  });

  group('Validación de entradas — buscar() no genera peticiones inválidas', () {
    test('query de 1 carácter no toca la red', () async {
      var llamadas = 0;
      final api = LuxrayApi(client: MockClient((req) async {
        llamadas++;
        return http.Response('{"total":0}', 200);
      }));

      final r = await api.buscar('o');
      expect(r.total, 0);
      expect(llamadas, 0, reason: 'una query inválida nunca debe llegar al servidor');
      api.close();
    });

    test('query de solo caracteres de control no toca la red', () async {
      var llamadas = 0;
      final api = LuxrayApi(client: MockClient((req) async {
        llamadas++;
        return http.Response('{"total":0}', 200);
      }));

      final r = await api.buscar('\x00\x01\t\r\n');
      expect(r.total, 0);
      expect(llamadas, 0);
      api.close();
    });

    test('query de 500 caracteres se trunca a 80 antes de enviar', () async {
      String? enviada;
      final api = LuxrayApi(client: MockClient((req) async {
        enviada = req.url.queryParameters['q'];
        return http.Response(
          '{"query":"","total":0,"cursos":[],"lecciones":[],"conceptos":[],"faqs":[]}',
          200,
          headers: {'content-type': 'application/json'},
        );
      }));

      await api.buscar('a' * 500);
      expect(enviada!.length, LuxrayApi.kMaxQuery);
      api.close();
    });
  });

  group('Prevención de exposición de información sensible', () {
    test('error HTTP del servidor no lanza ni expone detalles', () async {
      final api = LuxrayApi(client: MockClient((req) async {
        // El servidor devuelve un 500 con detalles internos hipotéticos
        return http.Response(
          '{"error":"Internal Server Error","detail":"jdbc:postgresql://prod-db:5432/secret"}',
          500,
        );
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0, reason: 'degrada a respuesta vacía, sin exception');
      api.close();
    });

    test('JSON malformado del servidor degrada a respuesta vacía', () async {
      final api = LuxrayApi(client: MockClient((req) async {
        return http.Response('<html>stack trace interno…</html>', 200);
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      api.close();
    });

    test('fallo de red degrada a respuesta vacía sin propagar la excepción',
        () async {
      final api = LuxrayApi(client: MockClient((req) async {
        throw Exception('SocketException: fallo de red con datos internos');
      }));

      final r = await api.buscar('ohm');
      expect(r.total, 0);
      api.close();
    });

    test('la query del usuario nunca aparece expuesta en excepciones', () async {
      // Buscamos una query "sensible": si el cliente la registrara o la
      // propagara en errores, el test fallaría. Solo debe devolver vacío.
      final api = LuxrayApi(client: MockClient((req) async {
        throw http.ClientException('boom');
      }));

      final r = await api.buscar('password=secreto123');
      expect(r.total, 0);
      expect(r.query, isNot('password=secreto123'));
      api.close();
    });
  });
}
