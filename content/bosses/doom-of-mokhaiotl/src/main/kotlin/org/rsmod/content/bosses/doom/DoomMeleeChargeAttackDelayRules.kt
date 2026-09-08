package org.rsmod.content.bosses.doom

internal object DoomMeleeChargeAttackDelayRules {
    fun mayClaim(state: DoomState, bossOwnedByPlayer: Boolean, alreadyClaimed: Boolean): Boolean =
        state == DoomState.MELEE_CHARGE && bossOwnedByPlayer && !alreadyClaimed
}
