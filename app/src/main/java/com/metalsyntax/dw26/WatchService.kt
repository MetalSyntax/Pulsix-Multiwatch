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
        if (method.startsWith("ConnectCallBack.") || method.endsWith("onGetBatteryInfo")) update()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Conexión con el reloj", NotificationManager.IMPORTANCE_MIN)
        )
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, build())
        App.listen(onSdk)
        if (!Watch.connected) BLEManager.autoConnect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        App.unlisten(onSdk)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun update() = getSystemService(NotificationManager::class.java).notify(1, build())

    private fun build(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_watch)
        .setColor(getColor(R.color.dw_blue))
        .setContentTitle("Daewoo DW26")
        .setContentText(
            if (Watch.connected) "Conectado" + (if (Watch.battery >= 0) " · batería ${Watch.battery}%" else "")
            else "Buscando el reloj…"
        )
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        private const val CHANNEL = "watch"
        fun start(c: Context) {
            if (BLEManager.isBind()) c.startForegroundService(Intent(c, WatchService::class.java))
        }
    }
}
