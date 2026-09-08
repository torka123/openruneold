package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MuspahWorld
@Inject
constructor(private val encounters: MuspahEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        onOpLoc1(requireLoc(MuspahAssets.ENTRANCE_ID)) {
            arriveDelay()
            encounters.enter(player)
        }
        onOpLoc1(requireLoc(MuspahAssets.EXIT_ID)) {
            arriveDelay()
            if (!encounters.escape(player)) {
                player.mes("You are not inside a Phantom Muspah encounter.")
            }
        }
        onOpLoc1(requireLoc(MuspahAssets.ESCAPE_ID)) {
            arriveDelay()
            if (!encounters.escape(player)) {
                player.mes("You are not inside a Phantom Muspah encounter.")
            }
        }
        onCommand("muspah") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport outside the real Phantom Muspah crevice"
            invalidArgs = "Use as ::muspah"
            cheat { if (args.isEmpty()) encounters.teleportOutside(player) }
        }
        onCommand("muspahdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the current private Phantom Muspah encounter state"
            invalidArgs = "Use as ::muspahdebug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing Phantom Muspah location definition: $id"
        }
}
