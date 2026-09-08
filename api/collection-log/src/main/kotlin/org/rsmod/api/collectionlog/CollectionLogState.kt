package org.rsmod.api.collectionlog

public data class CollectionLogState(
    public val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    public val quantities: MutableMap<Int, Int> = mutableMapOf(),
    public var legacyMigrationComplete: Boolean = false,
    public val recentAwardEvents: MutableList<String> = mutableListOf(),
    public val recentUnlocks: MutableList<CollectionLogRecentUnlock> = mutableListOf(),
    public val claimedRankRewards: MutableSet<String> = mutableSetOf(),
) {
    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 3
        public const val MAX_RECENT_AWARD_EVENTS: Int = 256
        public const val MAX_RECENT_UNLOCKS: Int = 12
    }
}

public data class CollectionLogRecentUnlock(public val itemId: Int, public val day: Int)

public fun currentCollectionLogDay(epochMillis: Long = System.currentTimeMillis()): Int =
    Math.floorDiv(epochMillis, 86_400_000L).toInt() and 0x3fff

public object CollectionLogStateCodec {
    private const val SCHEMA = "schemaVersion"
    private const val QUANTITIES = "quantities"
    private const val MIGRATED = "legacyMigrationComplete"
    private const val EVENTS = "recentAwardEvents"
    private const val UNLOCKS = "recentUnlocks"
    private const val CLAIMED_RANK_REWARDS = "claimedRankRewards"
    private const val ITEM_ID = "itemId"
    private const val DAY = "day"

    public fun encode(state: CollectionLogState): MutableMap<String, Any> =
        mutableMapOf(
            SCHEMA to CollectionLogState.CURRENT_SCHEMA_VERSION,
            QUANTITIES to
                state.quantities
                    .filterValues { it > 0 }
                    .toSortedMap()
                    .mapKeys { (itemId, _) -> itemId.toString() }
                    .toMutableMap(),
            MIGRATED to state.legacyMigrationComplete,
            EVENTS to
                state.recentAwardEvents
                    .takeLast(CollectionLogState.MAX_RECENT_AWARD_EVENTS)
                    .toMutableList(),
            UNLOCKS to
                state.recentUnlocks
                    .takeLast(CollectionLogState.MAX_RECENT_UNLOCKS)
                    .map { mutableMapOf(ITEM_ID to it.itemId, DAY to it.day) }
                    .toMutableList(),
            CLAIMED_RANK_REWARDS to state.claimedRankRewards.filter(String::isNotBlank).sorted(),
        )

    public fun decode(raw: Any?): CollectionLogState? {
        val map = raw as? Map<*, *> ?: return null
        val schema = (map[SCHEMA] as? Number)?.toInt() ?: return null
        if (schema !in 1..CollectionLogState.CURRENT_SCHEMA_VERSION) return null
        val quantityMap = map[QUANTITIES] as? Map<*, *> ?: emptyMap<Any, Any>()
        val quantities =
            quantityMap.entries
                .mapNotNull { (key, value) ->
                    val itemId = key?.toString()?.toIntOrNull() ?: return@mapNotNull null
                    val quantity = (value as? Number)?.toInt() ?: return@mapNotNull null
                    if (itemId <= 0 || quantity <= 0) null else itemId to quantity
                }
                .toMap(mutableMapOf())
        val events =
            (map[EVENTS] as? List<*>)
                .orEmpty()
                .mapNotNull { it as? String }
                .filter(String::isNotBlank)
                .takeLast(CollectionLogState.MAX_RECENT_AWARD_EVENTS)
                .toMutableList()
        val unlocks =
            (map[UNLOCKS] as? List<*>)
                .orEmpty()
                .mapNotNull { rawUnlock ->
                    val unlock = rawUnlock as? Map<*, *> ?: return@mapNotNull null
                    val itemId = (unlock[ITEM_ID] as? Number)?.toInt() ?: return@mapNotNull null
                    val day = (unlock[DAY] as? Number)?.toInt() ?: return@mapNotNull null
                    if (itemId <= 0 || day !in 0..0x3fff) null
                    else CollectionLogRecentUnlock(itemId, day)
                }
                .takeLast(CollectionLogState.MAX_RECENT_UNLOCKS)
                .toMutableList()
        val claimedRankRewards =
            (map[CLAIMED_RANK_REWARDS] as? List<*>)
                .orEmpty()
                .mapNotNull { it as? String }
                .filter(String::isNotBlank)
                .toMutableSet()
        return CollectionLogState(
            schemaVersion = CollectionLogState.CURRENT_SCHEMA_VERSION,
            quantities = quantities,
            legacyMigrationComplete = map[MIGRATED] as? Boolean ?: false,
            recentAwardEvents = events,
            recentUnlocks = unlocks,
            claimedRankRewards = claimedRankRewards,
        )
    }

