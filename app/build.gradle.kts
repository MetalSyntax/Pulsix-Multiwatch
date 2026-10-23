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
        versionCode = 1
        versionName = "0.1"
        // la .so de VeryFit solo se sacó para arm64
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // el SDK de IDO va sin reglas de keep: no minificar
            isMinifyEnabled = false
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
    }

    packaging {
        // libVeryFitMulti.so se carga con System.loadLibrary: extraída en disco como en VeryFit
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    // SDK de IDO sacado del APK de VeryFit (idoble.jar) + sus dependencias en las mismas versiones (ido-deps.jar)
    implementation(fileTree("libs") { include("*.jar") })
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
