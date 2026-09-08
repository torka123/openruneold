package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ConstructionUnresolvedCacheProbeTest {
    @Test
    fun `print object candidates for unresolved revision 240 furniture`() {
        val groups =
            linkedMapOf(
                "deadman_rug" to listOf("deadman"),
                "raging_echoes" to listOf("raging echoes"),
                "gnome_child_icon" to listOf("gnome child"),
                "grid_master_icon" to listOf("grid master"),
                "all_lecterns" to listOf("lectern"),
                "all_rugs" to listOf("rug"),
                "all_curtains" to listOf("curtain"),
                "quest_list" to listOf("quest list"),
                "trailblazer_rug" to listOf("trailblazer rug"),
                "pumpkin" to listOf("pumpkin"),
                "beehive" to listOf("beehive"),
                "greenman" to listOf("greenman"),
                "annihilation" to listOf("annihilation"),
            )
        val objects = ServerCacheManager.getObjects().values.sortedBy { it.id }
        groups.forEach { (group, terms) ->
            objects
                .filter { obj -> terms.any { obj.name.contains(it, ignoreCase = true) } }
                .forEach { obj ->
                    println(
                        "UNRESOLVED_OBJECT\t$group\t${obj.id}\t${obj.name}\t${obj.width}x${obj.length}\t${obj.transforms}\t${obj.actions}"
                    )
                }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$root${java.io.File.separator}")
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
