package org.rsmod.content.bosses.alchemicalhydra

import kotlin.math.abs

internal enum class HydraPhase {
    GREEN,
    BLUE,
    RED,
    FINAL,
}

internal enum class HydraAttackStyle {
    MAGIC,
    RANGED;

    fun opposite(): HydraAttackStyle = if (this == MAGIC) RANGED else MAGIC
}

internal enum class HydraVent(val x: Int, val z: Int) {
    RED(27, 23),
    GREEN(27, 32),
    BLUE(18, 32),
}

internal object HydraRules {
    const val MAX_HITPOINTS = 1_100
    const val NORMAL_ATTACK_RATE = 6
    const val FINAL_ATTACK_RATE = 4
    const val TRANSITION_TICKS = 4
    const val VENT_INITIAL_DELAY = 10
    const val VENT_INTERVAL = 8
    const val POISON_POOL_TICKS = 20
    const val LIGHTNING_TICKS = 18
    const val FLAME_TICKS = 18
    const val MAX_WRONG_VENT_STACKS = 10
    const val MIN_CHAMBER_X = 12
    const val MAX_CHAMBER_X = 35
    const val MIN_CHAMBER_Z = 17
    const val MAX_CHAMBER_Z = 39

    fun threshold(phase: HydraPhase): Int? =
        when (phase) {
            HydraPhase.GREEN -> 825
            HydraPhase.BLUE -> 550
            HydraPhase.RED -> 275
            HydraPhase.FINAL -> null
        }

    fun nextPhase(phase: HydraPhase): HydraPhase? =
        when (phase) {
            HydraPhase.GREEN -> HydraPhase.BLUE
            HydraPhase.BLUE -> HydraPhase.RED
            HydraPhase.RED -> HydraPhase.FINAL
            HydraPhase.FINAL -> null
        }

    fun activeNpc(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> HydraAssets.GREEN
            HydraPhase.BLUE -> HydraAssets.BLUE
            HydraPhase.RED -> HydraAssets.RED
            HydraPhase.FINAL -> HydraAssets.FINAL
        }

    fun transitionNpc(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> HydraAssets.GREEN_TO_BLUE
            HydraPhase.BLUE -> HydraAssets.BLUE_TO_RED
            HydraPhase.RED -> HydraAssets.RED_TO_FINAL
            HydraPhase.FINAL -> error("The final Hydra phase has no transition form.")
        }

    fun requiredVent(phase: HydraPhase): HydraVent? =
        when (phase) {
            HydraPhase.GREEN -> HydraVent.RED
            HydraPhase.BLUE -> HydraVent.GREEN
            HydraPhase.RED -> HydraVent.BLUE
            HydraPhase.FINAL -> null
        }

    fun incomingDamage(damage: Int, weakened: Boolean): Int =
        damage.coerceAtLeast(0).let { if (weakened) it else it / 4 }

    fun capAtPhaseFloor(phase: HydraPhase, hitpoints: Int, damage: Int): Int =
        threshold(phase)?.let { damage.coerceIn(0, (hitpoints - it).coerceAtLeast(0)) }
            ?: damage.coerceAtLeast(0)

    fun basicMaxHit(phase: HydraPhase, weakened: Boolean): Int =
        when (phase) {
            HydraPhase.GREEN -> if (weakened) 17 else 26
            HydraPhase.BLUE -> if (weakened) 35 else 26
            HydraPhase.RED -> if (weakened) 35 else 52
            HydraPhase.FINAL -> 55
        }

    fun basicHitCount(phase: HydraPhase, weakened: Boolean): Int =
        when {
            phase == HydraPhase.GREEN -> 2
            phase == HydraPhase.BLUE && !weakened -> 2
            else -> 1
        }

    fun specialDue(basicAttacks: Int): Boolean = basicAttacks >= 3 && (basicAttacks - 3) % 9 == 0

    fun empoweredDamage(damage: Int, wrongVentStacks: Int): Int =
        damage.coerceAtLeast(0) * (100 + wrongVentStacks.coerceIn(0, MAX_WRONG_VENT_STACKS) * 5) / 100

    fun applyWrongVentStack(current: Int): Int = (current + 1).coerceAtMost(MAX_WRONG_VENT_STACKS)

