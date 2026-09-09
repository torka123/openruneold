package org.rsmod.content.bosses.kraken

import kotlin.math.abs

internal object KrakenRules {
    const val BOSS_HITPOINTS = 255
    const val TENTACLE_HITPOINTS = 120
    const val ATTACK_RATE_TICKS = 4
    const val BOSS_MAX_HIT = 28
    const val TENTACLE_MAX_HIT = 2

    fun canAwaken(phase: KrakenPhase): Boolean = phase == KrakenPhase.DORMANT

    fun chebyshevDistance(firstX: Int, firstZ: Int, secondX: Int, secondZ: Int): Int =
        maxOf(abs(firstX - secondX), abs(firstZ - secondZ))
}
