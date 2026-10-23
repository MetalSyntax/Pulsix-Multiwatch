package com.metalsyntax.dw26

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ido.ble.BLEManager

/**
 * Pruebas desde la Mac sin tocar el teléfono:
 *   adb shell am broadcast -n com.metalsyntax.dw26/.AdbCommands --es cmd sync
 * Protegido con android.permission.DUMP: solo adb/sistema pueden enviarlo.
 */
class AdbCommands : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        App.emit("adb.$cmd", emptyList())
        when (cmd) {
            "sync" -> Watch.sync()
            "live" -> BLEManager.getLiveData()
            "info" -> { BLEManager.getBasicInfo(); BLEManager.getBatteryInfo() }
            "functions" -> BLEManager.getFunctionTables()
            "find" -> BLEManager.startFindDevice()
            "time" -> BLEManager.setTime()
            "notify" -> NotifListener.send(context.packageName, "DW26", intent.getStringExtra("text") ?: "Prueba")
            "connect" -> BLEManager.autoConnect()
            "face" -> Faces.exclusive.firstOrNull { it.file == intent.getStringExtra("text") }?.let { Faces.install(context, it) }
            // tema solo de esta app (no toca el del sistema): night yes|no|auto
            "night" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (intent.getStringExtra("text")) {
                "yes" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                "no" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
        }
    }
}
