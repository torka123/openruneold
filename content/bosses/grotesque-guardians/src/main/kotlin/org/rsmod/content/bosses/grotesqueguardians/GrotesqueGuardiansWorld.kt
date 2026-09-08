package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class GrotesqueGuardiansWorld
@Inject
constructor(private val encounters: GrotesqueGuardiansEncounterManager) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()

        onOpLoc1(requireLoc(GrotesqueAssets.ROOF_ENTRANCE)) {
            arriveDelay()
            encounters.enter(player)
        }
        onOpLoc1(requireLoc(GrotesqueAssets.CLOISTER_BELL)) {
            arriveDelay()
            encounters.ringBell(player)
        }
        GrotesqueAssets.ENERGY_SPHERES.forEach { id ->
            onOpLoc1(requireLoc(id)) {
                arriveDelay()
                encounters.absorbSphere(player, it.loc.coords)
            }
        }
        listOf(GrotesqueAssets.DAWN_PHASE_ONE, GrotesqueAssets.DAWN_PHASE_THREE).forEach { id ->
            onOpNpcU(requireNpc(id)) {
                arriveDelay()
                encounters.useHammer(player, it.npc, it.objType.internalName)
            }
        }

        onCommand("grotesque") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Grotesque Guardians encounter"
            invalidArgs = "Use as ::grotesque"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("gg") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Grotesque Guardians encounter"
            invalidArgs = "Use as ::gg"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("leavegg") {
            desc = "Leave your current Grotesque Guardians encounter"
            invalidArgs = "Use as ::leavegg"
            cheat {
                if (args.isEmpty() && !encounters.leave(player)) {
                    player.mes("You are not inside a Grotesque Guardians encounter.")
                }
            }
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Guardian NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing revision-240 Guardian loc $id" }
}
