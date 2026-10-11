package org.rsmod.content.bosses.royaltitans

import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.anim
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireLoc
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.spot
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.PendingWall
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.WallSegment
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape

/** Wall geometry/presentation only; phase readiness and room ownership remain native. */
internal class RoyalTitansWalls
@Inject
constructor(
    private val clock: MapClock,
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val random: GameRandom,
    private val arena: RoyalTitansArena,
) {
    fun castWall(run: Run, titan: Npc, element: RoyalElement) {
        val target =
            arena.online(run).minByOrNull { arena.distance(it.coords, titan.coords) } ?: return
        titan.faceSquare(target.coords)
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_WALL_CAST
                else RoyalTitansAssets.ELDRIC_WALL_CAST
            )
        )
        titan.spotanim(spot(RoyalTitansAssets.WALL_CAST_GFX))
        val row = arena.localZ(run, target.coords).coerceIn(27, 34)
        (0..4).forEach { index ->
            val tile = arena.local(run, 29 + index, row)
            world.spotanimMap(
                SpotanimType(
                    if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_START_GFX
                    else RoyalTitansAssets.ICE_WALL_START_GFX
                ),
                tile,
            )
            world.soundArea(
                tile,
                if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_FORM_SOUND
                else RoyalTitansAssets.ICE_WALL_FORM_SOUND,
                delay = 5,
                radius = 1,
            )
            world.soundArea(
                tile,
                if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_SOUND
                else RoyalTitansAssets.ICE_WALL_ACTIVE_SOUND,
                delay = if (element == RoyalElement.FIRE) 120 else 122,
                radius = 1,
            )
        }
        run.pendingWalls +=
            PendingWall(clock.cycle + RoyalTitansRules.WALL_TELEGRAPH_TICKS, element, row)
    }

    private fun formWall(run: Run, pending: PendingWall) {
        (0..4).forEach { index ->
            val tile = arena.local(run, 29 + index, pending.row)
            val occupant = arena.online(run).firstOrNull { it.coords == tile }
            if (occupant != null) {
                arena.hit(
                    run,
                    occupant,
                    random.of(RoyalTitansRules.WALL_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.wall_formation",
                )
                return@forEach
            }
            run.elementals.entries
                .filter { it.key.coords == tile && it.value.element != pending.element }
                .map { it.key }
                .forEach { it.queueDeath() }
            val loc =
                locs.add(
                    tile,
                    requireLoc(
                        if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL
                        else RoyalTitansAssets.ICE_WALL
                    ),
                    Int.MAX_VALUE,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
            val hitbox =
                arena.spawnNpc(
                    run,
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_HITBOX
                    else RoyalTitansAssets.ICE_WALL_HITBOX,
                    tile,
                    hitpoints = 1,
                    movementLocked = true,
                )
            run.walls +=
                WallSegment(
                    pending.element,
                    pending.row,
                    index,
                    loc,
                    hitbox,
                    clock.cycle + RoyalTitansRules.WALL_LIFETIME_TICKS,
                )
            world.spotanimMap(
                SpotanimType(
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_GFX
                    else RoyalTitansAssets.ICE_WALL_ACTIVE_GFX
                ),
                tile,
            )
        }
    }

    fun expireWalls(run: Run) {
        val pending = run.pendingWalls.iterator()
        while (pending.hasNext()) {
            val wall = pending.next()
            if (clock.cycle < wall.formCycle) continue
            pending.remove()
            formWall(run, wall)
        }
        run.walls.filter { clock.cycle >= it.expireCycle }.toList().forEach { clearWall(run, it) }
    }

    fun clearWallTriplet(run: Run, wall: WallSegment) {
        run.walls
            .filter {
                it.element == wall.element &&
                    it.row == wall.row &&
                    it.index in RoyalTitansRules.removedWallIndices(wall.index)
            }
            .sortedBy { if (it.index == wall.index) 0 else if (it.index < wall.index) 1 else 2 }
            .toList()
            .forEach { clearWall(run, it, playerRemoved = true) }
    }

    private fun clearWall(run: Run, wall: WallSegment, playerRemoved: Boolean = false) {
        if (!run.walls.remove(wall)) return
        locs.del(wall.loc, Int.MAX_VALUE)
        arena.removeNpc(run, wall.hitbox)
        world.spotanimMap(
            SpotanimType(
                if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_END_GFX
                else RoyalTitansAssets.ICE_WALL_END_GFX
            ),
            wall.loc.coords,
        )
        if (playerRemoved) return
        world.soundArea(
            wall.loc.coords,
            if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_BREAK_SOUND
            else RoyalTitansAssets.ICE_WALL_BREAK_SOUND,
            delay = if (wall.element == RoyalElement.FIRE) 0 else 4,
            radius = 1,
        )
    }

    fun clearWalls(run: Run) {
        run.pendingWalls.clear()
        run.walls.toList().forEach { clearWall(run, it) }
    }
}