    public fun legacyQuantities(raw: Any?): Map<Int, Int> {
        val outer = raw as? Map<*, *> ?: return emptyMap()
        val map = (outer["collected"] as? Map<*, *>) ?: outer
        return map.entries
            .mapNotNull { (key, value) ->
                val itemId = key?.toString()?.toIntOrNull() ?: return@mapNotNull null
                val quantity = (value as? Number)?.toInt() ?: return@mapNotNull null
                if (itemId <= 0 || quantity <= 0) null else itemId to quantity
            }
            .toMap()
    }
}

public enum class CollectionLogRecordStatus {
    Recorded,
    DuplicateEvent,
    InvalidAmount,
    UntrackedItem,
    AtCap,
}

public data class CollectionLogRecordResult(
    public val status: CollectionLogRecordStatus,
    public val canonicalItemId: Int? = null,
    public val previousQuantity: Int = 0,
    public val quantity: Int = 0,
    public val newlyObtained: Boolean = false,
    public val completedSlots: Int = 0,
)

public data class CollectionLogOwnedItem(public val itemId: Int, public val quantity: Int)

public data class CollectionLogReconciliationEntry(
    public val canonicalItemId: Int,
    public val quantity: Int,
)

public data class CollectionLogReconciliationPlan(
    public val entries: List<CollectionLogReconciliationEntry>
)

public class CollectionLogStateEngine(public val catalog: CollectionLogCatalogRepository) {
    public fun record(
        state: CollectionLogState,
        itemId: Int,
        amount: Int,
        eventId: String,
        collectionDay: Int = currentCollectionLogDay(),
    ): CollectionLogRecordResult {
        if (amount <= 0) return CollectionLogRecordResult(CollectionLogRecordStatus.InvalidAmount)
        val canonical =
            catalog.canonicalItemId(itemId)
                ?: return CollectionLogRecordResult(CollectionLogRecordStatus.UntrackedItem)
        val token = "$eventId:$canonical"
        if (token in state.recentAwardEvents) {
            return CollectionLogRecordResult(
                status = CollectionLogRecordStatus.DuplicateEvent,
                canonicalItemId = canonical,
                previousQuantity = state.quantities[canonical] ?: 0,
                quantity = state.quantities[canonical] ?: 0,
                completedSlots = completedSlots(state),
            )
        }
        rememberEvent(state, token)
        val previous = state.quantities[canonical] ?: 0
        val cap = requireNotNull(catalog.quantityCap(canonical))
        val quantity = (previous.toLong() + amount.toLong()).coerceAtMost(cap.toLong()).toInt()
        if (quantity == previous) {
            return CollectionLogRecordResult(
                status = CollectionLogRecordStatus.AtCap,
                canonicalItemId = canonical,
                previousQuantity = previous,
                quantity = quantity,
                completedSlots = completedSlots(state),
            )
        }
        state.quantities[canonical] = quantity
        if (previous == 0) rememberUnlock(state, canonical, collectionDay)
        return CollectionLogRecordResult(
            status = CollectionLogRecordStatus.Recorded,
            canonicalItemId = canonical,
            previousQuantity = previous,
            quantity = quantity,
            newlyObtained = previous == 0,
            completedSlots = completedSlots(state),
        )
    }

