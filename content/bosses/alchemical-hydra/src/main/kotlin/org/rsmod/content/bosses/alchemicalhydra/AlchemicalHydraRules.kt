package org.rsmod.content.bosses.alchemicalhydra

import kotlin.math.abs

internal object AlchemicalHydraRules {
    const val NORMAL_ATTACK_RATE = 6
    const val FINAL_ATTACK_RATE = 4
    const val TRANSITION_TICKS = 4
    const val VENT_INITIAL_DELAY = 10
    const val VENT_INTERVAL = 8
    const val VENT_RESOLVE_DELAY = 3
    const val MAX_WRONG_VENT_STACKS = 10
    const val POISON_POOL_LIFETIME = 20
    const val LIGHTNING_LIFETIME = 18
    const val FLAME_LIFETIME = 18
    const val FLAME_BURNS = 5

    const val CHAMBER_MIN_X = 12
    const val CHAMBER_MAX_X = 35
    const val CHAMBER_MIN_Z = 17
    const val CHAMBER_MAX_Z = 39

    val thresholds = mapOf(HydraPhase.GREEN to 825, HydraPhase.BLUE to 550, HydraPhase.RED to 275)

    fun activeNpc(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE
            HydraPhase.RED -> AlchemicalHydraAssets.RED
            HydraPhase.FINAL -> AlchemicalHydraAssets.FINAL
        }

    fun transitionNpc(phase: HydraPhase): Int? =
        when (phase) {
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN_TO_BLUE
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE_TO_RED
            HydraPhase.RED -> AlchemicalHydraAssets.RED_TO_FINAL
            HydraPhase.FINAL -> null
        }

    fun nextPhase(phase: HydraPhase): HydraPhase? =
        when (phase) {
            HydraPhase.GREEN -> HydraPhase.BLUE
            HydraPhase.BLUE -> HydraPhase.RED
            HydraPhase.RED -> HydraPhase.FINAL
            HydraPhase.FINAL -> null
        }

    fun requiredVent(phase: HydraPhase): HydraVent? =
        when (phase) {
            HydraPhase.GREEN -> HydraVent.RED
            HydraPhase.BLUE -> HydraVent.GREEN
            HydraPhase.RED -> HydraVent.BLUE
            HydraPhase.FINAL -> null
        }

    fun special(phase: HydraPhase): HydraSpecial =
        when (phase) {
            HydraPhase.GREEN,
            HydraPhase.FINAL -> HydraSpecial.POISON
            HydraPhase.BLUE -> HydraSpecial.LIGHTNING
            HydraPhase.RED -> HydraSpecial.FLAME
        }

    fun attackRate(phase: HydraPhase): Int =
        if (phase == HydraPhase.FINAL) FINAL_ATTACK_RATE else NORMAL_ATTACK_RATE

    fun specialDue(basicAttacks: Int, specialsLaunched: Int): Boolean {
        val requiredBasics = if (specialsLaunched == 0) 3 else 3 + specialsLaunched * 9
        return basicAttacks >= requiredBasics
    }

    fun shouldSwitchStyle(phase: HydraPhase, attacksWithStyle: Int): Boolean =
        phase != HydraPhase.FINAL && attacksWithStyle >= 3

    fun nextStyleAfterAttack(
        phase: HydraPhase,
        current: HydraAttackStyle,
        attacksWithStyle: Int,
    ): HydraAttackStyle =
        if (phase == HydraPhase.FINAL || shouldSwitchStyle(phase, attacksWithStyle)) {
            current.opposite()
        } else {
            current
        }

    fun incomingDamage(rawDamage: Int, weakened: Boolean): Int =
        if (weakened) rawDamage.coerceAtLeast(0) else rawDamage.coerceAtLeast(0) / 4

    fun capAtThreshold(phase: HydraPhase, currentHitpoints: Int, damage: Int): Int {
        val threshold = thresholds[phase] ?: 0
        return damage.coerceIn(0, (currentHitpoints - threshold).coerceAtLeast(0))
    }

    fun reachesThreshold(phase: HydraPhase, hitpointsAfterHit: Int): Boolean =
        thresholds[phase]?.let { hitpointsAfterHit <= it } == true

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

    fun empoweredDamage(baseDamage: Int, wrongVentStacks: Int): Int =
        baseDamage.coerceAtLeast(0) *
            (100 + wrongVentStacks.coerceIn(0, MAX_WRONG_VENT_STACKS) * 5) / 100

    fun applyWrongVentStack(current: Int): Int = (current + 1).coerceAtMost(MAX_WRONG_VENT_STACKS)

    fun inChamber(localX: Int, localZ: Int): Boolean =
        localX in CHAMBER_MIN_X..CHAMBER_MAX_X && localZ in CHAMBER_MIN_Z..CHAMBER_MAX_Z

    fun ventTouchesBoss(bossX: Int, bossZ: Int, bossSize: Int, ventX: Int, ventZ: Int): Boolean =
        ventX in bossX until bossX + bossSize && ventZ in bossZ until bossZ + bossSize

    fun stepToward(fromX: Int, fromZ: Int, targetX: Int, targetZ: Int): Pair<Int, Int> =
        (fromX + (targetX - fromX).sign()) to (fromZ + (targetZ - fromZ).sign())

    fun poisonCenters(targetX: Int, targetZ: Int, five: Boolean): List<Pair<Int, Int>> {
        val offsets =
            if (five) listOf(0 to 0, 2 to 0, -2 to 0, 0 to 2, 0 to -2)
            else listOf(0 to 0, 2 to 0, 0 to 2)
        return offsets.map { (dx, dz) -> targetX + dx to targetZ + dz }
    }

    fun isPoisonSplash(playerX: Int, playerZ: Int, centerX: Int, centerZ: Int): Boolean =
        abs(playerX - centerX) <= 1 && abs(playerZ - centerZ) <= 1

    fun shouldDiscardRun(expectedSession: Long, actualSession: Long?): Boolean =
        expectedSession != actualSession

    private fun Int.sign(): Int =
        when {
            this < 0 -> -1
            this > 0 -> 1
            else -> 0
        }
}
