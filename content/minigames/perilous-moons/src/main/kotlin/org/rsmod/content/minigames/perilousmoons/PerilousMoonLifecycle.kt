package org.rsmod.content.minigames.perilousmoons

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class PerilousMoonAttackHook
@Inject
constructor(private val encounters: PerilousMoonEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isOwnedBy(player, npc)) {
            return NpcAttackValidateResult.Deny(
                "That Moon belongs to another adventurer's encounter."
            )
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) {
            NpcAttackValidateResult.BypassSingleWayPvnRestriction
        } else {
            NpcAttackValidateResult.Deny(denial)
        }
    }
}

/** Moons use the normal PvM death pipeline; this hook only documents that authority. */
@Singleton
internal class PerilousMoonPlayerDeathHook
@Inject
constructor(private val encounters: PerilousMoonEncounterManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!encounters.isActive(context.player)) return null
        return null
    }
}

@Singleton
internal class PerilousMoonDeathCleanupHook
@Inject
constructor(private val encounters: PerilousMoonEncounterManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        encounters.abort(player, "player death", teleport = false)
    }
}

internal class PerilousMoonLifecycle
@Inject
constructor(private val encounters: PerilousMoonEncounterManager) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            encounters.removeDecorativeShells()
            encounters.resetClientState(player)
        }
        onPlayerLogout { encounters.abort(player, "logout or disconnect", teleport = false) }
    }

    override fun onPostTick(player: Player) {
        encounters.removeDecorativeShells()
        encounters.recoverOrphanedFrozenWeapon(player)
        encounters.tick(player)
    }
}