    public fun migrateLegacy(
        state: CollectionLogState,
        legacyMaps: Iterable<Map<Int, Int>>,
    ): Boolean {
        if (state.legacyMigrationComplete) return false
        legacyMaps.forEach { legacy ->
            legacy.forEach { (itemId, amount) ->
                if (amount <= 0) return@forEach
                val canonical = catalog.canonicalItemId(itemId) ?: return@forEach
                val cap = requireNotNull(catalog.quantityCap(canonical))
                val existing = state.quantities[canonical] ?: 0
                state.quantities[canonical] = maxOf(existing, amount.coerceAtMost(cap))
            }
        }
        state.legacyMigrationComplete = true
        return true
    }

    public fun planHistoricalUntradeableReconciliation(
        state: CollectionLogState,
        ownedItems: Iterable<CollectionLogOwnedItem>,
        isUntradeable: (canonicalItemId: Int) -> Boolean,
    ): CollectionLogReconciliationPlan {
        val totals = linkedMapOf<Int, Long>()
        ownedItems.forEach { owned ->
            if (owned.quantity <= 0) return@forEach
            val canonical = catalog.canonicalItemId(owned.itemId) ?: return@forEach
            if (!isUntradeable(canonical)) return@forEach
            totals[canonical] = (totals[canonical] ?: 0L) + owned.quantity.toLong()
        }
        val entries =
            totals.entries
                .asSequence()
                .filter { (canonical, _) -> (state.quantities[canonical] ?: 0) == 0 }
                .map { (canonical, total) ->
                    val cap = requireNotNull(catalog.quantityCap(canonical))
                    CollectionLogReconciliationEntry(
                        canonical,
                        total.coerceAtMost(cap.toLong()).toInt(),
                    )
                }
                .filter { it.quantity > 0 }
                .sortedBy(CollectionLogReconciliationEntry::canonicalItemId)
                .toList()
        return CollectionLogReconciliationPlan(entries)
    }

    public fun applyHistoricalReconciliation(
        state: CollectionLogState,
        plan: CollectionLogReconciliationPlan,
    ): Int {
        var applied = 0
        plan.entries.forEach { entry ->
            if ((state.quantities[entry.canonicalItemId] ?: 0) > 0) return@forEach
            val cap = catalog.quantityCap(entry.canonicalItemId) ?: return@forEach
            val quantity = entry.quantity.coerceIn(1, cap)
            state.quantities[entry.canonicalItemId] = quantity
            applied++
        }
        return applied
    }

    public fun hasClaimedRankReward(state: CollectionLogState, rank: CollectionLogRank): Boolean =
        rank.key in state.claimedRankRewards

    public fun markRankRewardClaimed(state: CollectionLogState, rank: CollectionLogRank): Boolean =
        state.claimedRankRewards.add(rank.key)

    public fun quantity(state: CollectionLogState, itemId: Int): Int {
        val canonical = catalog.canonicalItemId(itemId) ?: return 0
        return state.quantities[canonical] ?: 0
    }

    public fun completedSlots(state: CollectionLogState): Int =
        catalog.canonicalItemIds.count { canonical -> (state.quantities[canonical] ?: 0) > 0 }

    public fun completedSlots(state: CollectionLogState, section: CollectionLogSection): Int =
        section.slots.count { slot -> (state.quantities[slot.canonicalItemId] ?: 0) > 0 }

    public fun completedSlots(state: CollectionLogState, category: CollectionLogCategory): Int =
        catalog.canonicalItemIds(category).count { canonical ->
            (state.quantities[canonical] ?: 0) > 0
        }

    public fun isComplete(state: CollectionLogState, section: CollectionLogSection): Boolean =
        section.slots.isNotEmpty() && completedSlots(state, section) == section.slots.size

    private fun rememberEvent(state: CollectionLogState, token: String) {
        state.recentAwardEvents += token
        while (state.recentAwardEvents.size > CollectionLogState.MAX_RECENT_AWARD_EVENTS) {
            state.recentAwardEvents.removeAt(0)
        }
    }

    private fun rememberUnlock(state: CollectionLogState, itemId: Int, day: Int) {
        state.recentUnlocks += CollectionLogRecentUnlock(itemId, day.coerceIn(0, 0x3fff))
        while (state.recentUnlocks.size > CollectionLogState.MAX_RECENT_UNLOCKS) {
            state.recentUnlocks.removeAt(0)
        }
    }
}
