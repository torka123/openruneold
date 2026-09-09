package org.rsmod.content.interfaces.collectionlog

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import io.netty.buffer.Unpooled
import kotlin.test.*
import net.rsprot.buffer.JagByteBuf
import net.rsprot.crypto.cipher.NopStreamCipher
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.codec.inv.UpdateInvFullEncoder
import net.rsprot.protocol.game.outgoing.codec.misc.player.RunClientScriptEncoder
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.collectionlog.*
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.plugin.scripts.ScriptContext

class CollectionLogClickTest {
    @Test
    fun `real modal and overlay packets visit every collection and search result`() {
        for (modal in listOf(true, false)) for (ownership in 0..2) {
            val f = ClickFixture(modal, ownership)
            var globalIndex = 0
            f.catalog.categories.forEachIndexed { categoryIndex, category ->
                f.click(CollectionLogUi.CATEGORY_COMPONENTS[categoryIndex])
                category.sections.forEachIndexed { sectionIndex, section ->
                    f.messages.clear()
                    f.click(CollectionLogUi.SECTION_COMPONENTS[categoryIndex], sectionIndex)
                    f.assertView(categoryIndex, sectionIndex)
                    f.messages.clear()
                    f.click(CollectionLogUi.SEARCH_RESULTS_COMPONENT, globalIndex++)
                    f.assertView(categoryIndex, sectionIndex)
                    println(
                        "COLLECTION_CLICK modal=$modal ownership=$ownership category=${category.name} section=${section.name} slots=${section.slots.size} PASS"
                    )
                }
            }
            assertEquals(124, globalIndex)
            assertFalse(f.disconnected)
        }
    }

    @Test
    fun `invalid and stale section packets cannot redraw the wrong collection`() {
        val f = ClickFixture(true, 0)
        f.click(CollectionLogUi.CATEGORY_COMPONENTS[0])
        f.click(CollectionLogUi.CATEGORY_COMPONENTS[1])
        val selected = f.ui.session(f.player)
        for ((component, index) in
            listOf(
                CollectionLogUi.SECTION_COMPONENTS[0] to 1,
                CollectionLogUi.SECTION_COMPONENTS[1] to -1,
                CollectionLogUi.SECTION_COMPONENTS[1] to 65534,
                CollectionLogUi.SEARCH_RESULTS_COMPONENT to -1,
                CollectionLogUi.SEARCH_RESULTS_COMPONENT to 124,
            )) {
            f.messages.clear()
            f.click(component, index)
            assertEquals(selected, f.ui.session(f.player), "$component sub=$index")
            assertTrue(
                f.messages.filterIsInstance<RunClientScript>().none {
                    it.id == CollectionLogUi.DRAW_SCRIPT
                }
            )
        }
    }

    @Test
    fun `every search keyboard control sends valid item payloads and throttles duplicate refreshes`() {
        for (modal in listOf(true, false)) {
            val f = ClickFixture(modal, 1)
            CollectionLogUi.SEARCH_INPUT_COMPONENTS.forEach { component ->
                f.player.currentMapClock++
                f.messages.clear()
                f.click(component)
                val packets =
                    f.messages.filterIsInstance<RunClientScript>().filter {
                        it.id == CollectionLogUi.SEARCH_RESULT_SCRIPT
                    }
                assertEquals(1921, packets.size, component)
                val expected =
                    f.catalog.sections.flatMapIndexed { index, section ->
                        section.slots
                            .sortedBy { it.order }
                            .map { slot ->
                                listOf(
                                    slot.displayItemId,
                                    f.state.quantities[slot.canonicalItemId] ?: 0,
                                    index,
                                    section.structId,
                                )
                            }
                    }
                packets.zip(expected).forEach { (packet, args) ->
                    assertEquals(args, packet.values)
                    assertContentEquals(CharArray(4) { 'i' }, packet.types)
                    assertScriptWire(packet)
                }
                f.messages.clear()
                f.click(component)
                assertTrue(
                    f.messages.none { it is RunClientScript },
                    "Duplicate refresh in same tick: $component",
                )
                assertFalse(f.disconnected)
            }
        }
    }

