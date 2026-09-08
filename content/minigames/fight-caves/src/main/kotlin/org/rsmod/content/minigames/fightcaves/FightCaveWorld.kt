package org.rsmod.content.minigames.fightcaves

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class FightCaveWorld @Inject constructor(private val runs: FightCaveRunManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(FIGHT_CAVE_ENTRANCE_LOC) {
            arriveDelay()
            if (isFightCaveEntrance(it.loc.coords)) {
                runs.startFromEntrance(player)
            }
        }
        onOpLoc1(FIGHT_CAVE_EXIT_LOC) {
            arriveDelay()
            runs.exit(player)
        }
        onCommand("fightcaves") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport outside the real Fight Caves entrance"
            invalidArgs = "Use as ::fightcaves"
            cheat { if (args.isEmpty()) runs.teleportToEntrance(player) }
        }
        onCommand("fightcavesdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the current private Fight Caves state"
            invalidArgs = "Use as ::fightcavesdebug"
            cheat { if (args.isEmpty()) player.mes(runs.debugStatus(player)) }
        }
        onCommand("fightcaveswave") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Jump to a shortened Fight Caves wave"
            invalidArgs = "Use as ::fightcaveswave 1-7"
            cheat {
                val wave = args.singleOrNull()?.toIntOrNull()
                if (wave == null || wave !in 1..7) {
                    player.mes("Use as ::fightcaveswave 1-7")
                } else {
                    runs.forceWave(player, wave)
                }
            }
        }
        onCommand("fightcavesabort") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Abort the current Fight Caves test run"
            invalidArgs = "Use as ::fightcavesabort"
            cheat {
                if (args.isEmpty()) {
                    if (runs.isActive(player)) {
                        runs.abort(player, "admin abort command", teleport = true)
                        player.mes("Fight Caves run aborted.")
                    } else {
                        player.mes("You do not have an active Fight Caves run.")
                    }
                }
            }
        }
    }
}
