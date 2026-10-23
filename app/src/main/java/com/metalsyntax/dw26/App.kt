package com.metalsyntax.dw26

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.ido.ble.BLEManager
import com.ido.ble.InitParam
import com.ido.ble.callback.SdkLogCallback
import com.ido.ble.logs.LogLevel
import java.lang.reflect.Proxy

/** Evento de cualquier callback del SDK: nombre del método + argumentos. */
typealias SdkListener = (method: String, args: List<Any?>) -> Unit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        BLEManager.onApplicationCreate(this)
        BLEManager.init(InitParam().apply {
            databaseName = "dw26.db"
            log_save_path = getExternalFilesDir("sdklog")?.path
            soJinLogSavePath = getExternalFilesDir("solog")?.path ?: ""
            logLevel = LogLevel.ALL
            sdkLogCallback = SdkLogCallback.ICallBack { tag, msg -> Log.d("IDO/$tag", msg) }
        })
        Watch.init(this)
        applyTheme()
        Health.init(this)
        registerAllCallbacks()
    }

    /**
     * Fase de descubrimiento: registra un proxy en TODOS los BLEManager.registerXxx(ICallBack)
     * y vuelca cada respuesta del reloj como JSON. Así se ve qué soporta el DW26 sin
     * implementar a mano cientos de métodos.
     */
    private fun registerAllCallbacks() {
        val ok = mutableListOf<String>()
        for (m in BLEManager::class.java.methods) {
            if (!m.name.startsWith("register") || m.parameterTypes.size != 1) continue
            val iface = m.parameterTypes[0]
            if (!iface.isInterface) continue
            runCatching { m.invoke(null, spy(iface)) }
                .onSuccess { ok += m.name }
                .onFailure { Log.w(TAG, "${m.name}: ${it.cause ?: it}") }
        }
        Log.i(TAG, "callbacks registrados: ${ok.size}")
    }

    companion object {
        const val TAG = "DW26"
        val gson = Gson()
        private val main = Handler(Looper.getMainLooper())
        private val listeners = mutableListOf<SdkListener>()

        /** Modo de color elegido en Ajustes; "black" es el modo noche con fondos negros (tema Theme.DW26.Black). */
        fun applyTheme() = androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (Watch.theme) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark", "black" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })

        fun listen(l: SdkListener) = main.post { listeners += l }
        fun unlisten(l: SdkListener) = main.post { listeners -= l }

        /** Últimos eventos, para el "Registro técnico" de la pantalla Reloj. */
        val history = ArrayDeque<String>()

        fun emit(method: String, args: List<Any?>) {
            val line = "$method ${json(args)}"
            Log.i(TAG, line)
            main.post {
                if (method != "ScanCallBack.onFindDevice") {
                    history.addLast("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ${line.take(600)}")
                    while (history.size > 300) history.removeFirst()
                }
                Watch.handle(method, args)
                listeners.toList().forEach { it(method, args) }
            }
        }

        /** Implementa cualquier interfaz de callback del SDK reenviando cada llamada a emit(). */
        @Suppress("UNCHECKED_CAST")
        fun <T> spy(iface: Class<T>): T =
            Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { p, method, args ->
                when (method.name) {
                    "toString" -> "Spy(${iface.simpleName})"
                    "hashCode" -> System.identityHashCode(p)
                    "equals" -> p === args?.get(0)
                    else -> {
                        emit("${iface.enclosingClass?.simpleName ?: iface.simpleName}.${method.name}", args?.toList() ?: emptyList())
                        defaultFor(method.returnType)
                    }
                }
            } as T

        fun json(o: Any?): String = runCatching { gson.toJson(o) }.getOrElse { o.toString() }

        private fun defaultFor(t: Class<*>): Any? = when (t) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    }
}
