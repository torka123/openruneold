package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class YamaR240CacheAuditTest {
    @Test
    fun `revision 240 yama definitions are present and named`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            assertEquals("Yama", requireNotNull(provider.npcs[14176]).name)
            assertEquals("Void Flare", requireNotNull(provider.npcs[14179]).name)
            val judge = requireNotNull(provider.npcs[14180])
            assertEquals("Judge of Yama", judge.name)
            assertEquals(3, judge.size)
            val fireSurge = requireNotNull(provider.npcs[13507])
            assertEquals("<col=00ffff>Fire spell</col>", fireSurge.name)
            assertEquals(1, fireSurge.size)
            listOf(14182, 123, 124, 14157).forEach {
                assertNotNull(provider.npcs[it], "missing Yama NPC $it")
            }
            listOf(56246, 56264, 56335, 56336, 56337, 56338, 56339).forEach {
                assertNotNull(provider.objects[it], "missing Yama loc $it")
            }
            (12095..12176).forEach { id ->
                if (id !in setOf(12132, 12159, 12166, 12168, 12169, 12170, 12171, 12172, 12173))
                    assertNotNull(provider.anims[id], "missing Yama sequence $id")
            }
            provider.items
                .filterKeys { it in 30750..30840 }
                .toSortedMap()
                .forEach { (id, item) ->
                    if (
                        listOf(
                                "Yama",
                                "Contract",
                                "Oathplate",
                                "Soulflame",
                                "Dossier",
                                "Purifying sigil",
                            )
                            .any { item.name.contains(it, true) }
                    )
                        println("YAMA_R240_ITEM\t$id\t${item.name}\t${item.interfaceOptions}")
                }
            provider.npcs
                .filterKeys { it in 14150..14210 || it in setOf(123, 124, 7668) }
                .toSortedMap()
                .forEach { (id, npc) ->
                    if (npc.name.contains("Yama", true) || id in setOf(123, 124, 7668))
                        println("YAMA_R240_NPC\t$id\t${npc.name}\t${npc.actions}")
                }
            provider.objects
                .filterKeys { it in 56240..56370 }
                .toSortedMap()
                .forEach { (id, loc) ->
                    val glyphIds = setOf(56335, 56336, 56337, 56338, 56339)
                    if (
                        loc.name.contains("Yama", true) ||
                            id in setOf(56246, 56264, 56335, 56336, 56337, 56338, 56339)
                    ) {
                        if (id in glyphIds) {
                            assertEquals(3, loc.sizeX, "Yama glyph $id width")
                            assertEquals(3, loc.sizeY, "Yama glyph $id length")
                        }
                        println(
                            "YAMA_R240_LOC\t$id\t${loc.name}\t${loc.actions}\t${loc.sizeX}x${loc.sizeY}"
                        )
                    }
                }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `chasm entrance has only the floor zero Voice spawn`() {
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val group = (22 shl 8) or 157
            val data = requireNotNull(cache.data(MAPS, group, 5))
            val packed = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns
            val definitions = buildList {
                val iterator = packed.intIterator()
                while (iterator.hasNext()) {
                    add(MapNpcDefinition(iterator.nextInt()))
                }
            }
            val entrance = definitions.filter { it.id in setOf(7668, 14180) }

            assertEquals(
                listOf(MapNpcDefinition(7668, localX = 31, localZ = 31, level = 0)),
                entrance,
            )
        } finally {
            cache.close()
        }
    }
}
