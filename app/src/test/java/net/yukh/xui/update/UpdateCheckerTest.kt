package net.yukh.xui.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a later build of the same day is an update`() {
        assertTrue(UpdateChecker.isNewer("2026.10.21b78", "2026.10.21b77"))
        assertFalse(UpdateChecker.isNewer("2026.10.21b77", "2026.10.21b78"))
        assertFalse(UpdateChecker.isNewer("2026.10.21b77", "2026.10.21b77-debug"))
    }
}
