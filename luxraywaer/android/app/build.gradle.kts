plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.luxray.waer"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.luxray.waer"
        // Wear OS 2.0+ equivale a Android 8.0 (API 26). API 36 = Android 16 (Wear OS 5).
        minSdk = 26
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        // Indica al sistema que es app wearable standalone o emparejada
        // (las apps Wear clásicas ignoran esto; Play Store lo usa para Reach)
        manifestPlaceholders["appCategory"] = "wearable"
    }

    buildFeatures {
        // Wear OS no requiere viewBinding; BUILDConfig sí necesario para Flutter
        buildConfig = true
    }

    buildTypes {
        release {
            // Debug keys para que `flutter run --release` funcione mientras no agregas signing
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
