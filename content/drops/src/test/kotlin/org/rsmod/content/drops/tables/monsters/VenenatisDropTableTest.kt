package org.rsmod.content.drops.tables.monsters

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenenatisDropTableTest {
    @Test
    fun `unique outcomes are mutually exclusive with exact OSRS rates`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val content =
            Files.readString(
                root.resolve(
                    "content/drops/src/main/resources/drops/tables/monsters/venenatis.toml"
                )
            )
        val uniqueStart = content.indexOf("[[main.separate_rolls]]")
        val nextRoll = content.indexOf("[[main.separate_rolls]]", uniqueStart + 1)
        assertTrue(uniqueStart >= 0 && nextRoll > uniqueStart)
        val unique = content.substring(uniqueStart, nextRoll)

        assertTrue(Regex("(?m)^numerator\\s*=\\s*19921\\s*$").containsMatchIn(unique))
        assertTrue(Regex("(?m)^denominator\\s*=\\s*1128960\\s*$").containsMatchIn(unique))

        val entries =
            Regex(
                    """(?s)\[\[main\.separate_rolls\.entries]]\s*weight\s*=\s*(\d+)\s*obj\s*=\s*"([^"]+)""""
                )
                .findAll(unique)
                .associate { match -> match.groupValues[2] to match.groupValues[1].toInt() }
        val expected =
            mapOf(
                "obj.wbr_venenatis_fang" to 5760,
                "obj.dragon_2h_sword" to 4410,
                "obj.dragon_pickaxe" to 4410,
                "obj.wbr_voidwaker_gem" to 3136,
                "obj.sharp_ring" to 2205,
            )
        assertEquals(expected, entries)
        assertEquals(19921, entries.values.sum())

        mapOf(
                "obj.wbr_venenatis_fang" to 196,
                "obj.dragon_2h_sword" to 256,
                "obj.dragon_pickaxe" to 256,
                "obj.wbr_voidwaker_gem" to 360,
                "obj.sharp_ring" to 512,
            )
            .forEach { (item, denominator) ->
                assertEquals(1.0 / denominator, entries.getValue(item) / 1_128_960.0, 1e-12)
            }
    }

    @Test
    fun `base elite clue chance is one in one hundred`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val content =
            Files.readString(
                root.resolve(
                    "content/drops/src/main/resources/drops/tables/monsters/venenatis.toml"
                )
            )
        assertTrue(
            Regex(
                    """(?m)^\[\[tertiary]]\s*\Rnumerator\s*=\s*1\s*\Rdenominator\s*=\s*100\s*\Robj\s*=\s*"obj\.trail_elite_emote_exp1"$"""
                )
                .containsMatchIn(content),
            "Venenatis must use the base 1/100 elite clue roll.",
        )
    }
}
