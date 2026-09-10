package dtx.core

import kotlin.random.Random

/** Optional entropy supplied through the existing roll context, including nested table rolls. */
public interface RollRandom {
    public fun nextInt(bound: Int): Int

    public fun nextDouble(bound: Double): Double

    public companion object {
        public val Context: ArgKey<RollRandom> = ArgKey("roll.random", KotlinRollRandom)
    }
}

private object KotlinRollRandom : RollRandom {
    override fun nextInt(bound: Int): Int = Random.nextInt(0, bound)

    override fun nextDouble(bound: Double): Double = Random.nextDouble(0.0, bound)
}
