package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeviathanR240CacheAuditTest {
    @Test
    fun `revision 240 Leviathan room and animation contract`() {
        ServerCacheManager.init(REVISION)
        val sequenceNames = mutableMapOf<Int, String>()
        LEVIATHAN_SEQUENCES.forEach { id ->
            val type = requireNotNull(ServerCacheManager.getAnim(id))
            sequenceNames[id] = type.internalName
            println("sequence=$id name=${type.internalName} ticks=${type.tickDuration}")
        }
        val locations = mutableMapOf<Int, Triple<Int, Int, Int>>()
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, REVISION)
            provider.init()
            val locData = requireNotNull(cache.data(MAPS, SOURCE_REGION, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level != 0) continue
                val type = provider.objects[loc.id] ?: continue
                if (
                    loc.id in LEVIATHAN_LOCS ||
                        type.name.contains("handhold", ignoreCase = true) ||
                        type.name.contains("boat", ignoreCase = true)
                ) {
                    val worldX = (SOURCE_MAP_X shl 6) + loc.localX
                    val worldZ = (SOURCE_MAP_Z shl 6) + loc.localZ
                    locations[loc.id] = Triple(worldX, worldZ, loc.level)
                    println(
                        "id=${loc.id} name=${type.name} actions=${type.actions} " +
                            "world=($worldX,$worldZ,${loc.level}) local=(${loc.localX},${loc.localZ}) " +
                            "shape=${loc.shape} angle=${loc.angle}"
                    )
                }
            }
        } finally {
            cache.close()
        }
        assertEquals(Triple(2070, 6368, 0), locations[47593], "south-west handholds")
        assertEquals(Triple(2091, 6380, 0), locations[47594], "north-east handholds")
        assertEquals(Triple(2065, 6371, 0), locations[49213], "post-kill rowboat")
        assertEquals("seq.npc_leviathan_01_stun_01", sequenceNames[10282])
        assertEquals("seq.npc_leviathan_01_melee_01", sequenceNames[10283])
        assertEquals("seq.npc_leviathan_01_360_start", sequenceNames[10285])
        assertEquals("seq.npc_leviathan_01_spit_start", sequenceNames[10288])
        assertEquals("seq.npc_leviathan_01_spawn", sequenceNames[10291])
    }

    private companion object {
        const val REVISION = 240
        const val SOURCE_MAP_X = 32
        const val SOURCE_MAP_Z = 99
        const val SOURCE_REGION = (SOURCE_MAP_X shl 8) or SOURCE_MAP_Z
        val LEVIATHAN_LOCS = setOf(47566, 47584, 47585, 47586, 47592, 47593, 47594, 47595, 49213)
        val LEVIATHAN_SEQUENCES =
            listOf(
                10276,
                10278,
                10279,
                10280,
                10281,
                10282,
                10283,
                10284,
                10285,
                10286,
                10287,
                10288,
                10289,
                10290,
                10291,
                10293,
                10294,
                10296,
            )
    }
}
