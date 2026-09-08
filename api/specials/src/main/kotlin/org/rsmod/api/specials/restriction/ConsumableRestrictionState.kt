package org.rsmod.api.specials.restriction

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Temporary activity restrictions consumed by the shared food and potion scripts. */
public object ConsumableRestrictionState {
    public fun setFoodBlocked(player: Player, blocked: Boolean) {
        set(player, FOOD_BLOCKED, blocked)
    }

    public fun setHealingPotionsBlocked(player: Player, blocked: Boolean) {
        set(player, HEALING_POTIONS_BLOCKED, blocked)
    }

    public fun isFoodBlocked(player: Player): Boolean =
        player.attr.getOrDefault(FOOD_BLOCKED, false)

    public fun areHealingPotionsBlocked(player: Player): Boolean =
        player.attr.getOrDefault(HEALING_POTIONS_BLOCKED, false)

    public fun clear(player: Player) {
        player.attr.remove(FOOD_BLOCKED)
        player.attr.remove(HEALING_POTIONS_BLOCKED)
    }

    private fun set(player: Player, key: AttributeKey<Boolean>, enabled: Boolean) {
        if (enabled) {
            player.attr[key] = true
        } else {
            player.attr.remove(key)
        }
    }

    private val FOOD_BLOCKED: AttributeKey<Boolean> = AttributeKey(temp = true)
    private val HEALING_POTIONS_BLOCKED: AttributeKey<Boolean> = AttributeKey(temp = true)
}
