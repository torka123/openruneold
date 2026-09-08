package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.mechanics.toxins.SoulreaperStackDecay
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Restores one Soul Stack every 20 cycles after the player's most recent axe attack. */
class SoulreaperStackDecayScript
@Inject
constructor(
    private val worldClock: MapClock,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { decayDueStacks() }
    }

    private fun decayDueStacks() {
        for (player in players) {
            decayOneSoulStack(player)
        }
    }

    private fun decayOneSoulStack(player: Player) {
        if (!SoulreaperStackDecay.isDue(player, worldClock.cycle)) {
            return
        }

        val stacks = player.vars[SOUL_STACKS].coerceIn(0, MAX_SOUL_STACKS)
        if (stacks == 0) {
            SoulreaperStackDecay.clear(player)
            return
        }

        val remaining = stacks - 1
        VarPlayerIntMapSetter.set(player, SOUL_STACKS, remaining)
        player.statHeal("stat.hitpoints", constant = HEAL_PER_STACK, percent = 0)
        if (remaining > 0) {
            SoulreaperStackDecay.reset(player, worldClock.cycle)
        } else {
            SoulreaperStackDecay.clear(player)
        }
    }

    private companion object {
        const val SOUL_STACKS = "varp.soulreaper_stacks"
        const val HEAL_PER_STACK = 8
        const val MAX_SOUL_STACKS = 5
    }
}
