package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CorruptedGauntletInteractionCacheTest {
    @Test
    fun `revision 240 lobby interactions match the native transforms and map placement`() {
        Revision240GauntletCache.initialize()

        val chestBase = assertNotNull(ServerCacheManager.getObject(37_341))
        assertEquals(CorruptedGauntletConfig.VarBit.PENDING, chestBase.multiVarBit)
        assertEquals(listOf(36_088, 36_087, -1), chestBase.transforms?.toList())

        val closedChest =
            assertNotNull(ServerCacheManager.getObject(CorruptedGauntletConfig.Loc.REWARD_CHEST_CLOSED))
        assertEquals("loc.gauntlet_chest_closed", closedChest.internalName)
        assertEquals("Open", closedChest.actions.getOpOrNull(0))
        val openChest =
            assertNotNull(ServerCacheManager.getObject(CorruptedGauntletConfig.Loc.REWARD_CHEST_OPEN))
        assertEquals("loc.gauntlet_chest_open", openChest.internalName)
        assertNull(openChest.actions.getOpOrNull(0))

        val entranceBase = assertNotNull(ServerCacheManager.getObject(37_340))
        assertEquals(CorruptedGauntletConfig.NORMAL_COMPLETION_VARP, entranceBase.multiVarp)
        assertEquals(listOf(36_083, 36_084), entranceBase.transforms?.toList())
        val enabledEntrance =
            assertNotNull(ServerCacheManager.getObject(CorruptedGauntletConfig.Loc.ENABLED_ENTRANCE))
        assertEquals("Enter", enabledEntrance.actions.getOpOrNull(0))
        assertEquals("Enter-corrupted", enabledEntrance.actions.getOpOrNull(1))

        val instructor = assertNotNull(ServerCacheManager.getNpc(9_020))
        assertEquals("npc.gauntlet_instructor", instructor.internalName)
        assertEquals("Talk-to", instructor.actions.getOpOrNull(0))

        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/cache/SERVER/main_file_cache.dat2")) }
        val cache = Cache.load(root.resolve(".data/cache/SERVER"))
        try {
            val placements =
                MapLocListDecoder.decode(
                        InlineByteBuf(
                            assertNotNull(cache.data(MAPS, LOBBY_MAP_SQUARE, 1), "CG lobby loc archive")
                        )
                    )
                    .spawns
                    .longIterator()
            val objects = mutableListOf<MapLocDefinition>()
            while (placements.hasNext()) {
                val loc = MapLocDefinition(placements.nextLong())
                if (loc.id == 37_340 || loc.id == 37_341) objects += loc
            }

            assertEquals(
                listOf(
                    listOf(37_340, 24, 48, 1, 10, 0),
                    listOf(37_341, 19, 43, 1, 10, 3),
                ),
                objects.sortedBy { it.id }.map {
                    listOf(it.id, it.localX, it.localZ, it.level, it.shape, it.angle)
                },
            )
        } finally {
            cache.close()
        }
    }

    private companion object {
        const val LOBBY_MAP_SQUARE = (47 shl 8) or 95
    }
}
