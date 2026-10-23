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
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.content.pm.PackageManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ido.ble.protocol.model.AlarmV3
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
        b.moreFaces.setOnClickListener { Faces.loadMore() }

        tile("hr", R.drawable.ic_heart, R.color.heart, "Pulso")
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
            "Faces.installed" -> toast("Esfera instalada")
            "Faces.failed" -> if (args.firstOrNull() == "sin espacio") askReplace() else toast("No se pudo instalar la esfera: ${args.firstOrNull() ?: ""}")
            "GetDeviceParaCallBack.onGetAlarmV3" -> if (alarmSheet?.isShowing == true) showAlarms()
        }
        render()
    }

    // ---------- navegación ----------

    private fun show(id: Int) {
        b.pageHome.visibility = if (id == R.id.tab_home) View.VISIBLE else View.GONE
        b.pageHealth.visibility = if (id == R.id.tab_health) View.VISIBLE else View.GONE
        b.pageWatch.visibility = if (id == R.id.tab_watch) View.VISIBLE else View.GONE
        b.pageFaces.visibility = if (id == R.id.tab_faces) View.VISIBLE else View.GONE
        if (id == R.id.tab_faces) {
            Faces.refresh()
            if (Faces.catalog.isEmpty()) Faces.loadMore()
        }
        render()
    }

    // ---------- pintar estado ----------

    private fun render() {
        val paired = Watch.bound
        b.pagePair.visibility = if (paired) View.GONE else View.VISIBLE
        b.navCard.visibility = if (paired) View.VISIBLE else View.GONE
        if (!paired) {
            b.pageHome.visibility = View.GONE; b.pageHealth.visibility = View.GONE; b.pageWatch.visibility = View.GONE; b.pageFaces.visibility = View.GONE
        } else if (listOf(b.pageHome, b.pageHealth, b.pageWatch, b.pageFaces).none { it.visibility == View.VISIBLE }) {
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
        if (b.pageFaces.visibility == View.VISIBLE) renderFaces()
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
        val listener = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        rowSub("notify", if (!listener) "Falta dar permiso" else if (Watch.notifyEnabled) "WhatsApp, Telegram, SMS y más" else "Desactivadas")
        rowSub("alarms", when {
            !Alarms.loaded -> "Toca para ver las alarmas del reloj"
            Alarms.list.isEmpty() -> "Sin alarmas"
            else -> Alarms.list.filter { it.isOn_off }.joinToString("  ") { "%02d:%02d".format(it.hour, it.minute) }.ifEmpty { "Todas apagadas" }
        })
        rowSub("weather", if (!Watch.weatherEnabled) "Desactivado" else Weather.summary ?: "Se actualiza cada hora")
        rowSub("music", if (!listener) "Necesita el permiso de notificaciones" else if (!Watch.musicEnabled) "Desactivado" else Music.nowPlaying ?: "Muestra la canción y controla la música")
        rowSub("calls", if (!Calls.granted(this)) "Falta dar permiso" else if (Watch.callsEnabled) "Entrantes y perdidas" else "Desactivadas")
        toggle("notify", listener && Watch.notifyEnabled)
        toggle("weather", Watch.weatherEnabled)
        toggle("music", listener && Watch.musicEnabled)
        toggle("calls", Calls.granted(this) && Watch.callsEnabled)
        goalChips?.let { g -> (0 until g.childCount).map { g.getChildAt(it) as Chip }.forEach { it.isChecked = it.tag == Watch.stepGoal } }
    }

    // ---------- pantalla Reloj ----------

    private val rows = mutableMapOf<String, View>()
    private var goalChips: ChipGroup? = null
    private val notifAccess get() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun buildWatchRows() {
        val f = b.rowsFeatures
        row(f, "alarms", R.drawable.ic_alarm, "Alarmas", null) { Alarms.load(); showAlarms() }
        row(f, "weather", R.drawable.ic_cloud, "Clima en el reloj", null, toggle = true) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 2)
            } else {
                Watch.weatherEnabled = !Watch.weatherEnabled
                Weather.push(this)
            }
            render()
        }
        row(f, "music", R.drawable.ic_music, "Música", null, toggle = true) {
            if (!notifAccess) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else { Watch.musicEnabled = !Watch.musicEnabled; Music.onConnected() }
            render()
        }
        row(f, "calls", R.drawable.ic_call_missed, "Llamadas", null, toggle = true) {
            if (!Calls.granted(this)) requestPermissions(Calls.permissions, 3)
            else Watch.callsEnabled = !Watch.callsEnabled
            render()
        }
        row(f, "notify", R.drawable.ic_bell, "Notificaciones", null, toggle = true) {
            if (!notifAccess) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else Watch.notifyEnabled = !Watch.notifyEnabled
            render()
        }

        val r = b.rows
        row(r, "find", R.drawable.ic_vibrate, "Buscar mi reloj", "Lo hace vibrar hasta 15 segundos") { findWatch() }
        row(r, "sync", R.drawable.ic_sync, "Sincronizar ahora", null) { Watch.sync(); render() }
        row(r, "time", R.drawable.ic_clock, "Ajustar hora del reloj", "Usa la hora del teléfono") { BLEManager.setTime(); toast("Hora enviada al reloj") }
        goalRow(r)
        row(r, "log", R.drawable.ic_bug, "Registro técnico", "Para diagnóstico") {
            MaterialAlertDialogBuilder(this).setTitle("Registro técnico")
                .setMessage(App.history.toList().takeLast(120).joinToString("\n"))
                .setPositiveButton("Cerrar", null).show()
        }
        row(r, "unbind", R.drawable.ic_unlink, "Desvincular reloj", null) {
            MaterialAlertDialogBuilder(this).setTitle("¿Desvincular el DW26?")
                .setMessage("El reloj dejará de sincronizarse con este teléfono hasta que lo vuelvas a vincular.")
                .setPositiveButton("Desvincular") { _, _ -> BLEManager.unbind() }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun row(parent: LinearLayout, key: String, icon: Int, title: String, sub: String?, toggle: Boolean = false, onClick: () -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_row, parent, false)
        v.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.title).text = title
        if (toggle) {
            v.findViewById<View>(R.id.chevron).visibility = View.GONE
            v.findViewById<MaterialSwitch>(R.id.toggle).apply { visibility = View.VISIBLE; isClickable = false }
        }
        if (key == "unbind") v.findViewById<TextView>(R.id.title).setTextColor(getColor(R.color.heart))
        v.setOnClickListener { onClick() }
        rows[key] = v
        parent.addView(v)
        rowSub(key, sub)
    }

    private fun rowSub(key: String, sub: String?) {
        rows[key]?.findViewById<TextView>(R.id.sub)?.apply { text = sub; visibility = if (sub == null) View.GONE else View.VISIBLE }
    }

    private fun toggle(key: String, on: Boolean) {
        rows[key]?.findViewById<MaterialSwitch>(R.id.toggle)?.isChecked = on
    }

    /** Meta de pasos: fila con chips de valores fijos, sin diálogo. */
    private fun goalRow(parent: LinearLayout) {
        row(parent, "goal", R.drawable.ic_flag, "Meta diaria de pasos", null) {}
        rows["goal"]!!.findViewById<View>(R.id.chevron).visibility = View.GONE
        val group = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true; chipSpacingHorizontal = dp(8) }
        listOf(5000, 6000, 8000, 10000, 12000, 15000).forEach { steps ->
            group.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = fmt(steps.toLong()); tag = steps; isCheckable = true
                setOnClickListener { Watch.applyStepGoal(steps); render() }
            })
        }
        goalChips = group
        parent.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(76), 0, dp(20), dp(12)); clipToPadding = false
            addView(group)
        })
    }

    private fun findWatch() {
        if (!Watch.connected) return toast("El reloj no está conectado")
        if (Watch.functions?.findDevice == false) return toast("Tu reloj no admite esta función")
        Watch.findDevice()
        val d = MaterialAlertDialogBuilder(this).setTitle("Buscando tu reloj…")
            .setMessage("El DW26 está vibrando. Puedes detenerlo desde el reloj.")
            .setPositiveButton("Lo encontré") { _, _ -> Watch.stopFindDevice() }
            .setOnDismissListener { Watch.stopFindDevice() }
            .show()
        ticker.postDelayed({ if (d.isShowing) d.dismiss() }, 15_000)
    }

    // ---------- esferas ----------

    private var facesKey = ""
    private var marketShown = 0

    private fun renderFaces() {
        val p = Faces.installed
        val current = p?.now_show_watch_name?.substringBefore('.')
        val cur = current?.let { Faces.known[it] }
        if (b.currentWatch.tag != cur?.image) Faces.load(this, cur?.image, b.currentWatch)
        b.currentWatch.placeholder = getDrawable(R.drawable.ic_face)?.mutate()?.apply { setTint(0x66FFFFFF) }
        b.currentName.text = cur?.name ?: current?.let { if (Faces.isBuiltIn(it)) "Esfera integrada" else it } ?: "DW26"
        b.facesInfo.text = when {
            Faces.installing != null -> "Instalando «${Faces.installing!!.name}»… ${Faces.progress}%"
            !Watch.connected -> "Conecta el reloj para ver sus esferas"
            p == null -> "Leyendo esferas del reloj…"
            else -> "En uso · ${p.user_cloud_watch_num} de ${p.cloud_watch_num} esferas instaladas"
        }
        // reconstruir las grillas solo si cambió algo (evita parpadeo en cada evento)
        val key = "$current|${p?.item?.joinToString { it.name }}|${Faces.known.size}|${Faces.installing?.file}"
        if (key != facesKey) {
            facesKey = key
            b.facesGrid.removeAllViews()
            p?.item.orEmpty().forEach { item ->
                val name = item.name.substringBefore('.')
                val k = Faces.known[name]
                val removable = !Faces.isBuiltIn(name) && !Faces.isCurrent(name)
                faceCell(b.facesGrid, k?.name ?: if (Faces.isBuiltIn(name)) "Integrada" else name, k?.image, Faces.isCurrent(name),
                    badge = if (removable) R.drawable.badge_delete to R.drawable.ic_close else null,
                    onBadge = { confirmDelete(item.name, k?.name ?: name) },
                    onClick = { if (!Faces.isCurrent(name)) { Faces.use(item.name); toast("Cambiando a «${k?.name ?: name}»") } })
            }
            b.exclusiveGrid.removeAllViews()
            Faces.exclusive.forEach { f ->
                val inWatch = p?.item.orEmpty().any { it.name.substringBefore('.') == f.file }
                faceCell(b.exclusiveGrid, f.name, f.image, Faces.isCurrent(f.file),
                    badge = if (inWatch) null else R.drawable.badge_add to R.drawable.ic_add,
                    onBadge = { confirmInstall(f) },
                    onClick = { if (inWatch) Faces.use("${f.file}.iwf") else confirmInstall(f) })
            }
            // celdas vacías para que una sola esfera no ocupe todo el ancho
            repeat((3 - Faces.exclusive.size % 3) % 3) {
                b.exclusiveGrid.addView(View(this), GridLayout.LayoutParams().apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
            }
        }
        b.marketInfo.text = if (Faces.catalog.isEmpty()) "Cargando catálogo…" else "${Faces.catalogTotal} esferas gratis compatibles con el DW26"
        if (Faces.catalog.size != marketShown) {
            Faces.catalog.drop(marketShown).forEach { f ->
                faceCell(b.marketGrid, f.name, f.image, false, R.drawable.badge_add to R.drawable.ic_add, { confirmInstall(f) }) { confirmInstall(f) }
            }
            marketShown = Faces.catalog.size
        }
    }

    private fun faceCell(grid: GridLayout, label: String, image: String?, current: Boolean,
                         badge: Pair<Int, Int>?, onBadge: () -> Unit, onClick: () -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_face, grid, false)
        v.layoutParams = GridLayout.LayoutParams(v.layoutParams as ViewGroup.MarginLayoutParams).apply { width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }
        val mock = v.findViewById<WatchMock>(R.id.image)
        mock.inUse = current
        mock.placeholder = getDrawable(R.drawable.ic_face)?.mutate()?.apply { setTint(0x66FFFFFF) }
        Faces.load(this, image, mock)
        v.findViewById<TextView>(R.id.name).apply {
            text = label
            if (current) { setTextColor(getColor(R.color.dw_blue)); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        }
        badge?.let { (bg, icon) ->
            v.findViewById<ImageView>(R.id.badge).apply {
                visibility = View.VISIBLE; setBackgroundResource(bg); setImageResource(icon); setColorFilter(0xFFFFFFFF.toInt())
                setOnClickListener { onBadge() }
            }
        }
        v.setOnClickListener { onClick() }
        grid.addView(v)
    }

    private fun confirmDelete(file: String, label: String) {
        MaterialAlertDialogBuilder(this).setTitle("¿Quitar «$label» del reloj?")
            .setMessage("Libera espacio para otras esferas. Puedes volver a agregarla cuando quieras.")
            .setPositiveButton("Quitar") { _, _ -> Faces.delete(file); toast("Quitando «$label»…") }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun confirmInstall(f: Face) {
        if (!Watch.connected) return toast("Conecta el reloj primero")
        if (Faces.installing != null) return toast("Espera a que termine la instalación actual")
        val mock = WatchMock(this)
        Faces.load(this, f.image, mock)
        MaterialAlertDialogBuilder(this).setTitle(f.name)
            .setView(FrameLayout(this).apply { setPadding(dp(72), dp(8), dp(72), 0); addView(mock) })
            .setPositiveButton("Agregar al reloj") { _, _ -> lastAttempt = f; Faces.install(this, f); b.pageFaces.scrollTo(0, 0); render() }
            .setNegativeButton("Cancelar", null).show()
    }

    private var lastAttempt: Face? = null

    /** El reloj no tiene espacio: elegir cuál esfera reemplazar (como hace VeryFit, pero preguntando). */
    private fun askReplace() {
        val f = lastAttempt ?: return
        val options = Faces.installed?.item.orEmpty().map { it.name }
            .filter { !Faces.isCurrent(it) && !Faces.isBuiltIn(it) }
        if (options.isEmpty()) return toast("No hay espacio en el reloj")
        val labels = options.map { Faces.known[it.substringBefore('.')]?.name ?: it.substringBefore('.') }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle("No hay espacio en el reloj")
            .setItems(labels) { _, i -> Faces.replace(options[i], f); toast("Reemplazando «${labels[i]}» por «${f.name}»") }
            .setNegativeButton("Cancelar", null)
            .show()
        toast("Elige qué esfera reemplazar por «${f.name}»")
    }

    // ---------- alarmas ----------

    private var alarmSheet: BottomSheetDialog? = null

    private fun showAlarms() {
        val sheet = alarmSheet ?: BottomSheetDialog(this).also { alarmSheet = it }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }
        box.addView(TextView(this, null, 0, R.style.DW_Value).apply { text = "Alarmas" })
        if (!Watch.connected) box.addView(TextView(this, null, 0, R.style.DW_Label).apply { text = "Conecta el reloj para ver y cambiar sus alarmas." })
        else if (!Alarms.loaded) box.addView(TextView(this, null, 0, R.style.DW_Label).apply { text = "Leyendo alarmas del reloj…" })
        else if (Alarms.list.isEmpty()) box.addView(TextView(this, null, 0, R.style.DW_Label).apply { text = "No hay alarmas. Agrega una." })
        Alarms.list.forEach { a ->
            val v = layoutInflater.inflate(R.layout.item_row, box, false)
            v.setPadding(0, 0, 0, 0)
            v.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_alarm)
            v.findViewById<TextView>(R.id.title).apply { text = "%02d:%02d".format(a.hour, a.minute); textSize = 24f }
            v.findViewById<TextView>(R.id.sub).apply { text = Alarms.days(a); visibility = View.VISIBLE }
            v.findViewById<View>(R.id.chevron).visibility = View.GONE
            v.findViewById<MaterialSwitch>(R.id.toggle).apply {
                visibility = View.VISIBLE; isChecked = a.isOn_off
                setOnCheckedChangeListener { _, on -> a.setOn_off(on); Alarms.save(Alarms.list) }
            }
            v.setOnClickListener { editAlarm(a) }
            box.addView(v)
        }
        if (Watch.connected && Alarms.loaded && Alarms.list.size < Alarms.MAX) {
            box.addView(MaterialButton(this).apply {
                text = "Agregar alarma"; setIconResource(R.drawable.ic_add)
                layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16) }
                setOnClickListener { pickTime(7, 0) { h, m -> pickDays(Alarms.new(h, m)) { Alarms.save(Alarms.list + it) } } }
            })
        }
        sheet.setContentView(ScrollView(this).apply { addView(box) })
        if (!sheet.isShowing) sheet.show()
    }

    private fun editAlarm(a: AlarmV3) {
        MaterialAlertDialogBuilder(this).setTitle("%02d:%02d".format(a.hour, a.minute))
            .setItems(arrayOf("Cambiar hora", "Repetir", "Eliminar")) { _, which ->
                when (which) {
                    0 -> pickTime(a.hour, a.minute) { h, m -> a.hour = h; a.minute = m; Alarms.save(Alarms.list) }
                    1 -> pickDays(a) { Alarms.save(Alarms.list) }
                    2 -> Alarms.save(Alarms.list - a)
                }
            }.show()
    }

    private fun pickTime(h: Int, m: Int, done: (Int, Int) -> Unit) {
        MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(h).setMinute(m).setTitleText("Hora de la alarma").build()
            .apply { addOnPositiveButtonClickListener { done(hour, minute) } }
            .show(supportFragmentManager, "time")
    }

    private fun pickDays(a: AlarmV3, done: (AlarmV3) -> Unit) {
        val days = a.weekRepeat.copyOf()
        MaterialAlertDialogBuilder(this).setTitle("Repetir")
            .setMultiChoiceItems(arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"), days) { _, i, on -> days[i] = on }
            .setPositiveButton("Listo") { _, _ -> a.setWeekRepeat(days); done(a) }
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
        v.layoutParams = GridLayout.LayoutParams(v.layoutParams as ViewGroup.MarginLayoutParams).apply {
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(n: Long) = "%,d".format(es, n)
    private fun hm(min: Int) = "${min / 60} h ${min % 60} min"
    private fun stressLabel(v: Int) = when { v < 30 -> "Relajado"; v < 60 -> "Normal"; v < 80 -> "Moderado"; else -> "Alto" }
    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
}
