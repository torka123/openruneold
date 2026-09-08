package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal object Revision240GauntletCache {
    private val initialized by
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            GameValProvider.load("$root${File.separator}")
            ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
            true
        }

    fun initialize() {
        check(initialized)
    }
}
