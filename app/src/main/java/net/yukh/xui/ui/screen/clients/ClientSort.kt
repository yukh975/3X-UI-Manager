package net.yukh.xui.ui.screen.clients

import net.yukh.xui.data.api.dto.Client

private const val NEVER = Long.MAX_VALUE

// Keys and tie-breaks follow the panel's clientQuery.applyOrder, so both lists read the same.
enum class ClientSort(val key: String, private val order: Comparator<Client>) {
    OLDEST("createdAt:ascend", compareBy<Client> { it.createdAt }.thenBy { it.id }),
    NEWEST("createdAt:descend", compareByDescending<Client> { it.createdAt }.thenByDescending { it.id }),
    RECENTLY_UPDATED("updatedAt:descend", compareByDescending<Client> { it.updatedAt }.thenByDescending { it.id }),
    RECENTLY_ONLINE("lastOnline:descend", compareByDescending<Client> { it.lastOnline }.thenByDescending { it.id }),
    EMAIL_ASC("email:ascend", compareBy<Client> { it.email.lowercase() }.thenBy { it.id }),
    EMAIL_DESC("email:descend", compareByDescending<Client> { it.email.lowercase() }.thenBy { it.id }),
    MOST_TRAFFIC("traffic:descend", compareByDescending<Client> { it.up + it.down }.thenBy { it.id }),
    HIGHEST_REMAINING(
        "remaining:descend",
        compareByDescending<Client> { if (it.quota > 0) it.quota - it.up - it.down else NEVER }.thenBy { it.id },
    ),
    EXPIRING_SOONEST(
        "expiryTime:ascend",
        compareBy<Client> { if (it.expiryTime > 0) it.expiryTime else NEVER }.thenBy { it.id },
    );

    fun sorted(clients: List<Client>): List<Client> = clients.sortedWith(order)

    companion object {
        val DEFAULT = EMAIL_ASC

        fun fromKey(key: String?): ClientSort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
