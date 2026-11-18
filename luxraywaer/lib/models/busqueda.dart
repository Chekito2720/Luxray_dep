// Modelos Dart espejo del DTO `com.luxray.search.dto.SearchResponse`
// generado por el backend del `Search Service` (Elasticsearch).
//
// Estrategia: fromJson/toJson tolerante a campos null para que
// el wearable no se rompa si el backend evoluciona.
//
// El search-service envuelve cada hit en `SearchHitDTO` con campos:
//   - id, score, source (el hit real), highlight
// Este modelo extrae el `source` para mantener compatibilidad con la UI.

class BusquedaResponse {
  final String query;
  final int total;
  final int page;
  final int size;
  final List<CursoHit> cursos;
  final List<LeccionHit> lecciones;
  final List<ConceptoHit> conceptos;
  final List<FaqHit> faqs;

  BusquedaResponse({
    required this.query,
    required this.total,
    required this.page,
    required this.size,
    required this.cursos,
    required this.lecciones,
    required this.conceptos,
    required this.faqs,
  });

  factory BusquedaResponse.fromJson(Map<String, dynamic> json) {
    return BusquedaResponse(
      query: (json['query'] ?? '') as String,
      total: (json['total'] ?? 0) as int,
      page: (json['page'] ?? 0) as int,
      size: (json['size'] ?? 10) as int,
      cursos: _extractSources(json['cursos'], CursoHit.fromJson),
      lecciones: _extractSources(json['lecciones'], LeccionHit.fromJson),
      conceptos: _extractSources(json['conceptos'], ConceptoHit.fromJson),
      faqs: _extractSources(json['faqs'], FaqHit.fromJson),
    );
  }

  /// API del search-service envuelve en `ApiResponse` con campo `data`.
  /// Cada hit viene como `SearchHitDTO` con estructura: {id, score, source, highlight}
  factory BusquedaResponse.fromSearchApiResponse(Map<String, dynamic> json) {
    final data = json['data'];
    if (data is Map<String, dynamic>) return BusquedaResponse.fromJson(data);
    return BusquedaResponse.empty();
  }

  /// Mantiene compatibilidad con el endpoint legacy `/cursos/buscar`
  factory BusquedaResponse.fromApiResponse(Map<String, dynamic> json) {
    final data = json['data'];
    if (data is Map<String, dynamic>) {
      // Formato legacy: hits directos sin wrapper SearchHitDTO
      return BusquedaResponse(
        query: (data['query'] ?? '') as String,
        total: (data['total'] ?? 0) as int,
        page: 0,
        size: 10,
        cursos: _list(data['cursos'], CursoHit.fromJson),
        lecciones: _list(data['lecciones'], LeccionHit.fromJson),
        conceptos: _list(data['conceptos'], ConceptoHit.fromJson),
        faqs: _list(data['faqs'], FaqHit.fromJson),
      );
    }
    return BusquedaResponse.empty();
  }

  factory BusquedaResponse.empty() => BusquedaResponse(
        query: '',
        total: 0,
        page: 0,
        size: 10,
        cursos: [],
        lecciones: [],
        conceptos: [],
        faqs: [],
      );

  static List<T> _list<T>(Object? raw, T Function(Map<String, dynamic>) fromJson) {
    if (raw is! List) return [];
    return raw
        .whereType<Map<String, dynamic>>()
        .map(fromJson)
        .toList(growable: false);
  }

  /// Extrae el campo `source` de cada `SearchHitDTO` (formato nuevo search-service)
  static List<T> _extractSources<T>(Object? raw, T Function(Map<String, dynamic>) fromJson) {
    if (raw is! List) return [];
    return raw
        .whereType<Map<String, dynamic>>()
        .map((hit) => hit['source'] as Map<String, dynamic>?)
        .whereType<Map<String, dynamic>>()
        .map(fromJson)
        .toList(growable: false);
  }
}

