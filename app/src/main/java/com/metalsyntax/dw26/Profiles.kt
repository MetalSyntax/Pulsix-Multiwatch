package com.metalsyntax.dw26

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un perfil por reloj vinculado. kind: "ido" (Daewoo DW26, SDK de VeryFit) o "fitcloud" (Amazfit Pop 2, SDK htsmart de Zepp Active).
 * El nombre no se guarda fijo: se lee del Bluetooth del sistema cada vez (el que reporta el reloj).
 */
data class Profile(val mac: String, val kind: String) {
    val daewoo get() = kind == "ido"
}

object Profiles {
    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences

    fun init(c: Context) {
        ctx = c
        prefs = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)
        // perfil del DW26 vinculado antes de existir los perfiles
        if (list.none { it.daewoo } && Watch.bound && Watch.mac.isNotEmpty()) add(Profile(Watch.mac, "ido"))
    }

    val list: List<Profile>
        get() = JSONArray(prefs.getString("list", "[]")).let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Profile(o.getString("mac"), o.getString("kind")) } }
        }

    private fun save(l: List<Profile>) =
        prefs.edit().putString("list", JSONArray(l.map { JSONObject().put("mac", it.mac).put("kind", it.kind) }).toString()).apply()

    var active: Profile?
        get() = list.firstOrNull { it.mac == prefs.getString("active", null) } ?: list.firstOrNull()
        set(v) = prefs.edit().putString("active", v?.mac).apply()

    fun add(p: Profile) {
        save(list.filter { it.mac != p.mac } + p)
        active = p
    }

    fun remove(p: Profile) {
        save(list.filter { it.mac != p.mac })
        if (prefs.getString("active", null) == p.mac) active = list.firstOrNull()
    }

    /** Nombre que anuncia el reloj (p. ej. "DW26", "Amazfit Pop 2"); la MAC si el sistema aún no lo conoce. */
    @SuppressLint("MissingPermission")
    fun name(p: Profile?): String = p?.let {
        runCatching { ctx.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(it.mac).name }.getOrNull()
            ?.takeIf { n -> n.isNotBlank() } ?: it.mac
    } ?: ""

    /** Marca para la cabecera: DAEWOO para el DW26, si no la primera palabra del nombre (AMAZFIT, …). */
    fun brand(p: Profile?): String = if (p?.daewoo != false) "DAEWOO" else name(p).substringBefore(' ').uppercase()

    /** Tema neutro (blanco, gris claro y negro) para todo lo que no es Daewoo. */
    val neutral get() = active?.daewoo == false

    /**
     * Código QR que muestra el reloj al vincular, p. ej. el del Pop 2:
     * https://api.linwear.top/app/auto-download?…&MAC=DD:EA:FF:B0:9C:D4&BtName=Amazfit%20Pop%202
     * Devuelve (MAC con dos puntos, nombre) o null si no trae una MAC.
     */
    fun parseQr(s: String): Pair<String, String?>? {
        val hex = Regex("(?i)[?&]mac=([0-9a-f]{2}(?::?[0-9a-f]{2}){5})").find(s)?.groupValues?.get(1) ?: return null
        val mac = hex.replace(":", "").uppercase().chunked(2).joinToString(":")
        val name = Regex("[?&]BtName=([^&]*)").find(s)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        return mac to name?.takeIf { it.isNotBlank() }
    }

    fun connected(p: Profile?) = when (p?.kind) {
        "ido" -> Watch.connected
        "fitcloud" -> FitCloud.connected && FitCloud.mac == p.mac
        else -> false
    }
}
