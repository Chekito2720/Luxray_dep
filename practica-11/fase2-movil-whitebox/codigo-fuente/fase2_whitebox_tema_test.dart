import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/theme/wear_theme.dart';
import 'package:luxraywaer/widgets/circular_safe_area.dart';

/// ===========================================================================
/// PRÁCTICA 11 — FASE 2 (complemento) — APLICACIÓN MÓVIL
///
/// Completa la cobertura de los módulos de PRESENTACIÓN y del resto de
/// modelos del wearable Wear OS (`luxraywaer`), que quedan fuera de los tres
/// métodos críticos pero contienen lógica de negocio real:
///
///   M4 — PROCESAMIENTO : `luxBadgeFromNivelKey`, `luxBadgeFromTipoLeccion`,
///                          `luxBadgeFromCategoriaFaq`  (theme/wear_theme.dart)
///   M5 — PROCESAMIENTO : factories `CursoHit/LeccionHit/ConceptoHit/FaqHit`
///                          (models/busqueda.dart)
///
/// Los metodos M4 son `switch` exhaustivos: cada rama es una condicion de
/// decision, y cada rama `_` (por defecto) es la condicion de "ninguno de los
/// casos anteriores". Se cubren TODOS los casos, incluidos los valores
/// limite (cadena vacia, null, mayusculas/minusculas).
/// ===========================================================================
void main() {
  group('M4 · luxBadgeFromNivelKey (switch de nivel)', () {
    test('caso basico', () {
      expect(luxBadgeFromNivelKey('basico'), LuxBadgeKind.basico);
    });

    test('caso intermedio', () {
      expect(luxBadgeFromNivelKey('intermedio'), LuxBadgeKind.intermedio);
    });

    test('caso avanzado', () {
      expect(luxBadgeFromNivelKey('avanzado'), LuxBadgeKind.avanzado);
    });

    test('rama por defecto (nivel desconocido)', () {
      expect(luxBadgeFromNivelKey('experto'), LuxBadgeKind.blue);
    });

    test('es insensible a mayusculas/minusculas', () {
      expect(luxBadgeFromNivelKey('BASICO'), LuxBadgeKind.basico);
      expect(luxBadgeFromNivelKey('InterMedio'), LuxBadgeKind.intermedio);
    });

    test('valor limite: cadena vacia -> rama por defecto', () {
      expect(luxBadgeFromNivelKey(''), LuxBadgeKind.blue);
    });
  });

  group('M4 · luxBadgeFromTipoLeccion (switch de tipo de leccion)', () {
    test('caso VIDEO', () {
      expect(luxBadgeFromTipoLeccion('VIDEO'), LuxBadgeKind.leccion);
    });

    test('caso QUIZ', () {
      expect(luxBadgeFromTipoLeccion('QUIZ'), LuxBadgeKind.faq);
    });

    test('caso LECTURA', () {
      expect(luxBadgeFromTipoLeccion('LECTURA'), LuxBadgeKind.blue);
    });

    test('rama por defecto con tipo desconocido', () {
      expect(luxBadgeFromTipoLeccion('PODCAST'), LuxBadgeKind.blue);
    });

    test('valor limite: null -> rama por defecto (no lanza)', () {
      expect(luxBadgeFromTipoLeccion(null), LuxBadgeKind.blue);
    });

    test('valor limite: cadena vacia -> rama por defecto', () {
      expect(luxBadgeFromTipoLeccion(''), LuxBadgeKind.blue);
    });

    test('normaliza a mayusculas antes de comparar', () {
      expect(luxBadgeFromTipoLeccion('video'), LuxBadgeKind.leccion);
      expect(luxBadgeFromTipoLeccion('quiz'), LuxBadgeKind.faq);
    });
  });

  group('M4 · luxBadgeFromCategoriaFaq (switch de categoria)', () {
    test('caso cursos', () {
      expect(luxBadgeFromCategoriaFaq('cursos'), LuxBadgeKind.intermedio);
    });

    test('caso pagos', () {
      expect(luxBadgeFromCategoriaFaq('pagos'), LuxBadgeKind.basico);
    });

    test('caso certificados', () {
      expect(luxBadgeFromCategoriaFaq('certificados'), LuxBadgeKind.avanzado);
    });

    test('caso tecnico', () {
      expect(luxBadgeFromCategoriaFaq('tecnico'), LuxBadgeKind.leccion);
    });

    test('rama por defecto (categoria desconocida)', () {
      expect(luxBadgeFromCategoriaFaq('general'), LuxBadgeKind.faq);
    });

    test('valor limite: cadena vacia -> rama por defecto', () {
      expect(luxBadgeFromCategoriaFaq(''), LuxBadgeKind.faq);
    });

    test('es insensible a mayusculas/minusculas', () {
      expect(luxBadgeFromCategoriaFaq('CURSOS'), LuxBadgeKind.intermedio);
    });
  });

  group('M5 · Factories de modelos (parseo tolerante a nulls)', () {
    test('CursoHit.fromJson con todos los campos', () {
      final c = CursoHit.fromJson({
        'id': 'c1',
        'titulo': 'Fundamentos',
        'descripcion': 'Descripcion',
        'instructor': 'Docente',
        'nivel': 'BASICO',
        'icon': 'pi-bolt',
        'color': '#1565c0',
        'semanas': 8,
        'lecciones': 20,
        'publicado': true,
        'snippet': 'resumen',
      });
      expect(c.id, 'c1');
      expect(c.titulo, 'Fundamentos');
      expect(c.instructor, 'Docente');
      expect(c.semanas, 8);
      expect(c.lecciones, 20);
      expect(c.publicado, isTrue);
      expect(c.snippet, 'resumen');
      expect(c.nivelKey, 'basico');
    });

    test('CursoHit.fromJson con campos ausentes usa los valores por defecto', () {
      final c = CursoHit.fromJson({});
      expect(c.id, '');
      expect(c.titulo, '');
      expect(c.descripcion, '');
      expect(c.instructor, '');
      expect(c.nivel, '');
      expect(c.icon, '');
      expect(c.color, '');
      expect(c.semanas, 0);
      expect(c.lecciones, 0);
      expect(c.publicado, isFalse);
      expect(c.snippet, '');
      expect(c.nivelKey, '');
    });

    test('LeccionHit.fromJson con todos los campos', () {
      final l = LeccionHit.fromJson({
        'id': 'l1',
        'cursoId': 'c1',
        'cursoTitulo': 'Electricidad basica',
        'titulo': 'Ley de Ohm',
        'descripcion': 'Descripcion',
        'seccionTitulo': 'Fundamentos',
        'tipo': 'VIDEO',
        'duracion': '12 min',
        'snippet': 'resumen',
      });
      expect(l.id, 'l1');
      expect(l.cursoId, 'c1');
      expect(l.cursoTitulo, 'Electricidad basica');
      expect(l.titulo, 'Ley de Ohm');
      expect(l.seccionTitulo, 'Fundamentos');
      expect(l.tipo, 'VIDEO');
      expect(l.duracion, '12 min');
      expect(l.snippet, 'resumen');
    });

    test('LeccionHit.fromJson cae a cursoId cuando falta cursoTitulo', () {
      final l = LeccionHit.fromJson({'id': 'l2', 'cursoId': 'c9'});
      expect(l.cursoTitulo, 'c9',
          reason: 'sin cursoTitulo se usa el cursoId como respaldo');
      expect(l.tipo, isNull);
    });

    test('LeccionHit.fromJson sin cursoId ni cursoTitulo', () {
      final l = LeccionHit.fromJson({});
      expect(l.cursoTitulo, '');
      expect(l.cursoId, '');
    });

    test('ConceptoHit.fromJson con listas de cadenas', () {
      final c = ConceptoHit.fromJson({
        'id': 'k1',
        'termino': 'Voltaje',
        'definicion': 'Diferencia de potencial',
        'sinonimos': ['tension', 'potencial'],
        'temas': ['ley de ohm'],
        'snippet': 'resumen',
      });
      expect(c.termino, 'Voltaje');
      expect(c.sinonimos, ['tension', 'potencial']);
      expect(c.temas, ['ley de ohm']);
    });

    test('FaqHit.fromJson con todos los campos', () {
      final f = FaqHit.fromJson({
        'id': 'g1',
        'pregunta': '¿Necesito conocimientos previos?',
        'respuesta': 'No, parte de cero.',
        'categoria': 'cursos',
        'snippet': 'resumen',
      });
      expect(f.id, 'g1');
      expect(f.pregunta, '¿Necesito conocimientos previos?');
      expect(f.respuesta, 'No, parte de cero.');
      expect(f.categoria, 'cursos');
      expect(f.snippet, 'resumen');
    });

    test('FaqHit.fromJson con campos ausentes', () {
      final f = FaqHit.fromJson({});
      expect(f.id, '');
      expect(f.pregunta, '');
      expect(f.respuesta, '');
      expect(f.categoria, '');
    });
  });

  group('M4 · LuxSnippet (truncado de texto)', () {
    testWidgets('no trunca si el texto cabe en maxChars', (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: LuxSnippet('texto corto')),
      ));
      expect(find.text('texto corto'), findsOneWidget);
      expect(find.textContaining('…'), findsNothing);
    });

    testWidgets('trunca y anade elipsis si supera maxChars', (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: LuxSnippet('abcdefghij', maxChars: 5)),
      ));
      expect(find.text('abcde…'), findsOneWidget);
    });

    testWidgets('valor limite: exactamente maxChars no se trunca',
        (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: LuxSnippet('abcde', maxChars: 5)),
      ));
      expect(find.text('abcde'), findsOneWidget);
    });
  });

  group('M4 · LuxBadge (render de cada variante de color)', () {
    for (final kind in LuxBadgeKind.values) {
      testWidgets('renderiza el badge ${kind.name}', (tester) async {
        await tester.pumpWidget(MaterialApp(
          home: Scaffold(body: LuxBadge('PRUEBA', kind: kind)),
        ));
        expect(find.text('prueba'), findsOneWidget,
            reason: 'el texto del badge se normaliza a minusculas');
      });
    }
  });

  group('M4 · luxrayWearTheme (construccion del ThemeData del wearable)', () {
    test('construye un ThemeData completo y usable', () {
      final theme = luxrayWearTheme();
      expect(theme, isNotNull);
      expect(theme.brightness, Brightness.dark);
      expect(theme.colorScheme, isNotNull);
      expect(theme.useMaterial3, isTrue);
    });

    testWidgets('la app se monta con el tema del wearable', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: luxrayWearTheme(),
        home: const Scaffold(body: Text('LuxRay')),
      ));
      expect(find.text('LuxRay'), findsOneWidget);
    });
  });

  group('M6 · CircularSafeArea (layout para pantalla redonda)', () {
    testWidgets('aplica el padding de pantalla redonda', (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: CircularSafeArea(child: Text('X'))),
      ));
      expect(find.text('X'), findsOneWidget);
    });

    testWidgets('isWearRound distingue pantallas de mas de 240 dp', (tester) async {
      late bool redondo;
      await tester.pumpWidget(MediaQuery(
        data: const MediaQueryData(size: Size(390, 844)),
        child: Builder(builder: (context) {
          redondo = isWearRound(context);
          return const SizedBox();
        }),
      ));
      expect(redondo, isFalse, reason: '390x844 es un movil, no un reloj');
    });

    testWidgets('isWearRound detecta la pantalla redonda del wearable',
        (tester) async {
      late bool redondo;
      await tester.pumpWidget(MediaQuery(
        data: const MediaQueryData(size: Size(192, 192)),
        child: Builder(builder: (context) {
          redondo = isWearRound(context);
          return const SizedBox();
        }),
      ));
      expect(redondo, isTrue,
          reason: 'el lado menor < 240 dp identifica un reloj redondo');
    });
  });
}
