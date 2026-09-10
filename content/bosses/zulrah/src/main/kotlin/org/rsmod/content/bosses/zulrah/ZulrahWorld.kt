package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.*
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.ScriptContext

internal class ZulrahWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: ZulrahEncounterManager,
    private val storage: ZulrahDeathStorage,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        ZULRAH_INSTANCE_KEY,
        zulrahSpec(
            requireNotNull(ServerCacheManager.getNpc(ZulrahAssets.GREEN.asRSCM(RSCMType.NPC)))
        ),
    ) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(ZULRAH_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveShrine() }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }
        ZulrahAssets.boats.forEach { boat ->
            onOpLoc1(boat) {
                arriveDelay()
                enterShrine()
            }
            onOpLoc2(boat) {
                arriveDelay()
                enterShrine()
            }
        }
        onOpLoc1(ZulrahAssets.EXIT) {
            arriveDelay()
            leaveShrine()
        }
        ZulrahAssets.priestesses.forEach { priestess ->
            onOpNpc1(priestess) { talkToPriestess(it.npc) }
            onOpNpc3(priestess) { reclaimFromPriestess(it.npc) }
        }
        onCommand("zulrah") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Zul-Andra dock"
            invalidArgs = "Use as ::zulrah"
            cheat {
                if (args.isEmpty())
                    protectedAccess.launch(player) {
                        val session = manager.sessionForPlayer(player)
                        if (session != null && session.key != key) {
                            mes("Leave your current instance before travelling to Zul-Andra.")
                            return@launch
                        }
                        if (session != null) leaveShrine() else telejump(ZULRAH_DOCK)
                        mes("You arrive at the Zul-Andra dock. Board the boat to enter the shrine.")
                    }
            }
        }
        onCommand("zulrahdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start deterministic no-reward Zulrah: rotation [phase] [seed]"
            invalidArgs = "Use as ::zulrahdebug <rotation 1-4> [phase] [seed]"
            cheat {
                if (args.firstOrNull()?.equals("status", ignoreCase = true) == true) {
                    player.mes(encounters.debugStatus(player))
                    return@cheat
                }
                val rotation = args.getOrNull(0)?.toIntOrNull()
                val phase = args.getOrNull(1)?.toIntOrNull() ?: 1
                val seed = args.getOrNull(2)?.toLongOrNull() ?: 0L
                if (rotation == null || rotation !in 1..4) return@cheat
                protectedAccess.launch(player) {
                    startShrine(ZulrahEntryOptions(rotation, phase, seed))
                }
            }
        }
    }

    internal suspend fun ProtectedAccess.startShrine(
        options: ZulrahEntryOptions = ZulrahEntryOptions()
    ) {
        if (player.hitpoints <= 0) return
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return
        }
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        val result =
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        if (result is InstanceManager.Result.Created)
            result.session.state(ZULRAH_ENTRY_OPTIONS) { options }
        completeInstanceEntry(result)
    }

    internal suspend fun ProtectedAccess.leaveShrine() {
        if (manager.sessionForPlayer(player)?.key != key) return
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key != key) mes("You leave Zulrah's shrine.")
    }

    private suspend fun ProtectedAccess.enterShrine() {
        startShrine()
        if (!encounters.isActive(player)) return
        player.mes("The priestess rows you to Zulrah's shrine, then hurriedly paddles away.")
        player.mes("Zulrah respawns after 10 seconds.")
        val scene = encounters.entryScene(player) ?: return
        camModeFixed()
        minimapHideMap()
        try {
            camMoveTo(scene.camera, height = scene.cameraHeight, rate = 100, rate2 = 100)
            camLookAt(scene.focus, height = scene.focusHeight, rate = 100, rate2 = 100)
            delay(scene.durationTicks)
        } finally {
            camReset()
            camModeReset()
            minimapReset()
        }
    }

    private suspend fun ProtectedAccess.talkToPriestess(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) {
            if (!storage.hasItems(player)) {
                chatNpc(neutral, "I am not holding any of your belongings.")
            } else {
                reclaimDialogue()
            }
        }

    private suspend fun ProtectedAccess.reclaimFromPriestess(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) { reclaimDialogue() }

    private suspend fun Dialogue.reclaimDialogue() {
        if (!storage.hasItems(player)) {
            chatNpc(neutral, "I am not holding any of your belongings.")
            return
        }
        val fee = storage.fee(player)
        if (fee == 0) {
            chatNpc(neutral, "I recovered your belongings. There is no fee for you yet.")
            if (choice2("Reclaim my items.", 1, "Not now.", 2) == 1) storage.reclaim(player)
        } else {
            chatNpc(neutral, "I recovered your belongings. My fee is 100,000 coins.")
            if (choice2("Pay 100,000 coins.", 1, "Not now.", 2) == 1) storage.reclaim(player)
        }
    }
}
