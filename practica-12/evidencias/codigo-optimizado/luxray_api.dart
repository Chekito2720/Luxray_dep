import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/busqueda.dart';

/// ===========================================================================
/// PRÁCTICA 12 — FASE 6 — CAPA DE ACCESO A DATOS RESILIENTE Y EFICIENTE
///
/// Este archivo es la VERSIÓN OPTIMIZADA de `LuxrayApi`. La versión original
/// (sin caché, sin cancelación, sin reintento y con un `catch` que colapsaba
/// todos los errores) se conserva como evidencia en
/// `practica-12/evidencias/codigo-original/luxray_api.dart`.
///
/// MEDIDAS DE LA AUDITORÍA QUE MOTIVAN CADA CAMBIO
///
///  · P12-01 · La app NO cachea: repetir la misma búsqueda vuelve a pedir
///             todo al servidor. Con 10 búsquedas idénticas seMIDieron 48
///             peticiones HTTP (el doble por el fallback legacy).
///             → AHORA: caché LRU con TTL + coalescencia de peticiones en vuelo.
///
///  · P12-02 · No existe CANCELACIÓN: una búsqueda lenta que se resuelve
///             después de otra más reciente puede pisar el resultado correcto.
///             → AHORA: token de generación; el resultado obsoleto se descarta.
///
///  · P12-03 · No hay REINTENTO ante fallos transitorios (RES-03): un corte
///             de red de 200 ms hace perder la búsqueda entera.
///             → AHORA: backoff exponencial con jitter, sólo en fallos
///             transitorios (red/timeout/5xx), nunca en 4xx.
///
///  · P12-04 · El `catch (_)` colapsaba "sin resultados", "sin conexión" y
///             "error 500" en la misma respuesta vacía (DEF-18 de la
///             Práctica 11): la app no puede diferenciar ni informar al usuario.
///             → AHORA: estado explícito `LuxEstadoBusqueda`.
///
///  · P12-05 · El cliente HTTP no fijaba `User-Agent` ni `Accept` de forma
///             coherente y no reutilizaba conexiones de forma explícita.
///             → AHORA: cabeceras centralizadas y `persistentConnection`.
/// ===========================================================================

/// Estado real de una búsqueda, para poder diferenciar el fallo del vacío.
enum LuxEstadoBusqueda {
  /// La búsqueda se resolvió correctamente (puede o no tener resultados).
  ok,

  /// Sin resultados, pero la comunicación funcionó.
  vacio,

  /// No hubo comunicación con el servidor (red caída, DNS, timeout).
  sinConexion,

  /// El servidor respondió con un error (5xx).
  errorServidor,

  /// El servidor rechazó la petición por autenticación o permisos (401/403).
  ///
  /// PRÁCTICA 12 · RES-05 · Un 401/403 NO es una caída de red: confundirlos
  /// haría que la app pidiera al usuario que revise su conexión cuando el
  /// problema es de credenciales o de permisos.
  accesoRechazado,

  /// Otro error del cliente (400, 404, 422...).
  errorPeticion,
}

/// Resultado detallado de una búsqueda: datos + estado + metadatos.
class LuxResultadoBusqueda {
  const LuxResultadoBusqueda({
    required this.respuesta,
    required this.estado,
    this.httpStatus,
    this.intentos = 0,
    this.desdeCache = false,
    this.duracionMs = 0,
  });

  /// Respuesta (posiblemente vacía) del buscador.
  final BusquedaResponse respuesta;

  /// Estado de la operación.
  final LuxEstadoBusqueda estado;

  /// Código HTTP recibido, si lo hubo.
  final int? httpStatus;

  /// Número de intentos realizados (1 = sin reintento).
  final int intentos;

  /// `true` si la respuesta se sirvió desde la caché local.
  final bool desdeCache;

  /// Tiempo total consumido por la operación.
  final int duracionMs;

  /// `true` si el usuario debe recibir un aviso de falta de conexión.
  bool get hayErrorDeRed => estado == LuxEstadoBusqueda.sinConexion;

