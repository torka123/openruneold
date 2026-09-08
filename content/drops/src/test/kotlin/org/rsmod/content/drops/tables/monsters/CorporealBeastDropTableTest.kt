package org.rsmod.content.drops.tables.monsters

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorporealBeastDropTableTest {
    private fun tableText(): String {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        return Files.readString(
            root.resolve(
                "content/drops/src/main/resources/drops/tables/monsters/corporeal_beast.toml"
            )
        )
    }

    @Test
    fun `table targets the revision 240 Corporeal Beast`() {
        val content = tableText()
        assertTrue(Regex("(?m)^npcs\\s*=\\s*\\[\"npc\\.corp_beast\"]$").containsMatchIn(content))
    }

    @Test
    fun `sigils use the independent one in five hundred eighty five roll`() {
        val content = tableText()
        val start = content.indexOf("[[main.separate_rolls]]")
        val end = content.indexOf("[[tertiary]]", start)
        assertTrue(start >= 0 && end > start)
        val sigils = content.substring(start, end)

        assertTrue(Regex("(?m)^numerator\\s*=\\s*1$").containsMatchIn(sigils))
        assertTrue(Regex("(?m)^denominator\\s*=\\s*585$").containsMatchIn(sigils))
        val entries =
            Regex(
                    """(?s)\[\[main\.separate_rolls\.entries]]\s*weight\s*=\s*(\d+)\s*obj\s*=\s*"([^"]+)""""
                )
                .findAll(sigils)
                .associate { match -> match.groupValues[2] to match.groupValues[1].toInt() }
        assertEquals(
            mapOf("obj.elysian_sigil" to 1, "obj.spectral_sigil" to 3, "obj.arcane_sigil" to 3),
            entries,
        )
        assertEquals(7, entries.values.sum())
        assertEquals(1.0 / 4095.0, entries.getValue("obj.elysian_sigil") / 4095.0, 1e-12)
        assertEquals(1.0 / 1365.0, entries.getValue("obj.spectral_sigil") / 4095.0, 1e-12)
        assertEquals(1.0 / 1365.0, entries.getValue("obj.arcane_sigil") / 4095.0, 1e-12)
    }
}
