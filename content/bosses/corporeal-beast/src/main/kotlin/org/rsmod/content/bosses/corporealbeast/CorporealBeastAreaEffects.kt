package org.rsmod.content.bosses.corporealbeast

import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Tile snapshot and six splitting projectiles, scheduled only on the owning native encounter. */
internal class CorporealBeastAreaEffects(private val deps: BossDeps) {
    fun launch(npc: Npc, target: Player, encounter: BossEncounter) {
        npc.actionDelay = deps.mapClock.cycle + encounter.expectedAttackRate()
        npc.facePlayer(target)
        npc.anim(CorporealBeastAssets.AREA_MAGIC_ANIMATION)
        val center = target.coords
        val main =
            ProjAnim.fromNpcToCoord(
                npc,
                center,
                CorporealBeastAssets.AREA_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.AREA_MAIN_PROJECTILE,
            )
        deps.worldRepo.projAnim(main)
        encounter.schedule(deps.worldQueues, main.serverCycles.coerceAtLeast(1)) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@schedule
            impactArea(npc, encounter, center, split = false)
            val candidates = CorporealBeastRules.splitCandidates(center)
            val start = deps.random.of(candidates.size)
            val destinations =
                (candidates.drop(start) + candidates.take(start)).take(
                    CorporealBeastRules.AREA_SPLIT_COUNT
                )
            destinations.forEach { destination ->
                val split =
                    coordProjectile(
                        center,
                        destination,
                        CorporealBeastAssets.AREA_IMPACT_SPOTANIM,
                        CorporealBeastAssets.AREA_SPLIT_PROJECTILE,
                    )
                deps.worldRepo.projAnim(split)
                encounter.schedule(deps.worldQueues, split.serverCycles.coerceAtLeast(1)) {
                    if (deps.encounterRegistry.isCurrent(npc, encounter)) {
                        impactArea(npc, encounter, destination, split = true)
                    }
                }
            }
        }
    }

    private fun impactArea(npc: Npc, encounter: BossEncounter, center: CoordGrid, split: Boolean) {
        deps.worldRepo.spotanimMap(SpotanimType(CorporealBeastAssets.AREA_IMPACT_SPOTANIM), center)
        corpRoomPlayers(deps.playerList, npc).forEach { target ->
            val distance = target.coords.chebyshevDistance(center)
            if (distance > 1) return@forEach
            val maximum = CorporealBeastRules.areaMaximum(direct = distance == 0, split = split)
            val accurate = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
            val damage = if (accurate) deps.random.of(maximum + 1) else 0
            target.queueHit(
                source = npc,
                delay = 1,
                type = HitType.Magic,
                damage = damage,
                modifier = NpcPartialProtectionPrayerPlayerHitModifier(33),
                validity = corpHitValidity(deps, encounter, target),
            )
        }
    }

    private fun coordProjectile(
        source: CoordGrid,
        target: CoordGrid,
        spotanim: Int,
        type: ProjAnimType,
    ): ProjAnim =
        ProjAnim(
            spotanim = spotanim,
            startHeight = type.startHeight,
            endHeight = type.endHeight,
            startTime = type.delay,
            endTime = ProjAnim.calculateEndTime(type, source.chebyshevDistance(target)),
            angle = type.angle,
            progress = type.progress,
            sourceIndex = 0,
            targetIndex = 0,
            startCoord = source,
            endCoord = target,
        )
}
