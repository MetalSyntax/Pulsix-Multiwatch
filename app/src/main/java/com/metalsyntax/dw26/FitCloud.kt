package com.metalsyntax.dw26

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.htsmart.wristband2.WristbandApplication
import com.htsmart.wristband2.WristbandManager
import com.htsmart.wristband2.bean.ConnectionState
import com.htsmart.wristband2.bean.WristbandNotification
import com.htsmart.wristband2.dfu.DfuCallback
import com.htsmart.wristband2.dfu.DfuManager
import com.htsmart.wristband2.exceptions.AuthenticatedException
import io.reactivex.disposables.CompositeDisposable

/**
 * Relojes FitCloud (htsmart wristband2, chip Realtek): el Amazfit Pop 2 que usa Zepp Active.
 * Eventos hacia la UI con App.emit("FitCloud.<qué>").
 */
object FitCloud {
    // mismo id de usuario fijo que manda Zepp Active: el reloj lo acepta sin volver a vincular ni desvincular a Zepp
    private const val USER_ID = "123456789"
    private val main = Handler(Looper.getMainLooper())
    // perezoso y sin tipos del SDK en campos: en release (sin htsmart.jar) tocar FitCloud no debe romper
    private val subs by lazy { CompositeDisposable() }
    private var wm: WristbandManager? = null
    private var triedBind = false

    var mac = ""; private set
    /** CONNECTING | CONNECTED | DISCONNECTED */
    var state = "DISCONNECTED"; private set
    var battery = -1; private set
    var charging = false; private set
    var heartRate = 0; private set
    var dfuProgress = -1; private set
    val connected get() = state == "CONNECTED"
    val firmware: String? get() = wm?.wristbandConfig?.wristbandVersion?.let { "${it.project} · ${it.app}" }

    /** false en release (el SDK de Zepp aún no se carga en ejecución, ver PLAN-SIGUIENTE.md) */
    val available = runCatching { Class.forName("com.htsmart.wristband2.WristbandManager") }.isSuccess

    fun init(app: Application) {
        if (!available) return
        this.app = app
        WristbandApplication.init(app)
        // el DFU de Realtek (esferas) necesita su núcleo inicializado aparte, como en OtaUtil de Zepp Active
        com.realsil.sdk.core.RtkCore.initialize(app, com.realsil.sdk.core.RtkConfigure.Builder().logTag(App.TAG).build())
        com.realsil.sdk.dfu.RtkDfu.initialize(app, true)
        val m = WristbandApplication.getWristbandManager().also { wm = it }
        subs.add(m.observerConnectionState().subscribe({ s ->
            main.post {
                state = s.name
                if (s == ConnectionState.CONNECTED) {
                    triedBind = false; refresh()
                    // igual que el DW26: sincroniza al conectar si pasaron 15 min
                    if (System.currentTimeMillis() - lastSync > 15 * 60_000) main.postDelayed({ sync() }, 3_000)
                }
                App.emit("FitCloud.state", listOf(s.name))
            }
        }, ::log))
        subs.add(m.observerConnectionError().subscribe({ e ->
            main.post {
                val t = e.throwable
                // el reloj no reconoce el login: pedir vinculación (confirmar en el reloj) una sola vez
                if (t is AuthenticatedException && !triedBind) { triedBind = true; connect(mac, bind = true) }
                App.emit("FitCloud.error", listOf(t?.javaClass?.simpleName ?: "error", (t as? AuthenticatedException)?.reason))
            }
        }, ::log))
        // el reloj pide buscar el teléfono, colgar, música…: por ahora solo se registra
        subs.add(m.observerWristbandMessage().subscribe({ msg -> App.emit("FitCloud.message", listOf(msg)) }, ::log))
    }

    fun connect(address: String, bind: Boolean = false) {
        val m = wm ?: return
        mac = address
        if (m.isConnected) m.close()
        // perfil por defecto (hombre, 30 años, 170 cm, 70 kg): solo lo usa el reloj para calorías/distancia
        m.connect(address, USER_ID, bind, true, 30, 170f, 70f)
    }

