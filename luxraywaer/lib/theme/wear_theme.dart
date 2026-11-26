import 'package:flutter/material.dart';

/// Tokens LuxRay (espejo de frontend-web/src/styles.scss).
/// Adaptados a pantalla redonda Wear OS (familia Segoe UI tamaños 10–18).
class LuxColors {
  LuxColors._();

  static const Color bluePrimary = Color(0xFF1565C0);
  static const Color blueDark     = Color(0xFF0D47A1);
  static const Color blueLight    = Color(0xFF5E92F3);
  static const Color yellowBolt   = Color(0xFFFDD835);
  static const Color yellowWarm   = Color(0xFFFFB300);

  static const Color gray50  = Color(0xFFF8FAFC);
  static const Color gray100 = Color(0xFFF1F5F9);
  static const Color gray200 = Color(0xFFE2E8F0);
  static const Color gray300 = Color(0xFFCBD5E1);
  static const Color gray500 = Color(0xFF64748B);
  static const Color gray700 = Color(0xFF334155);
  static const Color gray900 = Color(0xFF0F172A);

  static const Color success = Color(0xFF16A34A);
  static const Color warning = Color(0xFFF59E0B);
  static const Color danger  = Color(0xFFDC2626);
  static const Color info    = Color(0xFF0284C7);

  /// Background OLED redondo (más profundo que gray-900 para ahorrar batería).
  static const Color wearBg = Color(0xFF060A12);
  static const Color cardBg = Color(0xFF0F1A2A);
  static const Color cardBorder = Color(0xFF1F2A36);

  /// Gradiente azul de la navbar (estilo luxray frontend).
  static const LinearGradient navbarGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [bluePrimary, blueDark],
  );
}

class LuxRadii {
  LuxRadii._();
  static const double sm = 6;
  static const double md = 10;
  static const double lg = 16;
  static const double bolt = 28;
  static const double pill = 999;
}

/// Construye el ThemeData oscuro del reloj usando la paleta LuxRay.
ThemeData luxrayWearTheme() {
  final base = ThemeData.dark(useMaterial3: true);

  return base.copyWith(
    scaffoldBackgroundColor: LuxColors.wearBg,
    colorScheme: const ColorScheme.dark(
      primary: LuxColors.bluePrimary,
      onPrimary: Colors.white,
      secondary: LuxColors.yellowBolt,
      onSecondary: LuxColors.blueDark,
      surface: LuxColors.wearBg,
      onSurface: Colors.white,
      error: LuxColors.danger,
    ),
    primaryColor: LuxColors.bluePrimary,
    appBarTheme: const AppBarTheme(
      backgroundColor: LuxColors.wearBg,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: true,
      titleTextStyle: TextStyle(
        color: Colors.white,
        fontSize: 13,
        fontWeight: FontWeight.w800,
        letterSpacing: 0.3,
      ),
    ),
    textTheme: const TextTheme(
      displaySmall: TextStyle(
        fontSize: 18, fontWeight: FontWeight.w800, color: Colors.white,
        letterSpacing: 0.2,
      ),
      titleMedium: TextStyle(fontSize: 13, fontWeight: FontWeight.w800, color: Colors.white),
      titleSmall:  TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white),
      bodyMedium:  TextStyle(fontSize: 11, color: Colors.white70, height: 1.4),
      bodySmall:   TextStyle(fontSize: 10, color: Colors.white60, height: 1.4),
      labelLarge:  TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: Colors.white,
          letterSpacing: 0.5),
    ),
    /// Botón primario: amarillo "bolt" con texto azul oscuro (igual que btn-cta-primary del front).
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: LuxColors.yellowBolt,
        foregroundColor: LuxColors.blueDark,
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        minimumSize: const Size(0, 32),
        textStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.5),
        elevation: 0,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Colors.white,
        side: const BorderSide(color: LuxColors.bluePrimary, width: 1.2),
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        minimumSize: const Size(0, 28),
        textStyle: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700),
      ),
    ),
    cardTheme: CardThemeData(
      color: LuxColors.cardBg,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(LuxRadii.lg),
        side: const BorderSide(color: LuxColors.cardBorder, width: 1),
      ),
      margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
    ),
    inputDecorationTheme: InputDecorationTheme(
      hintStyle: const TextStyle(color: Colors.white38, fontSize: 11),
      filled: true,
      fillColor: const Color(0xFF0B1525),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(LuxRadii.bolt),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(LuxRadii.bolt),
        borderSide: const BorderSide(color: LuxColors.yellowBolt, width: 1.5),
      ),
    ),
    iconTheme: const IconThemeData(color: LuxColors.yellowBolt, size: 18),
    dividerColor: LuxColors.cardBorder,
    textSelectionTheme: const TextSelectionThemeData(
      cursorColor: LuxColors.yellowBolt,
      selectionColor: LuxColors.blueDark,
    ),
  );
}

