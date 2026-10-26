package com.metalsyntax.dw26

import com.htsmart.wristband2.bean.SyncDataRaw
import com.htsmart.wristband2.bean.data.SleepItemData
import com.htsmart.wristband2.packet.SyncDataParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Convierte lo que sincroniza un reloj FitCloud (htsmart) en los Day de Health, guardados con la MAC como clave. */
object FitHealth {
    private fun at(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    private fun secOfDay(t: ZonedDateTime) = t.toLocalTime().toSecondOfDay()

    fun save(key: String, raw: SyncDataRaw) {
        App.emit("FitCloud.data", listOf(raw.dataType, raw.datas?.size))
        when (raw.dataType) {
            SyncDataParser.TYPE_STEP -> SyncDataParser.parserStepData(raw.datas, raw.config).orEmpty().groupBy { at(it.timeStamp).toLocalDate() }.forEach { (d, items) ->
                Health.edit(key, d) {
                    val parts = stepParts.orEmpty().toMutableMap()
                    // distancia en km y calorías en kcal (como las guarda Zepp Active); se guardan en m y cal
                    items.forEach { parts[(it.timeStamp / 1000).toString()] = intArrayOf(it.step, (it.distance * 1000).toInt(), (it.calories * 1000).toInt()) }
                    stepParts = parts
                    steps = maxOf(steps, parts.values.sumOf { it[0] }.toLong())
                    distanceM = maxOf(distanceM, parts.values.sumOf { it[1] })
                    kcal = maxOf(kcal, parts.values.sumOf { it[2] } / 1000)
                }
            }
            SyncDataParser.TYPE_SLEEP -> SyncDataParser.parserSleepData(raw.datas, raw.config).orEmpty().forEach { s ->
                val items = s.items.orEmpty().sortedBy { it.startTime }
                if (items.isEmpty()) return@forEach
                fun min(status: Int) = items.filter { it.status == status }.sumOf { (it.endTime - it.startTime) / 60_000 }.toInt()
                val bed = at(items.first().startTime); val up = at(items.last().endTime)
                // como en el DW26: el sueño se anota en el día en que uno se levanta
                Health.edit(key, up.toLocalDate()) {
                    deepMin = min(SleepItemData.SLEEP_STATUS_DEEP); lightMin = min(SleepItemData.SLEEP_STATUS_LIGHT)
                    wakeMin = min(SleepItemData.SLEEP_STATUS_SOBER); sleepMin = deepMin + lightMin
                    bedTime = "%02d:%02d".format(bed.hour, bed.minute); upTime = "%02d:%02d".format(up.hour, up.minute)
                }
            }
            SyncDataParser.TYPE_HEART_RATE -> SyncDataParser.parserHeartRateData(raw.datas).orEmpty().filter { it.heartRate > 0 }
                .groupBy { at(it.timeStamp).toLocalDate() }.forEach { (d, items) ->
                    Health.edit(key, d) {
                        hrPoints = (hrPoints + items.map { intArrayOf(secOfDay(at(it.timeStamp)), it.heartRate) }).distinctBy { it[0] }.sortedBy { it[0] }
                        val v = hrPoints.map { it[1] }
                        hrMin = v.min(); hrMax = v.max(); hrAvg = v.average().toInt()
                    }
                }
            SyncDataParser.TYPE_OXYGEN -> SyncDataParser.parserOxygenData(raw.datas).orEmpty().filter { it.oxygen in 1..100 }
                .groupBy { at(it.timeStamp).toLocalDate() }.forEach { (d, items) ->
                    // ponytail: promedio de este lote con el anterior, no de todas las mediciones del día
                    Health.edit(key, d) { spo2Avg = mix(spo2Avg, items.map { it.oxygen }.average().toInt()) }
                }
            SyncDataParser.TYPE_PRESSURE -> SyncDataParser.parserPressureData(raw.datas).orEmpty().filter { it.pressure in 1..100 }
                .groupBy { at(it.timeStamp).toLocalDate() }.forEach { (d, items) ->
                    Health.edit(key, d) { stressAvg = mix(stressAvg, items.map { it.pressure }.average().toInt()) }
                }
            SyncDataParser.TYPE_TOTAL_DATA -> raw.datas?.takeIf { it.isNotEmpty() }?.let { SyncDataParser.parserTotalData(it) }?.let { t ->
                App.emit("FitCloud.total", listOf(t.step, t.distance, t.calorie, t.heartRate, t.deepSleep, t.lightSleep, t.timeStamp))
                Health.edit(key, LocalDate.now()) { steps = maxOf(steps, t.step.toLong()) }
            }
        }
    }

    private fun mix(old: Int, new: Int) = if (old > 0) (old + new) / 2 else new
}
