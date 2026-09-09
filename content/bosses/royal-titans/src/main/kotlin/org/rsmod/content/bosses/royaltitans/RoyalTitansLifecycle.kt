package org.rsmod.content.bosses.royaltitans

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
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class RoyalTitansAttackHook
@Inject
constructor(private val encounters: RoyalTitansEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isParticipant(player, npc)) {
            return NpcAttackValidateResult.Deny("That creature belongs to another encounter.")
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class RoyalTitansPlayerDeathHook
@Inject
constructor(private val encounters: RoyalTitansEncounterManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!encounters.isActive(context.player)) return null
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
internal class RoyalTitansDeathCleanup
@Inject
constructor(private val encounters: RoyalTitansEncounterManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        // PlayerDeath has already delivered the outside pile and selected the normal respawn.
        encounters.removeParticipant(player, "player death", teleport = false)
    }
}

internal class RoyalTitansLifecycle
@Inject
constructor(private val encounters: RoyalTitansEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<InstanceEndedEvent>(instanceEventId(ROYAL_TITANS_INSTANCE_KEY)) {
            encounters.instanceEnded(instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == ROYAL_TITANS_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout {
            encounters.removeParticipant(
                player,
                "logout or disconnect",
                teleport = false,
                logout = true,
            )
        }
    }
}
