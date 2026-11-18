import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/screens/detalle_concepto_screen.dart';
import 'package:luxraywaer/screens/resultados_screen.dart';

/// ===========================================================================
/// PRÁCTICA 11 — FASE 2 (complemento) — PANTALLA DE DETALLE Y NAVEGACIÓN
///
/// Cubre la lógica de presentación del detalle de concepto / FAQ
/// (`DetalleConceptoScreen`), que decide título, cuerpo, chip y color del
/// badge a partir de qué dato se le pase, y las rutas de navegación de
/// `ResultadosScreen` (abrir concepto, abrir FAQ, volver).
/// ===========================================================================
void main() {
  ConceptoHit concepto({
    String termino = 'Resistencia eléctrica',
    String definicion = 'Oposición al paso de la corriente.',
    List<String> sinonimos = const ['ohm', 'impedancia'],
    List<String> temas = const ['ley de ohm'],
    String snippet = 'resumen del concepto',
  }) =>
      ConceptoHit(
        id: 'c1',
        termino: termino,
        definicion: definicion,
        sinonimos: sinonimos,
        temas: temas,
        snippet: snippet,
      );

  FaqHit faq({String categoria = 'cursos', String snippet = 'resumen faq'}) => FaqHit(
        id: 'g1',
        pregunta: '¿Necesito conocimientos previos?',
        respuesta: 'No, parte de cero.',
        categoria: categoria,
        snippet: snippet,
      );

  group('DetalleConceptoScreen · concepto', () {
    testWidgets('muestra termino, definicion, sinonimos y temas', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(concepto: concepto(), onVolver: () {}),
      ));

      expect(find.text('Resistencia eléctrica'), findsOneWidget);
      expect(find.text('Oposición al paso de la corriente.'), findsOneWidget);
      expect(find.text('Sinónimos'), findsOneWidget);
      expect(find.text('ohm'), findsOneWidget);
      expect(find.text('impedancia'), findsOneWidget);
      expect(find.text('Temas'), findsOneWidget);
      expect(find.text('#ley de ohm'), findsOneWidget);
    });

    testWidgets('la cabecera indica CONCEPTO', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(concepto: concepto(), onVolver: () {}),
      ));
      expect(find.text('CONCEPTO'), findsOneWidget);
    });

    testWidgets('el chip muestra el primer tema y el boton vuelve', (tester) async {
      var volvio = false;
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(
          concepto: concepto(),
          onVolver: () => volvio = true,
        ),
      ));

      expect(find.text('ley de ohm'), findsOneWidget,
          reason: 'el chip usa el primer tema del concepto');

      await tester.tap(find.byIcon(Icons.arrow_back));
      expect(volvio, isTrue);
    });

    testWidgets('sin sinonimos ni temas no se muestran esas secciones',
        (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(
          concepto: concepto(sinonimos: const [], temas: const []),
          onVolver: () {},
        ),
      ));

      expect(find.text('Sinónimos'), findsNothing);
      expect(find.text('Temas'), findsNothing);
      expect(find.text('Resistencia eléctrica'), findsOneWidget);
    });

    testWidgets('sin snippet no se muestra el bloque resaltado', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(
          concepto: concepto(snippet: ''),
          onVolver: () {},
        ),
      ));

      expect(find.text('resumen del concepto'), findsNothing);
      expect(find.text('Oposición al paso de la corriente.'), findsOneWidget);
    });

    testWidgets('el snippet se recorta a 100 caracteres', (tester) async {
      final largo = 'z' * 150;
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(
          concepto: concepto(snippet: largo),
          onVolver: () {},
        ),
      ));

      expect(find.text('${'z' * 100}…'), findsOneWidget);
    });
  });

  group('DetalleConceptoScreen · FAQ', () {
    testWidgets('muestra pregunta, respuesta y categoria', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(faq: faq(), onVolver: () {}),
      ));

      expect(find.text('FAQ'), findsOneWidget);
      expect(find.text('¿Necesito conocimientos previos?'), findsOneWidget);
      expect(find.text('No, parte de cero.'), findsOneWidget);
      expect(find.text('cursos'), findsOneWidget);
    });

    testWidgets('la categoria determina el color del badge', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(faq: faq(categoria: 'pagos'), onVolver: () {}),
      ));
      expect(find.text('pagos'), findsOneWidget);
    });

    testWidgets('sin snippet no se muestra el bloque resaltado', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DetalleConceptoScreen(faq: faq(snippet: ''), onVolver: () {}),
      ));
      expect(find.text('resumen faq'), findsNothing);
    });
  });

  group('ResultadosScreen · rutas de navegacion', () {
    BusquedaResponse conResultados() => BusquedaResponse(
          query: 'ohm',
          total: 2,
          page: 0,
          size: 10,
          cursos: const [],
          lecciones: const [],
          conceptos: [concepto()],
          faqs: [faq()],
        );

    testWidgets('pulsar un concepto invoca onAbrirConcepto', (tester) async {
      ConceptoHit? abierto;
      await tester.pumpWidget(MaterialApp(
        home: ResultadosScreen(
          query: 'ohm',
          resultado: conResultados(),
          onVolver: () {},
          onAbrirConcepto: (c) => abierto = c,
          onAbrirFaq: (_) {},
        ),
      ));

      await tester.tap(find.text('Resistencia eléctrica'));
      expect(abierto, isNotNull);
      expect(abierto!.termino, 'Resistencia eléctrica');
    });

    testWidgets('pulsar una FAQ invoca onAbrirFaq', (tester) async {
      FaqHit? abierta;
      await tester.pumpWidget(MaterialApp(
        home: ResultadosScreen(
          query: 'ohm',
          resultado: conResultados(),
          onVolver: () {},
          onAbrirConcepto: (_) {},
          onAbrirFaq: (f) => abierta = f,
        ),
      ));

      await tester.tap(find.text('¿Necesito conocimientos previos?'));
      expect(abierta, isNotNull);
      expect(abierta!.categoria, 'cursos');
    });

    testWidgets('el boton de volver invoca onVolver', (tester) async {
      var volvio = false;
      await tester.pumpWidget(MaterialApp(
        home: ResultadosScreen(
          query: 'ohm',
          resultado: conResultados(),
          onVolver: () => volvio = true,
          onAbrirConcepto: (_) {},
          onAbrirFaq: (_) {},
        ),
      ));

      await tester.tap(find.byIcon(Icons.arrow_back));
      expect(volvio, isTrue);
    });

    testWidgets('el encabezado muestra la consulta', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: ResultadosScreen(
          query: 'ley de ohm',
          resultado: conResultados(),
          onVolver: () {},
          onAbrirConcepto: (_) {},
          onAbrirFaq: (_) {},
        ),
      ));
      expect(find.text('"ley de ohm"'), findsOneWidget);
    });

    testWidgets('un grupo sin resultados no se muestra', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: ResultadosScreen(
          query: 'ohm',
          resultado: BusquedaResponse(
            query: 'ohm',
            total: 1,
            page: 0,
            size: 10,
            cursos: const [],
            lecciones: const [],
            conceptos: const [],
            faqs: const [],
          ),
          onVolver: () {},
          onAbrirConcepto: (_) {},
          onAbrirFaq: (_) {},
        ),
      ));

      expect(find.textContaining('Cursos'), findsNothing);
      expect(find.textContaining('Lecciones'), findsNothing);
      expect(find.textContaining('Conceptos'), findsNothing);
      expect(find.textContaining('FAQ'), findsNothing);
    });
  });
}
