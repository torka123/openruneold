package org.rsmod.content.interfaces.collectionlog

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogRecordStatus
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.CollectionLogState
import org.rsmod.api.collectionlog.PlayerCollectionLogLoad
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

class CollectionLogUiProtocolTest {
    @Test
    fun `revision 240 uses the collection draw wrapper clientscript`() {
        initRevision240()
        assertEquals(7797, CollectionLogUi.DRAW_SCRIPT)
    }

    @Test
    fun `revision 240 close button maps to collection child 1`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val ui = CollectionLogUi(CollectionLogService(catalog), catalog)
        val packed = CollectionLogUi.CLOSE_COMPONENT.asRSCM(RSCMType.COMPONENT)

        assertEquals((621 shl 16) or 1, packed)
        assertEquals(CollectionLogUi.CLOSE_COMPONENT_PACKED, packed)
        assertEquals(
            CollectionLogUi.CLOSE_COMPONENT_NAME,
            ServerCacheManager.fromComponent(packed).internalName,
        )
        assertEquals(packed, ui.resolveCloseComponent())
    }

    @Test
    fun `revision 240 search keyboard and combat achievements controls map correctly`() {
        initRevision240()

        assertEquals(29, CollectionLogUi.SEARCH_INPUT_COMPONENTS.size)
        CollectionLogUi.SEARCH_INPUT_COMPONENTS.forEachIndexed { index, component ->
            assertEquals((621 shl 16) or (42 + index), component.asRSCM(RSCMType.COMPONENT))
        }
        assertEquals(
            (621 shl 16) or 21,
            CollectionLogUi.COMBAT_ACHIEVEMENTS_COMPONENT.asRSCM(RSCMType.COMPONENT),
        )
        assertEquals(4100, CollectionLogUi.SEARCH_RESULT_SCRIPT)
    }

    @Test
    fun `search data covers every catalog slot and keeps global section ordering`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val ui = CollectionLogUi(CollectionLogService(catalog), catalog)
        val state = CollectionLogState()
        val firstSlot = catalog.sections.first().slots.first()
        state.quantities[firstSlot.canonicalItemId] = 7

        val transmissions = ui.searchTransmissions(state)

        assertEquals(catalog.catalog.totalSlots, transmissions.size)
        assertEquals(0, transmissions.first().globalSectionIndex)
        assertEquals(catalog.sections.first().structId, transmissions.first().sectionStructId)
        assertEquals(firstSlot.displayItemId, transmissions.first().displayItemId)
        assertEquals(7, transmissions.first().ownedAmount)
    }

    @Test
    fun `revision 240 collection draw receives the seven wrapper arguments in client order`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val ui = CollectionLogUi(CollectionLogService(catalog), catalog)
        val params = mapOf(685 to 101, 686 to 102, 687 to 103, 688 to 104)

        val args = ui.drawSectionScriptArgs(4, params, categoryStructId = 200, sectionIndex = 3)

        assertEquals(7, args.size)
        assertEquals(listOf(4, 101, 102, 103, 104, 200, 3), args)
    }

    @Test
    fun `revision 240 items contents name maps to collection child 37`() {
        initRevision240()
        val packed = CollectionLogUi.ITEMS_CONTENTS_COMPONENT.asRSCM(RSCMType.COMPONENT)

        assertEquals((621 shl 16) or 37, packed)
        assertEquals(40_697_893, packed)
        assertEquals(
            CollectionLogUi.ITEMS_CONTENTS_COMPONENT_NAME,
            ServerCacheManager.fromComponent(packed).internalName,
        )
    }

    @Test
    fun `revision 240 search results use the actual clickzone component`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val ui = CollectionLogUi(CollectionLogService(catalog), catalog)
        val packed = CollectionLogUi.SEARCH_RESULTS_COMPONENT.asRSCM(RSCMType.COMPONENT)

        assertEquals((621 shl 16) or 86, packed)
        assertEquals(CollectionLogUi.SEARCH_RESULTS_COMPONENT_PACKED, packed)
        assertEquals(
            CollectionLogUi.SEARCH_RESULTS_COMPONENT_NAME,
            ServerCacheManager.fromComponent(packed).internalName,
        )
        assertEquals(packed, ui.resolveSearchResultsClickzone())
    }

    @Test
    fun `section inventory is transmitted before the revision 240 draw script`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val ui = CollectionLogUi(CollectionLogService(catalog), catalog)
        val client = RecordingClient()
        val player = Player(client = client)

        ui.selectCategory(player, 0)

        val inventoryIndex =
            client.messages.indexOfFirst { it.javaClass.simpleName == "UpdateInvFull" }
        val drawScriptIndex =
            client.messages.indexOfLast { it.javaClass.simpleName == "RunClientScript" }
        assertTrue(inventoryIndex >= 0, "The selected section must transmit inventory 620.")
        assertTrue(
            drawScriptIndex > inventoryIndex,
            "Client script 7797 reads inventory 620 immediately, so the inventory must arrive first.",
        )
    }

    @Test
    fun `unlock state survives service reload and duplicate award events are ignored`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val service = CollectionLogService(catalog)
        val player = Player()
        val source = CollectionLogAwardSource(CollectionLogAwardKind.AdminTest, "protocol-test")
        val itemId =
            catalog.catalog.categories.first().sections.first().slots.first().canonicalItemId

        val first = service.record(player, itemId, 1, source, "same-drop")
        val duplicate = service.record(player, itemId, 1, source, "same-drop")
        val restoredPlayer = Player()
        restoredPlayer.attr[CollectionLogService.STATE] =
            requireNotNull(player.attr[CollectionLogService.STATE])
        val restored = assertIs<PlayerCollectionLogLoad.Ready>(service.load(restoredPlayer)).state

        assertEquals(CollectionLogRecordStatus.Recorded, first.status)
        assertEquals(CollectionLogRecordStatus.DuplicateEvent, duplicate.status)
        assertEquals(1, service.engine.quantity(restored, itemId))
    }

    @Test
    fun `popup preference sends the captured collection log notification protocol`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val service = CollectionLogService(catalog)
        val ui = CollectionLogUi(service, catalog, EventBus())
        val client = RecordingClient()
        val player = Player(client = client)
        val source = CollectionLogAwardSource(CollectionLogAwardKind.AdminTest, "popup-test")
        val slot = catalog.catalog.categories.first().sections.first().slots.first()
        VarPlayerIntMapSetter.set(player, CollectionLogUi.COLLECTION_LOG_NOTIFICATION_VARBIT, 2)
        ui.installRecordHook()

        service.record(player, slot.canonicalItemId, 1, source, "new-drop")

        val openIndex =
            client.messages.indexOfFirst {
                it.javaClass.simpleName == "IfOpenSub" &&
                    it.toString().contains("interfaceId=660") &&
                    it.toString().contains("destinationComponentId=13")
            }
        val scriptIndex =
            client.messages.indexOfFirst {
                it.javaClass.simpleName == "RunClientScript" &&
                    it.toString().contains("id=3343") &&
                    it.toString().contains("Collection Log") &&
                    it.toString().contains(slot.name)
            }
        assertTrue(openIndex >= 0, "The stock notification interface must open on child 13.")
        assertTrue(scriptIndex > openIndex, "Script 3343 must run after interface 660 opens.")
    }

    @Test
    fun `chat-only preference and duplicate unlocks do not emit a popup`() {
        initRevision240()
        val catalog = CollectionLogCatalogRepository()
        val service = CollectionLogService(catalog)
        val ui = CollectionLogUi(service, catalog, EventBus())
        val client = RecordingClient()
        val player = Player(client = client)
        val source = CollectionLogAwardSource(CollectionLogAwardKind.AdminTest, "popup-mode-test")
        val itemId = catalog.catalog.categories.first().sections.first().slots.first().canonicalItemId
        VarPlayerIntMapSetter.set(player, CollectionLogUi.COLLECTION_LOG_NOTIFICATION_VARBIT, 1)
        ui.installRecordHook()

        service.record(player, itemId, 1, source, "chat-only")
        assertFalse(client.messages.any { it.toString().contains("id=3343") })

        VarPlayerIntMapSetter.set(player, CollectionLogUi.COLLECTION_LOG_NOTIFICATION_VARBIT, 3)
        service.record(player, itemId, 1, source, "second-copy")
        assertFalse(
            client.messages.any { it.toString().contains("id=3343") },
            "A later copy of an already-unlocked item must not produce a new-item popup.",
        )
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun close() {}

        override fun write(message: Any) {
            messages += message
        }

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    private companion object {
        private val revision240 =
            lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ServerCacheManager.init(240) }

        private fun initRevision240() = revision240.value
    }
}
