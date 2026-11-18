import 'dart:convert';
import 'package:http/http.dart' as http;

import '../models/busqueda.dart';

/// Cliente del API público de LuxRay.
///
/// El wearable Wear OS consume el endpoint unificado de búsqueda (Elasticsearch):
///
///     GET {baseUrl}/search?q={query}&page=0&size=10
///
/// Como fallback, usa el endpoint legacy del cursos-service:
///
///     GET {baseUrl}/cursos/buscar?q={query}
///
/// Sin autenticación (lecturas públicas). Tolerante a fallos de red
/// porque los relojes pierden conectividad cada pocos minutos.
class LuxrayApi {
  LuxrayApi({String? baseUrl, http.Client? client})
      : _baseUrl = baseUrl ?? _kDefaultBase,
        _client = client ?? http.Client();

  /// URL base del API Gateway de LuxRay.
  ///
  /// Por defecto apunta al host del emulador Android (10.0.2.2 = alias de
  /// 127.0.0.1 del host). Puede sobreescribirse en tiempo de compilacion
  /// para apuntar a un servidor real, a un movil fisico o a un entorno de
  /// pruebas:
  ///
  ///     flutter build apk --dart-define=LUXRAY_API_BASE=http://192.168.1.50:8080/api
  ///
  /// Sin el `--dart-define` el comportamiento es identico al anterior.
  static const String _kDefaultBase = String.fromEnvironment(
    'LUXRAY_API_BASE',
    defaultValue: 'http://10.0.2.2:8080/api',
  );

  /// Longitud máxima aceptada para la query (evita payloads abusivos).
  static const int kMaxQuery = 80;

  /// Patrón de caracteres de control (NUL, tab, CR/LF, DEL...) que se
  /// eliminan de la entrada del usuario antes de enviarla al servidor.
  static final RegExp _ctrl = RegExp(r'[\x00-\x1f\x7f]');

  final String _baseUrl;
  final http.Client _client;

  /// Validación de entrada: recorta espacios, elimina caracteres de
  /// control y limita la longitud a [kMaxQuery].
  static String sanitizar(String q) {
    final limpio = q.replaceAll(_ctrl, '').trim();
    return limpio.length > kMaxQuery ? limpio.substring(0, kMaxQuery) : limpio;
  }

  /// Llamada al buscador unificado (Elasticsearch) del sitio LuxRay.
  ///
  /// Devuelve `BusquedaResponse.empty()` si:
  ///   - la query, saneada, tiene < 2 caracteres o supera límites
  ///   - la red falla (catch-all)
  /// Nunca lanza excepciones: el wearable siempre tiene algo que mostrar.
  Future<BusquedaResponse> buscar(String q) async {
    final query = sanitizar(q);
    if (query.length < 2) return BusquedaResponse.empty();

    // 1) Intenta nuevo endpoint /search (Elasticsearch)
    final searchResponse = await _buscarEnSearch(query);
    if (searchResponse.total > 0) {
      return searchResponse;
    }

    // 2) Fallback: endpoint legacy /cursos/buscar (DB directa)
    return await _buscarEnCursosLegacy(query);
  }

  Future<BusquedaResponse> _buscarEnSearch(String query) async {
    final uri = Uri.parse('$_baseUrl/search').replace(
      queryParameters: {'q': query, 'page': '0', 'size': '10'},
    );

    try {
      final resp = await _client
          .get(uri, headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 6));

      if (resp.statusCode < 200 || resp.statusCode >= 300) {
        return BusquedaResponse.empty();
      }
      final body = json.decode(resp.body);
      if (body is Map<String, dynamic>) {
        return BusquedaResponse.fromSearchApiResponse(body);
      }
      return BusquedaResponse.empty();
    } catch (_) {
      return BusquedaResponse.empty();
    }
  }

  Future<BusquedaResponse> _buscarEnCursosLegacy(String query) async {
    final uri = Uri.parse('$_baseUrl/cursos/buscar').replace(
      queryParameters: {'q': query},
    );

    try {
      final resp = await _client
          .get(uri, headers: {'Accept': 'application/json'})
          .timeout(const Duration(seconds: 6));

      if (resp.statusCode < 200 || resp.statusCode >= 300) {
        return BusquedaResponse.empty();
      }
      final body = json.decode(resp.body);
      if (body is Map<String, dynamic>) {
        return BusquedaResponse.fromApiResponse(body);
      }
      return BusquedaResponse.empty();
    } catch (_) {
      return BusquedaResponse.empty();
    }
  }

  /// Cierra el http client subjacente (útil en tests).
  void close() => _client.close();
}
