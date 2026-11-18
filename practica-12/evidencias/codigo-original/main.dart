import 'package:flutter/material.dart';

import 'models/busqueda.dart';
import 'screens/detalle_concepto_screen.dart';
import 'screens/home_screen.dart';
import 'screens/resultados_screen.dart';
import 'services/luxray_api.dart';
import 'theme/wear_theme.dart';

/// Punto de entrada del wearable LuxRay (Wear OS).
///
/// Pantallas:
///   [HomeScreen] -> [ResultadosScreen] -> [DetalleConceptoScreen]
///
/// La navegación se gestiona con un [PageView] indexado (no Navigator.push)
/// porque Wear OS prefiere transiciones inmediatas y ambient-aware.
void main() {
  runApp(const LuxRayWearApp());
}

class LuxRayWearApp extends StatelessWidget {
  const LuxRayWearApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LuxRay Wear',
      debugShowCheckedModeBanner: false,
      theme: luxrayWearTheme(),
      home: const _Root(),
    );
  }
}

class _Root extends StatefulWidget {
  const _Root();

  @override
  State<_Root> createState() => _RootState();
}

class _RootState extends State<_Root> {
  final LuxrayApi _api = LuxrayApi();

  /// 0 = home, 1 = resultados, 2 = detalle.
  late final PageController _pager = PageController(initialPage: 0);

  String _query = '';
  BusquedaResponse _resultado = BusquedaResponse.empty();
  bool _cargando = false;

  ConceptoHit? _conceptoDetalle;
  FaqHit? _faqDetalle;

  @override
  void dispose() {
    _pager.dispose();
    _api.close();
    super.dispose();
  }

  Future<void> _ejecutarBusqueda(String q) async {
    setState(() {
      _query = q;
      _cargando = true;
    });
    final res = await _api.buscar(q);
    setState(() {
      _resultado = res;
      _cargando = false;
      _irA(1); // resultados
    });
  }

  void _irA(int index) {
    _pager.animateToPage(
      index,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
    );
  }

  void _volverA0() => _irA(0);

  void _abrirConcepto(ConceptoHit c) {
    setState(() => _conceptoDetalle = c);
    _irA(2);
  }

  void _abrirFaq(FaqHit f) {
    setState(() => _faqDetalle = f);
    _irA(2);
  }

  void _cerrarDetalle() {
    setState(() {
      _conceptoDetalle = null;
      _faqDetalle = null;
    });
    _irA(1);
  }

  void _onPageChanged(int i) {
    // noop; reservado para telemetría / offset curves en el futuro
  }

  @override
  Widget build(BuildContext context) {
    // Clip oval adapta el contenido a pantalla redonda Wear OS даже en
    // simuladores square; el fondo queda redondo.
    return ClipOval(
      child: PageView(
        controller: _pager,
        physics: const NeverScrollableScrollPhysics(),
        onPageChanged: _onPageChanged,
        children: [
          HomeScreen(onBuscar: _ejecutarBusqueda),
          _buildResultados(),
          _buildDetalle(),
        ],
      ),
    );
  }

  Widget _buildResultados() {
    if (_cargando) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFFFDD835), strokeWidth: 2),
        ),
      );
    }
    return ResultadosScreen(
      query: _query,
      resultado: _resultado,
      onVolver: _volverA0,
      onAbrirConcepto: _abrirConcepto,
      onAbrirFaq: _abrirFaq,
    );
  }

  Widget _buildDetalle() {
    if (_conceptoDetalle != null) {
      return DetalleConceptoScreen(
        concepto: _conceptoDetalle,
        onVolver: _cerrarDetalle,
      );
    }
    if (_faqDetalle != null) {
      return DetalleConceptoScreen(
        faq: _faqDetalle,
        onVolver: _cerrarDetalle,
      );
    }
    // Fallback: si llegamos a la página 2 sin detalle, volver a la 1.
    //
    // IMPORTANTE (DEF-04): un PageView construye TODOS sus hijos desde el
    // primer fotograma, no solo la página visible. En el arranque
    // `_conceptoDetalle` y `_faqDetalle` son null, por lo que esta rama se
    // encola tambien al inicio y `_irA(1)` dejaba la app siempre en la
    // pantalla de resultados ("Sin coincidencias"), nunca en la de inicio.
    // Por eso solo se redirige si la página actual es realmente la 2.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_pager.hasClients && _pager.page != null && _pager.page!.round() == 2) {
        _irA(1);
      }
    });
    return const Scaffold(backgroundColor: Colors.black, body: SizedBox.shrink());
  }
}
