package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.support.TipJar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TipJarTest {
    @Test
    fun tipsAreWholeDollarsFromOneToAHundred() {
        assertEquals(1, TipJar.dollars("1"))
        assertEquals(100, TipJar.dollars(" $100 "))
        assertEquals(7, TipJar.dollars("$7"))
        assertNull(TipJar.dollars("0"))
        assertNull(TipJar.dollars("101"))
        assertNull(TipJar.dollars("4.99"))
        assertNull(TipJar.dollars(""))
        assertEquals("p2p_tip_7", TipJar.productId(7))
        assertEquals(100, TipJar.productIds.size)
    }
}
