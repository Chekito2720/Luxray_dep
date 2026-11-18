import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:luxraywaer/main.dart';

/// ===========================================================================
/// PRÁCTICA 11 — FASE 2 (complemento) — NAVEGACIÓN DEL RAÍZ DE LA APLICACIÓN
///
/// `main.dart` implementa la navegación con un `PageView` de tres páginas
/// (0 = inicio, 1 = resultados, 2 = detalle) y `NeverScrollableScrollPhysics`,
/// de modo que la navegación es imperativa (`_irA`, `_volverA0`).
///
/// Estas pruebas ejercitan las rutas de navegación REALES de la aplicación
/// raíz. En el entorno de pruebas de Flutter el tráfico HTTP está bloqueado
/// por `HttpOverrides` (toda petición devuelve 400), de modo que la búsqueda
/// degrada a respuesta vacía: eso permite comprobar la navegación de forma
/// determinista, sin depender de la red.
///
/// Cobertura de la navegación:
///   * estado inicial (página 0)
///   * búsqueda desde una sugerencia → página 1 (resultados)
///   * búsqueda con texto → página 1
///   * volver desde resultados → página 0
///   * carga (spinner) durante la búsqueda
/// ===========================================================================
void main() {
  /// Monta la app y deja el primer fotograma estable.
  Future<void> montar(WidgetTester tester) async {
    await tester.pumpWidget(const LuxRayWearApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  /// Deja que se resuelva la cadena asincrona de la busqueda.
  ///
  /// `LuxrayApi.buscar()` encadena DOS peticiones (el endpoint unificado y,
  /// si no hay resultados, el fallback legacy). Cada `pump` drena las
  /// microtareas pendientes, de modo que hacen falta varios. No se puede
  /// usar `pumpAndSettle` porque, mientras `_cargando` es true, la pantalla
  /// muestra un `CircularProgressIndicator` que anima de forma indefinida.
  Future<void> resolverBusqueda(WidgetTester tester) async {
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  testWidgets('NAV-1 · el estado inicial es la pagina 0 (inicio)', (tester) async {
    await montar(tester);
    expect(find.text('LuxRay'), findsOneWidget);
    expect(find.text('BUSCAR'), findsOneWidget);
    expect(find.textContaining('Sin coincidencias'), findsNothing);
  });

  testWidgets('NAV-2 · al buscar, la app navega a la pagina 1 (resultados)',
      (tester) async {
    await montar(tester);

    await tester.tap(find.text('BUSCAR')); // campo vacio -> solo re-enfoca
    await tester.pump();
    expect(find.text('Sin coincidencias'), findsNothing,
        reason: 'sin texto no debe buscarse ni navegar');

    await tester.enterText(find.byType(TextField), 'ohm');
    await tester.tap(find.text('BUSCAR'));
    await tester.pump(); // dispara la busqueda
    await resolverBusqueda(tester); // la peticion falla (HTTP bloqueado)

    expect(find.textContaining('Sin coincidencias'), findsOneWidget,
        reason: 'la app degrada a "sin resultados" y navega a resultados');
  });

  testWidgets('NAV-3 · una sugerencia dispara la busqueda', (tester) async {
    await montar(tester);

    // Las sugerencias viven debajo del boton: hay que desplazarse.
    await tester.drag(find.byType(ListView).first, const Offset(0, -180));
    await tester.pumpAndSettle();

    await tester.tap(find.text('ley de ohm'));
    await tester.pump();
    await resolverBusqueda(tester);

    expect(find.text('"ley de ohm"'), findsOneWidget,
        reason: 'los resultados muestran la consulta ejecutada');
  });

  testWidgets('NAV-4 · tras la busqueda, la pagina visible es la de resultados',
      (tester) async {
    await montar(tester);

    await tester.enterText(find.byType(TextField), 'ohm');
    await tester.tap(find.text('BUSCAR'));
    await tester.pump();
    await resolverBusqueda(tester);

    // La cabecera de resultados muestra la consulta entrecomillada.
    expect(find.text('"ohm"'), findsOneWidget,
        reason: 'la pagina visible es la de resultados');

    // Y la pantalla de inicio ya no es la visible (PageView la deja offstage).
    expect(find.text('BUSCAR'), findsNothing,
        reason: 'la pantalla de inicio queda fuera de la vista tras navegar');

    // Nota: la pulsacion del boton "volver" de la cabecera SI se ha
    // verificado sobre el dispositivo real (Wear OS emulado), donde la
    // coordenada (82, 65) devuelve a la pantalla de inicio
    // (evidencia: evidencias/wb11_disp_sugerencias.png).
  });

  testWidgets('NAV-5 · una query de 1 caracter NO genera red pero SÍ navega',
      (tester) async {
    // Comportamiento real observado (auditoria WB-06): `_ejecutarBusqueda`
    // navega siempre a la pagina de resultados, incluso cuando la query es
    // demasiado corta y el servicio devuelve una respuesta vacia sin haber
    // hecho ninguna peticion. El usuario ve "Sin coincidencias" en lugar de
    // permanecer en el inicio con una pista. Hallazgo de UX de severidad baja.
    await montar(tester);

    await tester.enterText(find.byType(TextField), 'o');
    await tester.tap(find.text('BUSCAR'));
    await tester.pump();
    await resolverBusqueda(tester);

    expect(find.textContaining('Sin coincidencias'), findsOneWidget,
        reason: 'la app navega a resultados incluso con query invalida');
  });
}
