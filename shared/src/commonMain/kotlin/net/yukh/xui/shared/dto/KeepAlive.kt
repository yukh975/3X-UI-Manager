package net.yukh.xui.shared.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Panel 3.11.0 sends a peer keepalive as seconds (a number) or, for AmneziaWG, a "15-25"
 *  string, and refuses seconds sent as a string, so the wire type follows the value. */
object KeepAliveSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("KeepAlive", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
        return (element as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    override fun serialize(encoder: Encoder, value: String) {
        val seconds = value.toLongOrNull()
        if (seconds != null) encoder.encodeLong(seconds) else encoder.encodeString(value)
    }
}

/** Canonical seconds or "min-max" within 0..65535 (empty means 0), or null when the panel would refuse it. */
fun parseKeepAlive(raw: String, allowRange: Boolean): String? {
    val parts = raw.ifEmpty { "0" }.split("-")
    if (parts.size > 2 || (parts.size == 2 && !allowRange)) return null
    val bounds = parts.map { part ->
        part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null
    }
    if (bounds.size == 2 && bounds[0] > bounds[1]) return null
    return bounds.joinToString("-")
}

/** Typing filter for the keepalive field: digits, plus one "-" where a range is allowed. */
fun sanitizeKeepAlive(raw: String, allowRange: Boolean): String =
    if (allowRange) raw.filter { it.isDigit() || it == '-' }.replace(Regex("-+"), "-").take(11)
    else raw.filter(Char::isDigit).take(5)
