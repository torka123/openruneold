package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollRandom
import dtx.core.RollResult
import dtx.core.with
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.GameDropRandom
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.EnsouledHeadProvenance
import org.rsmod.map.CoordGrid

/**
 * Native personal NPC-drop delivery shared by death hooks and deferred corpse claims.
 *
 * This does not make ground objects durable. Callers still own eligibility and claim transactions.
 * Callers with a persistent completion ID should supply a stable award identity across retries.
 */
@Singleton
public class NpcRewardDeliveryService
@Inject
constructor(
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
    private val marketPrices: MarketPrices,
    private val clans: ClanChatService,
) {
    /** Supplies native source/area context and injected entropy to deferred reward tables. */
    public fun rollArguments(npc: Npc): ArgMap =
        ArgMap(
            KillRollContext.npc with npc,
            KillRollContext.areaChecker with areaChecker,
            RollRandom.Context with GameDropRandom(random),
        )

    /**
     * Resolves a concrete reward on the game thread without emitting delivery effects.
     *
     * This consumes conditions and entropy once per call. A persistence caller must retain the
     * returned value across retries and encode its final item IDs using native account RSCM keys.
     * Eligibility and stable life/recipient identity remain the caller's responsibility.
     *
     * This is opt-in snapshot evaluation: callbacks do not observe intervening delivery hooks as
     * they can in [deliver]. Arbitrary callbacks may have their own effects. Invalid quantities or
     * missing item definitions fail preparation; no partially prepared value is returned.
     */
    public fun prepare(
        receiver: Player,
        npc: Npc,
        result: RollResult<DropRollItem>,
        coords: CoordGrid,
        extraDrops: List<DropRollItem> = emptyList(),
    ): PreparedNpcReward {
        val sourceNpcId = npc.id
        val sourceNpcKey = npc.type.internalName
        val duration = receiver.lootDropDuration ?: constants.lootdrop_duration
        val items = mutableListOf<PreparedNpcReward.Item>()
        val drops =
            when (result) {
                RollResult.Nothing -> emptyList()
                is RollResult.Single -> listOf(result.result)
                is RollResult.ListOf -> result.results
            }
        for (drop in drops + extraDrops) {
            prepareDrop(drop, coords, receiver, npc, items)
        }
        return PreparedNpcReward(sourceNpcId, sourceNpcKey, coords, duration, items)
    }

    private fun prepareDrop(
        drop: DropRollItem,
        coords: CoordGrid,
        receiver: Player,
        npc: Npc,
        items: MutableList<PreparedNpcReward.Item>,
    ) {
        if (drop.isNothing || !drop.condition(receiver)) return
        drop.killCondition?.let { if (!it(receiver, npc, areaChecker)) return }
        val obj = drop.transformObj(receiver) ?: drop.obj
        val count = drop.rollCount(random)
        require(count > 0) { "Prepared NPC reward quantity must be positive: $obj x$count" }
        val item = EnsouledHeadProvenance.fromMonsterDrop(InvObj(obj, count), coords)
        requireNotNull(ServerCacheManager.getItem(item.id)) {
            "Prepared NPC reward item is not in the native cache: ${item.id}"
        }
        items += PreparedNpcReward.Item(items.size, item)
        for (bonus in drop.bonusDrops) {
            prepareDrop(bonus, coords, receiver, npc, items)
        }
    }

    public fun deliver(
        receiver: Player,
        npc: Npc,
        result: RollResult<DropRollItem>,
        coords: CoordGrid,
        eventId: Int,
        awardIdentity: String = eventId.toString(),
        extraDrops: List<DropRollItem> = emptyList(),
    ) {
        val duration = receiver.lootDropDuration ?: constants.lootdrop_duration
        val awardOrdinal = intArrayOf(0)
        val drops =
            when (result) {
                RollResult.Nothing -> emptyList()
                is RollResult.Single -> listOf(result.result)
                is RollResult.ListOf -> result.results
            }
        for (drop in drops + extraDrops) {
            spawnDrop(drop, coords, duration, receiver, npc, eventId, awardOrdinal, awardIdentity)
        }
    }

    private fun spawnDrop(
        drop: DropRollItem,
        coords: CoordGrid,
        duration: Int,
        receiver: Player,
        npc: Npc,
        eventId: Int,
        awardOrdinal: IntArray,
        awardIdentity: String,
    ) {
        if (drop.isNothing) {
            return
        }
        if (!drop.condition(receiver)) {
            return
        }
        drop.killCondition?.let { killCondition ->
            if (!killCondition(receiver, npc, areaChecker)) {
                return
            }
        }
        val obj = drop.transformObj(receiver) ?: drop.obj
        val count = drop.rollCount(random)
        val dropped = EnsouledHeadProvenance.fromMonsterDrop(InvObj(obj, count), coords)
        val spawned = objRepo.add(dropped, coords, duration, receiver)
        ClientScripts.lootTrackerAddLoot(receiver, npc.id, eventId, spawned.type, spawned.count)
        val ordinal = awardOrdinal[0]++
        val stableDropId = "npc:${npc.type.internalName}:$awardIdentity:$ordinal"
        collectionLog.record(
            player = receiver,
            itemId = spawned.type,
            amount = spawned.count,
            source =
                CollectionLogAwardSource(CollectionLogAwardKind.NpcDrop, npc.type.internalName),
            eventId = "$awardIdentity:$ordinal",
        )
        val itemType = ServerCacheManager.getItem(spawned.type) ?: return
        val unitValue = (marketPrices[itemType] ?: 0).toLong().coerceAtLeast(0L)
        val totalValue = unitValue * spawned.count.toLong()
        clans.publishLootBroadcast(
            player = receiver,
            itemName = itemType.name,
            quantity = spawned.count,
            totalValue = totalValue,
            stableEventId = stableDropId,
        )
        for (bonus in drop.bonusDrops) {
            spawnDrop(bonus, coords, duration, receiver, npc, eventId, awardOrdinal, awardIdentity)
        }
    }
}
