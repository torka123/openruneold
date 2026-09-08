package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Test

class ConstructionModernRoomsCacheAuditTest {
    @Test
    fun `dump revision 240 portal nexus and league hall hotspots`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            val locData = requireNotNull(cache.data(MAPS, (29 shl 8) or 111, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level != 0 || loc.localZ !in 0..7) continue
                val room =
                    when (loc.localX) {
                        in 24..31 -> "PORTAL_NEXUS"
                        in 40..47 -> "LEAGUE_HALL"
                        else -> continue
                    }
                val definition = provider.objects[loc.id]
                println(
                    "MODERN_POH_LOC room=$room id=${loc.id} x=${loc.localX} z=${loc.localZ} " +
                        "type=${loc.shape} angle=${loc.angle} name=${definition?.name} " +
                        "actions=${definition?.actions}"
                )
            }
            val modernNames =
                listOf(
                    "portal nexus",
                    "xeric's talisman",
                    "digsite pendant",
                    "trophy pedestal",
                    "trophy case",
                    "banner stand",
                    "outfit stand",
                    "league statue",
                    "trailblazer globe",
                    "accomplishments scroll",
                    "raging echoes",
                    "deadman rug",
                    "greenman",
                    "pumpkin",
                    "beehive",
                    "gold sink",
                    "spice rack",
                    "ornate undead combat dummy",
                    "gnome child icon",
                    "grid master icon",
                    "marble lectern",
                    "stash chart",
                    "s.t.a.s.h chart",
                    "teak display",
                    "mahogany display",
                    "gilded display",
                    "oak display",
                )
            provider.objects.values
                .filter { definition ->
                    modernNames.any { name -> definition.name.contains(name, ignoreCase = true) }
                }
                .sortedBy { it.id }
                .forEach { definition ->
                    println(
                        "MODERN_POH_OBJECT id=${definition.id} name=${definition.name} actions=${definition.actions}"
                    )
                }
            provider.objects.values
                .filter { definition ->
                    definition.id in 33340..33550 || definition.id in 41020..41250
                }
                .sortedBy { it.id }
                .forEach { definition ->
                    println(
                        "MODERN_POH_RANGE id=${definition.id} name=${definition.name} actions=${definition.actions}"
                    )
                }
        } finally {
            cache.close()
        }
    }
}
