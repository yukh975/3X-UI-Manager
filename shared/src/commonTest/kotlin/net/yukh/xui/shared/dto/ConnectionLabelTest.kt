package net.yukh.xui.shared.dto

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionLabelTest {

    @Test
    fun percentEncodedRemarkIsDecodedAfterTheScheme() {
        val link = "vless://id@host:443?type=tcp#%D0%A1%D0%B5%D1%80%D0%B2%D0%B5%D1%80%201"
        assertEquals("VLESS · Сервер 1", connectionLabel(link, 0))
    }

    @Test
    fun linkWithoutRemarkIsNumberedFromOne() {
        assertEquals("TROJAN · #3", connectionLabel("trojan://pw@host:443?security=tls", 2))
    }

    @Test
    fun configWithoutSchemeIsCalledLink() {
        assertEquals("Link · #1", connectionLabel("[Interface]\nPrivateKey = abc", 0))
    }

    @Test
    fun remarkStopsAtTheEndOfItsLine() {
        assertEquals("SS · fi-hel", connectionLabel("ss://key@host:8388#fi-hel\nss://key@other:8388", 0))
    }
}
