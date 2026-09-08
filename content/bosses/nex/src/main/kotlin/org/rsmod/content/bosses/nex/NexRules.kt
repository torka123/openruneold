package org.rsmod.content.bosses.nex

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.rsmod.map.CoordGrid

internal object NexRules {
    private val phaseThreshold =
        mapOf(
            NexState.SMOKE to 2720,
            NexState.SHADOW to 2040,
            NexState.BLOOD to 1360,
            NexState.ICE to 680,
        )

    fun threshold(state: NexState): Int? = phaseThreshold[state]

    fun lowerFloor(state: NexState): Int? = threshold(state)?.minus(170)

    fun isPhase(state: NexState): Boolean = state in phaseThreshold

    fun calledBodyguard(state: NexState): NexState? =
        when (state) {
            NexState.SMOKE -> NexState.FUMUS
            NexState.SHADOW -> NexState.UMBRA
            NexState.BLOOD -> NexState.CRUOR
            NexState.ICE -> NexState.GLACIES
            else -> null
        }

    fun nextPhase(called: NexState): NexState? =
        when (called) {
            NexState.FUMUS -> NexState.SHADOW
            NexState.UMBRA -> NexState.BLOOD
            NexState.CRUOR -> NexState.ICE
            NexState.GLACIES -> NexState.ZAROS
            else -> null
        }

    fun softCap(damage: Int): Int {
        val positive = damage.coerceAtLeast(0)
        return if (positive <= 50) positive else 50 + (positive - 50) / 3
    }

    /** June-2025 Nex call floor: normal damage to threshold, then 10% overdamage to -170. */
    fun phaseDamage(currentHp: Int, incoming: Int, state: NexState): Int {
        val threshold = threshold(state) ?: return softCap(incoming).coerceAtMost(currentHp)
        val floor = lowerFloor(state)!!
        if (currentHp <= floor) return 0
        val capped = softCap(incoming)
        if (currentHp <= threshold)
            return min((capped / 10).coerceAtLeast(if (capped > 0) 1 else 0), currentHp - floor)
        val toThreshold = currentHp - threshold
        if (capped <= toThreshold) return capped
        val overdamage = (capped - toThreshold) / 10
        return (toThreshold + overdamage).coerceAtMost(currentHp - floor)
    }

    fun special(state: NexState, attackCounter: Int, specialIndex: Int): NexSpecial? {
        if (attackCounter != 5) return null
        val even = specialIndex % 2 == 0
        return when (state) {
            NexState.SMOKE -> if (even) NexSpecial.CHOKE else NexSpecial.SMOKE_DASH
            NexState.SHADOW -> if (even) NexSpecial.SHADOW_SMASH else NexSpecial.EMBRACE_DARKNESS
            NexState.BLOOD -> if (even) NexSpecial.BLOOD_SIPHON else NexSpecial.BLOOD_SACRIFICE
            NexState.ICE -> if (even) NexSpecial.CONTAINMENT else NexSpecial.ICE_PRISON
            else -> null
        }
    }

    fun zarosOverhead(attackCounter: Int): NexOverhead =
        when ((attackCounter.coerceAtLeast(0) / 4) % 3) {
            0 -> NexOverhead.SOUL_SPLIT
            1 -> NexOverhead.DEFLECT_MELEE
            else -> NexOverhead.NONE
        }

    fun eligibleDamage(total: Int): Int = min(100, ceil(total.coerceAtLeast(0) * 0.05).toInt())

    fun isEligible(damage: Int, total: Int): Boolean = damage > 0 && damage >= eligibleDamage(total)

    fun wildernessAttack(hasMeleeTarget: Boolean): WildernessNexAttack =
        if (hasMeleeTarget) WildernessNexAttack.MELEE else WildernessNexAttack.PURPLE_AREA

    fun withinWildernessLeash(spawn: CoordGrid, current: CoordGrid): Boolean =
        spawn.level == current.level &&
            max(
                kotlin.math.abs(spawn.x - current.x),
                kotlin.math.abs(spawn.z - current.z),
            ) <= WILDERNESS_NEX_LEASH

    fun uniqueRollThreshold(
        damage: Int,
        total: Int,
        mvp: Boolean,
        scale: Long = 43_000_000L,
    ): Long {
        if (damage <= 0 || total <= 0) return 0
        val base = scale * damage.toLong() / total.toLong() / 43L
        return if (mvp) (base * 110L / 100L).coerceAtMost(scale) else base.coerceAtMost(scale)
    }

    fun commonQuantity(base: Int, damage: Int, total: Int, mvp: Boolean): Int {
        if (base <= 0 || damage <= 0 || total <= 0) return 0
        val scaled = base.toLong() * damage / total
        val boosted = if (mvp) scaled * 110L / 100L else scaled
        return boosted.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun reaverHeal(damage: Int): Int = damage.coerceAtLeast(0) * 3 / 4
}
