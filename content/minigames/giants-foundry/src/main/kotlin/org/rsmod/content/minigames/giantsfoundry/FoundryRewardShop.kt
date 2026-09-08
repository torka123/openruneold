package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

/** Revision-240 adapter for the native Giants' Foundry reward shop (interface 753). */
@Singleton
internal class FoundryRewardShop
@Inject
constructor(
    private val service: FoundryService,
    private val ui: FoundryUi,
    private val clock: MapClock,
    private val collectionLog: CollectionLogService,
) {
    private val lastPurchaseTick = mutableMapOf<PlayerUid, Int>()

    fun buySelected(player: Player): Boolean {
        val selectedRewardVarp = requireNotNull(ServerCacheManager.getVarp(SELECTED_REWARD_VARP))
        val selectionId = player.vars[selectedRewardVarp]
        val entry = bySelection[selectionId]
        if (entry == null) {
            player.mes("Select a Giants' Foundry reward before pressing Buy.")
            return false
        }
        if (lastPurchaseTick[player.uid] == clock.cycle) return false
        lastPurchaseTick[player.uid] = clock.cycle
        return when (entry) {
            is PhysicalReward -> buyPhysical(player, entry)
            is MouldReward -> buyMould(player, entry)
        }
    }

    private fun buyPhysical(player: Player, reward: PhysicalReward): Boolean {
        val current = service.state(player)
        if (current !is FoundryServiceResult.Changed) {
            show(player, current)
            return false
        }
        if (current.state.reputation < reward.cost) {
            player.mes("You need ${reward.cost} Foundry Reputation for ${reward.displayName}.")
            return false
        }
        if (player.invAdd(player.inv, reward.internalName, 1).failure) {
            player.mes("You need a free inventory space before buying that reward.")
            return false
        }
        val changed = service.spendReputation(player, reward.cost)
        if (changed !is FoundryServiceResult.Changed) {
            player.invDel(player.inv, reward.internalName, 1, strict = false)
            show(player, changed)
            return false
        }
        ui.sync(player, changed.state)
        collectionLog.record(
            player = player,
            itemId = reward.itemId,
            amount = 1,
            source = CollectionLogAwardSource(CollectionLogAwardKind.MinigameReward, "giants-foundry"),
            eventId = "${player.uid}:${changed.state.revision}:${reward.selectionId}",
        )
        player.mes("You buy ${reward.displayName} for ${reward.cost} Foundry Reputation.")
        return true
    }

    private fun buyMould(player: Player, reward: MouldReward): Boolean {
        val mould = requireNotNull(FoundryMoulds.byKey[reward.mouldKey])
        check(mould.cost == reward.cost) { "Foundry mould price mismatch for ${reward.mouldKey}" }
        val changed = service.unlockMould(player, reward.mouldKey, player.smithingLvl)
        if (changed !is FoundryServiceResult.Changed) {
            show(player, changed)
            return false
        }
        ui.sync(player, changed.state)
        player.mes("You unlock ${mould.displayName} for ${mould.cost} Foundry Reputation.")
        return true
    }

    private fun show(player: Player, result: FoundryServiceResult) {
        when (result) {
            is FoundryServiceResult.Rejected -> player.mes(result.reason)
            FoundryServiceResult.CorruptState ->
                player.mes("Your Giants' Foundry state needs staff recovery before it can be changed.")
            else -> player.mes("That Giants' Foundry purchase could not be completed.")
        }
    }

    internal sealed interface RewardEntry {
        val selectionId: Int
        val cost: Int
    }

    internal data class PhysicalReward(
        override val selectionId: Int,
        val itemId: Int,
        val internalName: String,
        val displayName: String,
        override val cost: Int,
    ) : RewardEntry

    internal data class MouldReward(
        override val selectionId: Int,
        val mouldKey: String,
        override val cost: Int,
    ) : RewardEntry

    internal companion object {
        const val SELECTED_REWARD_VARP = 3_435

        val physicalRewards =
            listOf(
                PhysicalReward(1, 27_012, "obj.giants_foundry_double_ammo_mould", "a double ammo mould", 2_000),
                PhysicalReward(2, 27_014, "obj.giants_foundry_kovac_grog", "Kovac's grog", 300),
                PhysicalReward(3, 27_017, "obj.giants_foundry_smithing_catalyst", "a Smithing catalyst", 15),
                PhysicalReward(4, 27_019, "obj.giants_foundry_ore_pack", "an ore pack", 200),
                PhysicalReward(5, 27_023, "obj.giants_foundry_smiths_tunic", "a Smiths' tunic", 4_000),
                PhysicalReward(6, 27_025, "obj.giants_foundry_smiths_trousers", "Smiths' trousers", 4_000),
                PhysicalReward(7, 27_027, "obj.giants_foundry_smiths_boots", "Smiths' boots", 3_500),
                PhysicalReward(8, 27_029, "obj.giants_foundry_smiths_gloves", "Smiths' gloves", 3_500),
                PhysicalReward(9, 27_021, "obj.giants_foundry_colossal_blade", "a colossal blade", 5_000),
            )

        val mouldRewards =
            listOf(
                MouldReward(10, "stiletto_forte", 300),
                MouldReward(11, "defender_base", 300),
                MouldReward(12, "juggernaut_forte", 350),
                MouldReward(13, "chopper_forte_1", 400),
                MouldReward(14, "spiker", 450),
                MouldReward(15, "flamberge_blade", 300),
                MouldReward(16, "serpent_blade", 300),
                MouldReward(17, "claymore_blade", 350),
                MouldReward(18, "fleur_de_blade", 400),
                MouldReward(19, "choppa", 450),
                MouldReward(20, "corrupted_point", 300),
                MouldReward(21, "defenders_tip", 300),
                MouldReward(22, "serrated_tip", 350),
                MouldReward(23, "needle_point", 400),
                MouldReward(24, "the_point", 450),
            )

        val all: List<RewardEntry> = physicalRewards + mouldRewards
        val bySelection: Map<Int, RewardEntry> = all.associateBy(RewardEntry::selectionId)
    }
}
