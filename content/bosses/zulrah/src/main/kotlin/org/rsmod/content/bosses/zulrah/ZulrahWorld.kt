package org.rsmod.content.bosses.zulrah

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ZulrahWorld
@Inject
constructor(
    private val encounters: ZulrahEncounterManager,
    private val storage: ZulrahDeathStorage,
) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
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
            encounters.escape(player)
        }
        ZulrahAssets.priestesses.forEach { priestess ->
            onOpNpc1(priestess) { talkToPriestess(it.npc) }
            onOpNpc3(priestess) { reclaimFromPriestess(it.npc) }
        }
        onCommand("zulrah") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport to the Zul-Andra dock"
            invalidArgs = "Use as ::zulrah"
            cheat { if (args.isEmpty()) encounters.teleportDock(player) }
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
                encounters.enterDebug(player, rotation, phase, seed)
            }
        }
    }

    private suspend fun ProtectedAccess.enterShrine() {
        if (!encounters.enter(player)) return
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
