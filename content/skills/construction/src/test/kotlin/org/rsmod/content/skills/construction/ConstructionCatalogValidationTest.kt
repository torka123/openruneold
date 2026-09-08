package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.api.table.FurnitureRow
import org.rsmod.content.skills.construction.data.DonorBuildable
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.content.skills.construction.data.DonorRequirement

class ConstructionCatalogValidationTest {
    @Test
    fun `every active entry uses valid revision 240 item object level and material data`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val used =
            DonorConstructionData.hotspots
                .flatMap { it.buildables }
                .distinct()
                .map { requireNotNull(buildables[it]) { "Missing buildable $it" } }

        used.forEach { buildable ->
            val entry =
                requireNotNull(ConstructionFurnitureMenuCatalog.resolve(buildable)) {
                    buildable.name
                }
            val row = FurnitureRow.getRow(entry.furnitureRowId)
            val cacheLevel =
                row.levelRequirement.firstOrNull { it.t0.id == CONSTRUCTION_STAT_ID }?.t1
            assertEquals(cacheLevel, buildable.level, "${buildable.name}: Construction level")
            assertEquals(
                cacheMaterials(row),
                runtimeMaterials(buildable),
                "${buildable.name}: materials",
            )
            assertTrue(buildable.experience.toDouble() >= 0.0, "${buildable.name}: negative XP")
            assertNotNull(
                ServerCacheManager.getItem(entry.iconItemId),
                "${buildable.name}: icon item",
            )
            assertTrue(
                buildable.builtLocIds.isNotEmpty(),
                "${buildable.name}: no built object variants",
            )
            assertTrue(
                buildable.builtLocIds.any { it > 0 },
                "${buildable.name}: only empty object variants",
            )
            buildable.builtLocIds
                .filter { it > 0 }
                .forEach { objectId ->
                    assertNotNull(
                        ServerCacheManager.getObject(objectId),
                        "${buildable.name}: built object $objectId",
                    )
                }
            buildable.requirements.forEach { requirement ->
                when (requirement) {
                    is DonorRequirement.ITEM -> {
                        assertTrue(
                            requirement.amount > 0,
                            "${buildable.name}: non-positive material",
                        )
                        assertNotNull(
                            ServerCacheManager.getItem(requirement.itemId),
                            "${buildable.name}: material ${requirement.itemId}",
                        )
                    }
                    is DonorRequirement.UPGRADE -> {
                        assertTrue(
                            requirement.amount > 0,
                            "${buildable.name}: non-positive upgrade",
                        )
                        assertTrue(
                            requirement.buildable in buildables,
                            "${buildable.name}: missing upgrade ${requirement.buildable}",
                        )
                    }
                }
            }
        }

