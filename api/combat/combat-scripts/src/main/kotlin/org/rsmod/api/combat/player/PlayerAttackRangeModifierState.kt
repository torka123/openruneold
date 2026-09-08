package org.rsmod.api.combat.player

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Temporary encounter-specific scaling for the normal player combat range pipeline. */
public object PlayerAttackRangeModifierState {
    public fun set(player: Player, numerator: Int, denominator: Int) {
        require(numerator > 0) { "Attack-range numerator must be positive." }
        require(denominator > 0) { "Attack-range denominator must be positive." }
        if (numerator == denominator) {
            clear(player)
        } else {
            player.attr[MODIFIER] = Modifier(numerator, denominator)
        }
    }

    public fun apply(player: Player, baseRange: Int): Int {
        require(baseRange > 0) { "Base attack range must be positive." }
        val modifier = player.attr[MODIFIER] ?: return baseRange
        return ((baseRange.toLong() * modifier.numerator) / modifier.denominator)
            .coerceIn(1L, MAX_RANGE.toLong())
            .toInt()
    }

    public fun clear(player: Player) {
        player.attr.remove(MODIFIER)
    }

    private data class Modifier(val numerator: Int, val denominator: Int)

    private const val MAX_RANGE: Int = 10
    private val MODIFIER: AttributeKey<Modifier> = AttributeKey(resetOnDeath = true, temp = true)
}
