package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.core.with
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.NpcKillRewardContexts
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

@Singleton
public class NpcDropTableKillHook
@Inject
constructor(
    private val registry: DropTableRegistry,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
    private val marketPrices: MarketPrices,
    private val clans: ClanChatService,
    private val dt2AwakenedCompletions: Dt2AwakenedCompletionService,
) : NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.suppressDrops) return
        val table = registry.forNpc(context.npc, areaChecker) ?: return

        val player = context.hero
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        val dropCoords = context.dropCoords

        val npc = context.npc
        val awardOrdinal = intArrayOf(0)
        val result =
            table
                .roll(
                    player,
                    ArgMap(
                        KillRollContext.npc with npc,
                        KillRollContext.areaChecker with areaChecker,
                    ),
                )
                .flatten()
        val awakenedUpdate =
            NpcKillRewardContexts[npc]?.let { rewardContext ->
                Dt2AwakenedBoss.fromNpc(npc)?.let { boss ->
                    dt2AwakenedCompletions.record(player, boss, rewardContext)
                }
            }

        when (result) {
            is RollResult.Nothing -> Unit
            is RollResult.Single ->
                spawnDrop(
                    result.result,
                    dropCoords,
                    duration,
                    player,
                    npc,
                    context.lootTrackerEventId,
                    awardOrdinal,
                )
            is RollResult.ListOf ->
                result.results.forEach { drop ->
                    spawnDrop(
                        drop,
                        dropCoords,
                        duration,
                        player,
                        npc,
                        context.lootTrackerEventId,
                        awardOrdinal,
                    )
                }
        }
        if (awakenedUpdate?.awardKit == true) {
            spawnDrop(
                DropRollItem(Dt2AwakenedCompletionRules.KIT_OBJ, 1),
                dropCoords,
                duration,
                player,
                npc,
                context.lootTrackerEventId,
                awardOrdinal,
            )
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
        val stableDropId = "npc:${npc.type.internalName}:$eventId:$ordinal"
        collectionLog.record(
            player = receiver,
            itemId = spawned.type,
            amount = spawned.count,
            source =
                CollectionLogAwardSource(CollectionLogAwardKind.NpcDrop, npc.type.internalName),
            eventId = "$eventId:$ordinal",
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
            spawnDrop(bonus, coords, duration, receiver, npc, eventId, awardOrdinal)
        }
    }
}
