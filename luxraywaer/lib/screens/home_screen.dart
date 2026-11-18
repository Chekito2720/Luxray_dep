import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/luxray_api.dart';
import '../theme/wear_theme.dart';
import '../widgets/circular_safe_area.dart';

/// Pantalla Home del wearable — espejo del navbar del front web,
/// optimizada para una pantalla redonda Wear OS de ~227dp de diámetro.
///
/// Compromisos visuales en pantalla redonda:
///   - Header compacto (sin gradiente multilinea que toque bordes)
///   - SearchBar y botón en ~155dp de ancho útil
///   - Sugerencias en Wrap limitadas a 2 por fila
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.onBuscar});

  /// Callback invocado cuando el usuario confirma la búsqueda.
  final void Function(String query) onBuscar;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _ctrl = TextEditingController();
  final FocusNode _focus = FocusNode();
  final ScrollController _scroll = ScrollController();

  /// Sugerencias del dominio eléctrico, las más típicas del giro.
  static const List<String> _sugerencias = [
    'ley de ohm',
    'cortocircuito',
    'tablero',
    'plc',
    'nom-001',
    'voltaje',
  ];

  @override
  void dispose() {
    _ctrl.dispose();
    _focus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _buscar() {
    final q = _ctrl.text.trim();
    if (q.isEmpty) {
      _focus.requestFocus();
      return;
    }
    HapticFeedback.selectionClick();
    widget.onBuscar(q);
  }

  void _buscarSugerencia(String s) {
    _ctrl.text = s;
    _buscar();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final text = theme.textTheme;

    return WearScaffold(
      body: CircularSafeArea(
        child: ListView(
          controller: _scroll,
          padding: EdgeInsets.zero,
          shrinkWrap: true,
          children: [
            _Header(text: text),
            const SizedBox(height: 12),
            _SearchBar(controller: _ctrl, focus: _focus, onBuscar: _buscar),
            const SizedBox(height: 8),
            _BuscarButton(onBuscar: _buscar),
            const SizedBox(height: 14),
            _Sugerencias(sugerencias: _sugerencias, onTap: _buscarSugerencia, text: text),
            const SizedBox(height: 10),
            const _Footer(),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.text});
  final TextTheme text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Bolt amarillo en cuadro redondeado (= marca visual del frontal)
          Container(
            width: 38,
            height: 38,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: LuxColors.yellowBolt,
              borderRadius: BorderRadius.circular(LuxRadii.sm),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x66FDD835),
                  blurRadius: 6,
                  offset: Offset(0, 2),
                ),
              ],
            ),
            child: const Icon(Icons.bolt, color: LuxColors.blueDark, size: 20),
          ),
          const SizedBox(height: 6),
          Text('LuxRay', style: text.displaySmall?.copyWith(letterSpacing: 0.6)),
          Text(
            'Wear · Electricidad',
            style: TextStyle(fontSize: 10, color: LuxColors.blueLight, letterSpacing: 0.5),
          ),
        ],
      ),
    );
  }
}

class _SearchBar extends StatelessWidget {
  const _SearchBar({required this.controller, required this.focus, required this.onBuscar});
  final TextEditingController controller;
  final FocusNode focus;
  final VoidCallback onBuscar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 38,
      child: TextField(
        controller: controller,
        focusNode: focus,
        maxLength: LuxrayApi.kMaxQuery, // límite de entrada: bloquea payloads largos
        textInputAction: TextInputAction.search,
        onSubmitted: (_) => onBuscar(),
        style: theme.textTheme.bodyMedium,
        textAlign: TextAlign.center,
        decoration: InputDecoration(
          hintText: 'ohm, tablero, PLC…',
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
          prefixIcon: const Icon(Icons.search, size: 14, color: LuxColors.yellowBolt),
          prefixIconConstraints: const BoxConstraints(minWidth: 28, minHeight: 28),
          suffixIcon: IconButton(
            visualDensity: VisualDensity.compact,
            iconSize: 14,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            icon: const Icon(Icons.send, color: LuxColors.yellowBolt),
            onPressed: onBuscar,
          ),
        ),
      ),
    );
  }
}

class _BuscarButton extends StatelessWidget {
  const _BuscarButton({required this.onBuscar});
  final VoidCallback onBuscar;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 34,
      child: ElevatedButton.icon(
        style: ElevatedButton.styleFrom(
          backgroundColor: LuxColors.yellowBolt,
          foregroundColor: LuxColors.blueDark,
          shape: const StadiumBorder(),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
          elevation: 0,
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
        onPressed: onBuscar,
        icon: const Icon(Icons.search, size: 14, color: LuxColors.blueDark),
        label: const Text('BUSCAR'),
      ),
    );
  }
}

class _Sugerencias extends StatelessWidget {
  const _Sugerencias({
    required this.sugerencias,
    required this.onTap,
    required this.text,
  });

  final List<String> sugerencias;
  final void Function(String) onTap;
  final TextTheme text;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.roundabout_right, size: 11, color: LuxColors.yellowBolt),
            const SizedBox(width: 4),
            Text('Sugerencias', style: text.bodySmall),
          ],
        ),
        const SizedBox(height: 6),
        Wrap(
          spacing: 4,
          runSpacing: 4,
          alignment: WrapAlignment.center,
          children: [
            for (final s in sugerencias)
              OutlinedButton(
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(0, 26),
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                onPressed: () => onTap(s),
                child: Text(s, style: const TextStyle(fontSize: 9)),
              ),
          ],
        ),
      ],
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Text(
        'Giro eléctrico · sin salir del sitio',
        textAlign: TextAlign.center,
        style: TextStyle(fontSize: 8, color: LuxColors.blueLight, letterSpacing: 0.4),
      ),
    );
  }
}
