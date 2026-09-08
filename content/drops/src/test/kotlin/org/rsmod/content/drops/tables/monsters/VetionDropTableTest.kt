package org.rsmod.content.drops.tables.monsters

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VetionDropTableTest {
    @Test
    fun `unique outcomes are mutually exclusive with exact OSRS rates`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val content =
            Files.readString(
                root.resolve("content/drops/src/main/resources/drops/tables/monsters/vetion.toml")
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
                "obj.wbr_vetion_skull" to 5760,
                "obj.dragon_2h_sword" to 4410,
                "obj.dragon_pickaxe" to 4410,
                "obj.wbr_voidwaker_blade" to 3136,
                "obj.rotg" to 2205,
            )
        assertEquals(expected, entries)
        assertEquals(19921, entries.values.sum())

        mapOf(
                "obj.wbr_vetion_skull" to 196,
                "obj.dragon_2h_sword" to 256,
                "obj.dragon_pickaxe" to 256,
                "obj.wbr_voidwaker_blade" to 360,
                "obj.rotg" to 512,
            )
            .forEach { (item, denominator) ->
                assertEquals(1.0 / denominator, entries.getValue(item) / 1_128_960.0, 1e-12)
            }
    }
}
