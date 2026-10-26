package com.metalsyntax.dw26

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ido.ble.BLEManager
import com.ido.ble.protocol.model.NotificationPara

/** Reenvía las notificaciones del teléfono al reloj. */
class NotifListener : NotificationListenerService() {

    private var last = ""

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val key = "${sbn.packageName}|$title|$text"
        if (key == last) return // WhatsApp & co. repiten la misma notificación al actualizarla
        last = key
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        send(sbn.packageName, title, text, appName)
    }

    companion object {
        fun send(pkg: String, title: String, text: String, appName: String = "DW26") {
            if (!Watch.notifyEnabled) return
            FitCloud.notify(title, text)
            if (!BLEManager.isConnected()) return
            BLEManager.sendNotification(NotificationPara().apply {
                notify_type = typeOf(pkg)
                evt_type = 1
                contact = title
                msg_data = text
                items = arrayListOf(NotificationPara.AppNames().apply { language = 1; name = appName })
                app_items_len = 1
            })
        }

        /**
         * Tipo de app que entiende el reloj (protocolo V3): mismo hash que VeryFit
         * (NoticeAppUtil.convertPkg2Type): a-z=0..25, A-Z=26..51, '.'=52, '_'=53, ponderado por posición.
         */
        fun typeOf(pkg: String): Int {
            var v = 0
            pkg.forEachIndexed { i, c ->
                val w = when (c) {
                    in 'a'..'z' -> c - 'a'
                    in 'A'..'Z' -> c - 'A' + 26
                    '.' -> 52
                    '_' -> 53
                    else -> 0
                }
                v += w * i
            }
            return if (v in 20000..21000) v * 2 else v // rango reservado por el firmware
        }
    }
}
