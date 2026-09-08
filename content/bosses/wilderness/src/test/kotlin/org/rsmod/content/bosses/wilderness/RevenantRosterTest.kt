package org.rsmod.content.bosses.wilderness

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RevenantRosterTest {
    @Test
    fun `modern cave roster has exactly thirty ordinary revenants`() {
        val root = findProjectRoot()
        val sources =
            listOf(
                root.resolve(".data/raw-cache/map/npcs/unassigned.toml"),
                root.resolve(".data/raw-cache/map/npcs/revenant_caves.toml"),
            )
        val contents = sources.joinToString("\n") { it.readText() }
        val expected =
            mapOf(
                "npc.wild_cave_imp" to 4,
                "npc.wild_cave_goblin" to 2,
                "npc.wild_cave_pyrefiend" to 4,
                "npc.wild_cave_hobgoblin" to 2,
                "npc.wild_cave_cyclops" to 2,
                "npc.wild_cave_hellhound" to 2,
                "npc.wild_cave_demon" to 4,
                "npc.wild_cave_ork" to 4,
                "npc.wild_cave_darkbeast" to 2,
                "npc.wild_cave_knight" to 2,
                "npc.wild_cave_dragon" to 2,
            )
        val actual =
            expected.keys.associateWith { npc ->
                Regex("(?m)^npc\\s*=\\s*\"${Regex.escape(npc)}\"$").findAll(contents).count()
            }
        assertEquals(expected, actual)
        assertEquals(30, actual.values.sum())
    }

    @Test
    fun `dedicated additions file contains only deliberate missing modern forms`() {
        val contents =
            findProjectRoot().resolve(".data/raw-cache/map/npcs/revenant_caves.toml").readText()
        val names =
            Regex("(?m)^npc\\s*=\\s*\"([^\"]+)\"$")
                .findAll(contents)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(7, names.size)
        assertEquals(
            setOf("npc.wild_cave_imp", "npc.wild_cave_knight", "npc.wild_cave_dragon"),
            names.toSet(),
        )
    }

    private fun findProjectRoot(): Path {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .firstOrNull { Files.exists(it.resolve(".data/raw-cache/map/npcs")) }
        assertNotNull(root, "Could not locate OpenRune project root from test working directory")
        return root
    }
}
