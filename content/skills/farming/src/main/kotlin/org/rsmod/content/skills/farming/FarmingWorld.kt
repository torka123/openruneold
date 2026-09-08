package org.rsmod.content.skills.farming

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUDefaultEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Registers every live visual state of each Farming patch, not just its base loc id. */
class FarmingWorld @Inject constructor(private val farming: FarmingService) : PluginScript() {
    override fun ScriptContext.startup() {
        farming.registeredVisualIds().forEach { id ->
            onProtectedEvent<LocEvents.Op1>(id) { farming.handle(this, it.loc.id, 1) }
            onProtectedEvent<LocEvents.Op2>(id) { farming.handle(this, it.loc.id, 2) }
            onProtectedEvent<LocEvents.Op3>(id) { farming.handle(this, it.loc.id, 3) }
            onProtectedEvent<LocEvents.Op4>(id) { farming.handle(this, it.loc.id, 4) }
            onProtectedEvent<LocUDefaultEvents.OpType>(id) { farming.useItem(this, it.loc.id, it.objType.id) }
        }
        onPlayerLogin { farming.syncPlayer(player) }

        onCommand("farm") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Farming test controls"
            invalidArgs = "Use ::farm [status|grow|clear]"
            cheat {
                when (args.singleOrNull()?.lowercase()) {
                    null, "status" -> player.mes(farming.status(player))
                    "grow" -> player.mes("Finished growth for ${farming.forceGrow(player)} active Farming patch(es).")
                    "clear" -> player.mes("Cleared ${farming.clearAll(player)} Farming patch state(s).")
                }
            }
        }
    }
}