    fun poisonCenters(x: Int, z: Int, final: Boolean): List<Pair<Int, Int>> {
        val offsets = if (final) listOf(0 to 0, 2 to 0, -2 to 0, 0 to 2, 0 to -2) else listOf(0 to 0, 2 to 0, 0 to 2)
        return offsets.map { (dx, dz) -> (x + dx) to (z + dz) }
    }

    fun poisonSplash(playerX: Int, playerZ: Int, tileX: Int, tileZ: Int): Boolean =
        abs(playerX - tileX) <= 1 && abs(playerZ - tileZ) <= 1

    fun stepToward(x: Int, z: Int, targetX: Int, targetZ: Int): Pair<Int, Int> =
        (x + (targetX - x).sign()) to (z + (targetZ - z).sign())

    private fun Int.sign(): Int = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }
}

internal object HydraAssets {
    const val GREEN = 8615
    const val GREEN_TO_BLUE = 8616
    const val BLUE_TO_RED = 8617
    const val RED_TO_FINAL = 8618
    const val BLUE = 8619
    const val RED = 8620
    const val FINAL = 8621
    const val FINAL_DEATH = 8622

    const val GREEN_POISON_ANIM = 8234
    const val GREEN_RANGE_ANIM = 8235
    const val GREEN_MAGIC_ANIM = 8236
    const val GREEN_TRANSITION_ANIM = 8237
    const val BLUE_EMERGE_ANIM = 8238
    const val BLUE_LIGHTNING_ANIM = 8241
    const val BLUE_RANGE_ANIM = 8242
    const val BLUE_MAGIC_ANIM = 8243
    const val BLUE_TRANSITION_ANIM = 8244
    const val RED_EMERGE_ANIM = 8245
    const val RED_FLAME_ANIM = 8248
    const val RED_RANGE_ANIM = 8249
    const val RED_MAGIC_ANIM = 8250
    const val RED_TRANSITION_ANIM = 8251
    const val FINAL_EMERGE_ANIM = 8252
    const val FINAL_POISON_ANIM = 8254
    const val FINAL_RANGE_ANIM = 8255
    const val DEATH_ANIM = 8257
    const val DEATH_END_ANIM = 8258
    const val ENTRY_ANIM = 839

    const val POISON_PROJECTILE = 1644
    const val POISON_SPLASH = 1645
    const val POISON_POOL = 1655
    const val MAGIC_PROJECTILE = 1662
    const val RANGED_PROJECTILE = 1663
    const val LIGHTNING_CENTER = 1664
    const val LIGHTNING_CURRENT_PROJECTILE = 1665
    const val LIGHTNING_CURRENT = 1666
    const val FLAME_PROJECTILE = 1667
    const val FLAME_TILE = 1668

    const val DOOR_SOUTH = 34553
    const val DOOR_NORTH = 34554
    const val DOOR_SOUTH_GRAPHIC = 34555
    const val DOOR_NORTH_GRAPHIC = 34556

    val formIds = listOf(GREEN, GREEN_TO_BLUE, BLUE_TO_RED, RED_TO_FINAL, BLUE, RED, FINAL, FINAL_DEATH)
    val animationIds = listOf(
        GREEN_POISON_ANIM, GREEN_RANGE_ANIM, GREEN_MAGIC_ANIM, GREEN_TRANSITION_ANIM,
        BLUE_EMERGE_ANIM, BLUE_LIGHTNING_ANIM, BLUE_RANGE_ANIM, BLUE_MAGIC_ANIM,
        BLUE_TRANSITION_ANIM, RED_EMERGE_ANIM, RED_FLAME_ANIM, RED_RANGE_ANIM,
        RED_MAGIC_ANIM, RED_TRANSITION_ANIM, FINAL_EMERGE_ANIM, FINAL_POISON_ANIM,
        FINAL_RANGE_ANIM, DEATH_ANIM, DEATH_END_ANIM, ENTRY_ANIM,
    )
    val doorIds = listOf(DOOR_SOUTH, DOOR_NORTH, DOOR_SOUTH_GRAPHIC, DOOR_NORTH_GRAPHIC)
    val ventIds = listOf(34568, 34569, 34570)
    val spotanimIds = listOf(
        POISON_PROJECTILE,
        POISON_SPLASH,
        POISON_POOL,
        MAGIC_PROJECTILE,
        RANGED_PROJECTILE,
        LIGHTNING_CENTER,
        LIGHTNING_CURRENT_PROJECTILE,
        LIGHTNING_CURRENT,
        FLAME_PROJECTILE,
        FLAME_TILE,
    )
}
