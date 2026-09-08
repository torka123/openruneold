package org.rsmod.content.minigames.clanmayhem

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.DEATH_KILLER_ATTR
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.api.death.PvPPlayerHitHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerHitpointsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class ClanMayhemPvpHook @Inject constructor(private val manager: ClanMayhemManager) :
    PvPAttackValidateHook, PvPPlayerHitHook {
    override fun validate(attacker: Player, target: Player): PvPAttackValidateResult =
        manager.validatePvp(attacker, target)

    override fun onPlayerHit(attacker: Player, target: Player) {
        manager.onPvpHit(attacker, target)
    }
}

@Singleton
internal class ClanMayhemNpcAttackHook @Inject constructor(private val manager: ClanMayhemManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        manager.validateNpcAttack(player, npc)
}

@Singleton
internal class ClanMayhemPlayerDeathHook
@Inject
constructor(private val manager: ClanMayhemManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (manager.matchFor(context.player) == null) return null
        manager.markDeath(context.player, context.killer)
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
internal class ClanMayhemDeathCleanupHook
@Inject
constructor(private val manager: ClanMayhemManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        manager.markDeath(player, player.attr[DEATH_KILLER_ATTR])
    }
}

@Singleton
internal class ClanMayhemLifecycle @Inject constructor(private val manager: ClanMayhemManager) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick() }
        onPlayerLogin { manager.onLogin(player) }
        onPlayerLogout { manager.onLogout(player) }
        onPlayerHitpointsChanged {
            if (newHitpoints <= 0 && manager.matchFor(player) != null) {
                player.statRestore("stat.hitpoints")
                manager.markDeath(player, player.attr[DEATH_KILLER_ATTR])
            }
        }
    }

    override fun onPostTick(player: Player) {
        manager.onPostTick(player)
    }
}
