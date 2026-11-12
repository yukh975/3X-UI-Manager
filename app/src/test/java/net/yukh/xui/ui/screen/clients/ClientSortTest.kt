package net.yukh.xui.ui.screen.clients

import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientStat
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientSortTest {
    private fun client(
        id: Int,
        email: String = "c$id",
        createdAt: Long = 0,
        updatedAt: Long = 0,
        lastOnline: Long = 0,
        used: Long = 0,
        quota: Long = 0,
        expiryTime: Long = 0,
    ) = Client(
        id = id,
        email = email,
        createdAt = createdAt,
        updatedAt = updatedAt,
        totalGB = quota,
        expiryTime = expiryTime,
        traffic = ClientStat(up = used / 2, down = used - used / 2, lastOnline = lastOnline),
    )

    private fun ids(sort: ClientSort, vararg clients: Client) = sort.sorted(clients.toList()).map { it.id }

    // Clients made before the panel recorded createdAt all carry 0, so the id decides their order.
    @Test
    fun creationOrderBreaksTiesOnIdInTheSortDirection() {
        val a = client(1)
        val b = client(2)
        val c = client(3, createdAt = 500)
        val d = client(4, createdAt = 100)
        assertEquals(listOf(1, 2, 4, 3), ids(ClientSort.OLDEST, c, b, d, a))
        assertEquals(listOf(3, 4, 2, 1), ids(ClientSort.NEWEST, a, d, b, c))
    }

    @Test
    fun recentlyUpdatedPutsTheLatestEditFirst() {
        assertEquals(
            listOf(2, 3, 1),
            ids(ClientSort.RECENTLY_UPDATED, client(1, updatedAt = 10), client(2, updatedAt = 30), client(3, updatedAt = 20)),
        )
    }

    @Test
    fun recentlyOnlineLeavesNeverConnectedClientsLast() {
        assertEquals(
            listOf(2, 4, 3, 1),
            ids(ClientSort.RECENTLY_ONLINE, client(1), client(2, lastOnline = 900), client(3), client(4, lastOnline = 100)),
        )
    }

    @Test
    fun emailOrderIgnoresCase() {
        val alice = client(1, email = "alice")
        val bob = client(2, email = "Bob")
        val carol = client(3, email = "carol")
        assertEquals(listOf(1, 2, 3), ids(ClientSort.EMAIL_ASC, carol, bob, alice))
        assertEquals(listOf(3, 2, 1), ids(ClientSort.EMAIL_DESC, alice, bob, carol))
    }

    @Test
    fun mostTrafficCountsUploadAndDownloadTogether() {
        assertEquals(
            listOf(3, 1, 2, 4),
            ids(ClientSort.MOST_TRAFFIC, client(4), client(2), client(1, used = 50), client(3, used = 70)),
        )
    }

    @Test
    fun highestRemainingRanksUnlimitedAboveAnyQuotaAndOverusedLast() {
        val unlimited = client(1, used = 999)
        val halfLeft = client(2, used = 50, quota = 100)
        val mostLeft = client(3, used = 10, quota = 100)
        val overused = client(4, used = 150, quota = 100)
        assertEquals(listOf(1, 3, 2, 4), ids(ClientSort.HIGHEST_REMAINING, overused, halfLeft, mostLeft, unlimited))
    }

    // A negative expiry is the panel's "starts on first use" delay, not a past date.
    @Test
    fun expiringSoonestKeepsNeverExpiringAndDelayedStartClientsLast() {
        val never = client(1)
        val delayed = client(2, expiryTime = -2_592_000_000)
        val later = client(3, expiryTime = 2_000)
        val sooner = client(4, expiryTime = 1_000)
        assertEquals(listOf(4, 3, 1, 2), ids(ClientSort.EXPIRING_SOONEST, delayed, later, never, sooner))
    }

    @Test
    fun unknownStoredKeyFallsBackToTheEmailOrder() {
        assertEquals(ClientSort.EMAIL_ASC, ClientSort.fromKey("inboundIds:descend"))
    }
}
