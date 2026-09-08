package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.lang.reflect.Modifier
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ConstructionLayoutCacheAuditTest {
    @Test
    fun `revision 240 contains finished architecture for every house style`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            data class StyleArchitecture(
                val style: String,
                val mapX: Int,
                val level: Int,
                val locIds: List<Int>,
            )
            val styleSources =
                listOf(
                    StyleArchitecture("BASIC_WOOD", 29, 0, listOf(13098, 13099, 13100, 13101)),
                    StyleArchitecture("BASIC_STONE", 29, 1, listOf(1902, 13091, 13094, 13096)),
                    StyleArchitecture(
                        "WHITEWASHED_STONE",
                        29,
                        2,
                        listOf(1415, 13005, 13006, 13007),
                    ),
                    StyleArchitecture("FREMENNIK_WOOD", 29, 3, listOf(13111, 13112, 13109, 13107)),
                    StyleArchitecture("TROPICAL_WOOD", 30, 0, listOf(13011, 10816, 13016, 13015)),
                    StyleArchitecture("FANCY_STONE", 30, 1, listOf(13116, 13117, 13119, 13118)),
                    StyleArchitecture("DEATHLY_MANSION", 30, 2, listOf(27082, 27083, 27084, 27085)),
                )
            styleSources.forEach { architecture ->
                architecture.locIds.forEach { id ->
                    assertNotNull(
                        provider.objects[id],
                        "${architecture.style} architecture loc $id is missing",
                    )
                }
                val locData = requireNotNull(cache.data(MAPS, (architecture.mapX shl 8) or 110, 1))
                val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
                val iterator = decoded.spawns.longIterator()
                var templateWindows = 0
                while (iterator.hasNext()) {
                    val loc = MapLocDefinition(iterator.nextLong())
                    if (
                        loc.level == architecture.level &&
                            loc.localX in 0..7 &&
                            loc.localZ in 56..63 &&
                            loc.id == 13830
                    ) {
                        templateWindows++
                    }
                }
                assertEquals(
                    8,
                    templateWindows,
                    "${architecture.style} parlour template-window count",
                )
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `dump revision 240 house template source blocks`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (mapX in 20..50) {
                for (mapZ in 90..130) {
                    val locData = cache.data(MAPS, (mapX shl 8) or mapZ, 1) ?: continue
                    val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
                    val windows = mutableMapOf<Triple<Int, Int, Int>, Int>()
                    val iterator = decoded.spawns.longIterator()
                    while (iterator.hasNext()) {
                        val loc = MapLocDefinition(iterator.nextLong())
                        if (loc.id != 13830) continue
                        val block = Triple(loc.level, loc.localX / 8, loc.localZ / 8)
                        windows[block] = windows.getOrDefault(block, 0) + 1
                    }
                    if (windows.isNotEmpty()) {
                        println(
                            "HOUSE_TEMPLATE_MAP mapX=$mapX mapZ=$mapZ blocks=" +
                                windows.entries
                                    .sortedWith(
                                        compareBy(
                                            { it.key.first },
                                            { it.key.second },
                                            { it.key.third },
                                        )
                                    )
                                    .joinToString { (key, count) ->
                                        "level=${key.first},x=${key.second},z=${key.third},windows=$count"
                                    }
                        )
                    }
                }
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `dump revision 240 house style architecture candidates`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            for (mapX in 29..31) {
                val locData = requireNotNull(cache.data(MAPS, (mapX shl 8) or 110, 1))
                val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
                for (level in 0..3) {
                    val counts = sortedMapOf<Int, Int>()
                    val iterator = decoded.spawns.longIterator()
                    while (iterator.hasNext()) {
                        val loc = MapLocDefinition(iterator.nextLong())
                        if (loc.level != level) continue
                        counts[loc.id] = counts.getOrDefault(loc.id, 0) + 1
                    }
                    val rendered =
                        counts.entries.joinToString(" | ") { (id, count) ->
                            val definition = provider.objects[id]
                            "$id:$count:${definition?.name}:${definition?.actions}"
                        }
                    println("HOUSE_STYLE_LOCS mapX=$mapX level=$level $rendered")
                }
            }
            provider.objects.values
                .filter { definition ->
                    definition.name.equals("Wall", true) ||
                        definition.name.equals("Window", true) ||
                        definition.name.equals("Door", true)
                }
                .filter { definition -> definition.id >= 27000 }
                .sortedBy { it.id }
                .forEach { definition ->
                    println(
                        "HOUSE_ARCH_OBJECT id=${definition.id} name=${definition.name} " +
                            "actions=${definition.actions}; ${reflect(definition)}"
                    )
                }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `dump formal garden hedges and hall staircases`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            val locData = requireNotNull(cache.data(MAPS, (29 shl 8) or 110, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level != 0) continue
                val room =
                    when {
                        loc.localX in 16..23 && loc.localZ in 8..15 -> "FORMAL_GARDEN"
                        loc.localX in 8..15 && loc.localZ in 48..55 -> "SKILL_HALL_UP"
                        loc.localX in 24..31 && loc.localZ in 48..55 -> "SKILL_HALL_DOWN"
                        loc.localX in 40..47 && loc.localZ in 48..55 -> "QUEST_HALL_UP"
                        loc.localX in 56..63 && loc.localZ in 48..55 -> "QUEST_HALL_DOWN"
                        else -> continue
                    }
                if (loc.id in 15370..15391) {
                    println(
                        "CONSTRUCTION_LAYOUT_LOC room=$room $loc; ${reflect(provider.objects[loc.id])}"
                    )
                }
            }
            val ids = (6727..6747).toList() + (13497..13505).toList() + (15370..15391).toList()
            ids.forEach { id ->
                println("CONSTRUCTION_LAYOUT_OBJECT $id = ${reflect(provider.objects[id])}")
            }
        } finally {
            cache.close()
        }
    }

    private fun reflect(value: Any?): String {
        if (value == null) return "null"
        val fields = linkedMapOf<String, Any?>()
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers) && !field.isSynthetic) {
                    field.trySetAccessible()
                    fields.putIfAbsent(field.name, runCatching { field.get(value) }.getOrNull())
                }
            }
            type = type.superclass
        }
        return fields.entries.joinToString("; ") { (name, fieldValue) ->
            val rendered =
                when (fieldValue) {
                    is IntArray -> fieldValue.joinToString(prefix = "[", postfix = "]")
                    is Array<*> -> fieldValue.joinToString(prefix = "[", postfix = "]")
                    else -> fieldValue.toString()
                }
            "$name=$rendered"
        }
    }
}
