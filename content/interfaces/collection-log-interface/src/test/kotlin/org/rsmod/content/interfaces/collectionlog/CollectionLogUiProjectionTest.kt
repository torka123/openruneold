package org.rsmod.content.interfaces.collectionlog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogState

class CollectionLogUiProjectionTest {
    private val catalog = CollectionLogCatalogRepository()
    private val projection = CollectionLogUiProjection(catalog)

    @Test
    fun `global and category progress count each canonical entry once`() {
        val duplicated = catalog.slots.groupBy { it.canonicalItemId }.values.first { it.size > 1 }
        val state =
            CollectionLogState(quantities = mutableMapOf(duplicated.first().canonicalItemId to 1))
        val global = projection.globalProgress(state)
        assertEquals(1, global.first)
        assertEquals(catalog.catalog.uniqueCanonicalEntries, global.second)
        assertEquals(global.first, projection.categoryProgress(state).sumOf { it.obtained })
        assertEquals(
            listOf(339, 67, 611, 261, 434),
            projection.categoryProgress(state).map { it.total },
        )
        assertEquals(1712, projection.categoryProgress(state).sumOf { it.total })
    }

    @Test
    fun `section view preserves slot order and quantities`() {
        val first = catalog.category(0)!!.sections.first().slots.first()
        val state = CollectionLogState(quantities = mutableMapOf(first.canonicalItemId to 7))
        val view = assertNotNull(projection.section(state, 0, 0))
        assertEquals(first.displayItemId, view.section.slots.first().displayItemId)
        assertEquals(7, view.quantities.first())
    }

    @Test
    fun `native search result indices resolve across all categories`() {
        assertEquals(CollectionLogSectionLocation(0, 0), projection.searchResult(0))
        val firstRaidGlobalIndex = catalog.categories.first().sections.size
        assertEquals(
            CollectionLogSectionLocation(1, 0),
            projection.searchResult(firstRaidGlobalIndex),
        )
        assertNotNull(projection.searchResult(catalog.sections.lastIndex))
        assertNull(projection.searchResult(-1))
        assertNull(projection.searchResult(catalog.sections.size))
    }

    @Test
    fun `implemented linked activities use exact persisted counters`() {
        assertEquals(
            mapOf(
                "Nex" to "varp.total_nex_kills",
                "Phantom Muspah" to "varp.total_muspah_kills",
                "Vorkath" to "varp.total_vorkath_kills",
                "Zulrah" to "varp.total_snakeboss_kills",
            ),
            CollectionLogUi.SPECIAL_COUNTER_VARPS,
        )
        assertEquals(
            mapOf("Grotesque Guardians" to 1669, "The Gauntlet" to 2354),
            CollectionLogUi.SPECIAL_COUNTER_VARP_IDS,
        )
        assertEquals("doom_of_mokhaiotl_kill_count", CollectionLogUi.DOOM_KILL_COUNT.persistenceKey)
    }
}
