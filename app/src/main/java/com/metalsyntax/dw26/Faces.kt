package com.metalsyntax.dw26

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.ido.ble.BLEManager
import com.ido.ble.watch.custom.WatchPlateSetConfig
import com.ido.ble.watch.custom.callback.WatchPlateCallBack
import com.ido.ble.watch.custom.model.DialPlateParam
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Una esfera: del catálogo de IDO, o propia ([asset] = zip dentro de la app). */
data class Face(val id: Int, val name: String, val file: String, val image: String?, val asset: String? = null)

/**
 * Esferas: las instaladas (las informa el reloj) y el catálogo gratis de IDO (el mismo que usa VeryFit;
 * responde sin iniciar sesión). Instalar = descargar el .zip y pasarlo al SDK, que lo convierte y transfiere.
 */
object Faces {
    private const val BASE = "https://device.idoocloud.com"
    /** clave pública de la app VeryFit; la saca tools/extract_sdk.sh del APK (no se versiona) */
    private val APP_KEY = BuildConfig.IDO_APP_KEY
    private const val DEVICE_ID = 8117
    private const val FACE_VERSION = 6 // BasicInfo.user_defined_dial_main_version del DW26
    private val main = Handler(Looper.getMainLooper())

    var installed: DialPlateParam? = null; private set
    /** Esferas hechas para esta app (tools/faces/). */
    val exclusive = listOf(Face(-1, "Pip-Boy", "pipboy", "asset:faces/pipboy.png", "faces/pipboy.zip"))

    /** datos del catálogo (nombre e imagen) por nombre de archivo sin .iwf, para las instaladas */
    val known = exclusive.associateBy { it.file }.toMutableMap()
    /** esfera a instalar apenas se libere espacio (tras reemplazar otra) */
    var pending: Face? = null
    val catalog = mutableListOf<Face>()
    var catalogTotal = 0; private set
    var installing: Face? = null; private set
    var progress = 0; private set

    fun refresh() { if (Watch.connected) BLEManager.getDialPlateParam() }

    fun onInstalled(p: DialPlateParam) {
        installed = p
        val names = p.item.orEmpty().map { it.name.substringBefore('.') }.filter { it !in known && !isBuiltIn(it) }
        if (names.isNotEmpty()) thread {
            runCatching {
                val body = JSONObject().put("deviceId", DEVICE_ID).put("deviceName", "DW26").put("language", "es")
                    .put("supportFaceVersion", FACE_VERSION).put("appFaceVersion", FACE_VERSION).put("otaVersion", 1)
                    .put("faceNames", JSONArray(names))
                val r = JSONObject(http("$BASE/api/device/face/getByNames/v2", "POST", body.toString(), json = true))
                val list = r.optJSONArray("result") ?: r.optJSONObject("result")?.optJSONArray("items")
                for (i in 0 until (list?.length() ?: 0)) list!!.getJSONObject(i).let {
                    known[it.optString("otaFaceName")] = Face(it.optInt("id"), it.optString("name").trim(), it.optString("otaFaceName"), it.optString("imageUrl"))
                }
                main.post { App.emit("Faces.previews", emptyList()) }
            }
        }
    }

    fun isBuiltIn(name: String) = name.startsWith("local_") || name.startsWith("time_plate") || name.startsWith("wallpaper")
    fun isCurrent(name: String) = installed?.now_show_watch_name?.substringBefore('.') == name.substringBefore('.')

    /** Siguiente página del catálogo gratis. */
    fun loadMore() = thread {
        runCatching {
            check(APP_KEY.isNotEmpty()) { "falta ido.properties: corre tools/extract_sdk.sh" }
            val page = catalog.size / 30 + 1
            val r = JSONObject(http("$BASE/api/device/facestore/free/get?" + common() + "&pageId=$page&pageSize=30", "POST", ""))
                .getJSONObject("result")
            catalogTotal = r.optInt("numRows")
            val items = r.getJSONArray("items")
            val faces = (0 until items.length()).map { items.getJSONObject(it) }
                .map { Face(it.getInt("id"), it.optString("name").trim(), it.optString("otaFaceName"), it.optString("imageUrl")) }
            main.post { catalog += faces.filter { f -> catalog.none { it.id == f.id } }; App.emit("Faces.catalog", emptyList()) }
        }.onFailure { main.post { App.emit("Faces.error", listOf(it.toString())) } }
    }

