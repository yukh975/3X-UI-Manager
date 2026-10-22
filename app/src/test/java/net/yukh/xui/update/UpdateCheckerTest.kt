package net.yukh.xui.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a later build of the same day is an update`() {
        assertTrue(UpdateChecker.isNewer("26.10.21.79", "26.10.21.78"))
        assertFalse(UpdateChecker.isNewer("26.10.21.78", "26.10.21.79"))
    }

    @Test
    fun `the installed version is not an update to its own tag`() {
        assertFalse(UpdateChecker.isNewer("26.10.21.79", "26.10.21.79"))
        assertTrue(UpdateChecker.isNewer("26.10.21.80", "26.10.21.79-debug"))
    }

    @Test
    fun `a build that shows the build-suffix version sees the dotted tag`() {
        assertTrue(UpdateChecker.isNewer("26.10.21.79", "26.10.21 build 78"))
        assertFalse(UpdateChecker.isNewer("26.10.21.78", "26.10.21 build 78"))
    }
}