        DonorConstructionData.hotspots.forEach { hotspot ->
            assertTrue(hotspot.locIds.isNotEmpty(), "${hotspot.name}: no hotspot objects")
            if (hotspot.name != "EMPTY")
                assertTrue(
                    hotspot.locIds.any { it > 0 },
                    "${hotspot.name}: no concrete hotspot object",
                )
            hotspot.locIds
                .filter { it > 0 }
                .forEach { objectId ->
                    assertNotNull(
                        ServerCacheManager.getObject(objectId),
                        "${hotspot.name}: hotspot object $objectId",
                    )
                }
            assertEquals(
                hotspot.buildables.size,
                hotspot.buildables.distinct().size,
                "${hotspot.name}: duplicate option",
            )
            val payloads =
                hotspot.buildables.map { key ->
                    val buildable = requireNotNull(buildables[key])
                    requireNotNull(ConstructionFurnitureMenuCatalog.resolve(buildable))
                }
            assertEquals(
                payloads.size,
                payloads.map { it.furnitureRowId }.distinct().size,
                "${hotspot.name}: duplicate selection payload",
            )
        }
    }

    @Test
    fun `representative categories retain exact revision 240 rows`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val expected =
            mapOf(
                "CRUDE_WOODEN_CHAIR" to 5517,
                "OAK_LARDER" to 5563,
                "GOLD_SINK" to 5568,
                "SPICE_RACK" to 5572,
                "ORNATE_UNDEAD_COMBAT_DUMMY" to 5643,
                "OAK_STAIRCASE_UP" to 5644,
                "TEAK_HEAD_DISPLAY" to 5655,
                "OAK_FISHING_DISPLAY" to 5677,
                "GNOME_CHILD_ICON" to 5725,
                "MARBLE_LECTERN" to 5776,
                "STASH_CHART" to 5790,
                "MARBLE_PORTAL_NEXUS" to 5802,
                "CRYSTALLINE_PORTAL_NEXUS" to 5804,
                "MARBLE_DOOR" to 5862,
                "TEAK_CHEST" to 5865,
                "DEMON" to 5868,
                "QUEST_LIST" to 6007,
                "PUMPKIN_GARDEN_1" to 6023,
                "BEEHIVE_STYLE_2_GARDEN_1" to 6025,
                "GREENMAN_CARVING" to 6027,
                "LEAGUE_TROPHY_PEDESTAL_1" to 6008,
                "LEAGUE_ACCOMPLISHMENTS_SCROLL" to 6022,
                "ANNIHILATION_EXIT_PORTAL" to 9654,
            )
        expected.forEach { (key, rowId) ->
            val entry =
                requireNotNull(
                    ConstructionFurnitureMenuCatalog.resolve(requireNotNull(buildables[key]))
                )
            assertEquals(rowId, entry.furnitureRowId, key)
            assertEquals("Build: ${entry.displayName}", entry.hoverText, key)
        }
    }

    @Test
    fun `large then small menu payloads have no stale slot bindings`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val ordered = DonorConstructionData.hotspots.sortedBy { it.buildables.size }
        val small = ordered.first { it.buildables.isNotEmpty() }
        val large = ordered.last()
        assertTrue(large.buildables.size > small.buildables.size)

        val largePayload =
            large.buildables.map {
                requireNotNull(
                    ConstructionFurnitureMenuCatalog.resolve(requireNotNull(buildables[it]))
                )
            }
        val smallPayload =
            small.buildables.map {
                requireNotNull(
                    ConstructionFurnitureMenuCatalog.resolve(requireNotNull(buildables[it]))
                )
            }
        assertEquals(large.buildables, largePayload.map { it.buildable.name })
        assertEquals(small.buildables, smallPayload.map { it.buildable.name })
        assertEquals(small.buildables.size, smallPayload.size)
        assertFalse(
            smallPayload.any { choice ->
                choice.buildable.name in large.buildables.drop(smallPayload.size)
            }
        )
    }

    @Test
    fun `non-menu construction definitions remain explicitly classified`() {
        val used = DonorConstructionData.hotspots.flatMap { it.buildables }.toSet()
        val unused =
            DonorConstructionData.buildables.map { it.name }.filterNot(used::contains).toSet()
        assertEquals(
            setOf(
                "CRAWLING_HAND",
                "COCKATRICE_HEAD",
                "BASILISK_HEAD",
                "KURASK_HEAD",
                "ABYSSAL_DEMON_HEAD",
                "KBD_HEADS",
                "KALPHITE_QUEEN_HEAD",
                "VORKATH_HEAD",
                "MOUNTED_BASS",
                "MOUNTED_SWORDFISH",
                "MOUNTED_SHARK",
                "OCCULT_ALTAR",
                "OCCULT_ALTAR_FROM_ANCIENT",
                "OCCULT_ALTAR_FROM_LUNAR",
                "OCCULT_ALTAR_FROM_DARK",
                "SMALL_STORAGE_UNIT",
                "MEDIUM_STORAGE_UNIT",
                "LARGE_STORAGE_UNIT",
            ),
            unused,
        )
    }

    private fun cacheMaterials(row: FurnitureRow): Map<Int, Int> =
        row.materialCost.groupBy { it.t0.id }.mapValues { (_, entries) -> entries.sumOf { it.t1 } }

    private fun runtimeMaterials(buildable: DonorBuildable): Map<Int, Int> =
        buildable.requirements
            .filterIsInstance<DonorRequirement.ITEM>()
            .groupBy {
                if (it.itemId == LEGACY_GENERIC_NAILS) REVISION_240_GENERIC_NAILS else it.itemId
            }
            .mapValues { (_, entries) -> entries.sumOf { it.amount } }

    companion object {
        private const val CONSTRUCTION_STAT_ID = 22
        private const val LEGACY_GENERIC_NAILS = 4819
        private const val REVISION_240_GENERIC_NAILS = 32923

        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$root${java.io.File.separator}")
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