class CursoHit {
  final String id;
  final String titulo;
  final String descripcion;
  final String instructor;
  final String nivel;
  final String icon;
  final String color;
  final int semanas;
  final int lecciones;
  final bool publicado;
  final String snippet;

  CursoHit({
    required this.id,
    required this.titulo,
    required this.descripcion,
    required this.instructor,
    required this.nivel,
    required this.icon,
    required this.color,
    required this.semanas,
    required this.lecciones,
    required this.publicado,
    this.snippet = '',
  });

  /// Nivel en minúsculas para compatibilidad con badges legacy (basico/intermedio/avanzado)
  String get nivelKey => nivel.toLowerCase();

  factory CursoHit.fromJson(Map<String, dynamic> j) => CursoHit(
        id: (j['id'] ?? '') as String,
        titulo: (j['titulo'] ?? '') as String,
        descripcion: (j['descripcion'] ?? '') as String,
        instructor: (j['instructor'] ?? '') as String,
        nivel: (j['nivel'] ?? '') as String,
        icon: (j['icon'] ?? '') as String,
        color: (j['color'] ?? '') as String,
        semanas: (j['semanas'] ?? 0) as int,
        lecciones: (j['lecciones'] ?? 0) as int,
        publicado: (j['publicado'] ?? false) as bool,
        snippet: (j['snippet'] ?? '') as String,
      );
}

class LeccionHit {
  final String id;
  final String cursoId;
  final String cursoTitulo;
  final String titulo;
  final String descripcion;
  final String seccionTitulo;
  final String? tipo;
  final String duracion;
  final String snippet;

  LeccionHit({
    required this.id,
    required this.cursoId,
    required this.cursoTitulo,
    required this.titulo,
    required this.descripcion,
    required this.seccionTitulo,
    this.tipo,
    required this.duracion,
    this.snippet = '',
  });

  factory LeccionHit.fromJson(Map<String, dynamic> j) => LeccionHit(
        id: (j['id'] ?? '') as String,
        cursoId: (j['cursoId'] ?? '') as String,
        cursoTitulo: (j['cursoTitulo'] ?? j['cursoId'] ?? '') as String,
        titulo: (j['titulo'] ?? '') as String,
        descripcion: (j['descripcion'] ?? '') as String,
        seccionTitulo: (j['seccionTitulo'] ?? '') as String,
        tipo: j['tipo'] as String?,
        duracion: (j['duracion'] ?? '') as String,
        snippet: (j['snippet'] ?? '') as String,
      );
}

class ConceptoHit {
  final String id;
  final String termino;
  final String definicion;
  final List<String> sinonimos;
  final List<String> temas;
  final String snippet;

  ConceptoHit({
    required this.id,
    required this.termino,
    required this.definicion,
    required this.sinonimos,
    required this.temas,
    this.snippet = '',
  });

  factory ConceptoHit.fromJson(Map<String, dynamic> j) => ConceptoHit(
        id: (j['id'] ?? '') as String,
        termino: (j['termino'] ?? '') as String,
        definicion: (j['definicion'] ?? '') as String,
        sinonimos: _strList(j['sinonimos']),
        temas: _strList(j['temas']),
        snippet: (j['snippet'] ?? '') as String,
      );

  static List<String> _strList(Object? raw) =>
      raw is List ? raw.whereType<String>().toList(growable: false) : const [];
}

class FaqHit {
  final String id;
  final String pregunta;
  final String respuesta;
  final String categoria;
  final String snippet;

  FaqHit({
    required this.id,
    required this.pregunta,
    required this.respuesta,
    required this.categoria,
    this.snippet = '',
  });

  factory FaqHit.fromJson(Map<String, dynamic> j) => FaqHit(
        id: (j['id'] ?? '') as String,
        pregunta: (j['pregunta'] ?? '') as String,
        respuesta: (j['respuesta'] ?? '') as String,
        categoria: (j['categoria'] ?? '') as String,
        snippet: (j['snippet'] ?? '') as String,
      );
}
