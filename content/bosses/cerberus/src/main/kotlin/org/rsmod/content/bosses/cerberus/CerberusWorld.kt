package org.rsmod.content.bosses.cerberus

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpNpcU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CerberusWorld @Inject constructor(private val encounters: CerberusEncounterManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()

        listOf("npc.cerberus_attacking", "npc.cerberus_resetting", "npc.cerberus_sitting")
            .forEach { type ->
                onOpNpcU(type) { event ->
                    if (CerberusRules.isBoneItem(event.objType.internalName)) {
                        mes("Those aren't Cerby snacks")
                    }
                }
            }

        onCommand("cerberus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Cerberus encounter"
            invalidArgs = "Use as ::cerberus"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("cerb") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Cerberus encounter"
            invalidArgs = "Use as ::cerb"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("cerbdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect or force the next no-reward Cerberus mechanic"
            invalidArgs =
                "Use ::cerbdebug status|hp <value>|counter <value>|next <attack>|skip force|suppress|clear"
            cheat { player.mes(encounters.debug(player, args)) }
        }
        onCommand("leavecerberus") {
            desc = "Leave your current private Cerberus encounter"
            invalidArgs = "Use as ::leavecerberus"
            cheat {
                if (args.isEmpty() && !encounters.leave(player)) {
                    player.mes("You are not inside a Cerberus encounter.")
                }
            }
        }
    }
}
