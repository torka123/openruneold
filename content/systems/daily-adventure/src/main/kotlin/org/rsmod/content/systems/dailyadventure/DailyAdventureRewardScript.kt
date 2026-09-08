package org.rsmod.content.systems.dailyadventure

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class DailyAdventureRewardScript
@Inject
constructor(private val service: DailyAdventureService) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(MEMBER_BOX) { event ->
            openMemberBox(event.type, event.inventory)
        }
        onOpHeld1(PROTECTION_SCROLL) { event ->
            useProtectionScroll(event.type)
        }
    }

    private suspend fun ProtectedAccess.openMemberBox(
        type: ItemServerType,
        inventory: Inventory,
    ) {
        val reward = rollReward(random.of(TOTAL_WEIGHT))
        val result =
            player.invTransaction(inventory) {
                val selected = select(inventory)
                delete {
                    from = selected
                    obj = type.id
                    strictCount = 1
                }
                insert {
                    into = selected
                    obj = reward.item.asRSCM(RSCMType.OBJ)
                    strictCount = reward.count
                }
            }
        if (!result.success) {
            mes("You need enough inventory space to open the Daily Mystery Box.")
            return
        }
        mes("You open the Daily Mystery Box and receive ${reward.count} ${reward.name}.")
    }

    private fun ProtectedAccess.useProtectionScroll(type: ItemServerType) {
        if (!service.canAddProtectionCharge(player)) {
            mes("Your account already has the maximum of 3 streak-protection charges.")
            return
        }
        if (!service.consumeProtectionScroll(player, type.id)) {
            mes("The scroll could not be consumed; try again.")
            return
        }
        mes(
            "Your account now has ${service.state(player).streakProtectionCharges}/3 " +
                "streak-protection charges."
        )
    }

    private fun rollReward(roll: Int): MemberBoxReward {
        var cursor = 0
        for (reward in REWARDS) {
            cursor += reward.weight
            if (roll < cursor) return reward
        }
        return REWARDS.last()
    }

    private data class MemberBoxReward(
        val item: String,
        val count: Int,
        val name: String,
        val weight: Int,
    )

    private companion object {
        const val MEMBER_BOX = "obj.member_daily_mystery_box"
        const val PROTECTION_SCROLL = "obj.streak_protection_scroll"
        val REWARDS =
            listOf(
                MemberBoxReward("obj.coins", 50_000, "coins", 50),
                MemberBoxReward("obj.rune_arrow", 100, "rune arrows", 25),
                MemberBoxReward("obj.naturerune", 50, "nature runes", 15),
                MemberBoxReward("obj.deathrune", 50, "death runes", 10),
            )
        val TOTAL_WEIGHT = REWARDS.sumOf { it.weight }
    }
}
