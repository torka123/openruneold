package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import io.netty.buffer.Unpooled
import java.nio.file.Path
import kotlin.test.*
import net.rsprot.buffer.JagByteBuf
import net.rsprot.crypto.cipher.NopStreamCipher
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.resumed.ResumePObjDialog
import net.rsprot.protocol.game.outgoing.codec.inv.UpdateInvFullEncoder
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.player.PlayerInvUpdateProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.net.rsprot.handlers.ResumePObjDialogHandler
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.ShuffledPlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.UserInterface
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class GeUiInteractionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `abort button from the native status component refunds only once`() {
        val f = Fixture(directory)
        val offer = f.book.place(1, "buyer", 0, GeOfferType.Buy, 4151, 100, 10)
        f.open()
        f.click(GeConfig.indexSlot(0), 2)
        f.click("component.ge_offers:details_status", 0)
        assertTrue(offer.finished, "Real client abort click must reach the service")
        assertEquals(1000, offer.collectCoins)
        f.click("component.ge_offers:details_status", 0)
        assertEquals(1000, offer.collectCoins)
    }

    @Test
    fun `modify button reopens an active buy with escrow refunded and remaining quantity`() {
        val f = Fixture(directory)
        val offer = f.book.place(1, "buyer", 0, GeOfferType.Buy, 4151, 100, 10)
        f.open()
        f.click(GeConfig.indexSlot(0), 2)
        f.click("component.ge_offers:details_status", 1)
        assertTrue(offer.finished, "Real client modify click must reach the service")
        assertNull(f.book.offer(1, 0))
        assertEquals(
            1000,
            f.player.inv.objs.filterNotNull().sumOf { if (it.id == 995) it.count else 0 },
        )
        assertEquals(4151, f.player.vars[GeConfig.VARP_ITEM])
        assertEquals(10, f.player.vars[GeConfig.VARBIT_QUANTITY])
        assertEquals(100, f.player.vars[GeConfig.VARBIT_PRICE])
        f.click("component.ge_offers:details_status", 1)
        assertEquals(
            1000,
            f.player.inv.objs.filterNotNull().sumOf { if (it.id == 995) it.count else 0 },
        )
        f.click(GeConfig.COMP_SETUP, GeConfig.SETUP_QTY_MINUS_1)
        f.click(GeConfig.COMP_SETUP_CONFIRM, -1)
        assertEquals(9, assertNotNull(f.book.offer(1, 0)).total)
        assertEquals(
            100,
            f.player.inv.objs.filterNotNull().sumOf { if (it.id == 995) it.count else 0 },
        )
    }

    @Test
    fun `cache exposes the native GE interface initializers`() {
        val main = assertNotNull(ServerCacheManager.getInterface(465)?.components?.get(0)?.onLoad)
        assertEquals(803, main[0])
        // Argument 8 of script 803 becomes argument 2 (dynamic button parent) of script 819.
        assertEquals(GeConfig.COMP_DETAILS_STATUS.asRSCM(RSCMType.COMPONENT), main[9])
        assertEquals((465 shl 16) or 23, main[9])
        val side = assertNotNull(ServerCacheManager.getInterface(467)?.components?.get(0))
        assertContentEquals(arrayOf<Any>(781, -2147483645, (467 shl 16) or 1), side.onLoad)
        assertFalse(side.hide)
    }

    @Test
    fun `buy setup sends the actual inventory through the native inventory update processor`() {
        val f = Fixture(directory)
        f.player.inv[0] = InvObj(ServerCacheManager.getItemOrDefault(995), Int.MAX_VALUE)
        f.player.inv[7] = InvObj(ServerCacheManager.getItemOrDefault(11802), 1)
        f.player.inv[27] = InvObj(ServerCacheManager.getItemOrDefault(554), 2000)
        // Already-subscribed inventory with no pending changes: opening must still resync it.
        f.player.transmittedInvs.add(93)
        f.player.inv.clearModifiedSlots()
        f.open()
        f.click(GeConfig.indexSlot(0), GeConfig.INDEX_CHILD_BUY)
        assertNotNull(f.player.activeCoroutine, "Buy opens the native item-search dialog")
        ResumePObjDialogHandler().handle(f.player, ResumePObjDialog(11802))
        assertTrue(f.player.ui.containsModal(GeConfig.OFFERS_SIDE))
        assertTrue(f.packets.any { it is IfOpenSub && it.interfaceId == 467 })
        f.transmit()
        f.assertInventoryWire()
        assertEquals(11802, f.player.vars[GeConfig.VARP_ITEM])
        assertEquals(GeOfferType.Buy.clientId, f.player.vars[GeConfig.VARBIT_TYPE])
        f.player.ifClose(f.events)
        assertTrue(
            f.player.transmittedInvs.contains(93),
            "Closing GE must not stop normal inventory updates",
        )
    }

    @Test
    fun `reopening GE retains side inventory offer clicks after replacing the old side modal`() {
        val f = Fixture(directory)
        f.player.inv[3] = InvObj(ServerCacheManager.getItemOrDefault(4151), 1)
        f.open()
        f.openLenient()
        f.click(GeConfig.COMP_SIDE_ITEMS, 3)
        assertEquals(4151, f.player.vars[GeConfig.VARP_ITEM])
        assertEquals(GeOfferType.Sell.clientId, f.player.vars[GeConfig.VARBIT_TYPE])
        assertEquals(1, f.player.vars[GeConfig.VARBIT_QUANTITY])
        assertEquals(
            InvObj(ServerCacheManager.getItemOrDefault(4151), 1),
            f.player.inv[3],
            "Setup does not escrow the item before Confirm",
        )
    }

    @Test
    fun `partial buy abort preserves purchases and refunds only the unfilled amount in every slot`() {
        for (slot in 0..7) {
            val f = Fixture(directory)
            val offer = f.book.place(1, "buyer", slot, GeOfferType.Buy, 4151, 100, 10)
            f.book.place(2, "seller", 0, GeOfferType.Sell, 4151, 100, 3)
            f.open()
            f.click(GeConfig.indexSlot(slot), 2)
            f.click("component.ge_offers:details_status", 0, 2)
            assertTrue(offer.isActive, "Unsupported operation must remain gated")
            f.click("component.ge_offers:details_status", 0)
            assertTrue(offer.finished)
            assertEquals(3, offer.collectItems)
            assertEquals(700, offer.collectCoins)
            f.click("component.ge_offers:details_status", 0)
            assertEquals(700, offer.collectCoins)
        }
    }

    @Test
    fun `modify sell keeps collected coins and sets up only unfilled items`() {
        val f = Fixture(directory)
        val offer = f.book.place(1, "seller", 4, GeOfferType.Sell, 4151, 100, 10)
        f.book.place(2, "buyer", 0, GeOfferType.Buy, 4151, 100, 3)
        f.open()
        f.click(GeConfig.indexSlot(4), 2)
        f.click("component.ge_offers:details_status", 1)
        assertTrue(offer.finished)
        assertNull(f.book.offer(1, 4))
        assertEquals(7, f.player.vars[GeConfig.VARBIT_QUANTITY])
        assertEquals(GeOfferType.Sell.clientId, f.player.vars[GeConfig.VARBIT_TYPE])
        assertEquals(
            288,
            f.player.inv.objs.filterNotNull().sumOf { if (it.id == 995) it.count else 0 },
        )
        val note = assertNotNull(ServerCacheManager.getItem(4151)).certlink
        assertEquals(
            7,
            f.player.inv.objs.filterNotNull().sumOf { if (it.id == note) it.count else 0 },
        )
    }

    @Test
    fun `modify with a full inventory keeps escrow in the collection box for retry`() {
        val f = Fixture(directory)
        for (slot in f.player.inv.indices) f.player.inv[slot] =
            InvObj(ServerCacheManager.getItemOrDefault(4151), 1)
        val offer = f.book.place(1, "buyer", 0, GeOfferType.Buy, 11802, 1000, 2)
        f.open()
        f.click(GeConfig.indexSlot(0), 2)
        repeat(2) { f.click("component.ge_offers:details_status", 1) }
        assertSame(offer, f.book.offer(1, 0))
        assertEquals(2000, offer.collectCoins)
        assertTrue(offer.finished)
        f.player.inv[0] = null
        f.click("component.ge_offers:details_status", 1)
        assertNull(f.book.offer(1, 0))
        assertEquals(2000, f.player.inv[0]?.count)
        assertEquals(995, f.player.inv[0]?.id)
        assertEquals(2, f.player.vars[GeConfig.VARBIT_QUANTITY])
    }

    private class Fixture(directory: Path) {
        val packets = mutableListOf<Any>()
        val events = EventBus()
        val player =
            Player().apply {
                accountId = 1
                uuid = 400L
                observerUUID = 400L
                currentMapClock = 100
                processedMapClock = 100
                ui.topLevel = UserInterface(161)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
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
        private val items = GeItemData.load()
        val book = GeExchange(items) { 1000L }
        val service = GeService(book, items, PlayerList(), directory.resolve("book.json"))
        private val context = mock(ProtectedAccessContext::class.java)
        private val launcher: ProtectedAccessLauncher
        private val input: If3ButtonHandler

        init {
            `when`(context.eventBus).thenReturn(events)
            val factory = mock(ProtectedAccessContextFactory::class.java)
            `when`(factory.create()).thenReturn(context)
            launcher = ProtectedAccessLauncher(factory)
            input = If3ButtonHandler(events, launcher)
            with(GrandExchangeScript(service)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            service.onLogin(player)
        }

        fun access(action: suspend ProtectedAccess.() -> Unit) {
            assertTrue(launcher.launch(player) { action() })
        }

        fun open() = access { service.open(this) }

        fun openLenient() {
            launcher.launchLenient(player) { service.open(this) }
        }

        fun transmit() {
            val processor =
                PlayerInvUpdateProcessor(
                    mock(ShuffledPlayerList::class.java),
                    mock(GameExceptionHandler::class.java),
                    emptySet(),
                )
            processor.process(player)
            processor.cleanUp()
        }

        fun assertInventoryWire() {
            val update = packets.filterIsInstance<UpdateInvFull>().single { it.inventoryId == 93 }
            val buffer = Unpooled.buffer()
            try {
                UpdateInvFullEncoder().encode(NopStreamCipher, JagByteBuf(buffer), update)
                assertEquals(-1327, buffer.readInt())
                assertEquals(93, buffer.readUnsignedShort())
                val size = buffer.readUnsignedShort()
                assertEquals(28, size)
                repeat(size) { slot ->
                    val small = (128 - buffer.readUnsignedByte()) and 255
                    val count = if (small == 255) buffer.readInt() else small
                    val id = buffer.readUnsignedShort() - 1
                    assertEquals(player.inv[slot]?.id ?: -1, id, "slot $slot")
                    assertEquals(player.inv[slot]?.count ?: 0, count, "slot $slot")
                }
                assertFalse(buffer.isReadable)
            } finally {
                buffer.release()
            }
        }

        fun click(component: String, child: Int, op: Int = 1) {
            input.handle(
                player,
                If3Button(CombinedId(component.asRSCM(RSCMType.COMPONENT)), child, -1, op),
            )
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
