package org.rsmod.api.player.prayer

import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/** Shared calculation and client-state refresh for equipment prayer-drain resistance. */
public object PrayerDrainResistance {
    public const val BASE_RESISTANCE: Int = 60
    public const val RESISTANCE_PER_PRAYER_BONUS: Int = 2
    public const val VARBIT: String = "varbit.prayer_drain_resistance"

    public fun calculate(prayerBonus: Int): Int =
        (BASE_RESISTANCE.toLong() + prayerBonus.toLong() * RESISTANCE_PER_PRAYER_BONUS)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()

    public fun refresh(player: Player, bonuses: WornBonuses): Int {
        val resistance = calculate(bonuses.prayerBonus(player))
        VarPlayerIntMapSetter.set(player, VARBIT, resistance)
        return resistance
    }
}
