package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class KrakenWorld @Inject constructor(private val encounters: KrakenEncounterManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()

        onOpNpc2(KrakenAssets.BOSS_WHIRLPOOL) {
            arriveDelay()
            encounters.disturb(player, it.npc)
        }
        onOpNpc2(KrakenAssets.TENTACLE_WHIRLPOOL) {
            arriveDelay()
            encounters.disturb(player, it.npc)
        }

        onOpLoc2(requireLoc(KrakenAssets.PUBLIC_CREVICE)) {
            arriveDelay()
            encounters.enter(player)
        }
        onOpLoc1(requireLoc(KrakenAssets.INSIDE_CREVICE)) {
            arriveDelay()
            if (!encounters.leave(player)) player.mes("You are not inside a Kraken encounter.")
        }

        onCommand("kraken") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Kraken encounter"
            invalidArgs = "Use as ::kraken"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("krak") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Kraken encounter"
            invalidArgs = "Use as ::krak"
            cheat { if (args.isEmpty()) encounters.enter(player) }
        }
        onCommand("leavekraken") {
            desc = "Leave your current private Kraken encounter"
            invalidArgs = "Use as ::leavekraken"
            cheat {
                if (args.isEmpty() && !encounters.leave(player)) {
                    player.mes("You are not inside a Kraken encounter.")
                }
            }
        }
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing Kraken location definition: $id"
        }
}
