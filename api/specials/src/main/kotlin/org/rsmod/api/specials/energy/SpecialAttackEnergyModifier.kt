package org.rsmod.api.specials.energy

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public object SpecialAttackEnergyModifier {
    public fun setCostDivisor(player: Player, divisor: Int) {
        require(divisor >= 1) { "Special attack cost divisor must be at least 1." }

        player.attr[COST_DIVISOR] = divisor
    }

    public fun setMinimumCost(player: Player, minimumCost: Int) {
        require(minimumCost >= 0) { "Special attack minimum cost cannot be negative." }

        player.attr[MINIMUM_COST] = minimumCost
    }

    public fun adjustedCost(player: Player, baseCost: Int): Int {
        if (baseCost < SPECIALIZED_REQUIREMENT_THRESHOLD) {
            return baseCost
        }

        val minimumCost = player.attr.getOrDefault(key = MINIMUM_COST, default = 0)
        val effectiveCost = maxOf(baseCost, minimumCost)
        val divisor = player.attr.getOrDefault(key = COST_DIVISOR, default = 1)

        return ceilDiv(value = effectiveCost, divisor = divisor)
    }

    public fun isActive(player: Player): Boolean =
        player.attr.getOrDefault(key = COST_DIVISOR, default = 1) > 1

    public fun clear(player: Player) {
        player.attr.remove(COST_DIVISOR)
    }

    public fun clearMinimumCost(player: Player) {
        player.attr.remove(MINIMUM_COST)
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

    private val COST_DIVISOR: AttributeKey<Int> = AttributeKey(resetOnDeath = true, temp = true)
    private val MINIMUM_COST: AttributeKey<Int> = AttributeKey(temp = true)

    private const val SPECIALIZED_REQUIREMENT_THRESHOLD: Int = 10
}
