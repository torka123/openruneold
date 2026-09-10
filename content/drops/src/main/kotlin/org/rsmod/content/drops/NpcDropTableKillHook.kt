package org.rsmod.content.drops

import dtx.core.ArgMap
import dtx.core.flatten
import dtx.core.with
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.NpcKillRewardContexts

@Singleton
public class NpcDropTableKillHook
@Inject
constructor(
    private val registry: DropTableRegistry,
    private val areaChecker: AreaChecker,
    private val rewards: NpcRewardDeliveryService,
    private val dt2AwakenedCompletions: Dt2AwakenedCompletionService,
) : NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.suppressDrops) return
        val table = registry.forNpc(context.npc, areaChecker) ?: return

        val player = context.hero
        val dropCoords = context.dropCoords

        val npc = context.npc
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

        rewards.deliver(
            receiver = player,
            npc = npc,
            result = result,
            coords = dropCoords,
            eventId = context.lootTrackerEventId,
            extraDrops =
                if (awakenedUpdate?.awardKit == true) {
                    listOf(DropRollItem(Dt2AwakenedCompletionRules.KIT_OBJ, 1))
                } else emptyList(),
        )
    }
}
