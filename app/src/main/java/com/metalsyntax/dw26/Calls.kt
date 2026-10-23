package com.metalsyntax.dw26

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.ido.ble.BLEManager
import com.ido.ble.protocol.model.IncomingCallInfo

/**
 * Avisa al reloj de llamadas entrantes y perdidas, con la misma secuencia que VeryFit:
 * suena → setIncomingCallInfo, se contesta → setStopInComingCall, cuelgan sin contestar → missedInComingCall.
 */
object Calls {
    private lateinit var ctx: Context
    private var ringing: String? = null

    // ponytail: PhoneStateListener está obsoleto, pero es la única API que aún entrega el número (con READ_CALL_LOG)
    @Suppress("DEPRECATION")
    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, number: String?) {
            if (!Watch.callsEnabled || !Watch.connected) return
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    val n = number.orEmpty().replace("-", "")
                    ringing = n
                    BLEManager.setIncomingCallInfo(IncomingCallInfo().apply {
                        name = contactName(n) ?: n.ifEmpty { " " }
                        phoneNumber = n
                    })
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> if (ringing != null) { ringing = null; BLEManager.setStopInComingCall() }
                TelephonyManager.CALL_STATE_IDLE -> if (ringing != null) {
                    val n = ringing!!
                    ringing = null
                    BLEManager.missedInComingCall()
                    NotifListener.send("miss_call", contactName(n) ?: n, "Llamada perdida", "Teléfono")
                }
            }
        }
    }

    val permissions = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)

    fun granted(c: Context) = permissions.all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @Suppress("DEPRECATION")
    fun start(c: Context) {
        ctx = c.applicationContext
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        ctx.getSystemService(TelephonyManager::class.java).listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun contactName(number: String): String? {
        if (number.isEmpty() || ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
