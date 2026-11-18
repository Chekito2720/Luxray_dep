import 'package:flutter/material.dart';

import '../models/busqueda.dart';
import '../services/luxray_api.dart';
import '../theme/wear_theme.dart';
import '../widgets/circular_safe_area.dart';

/// Pantalla de Resultados del wearable — adaptada a pantalla redonda ~227dp.
/// Cards compactas, headers de 24dp, badges de 8px-font, todo pensado para
/// que ningún elemento toque el borde curvo.
class ResultadosScreen extends StatelessWidget {
  const ResultadosScreen({
    super.key,
    required this.query,
    required this.resultado,
    required this.onVolver,
    required this.onAbrirConcepto,
    required this.onAbrirFaq,
    this.estado = LuxEstadoBusqueda.ok,
  });

  final String query;
  final BusquedaResponse resultado;
  final VoidCallback onVolver;
  final void Function(ConceptoHit concepto) onAbrirConcepto;
  final void Function(FaqHit faq) onAbrirFaq;

  /// PRÁCTICA 12 · P12-04 · Estado real de la búsqueda.
  ///
  /// Permite distinguir "no hay resultados" de "no hay conexión" y de
  /// "el servidor falló" (DEF-18 de la Práctica 11). Por defecto `ok` para
  /// no romper las llamadas existentes.
  final LuxEstadoBusqueda estado;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final text = theme.textTheme;
    final total = resultado.total;

    return WearScaffold(
      body: CircularSafeArea(
        child: ListView(
          padding: const EdgeInsets.only(bottom: 14),
          shrinkWrap: true,
          children: [
            _Header(query: query, total: total, onVolver: onVolver, text: text),
            const Divider(color: LuxColors.cardBorder, height: 1, indent: 12, endIndent: 12),
            if (total == 0)
              _Empty(text: text, estado: estado)
            else
              _Grupos(resultado: resultado, onAbrirConcepto: onAbrirConcepto, onAbrirFaq: onAbrirFaq),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.query, required this.total, required this.onVolver, required this.text});
  final String query;
  final int total;
  final VoidCallback onVolver;
  final TextTheme text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 30,
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
          const SizedBox(width: 2),
          Expanded(
            child: Text(
              '"$query"',
              style: text.titleSmall?.copyWith(fontSize: 11),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
            decoration: BoxDecoration(
              color: LuxColors.blueDark,
              borderRadius: BorderRadius.circular(LuxRadii.sm),
            ),
            child: Text(
              '$total',
              style: const TextStyle(fontSize: 9, color: Colors.white, fontWeight: FontWeight.w800),
            ),
          ),
        ],
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.text, this.estado = LuxEstadoBusqueda.ok});
  final TextTheme text;

  /// PRÁCTICA 12 · P12-04 · Mensaje segun el estado real de la búsqueda.
  final LuxEstadoBusqueda estado;

  @override
  Widget build(BuildContext context) {
    // Mensajes diferenciados: antes, "sin resultados" y "sin conexión"
    // mostraban exactamente lo mismo (DEF-18).
    final (IconData icono, String mensaje) = switch (estado) {
      LuxEstadoBusqueda.sinConexion => (
          Icons.wifi_off_rounded,
          'Sin conexión con el servidor.\nReintenta en unos segundos.'
        ),
      LuxEstadoBusqueda.errorServidor => (
          Icons.cloud_off_rounded,
          'El servidor no pudo responder.\nInténtalo más tarde.'
        ),
      LuxEstadoBusqueda.accesoRechazado => (
          Icons.lock_outline_rounded,
          'Sin permisos para consultar.\nInicia sesión de nuevo.'
        ),
      LuxEstadoBusqueda.errorPeticion => (
          Icons.report_gmailerrorred_rounded,
          'No se pudo procesar la consulta.\nPrueba con otra búsqueda.'
        ),
      _ => (
          Icons.bolt,
          'Sin coincidencias dentro\ndel giro eléctrico.'
        ),
    };

    return Padding(
      padding: const EdgeInsets.only(top: 30, bottom: 10),
      child: Column(
        children: [
          Icon(icono,
              color: (estado == LuxEstadoBusqueda.ok ||
                      estado == LuxEstadoBusqueda.vacio)
                  ? LuxColors.yellowBolt
                  : LuxColors.blueLight,
              size: 32),
          const SizedBox(height: 8),
          Text(
            mensaje,
            textAlign: TextAlign.center,
            style: text.bodySmall,
          ),
        ],
      ),
    );
  }
}

