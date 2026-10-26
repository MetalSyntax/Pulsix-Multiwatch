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
            "find" -> Watch.findDevice()
            "time" -> BLEManager.setTime()
            "notify" -> NotifListener.send(context.packageName, "DW26", intent.getStringExtra("text") ?: "Prueba")
            "connect" -> BLEManager.autoConnect()
            "dumpfunctions" -> java.io.File(context.filesDir, "functions.json").writeText(App.json(Watch.functions))
            "face" -> Faces.exclusive.firstOrNull { it.file == intent.getStringExtra("text") }?.let { Faces.install(context, it) }
            // FitCloud (Pop 2): fitconnect <mac>, fitfind, fitdial <ruta .bin en el teléfono>
            "fitconnect" -> (intent.getStringExtra("text") ?: Profiles.list.first { it.kind == "fitcloud" }.mac).let { mac ->
                if (Profiles.list.none { it.mac == mac }) Profiles.add(Profile(mac, "fitcloud"))
                FitCloud.connect(mac)
                WatchService.start(context)
            }
            "fitfind" -> FitCloud.find()
            "fitinfo" -> FitCloud.refresh()
            "fitdial" -> FitCloud.installDial(context, intent.getStringExtra("text") ?: return)
            "theme" -> { Watch.theme = intent.getStringExtra("text") ?: "auto"; App.applyTheme() } // auto|light|dark|black
        }
    }
}
