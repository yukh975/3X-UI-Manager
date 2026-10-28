package net.yukh.xui.shared.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeepAliveTest {
    @Test
    fun amneziawgRangeIsKeptAsTyped() {
        assertEquals("15-25", parseKeepAlive("15-25", allowRange = true))
    }

    @Test
    fun rangeIsRefusedWhereOnlySecondsAreAllowed() {
        assertNull(parseKeepAlive("15-25", allowRange = false))
    }

    @Test
    fun outOfBoundsReversedAndIncompleteValuesAreRefused() {
        assertNull(parseKeepAlive("70000", allowRange = false))
        assertNull(parseKeepAlive("25-15", allowRange = true))
        assertNull(parseKeepAlive("15-70000", allowRange = true))
        assertNull(parseKeepAlive("15-", allowRange = true))
        assertNull(parseKeepAlive("1-2-3", allowRange = true))
        assertEquals("65535", parseKeepAlive("65535", allowRange = false))
    }

    @Test
    fun plainSecondsLoseLeadingZerosAndEmptyMeansOff() {
        assertEquals("25", parseKeepAlive("025", allowRange = false))
        assertEquals("0", parseKeepAlive("", allowRange = true))
    }

    @Test
    fun typingFilterKeepsDigitsAndOneDashOnlyWhereARangeIsAllowed() {
        assertEquals("1525", sanitizeKeepAlive("15-25", allowRange = false))
        assertEquals("15-25", sanitizeKeepAlive("15-25", allowRange = true))
        assertEquals("15-25", sanitizeKeepAlive("15--25x", allowRange = true))
    }
}
