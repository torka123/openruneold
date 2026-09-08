package org.rsmod.content.bosses.dagannothkings

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Public Dagannoth Kings entrance, exit, and administrator travel commands. */
internal class DagannothKingsWorld
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        DagannothKingsTravelRules.downVariants.forEach { ladder ->
            onOpLoc1(ladder) { climbTo(DagannothKingsRoom.Standard) }
            onOpLoc2(ladder) { climbTo(DagannothKingsRoom.Slayer) }
            onOpLoc4(ladder) { peek() }
        }
        onOpLoc3(DagannothKingsTravelRules.DOWN_PRIVATE) {
            arriveDelay()
            mes("Private Dagannoth Kings rooms are not enabled. Choose Standard or Slayer.")
        }
        onOpLoc1(DagannothKingsTravelRules.UP) { climbOut() }

        listOf("dks", "dagannothkings", "daggonathkings").forEach { command ->
            onCommand(command) {
                requiredRights = Rights.ADMINISTRATOR
                desc = "Teleport beside the Dagannoth Kings entrance ladder"
                invalidArgs = "Use as ::$command"
                cheat {
                    if (args.isEmpty()) {
                        protectedAccess.launch(player) {
                            val destination = DagannothKingsTravelRules.EXTERNAL_ARRIVAL
                            instances.leaveForExternalTeleport(player, destination, clock.cycle)
                            telejump(destination, TeleportType.Exempt)
                            mes("Teleported beside the Dagannoth Kings entrance ladder.")
                        }
                    }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.climbTo(room: DagannothKingsRoom) {
        arriveDelay()
        animateLadderTravel(DagannothKingsTravelRules.entry(room))
    }

    private suspend fun ProtectedAccess.climbOut() {
        arriveDelay()
        animateLadderTravel(DagannothKingsTravelRules.EXTERNAL_ARRIVAL)
    }

    private suspend fun ProtectedAccess.animateLadderTravel(destination: CoordGrid) {
        anim("seq.human_reachforladder")
        delay(1)
        telejump(destination)
    }

    private fun ProtectedAccess.peek() {
        val standard = countPlayers(DagannothKingsRoom.Standard)
        val slayer = countPlayers(DagannothKingsRoom.Slayer)
        mes(roomCountMessage("standard", standard))
        mes(roomCountMessage("Slayer", slayer))
    }

    private fun countPlayers(room: DagannothKingsRoom): Int =
        players.count { candidate ->
            candidate.isSlotAssigned && DagannothKingsTravelRules.contains(room, candidate.coords)
        }

    private fun roomCountMessage(name: String, count: Int): String =
        when (count) {
            0 -> "The $name Dagannoth Kings room is currently empty."
            1 -> "There is 1 player in the $name Dagannoth Kings room."
            else -> "There are $count players in the $name Dagannoth Kings room."
        }
}
