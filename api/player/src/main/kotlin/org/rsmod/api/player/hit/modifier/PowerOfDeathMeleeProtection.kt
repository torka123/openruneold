package org.rsmod.api.player.hit.modifier

import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * Shared state and damage handling for the Staff of the Dead family's Power of Death special.
 *
 * The one-minute effect is suspended while its staff is unequipped. It is lost only when the player
 * subsequently receives a non-zero hit without one of the eligible staves equipped.
 */
public object PowerOfDeathMeleeProtection {
    public const val DURATION_CYCLES: Int = 100

    /** Starts Power of Death without extending an already active duration. */
    public fun activate(player: Player) {
        if (!isActive(player)) {
            player.powerOfDeathUntil = player.currentMapClock + DURATION_CYCLES
        }
    }

    /** Clears a suspended effect when the player receives damage without an eligible staff. */
    public fun invalidateIfUnequipped(player: Player, incomingDamage: Int) {
        if (incomingDamage > 0 && isActive(player) && !hasEligibleStaff(player)) {
            player.powerOfDeathUntil = Int.MIN_VALUE
        }
    }

    /** Halves active incoming melee damage while an eligible staff remains equipped. */
    public fun reduceMeleeDamage(player: Player, incomingDamage: Int, type: HitType): Int {
        if (type != HitType.Melee || !isActive(player) || !hasEligibleStaff(player)) {
            return incomingDamage
        }
        return incomingDamage / 2
    }

    public fun isActive(player: Player): Boolean = player.currentMapClock < player.powerOfDeathUntil

    private fun hasEligibleStaff(player: Player): Boolean =
        player.righthand?.id in ELIGIBLE_STAFF_IDS

    private val ELIGIBLE_STAFF_IDS =
        setOf(
            11791, // Staff of the dead
            12902, // Toxic staff (uncharged)
            12904, // Toxic staff of the dead
            22296, // Staff of light
            23613, // Bounty hunter Staff of the dead
            24144, // Staff of balance
            33035, // Deadman Toxic staff (uncharged)
            33036, // Deadman Toxic staff of the dead
        )
}
