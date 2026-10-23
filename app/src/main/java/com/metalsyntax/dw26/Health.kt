package com.metalsyntax.dw26

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.ido.ble.data.manage.database.HealthHeartRateSecond
import com.ido.ble.data.manage.database.HealthPressure
import com.ido.ble.data.manage.database.HealthPressureItem
import com.ido.ble.data.manage.database.HealthSpO2
import com.ido.ble.data.manage.database.HealthSpO2Item
import com.ido.ble.data.manage.database.HealthSleepV3
import com.ido.ble.data.manage.database.HealthSportV3
import java.io.File
import java.time.LocalDate

/** Resumen de un día. El SDK no guarda en su base los datos V3 del DW26, así que los guardamos nosotros. */
data class Day(
    var steps: Long = 0,
    var kcal: Int = 0,
    var distanceM: Int = 0,
    var activeMin: Int = 0,
    var sleepMin: Int = 0,
    var deepMin: Int = 0,
    var lightMin: Int = 0,
    var remMin: Int = 0,
    var wakeMin: Int = 0,
    var sleepScore: Int = 0,
    var bedTime: String? = null,
    var upTime: String? = null,
    var restingHr: Int = 0,
    var hrMin: Int = 0,
    var hrMax: Int = 0,
    var hrAvg: Int = 0,
    /** pares [segundo del día, bpm] */
    var hrPoints: List<IntArray> = emptyList(),
    var stressAvg: Int = 0,
    var spo2Avg: Int = 0,
)

object Health {
    private lateinit var file: File
    private val days = sortedMapOf<String, Day>()

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "health.json")
        runCatching {
            val type = object : TypeToken<Map<String, Day>>() {}.type
            days.putAll(App.gson.fromJson<Map<String, Day>>(file.readText(), type))
        }
    }

    fun day(d: LocalDate): Day? = days[d.toString()]

    private fun edit(y: Int, m: Int, d: Int, block: Day.() -> Unit) {
        if (y < 2000) return
        days.getOrPut(LocalDate.of(y, m, d).toString()) { Day() }.block()
        file.writeText(App.json(days))
    }

    /** Recibe los callbacks de sincronización (ISyncDataListener) que interesan. */
    fun onSync(method: String, args: List<Any?>) {
        when (method) {
            "ISyncDataListener.onGetHealthSportV3Data" -> (args[0] as HealthSportV3).run {
                edit(year, month, day) {
                    steps = total_step; kcal = total_activity_calories
                    distanceM = total_distances; activeMin = total_active_time / 60
                }
            }
            "ISyncDataListener.onGetHealthSleepV3Data" -> (args[0] as HealthSleepV3).run {
                if (total_sleep_time_mins == 0) return
                // el sueño se anota en el día en que uno se levanta
                edit(get_up_year, get_up_month, get_up_day) {
                    sleepMin = total_sleep_time_mins; deepMin = deep_mins; lightMin = light_mins
                    remMin = rem_mins; wakeMin = wake_mins; sleepScore = sleep_score
                    bedTime = "%02d:%02d".format(fall_asleep_hour, fall_asleep_minte)
                    upTime = "%02d:%02d".format(get_up_hour, get_up_minte)
                }
            }
            "ISyncDataListener.onGetHealthHeartRateSecondData" -> (args[0] as HealthHeartRateSecond).run {
                // offset = segundos desde la muestra anterior (255 con valor 0 = relleno de huecos largos)
                var second = startTime
                val pts = items.orEmpty().mapNotNull { second += it.offset; if (it.heartRateVal > 0) intArrayOf(second, it.heartRateVal) else null }
                if (pts.isEmpty() && silentHR == 0) return
                edit(year, month, day) {
                    hrPoints = (hrPoints + pts).distinctBy { it[0] }.sortedBy { it[0] }
                    if (silentHR > 0) restingHr = silentHR
                    hrPoints.map { it[1] }.takeIf { it.isNotEmpty() }?.let { v ->
                        hrMin = v.min(); hrMax = v.max(); hrAvg = v.average().toInt()
                    }
                }
            }
            "ISyncDataListener.onGetHealthPressureData" -> {
                val h = args[0] as HealthPressure
                avg((args[1] as List<*>).map { (it as HealthPressureItem).value })?.let { edit(h.year, h.month, h.day) { stressAvg = it } }
            }
            "ISyncDataListener.onGetHealthSpO2Data" -> {
                val h = args[0] as HealthSpO2
                avg((args[1] as List<*>).map { (it as HealthSpO2Item).value })?.let { edit(h.year, h.month, h.day) { spo2Avg = it } }
            }
        }
    }

    /** Promedio ignorando 0 (sin medición) y 255 (relleno de huecos). */
    private fun avg(values: List<Int>) = values.filter { it in 1..254 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}
