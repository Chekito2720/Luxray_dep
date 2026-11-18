import 'package:flutter/material.dart';

/// Padding que respeta el área útil de una pantalla redonda Wear OS.
///
/// En un círculo de diámetro D (en dp), el borde curvo corta contenido
/// rectangular por los cuatro esquinas. El rectángulo inscrito
/// (área 100% visible) es D×D centrado, pero el contenido cercano a los
/// bordes laterales se recorta más que el del top/bottom. Por eso este
/// padding aplica:
///   - padding lateral X (donde el círculo curva más rápido)
///   - padding vertical Y (donde el círculo curva menos)
///
/// Para 454px @ 320dpi = 227dp de diámetro, los valores por defecto son:
///   - horizontal 24dp
///   - vertical   18dp
///
/// Eso deja un ancho útil de ~180dp y un alto útil de ~190dp, lo cual
/// es justo para Wear OS Large Round.
class CircularSafeArea extends StatelessWidget {
  const CircularSafeArea({
    super.key,
    required this.child,
    this.horizontal = 22,
    this.vertical = 18,
    this.keepBottomGap = 24,
  });

  final Widget child;
  final double horizontal;
  final double vertical;
  /// Gap extra al fondo del scroll para que el último elemento no se quede
  /// oculto por el borde curvo inferior.
  final double keepBottomGap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: horizontal, vertical: vertical),
      child: child,
    );
  }
}

/// Scaffold específico para Wear OS Large Round.
/// Envuelve [Scaffold] con [CircularSafeArea] y color de fondo LuxRay.
class WearScaffold extends StatelessWidget {
  const WearScaffold({
    super.key,
    required this.body,
    this.backgroundColor,
  });

  final Widget body;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final bg = backgroundColor ?? Theme.of(context).scaffoldBackgroundColor;
    return Scaffold(
      backgroundColor: bg,
      // SafeArea nativo removido: en pantalla redonda, aplicamos padding
      // simétrico nosotros mismos para maximizar área útil sin tocar bordes.
      body: body,
    );
  }
}

/// Medida utilitaria: ¿la pantalla actual es apaisada o redonda?
/// Útil para decidir tamaños de fuente o número de chips por fila.
bool isWearRound(BuildContext context) {
  final shortestSide = MediaQuery.of(context).size.shortestSide;
  return shortestSide < 240; // 227dp para Wear OS Large Round
}
