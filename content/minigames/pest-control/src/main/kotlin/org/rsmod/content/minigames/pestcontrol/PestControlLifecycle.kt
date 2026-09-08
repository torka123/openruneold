package org.rsmod.content.minigames.pestcontrol

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerHitpointsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class PestControlNpcKillHook @Inject constructor(private val manager: PestControlManager) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        manager.handleNpcKill(context.npc)
    }
}

@Singleton
internal class PestControlAttackHook @Inject constructor(private val manager: PestControlManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        manager.validateAttack(player, npc)
}

@Singleton
internal class PestControlPlayerDeathHook
@Inject
constructor(private val manager: PestControlManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (manager.matchFor(context.player) == null) return null
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
internal class PestControlDeathCleanupHook
@Inject
constructor(private val manager: PestControlManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        manager.markPlayerDeath(player)
    }
}

@Singleton
internal class PestControlLifecycle @Inject constructor(private val manager: PestControlManager) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick() }
        onPlayerLogout { manager.logout(player) }
        onPlayerHitpointsChanged {
            if (newHitpoints <= 0 && manager.matchFor(player) != null) {
                player.statRestore("stat.hitpoints")
                manager.markPlayerDeath(player)
            }
        }
    }

    override fun onPostTick(player: Player) {
        manager.onPlayerPostTick(player)
    }
}
