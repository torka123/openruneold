package org.rsmod.content.bosses.doom

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Durable, synchronized storage boundary for the global Doom scoreboard. */
internal class DoomGlobalStatsStore(private val stateFile: Path) {
    @Synchronized
    fun load(): DoomGlobalStats {
        if (!Files.exists(stateFile)) return DoomGlobalStats()
        return try {
            DoomGlobalStatsCodec.decode(Files.readString(stateFile))
        } catch (error: Exception) {
            throw IllegalStateException("Malformed Doom scoreboard state at $stateFile", error)
        }
    }

    @Synchronized
    fun save(state: DoomGlobalStats) {
        Files.createDirectories(stateFile.parent)
        val temporary = stateFile.resolveSibling("${stateFile.fileName}.tmp")
        Files.writeString(temporary, DoomGlobalStatsCodec.encode(state))
        try {
            Files.move(
                temporary,
                stateFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun live(): DoomGlobalStatsStore =
            DoomGlobalStatsStore(Path.of(".data", "doom-of-mokhaiotl", "scoreboard.txt"))
    }
}
