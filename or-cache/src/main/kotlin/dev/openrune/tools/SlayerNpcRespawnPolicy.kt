package dev.openrune.tools

/** Applies the project-wide faster respawn rule to NPCs that count for a Slayer assignment. */
object SlayerNpcRespawnPolicy {
    fun halve(respawnRate: Int): Int =
        when {
            respawnRate == Int.MAX_VALUE -> Int.MAX_VALUE
            respawnRate <= 1 -> 1
            else -> (respawnRate / 2) + (respawnRate % 2)
        }
}
