package org.rsmod.content.interfaces.collectionlog

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import dev.openrune.types.NpcServerType
import dev.openrune.types.util.UncheckedType
import dev.openrune.types.varp.VarpServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogOwnedItem
import org.rsmod.api.collectionlog.CollectionLogRank
import org.rsmod.api.collectionlog.CollectionLogRanks
import org.rsmod.api.collectionlog.CollectionLogRecordContext
import org.rsmod.api.collectionlog.CollectionLogRecordHook
import org.rsmod.api.collectionlog.CollectionLogRecordStatus
import org.rsmod.api.collectionlog.CollectionLogSection
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.CollectionLogState
import org.rsmod.api.collectionlog.PlayerCollectionLogLoad
import org.rsmod.api.collectionlog.currentCollectionLogDay
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

public data class CollectionLogUiSession(
    public val categoryIndex: Int = 0,
    public val sectionIndex: Int = 0,
)

internal data class CollectionLogSearchTransmission(
    val displayItemId: Int,
    val ownedAmount: Int,
    val globalSectionIndex: Int,
    val sectionStructId: Int,
)

@OptIn(UncheckedType::class)
@Singleton
public class CollectionLogUi
@Inject
constructor(
    private val service: CollectionLogService,
    private val catalog: CollectionLogCatalogRepository,
    private val eventBus: EventBus? = null,
    private val protectedAccess: ProtectedAccessLauncher? = null,
) : CollectionLogRecordHook {
    private val projection: CollectionLogUiProjection = CollectionLogUiProjection(catalog)

    public fun validateAssets() {
        resolveItemsContentsComponent()
        resolveSearchResultsClickzone()
        resolveCloseComponent()
    }

    public fun installRecordHook() {
        service.addRecordHook(this)
    }

    public fun onLogin(player: Player) {
        val state = readyState(player) ?: return
        reconcileHistoricalOwnership(player, state)
        syncProgress(player, state)
        syncOverview(player, state)
    }

    override fun onRecord(context: CollectionLogRecordContext) {
        if (context.result.status != CollectionLogRecordStatus.Recorded) return
        val player = context.player
        if (context.result.newlyObtained) {
            showNewItemNotification(player, requireNotNull(context.result.canonicalItemId))
        }
        val state = readyState(player) ?: return
        syncProgress(player, state)
        syncOverview(player, state)
        val session = player.attr[SESSION] ?: return
        selectSection(player, session.categoryIndex, session.sectionIndex, state)
    }

    @OptIn(InternalApi::class)
    private fun showNewItemNotification(player: Player, canonicalItemId: Int) {
        val notificationMode = player.vars[COLLECTION_LOG_NOTIFICATION_VARBIT]
        if (notificationMode and COLLECTION_LOG_POPUP_NOTIFICATION == 0) return
        val events = eventBus ?: return
        val name = catalog.slotsFor(canonicalItemId).firstOrNull()?.name ?: "item"
        player.ifOpenOverlay(NOTIFICATION_INTERFACE, NOTIFICATION_TARGET, events)
        ClientScripts.notificationDisplay(
            player,
            title = "Collection Log",
            text = "New item:<br><br><col=ffffff>$name</col>",
            colour = COLLECTION_LOG_NOTIFICATION_COLOUR,
        )
        protectedAccess?.launchLenient(player) {
            delay(COLLECTION_LOG_NOTIFICATION_DURATION_TICKS)
            player.ifCloseOverlay(NOTIFICATION_INTERFACE, events)
        }
    }

    public fun onOpen(player: Player) {
        val state = readyState(player) ?: return
        player.attr[SESSION] = CollectionLogUiSession()
        player.attr.remove(LAST_SEARCH_REFRESH_TICK)
        syncProgress(player, state)
        bindAccess(player)
        selectCategory(player, 0)
    }

    public fun prepareOverview(player: Player): Boolean {
        val state = readyState(player) ?: return false
        syncProgress(player, state)
        syncOverview(player, state)
        return true
    }

    public fun selectCategory(player: Player, categoryIndex: Int) {
        val state = readyState(player) ?: return
        val category = catalog.category(categoryIndex) ?: return
        player.attr[SESSION] = CollectionLogUiSession(categoryIndex, 0)
        bindSectionAccess(player, categoryIndex)
        if (category.sections.isNotEmpty()) {
            selectSection(player, categoryIndex, 0, state)
        }
    }

    public fun selectCurrentSection(player: Player, sectionIndex: Int) {
        val session = player.attr[SESSION] ?: CollectionLogUiSession()
        val state = readyState(player) ?: return
        selectSection(player, session.categoryIndex, sectionIndex, state)
    }

    public fun selectSearchResult(player: Player, globalSectionIndex: Int) {
        val location = projection.searchResult(globalSectionIndex) ?: return
        val state = readyState(player) ?: return
        bindSectionAccess(player, location.categoryIndex)
        selectSection(player, location.categoryIndex, location.sectionIndex, state)
    }

    public fun giveCollectionBook(player: Player): Boolean {
        if (COLLECTION_BOOKS.any { owns(player, it) }) {
            player.mes("You already have a Collection log or a ranked version of it.")
            return false
        }
        if (player.invAdd(player.inv, COLLECTION_BOOK).failure) {
            player.mes("You need a free inventory space for the Collection log.")
            return false
        }
        player.mes("The Collector gives you a Collection log.")
        return true
    }

    public fun claimRankReward(player: Player): Boolean {
        val state = readyState(player) ?: return false
        val completed = service.engine.completedSlots(state)
        val rank =
            CollectionLogRanks.highestAchieved(completed, catalog.catalog.uniqueCanonicalEntries)
        if (rank == null) {
            player.mes(
                "You need at least 100 collection entries for the first staff of collection."
            )
            return false
        }
        if (service.engine.hasClaimedRankReward(state, rank)) {
            player.mes("You have already claimed the ${rank.displayName} staff of collection.")
            return false
        }
        if (owns(player, rank.staffInternalName)) {
            service.engine.markRankRewardClaimed(state, rank)
            service.save(player, state)
            player.mes("You already have the ${rank.displayName} staff of collection.")
            return false
        }
        if (player.invAdd(player.inv, rank.staffInternalName).failure) {
            player.mes("You need a free inventory space for your staff of collection.")
            return false
        }
        check(service.engine.markRankRewardClaimed(state, rank)) {
            "Collection Log rank reward was claimed concurrently"
        }
        service.save(player, state)
        player.mes("The Collector awards you the ${rank.displayName} staff of collection.")
        return true
    }

    public fun createRankBook(player: Player, rank: CollectionLogRank): Boolean {
        if (owns(player, rank.bookInternalName)) {
            player.mes("You already have the ${rank.displayName} Collection log.")
            return false
        }
        if (player.invAdd(player.inv, rank.bookInternalName).failure) {
            player.mes("You need a free inventory space to create the Collection log.")
            return false
        }
        player.mes("Your staff creates a ${rank.displayName} Collection log.")
        return true
    }

    public fun onClose(player: Player) {
        player.attr.remove(SESSION)
        player.attr.remove(LAST_SEARCH_REFRESH_TICK)
        player.runClientScript(CLOSE_RESET_SCRIPT)
        UpdateInventory.updateInvFull(player, emptyCollectionInventory())
    }

    public fun refreshSearch(player: Player) {
        val state = readyState(player) ?: return
        val tick = player.currentMapClock
        if (player.attr[LAST_SEARCH_REFRESH_TICK] == tick) return
        player.attr[LAST_SEARCH_REFRESH_TICK] = tick
        searchTransmissions(state).forEach { transmission ->
            player.runClientScript(
                SEARCH_RESULT_SCRIPT,
                transmission.displayItemId,
                transmission.ownedAmount,
                transmission.globalSectionIndex,
                transmission.sectionStructId,
            )
        }
    }

    internal fun searchTransmissions(
        state: CollectionLogState
    ): List<CollectionLogSearchTransmission> =
        catalog.sections.flatMapIndexed { globalSectionIndex, section ->
            section.slots
                .sortedBy { slot -> slot.order }
                .map { slot ->
                    CollectionLogSearchTransmission(
                        displayItemId = slot.displayItemId,
                        ownedAmount = state.quantities[slot.canonicalItemId] ?: 0,
                        globalSectionIndex = globalSectionIndex,
                        sectionStructId = section.structId,
                    )
                }
        }

    public fun session(player: Player): CollectionLogUiSession? = player.attr[SESSION]

    private fun selectSection(
        player: Player,
        categoryIndex: Int,
        sectionIndex: Int,
        state: CollectionLogState,
    ) {
        val view = projection.section(state, categoryIndex, sectionIndex) ?: return
        player.attr[SESSION] = CollectionLogUiSession(categoryIndex, sectionIndex)
        UpdateInventory.updateInvFull(player, sectionInventory(view.section, state))
        VarPlayerIntMapSetter.set(
            player,
            COLLECTION_GENERAL_VARP,
            sectionActivityCount(player, view.section),
        )
        val params = view.category.clientParams
        player.runClientScript(
            id = DRAW_SCRIPT,
            args =
                drawSectionScriptArgs(categoryIndex, params, view.category.structId, sectionIndex),
        )
    }

    private fun syncProgress(player: Player, state: CollectionLogState) {
        val (obtained, total) = projection.globalProgress(state)
        VarPlayerIntMapSetter.set(player, GLOBAL_COUNT_VARP, obtained)
        VarPlayerIntMapSetter.set(player, GLOBAL_MAX_VARP, total)
        VarPlayerIntMapSetter.set(player, HIGHSCORES_COUNT_VARP, obtained)
        projection.categoryProgress(state).forEach { progress ->
            val pair = CATEGORY_VARPS[progress.name] ?: return@forEach
            VarPlayerIntMapSetter.set(player, pair.first, progress.obtained)
            VarPlayerIntMapSetter.set(player, pair.second, progress.total)
        }
    }

    private fun syncOverview(player: Player, state: CollectionLogState) {
        VarPlayerIntMapSetter.set(
            player,
            requireNotNull(ServerCacheManager.getVarbit(CURRENT_DAY_VARBIT_ID)),
            currentCollectionLogDay(),
        )
        val newestFirst = state.recentUnlocks.asReversed()
        repeat(RECENT_UNLOCK_COUNT) { index ->
            val unlock = newestFirst.getOrNull(index)
            VarPlayerIntMapSetter.set(
                player,
                requireNotNull(ServerCacheManager.getVarp(RECENT_ITEM_VARP_START + index * 2)),
                unlock?.itemId ?: -1,
            )
            VarPlayerIntMapSetter.set(
                player,
                requireNotNull(ServerCacheManager.getVarp(RECENT_DATE_VARP_START + index * 2)),
                unlock?.day ?: 0,
            )
        }
    }

    private fun bindAccess(player: Player) {
        CATEGORY_COMPONENTS.forEach { component ->
            player.ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        SEARCH_INPUT_COMPONENTS.forEach { component ->
            player.ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        player.ifSetEvents(COMBAT_ACHIEVEMENTS_COMPONENT, -1..-1, IfEvent.Op1)
        player.ifSetEvents(SEARCH_RESULTS_COMPONENT, 0 until catalog.sections.size, IfEvent.Op1)
        player.ifSetEvents(ITEMS_COMPONENT, 0..MAX_ITEM_EVENT_SLOT, IfEvent.Op1)
    }

    private fun bindSectionAccess(player: Player, categoryIndex: Int) {
        val category = catalog.category(categoryIndex) ?: return
        val component = SECTION_COMPONENTS.getOrNull(categoryIndex) ?: return
        player.ifSetEvents(component, 0 until category.sections.size, IfEvent.Op1)
    }

    private fun sectionInventory(
        section: CollectionLogSection,
        state: CollectionLogState,
    ): Inventory {
        val type = requireNotNull(ServerCacheManager.getInventory(COLLECTION_INVENTORY_ID))
        val objects = arrayOfNulls<InvObj>(maxOf(type.size, section.slots.size))
        section.slots.forEachIndexed { index, slot ->
            val quantity = state.quantities[slot.canonicalItemId] ?: 0
            if (quantity > 0) {
                objects[index] = InvObj(slot.displayItemId, quantity)
            }
        }
        return Inventory(type, objects)
    }

    private fun emptyCollectionInventory(): Inventory {
        val type = requireNotNull(ServerCacheManager.getInventory(COLLECTION_INVENTORY_ID))
        return Inventory(type, arrayOfNulls(type.size))
    }

    private fun sectionActivityCount(player: Player, section: CollectionLogSection): Int {
        if (section.linkedCounter == null) return 0
        if (section.name == DOOM_SECTION) {
            return player.attr[DOOM_KILL_COUNT] ?: 0
        }
        SPECIAL_COUNTER_VARPS[section.name]?.let { varp ->
            return player.vars[varp]
        }
        SPECIAL_COUNTER_VARP_IDS[section.name]?.let { varpId ->
            return player.vars[requireNotNull(ServerCacheManager.getVarp(varpId))]
        }
        val normalizedSection = normalize(section.name)
        return killcountNpcs
            .asSequence()
            .filter { npc -> normalizedSection == normalize(npc.name) }
            .mapNotNull { npc -> npc.paramOrNull(BaseParams.killcount_varp) }
            .distinctBy(VarpServerType::id)
            .maxOfOrNull { varp -> player.vars[varp] } ?: 0
    }

    private fun reconcileHistoricalOwnership(player: Player, state: CollectionLogState) {
        val owned =
            player.invMap.values
                .asSequence()
                .filter { inventory -> inventory.type.scope == InvScope.Perm }
                .distinctBy { inventory -> inventory.type.id }
                .flatMap { inventory -> inventory.objs.asSequence().filterNotNull() }
                .map { obj -> CollectionLogOwnedItem(obj.id, obj.count) }
                .toList()
        val plan =
            service.engine.planHistoricalUntradeableReconciliation(state, owned) { canonical ->
                ServerCacheManager.getItem(canonical)?.tradeable == false
            }
        if (plan.entries.isEmpty()) return

        val apply = java.lang.Boolean.getBoolean(HISTORICAL_RECONCILIATION_PROPERTY)
        LOGGER.log(
            System.Logger.Level.INFO,
            "Collection Log historical ownership reconciliation mode=" +
                (if (apply) "apply" else "dry-run") +
                " entries=" +
                plan.entries.joinToString { entry -> "${entry.canonicalItemId}x${entry.quantity}" },
        )
        if (apply && service.engine.applyHistoricalReconciliation(state, plan) > 0) {
            service.save(player, state)
        }
    }

    private fun readyState(player: Player): CollectionLogState? =
        when (val loaded = service.load(player)) {
            is PlayerCollectionLogLoad.Ready -> loaded.state
            PlayerCollectionLogLoad.CorruptOrUnsupported -> null
        }

    private fun owns(player: Player, item: String): Boolean =
        item in player.inv || item in player.worn || item in player.invMap.getOrPut("inv.bank")

    private val killcountNpcs: List<NpcServerType> by lazy {
        ServerCacheManager.getNpcs().values.filter {
            it.paramOrNull(BaseParams.killcount_varp) != null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace("the ", "").filter(Char::isLetterOrDigit)

    internal fun drawSectionScriptArgs(
        categoryIndex: Int,
        params: Map<Int, Int>,
        categoryStructId: Int,
        sectionIndex: Int,
    ): List<Any> =
        listOf(
            categoryIndex,
            requireNotNull(params[685]),
            requireNotNull(params[686]),
            requireNotNull(params[687]),
            requireNotNull(params[688]),
            categoryStructId,
            sectionIndex,
        )

    internal fun resolveItemsContentsComponent(): Int {
        val packed = ITEMS_CONTENTS_COMPONENT.asRSCM(RSCMType.COMPONENT)
        require(packed == ITEMS_CONTENTS_COMPONENT_PACKED) {
            "Revision-240 Collection Log items contents mapped to $packed; " +
                "expected $ITEMS_CONTENTS_COMPONENT_PACKED"
        }
        val component = ServerCacheManager.fromComponent(packed)
        require(component.internalName == ITEMS_CONTENTS_COMPONENT_NAME) {
            "Revision-240 Collection Log component $packed is ${component.internalName}; " +
                "expected $ITEMS_CONTENTS_COMPONENT_NAME"
        }
        return packed
    }

    internal fun resolveCloseComponent(): Int {
        val packed = CLOSE_COMPONENT.asRSCM(RSCMType.COMPONENT)
        require(packed == CLOSE_COMPONENT_PACKED) {
            "Revision-240 Collection Log close button mapped to $packed; " +
                "expected $CLOSE_COMPONENT_PACKED"
        }
        val component = ServerCacheManager.fromComponent(packed)
        require(component.internalName == CLOSE_COMPONENT_NAME) {
            "Revision-240 Collection Log component $packed is ${component.internalName}; " +
                "expected $CLOSE_COMPONENT_NAME"
        }
        return packed
    }

    internal fun resolveSearchResultsClickzone(): Int {
        val packed = SEARCH_RESULTS_COMPONENT.asRSCM(RSCMType.COMPONENT)
        require(packed == SEARCH_RESULTS_COMPONENT_PACKED) {
            "Revision-240 Collection Log search clickzone mapped to $packed; " +
                "expected $SEARCH_RESULTS_COMPONENT_PACKED"
        }
        val component = ServerCacheManager.fromComponent(packed)
        require(component.internalName == SEARCH_RESULTS_COMPONENT_NAME) {
            "Revision-240 Collection Log component $packed is ${component.internalName}; " +
                "expected $SEARCH_RESULTS_COMPONENT_NAME"
        }
        return packed
    }

    public companion object {
        private val LOGGER: System.Logger = System.getLogger(CollectionLogUi::class.java.name)
        public const val HISTORICAL_RECONCILIATION_PROPERTY: String =
            "openrune.collectionLog.applyHistoricalUntradeableReconciliation"

        public val LAST_SEARCH_REFRESH_TICK: AttributeKey<Int> = AttributeKey()
        public val SESSION: AttributeKey<CollectionLogUiSession> = AttributeKey()
        public val DOOM_KILL_COUNT: AttributeKey<Int> =
            AttributeKey(persistenceKey = "doom_of_mokhaiotl_kill_count")

        public const val INTERFACE: String = "interface.collection"
        public const val CLOSE_COMPONENT: String = "component.collection:close"
        public const val CLOSE_COMPONENT_NAME: String = "close"
        public const val CLOSE_COMPONENT_PACKED: Int = (621 shl 16) or 1

        public const val OVERVIEW_INTERFACE: String = "interface.collection_overview"
        public const val COLLECTION_BOOK: String = "obj.collection_log"
        public const val COLLECTOR_NPC_ID: Int = 8491
        public const val DRAW_SCRIPT: Int = 7797
        public const val CLOSE_RESET_SCRIPT: Int = 2158
        public const val COLLECTION_INVENTORY_ID: Int = 620
        public const val COLLECTION_GENERAL_VARP: String = "varp.collection_general"

        public const val GLOBAL_COUNT_VARP: String = "varp.collection_count"
        public const val GLOBAL_MAX_VARP: String = "varp.collection_count_max"
        public const val HIGHSCORES_COUNT_VARP: String = "varp.collection_count_highscores"
        public const val CURRENT_DAY_VARBIT_ID: Int = 9535
        public const val COLLECTION_LOG_NOTIFICATION_VARBIT: String =
            "varbit.option_collection_new_item"
        public const val COLLECTION_LOG_POPUP_NOTIFICATION: Int = 2
        public const val NOTIFICATION_INTERFACE: String = "interface.notification_display"
        public const val NOTIFICATION_TARGET: String =
            "component.toplevel_osrs_stretch:notifications"
        public const val COLLECTION_LOG_NOTIFICATION_COLOUR: Int = -1
        public const val COLLECTION_LOG_NOTIFICATION_DURATION_TICKS: Int = 13
        public const val RECENT_ITEM_VARP_START: Int = 4623
        public const val RECENT_DATE_VARP_START: Int = 4624
        public const val RECENT_UNLOCK_COUNT: Int = 12

        public const val DOOM_SECTION: String = "Doom of Mokhaiotl"
        public val SPECIAL_COUNTER_VARPS: Map<String, String> =
            mapOf(
                "Nex" to "varp.total_nex_kills",
                "Phantom Muspah" to "varp.total_muspah_kills",
                "Vorkath" to "varp.kc_vorkath",
                "Zulrah" to "varp.total_snakeboss_kills",
            )
        public val SPECIAL_COUNTER_VARP_IDS: Map<String, Int> =
            mapOf("Grotesque Guardians" to 1669, "The Gauntlet" to 2354)

        public val COLLECTION_BOOKS: List<String> =
            listOf(COLLECTION_BOOK) +
                CollectionLogRanks.all.map(CollectionLogRank::bookInternalName)

        public val CATEGORY_COMPONENTS: List<String> =
            listOf(
                "component.collection:boss_tab",
                "component.collection:raid_tab",
                "component.collection:clue_tab",
                "component.collection:minigame_tab",
                "component.collection:other_tab",
            )

        public val SECTION_COMPONENTS: List<String> =
            listOf(
                "component.collection:boss_background",
                "component.collection:raid_background",
                "component.collection:clue_background",
                "component.collection:minigame_background",
                "component.collection:other_background",
            )
        public val SEARCH_INPUT_COMPONENTS: List<String> =
            ('a'..'z').map { letter -> "component.collection:$letter" } +
                listOf(
                    "component.collection:space",
                    "component.collection:backspace",
                    "component.collection:tab",
                )
        public const val COMBAT_ACHIEVEMENTS_COMPONENT: String =
            "component.collection:combat_achievements"

        public const val ITEMS_COMPONENT: String = "component.collection:items"
        public const val ITEMS_CONTENTS_COMPONENT: String = "component.collection:items_contents"
        public const val ITEMS_CONTENTS_COMPONENT_NAME: String = "items_contents"
        public const val ITEMS_CONTENTS_COMPONENT_PACKED: Int = (621 shl 16) or 37
        public const val SEARCH_RESULT_SCRIPT: Int = 4100
        public const val SEARCH_RESULTS_COMPONENT: String =
            "component.collection:search_results_clickzone"
        public const val SEARCH_RESULTS_COMPONENT_NAME: String = "search_results_clickzone"
        public const val SEARCH_RESULTS_COMPONENT_PACKED: Int = (621 shl 16) or 86
        public const val MAX_ITEM_EVENT_SLOT: Int = 255

        public val CATEGORY_VARPS: Map<String, Pair<String, String>> =
            mapOf(
                "Bosses" to ("varp.collection_count_bosses" to "varp.collection_count_bosses_max"),
                "Raids" to ("varp.collection_count_raids" to "varp.collection_count_raids_max"),
                "Clues" to ("varp.collection_count_clues" to "varp.collection_count_clues_max"),
                "Minigames" to
                    ("varp.collection_count_minigames" to "varp.collection_count_minigames_max"),
                "Other" to ("varp.collection_count_other" to "varp.collection_count_other_max"),
            )
    }
}
