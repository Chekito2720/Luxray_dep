package com.luxray.waer

import io.flutter.embedding.android.FlutterActivity

/**
 * Activity principal del wearable LuxRay.
 *
 * Hereda de FlutterActivity; el namespace `com.luxray.waer` coincide
 * con el `applicationId` declarado en build.gradle.kts.
 *
 * Wear OS maneja automáticamente:
 *  - pantalla redonda (round screen)
 *  - ambient mode (si la app declara el feature)
 *  - rotary input (bezel)
 */
class MainActivity : FlutterActivity()
