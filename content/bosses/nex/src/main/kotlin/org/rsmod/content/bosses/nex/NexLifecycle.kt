package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops.Companion.DROP_DURATION_STANDARD
import org.rsmod.api.death.PlayerDeathDrops.Companion.standardKeepCount
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class NexAttackHook @Inject constructor(private val encounters: NexEncounterManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class NexPlayerDeathHook @Inject constructor(private val encounters: NexEncounterManager) :
    PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        // This policy belongs only to registered participants in the Ancient Prison chamber.
        if (!encounters.isChamberParticipant(context.player)) return null
        return PlayerDeathHandling(
            keepCount = standardKeepCount(context.hasProtectItem),
            dropReceiver = context.player,
            dropDuration = DROP_DURATION_STANDARD,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.DROP,
        )
    }
}

@Singleton
internal class NexDeathCleanup @Inject constructor(private val encounters: NexEncounterManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = encounters.playerDied(player)
}

internal class NexLifecycle @Inject constructor(private val encounters: NexEncounterManager) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerLogout { encounters.playerLoggedOut(player) }
        onEvent<GameLifecycle.EndCycle> { encounters.tickWorld() }
    }

    override fun onPostTick(player: Player) = encounters.tick(player)
}
