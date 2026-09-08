package org.rsmod.api.combat.player

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.api.death.PvPSkullHook
import org.rsmod.api.death.PvPSpecialAttackHook
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.mapMultiway
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player

/**
 * Applies the PvP checks and bookkeeping required for a secondary target of a multiway special.
 *
 * The primary target is still handled by the normal player-combat flow. This helper intentionally
 * omits single-way engagement checks because callers must only select secondary targets after
 * [ProtectedAccess.mapMultiway] succeeds.
 */
public class PvPAreaAttackManager
@Inject
constructor(
    private val areaChecker: AreaChecker,
    private val attackValidateHooks: Set<PvPAttackValidateHook>,
    private val skullHooks: Set<PvPSkullHook>,
    private val specialAttackHooks: Set<PvPSpecialAttackHook>,
) {
    /**
     * Runs the applicable target checks without sending a failure message or changing the
     * attacker's primary interaction.
     */
    public fun canAttack(attacker: Player, target: Player): Boolean {
        if (!attacker.mapMultiway(areaChecker)) {
            return false
        }
        if (!target.isValidTarget()) {
            return false
        }
        return attackValidateHooks.all { hook ->
            hook.validate(attacker, target) is PvPAttackValidateResult.Pass
        }
    }

    /**
     * Applies the same post-special PvP state as [org.rsmod.api.combat.PvPCombat].
     *
     * Call this after queueing the target's hit so [org.rsmod.api.death.PvPPlayerHitHook] keeps its
     * normal ordering.
     */
    /** Applies normal PvP skull and combat state for a secondary area-spell target. */
    public fun applySecondaryAttack(access: ProtectedAccess, target: Player) {
        for (hook in skullHooks) {
            hook.onPlayerAttack(access.player, target)
        }
        access.setPkVars(target)
    }

    /** Applies the special-attack hook in addition to the normal secondary-target state. */
    public fun applySecondarySpecialAttack(access: ProtectedAccess, target: Player) {
        for (hook in specialAttackHooks) {
            hook.onPlayerSpecialAttack(access.player, target)
        }
        for (hook in skullHooks) {
            hook.onPlayerAttack(access.player, target)
        }
        access.setPkVars(target)
    }
}