/// Snippet con match resaltado en amarillo (igual que .hit-snippet del front).
class LuxSnippet extends StatelessWidget {
  const LuxSnippet(this.text, {super.key, this.maxChars = 110});
  final String text;
  final int maxChars;

  @override
  Widget build(BuildContext context) {
    final t = text.length > maxChars ? '${text.substring(0, maxChars)}…' : text;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1505),
        border: const Border(left: BorderSide(color: LuxColors.yellowBolt, width: 2)),
        borderRadius: BorderRadius.circular(LuxRadii.sm),
      ),
      child: Text(
        t,
        style: const TextStyle(fontSize: 10, color: Color(0xFFFFE176), height: 1.4),
      ),
    );
  }
}

/// Badge del estilo de los chips de nivel del front (.badge-basico/intermedio/avanzado).
class LuxBadge extends StatelessWidget {
  const LuxBadge(this.text, {super.key, this.kind = LuxBadgeKind.blue});
  final String text;
  final LuxBadgeKind kind;

  @override
  Widget build(BuildContext context) {
    final color = switch (kind) {
      LuxBadgeKind.basico     => LuxColors.success,
      LuxBadgeKind.intermedio => LuxColors.bluePrimary,
      LuxBadgeKind.avanzado   => const Color(0xFF6A1B9A),
      LuxBadgeKind.leccion    => const Color(0xFF00897B),
      LuxBadgeKind.faq        => LuxColors.warning,
      LuxBadgeKind.blue       => LuxColors.bluePrimary,
    };
    final label = text.toLowerCase();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(LuxRadii.sm),
      ),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 8,
          fontWeight: FontWeight.w800,
          color: Colors.white,
          letterSpacing: 0.4,
        ),
      ),
    );
  }
}

enum LuxBadgeKind { basico, intermedio, avanzado, leccion, faq, blue }

/// Mapeo de nivelKey (cursos-service) → tipo de badge.
LuxBadgeKind luxBadgeFromNivelKey(String nivelKey) {
  return switch (nivelKey.toLowerCase()) {
    'basico'     => LuxBadgeKind.basico,
    'intermedio' => LuxBadgeKind.intermedio,
    'avanzado'   => LuxBadgeKind.avanzado,
    _            => LuxBadgeKind.blue,
  };
}

LuxBadgeKind luxBadgeFromTipoLeccion(String? tipo) {
  return switch (tipo?.toUpperCase()) {
    'VIDEO'    => LuxBadgeKind.leccion,
    'QUIZ'     => LuxBadgeKind.faq,
    'LECTURA'  => LuxBadgeKind.blue,
    _          => LuxBadgeKind.blue,
  };
}

LuxBadgeKind luxBadgeFromCategoriaFaq(String categoria) {
  return switch (categoria.toLowerCase()) {
    'cursos'       => LuxBadgeKind.intermedio,
    'pagos'        => LuxBadgeKind.basico,
    'certificados' => LuxBadgeKind.avanzado,
    'tecnico'      => LuxBadgeKind.leccion,
    _              => LuxBadgeKind.faq,
  };
}
