package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.square.MapSquareGrid

class ClanHallR240CacheAuditTest {
    @Test
    fun `revision 240 contains clan hall assets and placements`() {
        ServerCacheManager.init(REVISION)
        val relevant =
            ServerCacheManager.getObjects()
                .values
                .filter { type ->
                    val searchable =
                        buildString {
                                append(type.internalName)
                                append(' ')
                                append(type.name)
                                append(' ')
                                append(type.actions)
                            }
                            .lowercase()
                    SEARCH_TERMS.any(searchable::contains)
                }
                .sortedBy { it.id }

        assertTrue(relevant.any { it.id == CLAN_HALL_PORTAL })
        assertTrue(relevant.any { it.id == CLAN_COFFER })
        relevant.forEach {
            println(
                "Clan Hall loc: id=${it.id}, internal=${it.internalName}, " +
                    "name=${it.name}, actions=${it.actions}, width=${it.width}, length=${it.length}"
            )
        }

        val ids = relevant.mapTo(HashSet(), { it.id })
        val placements = placements(ids)
        placements.forEach(::println)
        assertTrue(placements.any { it.id == CLAN_HALL_PORTAL })
        assertTrue(placements.any { it.id == CLAN_COFFER })
    }

    @Test
    fun `revision 240 balloon assets are identified without guessing`() {
        ServerCacheManager.init(REVISION)
        val balloons =
            ServerCacheManager.getObjects().values
                .filter {
                    val searchable = "${it.internalName} ${it.name} ${it.actions}".lowercase()
                    "balloon" in searchable || "pop" in searchable
                }
                .sortedBy { it.id }
        balloons.forEach { println("CLAN_BALLOON_LOC id=${it.id} internal=${it.internalName} name=${it.name} actions=${it.actions}") }
        assertTrue(balloons.isNotEmpty(), "Revision 240 exposes no identifiable balloon locs.")
    }

    private fun placements(ids: Set<Int>): List<Placement> {
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        return try {
            cache
                .archives(MAPS)
                .asSequence()
                .filter { it <= 25286 }
                .mapNotNull { groupId ->
                    cache.data(MAPS, groupId, 0) ?: return@mapNotNull null
                    val data = cache.data(MAPS, groupId, 1) ?: return@mapNotNull null
                    val squareX = groupId shr 8
                    val squareZ = groupId and 0xFF
                    val decoded = MapLocListDecoder.decode(InlineByteBuf(data))
                    val matches = mutableListOf<Placement>()
                    val iterator = decoded.spawns.longIterator()
                    while (iterator.hasNext()) {
                        val loc = MapLocDefinition(iterator.nextLong())
                        if (loc.id in ids) {
                            matches +=
                                Placement(
                                    loc.id,
                                    squareX * MapSquareGrid.LENGTH + loc.localX,
                                    squareZ * MapSquareGrid.LENGTH + loc.localZ,
                                    loc.level,
                                    loc.shape,
                                    loc.angle,
                                )
                        }
                    }
                    matches.takeIf(List<Placement>::isNotEmpty)
                }
                .flatten()
                .toList()
        } finally {
            cache.close()
        }
    }

    private data class Placement(
        val id: Int,
        val x: Int,
        val z: Int,
        val level: Int,
        val shape: Int,
        val angle: Int,
    )

    private companion object {
        const val REVISION = 240
        const val CLAN_HALL_PORTAL = 41724
        const val CLAN_COFFER = 41626
        val SEARCH_TERMS =
            listOf(
                "clan hall", "clan_hall", "clan coffer", "clan_coffer", "party chest", "party_chest"
            )
    }
}
