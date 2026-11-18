import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:luxraywaer/models/busqueda.dart';
import 'package:luxraywaer/screens/home_screen.dart';
import 'package:luxraywaer/screens/resultados_screen.dart';

void main() {
  group('Modelos Busqueda', () {
    test('BusquedaResponse.fromJson parsea hits vacíos', () {
      final r = BusquedaResponse.fromJson({
        'query': 'ohm',
        'total': 0,
        'cursos': [],
        'lecciones': [],
        'conceptos': [],
        'faqs': [],
      });
      expect(r.total, 0);
      expect(r.query, 'ohm');
    });

    test('ConceptoHit.fromJson tolera nulls', () {
      final c = ConceptoHit.fromJson({'termino': 'Voltaje'});
      expect(c.termino, 'Voltaje');
      expect(c.definicion, '');
      expect(c.sinonimos, isEmpty);
      expect(c.temas, isEmpty);
    });

    test('BusquedaResponse.fromApiResponse extrae data', () {
      final r = BusquedaResponse.fromApiResponse({
        'data': {
          'query': 'PLC',
          'total': 1,
          'cursos': [],
          'lecciones': [],
          'conceptos': [
            {'termino': 'PLC', 'definicion': 'Controlador lógico programable'}
          ],
          'faqs': [],
        },
        'success': true,
      });
      expect(r.total, 1);
      expect(r.conceptos.first.termino, 'PLC');
    });
  });

  group('HomeScreen', () {
    testWidgets('render sin petición previa', (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(home: HomeScreen(onBuscar: _noopStr)),
      );
      expect(find.text('LuxRay'), findsOneWidget);
      expect(find.text('BUSCAR'), findsOneWidget);
      expect(find.byType(TextField), findsOneWidget);
    });

    testWidgets('el botón BUSCAR llama a onBuscar con texto',
        (WidgetTester tester) async {
      String? recibida;
      await tester.pumpWidget(
        MaterialApp(
          home: HomeScreen(onBuscar: (q) => recibida = q),
        ),
      );
      await tester.enterText(find.byType(TextField), 'ohm');
      await tester.tap(find.text('BUSCAR'));
      expect(recibida, 'ohm');
    });
  });

  group('ResultadosScreen', () {
    testWidgets('estado vacío muestra mensaje de sin coincidencias',
        (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: ResultadosScreen(
            query: 'noexistente',
            resultado: BusquedaResponse.empty(),
            onVolver: _noop,
            onAbrirConcepto: _noopC,
            onAbrirFaq: _noopF,
          ),
        ),
      );
      expect(find.textContaining('Sin coincidencias'), findsOneWidget);
    });

    testWidgets('muestra grupos cuando hay hits', (WidgetTester tester) async {
      final r = BusquedaResponse(
        query: 'ohm',
        total: 2,
        page: 0,
        size: 10,
        cursos: [],
        lecciones: [],
        conceptos: [
          ConceptoHit(
            id: 'c1',
            termino: 'Resistencia eléctrica',
            definicion: 'Oposición al paso de la corriente, medida en ohms.',
            sinonimos: const ['ohm', 'impedancia'],
            temas: const ['ley de ohm'],
            snippet: '',
          ),
        ],
        faqs: [
          FaqHit(
            id: 'g3',
            pregunta: '¿Necesito conocimientos previos?',
            respuesta: 'No, parte de cero.',
            categoria: 'general',
            snippet: '',
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: ResultadosScreen(
            query: 'ohm',
            resultado: r,
            onVolver: _noop,
            onAbrirConcepto: _noopC,
            onAbrirFaq: _noopF,
          ),
        ),
      );
      expect(find.text('Conceptos  ·  1'), findsOneWidget);
      expect(find.text('FAQ  ·  1'), findsOneWidget);
      expect(find.text('Resistencia eléctrica'), findsOneWidget);
    });
  });
}

void _noop() {}
void _noopStr(String _) {}
void _noopC(ConceptoHit _) {}
void _noopF(FaqHit _) {}
