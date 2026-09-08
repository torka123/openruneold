package org.rsmod.content.bosses.doom

/** Pure shield-damage contracts shared by the live encounter and deterministic tests. */
internal object DoomShieldRuntimeRules {
    fun acceptedDamage(demonbane: Boolean, rolledDamage: Int, remainingShield: Int): Int {
        if (!demonbane || remainingShield <= 0) return 0
        return rolledDamage.coerceAtLeast(0).coerceAtMost(remainingShield)
    }

    fun remainingShield(currentShield: Int, appliedDamage: Int): Int =
        (currentShield - appliedDamage.coerceAtLeast(0)).coerceAtLeast(0)

    fun captureNormalHitpoints(storedHitpoints: Int, currentHitpoints: Int): Int {
        val current = currentHitpoints.coerceAtLeast(0)
        return if (storedHitpoints < 0) current else minOf(storedHitpoints, current)
    }

    fun restoreNormalHitpoints(storedHitpoints: Int, maximumHitpoints: Int): Int? {
        if (storedHitpoints < 0) return null
        return storedHitpoints.coerceIn(0, maximumHitpoints.coerceAtLeast(1))
    }

    fun restoredHitpointsAfterStaleShieldImpact(
        currentHitpoints: Int,
        maximumHitpoints: Int,
        appliedDamage: Int,
    ): Int =
        (currentHitpoints + appliedDamage.coerceAtLeast(0)).coerceIn(
            0,
            maximumHitpoints.coerceAtLeast(1),
        )
}

internal enum class DoomShieldTransitionDestination {
    NORMAL_HEALTH,
    SHIELD,
    STALE_PHASE,
}

internal data class DoomShieldTransitionResolution(
    val destination: DoomShieldTransitionDestination,
    val shieldDamage: Int = 0,
    val requiresScriptedDamage: Boolean = false,
    val resetsCharge: Boolean = false,
) {
    val damagesShield: Boolean
        get() = destination == DoomShieldTransitionDestination.SHIELD && shieldDamage > 0
}

/** Routes a hit which was launched during Doom's two-tick shield transition at impact time. */
internal object DoomShieldTransitionRules {
    fun resolve(
        epochMatches: Boolean,
        impactState: DoomState,
        demonbane: Boolean,
        impactDamage: Int,
    ): DoomShieldTransitionResolution {
        if (!epochMatches) {
            return DoomShieldTransitionResolution(DoomShieldTransitionDestination.STALE_PHASE)
        }
        if (impactState == DoomState.SHIELD_TRANSITION) {
            return DoomShieldTransitionResolution(DoomShieldTransitionDestination.NORMAL_HEALTH)
        }
        if (impactState != DoomState.SHIELDED) {
            return DoomShieldTransitionResolution(DoomShieldTransitionDestination.STALE_PHASE)
        }

        /*
         * Since the 20 August 2025 live change, any positive transition-launched hit damages the
         * shield even when it was not demonbane. Only a demonbane hit resets the shield charge.
         */
        val damage = impactDamage.coerceAtLeast(0)
        return DoomShieldTransitionResolution(
            destination = DoomShieldTransitionDestination.SHIELD,
            shieldDamage = damage,
            resetsCharge = demonbane && damage > 0,
        )
    }
}