class _Grupos extends StatelessWidget {
  const _Grupos({required this.resultado, required this.onAbrirConcepto, required this.onAbrirFaq});
  final BusquedaResponse resultado;
  final void Function(ConceptoHit) onAbrirConcepto;
  final void Function(FaqHit) onAbrirFaq;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Grupo(
          icon: Icons.book,
          titulo: 'Cursos',
          cantidad: resultado.cursos.length,
          items: resultado.cursos
              .map((c) => _HitTile(
                    titulo: c.titulo,
                    sub: c.instructor,
                    snippet: c.snippet,
                    badge: LuxBadge(c.nivel, kind: luxBadgeFromNivelKey(c.nivelKey)),
                    onTap: () {},
                  ))
              .toList(),
        ),
        _Grupo(
          icon: Icons.play_circle_outline,
          titulo: 'Lecciones',
          cantidad: resultado.lecciones.length,
          items: resultado.lecciones
              .map((l) => _HitTile(
                    titulo: l.titulo,
                    sub: l.cursoTitulo,
                    snippet: l.snippet,
                    badge: LuxBadge(l.tipo ?? '', kind: luxBadgeFromTipoLeccion(l.tipo)),
                    onTap: () {},
                  ))
              .toList(),
        ),
        _Grupo(
          icon: Icons.bolt,
          titulo: 'Conceptos',
          cantidad: resultado.conceptos.length,
          items: resultado.conceptos
              .map((c) => _HitTile(
                    titulo: c.termino,
                    sub: c.definicion,
                    snippet: c.snippet,
                    badge: c.temas.isEmpty
                        ? const SizedBox.shrink()
                        : LuxBadge(c.temas.first, kind: LuxBadgeKind.blue),
                    onTap: () => onAbrirConcepto(c),
                  ))
              .toList(),
        ),
        _Grupo(
          icon: Icons.help_outline,
          titulo: 'FAQ',
          cantidad: resultado.faqs.length,
          items: resultado.faqs
              .map((f) => _HitTile(
                    titulo: f.pregunta,
                    sub: f.respuesta,
                    snippet: f.snippet,
                    badge: LuxBadge(f.categoria, kind: luxBadgeFromCategoriaFaq(f.categoria)),
                    onTap: () => onAbrirFaq(f),
                  ))
              .toList(),
        ),
      ],
    );
  }
}

class _Grupo extends StatelessWidget {
  const _Grupo({required this.icon, required this.titulo, required this.cantidad, required this.items});
  final IconData icon;
  final String titulo;
  final int cantidad;
  final List<_HitTile> items;

  @override
  Widget build(BuildContext context) {
    if (cantidad == 0) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 10, bottom: 4),
          child: Row(
            children: [
              Icon(icon, size: 11, color: LuxColors.yellowBolt),
              const SizedBox(width: 4),
              Text('$titulo  ·  $cantidad',
                  style: theme.textTheme.bodySmall?.copyWith(color: Colors.white70, fontSize: 9)),
            ],
          ),
        ),
        for (final it in items) it,
      ],
    );
  }
}

class _HitTile extends StatelessWidget {
  const _HitTile({
    required this.titulo,
    required this.sub,
    required this.snippet,
    required this.badge,
    required this.onTap,
  });

  final String titulo;
  final String sub;
  final String snippet;
  final Widget badge;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 3),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(LuxRadii.lg),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(8, 6, 8, 6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisSize: MainAxisSize.max,
                children: [
                  if (badge is! SizedBox) badge,
                  if (badge is! SizedBox) const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      titulo,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: text.titleSmall?.copyWith(fontSize: 11),
                    ),
                  ),
                ],
              ),
              if (snippet.isNotEmpty) ...[
                const SizedBox(height: 3),
                _Snippet(text: snippet),
              ] else if (sub.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  sub,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: text.bodySmall?.copyWith(fontSize: 9),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Snippet compacto: 2 líneas máximo, fondo amarillo suave.
class _Snippet extends StatelessWidget {
  const _Snippet({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) {
    final t = text.length > 70 ? '${text.substring(0, 70)}…' : text;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: const Color(0xFF1A1505),
        border: const Border(left: BorderSide(color: LuxColors.yellowBolt, width: 2)),
        borderRadius: BorderRadius.circular(LuxRadii.sm),
      ),
      child: Text(
        t,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 9, color: Color(0xFFFFE176), height: 1.3),
      ),
    );
  }
}