    @Test
    fun `closing then reopening does not leave a stale collection or search session`() {
        for (topLevel in listOf(548, 161)) for (modal in listOf(true, false)) {
            val f = ClickFixture(modal, 1, topLevel)
            repeat(5) {
                f.click(CollectionLogUi.SEARCH_RESULTS_COMPONENT, f.catalog.sections.lastIndex)
                f.click(CollectionLogUi.SEARCH_INPUT_COMPONENTS.first())
                assertNotNull(f.player.attr[CollectionLogUi.LAST_SEARCH_REFRESH_TICK])
                f.messages.clear()
                f.click(CollectionLogUi.CLOSE_COMPONENT)
                assertNull(f.ui.session(f.player), "Close must remove session for modal=$modal")
                assertNull(f.player.attr[CollectionLogUi.LAST_SEARCH_REFRESH_TICK])
                assertFalse(f.player.ui.containsModal(CollectionLogUi.INTERFACE))
                assertFalse(f.player.ui.containsOverlay(CollectionLogUi.INTERFACE))
                val target =
                    if (modal) "component.toplevel_osrs_stretch:mainmodal"
                    else "component.toplevel_osrs_stretch:floater"
                val baseTarget = Component(target.asRSCM(RSCMType.COMPONENT))
                val expectedTarget = f.player.ui.gameframe.getOrNull(baseTarget) ?: baseTarget
                assertTrue(
                    f.messages.filterIsInstance<IfCloseSub>().any {
                        it.combinedId == expectedTarget.packed
                    },
                    "Client must receive close for the actual parent slot",
                )
                assertEquals(
                    1,
                    f.messages.filterIsInstance<RunClientScript>().count {
                        it.id == CollectionLogUi.CLOSE_RESET_SCRIPT
                    },
                )
                val cleared = f.messages.filterIsInstance<UpdateInvFull>().single()
                repeat(cleared.capacity) { slot -> assertEquals(-1L, cleared.getObject(slot)) }
                val before = f.messages.size
                f.click(CollectionLogUi.CLOSE_COMPONENT)
                f.click(CollectionLogUi.SECTION_COMPONENTS.last(), 0)
                assertEquals(before, f.messages.size)
                f.open()
                f.assertView(0, 0)
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

private class ClickFixture(private val modal: Boolean, ownership: Int, topLevel: Int = 161) {
    val messages = mutableListOf<Any>()
    var disconnected = false
    val catalog = CollectionLogCatalogRepository()
    private val service = CollectionLogService(catalog)
    val state = CollectionLogState()
    private val events = EventBus()
    val ui = CollectionLogUi(service, catalog, events)
    val player =
        Player().apply {
            currentMapClock = 100
            processedMapClock = 100
            this.ui.topLevel = UserInterface(topLevel)
            // Install the cache-backed target translations used by the actual gameframe.
            for (name in listOf("mainmodal", "floater")) {
                val original =
                    ServerCacheManager.fromComponent("component.toplevel_osrs_stretch:$name")
                val translated =
                    requireNotNull(ServerCacheManager.getInterface(topLevel))
                        .components
                        .values
                        .single { it.internalName == name }
                this.ui.gameframe[Component(original.packed)] = Component(translated.packed)
            }
        }
    private val input: If3ButtonHandler

    init {
        player.client =
            object : Client<Any, Any> {
                override fun write(message: Any) {
                    messages += message
                }

                override fun close() {
                    disconnected = true
                }

                override fun read(player: Player) = Unit

                override fun flush() = Unit

                override fun flushHighPriority() = Unit

                override fun unregister(service: Any, player: Player) = Unit
            }
        catalog.slots.forEachIndexed { index, slot ->
            if (ownership == 2 || ownership == 1 && index % 2 == 0)
                state.quantities[slot.canonicalItemId] = slot.quantityCap
        }
        service.save(player, state)
        val context = mock(ProtectedAccessContext::class.java)
        `when`(context.eventBus).thenReturn(events)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        val launcher = ProtectedAccessLauncher(factory)
        input = If3ButtonHandler(events, launcher)
        with(CollectionLogScript(ui)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        open()
    }

    fun open() {
        if (modal) player.ifOpenMainModal(CollectionLogUi.INTERFACE, events)
        // Same native opening path as the account-summary Collection Log button.
        else player.ifOpenOverlay(CollectionLogUi.INTERFACE, events)
    }

    fun click(component: String, sub: Int = -1) {
        input.handle(
            player,
            If3Button(CombinedId(component.asRSCM(RSCMType.COMPONENT)), sub, -1, 1),
        )
    }

    fun assertView(categoryIndex: Int, sectionIndex: Int) {
        assertEquals(CollectionLogUiSession(categoryIndex, sectionIndex), ui.session(player))
        val draw =
            messages.filterIsInstance<RunClientScript>().last {
                it.id == CollectionLogUi.DRAW_SCRIPT
            }
        val category = catalog.categories[categoryIndex]
        val expectedArgs: List<Any> =
            listOf(categoryIndex) +
                (685..688).map { category.clientParams.getValue(it) } +
                listOf(category.structId, sectionIndex)
        assertEquals(expectedArgs, draw.values)
        assertContentEquals(CharArray(7) { 'i' }, draw.types)
        assertScriptWire(draw)
        val inv = messages.filterIsInstance<UpdateInvFull>().last()
        assertEquals(620, inv.inventoryId)
        assertTrue(
            messages.indexOfLast { it === inv } < messages.indexOfLast { it === draw },
            "Inventory must precede draw",
        )
        val capacity = inv.capacity
        val section = category.sections[sectionIndex]
        val buffer = Unpooled.buffer()
        try {
            // The real encoder returns its pooled inventory; inspect the bytes, not that inventory,
            // after encoding. This covers large sections and the 255+ quantity representation.
            UpdateInvFullEncoder().encode(NopStreamCipher, JagByteBuf(buffer), inv)
            assertEquals(inv.combinedId, buffer.readInt())
            assertEquals(620, buffer.readUnsignedShort())
            assertEquals(capacity, buffer.readUnsignedShort())
            repeat(capacity) { index ->
                val smallCount = (128 - buffer.readUnsignedByte()) and 255
                val count = if (smallCount == 255) buffer.readInt() else smallCount
                val id = buffer.readUnsignedShort() - 1
                val slot = section.slots.getOrNull(index)
                val expectedCount = slot?.let { state.quantities[it.canonicalItemId] } ?: 0
                assertEquals(expectedCount, count, "${section.name} slot=$index")
                assertEquals(
                    if (expectedCount > 0) slot!!.displayItemId else -1,
                    id,
                    "${section.name} slot=$index",
                )
            }
            assertFalse(buffer.isReadable, "Trailing inventory bytes for ${section.name}")
        } finally {
            buffer.release()
        }
        assertNull(player.activeCoroutine)
        assertFalse(disconnected)
    }
}

private fun assertScriptWire(message: RunClientScript) {
    val buffer = Unpooled.buffer()
    try {
        RunClientScriptEncoder().encode(NopStreamCipher, JagByteBuf(buffer), message)
        message.types.forEach { assertEquals(it.code, buffer.readUnsignedByte().toInt()) }
        assertEquals(0, buffer.readUnsignedByte().toInt())
        message.values.asReversed().forEach { assertEquals(it, buffer.readInt()) }
        assertEquals(message.id, buffer.readInt())
        assertFalse(buffer.isReadable, "Trailing clientscript bytes for ${message.id}")
    } finally {
        buffer.release()
    }
}
