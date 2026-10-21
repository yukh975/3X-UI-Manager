package net.yukh.xui.shared.dto

/**
 * Sets the rolling reset period and the renewal day. The panel refuses weekly renewal
 * (v3.9.0, set in the web panel) next to either of them, so setting one turns it off;
 * with both at 0 the stored weekly renewal is kept.
 */
fun ClientModel.withRenewal(reset: Int, resetDay: Int): ClientModel = copy(
    reset = reset,
    resetDay = resetDay,
    resetWeekday = if (reset > 0 || resetDay > 0) 0 else resetWeekday,
)

/**
 * MASQUE logs users in with the email as the Basic-auth name, so panel 3.10.0 refuses
 * an email holding ":" while a MASQUE inbound is among [selectedProtocols].
 */
fun masqueEmailInvalid(email: String, selectedProtocols: Collection<String>): Boolean =
    ':' in email && selectedProtocols.any { it.equals("masque", ignoreCase = true) }

/** An ML-KEM-768 key is far longer than an X25519 one (the panel's MLKEM768_MIN_KEY_LENGTH). */
private const val MLKEM768_MIN_KEY_LENGTH = 300

/**
 * A share link carrying a post-quantum key too big to scan as a QR: a non-empty ML-DSA-65
 * `pqv`, or a VLESS `encryption` whose auth key is ML-KEM-768. Mirrors the panel's
 * isPostQuantumLink (3.10.0); the REALITY ML-KEM hint and the X25519 prefix don't count.
 */
fun isPostQuantumLink(link: String): Boolean {
    val withoutRemark = link.substringBefore('#')
    val queryStart = withoutRemark.indexOf('?')
    if (queryStart < 0) return false
    val params = withoutRemark.substring(queryStart + 1).split('&').mapNotNull { pair ->
        if (pair.isEmpty()) return@mapNotNull null
        val key = decodeQueryComponent(pair.substringBefore('='))
        val value = if ('=' in pair) decodeQueryComponent(pair.substringAfter('=')) else ""
        key to value
    }
    fun first(name: String): String = params.firstOrNull { it.first == name }?.second.orEmpty()
    if (first("pqv").isNotEmpty()) return true
    val encryption = first("encryption")
    if (encryption.isEmpty() || encryption == "none") return false
    val authKey = encryption.split('.').lastOrNull { it.isNotEmpty() }.orEmpty()
    return authKey.length > MLKEM768_MIN_KEY_LENGTH
}

/** A share link's name in the client card: its scheme plus the `#remark`, else its position. */
fun connectionLabel(link: String, index: Int): String {
    val scheme = link.substringBefore("://", "").uppercase().ifBlank { "Link" }
    val remark = decodeQueryComponent(link.substringAfter("#", "").substringBefore("\n").trim())
    return if (remark.isNotBlank()) "$scheme · $remark" else "$scheme · #${index + 1}"
}

/** application/x-www-form-urlencoded decoding, as URLSearchParams does it; bad escapes stay as is. */
private fun decodeQueryComponent(s: String): String {
    if ('%' !in s && '+' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val hex = if (s[i] == '%' && i + 2 <= s.lastIndex && s[i + 1].isHex() && s[i + 2].isHex()) {
            s.substring(i + 1, i + 3).toInt(16)
        } else {
            null
        }
        if (hex != null) {
            out += hex.toByte()
            i += 3
            continue
        }
        // Copy the plain run up to the next escape whole, so surrogate pairs stay together.
        var end = i + 1
        while (end < s.length && s[end] != '%') end++
        s.substring(i, end).replace('+', ' ').encodeToByteArray().forEach { out += it }
        i = end
    }
    return out.toByteArray().decodeToString()
}

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
