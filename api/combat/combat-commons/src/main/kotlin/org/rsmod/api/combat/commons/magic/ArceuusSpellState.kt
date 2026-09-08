package org.rsmod.api.combat.commons.magic

import org.rsmod.api.player.hit.modifier.WardOfArceuusState
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

/** Shared timed state used by Arceuus combat spells and the combat formula layer. */
public object ArceuusSpellState {
    private val marks = mutableMapOf<PlayerUid, Int>()
    private val shadowVeils = mutableMapOf<PlayerUid, Int>()

    public fun mark(caster: Player, expiresAt: Int) {
        marks[caster.uid] = expiresAt
        // Revision 240: 5926/3120 records map_clock and a duration measured in game ticks.
        caster.runClientScript(5926, 3120, (expiresAt - caster.currentMapClock).coerceAtLeast(0))
    }

    public fun isMarked(caster: Player, cycle: Int): Boolean {
        val expiry = marks[caster.uid] ?: return false
        if (expiry <= cycle) {
            clearMark(caster)
            return false
        }
        return true
    }

    public fun clearMark(caster: Player) {
        marks.remove(caster.uid)
        caster.runClientScript(5926, 3120, 0)
    }

    public fun ward(player: Player, expiresAt: Int) {
        WardOfArceuusState.activate(player, expiresAt)
    }

    public fun hasWard(player: Player, cycle: Int): Boolean =
        WardOfArceuusState.isActive(player, cycle)

    public fun hasWard(target: PathingEntity, cycle: Int): Boolean =
        target is Player && hasWard(target, cycle)

    public fun clearWard(player: Player) {
        WardOfArceuusState.clear(player)
    }

    public fun shadowVeil(player: Player, expiresAt: Int) {
        shadowVeils[player.uid] = expiresAt
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 1)
    }

    public fun hasShadowVeil(player: Player, cycle: Int): Boolean {
        val expiry = shadowVeils[player.uid] ?: return false
        if (expiry <= cycle) {
            shadowVeils.remove(player.uid)
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 0)
            return false
        }
        return true
    }

    public fun tick(player: Player, cycle: Int) {
        if ((marks[player.uid] ?: Int.MAX_VALUE) <= cycle) {
            clearMark(player)
        }
        WardOfArceuusState.isActive(player, cycle)
        if ((shadowVeils[player.uid] ?: Int.MAX_VALUE) <= cycle) {
            shadowVeils.remove(player.uid)
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 0)
        }
    }

    public fun clear(player: Player) {
        clearMark(player)
        clearWard(player)
        shadowVeils.remove(player.uid)
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_shadow_veil_active", 0)
    }
}
