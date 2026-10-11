package org.rsmod.content.bosses.vorkath

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.MoveRestrict
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.get
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Collision-sensitive summoned NPC movement/contact; no independent fight or membership loop. */
internal class VorkathSpawnEffects(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val world: WorldRepository,
    private val arena: VorkathArenaEffects,
) {
    private val log = InlineLogger()
    private val spawnSteps = StepFactory(collision)

    fun beginSpawn(run: VorkathArenaState) {
        run.boss.anim(VorkathAssets.RANGED_ANIM)
        val ice = VorkathProjectiles.ICE.build(run.boss, run.player.coords, run.player)
        world.projAnim(ice)
        run.player.spotanim(369, delay = ice.endTime, height = 0)
        run.freezeCycle = clock.cycle + ice.endTime / 30
        run.spawnLaunchCycle = run.freezeCycle
    }

    fun tickSpawn(run: VorkathArenaState) {
        if (clock.cycle >= run.freezeCycle) {
            run.freezeCycle = Int.MAX_VALUE
            // This bind belongs to the special; PvP freeze immunity must not prevent it.
            run.player.clearQueue("queue.com_retaliate_npc")
            run.player.clearInteraction()
            CombatEffects.breakBind(run.player)
            run.player.applyBind(Int.MAX_VALUE - clock.cycle)
            run.player.frozen = true
            run.ownsFreeze = true
            run.player.mes("You have been frozen!")
        }
        if (clock.cycle >= run.spawnLaunchCycle) {
            run.spawnLaunchCycle = Int.MAX_VALUE
            val candidates =
                arenaTiles(run.session).filter {
                    it.chebyshevDistance(run.player.coords) == VORKATH_SPAWN_DISTANCE &&
                        !bossOccupies(run, it) &&
                        collision[it] and CollisionFlag.BLOCK_WALK == 0 &&
                        spawnCanReach(run, it)
                }
            if (candidates.isEmpty()) {
                log.error {
                    "[Vorkath] no valid spawn location at distance $VORKATH_SPAWN_DISTANCE for ${run.session.id}"
                }
                arena.finishSpecial(run)
                return
            }
            val tile = candidates[random.of(candidates.size)]
            run.boss.anim(VorkathAssets.RANGED_UP_ANIM)
            arena.launchTileProjectile(run, tile, VorkathProjectiles.SPAWN)
            run.pendingSpawnTile = tile
            run.spawnArrivalCycle = clock.cycle + 4
        }
        val pendingTile = run.pendingSpawnTile
        if (pendingTile != null && clock.cycle >= run.spawnArrivalCycle) {
            val spawn = arena.spawnNpc(run.player, run.session, VorkathAssets.SPAWN, pendingTile)
            spawn.baseHitpointsLvl = 38
            spawn.hitpoints = 38
            spawn.mode = null
            run.spawn = spawn
            run.pendingSpawnTile = null
            return // First walk is the tick after arrival.
        }
        val spawn = run.spawn ?: return
        if (!spawn.isSlotAssigned) {
            arena.finishSpecial(run)
            return
        }
        if (spawn.hitpoints <= 0) {
            if (clock.cycle >= run.spawnDeathCycle) {
                spawn.anim(VorkathAssets.SPAWN_DEATH_ANIM)
                run.retiringSpawns += spawn
                run.spawn = null
                run.spawnRetireCycle = clock.cycle + 2
                arena.schedule(run, run.spawnRetireCycle) {
                    run.retiringSpawns.remove(spawn)
                    if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
                    run.spawnRetireCycle = Int.MAX_VALUE
                }
                arena.finishSpecial(run)
            }
            return
        }
        if (clock.cycle >= run.spawnExplosionCycle) {
            run.spawnExplosionCycle = Int.MAX_VALUE
            val damage = VorkathRules.zombifiedSpawnDamage(spawn.hitpoints)
            run.player.queueHit(
                source = spawn,
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = arena.hitValidity(run),
            )
            // Captures remove the NPC on the explosion's damage tick (contact +1).
            if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
            run.spawn = null
            arena.finishSpecial(run, recoveryTicks = 0)
        } else if (run.spawnExplosionCycle != Int.MAX_VALUE) {
            return
        } else if (spawn.coords.chebyshevDistance(run.player.coords) == 0) {
            beginSpawnExplosion(run, spawn, contactCycle = clock.cycle)
        } else {
            spawn.facePlayer(run.player)
            // Arrival is processed after movement and player hits, before NPC/world updates.
            // The map clock has advanced by then, so retain this movement tick's cycle.
            val movementCycle = clock.cycle
            val next = nextSpawnStep(run, spawn.coords)
            if (next == CoordGrid.NULL) return
            // Only the final step may overlap the target. Captures retain boss-body clipping.
            spawn.moveRestrict =
                if (next == run.player.coords) MoveRestrict.PassThru else MoveRestrict.Normal
            spawn.walk(next) { beginSpawnExplosion(run, spawn, contactCycle = movementCycle) }
        }
    }

    fun nextSpawnStep(run: VorkathArenaState, source: CoordGrid): CoordGrid {
        val target = run.player.coords
        if (source == target) return target
        if (source.chebyshevDistance(target) == 1 && !bossOccupies(run, target)) {
            val diagonal = source.x != target.x && source.z != target.z
            val crossesBossCorner =
                diagonal &&
                    (bossOccupies(run, CoordGrid(source.x, target.z, source.level)) ||
                        bossOccupies(run, CoordGrid(target.x, source.z, source.level)))
            if (!crossesBossCorner && spawnSteps.validated(source, target) == target) return target
        }
        return spawnSteps.validated(source, target, extraFlag = CollisionFlag.BLOCK_NPCS)
    }

    fun spawnCanReach(run: VorkathArenaState, source: CoordGrid): Boolean {
        // The ring and edge-clipping are captured; rejecting stuck direct routes is inferred.
        var tile = source
        repeat(VORKATH_SPAWN_DISTANCE * 2) {
            if (tile == run.player.coords) return true
            tile = nextSpawnStep(run, tile)
            if (tile == CoordGrid.NULL) return false
        }
        return tile == run.player.coords
    }

    private fun beginSpawnExplosion(run: VorkathArenaState, spawn: Npc, contactCycle: Int) {
        if (
            !arena.validTarget(run) ||
                run.state != VorkathState.ZOMBIFIED_SPAWN_SPECIAL ||
                run.spawn !== spawn ||
                !spawn.isSlotAssigned ||
                spawn.hitpoints <= 0 ||
                run.spawnExplosionCycle != Int.MAX_VALUE ||
                spawn.coords != run.player.coords
        )
            return
        spawn.anim(VorkathAssets.SPAWN_ATTACK_ANIM)
        world.spotanimMap(
            SpotanimType(VorkathAssets.SPAWN_EXPLOSION),
            spawn.coords,
            height = 30,
            delay = 22,
        )
        releaseFreeze(run)
        spawn.movementLocked = true
        spawn.abortRoute()
        run.spawnExplosionCycle = contactCycle + 1
    }

    fun releaseFreeze(run: VorkathArenaState) {
        run.freezeCycle = Int.MAX_VALUE
        if (!run.ownsFreeze) return
        CombatEffects.breakBind(run.player)
        run.player.resetSpotanim()
        run.ownsFreeze = false
    }

    fun clearSpawn(run: VorkathArenaState) {
        run.pendingSpawnTile = null
        run.spawnLaunchCycle = Int.MAX_VALUE
        run.spawnArrivalCycle = Int.MAX_VALUE
        run.spawnExplosionCycle = Int.MAX_VALUE
        run.spawnDeathCycle = Int.MAX_VALUE
        val spawn = run.spawn ?: return
        run.spawn = null
        if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
    }

    private fun arenaTiles(session: InstanceSession): List<CoordGrid> {
        val tiles = ArrayList<CoordGrid>(550)
        // All four bounds are witnessed by the supplied spawn launches.
        for (x in 21..43) {
            for (z in 22..44) {
                instances.localCoord(session, RegionLocal(0, 35, 63, x, z))?.let(tiles::add)
            }
        }
        return tiles
    }

    private fun bossOccupies(run: VorkathArenaState, tile: CoordGrid): Boolean =
        tile.x in run.boss.coords.x until (run.boss.coords.x + run.boss.size) &&
            tile.z in run.boss.coords.z until (run.boss.coords.z + run.boss.size)
}