    fun disconnect() { wm?.close(); state = "DISCONNECTED" }

    fun refresh() {
        val m = wm?.takeIf { it.isConnected } ?: return
        subs.add(m.requestBattery().subscribe({ b -> main.post { battery = b.percentage; charging = b.isCharging; App.emit("FitCloud.battery", listOf(battery)) } }, ::log))
        subs.add(m.requestLatestHealthy().subscribe({ h -> main.post { heartRate = h.heartRate; App.emit("FitCloud.healthy", listOf(heartRate)) } }, ::log))
    }

    fun find() { wm?.takeIf { it.isConnected }?.findWristband()?.subscribe({}, ::log)?.let(subs::add) }

    fun notify(title: String, text: String) {
        val m = wm?.takeIf { it.isConnected } ?: return
        val n = WristbandNotification().apply { type = WristbandNotification.TYPE_OTHERS_APP; name = title; content = text }
        subs.add(m.sendWristbandNotification(n).subscribe({}, ::log))
    }

    /** Sube una esfera .bin (formato FitCloud, la que baja Zepp Active) por DFU de Realtek. */
    /** Nombre de la última esfera instalada desde la app (el SDK no informa cuál está en uso). */
    var lastDial: String?
        get() = prefs.getString("fit_dial_$mac", null)
        private set(v) = prefs.edit().putString("fit_dial_$mac", v).apply()

    fun installDial(ctx: android.content.Context, path: String, name: String) {
        if (!connected || dfuProgress >= 0) return
        dfuProgress = 0
        DfuManager(ctx).apply {
            setDfuCallback(object : DfuCallback {
                override fun onError(type: Int, code: Int) = done("FitCloud.dialFailed", "$type/$code")
                override fun onStateChanged(state: Int, cancelable: Boolean) {}
                override fun onProgressChanged(p: Int) = main.post { dfuProgress = p; App.emit("FitCloud.dfu", listOf(p)) }.let {}
                override fun onSuccess() { lastDial = name; done("FitCloud.dialInstalled", null) }
                private fun done(event: String, arg: String?) = main.post {
                    dfuProgress = -1; release()
                    App.emit(event, listOf(arg))
                    // el DFU corta la conexión normal: reconectar como hace Zepp Active
                    connect(mac)
                }.let {}
            })
            init()
            start(path, false)
        }
    }

    var syncing = false; private set
    var lastSync: Long
        get() = prefs.getLong("fit_last_sync_$mac", 0)
        private set(v) = prefs.edit().putLong("fit_last_sync_$mac", v).apply()
    private val prefs by lazy { app.getSharedPreferences("watch", android.content.Context.MODE_PRIVATE) }
    private lateinit var app: Application

    /** Baja del reloj pasos, sueño, pulso, SpO2 y estrés (el reloj los borra al entregarlos) y los guarda en Health. */
    fun sync() {
        val m = wm?.takeIf { it.isConnected && !syncing } ?: return
        syncing = true
        App.emit("FitCloud.sync", listOf("start"))
        val key = mac
        subs.add(m.syncData().subscribe({ raw ->
            // el reloj borra lo que ya entregó: un dato que no se entiende se registra y se sigue, nunca tumba la app
            main.post { runCatching { FitHealth.save(key, raw) }.onFailure { App.emit("FitCloud.dataError", listOf(raw.dataType, it.toString())) } }
        }, { e ->
            main.post { syncing = false; App.emit("FitCloud.sync", listOf("failed", e.toString())) }
        }, {
            main.post { syncing = false; lastSync = System.currentTimeMillis(); refresh(); App.emit("FitCloud.sync", listOf("done")) }
        }))
    }

    private fun log(t: Throwable) = Log.w(App.TAG, "FitCloud: $t").let {}
}
