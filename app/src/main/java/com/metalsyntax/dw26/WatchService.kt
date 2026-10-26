package com.metalsyntax.dw26

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import com.ido.ble.BLEManager

/** Mantiene vivo el proceso (y con él la conexión BLE del SDK) con una notificación fija. */
class WatchService : Service() {

    private val onSdk: SdkListener = { method, _ ->
        if (method.startsWith("ConnectCallBack.") || method.endsWith("onGetBatteryInfo") || method.startsWith("FitCloud.")) update()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Conexión con el reloj", NotificationManager.IMPORTANCE_MIN)
        )
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, build())
        App.listen(onSdk)
        Music.start(this)
        Calls.start(this)
        hourly.run()
        if (!Watch.connected && Watch.bound) BLEManager.autoConnect()
        // ponytail: el SDK de FitCloud maneja un solo reloj a la vez; con varios Pop 2 se conecta el activo o el primero
        (Profiles.active?.takeIf { it.kind == "fitcloud" } ?: Profiles.list.firstOrNull { it.kind == "fitcloud" })
            ?.let { if (!FitCloud.connected) FitCloud.connect(it.mac) }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hourly = object : Runnable {
        override fun run() { Weather.push(this@WatchService); handler.postDelayed(this, 60 * 60_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        App.unlisten(onSdk)
        handler.removeCallbacks(hourly)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun update() = getSystemService(NotificationManager::class.java).notify(1, build())

    private fun build(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_watch)
        .setContentTitle(Profiles.list.joinToString(" · ") { Profiles.name(it) })
        .setContentText(Profiles.list.joinToString(" · ") { p ->
            val bat = if (p.daewoo) Watch.battery else FitCloud.battery
            if (Profiles.connected(p)) "Conectado" + (if (bat >= 0) " $bat%" else "") else "Buscando…"
        })
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        private const val CHANNEL = "watch"
        fun start(c: Context) {
            if (Profiles.list.isNotEmpty()) c.startForegroundService(Intent(c, WatchService::class.java))
        }
    }
}
