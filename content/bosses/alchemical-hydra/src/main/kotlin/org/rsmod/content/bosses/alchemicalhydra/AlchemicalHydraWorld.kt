package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class AlchemicalHydraWorld
@Inject
constructor(private val encounters: AlchemicalHydraEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        AlchemicalHydraAssets.doors.forEach { id ->
            val door = requireNotNull(ServerCacheManager.getObject(id)) { "Missing Hydra door $id" }
            onOpLoc1(door) {
                arriveDelay()
                encounters.crossDoor(player)
            }
            onOpLoc2(door) {
                arriveDelay()
                encounters.crossDoor(player)
            }
        }
        onCommand("hydra") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Alchemical Hydra encounter"
            invalidArgs = "Use as ::hydra"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("leavehydra") {
            desc = "Leave your private Alchemical Hydra encounter"
            invalidArgs = "Use as ::leavehydra"
            cheat {
                if (args.isEmpty() && !encounters.leave(player)) {
                    player.mes("You are not inside an Alchemical Hydra encounter.")
                }
            }
        }
        onCommand("hydrastatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the active Alchemical Hydra state"
            invalidArgs = "Use as ::hydrastatus"
            cheat { if (args.isEmpty()) player.mes(encounters.status(player)) }
        }
    }
}
