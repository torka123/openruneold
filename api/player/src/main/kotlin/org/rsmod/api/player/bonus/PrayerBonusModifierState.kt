package org.rsmod.api.player.bonus

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Temporary multiplier and addend for activity-specific effective prayer bonuses. */
public object PrayerBonusModifierState {
    public const val DEFAULT_MULTIPLIER: Int = 1
    public const val DEFAULT_ADDEND: Int = 0

    public fun set(
        player: Player,
        multiplier: Int = DEFAULT_MULTIPLIER,
        addend: Int = DEFAULT_ADDEND,
    ) {
        if (multiplier == DEFAULT_MULTIPLIER && addend == DEFAULT_ADDEND) {
            clear(player)
        } else {
            player.attr[MODIFIER] = Modifier(multiplier, addend)
        }
    }

    public fun apply(player: Player, base: Int): Int {
        val modifier = player.attr[MODIFIER] ?: return base
        return (base.toLong() * modifier.multiplier + modifier.addend)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()
    }

    public fun clear(player: Player) {
        player.attr.remove(MODIFIER)
    }

    private data class Modifier(val multiplier: Int, val addend: Int)

    private val MODIFIER: AttributeKey<Modifier> = AttributeKey(resetOnDeath = true, temp = true)
}
