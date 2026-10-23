package com.metalsyntax.dw26

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.view.KeyEvent
import com.ido.ble.BLEManager
import com.ido.ble.business.sync.ISyncDataListener
import com.ido.ble.business.sync.ISyncProgressListener
import com.ido.ble.business.sync.SyncPara
import com.ido.ble.callback.DeviceControlAppCallBack.DeviceControlEventType
import com.ido.ble.protocol.model.BatteryInfo
import com.ido.ble.protocol.model.LiveData

/** Estado del reloj y reacciones automáticas a lo que manda. Todo corre en el hilo principal. */
object Watch {
    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences
    private var ringtone: Ringtone? = null

    var battery = -1; private set
    var live: LiveData? = null; private set
    var syncing = false; private set
    var syncProgress = 0; private set

    val connected get() = BLEManager.isConnected()
    val bound get() = BLEManager.isBind()
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        private set(v) = prefs.edit().putLong("last_sync", v).apply()
    val mac: String get() = prefs.getString("mac", "") ?: ""
    var stepGoal: Int
        get() = prefs.getInt("step_goal", 8000)
        set(v) = prefs.edit().putInt("step_goal", v).apply()
    var notifyEnabled: Boolean
        get() = prefs.getBoolean("notify", true)
        set(v) = prefs.edit().putBoolean("notify", v).apply()

    fun init(c: Context) {
        ctx = c
        prefs = c.getSharedPreferences("watch", Context.MODE_PRIVATE)
        battery = prefs.getInt("battery", -1)
    }

    fun sync() {
        if (syncing || !connected) return
        syncing = true
        BLEManager.syncAllData(SyncPara().apply {
            isNeedSyncConfigData = true
            iSyncDataListener = App.spy(ISyncDataListener::class.java)
            iSyncProgressListener = App.spy(ISyncProgressListener::class.java)
        })
    }

    fun refresh() {
        if (!connected) return
        BLEManager.getBatteryInfo()
        BLEManager.getLiveData()
    }

    /** Se llama con cada evento del SDK antes que la UI. */
    fun handle(method: String, args: List<Any?>) {
        when (method) {
            "ConnectCallBack.onConnectSuccess" -> prefs.edit().putString("mac", args[0] as String).apply()
            "ConnectCallBack.onInitCompleted" -> {
                refresh()
                // ponytail: sincroniza al conectar si pasaron 15 min; sin tarea periódica en segundo plano
                if (System.currentTimeMillis() - lastSync > 15 * 60_000) sync()
            }
            "GetDeviceInfoCallBack.onGetBatteryInfo" -> {
                battery = (args[0] as BatteryInfo).level
                prefs.edit().putInt("battery", battery).apply()
            }
            "GetDeviceInfoCallBack.onGetBasicInfo" -> {
                battery = (args[0] as com.ido.ble.protocol.model.BasicInfo).energe
                prefs.edit().putInt("battery", battery).apply()
            }
            "GetDeviceInfoCallBack.onGetLiveData" -> live = args[0] as LiveData
            "ISyncProgressListener.onProgress" -> syncProgress = args[0] as Int
            "ISyncProgressListener.onSuccess", "ISyncProgressListener.onFailed" -> {
                // un fallo parcial (p. ej. GPS que el DW26 no tiene) igual deja los datos de salud guardados
                syncing = false; syncProgress = 0
                lastSync = System.currentTimeMillis()
                BLEManager.getLiveData()
            }
            "DeviceControlAppCallBack.onFindPhone" -> findPhone(args[0] as Boolean)
            "DeviceControlAppCallBack.onControlEvent" -> media(args[0] as DeviceControlEventType)
        }
        if (method.startsWith("ISyncDataListener.")) Health.onSync(method, args)
    }

    private fun findPhone(start: Boolean) {
        ringtone?.stop()
        ringtone = if (!start) null else RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            play()
        }
    }

    private fun media(type: DeviceControlEventType) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val key = when (type) {
            DeviceControlEventType.START, DeviceControlEventType.PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            DeviceControlEventType.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            DeviceControlEventType.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            DeviceControlEventType.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            DeviceControlEventType.VOLUME_UP -> return am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            DeviceControlEventType.VOLUME_DOWN -> return am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            else -> return
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }
}
