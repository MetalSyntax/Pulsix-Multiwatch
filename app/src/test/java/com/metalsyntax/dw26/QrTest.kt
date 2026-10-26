package com.metalsyntax.dw26

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrTest {
    @Test fun pop2() = assertEquals("DD:EA:FF:B0:9C:D4" to "Amazfit Pop 2",
        Profiles.parseQr("https://api.linwear.top/app/auto-download?release=1&deviceName=LA21&adaptNum=30BE&MAC=DD:EA:FF:B0:9C:D4&BtName=Amazfit%20Pop%202"))

    @Test fun macSinDosPuntosNiNombre() = assertEquals("DD:EA:FF:B0:9C:D4" to null, Profiles.parseQr("x?Mac=ddeaffb09cd4"))

    @Test fun sinMac() = assertNull(Profiles.parseQr("https://example.com/?BtName=DW26"))
}
