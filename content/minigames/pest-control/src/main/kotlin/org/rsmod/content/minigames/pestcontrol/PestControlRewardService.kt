package org.rsmod.content.minigames.pestcontrol

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

@Singleton
internal class PestControlRewardService
@Inject
constructor(
    private val objs: ObjRepository,
    private val eventBus: EventBus,
    private val clock: MapClock,
    private val collectionLog: CollectionLogService,
) {
    private val lastPurchaseTick = mutableMapOf<PlayerUid, Int>()

    fun award(player: Player, match: PestControlMatch, participant: PestControlParticipant) {
        if (match.result != PestControlResult.Won || !participant.claimReward()) return
        val before = points(player)
        val after = PestControlRules.cappedPoints(before)
        val granted = after - before
        player.attr[PEST_CONTROL_POINTS] = after
        player.mes(
            "You receive $granted Pest Control point${if (granted == 1) "" else "s"}. " +
                "You now have $after points."
        )
    }

    fun points(player: Player): Int =
        player.attr.getOrDefault(PEST_CONTROL_POINTS, 0).coerceIn(0, PC_POINT_CAP)

    fun setPoints(player: Player, amount: Int): Int {
        val balance = PestControlRules.normalizePoints(amount)
        player.attr[PEST_CONTROL_POINTS] = balance
        syncPoints(player, balance)
        return balance
    }

    fun openShop(player: Player) {
        val balance = points(player)
        syncPoints(player, balance)
        player.ifOpenMainModal("interface.pest_rewardshop", eventBus)
        player.ifSetEvents(PestControlRewardShop.confirmComponent, 0..31, IfEvent.Op1)
        player.mes("You have $balance Pest Control commendation points.")
    }

    fun buySelection(player: Player, selectionId: Int): Boolean {
        val reward = SHOP_BY_SELECTION[selectionId]
        if (reward == null) {
            player.mes("That Pest Control reward is not available from this server.")
            return false
        }
        if (lastPurchaseTick[player.uid] == clock.cycle) return false
        lastPurchaseTick[player.uid] = clock.cycle
        val available = points(player)
        if (available < reward.cost) {
            player.mes("You need ${reward.cost} commendation points for that reward.")
            return false
        }
        val transaction = player.invAdd(player.inv, reward.internal, 1)
        if (!transaction.success) {
            player.mes("You need a free inventory space before buying that reward.")
            return false
        }
        val remaining = available - reward.cost
        player.attr[PEST_CONTROL_POINTS] = remaining
        syncPoints(player, remaining)
        val purchaseCount = player.attr.getOrDefault(PEST_CONTROL_REWARD_PURCHASES, 0) + 1
        player.attr[PEST_CONTROL_REWARD_PURCHASES] = purchaseCount
        collectionLog.record(
            player = player,
            itemId = reward.id,
            amount = 1,
            source =
                CollectionLogAwardSource(
                    CollectionLogAwardKind.MinigameReward,
                    "pest-control",
                ),
            eventId = "${player.uid}:$purchaseCount",
        )
        player.mes("You buy ${reward.displayName} for ${reward.cost} commendation points.")
        return true
    }

    private fun syncPoints(player: Player, balance: Int) {
        VarPlayerIntMapSetter.set(player, PestControlRewardShop.pointsVar, balance)
    }

    internal data class ShopReward(
        val selectionId: Int,
        val internal: String,
        val displayName: String,
        val cost: Int,
    ) {
        val id: Int
            get() = internal.asRSCM(RSCMType.OBJ)
    }

    companion object {
        val SHOP =
            listOf(
                ShopReward(24, "obj.pest_void_knight_mace", "a Void knight mace", 250),
                ShopReward(25, "obj.pest_void_knight_top", "a Void knight top", 250),
                ShopReward(26, "obj.pest_void_knight_robes", "Void knight robes", 250),
                ShopReward(27, "obj.pest_void_knight_gloves", "Void knight gloves", 150),
                ShopReward(28, "obj.game_pest_mage_helm", "a Void mage helm", 200),
                ShopReward(29, "obj.game_pest_archer_helm", "a Void ranger helm", 200),
                ShopReward(30, "obj.game_pest_melee_helm", "a Void melee helm", 200),
                ShopReward(31, "obj.pest_seal_8", "a Void seal", 10),
            )
        val SHOP_BY_SELECTION: Map<Int, ShopReward> = SHOP.associateBy(ShopReward::selectionId)
    }
}
