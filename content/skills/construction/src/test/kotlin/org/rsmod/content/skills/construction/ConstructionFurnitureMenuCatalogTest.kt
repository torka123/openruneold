package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.api.table.FurnitureRow
import org.rsmod.content.skills.construction.data.DonorConstructionData

class ConstructionFurnitureMenuCatalogTest {
    @Test
    fun `every active hotspot option resolves to its revision 240 furniture row`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val duplicateBuildables =
            DonorConstructionData.buildables.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue(duplicateBuildables.isEmpty(), "Duplicate buildable keys: $duplicateBuildables")

        val missingKeys =
            DonorConstructionData.hotspots
                .flatMap { hotspot -> hotspot.buildables.map { hotspot.name to it } }
                .filter { (_, key) -> key !in buildables }
        assertTrue(missingKeys.isEmpty(), "Hotspots reference missing buildables: $missingKeys")

        val used =
            DonorConstructionData.hotspots
                .flatMap { it.buildables }
                .distinct()
                .mapNotNull(buildables::get)
        val unresolved =
            used.filter { ConstructionFurnitureMenuCatalog.resolve(it) == null }.map { it.name }
        assertTrue(unresolved.isEmpty(), "Furniture without a revision-240 menu row: $unresolved")

        used.forEach { buildable ->
            val entry = requireNotNull(ConstructionFurnitureMenuCatalog.resolve(buildable))
            val cacheRow = FurnitureRow.getRow(entry.furnitureRowId)
            assertEquals(buildable.itemId, entry.iconItemId, buildable.name)
            assertEquals(buildable.itemId, cacheRow.modelObj.id, buildable.name)
            assertEquals(cacheRow.name.trim(), entry.displayName, buildable.name)
            assertEquals("Build: ${cacheRow.name.trim()}", entry.hoverText, buildable.name)
            assertFalse(entry.hoverText.contains("::"), buildable.name)
            assertFalse(entry.hoverText.contains('%'), buildable.name)
            assertFalse(
                entry.hoverText.contains(buildable.name),
                "Internal key leaked for ${buildable.name}",
            )
        }
    }

    @Test
    fun `representative basic special mounted dungeon and modern rows are exact`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val expected =
            mapOf(
                "CRUDE_WOODEN_CHAIR" to 5517,
                "GOLD_SINK" to 5568,
                "SPICE_RACK" to 5572,
                "ORNATE_UNDEAD_COMBAT_DUMMY" to 5643,
                "TEAK_HEAD_DISPLAY" to 5655,
                "OAK_FISHING_DISPLAY" to 5677,
                "STASH_CHART" to 5790,
                "MARBLE_PORTAL_NEXUS" to 5802,
                "CRYSTALLINE_PORTAL_NEXUS" to 5804,
                "LEAGUE_TROPHY_PEDESTAL_1" to 6008,
                "LEAGUE_ACCOMPLISHMENTS_SCROLL" to 6022,
            )
        expected.forEach { (key, rowId) ->
            val buildable = requireNotNull(buildables[key]) { key }
            val entry = requireNotNull(ConstructionFurnitureMenuCatalog.resolve(buildable)) { key }
            assertEquals(rowId, entry.furnitureRowId, key)
            assertEquals(FurnitureRow.getRow(rowId).name.trim(), entry.displayName, key)
        }
    }

    @Test
    fun `modern rooms retain cache order and exact hotspots`() {
        val rooms = DonorConstructionData.rooms.associateBy { it.name }
        assertEquals(
            listOf(
                "PORTAL_NEXUS",
                "PORTAL_NEXUS_RUG",
                "PORTAL_NEXUS_CURTAINS",
                "PORTAL_NEXUS_AMULET_1",
                "PORTAL_NEXUS_AMULET_2",
            ),
            rooms.getValue("PORTAL_NEXUS_ROOM").hotspots,
        )
        assertEquals(
            listOf(
                "LEAGUE_PEDESTAL_1",
                "LEAGUE_PEDESTAL_2",
                "LEAGUE_PEDESTAL_3",
                "LEAGUE_RUG",
                "LEAGUE_TROPHY_CASE",
                "LEAGUE_BANNER",
                "LEAGUE_OUTFIT",
                "LEAGUE_STATUE",
                "LEAGUE_SCROLL",
            ),
            rooms.getValue("LEAGUE_HALL").hotspots,
        )
        assertTrue(rooms.getValue("KITCHEN").hotspots.contains("SPICE_RACK"))
        assertEquals(29, rooms.size)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val projectRoot =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .firstOrNull { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
                    ?: error("Could not locate revision-240 gamevals")
            System.setProperty("user.dir", projectRoot.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$projectRoot${java.io.File.separator}")
                    ServerCacheManager.init(projectRoot.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
