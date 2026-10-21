package net.yukh.xui.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a later build of the same day is an update`() {
        assertTrue(UpdateChecker.isNewer("26.10.21b78", "26.10.21b77"))
        assertFalse(UpdateChecker.isNewer("26.10.21b77", "26.10.21b78"))
    }

    @Test
    fun `the installed version is not an update to its own tag`() {
        assertFalse(UpdateChecker.isNewer("26.10.21b77", "26.10.21 build 77"))
        assertTrue(UpdateChecker.isNewer("26.10.21b78", "26.10.21 build 77-debug"))
    }
}
