package org.rsmod.content.minigames.perilousmoons

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid

internal enum class ColdWarning {
    NONE,
    MILD,
    SEVERE,
}

internal object PerilousMoonRules {
    val comboMaxHits = intArrayOf(4, 8, 20)
    val comboCurseAdds = intArrayOf(1, 2, 5)
    val auraDamage = intArrayOf(1, 2, 4, 8, 16)

    fun flatArmour(damage: Int, armour: Int): Int {
        if (damage <= 0) return 0
        return if (armour >= 0) max(1, damage - armour) else damage + abs(armour)
    }

    fun comboCanContinue(previousAccuracy: List<Boolean>, nextIndex: Int): Boolean =
        nextIndex == 0 || previousAccuracy.take(nextIndex).all { it }

    fun thirdHitStepUnderDodges(
        moon: PerilousMoon,
        hitIndex: Int,
        standingUnder: Boolean,
    ): Boolean = moon == PerilousMoon.Blood && hitIndex == 2 && standingUnder

    fun addCurse(current: Int, amount: Int): Int =
        (current + amount.coerceAtLeast(0)).coerceAtMost(MOON_CURSE_CAP)

    fun decayCurse(current: Int): Int = (current - 2).coerceAtLeast(0)

    fun coldWarning(previous: Int, current: Int): ColdWarning =
        when {
            previous < 9 && current >= 9 -> ColdWarning.SEVERE
            previous < 3 && current >= 3 -> ColdWarning.MILD
            else -> ColdWarning.NONE
        }

    fun shouldCancelColdAttack(curse: Int): Boolean = curse >= 18

    fun curseAfterCancelledAttack(curse: Int): Int = (curse - 18).coerceAtLeast(0)

    fun canAttackFrozenBlock(weaponEquipped: Boolean): Boolean = !weaponEquipped

    fun bloodHeal(hitIndex: Int, damage: Int): Int {
        if (damage <= 0) return 0
        return when (hitIndex) {
            0 -> damage
            1 -> damage * 2
            2 -> (damage * 3).coerceIn(30, 50)
            else -> 0
        }
    }

    fun bloodRainBossHeal(damage: Int): Int = damage.coerceAtLeast(0) * 3

    fun bloodRainAvoidanceChip(onPool: Boolean): Int = if (onPool) 0 else 1

    fun isOnMoonGlyph(glyphCenter: CoordGrid, tile: CoordGrid): Boolean {
        if (glyphCenter.level != tile.level) return false
        val southWest = moonGlyphSouthWest(glyphCenter)
        return tile.x in southWest.x until southWest.x + MOON_GLYPH_SIZE &&
            tile.z in southWest.z until southWest.z + MOON_GLYPH_SIZE
    }

    fun jaguarCenter(moonCenter: CoordGrid, glyphCenter: CoordGrid): CoordGrid =
        glyphCenter.translate(
            (glyphCenter.x - moonCenter.x).compareTo(0) * BLOOD_JAGUAR_OUTWARD_TILES,
            (glyphCenter.z - moonCenter.z).compareTo(0) * BLOOD_JAGUAR_OUTWARD_TILES,
        )

    fun isJaguarPoolSpawnTick(age: Int): Boolean =
        isJaguarCycleTick(age, BLOOD_JAGUAR_POOL_SPAWN_OFFSET, BLOOD_JAGUAR_CYCLES)

    fun isJaguarPoolDamageTick(age: Int): Boolean =
        isJaguarCycleTick(age, BLOOD_JAGUAR_POOL_DAMAGE_OFFSET, BLOOD_JAGUAR_CYCLES)

    fun isJaguarCleaveTick(age: Int): Boolean =
        isJaguarCycleTick(age, BLOOD_JAGUAR_CYCLE_TICKS, BLOOD_JAGUAR_CLEAVES)

    fun isJaguarPhaseComplete(age: Int): Boolean =
        age >= BLOOD_JAGUAR_CYCLES * BLOOD_JAGUAR_CYCLE_TICKS

