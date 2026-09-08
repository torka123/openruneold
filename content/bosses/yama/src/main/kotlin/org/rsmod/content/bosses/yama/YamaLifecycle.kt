package org.rsmod.content.bosses.yama

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.scripts.NpcMeleeApRangeHook
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
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.RaidLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class YamaAttackHook @Inject constructor(private val encounters: YamaEncounterManager) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isOwnedBy(player, npc)) {
            return NpcAttackValidateResult.Deny("That creature belongs to another pact.")
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class YamaJudgeMeleeApRangeHook
@Inject
constructor(private val encounters: YamaEncounterManager) : NpcMeleeApRangeHook {
    override fun range(player: Player, npc: Npc): Int? = encounters.judgeMeleeApRange(player, npc)
}

@Singleton
internal class YamaPlayerDeathHook
@Inject
constructor(private val encounters: YamaEncounterManager) : PlayerDeathHook {
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
internal class YamaDeathCleanup @Inject constructor(private val encounters: YamaEncounterManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        encounters.removeParticipant(player, "player death", teleport = true)
    }
}

internal class YamaLifecycle
@Inject
constructor(private val encounters: YamaEncounterManager, private val storage: YamaDeathStorage) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        // Yama creates and replaces glyph locations while ticking. Run before player zone
        // updates are flushed so every participant sees the same element the server resolves.
        onEvent<GameLifecycle.LateCycle> { encounters.tickAll() }
        onEvent<RaidLeaveRequestEvent>(instanceEventId(YAMA_INSTANCE_KEY)) {
            result = encounters.leaveFromLogoutTab(player, instanceId)
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == YAMA_INSTANCE_KEY) encounters.externalTeleport(player, instanceId)
        }
        onPlayerLogout {
            encounters.removeRecordsImp(player)
            encounters.removeParticipant(
                player,
                "logout or disconnect",
                teleport = false,
                logout = true,
            )
        }
    }

    override fun onPostTick(player: Player) {
        encounters.exposeNativeVoiceOptions(player)
        if (storage.hasItems(player) && !player.attr.has(YAMA_STORAGE_REMINDER_SHOWN)) {
            player.attr[YAMA_STORAGE_REMINDER_SHOWN] = true
            player.mes(
                "The Voice of Yama is guarding ${storage.count(player)} item stacks from your death."
            )
        }
    }
}
