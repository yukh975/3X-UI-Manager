package net.yukh.xui.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `a later build of the same day is an update`() {
        assertTrue(UpdateChecker.isNewer("26.10.21.79", "26.10.21.78"))
        assertFalse(UpdateChecker.isNewer("26.10.21.78", "26.10.21.79"))
    }

    @Test
    fun `the installed version is not an update to its own tag`() {
        assertFalse(UpdateChecker.isNewer("26.10.22.79", "26.10.22.79"))
    }

    @Test
    fun `a build that shows the build-suffix version sees the dotted tag`() {
        assertTrue(UpdateChecker.isNewer("26.10.21.79", "26.10.21 build 78"))
        assertFalse(UpdateChecker.isNewer("26.10.21.78", "26.10.21 build 78"))
    }

    @Test
    fun `a dated build replaces a semver one`() {
        assertTrue(UpdateChecker.isNewer("26.10.22.79", "0.15.3"))
    }

    @Test
    fun `versions of the build-suffix era are shown dotted like the current tags`() {
        assertEquals("26.10.21.78", UpdateChecker.displayVersion("26.10.21b78"))
        assertEquals("26.10.21.77", UpdateChecker.displayVersion("26.10.21 build 77"))
        assertEquals("26.10.22.79", UpdateChecker.displayVersion("26.10.22.79"))
        assertEquals("0.15.3", UpdateChecker.displayVersion("0.15.3"))
    }
}
