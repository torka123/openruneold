package org.rsmod.content.bosses.royaltitans

import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireLoc
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Paired arrival geometry and floor presentation; no ordinary combat or lifecycle authority. */
internal class RoyalTitansLandings
@Inject
constructor(
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val arena: RoyalTitansArena,
) {
    fun landingTelegraph(run: Run, element: RoyalElement) {
        val row = if (element == RoyalElement.FIRE) 35 else 25
        val gfx =
            if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_GFX
            else RoyalTitansAssets.ICE_WALL_ACTIVE_GFX
        // 2283 T1555/T1559: the visual is5x3, the eventual occupied footprint is3x3.
        for (z in row..row + 2) for (x in 29..33) {
            world.spotanimMap(SpotanimType(gfx), arena.local(run, x, z))
        }
    }

    fun resolveTitanLanding(run: Run, element: RoyalElement) {
        arena.online(run).forEach { player ->
            if (arena.inTitanLanding(run, player.coords, element)) {
                arena.hit(
                    run,
                    player,
                    random.of(16) + 15,
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.landing",
                )
                PathingEntityCommon.telejump(player, collision, arena.local(run, 31, 30))
            }
        }
    }

    fun spawnBrandaArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_FLOOR_WEST)
        spawnTitanFootprintBlockers(run, 35, 30..31)
        run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_FLOOR_EAST)
        spawnTitanFootprintBlockers(run, 35, 32..32)
    }

    fun spawnEldricArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_FLOOR_WEST)
        spawnTitanFootprintBlockers(run, 25, 30..31)
        run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_FLOOR_EAST)
        spawnTitanFootprintBlockers(run, 25, 32..32)
    }

    private fun spawnTitanFootprintBlockers(run: Run, row: Int, columns: IntRange) {
        for (x in columns) for (z in row..row + 2) {
            run.arenaBlockers += spawnLoc(run, x, z, 26209)
        }
    }

    fun replaceArenaFloors(run: Run, element: RoyalElement) {
        val relevant =
            run.arenaFloors.filter {
                if (element == RoyalElement.FIRE) arena.localZ(run, it.coords) == 35
                else arena.localZ(run, it.coords) == 25
            }
        relevant.forEach {
            locs.del(it, Int.MAX_VALUE)
            run.arenaFloors.remove(it)
        }
        if (element == RoyalElement.FIRE) {
            run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_EAST)
        } else {
            run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_EAST)
        }
    }

    private fun spawnLoc(run: Run, x: Int, z: Int, id: Int): LocInfo =
        locs.add(
            arena.local(run, x, z),
            requireLoc(id),
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )

    fun clearArenaFloors(run: Run) {
        run.arenaFloors.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.arenaFloors.clear()
        run.arenaBlockers.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.arenaBlockers.clear()
    }
}
