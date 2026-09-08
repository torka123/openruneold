package org.rsmod.api.player.stat

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Temporary, per-stat scaling applied to positive [statAdd] and [statHeal] gains. */
public object StatRestorationModifierState {
    public const val DEFAULT_PERCENTAGE: Int = 100

    public fun setPercentage(player: Player, stat: String, percentage: Int) {
        require(percentage in 0..100) { "Restoration percentage must be between 0 and 100." }
        val percentages = player.attr[PERCENTAGES]
        if (percentage == DEFAULT_PERCENTAGE) {
            percentages?.remove(stat)
            if (percentages.isNullOrEmpty()) player.attr.remove(PERCENTAGES)
            return
        }
        if (percentages == null) {
            player.attr[PERCENTAGES] = mutableMapOf(stat to percentage)
        } else {
            percentages[stat] = percentage
        }
    }

    public fun percentage(player: Player, stat: String): Int =
        player.attr[PERCENTAGES]?.get(stat) ?: DEFAULT_PERCENTAGE

    public fun scale(player: Player, stat: String, amount: Int): Int {
        require(amount >= 0) { "Restoration amount must not be negative." }
        val percentage = percentage(player, stat)
        if (percentage == DEFAULT_PERCENTAGE || amount == 0) return amount
        return ((amount.toLong() * percentage) / 100L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    public fun clear(player: Player, stat: String) {
        val percentages = player.attr[PERCENTAGES] ?: return
        percentages.remove(stat)
        if (percentages.isEmpty()) player.attr.remove(PERCENTAGES)
    }

    public fun clear(player: Player) {
        player.attr.remove(PERCENTAGES)
    }

    private val PERCENTAGES: AttributeKey<MutableMap<String, Int>> =
        AttributeKey(resetOnDeath = true, temp = true)
}