    private fun isJaguarCycleTick(age: Int, firstTick: Int, cycles: Int): Boolean =
        age >= firstTick &&
            age <= firstTick + (cycles - 1) * BLOOD_JAGUAR_CYCLE_TICKS &&
            (age - firstTick) % BLOOD_JAGUAR_CYCLE_TICKS == 0

    fun jaguarPlayerHeal(damage: Int): Int = damage.coerceIn(0, BLOOD_JAGUAR_MAX_PLAYER_HEAL)

    fun jaguarBossSiphon(damage: Int): Int = damage.coerceIn(0, BLOOD_JAGUAR_MAX_SIPHON)

    fun nextGlyph(current: Int): Int = (current + 1).mod(8)

    fun shouldMoveGlyph(standardAttacks: Int): Boolean =
        standardAttacks > 0 && standardAttacks % MOON_ATTACKS_PER_GLYPH == 0

    fun shouldStartSpecial(standardAttacks: Int): Boolean =
        standardAttacks > 0 && standardAttacks % MOON_STANDARD_ATTACKS_PER_SPECIAL == 0

    fun nextSpecial(current: Int): Int = if (current == 1) 2 else 1

    fun commonRolls(subduedCount: Int): Int =
        when (subduedCount) {
            1 -> 1
            2 -> 3
            3 -> 6
            else -> 0
        }

    fun glancingChance(curse: Int): Int = curse.coerceIn(0, 100)

    fun glancingMaximum(normalMaximum: Int, clonePhase: Boolean): Int =
        (normalMaximum - if (clonePhase) 8 else 12).coerceAtLeast(1)

    fun frostStormHeal(unlitBraziers: Int): Int = unlitBraziers.coerceIn(0, 2) * 10

    fun frostStormTotalHeal(unlitBraziers: Int): Int =
        frostStormHeal(unlitBraziers) * BLUE_STORM_HEAL_CHECKS

    val eclipseCloneCount: Int
        get() = ECLIPSE_CLONE_GROUPS * ECLIPSE_CLONES_PER_GROUP

    fun eclipseCloneGroup(cloneIndex: Int): Int =
        cloneIndex.coerceIn(0, eclipseCloneCount - 1) / ECLIPSE_CLONES_PER_GROUP

    fun pauseAfterClone(completedClones: Int): Boolean =
        completedClones in 1 until eclipseCloneCount &&
            completedClones % ECLIPSE_CLONES_PER_GROUP == 0

    fun isInsideRoom(moon: PerilousMoon, x: Int, z: Int, level: Int): Boolean =
        level == moon.center.level &&
            x in moon.roomMin.x..moon.roomMax.x &&
            z in moon.roomMin.z..moon.roomMax.z
}

internal data class MoonProgressModel(
    val hitpoints: Int = MOON_MAX_HITPOINTS,
    val glyphIndex: Int = 0,
    val standardAttacks: Int = 0,
    val nextSpecial: Int = 1,
    val curse: Int = 0,
    val state: MoonEncounterState = MoonEncounterState.WAITING,
) {
    fun damaged(amount: Int): MoonProgressModel =
        copy(hitpoints = (hitpoints - amount.coerceAtLeast(0)).coerceAtLeast(0))

    fun healed(amount: Int): MoonProgressModel =
        copy(hitpoints = (hitpoints + amount.coerceAtLeast(0)).coerceAtMost(MOON_MAX_HITPOINTS))

    fun completedStandardAttack(): MoonProgressModel {
        val attacks = standardAttacks + 1
        val moved = PerilousMoonRules.shouldMoveGlyph(attacks)
        return copy(
            standardAttacks = attacks,
            glyphIndex = if (moved) PerilousMoonRules.nextGlyph(glyphIndex) else glyphIndex,
            state =
                if (PerilousMoonRules.shouldStartSpecial(attacks)) {
                    if (nextSpecial == 1) MoonEncounterState.SPECIAL_ATTACK_ONE
                    else MoonEncounterState.SPECIAL_ATTACK_TWO
                } else {
                    MoonEncounterState.STANDARD_COMBAT
                },
        )
    }
}
