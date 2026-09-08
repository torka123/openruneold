package org.rsmod.content.minigames.pestcontrol

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class PestControlWorld
@Inject
constructor(
    private val manager: PestControlManager,
    private val rewards: PestControlRewardService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(PestControlLander.gangplank) {
            arriveDelay()
            manager.joinQueue(player)
        }
        onOpLoc1(PestControlLander.ladder) {
            arriveDelay()
            manager.leaveLander(player)
        }
        onOpNpc1(PestControlLander.squire) {
            arriveDelay()
            player.mes("Climb the nearby gangplank to enter the Pest Control lander.")
        }
        onOpNpc2(PestControlLander.squire) {
            arriveDelay()
            player.mes("Climb the nearby gangplank to enter the Pest Control lander.")
        }
        onOpNpc3(PestControlLander.squire) {
            arriveDelay()
            rewards.openShop(player)
        }
        for (knight in PestControlRewardNpcs.voidKnights) {
            onOpNpc1(knight) {
                arriveDelay()
                player.mes("Right-click Exchange to spend your Pest Control commendation points.")
            }
            onOpNpc3(knight) {
                arriveDelay()
                rewards.openShop(player)
            }
        }

        onIfModalButton(PestControlRewardShop.confirmComponent) {
            rewards.buySelection(player, it.comsub)
        }

        onCommand("pc") {
            desc = "Teleport to the Pest Control Novice boat"
            invalidArgs = "Use as ::pc"
            cheat { if (args.isEmpty()) manager.teleportToOutpost(player) }
        }
        onCommand("pcstart") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start the queued Pest Control game immediately"
            invalidArgs = "Use as ::pcstart after boarding the Novice lander"
            cheat { if (args.isEmpty()) manager.startImmediately(player) }
        }
        onCommand("pcstatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show Pest Control queue or match state"
            invalidArgs = "Use as ::pcstatus"
            cheat { if (args.isEmpty()) player.mes(manager.status(player)) }
        }
        onCommand("pcpoints") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset your Pest Control points to $PC_ADMIN_TEST_POINTS for shop testing"
            invalidArgs = "Use as ::pcpoints"
            cheat {
                if (args.isEmpty()) {
                    val balance = rewards.setPoints(player, PC_ADMIN_TEST_POINTS)
                    player.mes("Your Pest Control test balance is now $balance points.")
                }
            }
        }
    }
}
