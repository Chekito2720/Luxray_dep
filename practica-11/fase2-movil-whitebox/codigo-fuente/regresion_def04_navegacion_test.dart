import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:luxraywaer/main.dart';

/// ===========================================================================
/// PRÁCTICA 11 — FASE 6 — REPRODUCCIÓN DEL DEFECTO DEF-04
/// (la aplicación móvil NO arranca en la pantalla de inicio)
///
/// Hallazgo
/// --------
/// `main.dart`, método `_buildDetalle()` (línea ~157):
///
///     // fallback: si llegamos a página 2 sin detalle, volver a 1
///     WidgetsBinding.instance.addPostFrameCallback((_) => _irA(1));
///
/// Un `PageView` construye TODOS sus hijos desde el primer fotograma, no
/// sólo la página visible. Al arrancar, `_conceptoDetalle` y `_faqDetalle`
/// son `null`, de modo que `_buildDetalle()` entra en su rama de "fallback"
/// y encola `_irA(1)`. El resultado es que la app NUNCA muestra la pantalla
/// de inicio (índice 0): el usuario arranca directamente en la pantalla de
/// resultados con el mensaje "Sin coincidencias", sin haber escrito nada.
///
/// Evidencia en dispositivo real (Wear OS emulado, API 36):
///   * `wb11_disp_estado_inicial.png` -> arranca en la pantalla de resultados.
///   * `evidencias/fase6_defectos.md` -> ficha DEF-04.
///
/// Esta prueba falla con el código defectuoso y pasa tras la corrección
/// (guardar en `_buildDetalle()` que sólo redireccione si la página actual
/// es realmente la 2).
/// ===========================================================================
void main() {
  testWidgets('DEF-04 · al arrancar, la app debe mostrar la pantalla de INICIO',
      (tester) async {
    await tester.pumpWidget(const LuxRayWearApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    // La pantalla de inicio contiene el campo de busqueda y el boton BUSCAR
    expect(find.text('LuxRay'), findsOneWidget,
        reason: 'debe verse la cabecera de la pantalla de inicio');
    expect(find.byType(TextField), findsOneWidget,
        reason: 'debe verse el campo de busqueda de la pantalla de inicio');
    expect(find.text('BUSCAR'), findsOneWidget,
        reason: 'debe verse el boton BUSCAR de la pantalla de inicio');
  });

  testWidgets('DEF-04 · no debe aparecer "Sin coincidencias" antes de buscar',
      (tester) async {
    await tester.pumpWidget(const LuxRayWearApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.textContaining('Sin coincidencias'), findsNothing,
        reason: 'sin una busqueda previa no debe mostrarse la pantalla de resultados');
  });
}
