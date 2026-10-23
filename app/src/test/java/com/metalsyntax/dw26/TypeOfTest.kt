package com.metalsyntax.dw26

import org.junit.Assert.assertEquals
import org.junit.Test

class TypeOfTest {
    // valores calculados con el algoritmo de NoticeAppUtil.convertPkg2Type de VeryFit
    @Test fun whatsapp() = assertEquals(909, NotifListener.typeOf("com.whatsapp"))
    @Test fun telegram() = assertEquals(2955, NotifListener.typeOf("org.telegram.messenger"))
}
