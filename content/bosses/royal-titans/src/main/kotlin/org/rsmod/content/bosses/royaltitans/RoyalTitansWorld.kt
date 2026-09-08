package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class RoyalTitansWorld
@Inject
constructor(private val encounters: RoyalTitansEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        val entrance = requireLoc(RoyalTitansAssets.ENTRANCE)
        onOpLoc1(entrance) {
            arriveDelay()
            entryMenu()
        }
        onOpLoc2(entrance) {
            arriveDelay()
            entryMenu()
        }
        onOpLoc3(entrance) {
            arriveDelay()
            encounters.enter(player)
        }

        val exit = requireLoc(RoyalTitansAssets.EXIT)
        onOpLoc1(exit) {
            arriveDelay()
            if (!encounters.escape(player))
                player.mes("You are not inside a Royal Titans encounter.")
        }
        onOpLoc2(exit) {
            arriveDelay()
            if (!encounters.escape(player))
                player.mes("You are not inside a Royal Titans encounter.")
        }

        onOpLoc1(requireLoc(RoyalTitansAssets.SCOREBOARD)) {
            arriveDelay()
            player.mes(encounters.status(player))
        }

        for (id in listOf(RoyalTitansAssets.BRANDA_CORPSE, RoyalTitansAssets.ELDRIC_CORPSE)) {
            val corpse = requireNpc(id)
            onOpNpc1(corpse) {
                arriveDelay()
                encounters.claim(player, it.npc, ClaimChoice.LOOT)
            }
            onOpNpc3(corpse.internalName) {
                arriveDelay()
                if (choice2("Destroy regular loot for doubled pet chance?", 1, "Cancel", 2) == 1) {
                    encounters.claim(player, it.npc, ClaimChoice.SACRIFICE)
                }
            }
            onOpNpc4(corpse.internalName) {
                arriveDelay()
                encounters.claim(player, it.npc, ClaimChoice.PAGES)
            }
        }

        onCommand("rtenter") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a Royal Titans encounter"
            invalidArgs = "Use as ::rtenter"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("rtjoin") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Join a Royal Titans encounter by player name"
            invalidArgs = "Use as ::rtjoin player name"
            cheat { if (args.isNotEmpty()) encounters.join(player, args.joinToString(" ")) }
        }
        onCommand("rtstatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the active Royal Titans encounter"
            invalidArgs = "Use as ::rtstatus"
            cheat { player.mes(encounters.status(player)) }
        }
    }

    private suspend fun ProtectedAccess.entryMenu() {
        player.mes(
            "Dying during this encounter is not safe. Use Quick-escape on the tunnel or teleport to leave."
        )
        when (choice3("Start a new encounter.", 1, "Join another player.", 2, "Cancel.", 3)) {
            1 -> {
                ifClose()
                encounters.enter(player)
            }
            2 -> {
                val name = stringDialog("Enter the player's name:").trim()
                ifClose()
                if (name.isNotEmpty()) encounters.join(player, name)
            }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Royal Titans NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing Royal Titans location $id" }
}
