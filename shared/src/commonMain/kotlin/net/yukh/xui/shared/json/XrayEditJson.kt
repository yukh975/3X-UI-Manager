package net.yukh.xui.shared.json

/** The panel writes a DNS server's `expectedIPs`, older configs `expectIPs` (Xray reads
 *  both): edit whichever the server already uses, new servers keep `expectIPs`. */
fun dnsExpectedIpsKey(server: String): String =
    if (jsonGetChild(server, listOf("expectedIPs")) != "null") "expectedIPs" else "expectIPs"

/** Balancer strategy as the panel writes it: none for random, `settings` only for
 *  leastLoad. The dialog keeps them while the type is switched, so going back restores them. */
fun normalizeBalancer(balancer: String): String {
    if (!jsonIsObject(jsonGetChild(balancer, listOf("strategy")))) return balancer
    val type = jsonGetString(balancer, listOf("strategy", "type"))
    return when {
        type.isBlank() || type == "random" -> jsonRemove(balancer, listOf("strategy"))
        type != "leastLoad" -> jsonRemove(balancer, listOf("strategy", "settings"))
        else -> balancer
    }
}