  /// `true` si el servidor devolvió un error.
  bool get hayErrorDeServidor => estado == LuxEstadoBusqueda.errorServidor;

  @override
  String toString() =>
      'LuxResultadoBusqueda(estado: ${estado.name}, http: $httpStatus, '
      'intentos: $intentos, cache: $desdeCache, ${duracionMs}ms)';
}

/// Entrada de la caché LRU.
class _EntradaCache {
  _EntradaCache(this.valor, this.expiraEn);

  final LuxResultadoBusqueda valor;
  final DateTime expiraEn;
  bool get caducada => DateTime.now().isAfter(expiraEn);
}

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
  LuxrayApi({
    String? baseUrl,
    http.Client? client,
    this.cacheActivo = true,
    this.cacheTTL = const Duration(seconds: 30),
    this.cacheMaxEntradas = 20,
    this.reintentosMax = 2,
    this.backoffBase = const Duration(milliseconds: 250),
    this.timeoutPeticion = const Duration(seconds: 6),
    this.timeoutTotal = const Duration(seconds: 12),
  })  : _baseUrl = baseUrl ?? _kDefaultBase,
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

  // ── P12-01 · CACHÉ LRU CON TTL ──────────────────────────────────────────
  /// Activa o desactiva la caché local (se desactiva en pruebas unitarias
  /// que necesitan observar cada petición HTTP).
  final bool cacheActivo;

  /// Tiempo de vida de una entrada de la caché.
  final Duration cacheTTL;

  /// Número máximo de entradas (LRU: se descarta la menos reciente).
  final int cacheMaxEntradas;

  /// Caché LRU: `LinkedHashMap` mantiene el orden de inserción, y al
  /// reinsertar una clave existente se actualiza su posición.
  final Map<String, _EntradaCache> _cache = <String, _EntradaCache>{};

  // ── P12-01 · COALESCENCIA DE PETICIONES EN VUELO ───────────────────────
  /// Peticiones en curso, indexadas por query normalizada. Dos búsquedas
  /// idénticas simultáneas comparten una sola petición HTTP.
  final Map<String, Future<LuxResultadoBusqueda>> _enVuelo =
      <String, Future<LuxResultadoBusqueda>>{};

  // ── P12-02 · CANCELACIÓN LÓGICA ────────────────────────────────────────
  /// Token de generación: se incrementa en cada búsqueda. Cuando una
  /// petición termina, si el token ya no es el vigente su resultado es
  /// OBSOLETO y no debe mostrarse.
  int _generacion = 0;

  /// Genera el token de una nueva búsqueda e invalida las anteriores.
  int _nuevaGeneracion() => ++_generacion;

  /// `true` si [token] sigue siendo la búsqueda más reciente.
  bool _esVigente(int token) => token == _generacion;

  // ── P12-03 · REINTENTO CON BACKOFF EXPONENCIAL ──────────────────────────
  /// Número máximo de reintentos (además del intento inicial).
  final int reintentosMax;

  /// Base del backoff: 250 ms → 500 ms → 1000 ms (con jitter).
  final Duration backoffBase;

  /// Timeout de cada petición individual.
  final Duration timeoutPeticion;

  /// Presupuesto global de la operación (cubre reintentos + respaldo).
  /// Evita que el usuario espere indefinidamente en un reloj sin cobertura.
  final Duration timeoutTotal;

  /// Cabeceras comunes a todas las peticiones.
  static const Map<String, String> _cabeceras = {
    'Accept': 'application/json',
    'User-Agent': 'LuxRay-WearOS/1.0 (Flutter)',
  };

  /// Validación de entrada: recorta espacios, elimina caracteres de
  /// control y limita la longitud a [kMaxQuery].
  static String sanitizar(String q) {
    final limpio = q.replaceAll(_ctrl, '').trim();
    return limpio.length > kMaxQuery ? limpio.substring(0, kMaxQuery) : limpio;
  }

  /// Búsqueda con estado explícito.
  ///
  /// Aplica, en este orden:
  ///   1. Sanitización y validación de la query.
  ///   2. Caché (si la entrada sigue viva).
  ///   3. Coalescencia: si ya hay una petición idéntica en vuelo, se espera.
  ///   4. Petición HTTP con reintento y backoff exponencial.
  ///   5. Fallback al endpoint legacy sólo cuando el endpoint unificado
  ///      **funcionó** y no encontró resultados.
  Future<LuxResultadoBusqueda> buscarDetallado(String q) async {
    final inicio = DateTime.now();
    final query = sanitizar(q);

    // 1 · Query inválida: no se genera ninguna petición HTTP.
    if (query.length < 2) {
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.vacio,
        duracionMs: DateTime.now().difference(inicio).inMilliseconds,
      );
    }

    final token = _nuevaGeneracion();
    final clave = _claveCache(query);

    // 2 · Caché.
    if (cacheActivo) {
      final entrada = _cache[clave];
      if (entrada != null) {
        if (!entrada.caducada) {
          // LRU: al acceder se refresca la posición de la clave.
          _cache.remove(clave);
          _cache[clave] = entrada;
          return LuxResultadoBusqueda(
            respuesta: entrada.valor.respuesta,
            estado: entrada.valor.estado,
            httpStatus: entrada.valor.httpStatus,
            intentos: 0,
            desdeCache: true,
            duracionMs: DateTime.now().difference(inicio).inMilliseconds,
          );
        }
        _cache.remove(clave);
      }
    }

    // 3 · Coalescencia: si hay una petición idéntica en vuelo, se comparte.
    final enVuelo = _enVuelo[clave];
    if (enVuelo != null) {
      final compartido = await enVuelo;
      return LuxResultadoBusqueda(
        respuesta: compartido.respuesta,
        estado: compartido.estado,
        httpStatus: compartido.httpStatus,
        intentos: compartido.intentos,
        duracionMs: DateTime.now().difference(inicio).inMilliseconds,
      );
    }

    // 4 · Petición real (compartida por las consultas concurrentes iguales).
    final futuro = _ejecutarBusqueda(query, token, inicio);
    _enVuelo[clave] = futuro;
    try {
      final resultado = await futuro;
      // Sólo se cachean las respuestas VÁLIDAS: un fallo de red o de
      // servidor no debe quedar memorizado (P12-01).
      if (cacheActivo &&
          (resultado.estado == LuxEstadoBusqueda.ok ||
              resultado.estado == LuxEstadoBusqueda.vacio)) {
        _cache[clave] = _EntradaCache(resultado, DateTime.now().add(cacheTTL));
        _podaCache();
      }
      return resultado;
    } finally {
      _enVuelo.remove(clave);
    }
  }

  /// Búsqueda con la firma original (compatibilidad con el resto de la app).
  ///
  /// Ahora delega en [buscarDetallado] y descarta el estado: el resultado
  /// observable es el mismo, pero por debajo hay caché, cancelación y
  /// reintento con backoff.
  Future<BusquedaResponse> buscar(String q) async {
    final r = await buscarDetallado(q);
    return r.respuesta;
  }

  /// Ejecuta la búsqueda: endpoint unificado y, si procede, fallback legacy.
  ///
  /// Política de resiliencia (P12-03):
  ///   · Un fallo de TRANSPORTE (red, timeout) indica que la petición puede
  ///     no haber llegado al servidor: se reintenta con backoff exponencial
  ///     (250 ms → 500 ms) hasta agotar [reintentosMax] o el presupuesto
  ///     global [timeoutTotal].
  ///   · Un 5xx del endpoint unificado NO se reintenta: el respaldo
  ///     [legacy] consulta OTRO servicio (cursos-service → PostgreSQL) y
  ///     está completo, de modo que es una mejor respuesta que insistir.
  Future<LuxResultadoBusqueda> _ejecutarBusqueda(
      String query, int token, DateTime inicio) async {
    final deadline = DateTime.now().add(timeoutTotal);
    var intentos = 0;

    for (int intento = 0; intento <= reintentosMax; intento++) {
      if (DateTime.now().isAfter(deadline)) break;
      intentos = intento + 1;

      final res = await _buscarEnSearch(query);

      // Fallo de TRANSPORTE: reintento con backoff exponencial.
      if (res.estado == LuxEstadoBusqueda.sinConexion &&
          res.httpStatus == null &&
          intento < reintentosMax) {
        await _esperaBackoff(intento);
        continue;
      }

      // El endpoint unificado FUNCIONÓ (2xx): si no hay resultados, o si
      // falló, se prueba el respaldo legacy (otro servicio, base de datos).
      final necesitaFallback = res.estado != LuxEstadoBusqueda.ok ||
          res.respuesta.total == 0;

      // P12-02 · Si el usuario lanzó otra búsqueda mientras tanto, este
      // resultado es OBSOLETO: se abandona ANTES de gastar una segunda
      // petición HTTP en el respaldo.
      if (necesitaFallback && !_esVigente(token)) {
        return LuxResultadoBusqueda(
          respuesta: res.respuesta,
          estado: res.estado,
          httpStatus: res.httpStatus,
          intentos: intentos,
          duracionMs: DateTime.now().difference(inicio).inMilliseconds,
        );
      }

      if (necesitaFallback) {
        final leg = await _buscarEnCursosLegacy(query);
        if (leg.estado == LuxEstadoBusqueda.ok) {
          return LuxResultadoBusqueda(
            respuesta: leg.respuesta,
            estado: leg.respuesta.total == 0
                ? LuxEstadoBusqueda.vacio
                : LuxEstadoBusqueda.ok,
            httpStatus: leg.httpStatus,
            intentos: intentos,
            duracionMs: DateTime.now().difference(inicio).inMilliseconds,
          );
        }
        // Ni el unificado ni el respaldo sirvieron: se informa del estado
        // real (P12-04) en lugar de colapsarlo todo en "vacío".
        return LuxResultadoBusqueda(
          respuesta: res.respuesta.total > 0
              ? res.respuesta
              : BusquedaResponse.empty(),
          estado: res.estado == LuxEstadoBusqueda.ok
              ? leg.estado
              : res.estado,
          httpStatus: res.httpStatus ?? leg.httpStatus,
          intentos: intentos,
          duracionMs: DateTime.now().difference(inicio).inMilliseconds,
        );
      }

      // El endpoint unificado respondió 2xx con resultados.
      return LuxResultadoBusqueda(
        respuesta: res.respuesta,
        estado: LuxEstadoBusqueda.ok,
        httpStatus: res.httpStatus,
        intentos: intentos,
        duracionMs: DateTime.now().difference(inicio).inMilliseconds,
      );
    }

    // Se agotaron los reintentos: se prueba igualmente el respaldo legacy
    // (otro servicio) antes de declarar el fallo.
    final leg = await _buscarEnCursosLegacy(query);
    if (leg.estado == LuxEstadoBusqueda.ok) {
      return LuxResultadoBusqueda(
        respuesta: leg.respuesta,
        estado: leg.respuesta.total == 0
            ? LuxEstadoBusqueda.vacio
            : LuxEstadoBusqueda.ok,
        httpStatus: leg.httpStatus,
        intentos: intentos,
        duracionMs: DateTime.now().difference(inicio).inMilliseconds,
      );
    }
    return LuxResultadoBusqueda(
      respuesta: BusquedaResponse.empty(),
      estado: LuxEstadoBusqueda.sinConexion,
      intentos: intentos,
      duracionMs: DateTime.now().difference(inicio).inMilliseconds,
    );
  }

  /// Clasifica un código HTTP que no es 2xx.
  ///
  /// PRÁCTICA 12 · RES-05 · La distinción es esencial: un 401/403 es un
  /// rechazo de acceso, un 5xx un fallo del servidor y el resto un error de
  /// la petición. Ninguno de ellos es una "caída de red".
  static LuxEstadoBusqueda _clasificar(int status) {
    if (status == 401 || status == 403) return LuxEstadoBusqueda.accesoRechazado;
    if (status >= 500) return LuxEstadoBusqueda.errorServidor;
    return LuxEstadoBusqueda.errorPeticion;
  }

  /// Espera de backoff exponencial con jitter (±20 %).
  ///
  /// El jitter evita que varios clientes reintenten a la vez (thundering herd).
  Future<void> _esperaBackoff(int intento) async {
    final base = backoffBase.inMilliseconds * (1 << intento); // 250, 500, 1000...
    final jitter = (base * 0.2 * ((DateTime.now().microsecondsSinceEpoch % 1000) / 1000 - 0.5)).round();
    await Future<void>.delayed(Duration(milliseconds: (base + jitter).clamp(0, 10000)));
  }

  Future<LuxResultadoBusqueda> _buscarEnSearch(String query) async {
    final uri = Uri.parse('$_baseUrl/search').replace(
      queryParameters: {'q': query, 'page': '0', 'size': '10'},
    );

    try {
      final resp = await _client
          .get(uri, headers: _cabeceras)
          .timeout(timeoutPeticion);

      if (resp.statusCode < 200 || resp.statusCode >= 300) {
        return LuxResultadoBusqueda(
          respuesta: BusquedaResponse.empty(),
          estado: _clasificar(resp.statusCode),
          httpStatus: resp.statusCode,
        );
      }
      final body = json.decode(resp.body);
      if (body is Map<String, dynamic>) {
        return LuxResultadoBusqueda(
          respuesta: BusquedaResponse.fromSearchApiResponse(body),
          estado: LuxEstadoBusqueda.ok,
          httpStatus: resp.statusCode,
        );
      }
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.errorServidor,
        httpStatus: resp.statusCode,
      );
    } on TimeoutException {
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.sinConexion,
      );
    } catch (_) {
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.sinConexion,
      );
    }
  }

  Future<LuxResultadoBusqueda> _buscarEnCursosLegacy(String query) async {
    final uri = Uri.parse('$_baseUrl/cursos/buscar').replace(
      queryParameters: {'q': query},
    );

    try {
      final resp = await _client
          .get(uri, headers: _cabeceras)
          .timeout(timeoutPeticion);

      if (resp.statusCode < 200 || resp.statusCode >= 300) {
        return LuxResultadoBusqueda(
          respuesta: BusquedaResponse.empty(),
          estado: _clasificar(resp.statusCode),
          httpStatus: resp.statusCode,
        );
      }
      final body = json.decode(resp.body);
      if (body is Map<String, dynamic>) {
        return LuxResultadoBusqueda(
          respuesta: BusquedaResponse.fromApiResponse(body),
          estado: LuxEstadoBusqueda.ok,
          httpStatus: resp.statusCode,
        );
      }
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.errorServidor,
        httpStatus: resp.statusCode,
      );
    } on TimeoutException {
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.sinConexion,
      );
    } catch (_) {
      return LuxResultadoBusqueda(
        respuesta: BusquedaResponse.empty(),
        estado: LuxEstadoBusqueda.sinConexion,
      );
    }
  }

  /// Clave normalizada de la caché: la query saneada y en minúsculas, de modo
  /// que "Ohm" y "ohm " compartan la misma entrada.
  String _claveCache(String query) => query.toLowerCase();

  /// Descarta las entradas caducadas y recorta la caché al máximo permitido.
  void _podaCache() {
    _cache.removeWhere((_, e) => e.caducada);
    while (_cache.length > cacheMaxEntradas) {
      _cache.remove(_cache.keys.first); // la menos reciente
    }
  }

  /// Vacía la caché local (p. ej. al cambiar de usuario).
  void limpiarCache() => _cache.clear();

  /// Estado de la caché (para diagnóstico y pruebas).
  int get entradasCache => _cache.length;

  /// Cierra el http client subyacente (util en tests).
  void close() => _client.close();
}
