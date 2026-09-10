package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.*
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.map.Direction
import org.rsmod.plugin.scripts.ScriptContext

internal class CerberusWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: CerberusEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        CERBERUS_INSTANCE_KEY,
        cerberusSpec(requireNotNull(ServerCacheManager.getNpc(CerberusAssets.SITTING_NPC_ID))),
    ) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave { encounters.onLeave(player, instanceId) }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(CERBERUS_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (!protectedAccess.launch(player) { leaveLair() }) RaidLeaveResult.Busy
                    else if (manager.sessionForPlayer(player)?.id != instanceId)
                        RaidLeaveResult.Left
                    else RaidLeaveResult.Failed
            }
        }
        listOf("npc.cerberus_attacking", "npc.cerberus_resetting", "npc.cerberus_sitting")
            .forEach { type ->
                onOpNpcU(type) { event ->
                    if (CerberusRules.isBoneItem(event.objType.internalName)) {
                        mes("Those aren't Cerby snacks")
                    }
                }
            }
        for (name in listOf("cerberus", "cerb")) {
            onCommand(name) {
                requiredRights = Rights.ADMINISTRATOR
                desc = "Create and enter a private Cerberus encounter"
                invalidArgs = "Use as ::$name"
                cheat { if (args.isEmpty()) protectedAccess.launch(player) { startLair() } }
            }
        }
        onCommand("cerbdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect or force the next no-reward Cerberus mechanic"
            invalidArgs =
                "Use ::cerbdebug status|hp <value>|counter <value>|next <attack>|skip force|suppress|clear"
            cheat { player.mes(encounters.debug(player, args)) }
        }
        onCommand("leavecerberus") {
            desc = "Leave your current private Cerberus encounter"
            invalidArgs = "Use as ::leavecerberus"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { leaveLair() } }
        }
    }

    internal suspend fun ProtectedAccess.startLair() {
        if (player.hitpoints <= 0) return
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return
        }
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        completeInstanceEntry(
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        )
        if (manager.sessionForPlayer(player)?.key == key) player.faceDirection(Direction.South)
    }

    internal suspend fun ProtectedAccess.leaveLair() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside a Cerberus encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key != key) mes("You leave Cerberus's private lair.")
    }
}
