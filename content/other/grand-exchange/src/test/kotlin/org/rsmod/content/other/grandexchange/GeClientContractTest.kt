package org.rsmod.content.other.grandexchange

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlot
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

class GeClientContractTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `actual GE script startup loads legacy JSON and service saves without purchase state`() {
        val path = directory.resolve("grand-exchange.json")
        requireNotNull(javaClass.getResourceAsStream("/legacy-ge-book.json")).use {
            Files.copy(it, path)
        }
        val items = GeItemData.load()
        val book = GeExchange(items) { 1_000_000L }
        val service = GeService(book, items, PlayerList(), path)
        with(GrandExchangeScript(service)) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
        assertEquals(1, book.offerCount, "startup must not silently discard the legacy order book")
        val waiting = requireNotNull(book.offer(1, 7))
        book.place(2, "seller", 0, GeOfferType.Sell, 10344, 1000, 12)
        assertTrue(waiting.finished)
        service.save()
        assertFalse(jacksonObjectMapper().readTree(path.toFile()).has("buyWindows"))
        val reloaded = GeExchange(items) { 1_000_000L }
        GeService(reloaded, items, PlayerList(), path).load()
        assertEquals(
            book.allOffers().map(GeOfferData::from),
            reloaded.allOffers().map(GeOfferData::from),
        )
    }

    @Test
    fun `completion still sends the GE jingle and resets the native music clock but abort does not`() {
        val packets = mutableListOf<Any>()
        val player =
            Player().apply {
                accountId = 1
                client =
                    object : Client<Any, Any> {
                        override fun write(message: Any) {
                            packets += message
                        }

                        override fun close() = error("Unexpected disconnect")

                        override fun read(player: Player) = Unit

                        override fun flush() = Unit

                        override fun flushHighPriority() = Unit

                        override fun unregister(service: Any, player: Player) = Unit
                    }
            }
        val players = PlayerList().apply { this[1] = player }
        val items = GeItemData.load()
        val exchange = GeExchange(items) { 1L }
        GeService(exchange, items, players)
        VarPlayerIntMapSetter.set(player, "varbit.music_curr_clocks", 12)
        exchange.place(1, "buyer", 0, GeOfferType.Buy, 4151, 100, 1)
        exchange.place(2, "seller", 0, GeOfferType.Sell, 4151, 100, 1)
        assertEquals(
            listOf(MidiJingle(GeConfig.COMPLETION_JINGLE_GROUP)),
            packets.filterIsInstance<MidiJingle>(),
        )
        assertEquals(0, player.vars["varbit.music_curr_clocks"])
        packets.clear()
        val waiting = exchange.place(1, "buyer", 1, GeOfferType.Buy, 4151, 100, 1)
        exchange.abort(waiting)
        assertTrue(packets.none { it is MidiJingle })
    }

    @Test
    fun `login sends the 4 percent tax rate and eight slots regardless of membership`() {
        for (membership in listOf(false, true)) {
            val packets = mutableListOf<Any>()
            val player =
                Player().apply {
                    accountId = if (membership) 1 else 2
                    members = membership
                    client =
                        object : Client<Any, Any> {
                            override fun write(message: Any) {
                                packets += message
                            }

                            override fun close() = error("Unexpected disconnect")

                            override fun read(player: Player) = Unit

                            override fun flush() = Unit

                            override fun flushHighPriority() = Unit

                            override fun unregister(service: Any, player: Player) = Unit
                        }
                }
            val items = GeItemData.load()
            val exchange = GeExchange(items) { 1L }
            val service = GeService(exchange, items, PlayerList())
            service.onLogin(player)
            assertEquals(40, player.vars[GeConfig.VARBIT_TAX_RATE])
            assertEquals(8, packets.filterIsInstance<UpdateStockMarketSlot>().size)
            assertEquals(8, exchange.slots(player.accountId).size)
            assertEquals(membership, player.members, "GE must not change account membership")
        }
    }

    @Test
    fun `pricing experts quote price without purchase restriction`() {
        val items = GeItemData.fromLines(listOf("4151\t70\t1500000\tAbyssal whip"))
        val service = GeService(GeExchange(items) { 1L }, items, PlayerList())
        val quote = service.guidePriceText(requireNotNull(ServerCacheManager.getItem(4151)))
        assertEquals("Abyssal whip: 1,500,000 coins.", quote)
    }

    @Test
    fun `all existing named exemptions remain exempt at taxable and capped prices`() {
        val exemptions =
            listOf(
                "Old school bond",
                "Bronze arrow",
                "Bronze dart",
                "Iron arrow",
                "Iron dart",
                "Mind rune",
                "Steel arrow",
                "Steel dart",
                "Bass",
                "Bread",
                "Cake",
                "Cooked chicken",
                "Cooked meat",
                "Herring",
                "Lobster",
                "Mackerel",
                "Meat pie",
                "Pike",
                "Salmon",
                "Shrimps",
                "Tuna",
                "Ardougne teleport",
                "Camelot teleport",
                "Civitas illa fortis teleport",
                "Falador teleport",
                "Games necklace(8)",
                "Kourend castle teleport",
                "Lumbridge teleport",
                "Ring of dueling(8)",
                "Teleport to house",
                "Varrock teleport",
                "Chisel",
                "Gardening trowel",
                "Glassblowing pipe",
                "Hammer",
                "Needle",
                "Pestle and mortar",
                "Rake",
                "Saw",
                "Secateurs",
                "Seed dibber",
                "Shears",
                "Spade",
                "Watering can",
                "Energy potion(1)",
                "Energy potion(2)",
                "Energy potion(3)",
                "Energy potion(4)",
            )
        val items = GeItemData.fromLines(exemptions.mapIndexed { i, name -> "$i\t1\t100\t$name" })
        for ((id, name) in exemptions.withIndex()) {
            assertTrue(items.isTaxExempt(id), name)
            assertEquals(0, items.taxPerItem(id, 1000), name)
            assertEquals(0, items.taxPerItem(id, Int.MAX_VALUE), name)
        }
        assertFalse(GeItemData.isTaxExemptName("Super energy(4)"))
        assertFalse(GeItemData.isTaxExemptName("Rune arrow"))
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
