package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DukeR240CacheAuditTest {
    @Test
    fun `Duke assets and upper scene planes exist in revision 240`() {
        ServerCacheManager.init(240)
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val terrain = MapTileDecoder.decode(InlineByteBuf(requireNotNull(cache.data(MAPS, 12132, 0))))
            val linked = buildList {
                for (x in 0..63) for (z in 0..63) {
                    if (terrain[x, z, 1].toInt() and MapTileSimpleDefinition.LINK_BELOW != 0) add(x to z)
                }
            }
            println("DUKE plane-1 links: $linked")
            assertTrue(linked.isNotEmpty(), "Duke's upper scene must be copied")
            assertTrue(22 to 40 in linked, "West corridor bridge link")
            assertTrue(40 to 40 in linked, "East corridor bridge link")
            assertFalse(31 to 33 in linked, "Entry remains on the ground scene")
            for (id in listOf(12167, 12191, 12192, 12195, 12196, 12198, 12199, 12200, 12201, 11708)) {
                val type = requireNotNull(ServerCacheManager.getNpc(id))
                println("DUKE npc=$id size=${type.size} hp=${type.hitpoints} atk=${type.attack} str=${type.strength} def=${type.defence} mage=${type.magic} actions=${type.actions}")
                if (id in listOf(12167, 12191, 12195)) assertEquals(7, type.size)
            }
            for (id in 10174..10189) assertNotNull(ServerCacheManager.getAnim(id), "animation $id")
            for (id in listOf(364, 626, 832, 2282, 9401)) assertNotNull(ServerCacheManager.getAnim(id), "preparation animation $id")
            val death = requireNotNull(ServerCacheManager.getAnim(10181))
            assertEquals(4, death.tickDuration, "Death/drop delay must match capture 1827")
            for (id in listOf(1447, 538, 1354, 379, 382) + (2431..2443)) {
                val spot = requireNotNull(ServerCacheManager.getSpotAnimation(id))
                assertTrue(cache.data(7, spot.modelId)?.isNotEmpty() == true, "spot model $id")
            }
            for (id in listOf(233, 1267, 28341, 28342, 28345, 28346, 28349, 28351)) {
                val item = requireNotNull(ServerCacheManager.getItem(id))
                println("DUKE item=$id ${item.name}")
            }
            for (id in 47522..47568) {
                val obj = ServerCacheManager.getObject(id) ?: continue
                println("DUKE loc=$id ${obj.name} ${obj.actions}")
            }
            assertNotNull(ServerCacheManager.getObject(30032), "Bile pool object")
            assertNotNull(ServerCacheManager.getItem(1506), "Gas mask")
            for (id in listOf(7181, 2707, 7197, 3052, 6545)) assertTrue(cache.data(4, id)?.isNotEmpty() == true, "sound $id")
        } finally { cache.close() }
    }
}
