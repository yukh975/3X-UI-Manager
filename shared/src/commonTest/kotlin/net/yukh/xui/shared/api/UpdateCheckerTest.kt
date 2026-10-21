package net.yukh.xui.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `a later build of the same day is an update`() {
        assertTrue(UpdateChecker.isNewer("26.10.21b78", "26.10.21 build 77"))
        assertFalse(UpdateChecker.isNewer("26.10.21b77", "26.10.21 build 78"))
    }

    @Test
    fun `the installed version is not an update to its own tag`() {
        assertFalse(UpdateChecker.isNewer("26.10.21b78", "26.10.21 build 78"))
    }

    @Test
    fun `a dated build replaces a semver one`() {
        assertTrue(UpdateChecker.isNewer("26.10.21b78", "0.15.3"))
    }

    @Test
    fun `the short b of a tag is shown as build`() {
        assertEquals("26.10.21 build 78", UpdateChecker.displayVersion("26.10.21b78"))
        assertEquals("0.15.3", UpdateChecker.displayVersion("0.15.3"))
    }
}
