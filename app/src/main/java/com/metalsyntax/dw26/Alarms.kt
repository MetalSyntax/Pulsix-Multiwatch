package com.metalsyntax.dw26

import com.ido.ble.BLEManager
import com.ido.ble.protocol.model.AlarmV3

/**
 * Alarmas V3. El reloj tiene una tabla fija de ranuras: siempre se envía completa,
 * con las libres marcadas STATUS_NOT_DISPLAY (así lo hace VeryFit).
 */
object Alarms {
    const val MAX = 10
    var list: List<AlarmV3> = emptyList(); private set
    var loaded = false; private set

    fun load() { if (Watch.connected) BLEManager.getAlarmV3() }

    fun onLoaded(all: List<AlarmV3>) {
        list = all.filter { it.status == AlarmV3.STATUS_DISPLAY }.sortedBy { it.hour * 60 + it.minute }
        loaded = true
    }

    fun new(hour: Int, minute: Int) = AlarmV3().apply {
        status = AlarmV3.STATUS_DISPLAY; type = AlarmV3.TYPE_GETUP
        this.hour = hour; this.minute = minute; name = ""
        setOn_off(true); setWeekRepeat(BooleanArray(7))
        delay_min = 10; repeat_times = 3
    }

    fun save(items: List<AlarmV3>) {
        list = items.sortedBy { it.hour * 60 + it.minute }
        val slots = list.take(MAX).mapIndexed { i, a -> a.apply { alarm_id = i + 1 } } +
            (list.size until MAX).map { i ->
                AlarmV3().apply { alarm_id = i + 1; status = AlarmV3.STATUS_NOT_DISPLAY; delay_min = 10; repeat_times = 3 }
            }
        BLEManager.setAlarmV3(slots)
    }

    /** "L M X J V S D" con los días activos; lunes = índice 0. */
    fun days(a: AlarmV3): String {
        val r = a.weekRepeat
        return when {
            r.none { it } -> "Una vez"
            r.all { it } -> "Todos los días"
            r.take(5).all { it } && !r[5] && !r[6] -> "Lunes a viernes"
            else -> listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom").filterIndexed { i, _ -> r[i] }.joinToString(" ")
        }
    }
}
