package org.rsmod.content.bosses.muspah

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.instances.*
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.*
import org.rsmod.game.loc.*
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Spatial geometry and resource effects only; no ordinary attack selection or membership. */
internal class MuspahArenaEffects
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val world: WorldRepository,
    private val queues: WorldQueueList,
    private val corruption: CombatCorruption,
) {
    private val lineValidator = LineValidator(collision)

    fun spawnLightningCloud(run: MuspahArenaState, source: CoordGrid) {
        val center = arenaCenter(run.session)
        val dx = (center.x - source.x).coerceIn(-1, 1)
        val dz = (center.z - source.z).coerceIn(-1, 1)
        run.clouds +=
            LightningCloud(
                tile = source,
                deltaX = dx,
                deltaZ = dz,
                endCycle = clock.cycle + 18,
                nextMoveCycle = clock.cycle,
            )
    }

    fun tickClouds(run: MuspahArenaState) {
        val iterator = run.clouds.iterator()
        while (iterator.hasNext()) {
            val cloud = iterator.next()
            if (clock.cycle >= cloud.endCycle || !inArena(run.session, cloud.tile)) {
                iterator.remove()
                continue
            }
            if (clock.cycle >= cloud.nextMoveCycle) {
                cloud.tile = cloud.tile.translate(cloud.deltaX, cloud.deltaZ)
                cloud.nextMoveCycle++
                world.spotanimMap(spot(MuspahAssets.LIGHTNING), cloud.tile)
            }
            if (cloud.tile == run.player.coords && run.lastCloudHitCycle != clock.cycle) {
                run.lastCloudHitCycle = clock.cycle
                run.player.queueHit(
                    source = run.boss,
                    context = HitContext.of(HitAttribution(HitOrigin.Environment), run.hazardScope),
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..20),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    fun homingNextTile(run: MuspahArenaState, from: CoordGrid): CoordGrid {
        val primary =
            MuspahRules.nextOrthogonalStep(from.x, from.z, run.player.coords.x, run.player.coords.z)
                .let { CoordGrid(it.first, it.second, from.level) }
        if (validSpikeTile(run, primary)) return primary
        val dx = (run.player.coords.x - from.x).coerceIn(-1, 1)
        val dz = (run.player.coords.z - from.z).coerceIn(-1, 1)
        val alternate = if (primary.x != from.x) from.translate(0, dz) else from.translate(dx, 0)
        return alternate.takeIf { validSpikeTile(run, it) } ?: from
    }

    fun hardenMovingSpikes(run: MuspahArenaState) {
        for (moving in run.movingSpikes) {
            val tile = moving.loc.coords
            locs.del(moving.loc, Int.MAX_VALUE)
            spawnStaticSpike(run, tile)
        }
        run.movingSpikes.clear()
    }

    fun spawnCrack(run: MuspahArenaState, tile: CoordGrid) {
        if (!validSpikeTile(run, tile)) return
        world.spotanimMap(spot(MuspahAssets.SPIKE_WARNING), tile)
        val warning =
            locs.add(
                tile,
                MuspahAssets.SPIKE_PRE,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        run.warningSpikes += warning
        // Native encounter queue owns the replacement, not a re-entrant loc expiry callback.
        run.encounter.scheduleAt(queues, clock, clock.cycle + 2) {
            if (
                instances.muspahState(run.player) !== run ||
                    run.state in
                        setOf(MuspahState.DYING, MuspahState.RESPAWNING, MuspahState.INVALIDATED)
            )
                return@scheduleAt
            run.warningSpikes.remove(warning)
            locs.del(warning, Int.MAX_VALUE)
            spawnStaticSpike(run, tile)
            if (run.player.coords == tile) spikeHit(run)
        }
    }

    fun spawnStaticSpike(run: MuspahArenaState, tile: CoordGrid) {
        if (!validSpikeTile(run, tile)) return
        if (run.staticSpikes.containsKey(tile)) return
        val loc =
            locs.add(
                tile,
                MuspahAssets.SPIKE,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        run.staticSpikes[tile] = loc
    }

    fun spreadFinalSpikes(run: MuspahArenaState) {
        val existing = run.staticSpikes.keys.toList()
        for (tile in existing.take(6)) {
            val candidates =
                listOf(
                    tile.translateX(1),
                    tile.translateX(-1),
                    tile.translateZ(1),
                    tile.translateZ(-1),
                )
            val target = candidates.firstOrNull { validSpikeTile(run, it) }
            if (target != null) spawnCrack(run, target)
        }
    }

    fun spikeHit(run: MuspahArenaState) {
        val damage = random.of(0..20)
        run.player.queueHit(
            source = run.boss,
            context = HitContext.of(HitAttribution(HitOrigin.Environment), run.hazardScope),
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            impactHandler =
                org.rsmod.game.hit.HitImpactHandler { impact ->
                    run.boss.heal(MuspahRules.spikeHeal(impact.damage))
                },
        )
        val destination =
            adjacentTiles(run.player.coords).firstOrNull {
                inArena(run.session, it) &&
                    it !in run.staticSpikes &&
                    run.movingSpikes.none { moving -> moving.loc.coords == it }
            }
        if (destination != null) PathingEntityCommon.telejump(run.player, collision, destination)
    }

    fun cleanupHazards(run: MuspahArenaState) {
        run.hazardScope.invalidate()
        run.warningSpikes.forEach { locs.del(it, Int.MAX_VALUE) }
        run.warningSpikes.clear()
        run.staticSpikes.values.forEach { locs.del(it, Int.MAX_VALUE) }
        run.staticSpikes.clear()
        run.movingSpikes.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.movingSpikes.clear()
        run.clouds.clear()
    }

    fun arenaCenter(session: InstanceSession): CoordGrid = requireLocal(session, 31, 34)

    fun requireLocal(session: InstanceSession, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 44, 66, x, z)))

    fun inArena(session: InstanceSession, tile: CoordGrid): Boolean {
        val southWest = requireLocal(session, 17, 19)
        val northEast = requireLocal(session, 43, 49)
        return tile.level == southWest.level &&
            tile.x in southWest.x..northEast.x &&
            tile.z in southWest.z..northEast.z
    }

    fun validSpikeTile(run: MuspahArenaState, tile: CoordGrid): Boolean =
        inArena(run.session, tile) &&
            tile !in run.staticSpikes &&
            run.movingSpikes.none { it.loc.coords == tile } &&
            !bossOccupies(run, tile)

    fun bossOccupies(run: MuspahArenaState, tile: CoordGrid): Boolean =
        tile.x in run.boss.coords.x until (run.boss.coords.x + run.boss.size) &&
            tile.z in run.boss.coords.z until (run.boss.coords.z + run.boss.size)

    fun adjacentTiles(tile: CoordGrid): List<CoordGrid> =
        listOf(
            tile.translate(1, 1),
            tile.translate(-1, 1),
            tile.translate(1, -1),
            tile.translate(-1, -1),
            tile.translateX(1),
            tile.translateX(-1),
            tile.translateZ(1),
            tile.translateZ(-1),
        )

    fun spot(internal: String): SpotanimType = SpotanimType(internal.asRSCM(RSCMType.SPOTANIM))

    fun startHoming(run: MuspahArenaState): Boolean {
        val desiredSeedCount = 4
        for (offset in homingSeedOffsets) {
            if (run.staticSpikes.size >= desiredSeedCount) break
            spawnStaticSpike(run, run.player.coords.translate(offset.first, offset.second))
        }
        val available = run.staticSpikes.values.toMutableList()
        if (available.isEmpty()) return false
        val amount = random.of(1..4).coerceAtMost(available.size)
        repeat(amount) {
            val selected = available.removeAt(random.of(available.size))
            run.staticSpikes.remove(selected.coords)
            locs.del(selected, Int.MAX_VALUE)
            val moving =
                locs.add(
                    selected.coords,
                    MuspahAssets.SPIKE_FOLLOW,
                    Int.MAX_VALUE,
                    selected.angle,
                    selected.shape,
                )
            run.movingSpikes += MovingSpike(moving, clock.cycle + 2)
        }
        return true
    }

    fun moveHoming(run: MuspahArenaState) {
        for (moving in run.movingSpikes) {
            if (clock.cycle < moving.nextMoveCycle) continue
            moving.nextMoveCycle += 2
            val next = homingNextTile(run, moving.loc.coords)
            if (next == moving.loc.coords) continue
            locs.del(moving.loc, Int.MAX_VALUE)
            moving.loc =
                locs.add(
                    next,
                    MuspahAssets.SPIKE_FOLLOW,
                    Int.MAX_VALUE,
                    moving.loc.angle,
                    moving.loc.shape,
                )
            if (next == run.player.coords) spikeHit(run)
        }
    }

    fun shockwave(run: MuspahArenaState) {
        world.spotanimMap(spot(MuspahAssets.SHOCKWAVE), arenaCenter(run.session))
        val exposed =
            lineValidator.hasLineOfSight(
                level = run.boss.level,
                srcX = run.boss.coords.x,
                srcZ = run.boss.coords.z,
                destX = run.player.coords.x,
                destZ = run.player.coords.z,
                srcWidth = run.boss.size,
                srcLength = run.boss.size,
            )
        if (exposed) {
            run.player.queueHit(
                source = run.boss,
                context = HitContext.of(HitAttribution(HitOrigin.Environment), run.hazardScope),
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(0..80),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
            corruption.apply(run.player, strong = true)
        }
    }

    private val homingSeedOffsets = listOf(4 to 0, -4 to 0, 0 to 4, 0 to -4)
}
