package org.rsmod.api.droptable

import dtx.core.RollRandom
import org.rsmod.api.random.GameRandom

/** Adapts OpenRune's injectable entropy to the existing dtx selectors. */
public class GameDropRandom(private val random: GameRandom) : RollRandom {
    override fun nextInt(bound: Int): Int = random.of(bound)

    override fun nextDouble(bound: Double): Double = random.randomDouble() * bound
}
