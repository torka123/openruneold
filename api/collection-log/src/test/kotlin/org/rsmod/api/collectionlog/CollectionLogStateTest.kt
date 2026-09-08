package org.rsmod.api.collectionlog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionLogStateTest {
    private val catalog = CollectionLogCatalogRepository()
    private val engine = CollectionLogStateEngine(catalog)

    @Test
    fun `catalog totals ordering and duplicate slots match revision 240`() {
        assertEquals(240, catalog.catalog.metadata.cacheRevision)
        assertEquals(124, catalog.catalog.totalSections)
        assertEquals(1921, catalog.catalog.totalSlots)
        assertEquals(1712, catalog.catalog.uniqueCanonicalEntries)
        assertEquals(
            listOf("Bosses", "Raids", "Clues", "Minigames", "Other"),
            catalog.categories.map { it.name },
        )

        val prospectorHelmet = catalog.slotsFor(12013)
        assertEquals(2, prospectorHelmet.size)
        assertEquals(setOf(12013, 29472), prospectorHelmet.map { it.displayItemId }.toSet())
    }

    @Test
    fun `recording is monotonic capped idempotent and ignores untracked items`() {
        val state = CollectionLogState(legacyMigrationComplete = true)
        val first = engine.record(state, 12013, 1, "reward:1", collectionDay = 4321)
        assertEquals(CollectionLogRecordStatus.Recorded, first.status)
        assertTrue(first.newlyObtained)
        assertEquals(1, first.completedSlots)
        assertEquals(listOf(CollectionLogRecentUnlock(12013, 4321)), state.recentUnlocks)

        val duplicate = engine.record(state, 12013, 1, "reward:1", collectionDay = 4322)
        assertEquals(CollectionLogRecordStatus.DuplicateEvent, duplicate.status)
        assertEquals(1, engine.quantity(state, 12013))
        assertEquals(1, state.recentUnlocks.size)

        engine.record(state, 12013, Int.MAX_VALUE, "reward:2")
        val cap = assertNotNull(catalog.quantityCap(12013))
        assertEquals(cap, engine.quantity(state, 12013))
        assertEquals(
            CollectionLogRecordStatus.UntrackedItem,
            engine.record(state, 1, 1, "reward:3").status,
        )
    }

    @Test
    fun `recent unlocks contain only first acquisitions and retain the newest twelve`() {
        val state = CollectionLogState(legacyMigrationComplete = true)
        val uniqueIds = catalog.slots.map { it.canonicalItemId }.distinct().take(13)
        uniqueIds.forEachIndexed { index, itemId ->
            engine.record(state, itemId, 1, "recent:$index", collectionDay = index)
        }
        assertEquals(12, state.recentUnlocks.size)
        assertEquals(uniqueIds.drop(1), state.recentUnlocks.map { it.itemId })
        assertEquals((1..12).toList(), state.recentUnlocks.map { it.day })
    }

    @Test
    fun `legacy migration clamps merges and is idempotent`() {
        val state = CollectionLogState()
        assertTrue(
            engine.migrateLegacy(state, listOf(mapOf(12013 to Int.MAX_VALUE, 29472 to 3, 1 to 99)))
        )
        assertEquals(catalog.quantityCap(12013), engine.quantity(state, 12013))
        assertTrue(state.legacyMigrationComplete)

        val before = state.quantities.toMap()
        assertFalse(engine.migrateLegacy(state, listOf(mapOf(12013 to 1))))
        assertEquals(before, state.quantities)
    }

    @Test
    fun `state codec round trips and upgrades schema one safely`() {
        val state =
            CollectionLogState(
                quantities = mutableMapOf(12013 to 4),
                legacyMigrationComplete = true,
                recentAwardEvents = mutableListOf("drop:1:12013"),
                recentUnlocks = mutableListOf(CollectionLogRecentUnlock(12013, 4000)),
                claimedRankRewards = mutableSetOf("bronze"),
            )
        assertEquals(state, CollectionLogStateCodec.decode(CollectionLogStateCodec.encode(state)))

        val schemaOne =
            mutableMapOf<String, Any>(
                "schemaVersion" to 1,
                "quantities" to mutableMapOf("12013" to 2),
                "legacyMigrationComplete" to true,
                "recentAwardEvents" to mutableListOf("old:event"),
            )
        val upgraded = assertNotNull(CollectionLogStateCodec.decode(schemaOne))
        assertEquals(CollectionLogState.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion)
        assertEquals(2, upgraded.quantities[12013])
        assertTrue(upgraded.recentUnlocks.isEmpty())
        assertNull(CollectionLogStateCodec.decode(mapOf("schemaVersion" to 99)))
        assertEquals(
            mapOf(12013 to 7),
            CollectionLogStateCodec.legacyQuantities(mapOf("collected" to mapOf("12013" to 7))),
        )
    }

    @Test
    fun `historical reconciliation is dry run safe canonical and idempotent`() {
        val state = CollectionLogState(legacyMigrationComplete = true)
        val plan =
            engine.planHistoricalUntradeableReconciliation(
                state,
                listOf(
                    CollectionLogOwnedItem(12013, 1),
                    CollectionLogOwnedItem(29472, 2),
                    CollectionLogOwnedItem(1, 99),
                ),
            ) {
                true
            }

        assertEquals(listOf(CollectionLogReconciliationEntry(12013, 3)), plan.entries)
        assertTrue(state.quantities.isEmpty(), "planning must never mutate historical state")
        assertEquals(1, engine.applyHistoricalReconciliation(state, plan))
        assertEquals(3, engine.quantity(state, 12013))
        assertEquals(0, engine.applyHistoricalReconciliation(state, plan))
        assertTrue(
            engine
                .planHistoricalUntradeableReconciliation(
                    state,
                    listOf(CollectionLogOwnedItem(12013, 99)),
                ) {
                    true
                }
                .entries
                .isEmpty()
        )
    }

    @Test
    fun `rank reward claims persist and cannot be claimed twice`() {
        val state = CollectionLogState(legacyMigrationComplete = true)
        val bronze = CollectionLogRanks.all.first()
        assertFalse(engine.hasClaimedRankReward(state, bronze))
        assertTrue(engine.markRankRewardClaimed(state, bronze))
        assertFalse(engine.markRankRewardClaimed(state, bronze))
        val restored =
            assertNotNull(CollectionLogStateCodec.decode(CollectionLogStateCodec.encode(state)))
        assertTrue(engine.hasClaimedRankReward(restored, bronze))
    }

    @Test
    fun `rank thresholds and revision 240 rewards are stable`() {
        assertEquals(9, CollectionLogRanks.all.size)
        assertEquals(
            1525,
            CollectionLogRanks.gildedThreshold(catalog.catalog.uniqueCanonicalEntries),
        )
        assertNull(CollectionLogRanks.highestAchieved(99, catalog.catalog.uniqueCanonicalEntries))
        assertEquals(
            "Bronze",
            CollectionLogRanks.highestAchieved(100, catalog.catalog.uniqueCanonicalEntries)
                ?.displayName,
        )
        assertEquals(
            "Dragon",
            CollectionLogRanks.highestAchieved(1524, catalog.catalog.uniqueCanonicalEntries)
                ?.displayName,
        )
        assertEquals(
            "Gilded",
            CollectionLogRanks.highestAchieved(1525, catalog.catalog.uniqueCanonicalEntries)
                ?.displayName,
        )
        assertEquals(30_613, CollectionLogRanks.all.last().staffItemId)
    }

    @Test
    fun `current collection day matches the fourteen bit client counter`() {
        assertEquals(0, currentCollectionLogDay(0L))
        assertEquals(1, currentCollectionLogDay(86_400_000L))
        assertEquals(0, currentCollectionLogDay(16_384L * 86_400_000L))
    }

    @Test
    fun `every authoritative award kind has a distinct idempotency namespace`() {
        assertEquals(
            setOf(
                "NpcDrop",
                "BossChest",
                "RaidReward",
                "ClueReward",
                "MinigameReward",
                "SkillingReward",
                "PetGrant",
                "SpecialContainer",
                "AdminTest",
            ),
            CollectionLogAwardKind.entries.map(CollectionLogAwardKind::name).toSet(),
        )
        val keys =
            CollectionLogAwardKind.entries.map { kind ->
                CollectionLogAwardSource(kind, "representative-source").stableEventId("award-1")
            }
        assertEquals(CollectionLogAwardKind.entries.size, keys.distinct().size)
        assertFailsWith<IllegalArgumentException> {
            CollectionLogAwardSource(CollectionLogAwardKind.NpcDrop, "npc.test").stableEventId("")
        }
    }
}
