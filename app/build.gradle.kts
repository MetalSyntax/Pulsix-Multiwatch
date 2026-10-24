import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.metalsyntax.dw26"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.metalsyntax.dw26"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5"
        // clave de VeryFit para el catálogo de esferas: la escribe tools/extract_sdk.sh (no se versiona)
        val ido = Properties().apply { rootProject.file("ido.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        buildConfigField("String", "IDO_APP_KEY", "\"${ido.getProperty("ido.appKey", "")}\"")
        // la .so de VeryFit solo se sacó para arm64
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // el SDK de IDO va sin reglas de keep: no minificar
            isMinifyEnabled = false
            // redistribuible: sin SDK ni clave de VeryFit (se cargan de la VeryFit instalada, ver VeryFit.kt)
            buildConfigField("String", "IDO_APP_KEY", "\"\"")
            // firmado con la clave debug, igual que los releases de Mandix
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // las .so de VeryFit solo van en debug
    sourceSets {
        getByName("main").jniLibs.setSrcDirs(emptyList<String>())
        getByName("debug").jniLibs.srcDir("src/main/jniLibs")
    }

    packaging {
        // libVeryFitMulti.so se carga con System.loadLibrary: extraída en disco como en VeryFit
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    // SDK de IDO sacado del APK de VeryFit (idoble.jar) + sus dependencias en las mismas versiones (ido-deps.jar)
    // debug los incluye; release solo compila contra ellos y los toma de VeryFit en ejecución
    debugImplementation(fileTree("libs") { include("*.jar") })
    releaseCompileOnly(fileTree("libs") { include("*.jar") })
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
