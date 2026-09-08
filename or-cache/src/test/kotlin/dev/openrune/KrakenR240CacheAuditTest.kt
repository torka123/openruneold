package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KrakenR240CacheAuditTest {
    @Test
    fun `revision 240 preserves the authentic Kraken forms`() {
        ServerCacheManager.init(REVISION)

        val bossPool = requireNotNull(ServerCacheManager.getNpc(496))
        assertEquals("Whirlpool", bossPool.name)
        assertEquals(5, bossPool.size)
        assertEquals("Disturb", bossPool.actions.getOpOrNull(1))

        val boss = requireNotNull(ServerCacheManager.getNpc(494))
        assertEquals("Kraken", boss.name)
        assertEquals(4, boss.size)
        assertEquals(255, boss.hitpoints)
        assertEquals("Attack", boss.actions.getOpOrNull(1))

        val tentaclePool = requireNotNull(ServerCacheManager.getNpc(5534))
        assertEquals("Whirlpool", tentaclePool.name)
        assertEquals(2, tentaclePool.size)
        assertEquals("Disturb", tentaclePool.actions.getOpOrNull(1))

        val tentacle = requireNotNull(ServerCacheManager.getNpc(5535))
        assertEquals("Enormous Tentacle", tentacle.name)
        assertEquals(2, tentacle.size)
        assertEquals(120, tentacle.hitpoints)
        assertEquals("Attack", tentacle.actions.getOpOrNull(1))
    }

    @Test
    fun `revision 240 contains the Kraken room crevices and public approach`() =
        withCache { provider, entries ->
            val publicCrevice = requireNotNull(provider.objects[537])
            assertEquals("Crevice", publicCrevice.name)
            assertEquals("Enter", publicCrevice.actions.getOpOrNull(0))
            assertEquals("Private", publicCrevice.actions.getOpOrNull(1))
            assertEquals("Peek", publicCrevice.actions.getOpOrNull(2))

            val insideCrevice = requireNotNull(provider.objects[538])
            assertEquals("Crevice", insideCrevice.name)
            assertEquals("Use", insideCrevice.actions.getOpOrNull(0))

            assertTrue(
                entries.any {
                    it.id == 537 &&
                        it.localX == 40 &&
                        it.localZ == 33 &&
                        it.shape == 0 &&
                        it.angle == 3
                }
            )
            assertTrue(
                entries.any {
                    it.id == 538 &&
                        it.localX == 40 &&
                        it.localZ == 37 &&
                        it.shape == 0 &&
                        it.angle == 1
                }
            )

            val publicApproachX = (SOURCE_MAP_X shl 6) + 40
            val publicApproachZ = (SOURCE_MAP_Z shl 6) + 32
            assertEquals(2280, publicApproachX)
            assertEquals(10016, publicApproachZ)
        }

    @Test
    fun `server map dump preserves all five dormant Kraken spawns`() {
        val dump =
            Files.readString(Path.of(".data/raw-cache/map/npcs/unassigned.toml"))
                .replace("\r\n", "\n")
        val expected =
            listOf(
                "npc = \"npc.slayer_kraken_boss_whirlpool\"\ncoords = \"0_35_156_38_50\"",
                "npc = \"npc.slayer_kraken_boss_tentacle_whirlpool\"\ncoords = \"0_35_156_35_50\"",
                "npc = \"npc.slayer_kraken_boss_tentacle_whirlpool\"\ncoords = \"0_35_156_35_54\"",
                "npc = \"npc.slayer_kraken_boss_tentacle_whirlpool\"\ncoords = \"0_35_156_44_50\"",
                "npc = \"npc.slayer_kraken_boss_tentacle_whirlpool\"\ncoords = \"0_35_156_44_54\"",
            )
        expected.forEach { assertTrue(dump.contains(it), "Missing Kraken spawn:\n$it") }
    }

    private fun <T> withCache(block: (OsrsCacheProvider, List<MapLocDefinition>) -> T): T {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, REVISION)
            provider.init()
            val locData = requireNotNull(cache.data(MAPS, SOURCE_REGION, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val entries = mutableListOf<MapLocDefinition>()
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level == 0) entries += loc
            }
            return block(provider, entries)
        } finally {
            cache.close()
        }
    }

    private companion object {
        const val REVISION = 240
        const val SOURCE_MAP_X = 35
        const val SOURCE_MAP_Z = 156
        const val SOURCE_REGION = (SOURCE_MAP_X shl 8) or SOURCE_MAP_Z
    }
}
