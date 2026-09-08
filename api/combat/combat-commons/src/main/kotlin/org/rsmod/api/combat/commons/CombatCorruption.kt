package org.rsmod.api.combat.commons

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

/** Shared, non-stacking implementation of the Lesser and Greater Corruption prayer drain. */
@Singleton
public class CombatCorruption @Inject constructor(private val clock: MapClock) {
    private val active = mutableMapOf<PlayerUid, State>()

    /**
     * Applies corruption when the target has no active corruption. Greater corruption upgrades an
     * active lesser effect, while repeated applications never create parallel drain schedules.
     */
    public fun apply(player: Player, strong: Boolean = false, accelerated: Boolean = false): Boolean {
        if (ArceuusSpellState.hasWard(player, clock.cycle)) {
            return false
        }
        val current = active[player.uid]
        if (current != null) {
            if (strong && !current.greater) {
                current.greater = true
                current.stage = 0
                current.interval = if (accelerated) MARKED_DRAIN_INTERVAL else DRAIN_INTERVAL
                current.nextDrain = clock.cycle + current.interval
                player.mes("The corruption afflicting you grows stronger.")
                return true
            }
            return false
        }
        active[player.uid] =
            State(
                player = player,
                greater = strong,
                stage = 0,
                interval = if (accelerated) MARKED_DRAIN_INTERVAL else DRAIN_INTERVAL,
                nextDrain = clock.cycle + if (accelerated) MARKED_DRAIN_INTERVAL else DRAIN_INTERVAL,
            )
        player.mes(
            if (strong) "You have been afflicted with greater corruption."
            else "You have been corrupted."
        )
        return true
    }

    /** Advances one player's corruption schedule. Call once per player game tick. */
    public fun tick(player: Player) {
        val state = active[player.uid] ?: return
        if (state.player !== player || !player.isSlotAssigned || player.pendingLogout) {
            active.remove(player.uid)
            return
        }
        if (clock.cycle < state.nextDrain) return
        val drains = if (state.greater) GREATER_DRAINS else LESSER_DRAINS
        player.statSub("stat.prayer", constant = drains[state.stage], percent = 0)
        state.stage++
        if (state.stage >= drains.size) {
            active.remove(player.uid)
            player.mes("The corruption afflicting you subsides.")
        } else {
            state.nextDrain += state.interval
        }
    }

    public fun clear(player: Player) {
        active.remove(player.uid)
    }

    public fun isActive(player: Player): Boolean = active.containsKey(player.uid)

    private data class State(
        val player: Player,
        var greater: Boolean,
        var stage: Int,
        var interval: Int,
        var nextDrain: Int,
    )

    private companion object {
        const val DRAIN_INTERVAL = 10
        const val MARKED_DRAIN_INTERVAL = 5
        val LESSER_DRAINS = intArrayOf(1, 2, 3)
        val GREATER_DRAINS = intArrayOf(2, 4, 6)
    }
}
