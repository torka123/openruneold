package org.rsmod.content.other.grandexchange

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeExchangeTest {
    private val items =
        GeItemData.fromLines(
            listOf(
                "# id\tlimit\tprice\tname",
                "$WHIP\t70\t1500000\tAbyssal whip",
                "$FEATHER\t13000\t3\tFeather",
                "$LOBSTER\t6000\t150\tLobster",
                "$COAL\t13000\t150\tCoal",
                "$BOND\t100\t8000000\tOld school bond",
                "$ENERGY\t2000\t90\tEnergy potion(4)",
                "$RARE\t8\t2000000000\tThird age",
            )
        )

    private var now = 1_000_000L
    private val exchange = GeExchange(items) { now }
    private val finished = ArrayList<Pair<GeOffer, Boolean>>()
    private val traded = ArrayList<Pair<GeOffer, GeTrade>>()

    init {
        exchange.listener =
            object : GeExchange.Listener {
                override fun onOfferTraded(offer: GeOffer, trade: GeTrade) {
                    traded += offer to trade
                }

                override fun onOfferFinished(offer: GeOffer, aborted: Boolean) {
                    finished += offer to aborted
                }
            }
    }

    @Test
    fun `buy offer trades at the cheapest sell price and refunds the difference`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, FEATHER, 2, 100)
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, FEATHER, 5, 100)

        assertTrue(buy.finished)
        assertTrue(sell.finished)
        assertEquals(100, buy.collectItems)
        assertEquals(300, buy.collectCoins, "buyer paid 5 each, trade happened at 2 each")
        assertEquals(200, buy.gold)
        assertEquals(200, sell.gold)
        assertEquals(0, sell.taxPaid, "4% of 2 coins rounds down to nothing")
        assertEquals(200, sell.collectCoins)
        assertEquals(listOf(buy to false, sell to false), finished)
    }

    @Test
    fun `sell offer trades at the highest buy price so the seller earns more`() {
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, WHIP, 1_600_000, 1)
        exchange.place(BUYER, "buyer", 1, GeOfferType.Buy, WHIP, 1_700_000, 1)
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, WHIP, 1_500_000, 1)

        assertTrue(sell.finished)
        assertEquals(1_700_000, sell.gold)
        assertEquals(68_000, sell.taxPaid)
        assertEquals(1_632_000, sell.collectCoins)
        val cheaper = exchange.offer(BUYER, 0)!!
        assertTrue(cheaper.isActive, "the lower buy offer is still waiting")
        assertNull(exchange.offer(BUYER, 1)?.takeIf { it.isActive })
    }

    @Test
    fun `older offers win ties`() {
        val first = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 150, 10)
        now += 1
        val second = exchange.place(SELLER, "seller", 1, GeOfferType.Sell, LOBSTER, 150, 10)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 150, 10)

        assertTrue(first.finished)
        assertFalse(second.finished)
        assertEquals(0, second.completed)
    }

    @Test
    fun `partial fills leave both offers live until quantities run out`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 100, 30)
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 100, 10)

        assertTrue(buy.finished)
        assertFalse(sell.finished)
        assertEquals(10, sell.completed)
        assertEquals(20, sell.remaining)
        assertEquals(1000, sell.gold)
        assertEquals(40, sell.taxPaid)
        assertEquals(960, sell.collectCoins)
    }

    @Test
    fun `tax is capped per item and waived on exempt items`() {
        assertEquals(5_000_000, items.taxPerItem(RARE, 2_000_000_000))
        assertEquals(0, items.taxPerItem(ENERGY, 90))
        assertEquals(0, items.taxPerItem(BOND, 8_000_000))
        assertEquals(0, items.taxPerItem(LOBSTER, 150), "lobsters are exempt")
        assertEquals(6, items.taxPerItem(COAL, 150))
        assertEquals(60_000, items.taxPerItem(WHIP, 1_500_000))
        assertEquals(0, items.taxPerItem(FEATHER, 24))
        assertEquals(1, items.taxPerItem(FEATHER, 25))
        assertEquals(1, items.taxPerItem(FEATHER, 49))
        assertEquals(2, items.taxPerItem(FEATHER, 50))
    }

    @Test
    fun `aborting returns what was not traded to the collect box`() {
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 10)
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 4)
        assertTrue(sell.finished)
        assertTrue(exchange.abort(buy))
        assertFalse(exchange.abort(buy))

        assertTrue(buy.isCancelled)
        assertEquals(4, buy.collectItems)
        assertEquals(600, buy.collectCoins)
        assertEquals(buy to true, finished.last())

        val sell2 = exchange.place(SELLER, "seller", 1, GeOfferType.Sell, LOBSTER, 100, 5)
        assertTrue(exchange.abort(sell2))
        assertEquals(5, sell2.collectItems)
        assertEquals(0, sell2.collectCoins)
    }

    @Test
    fun `collecting everything frees the slot`() {
        val sell = exchange.place(SELLER, "seller", 3, GeOfferType.Sell, COAL, 100, 1)
        val buy = exchange.place(BUYER, "buyer", 2, GeOfferType.Buy, COAL, 120, 1)

        assertEquals(1, exchange.takeItems(buy))
        assertEquals(buy, exchange.offer(BUYER, 2), "coins are still waiting")
        assertEquals(20, exchange.takeCoins(buy))
        assertNull(exchange.offer(BUYER, 2))
        assertEquals(0, exchange.firstEmptySlot(BUYER))

        assertEquals(96, exchange.takeCoins(sell))
        assertNull(exchange.offer(SELLER, 3))
        assertEquals(0, exchange.offerCount)
    }

    @Test
    fun `a single purchase exceeds the legacy TSV amount in either placement order`() {
        for (buyFirst in listOf(false, true)) {
            val book = GeExchange(items) { now }
            val first = if (buyFirst) GeOfferType.Buy else GeOfferType.Sell
            val second = if (buyFirst) GeOfferType.Sell else GeOfferType.Buy
            val a = book.place(1, "first", 0, first, RARE, 1000, 200)
            val b = book.place(2, "second", 0, second, RARE, 1000, 200)
            assertTrue(a.finished)
            assertTrue(b.finished)
            assertEquals(200, a.completed)
            assertEquals(200, b.completed)
        }
    }

    @Test
    fun `repeated purchases and partial fills never wait for the clock`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, RARE, 1000, 400)
        repeat(20) {
            val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, RARE, 1000, 20)
            assertTrue(buy.finished)
            assertEquals(20, exchange.takeItems(buy))
            assertNull(exchange.offer(BUYER, 0))
        }
        assertTrue(sell.finished)
        assertEquals(400, sell.completed)
        val waiting = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, RARE, 1000, 50)
        repeat(5) { slot ->
            exchange.place(SELLER, "seller", slot + 1, GeOfferType.Sell, RARE, 1000, 10)
            assertEquals((slot + 1) * 10, waiting.completed)
        }
        assertTrue(waiting.finished)
        assertEquals(1_000_000L, now, "no elapsed-time reset is needed")
    }

    @Test
    fun `seller receives 96 percent of uncapped taxable proceeds`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 1000, 100)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 1000, 100)
        assertEquals(100_000, sell.gold)
        assertEquals(4_000, sell.taxPaid)
        assertEquals(96_000, sell.collectCoins)
    }

    @Test
    fun `tax rounds down per item rather than across the whole fill`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 49, 10)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 49, 10)
        assertEquals(10, sell.taxPaid, "floor(49 * 4 / 100) * 10, not floor(490 * 4 / 100)")
        assertEquals(480, sell.collectCoins)
    }

    @Test
    fun `tax cap applies to each item and not the whole offer`() {
        assertEquals(4_999_999, items.taxPerItem(RARE, 124_999_999))
        assertEquals(5_000_000, items.taxPerItem(RARE, 125_000_000))
        assertEquals(5_000_000, items.taxPerItem(RARE, Int.MAX_VALUE))
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, RARE, 200_000_000, 3)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, RARE, 200_000_000, 3)
        assertEquals(15_000_000, sell.taxPaid)
        assertEquals(585_000_000, sell.collectCoins)
    }

    @Test
    fun `eight slots remain available per owner`() {
        for (owner in listOf(BUYER, SELLER)) {
            assertEquals(8, exchange.slots(owner).size)
            repeat(8) { slot ->
                assertEquals(slot, exchange.firstEmptySlot(owner))
                exchange.place(owner, "owner", slot, GeOfferType.Buy, COAL, 100, 1)
            }
            assertNull(exchange.firstEmptySlot(owner))
        }
    }

    @Test
    fun `history records both sides newest first`() {
        exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 100, 2)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 100, 1)
        now += 10
        exchange.place(BUYER, "buyer", 1, GeOfferType.Buy, COAL, 100, 1)

        val buyer = exchange.history(BUYER)
        assertEquals(2, buyer.size)
        assertEquals(GeOfferType.Buy, buyer[0].type)
        assertEquals(now, buyer[0].time)
        val seller = exchange.history(SELLER)
        assertEquals(2, seller.size)
        assertEquals(GeOfferType.Sell, seller[0].type)
        assertEquals(100, seller[0].total)
        assertEquals(4, seller[0].tax)
    }

    @Test
    fun `offers survive a save and load round trip`() {
        exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 5)
        val buy = exchange.place(BUYER, "buyer", 4, GeOfferType.Buy, LOBSTER, 100, 2)
        val path = Files.createTempFile("ge-test", ".json")
        try {
            exchange.save(path)
            assertFalse(exchange.dirty)
            assertFalse(jacksonObjectMapper().readTree(path.toFile()).has("buyWindows"))

            val loaded = GeExchange(items) { now }
            assertTrue(loaded.load(path))
            val sell = loaded.offer(SELLER, 0)!!
            assertEquals(2, sell.completed)
            assertEquals(3, sell.remaining)
            assertTrue(sell.isActive)
            val reloadedBuy = loaded.offer(BUYER, 4)!!
            assertEquals(buy.id, reloadedBuy.id)
            assertTrue(reloadedBuy.finished)
            assertEquals(2, reloadedBuy.collectItems)
            assertEquals(1, loaded.history(BUYER).size)

            // The active sell offer still matches new buyers after the reload.
            loaded.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 3)
            assertTrue(loaded.offer(SELLER, 0)!!.finished)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `legacy save loads offers and ignores exhausted purchase state then writes no such field`() {
        val path = Files.createTempFile("ge-legacy", ".json")
        try {
            val fixture = requireNotNull(javaClass.getResourceAsStream("/legacy-ge-book.json"))
            fixture.use { Files.copy(it, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            assertTrue(exchange.load(path))
            assertEquals(1, exchange.offerCount)
            assertEquals(1, exchange.history(BUYER).size)
            val waiting = exchange.offer(BUYER, 7)!!
            assertEquals(12, waiting.remaining)
            exchange.place(SELLER, "seller", 0, GeOfferType.Sell, RARE, 1000, 12)
            assertTrue(waiting.finished)
            assertEquals(20, waiting.collectItems)
            exchange.place(SELLER, "seller", 1, GeOfferType.Sell, RARE, 1000, 50)
            val again = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, RARE, 1000, 50)
            assertTrue(again.finished)
            exchange.save(path)
            val json = jacksonObjectMapper().readTree(path.toFile())
            assertFalse(json.has("buyWindows"))
            assertEquals(
                setOf("nextId", "offers", "history"),
                json.fieldNames().asSequence().toSet(),
            )
            val reloaded = GeExchange(items) { now }
            assertTrue(reloaded.load(path))
            assertEquals(
                exchange.allOffers().map(GeOfferData::from),
                reloaded.allOffers().map(GeOfferData::from),
            )
            assertEquals(exchange.history(BUYER), reloaded.history(BUYER))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `legacy TSV column is ignored even when non numeric`() {
        val data = GeItemData.fromLines(listOf("$COAL\tignored\t123\tCoal"))
        assertEquals(123, data.guidePrice(COAL))
        assertEquals(4, data.taxPerItem(COAL, 123))
    }

    @Test
    fun `client status encodes type and completion`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 1)
        assertEquals(10, sell.clientStatus())
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 1)
        assertEquals(5, buy.clientStatus())
        assertEquals(13, sell.clientStatus())
    }

    private companion object {
        const val BUYER = 1
        const val SELLER = 2
        const val WHIP = 4151
        const val FEATHER = 314
        const val LOBSTER = 379
        const val COAL = 453
        const val BOND = 13190
        const val ENERGY = 3008
        const val RARE = 10344
    }
}
