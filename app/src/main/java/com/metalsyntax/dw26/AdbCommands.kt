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
        }
    }
}
