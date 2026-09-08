package org.rsmod.content.other.consumables.mysterybox

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Opens one Mystery Box, awards one equally likely reward, and broadcasts the opening graphics. */
class MysteryBoxScript
@Inject
constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(MysteryBoxIds.Item) {
            open(
                type = it.type,
                slot = it.slot,
                inventory = it.inventory,
            )
        }
    }

    private suspend fun ProtectedAccess.open(
        type: ItemServerType,
        slot: Int,
        inventory: Inventory,
    ) {
        val reward = MysteryBoxIds.Rewards[random.of(MysteryBoxIds.Rewards.indices)]
        val consumed =
            invDel(
                inv = inventory,
                type = type.internalName,
                count = 1,
                slot = slot,
            )
        if (consumed.failure) {
            return
        }

        if (invAdd(inventory, reward.item, 1).failure) {
            invAdd(inventory, MysteryBoxIds.Item, 1)
            mes("You need an inventory space to open the Mystery Box.")
            return
        }

        anim(MysteryBoxIds.OpenAnimation)
        player.spotanim(
            MysteryBoxIds.SpinningBoxGraphic,
            height = MysteryBoxIds.BoxHeight,
            slot = MysteryBoxIds.SpotanimSlot,
        )
        delay(MysteryBoxIds.OpenAnimationCycles)

        repeat(MysteryBoxIds.RewardDisplayCycles) {
            player.spotanim(
                reward.graphic,
                height = MysteryBoxIds.RewardHeight,
                slot = MysteryBoxIds.SpotanimSlot,
            )
            delay(1)
        }
        spotanim(null, slot = MysteryBoxIds.SpotanimSlot)
        mes("You open the Mystery Box and receive ${reward.name}!")
    }
}

private object MysteryBoxIds {
    const val Item = "obj.mystery_box"
    const val OpenAnimation = 6303
    const val SpinningBoxGraphic = 4018
    const val SpotanimSlot = 0
    const val OpenAnimationCycles = 7
    const val RewardDisplayCycles = 8
    const val BoxHeight = 120
    const val RewardHeight = 150

    val Rewards: List<MysteryBoxReward> =
        listOf(
            MysteryBoxReward("obj.red_partyhat", "a Red partyhat", 4019),
            MysteryBoxReward("obj.scythe_of_vitur", "a Scythe of Vitur", 4020),
            MysteryBoxReward("obj.armadyl_godsword", "an Armadyl godsword", 4021),
            MysteryBoxReward("obj.twisted_bow", "a Twisted bow", 4022),
            MysteryBoxReward("obj.purple_partyhat", "a Purple partyhat", 4023),
        )
}

private data class MysteryBoxReward(
    val item: String,
    val name: String,
    val graphic: Int,
)
