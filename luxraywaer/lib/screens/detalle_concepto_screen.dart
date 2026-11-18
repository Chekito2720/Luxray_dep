import 'package:flutter/material.dart';

import '../models/busqueda.dart';
import '../theme/wear_theme.dart';
import '../widgets/circular_safe_area.dart';

/// Detalle para un concepto del glosario eléctrico o un FAQ.
/// Compactado a ~180dp útiles en pantalla redonda Wear OS Large Round.
class DetalleConceptoScreen extends StatelessWidget {
  const DetalleConceptoScreen({
    super.key,
    this.concepto,
    this.faq,
    required this.onVolver,
  }) : assert(concepto != null || faq != null, 'Se requiere al menos un concepto o faq');

  final ConceptoHit? concepto;
  final FaqHit? faq;
  final VoidCallback onVolver;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final text = theme.textTheme;

    final titulo  = concepto?.termino  ?? faq?.pregunta  ?? '';
    final cuerpo  = concepto?.definicion ?? faq?.respuesta ?? '';
    final snippet = concepto?.snippet ?? faq?.snippet ?? '';
    final chip    = concepto?.temas.firstOrNull ?? faq?.categoria ?? '';

    final LuxBadgeKind kind;
    if (concepto != null) {
      kind = LuxBadgeKind.intermedio;
    } else if (faq != null) {
      kind = luxBadgeFromCategoriaFaq(faq!.categoria);
    } else {
      kind = LuxBadgeKind.blue;
    }

    return WearScaffold(
      body: CircularSafeArea(
        child: ListView(
          padding: const EdgeInsets.only(bottom: 16),
          shrinkWrap: true,
          children: [
            _Header(onVolver: onVolver, label: concepto != null ? 'Concepto' : 'FAQ', text: text),
            if (chip.isNotEmpty) ...[
              Center(child: LuxBadge(chip, kind: kind)),
              const SizedBox(height: 8),
            ] else
              const SizedBox(height: 4),
            Text(
              titulo,
              textAlign: TextAlign.center,
              style: text.titleMedium?.copyWith(fontSize: 13),
            ),
            if (snippet.isNotEmpty) ...[
              const SizedBox(height: 6),
              _Snippet(snippet),
            ],
            const SizedBox(height: 8),
            Text(cuerpo, style: text.bodyMedium?.copyWith(fontSize: 11, height: 1.45)),
            if (concepto != null) ...[
              if (concepto!.sinonimos.isNotEmpty) ...[
                const SizedBox(height: 10),
                Text('Sinónimos', style: text.bodySmall?.copyWith(color: LuxColors.yellowBolt, fontSize: 9)),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  alignment: WrapAlignment.center,
                  children: [
                    for (final s in concepto!.sinonimos)
                      OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size(0, 24),
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                        onPressed: () {},
                        child: Text(s, style: const TextStyle(fontSize: 9)),
                      ),
                  ],
                ),
              ],
              if (concepto!.temas.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text('Temas', style: text.bodySmall?.copyWith(color: LuxColors.yellowBolt, fontSize: 9)),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  alignment: WrapAlignment.center,
                  children: [
                    for (final t in concepto!.temas)
                      Text('#$t', style: const TextStyle(fontSize: 9, color: LuxColors.yellowBolt)),
                  ],
                ),
              ],
            ],
            const SizedBox(height: 16),
            const _CompanionNote(),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.onVolver, required this.label, required this.text});
  final VoidCallback onVolver;
  final String label;
  final TextTheme text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 28,
      child: Row(
        children: [
          IconButton(
            visualDensity: VisualDensity.compact,
            iconSize: 14,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 24, minHeight: 24),
            icon: const Icon(Icons.arrow_back, color: LuxColors.blueLight, size: 14),
            onPressed: onVolver,
          ),
          const SizedBox(width: 4),
          const Icon(Icons.bolt, color: LuxColors.yellowBolt, size: 11),
          const SizedBox(width: 4),
          Expanded(
            child: Text(
              label.toUpperCase(),
              style: text.bodySmall?.copyWith(color: LuxColors.blueLight, fontSize: 9, letterSpacing: 0.7),
            ),
          ),
        ],
      ),
    );
  }
}

class _Snippet extends StatelessWidget {
  const _Snippet(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    final t = text.length > 100 ? '${text.substring(0, 100)}…' : text;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1505),
        border: const Border(left: BorderSide(color: LuxColors.yellowBolt, width: 2)),
        borderRadius: BorderRadius.circular(LuxRadii.sm),
      ),
      child: Text(
        t,
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 10, color: Color(0xFFFFE176), height: 1.4),
      ),
    );
  }
}

class _CompanionNote extends StatelessWidget {
  const _CompanionNote();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFF0B1525),
        borderRadius: BorderRadius.circular(LuxRadii.md),
        border: Border.all(color: LuxColors.cardBorder),
      ),
      child: const Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.phone_android, size: 10, color: LuxColors.blueLight),
          SizedBox(width: 6),
          Expanded(
            child: Text(
              'Para el curso completo en LuxRay, usa tu móvil companion.',
              textAlign: TextAlign.left,
              style: TextStyle(fontSize: 8, color: Colors.white54, height: 1.3),
            ),
          ),
        ],
      ),
    );
  }
}
