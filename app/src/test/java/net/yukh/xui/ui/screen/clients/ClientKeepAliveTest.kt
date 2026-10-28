package net.yukh.xui.ui.screen.clients

import net.yukh.xui.data.api.dto.InboundSlim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Panel 3.11.0 takes a keepalive range ("15-25") for AmneziaWG peers only and refuses
 *  anything outside 0..65535, so the editor must neither drop a range nor send a bad one. */
class ClientKeepAliveTest {
    private val awg = InboundSlim(id = 1, protocol = "amneziawg")
    private val wg = InboundSlim(id = 2, protocol = "wireguard")

    private fun editor(keepAlive: String, vararg selected: Int) = ClientEditorState(
        isNew = true,
        email = "peer",
        keepAlive = keepAlive,
        selectedInboundIds = selected.toSet(),
        availableInbounds = listOf(awg, wg),
    )

    @Test
    fun amneziawgRangeIsSavedAsTyped() {
        val e = editor("15-25", 1)
        assertFalse(e.keepAliveInvalid)
        assertTrue(e.canSave)
        assertEquals("15-25", e.toClientModel().keepAlive)
    }

    @Test
    fun rangeIsRefusedWhenAWireguardInboundIsSelected() {
        assertTrue(editor("15-25", 2).keepAliveInvalid)
        assertTrue(editor("15-25", 1, 2).keepAliveInvalid)
        assertFalse(editor("15-25", 1, 2).canSave)
    }

    @Test
    fun outOfBoundsAndReversedValuesAreRefused() {
        assertTrue(editor("70000", 2).keepAliveInvalid)
        assertTrue(editor("25-15", 1).keepAliveInvalid)
        assertTrue(editor("15-70000", 1).keepAliveInvalid)
        assertTrue(editor("15-", 1).keepAliveInvalid)
        assertFalse(editor("65535", 2).keepAliveInvalid)
    }

    @Test
    fun plainSecondsAreSentAsANumberWithoutLeadingZeros() {
        assertEquals("25", editor("025", 2).toClientModel().keepAlive)
        assertEquals("0", editor("", 1).toClientModel().keepAlive)
    }

    @Test
    fun wireguardInputKeepsDigitsOnly() {
        assertEquals("1525", sanitizeKeepAlive("15-25", allowRange = false))
        assertEquals("15-25", sanitizeKeepAlive("15-25", allowRange = true))
        assertEquals("15-25", sanitizeKeepAlive("15--25x", allowRange = true))
    }
}
