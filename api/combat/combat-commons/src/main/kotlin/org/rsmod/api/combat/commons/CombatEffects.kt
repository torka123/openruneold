package org.rsmod.api.combat.commons

import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

public object CombatEffects {

    public fun poison(target: Player, damage: Int) {
        PlayerPoison.tryPoison(target, initialDamage = damage)
    }

    private const val FREEZE_IMMUNITY_TICKS = 5

    /**
     * Applies a non-stacking binding effect to a player or NPC.
     *
     * Player freezes retain their existing message, timer, and immunity behavior through [freeze].
     * NPC binds are represented by the same clock-backed state and are enforced by the NPC movement
     * processor.
     */
    public fun bind(target: PathingEntity, ticks: Int): Boolean {
        if (target.isBound) {
            return false
        }
        val effectiveTicks =
            if (target is Npc) {
                val resistance = target.visType.paramOrNull(params.freeze_resistance) ?: 0
                npcBindDuration(ticks, resistance)
            } else ticks
        if (effectiveTicks == 0) return false
        target.applyBind(effectiveTicks)
        return true
    }

    /** Removes a binding effect and returns its remaining active duration. */
    public fun breakBind(target: PathingEntity): Int {
        val remaining = target.removeBind()
        if (target is Player) {
            target.frozen = false
            target.clearTimer("timer.combat_freeze")
        }
        return remaining
    }

    public fun freeze(target: Player, ticks: Int) {
        if (target.freezeImmune || !bind(target, ticks)) {
            return
        }
        target.frozen = true
        target.timer("timer.combat_freeze", ticks)
        target.mes("You have been frozen!", ChatType.Spam)
    }

    public fun unfreeze(target: Player) {
        breakBind(target)
        target.freezeImmune = true
        target.timer("timer.combat_freeze_immunity", FREEZE_IMMUNITY_TICKS)
    }

    public fun clearFreezeImmunity(target: Player) {
        target.freezeImmune = false
    }

    public fun statDrain(target: Player, stats: List<String>, amount: Int) {
        for (stat in stats) {
            target.statSub(stat, constant = amount, percent = 0)
        }
    }
}

internal fun npcBindDuration(ticks: Int, resistance: Int): Int =
    if (resistance >= 100) 0
    else (ticks * (100 - resistance.coerceAtLeast(0)) / 100).coerceAtLeast(1)
