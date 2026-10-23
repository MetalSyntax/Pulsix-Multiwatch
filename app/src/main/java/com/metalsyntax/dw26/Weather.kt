package com.metalsyntax.dw26

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.ido.ble.BLEManager
import com.ido.ble.protocol.model.WeatherInfoV3
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.concurrent.thread

/** Clima de Open-Meteo (gratis, sin clave) enviado al reloj en formato V3. */
object Weather {
    var summary: String? = null; private set
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Descarga y envía. Corre en un hilo; no hace nada si el clima está apagado o no hay ubicación. */
    @SuppressLint("MissingPermission")
    fun push(ctx: Context) {
        if (!Watch.weatherEnabled || !Watch.connected) return
        thread {
            runCatching {
                val lm = ctx.getSystemService(LocationManager::class.java)
                val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                    .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                    ?: return@thread App.emit("Weather.noLocation", emptyList())
                val city = runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale("es")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
                }.getOrNull() ?: ""
                val j = JSONObject(URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                        "&hourly=temperature_2m,weather_code,precipitation_probability" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
                        "&timezone=auto&forecast_days=7"
                ).readText())
                val info = build(j, city)
                main.post {
                    BLEManager.setWeatherSwitch(true)
                    BLEManager.setWeatherDataV3(info)
                    summary = "${city.ifEmpty { "Tu ubicación" }} · ${info.today_tmp - 100}°C ${label(info.weather_type)}"
                    App.emit("Weather.sent", listOf(summary))
                }
            }.onFailure { App.emit("Weather.error", listOf(it.toString())) }
        }
    }

    private fun build(j: JSONObject, city: String): WeatherInfoV3 {
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val hourly = j.getJSONObject("hourly")
        val now = LocalDateTime.now()
        val day = { k: String, i: Int -> daily.getJSONArray(k).getDouble(i) }
        val t = { c: Double -> Math.round(c).toInt() + 100 } // el reloj usa °C + 100
        val night = cur.optInt("is_day", 1) == 0
        val (riseH, riseM) = hm(daily.getJSONArray("sunrise").getString(0))
        val (setH, setM) = hm(daily.getJSONArray("sunset").getString(0))
        return WeatherInfoV3().apply {
            version = 1
            month = now.monthValue; this.day = now.dayOfMonth; hour = now.hour; min = now.minute; sec = now.second
            week = LocalDate.now().dayOfWeek.value - 1 // 0 = lunes
            city_name = city
            weather_type = ido(cur.getInt("weather_code"), night)
            today_tmp = t(cur.getDouble("temperature_2m"))
            today_max_temp = maxOf(t(day("temperature_2m_max", 0)), today_tmp)
            today_min_temp = minOf(t(day("temperature_2m_min", 0)), today_tmp)
            humidity = cur.getInt("relative_humidity_2m")
            wind_speed = Math.round(cur.getDouble("wind_speed_10m")).toInt()
            today_uv_intensity = Math.round(day("uv_index_max", 0)).toInt()
            precipitation_probability = daily.getJSONArray("precipitation_probability_max").optInt(0)
            sunrise_hour = riseH; sunrise_min = riseM; sunset_hour = setH; sunset_min = setM
            future_items = ArrayList((1 until daily.getJSONArray("time").length()).map { i ->
                WeatherInfoV3.Future().apply {
                    max_temp = t(day("temperature_2m_max", i)); min_temp = t(day("temperature_2m_min", i))
                    weather_type = ido(daily.getJSONArray("weather_code").getInt(i), false)
                }
            })
            // las horas vienen desde las 00:00 de hoy: tomar las próximas 24
            val start = now.hour
            hours_weather_items = ArrayList((start until start + 24).map { i ->
                WeatherInfoV3.Hour24().apply {
                    temperature = t(hourly.getJSONArray("temperature_2m").getDouble(i))
                    weather_type = ido(hourly.getJSONArray("weather_code").getInt(i), false)
                    probability = hourly.getJSONArray("precipitation_probability").optInt(i)
                }
            })
        }
    }

    private fun hm(iso: String) = iso.substringAfter('T').split(':').let { it[0].toInt() to it[1].toInt() }

    /** Código WMO de Open-Meteo → código de clima IDO. */
    fun ido(wmo: Int, night: Boolean) = when (wmo) {
        0 -> if (night) 11 else 1
        1, 2 -> if (night) 12 else 2
        3 -> 3
        45, 48 -> 17
        51, 53, 55, 56, 57, 61, 63 -> 4
        65 -> 5
        66, 67 -> 8
        71, 73, 75, 77, 85, 86 -> 7
        80, 81, 82 -> 6
        95, 96, 99 -> 18
        else -> 0
    }

    private fun label(ido: Int) = when (ido) {
        1, 11 -> "Despejado"; 2, 12 -> "Parcialmente nublado"; 3 -> "Nublado"; 4 -> "Lluvia"; 5 -> "Lluvia fuerte"
        6 -> "Chubascos"; 7 -> "Nieve"; 8 -> "Aguanieve"; 17 -> "Neblina"; 18 -> "Tormenta"; else -> ""
    }
}
