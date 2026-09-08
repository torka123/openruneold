package org.rsmod.content.bosses.dt2

import kotlin.math.abs

/** Source coordinates and packet constants: see docs/duke-repair-20260907.md. */
internal object DukeRules {
    const val ASLEEP = 12167
    const val NORMAL = 12191
    const val AWAKENED = 12195
    const val VENT = 12198
    const val ORB = 11708
    const val SLEEP = 10174
    const val IDLE = 10175
    const val MELEE = 10176
    const val MAGIC = 10177
    const val SPIT = 10178
    const val WAKE = 10179
    const val GAZE = 10180
    const val DEATH = 10181
    const val RESET = 10182
    const val WAKE_TICKS = 5
    // Both 3285 and 1703 send the avoidance result at launch + 4; resume at + 6.
    const val GAZE_CHECK_TICKS = 4
    const val GAZE_TICKS = 6
    const val MAGIC_HIT_TICKS = 4
    const val SLAM_TICKS = 2
    const val FERMENT_TICKS = 6
    // 1827:1673 -> 1696, repeated on five kills; 91:11010 -> 11033.
    const val RESPAWN_TICKS = 23
    const val BILE_TICKS = 15
    // 555:317662 -> 317978 (15450 -> 15459), repeated for every later pool.
    const val POOL_TICKS = 9
    val ventCentres = listOf(6442, 6446, 6450).flatMap { z -> listOf(3036, 3039, 3042).map { it to z } }
    val pillarSafeTiles = setOf(3034 to 6451, 3035 to 6451, 3043 to 6451, 3044 to 6451)
    val icicleTiles = (3036..3042).map { it to 6451 }
    val icicleGraphics = listOf(2440, 2441, 2442, 2443, 2442, 2441, 2440)
    val extremityRows = listOf(6444, 6440, 6448)
    val westExtremities = listOf(12199, 12200, 12201)
    val eastExtremities = listOf(12201, 12199, 12200)
    val iceTiles = (6435..6450).flatMap { z -> (3030..3032).map { it to z } + (3046..3048).map { it to z } }

    fun powderYield(level: Int) = ((level - 20) / 10).coerceIn(1, 6)
    fun saltYield(level: Int) = when { level >= 80 -> 6; level >= 75 -> 5; level >= 70 -> 4; else -> 3 }
    fun tolerance(awakened: Boolean) = if (awakened) 370 else 200
    fun poisonStrength(awakened: Boolean) = if (awakened) 125 else 100
    fun poisonDoses(awakened: Boolean) = (tolerance(awakened) + poisonStrength(awakened) - 1) / poisonStrength(awakened)
    fun enraged(hp: Int, awakened: Boolean) = hp <= if (awakened) 440 else 121
    fun rate(hp: Int, awakened: Boolean) = if (enraged(hp, awakened)) 4 else 5
    fun gasInterval(hp: Int, awakened: Boolean): Int = when {
        enraged(hp, awakened) -> 3
        hp <= (if (awakened) 1697 else 485) * 3 / 4 -> 4
        else -> 5
    }
    fun gasCount(hp: Int, awakened: Boolean) =
        if (awakened) { if (hp < 1290) 4 else 1 } else if (hp <= 242) 2 else 1
    fun gasOffsets(count: Int) = when (count) { 1 -> listOf(0); 2 -> listOf(0, 3); 4 -> listOf(0, 3, 5, 7); else -> error("Invalid gas count") }
    fun meleeRange(x: Int, z: Int) = (x in 3035..3043 && z == 6451) || (x to z) in pillarSafeTiles
    fun icicleArea(x: Int, z: Int) = icicleTiles.any { (ix, iz) -> abs(ix - x) <= 1 && abs(iz - z) <= 1 }
    fun slamArea(x: Int, z: Int) = (x to z) in icicleTiles
    fun pillarProtected(x: Int, z: Int) = (x to z) in pillarSafeTiles
    fun nearestVent(x: Int, z: Int): Pair<Int, Int> = ventCentres.minBy { (vx, vz) -> (vx-x)*(vx-x)+(vz-z)*(vz-z) }
    fun ventArea(x: Int, z: Int, centre: Pair<Int, Int>) = abs(x-centre.first) <= 1 && abs(z-centre.second) <= 1
    fun prepVents(step: Int) = listOf(2, 1, 0).mapIndexed { row, start -> (3036 + Math.floorMod(start + step, 3) * 3) to (6442 + row * 4) }
    fun coneArea(x: Int, z: Int, row: Int, west: Boolean): Boolean {
        val distance = if (west) x - 3028 else 3049 - x
        return distance in 1..4 && abs(z - (row + 1)) <= distance / 2
    }
    fun icicleDamage(roll: Int, prayer: Boolean) = if (prayer) roll.coerceIn(0, 11) / 2 else roll.coerceIn(0, 11)
    fun slamDamage(roll: Int, prayer: Boolean) = if (prayer) roll * 3 / 4 else roll
    fun magicDamage(roll: Int, prayer: Boolean) = if (prayer) (roll + 1) / 2 else roll
}

internal enum class DukeAction { STANDARD, GAZE, GAS }

/** Counter transitions consume no RNG. Specials do not increment the standard-attack counter. */
internal class DukeRotation {
    var standards = 0
        private set
    private var sinceGaze = 0
    private var sinceGas = 0
    private var gasDue = 8
    private var lastWasGaze = false
    fun next(hp: Int, awakened: Boolean): DukeAction {
        if (sinceGaze == 5) {
            sinceGaze = 0
            lastWasGaze = true
            return DukeAction.GAZE
        }
        if (sinceGas >= gasDue && !lastWasGaze) {
            sinceGas = 0
            gasDue = DukeRules.gasInterval(hp, awakened)
            return DukeAction.GAS
        }
        standards++
        sinceGaze++
        sinceGas++
        lastWasGaze = false
        return DukeAction.STANDARD
    }
}

/** Each pending operation carries its owning run generation. No wall-clock tasks are used. */
internal class DukeTimeline {
    private data class Event(val tick: Int, val generation: Long, val action: () -> Unit)
    private val events = mutableListOf<Event>()
    var epoch = 0L
        private set
    val size get() = events.size
    fun at(tick: Int, generation: Long, action: () -> Unit) { events += Event(tick, generation, action) }
    fun pulse(tick: Int, generation: Long) {
        val pulseEpoch = epoch
        val due = events.filter { it.tick <= tick }
        events.removeAll(due.toSet())
        for (event in due) {
            if (epoch != pulseEpoch) break
            if (event.generation == generation) event.action()
        }
    }
    fun clear() { epoch++; events.clear() }
}

internal class DukePreparation {
    var tolerance = 0
    val mushroomsPicked = mutableMapOf<Int, Int>()
    val vats = mutableMapOf<Pair<Int, Int>, DukeVat>()
    fun feed(awakened: Boolean, powder: Boolean): Boolean {
        tolerance = (tolerance + if (powder) 5 else DukeRules.poisonStrength(awakened)).coerceAtMost(DukeRules.tolerance(awakened))
        return tolerance >= DukeRules.tolerance(awakened)
    }
}

internal data class DukeVat(val doses: Int, val ready: Int)