    fun install(ctx: Context, f: Face) {
        if (installing != null || !Watch.connected) return
        installing = f; progress = 0
        App.emit("Faces.install", listOf(f.name))
        thread {
            runCatching {
                if (f.asset != null) {
                    val zip = File(ctx.cacheDir, "faces/${f.file}.zip").apply { parentFile?.mkdirs() }
                    ctx.assets.open(f.asset).use { i -> zip.outputStream().use { i.copyTo(it) } }
                    main.post { transfer(zip, f) }
                    return@thread
                }
                val d = JSONObject(http("$BASE/api/device/face/v4/get?id=${f.id}&language=es&appFaceVersion=$FACE_VERSION" +
                    "&supportFaceVersion=$FACE_VERSION&otaVersion=1&withoutVipFlag=false&openFaceTestFlag=false", "GET", null))
                    .getJSONObject("result").getJSONObject("otaFaceVersion")
                val zip = File(ctx.cacheDir, "faces/${f.file}.zip").apply { parentFile?.mkdirs() }
                val bytes = URL(d.getString("linkUrl")).readBytes()
                val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
                check(d.optString("md5").isEmpty() || md5.equals(d.optString("md5"), true)) { "descarga corrupta" }
                zip.writeBytes(bytes)
                main.post { transfer(zip, f) }
            }.onFailure { main.post { done(false, it.toString()) } }
        }
    }

    private fun transfer(zip: File, f: Face) {
        BLEManager.startSetPlateFileToWatch(WatchPlateSetConfig().apply {
            filePath = zip.path
            uniqueID = f.file
            isOnlyTranslateWatchFile = false // el SDK la deja puesta al terminar
            isAutoProcessDiskDefrag = true
            stateListener = object : WatchPlateCallBack.IAutoSetPlateCallBack {
                override fun onStart() {}
                override fun onProgress(p: Int) { progress = p; App.emit("Faces.progress", listOf(p)) }
                override fun onSuccess() = done(true, null)
                override fun onFailed() = done(false, "transferencia")
            }
            errorCallback = WatchPlateCallBack.ISetPlatErrorCallback { code -> done(false, if (code == 21) "sin espacio" else "código $code") }
        })
    }

    private fun done(ok: Boolean, error: String?) {
        if (installing == null) return
        installing?.let { known[it.file] = it }
        installing = null; progress = 0
        App.emit(if (ok) "Faces.installed" else "Faces.failed", listOfNotNull(error))
        refresh()
    }

    fun use(name: String) = BLEManager.setWatchPlate(name)
    fun delete(name: String) = BLEManager.deleteWatchPlate(name)

    /** Libera espacio borrando [old] e instala [f] cuando el reloj confirme el borrado. */
    fun replace(old: String, f: Face) { pending = f; delete(old) }

    fun onDeleted(ctx: Context, ok: Boolean) {
        val f = pending ?: return
        pending = null
        if (ok) install(ctx, f) else App.emit("Faces.failed", listOf("no se pudo borrar la esfera"))
    }

    private fun common() = "deviceName=DW26&deviceId=$DEVICE_ID&language=es&supportFaceVersion=$FACE_VERSION&appFaceVersion=$FACE_VERSION&otaVersion=1"

    private fun http(url: String, method: String, body: String?, json: Boolean = false): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("appkey", APP_KEY)
        c.setRequestProperty("Authorization", "")
        c.setRequestProperty("X-HB-Client-Type", "ayb-android")
        if (body != null && method == "POST") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (json) "application/json" else "application/x-www-form-urlencoded; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    // ---------- imágenes (caché en memoria + disco, sin librerías) ----------

    private val mem = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Carga [url] y entrega el bitmap a [into] (que se compara por tag para no pintar en una vista reciclada). */
    fun load(ctx: Context, url: String?, into: WatchMock) {
        into.tag = url
        into.bitmap = null
        if (url.isNullOrEmpty()) return
        mem.get(url)?.let { into.bitmap = it; return }
        thread {
            val f = File(ctx.cacheDir, "img/" + URLEncoder.encode(url, "UTF-8").takeLast(120)).apply { parentFile?.mkdirs() }
            val bmp = runCatching {
                if (!f.exists()) {
                    // assets locales (esferas propias) o red
                    val bytes = if (url.startsWith("asset:")) ctx.assets.open(url.removePrefix("asset:")).readBytes() else URL(url).readBytes()
                    f.writeBytes(bytes)
                }
                BitmapFactory.decodeFile(f.path)
            }.getOrNull() ?: return@thread
            mem.put(url, bmp)
            main.post { if (into.tag == url) into.bitmap = bmp }
        }
    }
}
