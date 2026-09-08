package org.rsmod.content.minigames.fightcaves

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class FightCaveAttackHook @Inject constructor(private val runs: FightCaveRunManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        when {
            !runs.isFightCaveNpc(npc) -> NpcAttackValidateResult.Pass
            runs.isOwnedFightCaveNpc(player, npc) ->
                NpcAttackValidateResult.BypassSingleWayPvnRestriction
            else ->
                NpcAttackValidateResult.Deny(
                    "That Fight Cave monster belongs to another adventurer."
                )
        }
}

@Singleton
internal class FightCavePlayerDeathHook @Inject constructor(private val runs: FightCaveRunManager) :
    PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!runs.isActive(context.player)) return null
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = context.player,
            dropDuration = PlayerDeathDrops.DROP_DURATION_STANDARD,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }
}

@Singleton
internal class FightCaveDeathCleanupHook
@Inject
constructor(private val runs: FightCaveRunManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        runs.handleDeath(player)
    }
}

internal class FightCaveLifecycle @Inject constructor(private val runs: FightCaveRunManager) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerLogout { runs.handleLogout(player) }
    }

    override fun onPostTick(player: Player) {
        runs.tick(player)
    }
}
