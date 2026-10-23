package com.metalsyntax.dw26

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.format.DateUtils
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.ido.ble.BLEManager
import com.ido.ble.bluetooth.device.BLEDevice
import com.metalsyntax.dw26.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val es = Locale("es")
    private val found = linkedMapOf<String, BLEDevice>()
    private lateinit var adapter: ArrayAdapter<String>
    private val tiles = mutableMapOf<String, View>()
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { Watch.refresh(); ticker.postDelayed(this, 30_000) }
    }
    private val onSdk: SdkListener = { method, args -> handle(method, args) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.nav.setOnItemSelectedListener { show(it.itemId); true }
        b.syncBtn.setOnClickListener { Watch.sync(); render() }

        tile("hr", R.drawable.ic_heart, R.color.heart, "Ritmo cardíaco")
        tile("kcal", R.drawable.ic_fire, R.color.kcal, "Calorías")
        tile("dist", R.drawable.ic_route, R.color.distance, "Distancia")
        tile("sleep", R.drawable.ic_moon, R.color.sleep, "Sueño")
        tile("stress", R.drawable.ic_psych, R.color.stress, "Estrés")
        tile("spo2", R.drawable.ic_air, R.color.spo2, "Oxígeno (SpO₂)")

        buildWatchRows()
        setupPairing()

        requestPermissions(
            if (Build.VERSION.SDK_INT >= 31) arrayOf(
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
        )
        App.listen(onSdk)
        WatchService.start(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        ticker.post(tick)
        render()
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        App.unlisten(onSdk)
        super.onDestroy()
    }

    private fun handle(method: String, args: List<Any?>) {
        when (method) {
            "ScanCallBack.onFindDevice" -> return addDevice(args[0] as BLEDevice)
            "ConnectCallBack.onConnectSuccess" -> if (!Watch.bound) BLEManager.bind()
            "BindCallBack.onSuccess" -> { WatchService.start(this); b.nav.selectedItemId = R.id.tab_home }
            "BindCallBack.onFailed", "BindCallBack.onReject" -> toast("No se pudo vincular. Acepta en el reloj e inténtalo de nuevo.")
            "UnbindCallBack.onSuccess" -> stopService(Intent(this, WatchService::class.java))
        }
        render()
    }

    // ---------- navegación ----------

    private fun show(id: Int) {
        b.pageHome.visibility = if (id == R.id.tab_home) View.VISIBLE else View.GONE
        b.pageHealth.visibility = if (id == R.id.tab_health) View.VISIBLE else View.GONE
        b.pageWatch.visibility = if (id == R.id.tab_watch) View.VISIBLE else View.GONE
        render()
    }

    // ---------- pintar estado ----------

    private fun render() {
        val paired = Watch.bound
        b.pagePair.visibility = if (paired) View.GONE else View.VISIBLE
        b.nav.visibility = if (paired) View.VISIBLE else View.GONE
        if (!paired) {
            b.pageHome.visibility = View.GONE; b.pageHealth.visibility = View.GONE; b.pageWatch.visibility = View.GONE
        } else if (b.pageHome.visibility != View.VISIBLE && b.pageHealth.visibility != View.VISIBLE && b.pageWatch.visibility != View.VISIBLE) {
            b.pageHome.visibility = View.VISIBLE
        }

        b.statusPill.text = when {
            !paired -> "Sin reloj"
            Watch.syncing -> "Sincronizando ${Watch.syncProgress}%"
            Watch.connected -> "Conectado" + if (Watch.battery >= 0) " · ${Watch.battery}%" else ""
            else -> "Desconectado"
        }
        b.statusPill.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.drawable.dot)?.mutate()?.apply {
            setTint(getColor(if (Watch.connected) R.color.ok else R.color.dw_muted))
        }, null, null, null)

        renderHome()
        renderHealth()
        renderWatch()
    }

    private fun renderHome() {
        val today = LocalDate.now()
        val day = Health.day(today)
        val live = Watch.live
        val steps = maxOf(live?.totalStep?.toLong() ?: 0, day?.steps ?: 0)
        b.today.text = "Hoy, " + today.dayOfMonth + " de " + today.month.getDisplayName(TextStyle.FULL, es)
        b.steps.text = fmt(steps)
        b.goal.text = "de ${fmt(Watch.stepGoal.toLong())} pasos"
        b.ring.progress = steps / Watch.stepGoal.toFloat()
        b.syncBtn.isEnabled = Watch.connected && !Watch.syncing
        b.syncBtn.text = if (Watch.syncing) "Sincronizando… ${Watch.syncProgress}%" else "Sincronizar"
        b.lastSync.text = if (Watch.lastSync == 0L) "Aún sin sincronizar"
        else "Última sincronización " + DateUtils.getRelativeTimeSpanString(Watch.lastSync).toString().lowercase(es)

        setTile("hr", live?.heartRate?.takeIf { it > 0 }?.toString() ?: day?.hrAvg?.takeIf { it > 0 }?.toString(), "lpm",
            day?.restingHr?.takeIf { it > 0 }?.let { "En reposo $it lpm" })
        setTile("kcal", maxOf(live?.totalCalories ?: 0, day?.kcal ?: 0).takeIf { it > 0 }?.toString(), "kcal", null)
        val meters = maxOf(live?.totalDistances ?: 0, day?.distanceM ?: 0)
        setTile("dist", if (meters > 0) "%.2f".format(es, meters / 1000f) else null, "km", null)
        setTile("sleep", day?.sleepMin?.takeIf { it > 0 }?.let { hm(it) }, null, day?.sleepScore?.takeIf { it > 0 }?.let { "Puntuación $it" })
        setTile("stress", day?.stressAvg?.takeIf { it > 0 }?.toString(), null, day?.stressAvg?.takeIf { it > 0 }?.let { stressLabel(it) })
        setTile("spo2", day?.spo2Avg?.takeIf { it > 0 }?.toString(), "%", null)
    }

    private fun renderHealth() {
        val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase(es) }
        val steps = days.map { d -> (Health.day(d)?.steps ?: 0).let { if (d == LocalDate.now()) maxOf(it, Watch.live?.totalStep?.toLong() ?: 0) else it }.toFloat() }
        b.stepsChart.bars(steps, labels)
        b.stepsWeek.text = steps.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { "Promedio ${fmt(it.average().toLong())} pasos al día" } ?: "Sin datos todavía"

        b.sleepChart.color = getColor(R.color.sleep)
        b.sleepChart.bars(days.map { (Health.day(it)?.sleepMin ?: 0) / 60f }, labels)
        val last = days.reversed().firstNotNullOfOrNull { d -> Health.day(d)?.takeIf { it.sleepMin > 0 } }
        b.sleepLast.text = last?.let { hm(it.sleepMin) } ?: "Sin datos de sueño"
        b.sleepDetail.text = last?.let {
            "${it.bedTime} → ${it.upTime}  ·  Profundo ${hm(it.deepMin)} · Ligero ${hm(it.lightMin)} · REM ${hm(it.remMin)} · Despierto ${hm(it.wakeMin)}"
        } ?: "Usa el reloj al dormir y sincroniza por la mañana."

        b.hrChart.color = getColor(R.color.heart)
        val hr = Health.day(LocalDate.now())
        val pts = hr?.hrPoints.orEmpty()
        b.hrChart.line(pts.map { it[0] / 86400f }, pts.map { it[1].toFloat() }, listOf("0h", "6h", "12h", "18h"))
        b.hrSummary.text = if (pts.isEmpty()) "Sin mediciones hoy todavía"
        else "Mín ${hr!!.hrMin} · Prom ${hr.hrAvg} · Máx ${hr.hrMax} lpm" + if (hr.restingHr > 0) " · Reposo ${hr.restingHr}" else ""
    }

    private fun renderWatch() {
        b.watchState.text = when {
            Watch.connected -> "Conectado" + if (Watch.battery >= 0) " · batería ${Watch.battery}%" else ""
            Watch.bound -> "Desconectado · se reconectará solo"
            else -> "Sin vincular"
        }
        b.watchInfo.text = Watch.mac
        rowSub("notify", when {
            !NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) -> "Falta dar permiso"
            Watch.notifyEnabled -> "WhatsApp, Telegram, SMS y más"
            else -> "Desactivadas"
        })
        rowSub("goal", "${fmt(Watch.stepGoal.toLong())} pasos")
        (rows["notify"]?.findViewById<MaterialSwitch>(R.id.toggle))?.isChecked = Watch.notifyEnabled
    }

    // ---------- pantalla Reloj ----------

    private val rows = mutableMapOf<String, View>()

    private fun buildWatchRows() {
        row("find", R.drawable.ic_vibrate, "Buscar mi reloj", "Lo hace vibrar") {
            if (Watch.connected) { BLEManager.startFindDevice(); toast("El reloj está vibrando") } else toast("El reloj no está conectado")
        }
        row("sync", R.drawable.ic_sync, "Sincronizar ahora", null) { Watch.sync(); render() }
        row("time", R.drawable.ic_clock, "Ajustar hora del reloj", "Usa la hora del teléfono") { BLEManager.setTime(); toast("Hora enviada al reloj") }
        row("notify", R.drawable.ic_bell, "Notificaciones", null, toggle = true) {
            if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                Watch.notifyEnabled = !Watch.notifyEnabled; render()
            }
        }
        row("goal", R.drawable.ic_flag, "Meta diaria de pasos", null) { editGoal() }
        row("log", R.drawable.ic_bug, "Registro técnico", "Para diagnóstico") {
            MaterialAlertDialogBuilder(this).setTitle("Registro técnico")
                .setMessage(App.history.toList().takeLast(120).joinToString("\n"))
                .setPositiveButton("Cerrar", null).show()
        }
        row("unbind", R.drawable.ic_unlink, "Desvincular reloj", null) {
            MaterialAlertDialogBuilder(this).setTitle("¿Desvincular el DW26?")
                .setMessage("El reloj dejará de sincronizarse con este teléfono hasta que lo vuelvas a vincular.")
                .setPositiveButton("Desvincular") { _, _ -> BLEManager.unbind() }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun row(key: String, icon: Int, title: String, sub: String?, toggle: Boolean = false, onClick: () -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_row, b.rows, false)
        v.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.title).text = title
        if (toggle) {
            v.findViewById<View>(R.id.chevron).visibility = View.GONE
            v.findViewById<MaterialSwitch>(R.id.toggle).apply { visibility = View.VISIBLE; isClickable = false }
        }
        if (key == "unbind") v.findViewById<TextView>(R.id.title).setTextColor(getColor(R.color.heart))
        v.setOnClickListener { onClick() }
        rows[key] = v
        b.rows.addView(v)
        rowSub(key, sub)
    }

    private fun rowSub(key: String, sub: String?) {
        rows[key]?.findViewById<TextView>(R.id.sub)?.apply { text = sub; visibility = if (sub == null) View.GONE else View.VISIBLE }
    }

    private fun editGoal() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText(Watch.stepGoal.toString()); setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this).setTitle("Meta diaria de pasos").setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                input.text.toString().toIntOrNull()?.takeIf { it in 1000..50000 }?.let { Watch.stepGoal = it; render() }
                    ?: toast("Escribe un número entre 1.000 y 50.000")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ---------- vincular ----------

    private fun setupPairing() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, mutableListOf())
        b.devices.adapter = adapter
        b.scanBtn.setOnClickListener {
            found.clear(); adapter.clear()
            // un reloj emparejado y ya conectado al sistema no se anuncia: se ofrece por MAC
            @Suppress("MissingPermission")
            getSystemService(BluetoothManager::class.java).adapter.bondedDevices
                .filter { it.type != BluetoothDevice.DEVICE_TYPE_CLASSIC && it.name?.startsWith("DW") == true }
                .forEach { addDevice(BLEDevice().apply { mDeviceName = it.name; mDeviceAddress = it.address }) }
            BLEManager.startScanDevices()
            b.scanBtn.text = "Buscando…"
            ticker.postDelayed({ b.scanBtn.text = "Buscar de nuevo" }, 10_000)
        }
        b.devices.setOnItemClickListener { _, _, pos, _ ->
            val dev = found.values.elementAt(pos)
            BLEManager.stopScanDevices()
            b.scanBtn.text = "Vinculando con ${dev.mDeviceName}…"
            BLEManager.connect(dev)
        }
    }

    private fun addDevice(d: BLEDevice) {
        if (d.mDeviceName.isNullOrBlank()) return
        val known = found.containsKey(d.mDeviceAddress)
        found[d.mDeviceAddress] = d // el del escaneo trae id/plataforma que el emparejado no tiene
        if (!known) adapter.add("${d.mDeviceName}  ·  ${d.mDeviceAddress}")
    }

    // ---------- utilidades ----------

    private fun tile(key: String, icon: Int, color: Int, label: String) {
        val v = layoutInflater.inflate(R.layout.item_tile, b.tiles, false)
        v.layoutParams = GridLayout.LayoutParams(v.layoutParams).apply {
            width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        v.findViewById<ImageView>(R.id.icon).apply { setImageResource(icon); setColorFilter(getColor(color)) }
        v.findViewById<TextView>(R.id.label).text = label
        tiles[key] = v
        b.tiles.addView(v)
    }

    private fun setTile(key: String, value: String?, unit: String?, sub: String?) {
        val v = tiles[key] ?: return
        v.findViewById<TextView>(R.id.value).text = if (value == null) "--" else value + (unit?.let { " $it" } ?: "")
        v.findViewById<TextView>(R.id.sub).apply { text = sub ?: " " }
    }

    private fun fmt(n: Long) = "%,d".format(es, n)
    private fun hm(min: Int) = "${min / 60} h ${min % 60} min"
    private fun stressLabel(v: Int) = when { v < 30 -> "Relajado"; v < 60 -> "Normal"; v < 80 -> "Moderado"; else -> "Alto" }
    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
}
