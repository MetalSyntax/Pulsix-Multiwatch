package com.metalsyntax.dw26

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * El APK de release no trae el SDK de IDO ni sus .so (son de VeryFit, no se pueden redistribuir):
 * se cargan en ejecución desde la VeryFit instalada en el mismo teléfono, añadiendo su APK y su
 * carpeta de librerías al ClassLoader de la app (como hacía MultiDex). En debug van dentro del APK.
 */
object VeryFit {
    const val PKG = "com.sport.fitplus1"

    /** true si las clases del SDK se pueden usar (incluidas en el APK o cargadas de VeryFit) */
    fun load(ctx: Context): Boolean {
        if (hasSdk()) return true
        return runCatching {
            // MATCH_DISABLED_COMPONENTS: sigue sirviendo si el usuario desactivó VeryFit para que no robe el reloj
            val ai = ctx.packageManager.getApplicationInfo(PKG, PackageManager.MATCH_DISABLED_COMPONENTS)
            val loader = ctx.classLoader
            val cls = Class.forName("dalvik.system.BaseDexClassLoader")
            // ponytail: API oculta (lista gris, accesible hasta Android 15); si Google la cierra, habrá que usar DexClassLoader + reflexión en todo el SDK
            cls.getMethod("addDexPath", String::class.java).invoke(loader, ai.sourceDir)
            val native = listOfNotNull(ai.nativeLibraryDir) +
                (ai.splitSourceDirs ?: emptyArray()).map { "$it!/lib/arm64-v8a" }
            cls.getMethod("addNativePath", Collection::class.java).invoke(loader, native)
            hasSdk()
        }.onFailure { Log.w(App.TAG, "VeryFit no disponible: $it") }.getOrDefault(false)
    }

    /** clave pública de VeryFit para el catálogo de esferas: la de compilación (debug) o la de su APK */
    val appKey: String by lazy {
        BuildConfig.IDO_APP_KEY.ifEmpty {
            runCatching { Class.forName("com.ido.life.constants.Constants").getField("APP_KEY").get(null) as String }
                .getOrDefault("")
        }
    }

    private fun hasSdk() = runCatching { Class.forName("com.ido.ble.BLEManager") }.isSuccess
}
