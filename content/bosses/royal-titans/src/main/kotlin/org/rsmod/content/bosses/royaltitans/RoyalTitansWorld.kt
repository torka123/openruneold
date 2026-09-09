package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.input.ResumePNameDialogInput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class RoyalTitansWorld
@Inject
constructor(
    private val encounters: RoyalTitansEncounterManager,
    private val scoreboard: RoyalTitansScoreboard,
    private val players: PlayerList,
) : PluginScript() {
    private var ProtectedAccess.warningSuppressed by boolVarBit("varbit.royal_titans_enter_warning")

    override fun ScriptContext.startup() {
        encounters.validateAssets()
        val entrance = requireLoc(RoyalTitansAssets.ENTRANCE)
        onOpLoc1(entrance) {
            arriveDelay()
            entryMenu()
        }
        onOpLoc2(entrance) {
            arriveDelay()
            joinMenu()
        }
        onOpLoc3(entrance) {
            arriveDelay()
            startFight()
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
            scoreboard.show(this)
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
        when (
            choice3(
                "Start a fight (Your friends will be able to join you).",
                1,
                "Join a fight.",
                2,
                "Do nothing.",
                3,
            )
        ) {
            1 -> startFight()
            2 -> joinMenu()
        }
    }

    private suspend fun ProtectedAccess.startFight() {
        if (!confirmUnsafeDeath()) return
        ifClose()
        encounters.enter(player)
    }

    private suspend fun ProtectedAccess.joinMenu() {
        val result =
            nameDialog("Whose fight would you like to join? You must be on their friends' list.")
        val target = (result as? ResumePNameDialogInput.Result.SameWorld)?.uid
        if (!confirmUnsafeDeath()) return
        ifClose()
        // Resolve after the warning as well: a disconnected or reused slot is not the same player.
        encounters.join(player, target?.resolve(players)?.displayName.orEmpty())
    }

    private suspend fun ProtectedAccess.confirmUnsafeDeath(): Boolean {
        if (warningSuppressed) return true
        mesbox(
            "You are about to begin an encounter with the Royal Titans. Dying during this encounter " +
                "will not be considered a safe death. To leave, you'll need to use <col=ff0000>'Quick-escape'</col> " +
                "via the tunnel or teleport out. Are you sure you wish to proceed?"
        )
        return when (
            choice3(
                "Yes.",
                1,
                "Yes, and don't ask again.",
                2,
                "No.",
                3,
                title = "Are you sure you wish to proceed?",
            )
        ) {
            1 -> true
            2 -> {
                warningSuppressed = true
                true
            }
            else -> false
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Royal Titans NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing Royal Titans location $id" }
}
