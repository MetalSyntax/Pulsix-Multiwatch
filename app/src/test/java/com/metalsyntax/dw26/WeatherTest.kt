package com.metalsyntax.dw26

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {
    // WMO (Open-Meteo) → código IDO del reloj
    @Test fun clearDayNight() { assertEquals(1, Weather.ido(0, false)); assertEquals(11, Weather.ido(0, true)) }
    @Test fun rainAndStorm() { assertEquals(4, Weather.ido(61, false)); assertEquals(5, Weather.ido(65, false)); assertEquals(18, Weather.ido(95, false)) }
    @Test fun unknownIsOther() = assertEquals(0, Weather.ido(42, false))
}
