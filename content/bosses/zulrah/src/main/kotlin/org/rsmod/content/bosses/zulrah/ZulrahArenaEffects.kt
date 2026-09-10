package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.instances.*
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.*
import org.rsmod.game.loc.*
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants

/** Spatial effects only. Deadlines and cancellation belong to the native BossEncounter queue. */
internal class ZulrahArenaEffects
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val world: WorldRepository,
    private val queues: WorldQueueList,
    private val encounters: EncounterRegistry,
) {
    fun launch(run: ZulrahArenaState, action: Any) {
        when (action) {
            is ZulrahAction.VenomCloudVolley -> launchCloudVolley(run, action)
            is ZulrahAction.SnakelingVolley -> launchSnakelingVolley(run, action)
            is ZulrahTail -> launchTail(run, action)
            else -> error("Unknown Zulrah arena action: $action")
        }
    }

    private fun live(run: ZulrahArenaState): Boolean =
        instances.zulrahState(run.player) === run &&
            run.player.hitpoints > 0 &&
            run.player.isSlotAssigned &&
            !run.player.pendingLogout &&
            !run.player.loggingOut &&
            run.state in setOf(ZulrahState.ACTIVE, ZulrahState.DIVING, ZulrahState.EMERGING)

    fun clear(run: ZulrahArenaState) {
        run.hazardScope.invalidate()
        run.clouds.forEach { world.locDelPrivate(run.player, it.visual) }
        run.clouds.clear()
        run.snakelings.keys.toList().forEach { npc ->
            run.encounter.disown(npc)
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        }
        run.snakelings.clear()
    }

    private fun launchCloudVolley(run: ZulrahArenaState, action: ZulrahAction.VenomCloudVolley) {
        val primary = localCoord(run.session, action.primary)
        val secondary = localCoord(run.session, action.secondary)
        val primaryProjectileTarget =
            localCoord(run.session, ZulrahRules.cloudOrigin(action.primary))
        val secondaryProjectileTarget =
            localCoord(run.session, ZulrahRules.cloudOrigin(action.secondary))
        val midpoint =
            localCoord(
                run.session,
                ShrineTile(
                    (action.primary.x + action.secondary.x) / 2,
                    (action.primary.z + action.secondary.z) / 2,
                ),
            )
        run.boss.faceSquare(midpoint)
        run.boss.anim(ZulrahAssets.SUMMON)
        run.player.soundSynth(ZULRAH_CLOUD_SEND_SOUND)
        val primaryProjectile =
            launchTileProjectile(
                run.boss,
                primaryProjectileTarget,
                ZulrahAssets.CLOUD_PROJECTILE,
                ZulrahAssets.CLOUD_PRIMARY_PROJECTILE_CONTROLLER,
            )
        val secondaryProjectile =
            launchTileProjectile(
                run.boss,
                secondaryProjectileTarget,
                ZulrahAssets.CLOUD_PROJECTILE,
                ZulrahAssets.CLOUD_SECONDARY_PROJECTILE_CONTROLLER,
            )
        run.encounter.scheduleAt(
            queues,
            clock,
            clock.cycle + primaryProjectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
        ) {
            if (live(run)) spawnCloud(run, primary)
        }
        run.encounter.scheduleAt(
            queues,
            clock,
            clock.cycle + secondaryProjectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
        ) {
            if (live(run)) spawnCloud(run, secondary)
        }
    }

    private fun launchSnakelingVolley(run: ZulrahArenaState, action: ZulrahAction.SnakelingVolley) {
        val tile = localCoord(run.session, action.target)
        run.boss.faceSquare(tile)
        run.boss.anim(ZulrahAssets.SUMMON)
        run.player.soundSynth(ZULRAH_SNAKELING_SEND_SOUND)
        val projectile =
            launchTileProjectile(
                run.boss,
                tile,
                ZulrahAssets.SNAKELING_PROJECTILE,
                ZulrahAssets.SNAKELING_EGG_PROJECTILE_CONTROLLER,
            )
        run.encounter.scheduleAt(
            queues,
            clock,
            clock.cycle + projectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
        ) {
            if (live(run)) spawnSnakeling(run, tile)
        }
    }

    private fun launchTail(run: ZulrahArenaState, action: ZulrahTail) {
        val target = run.player.coords
        run.boss.faceSquare(target)
        run.boss.anim(if (action.left) ZulrahAssets.TAIL_LEFT else ZulrahAssets.TAIL_RIGHT)
        run.encounter.scheduleAt(queues, clock, clock.cycle + ZULRAH_TAIL_WINDUP_TICKS) {
            if (live(run)) tailImpact(run, target)
        }
    }

    private fun tailImpact(run: ZulrahArenaState, tile: CoordGrid) {
        val onSafespot =
            ZULRAH_TAIL_SAFESPOTS.any { safe -> localCoord(run.session, safe) == run.player.coords }
        val hit =
            ZulrahRules.tailDamage(
                !onSafespot &&
                    ZulrahRules.inTailTarget(
                        run.player.coords.x,
                        run.player.coords.z,
                        tile.x,
                        tile.z,
                    ),
                roll(run, 11),
            )
        if (hit > 0) {
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = hit,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = HitValidity { live(run) },
                context = HitContext.of(HitAttribution(HitOrigin.Environment), run.hazardScope),
                impactHandler =
                    HitImpactHandler { impact ->
                        if (instances.zulrahState(run.player) === run && impact.damage > 0) {
                            PlayerVenom.tryVenom(run.player)
                        }
                    },
            )
            run.player.applyShoveStun(stunCycles = 5, postStunImmunityCycles = 1)
        }
    }

    private fun spawnCloud(run: ZulrahArenaState, tile: CoordGrid) {
        val existing = run.clouds.firstOrNull { it.center == tile }
        if (existing != null) {
            world.locDelPrivate(run.player, existing.visual)
            run.clouds.remove(existing)
        }
        val origin = CoordGrid(tile.x - 1, tile.z - 1, tile.level)
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        val visual =
            LocInfo(
                LocLayerConstants.of(shape.id),
                origin,
                LocEntity(ZulrahAssets.CLOUD_LOC.asRSCM(RSCMType.LOC), shape.id, angle.id),
            )
        world.locAddPrivate(run.player, visual)
        run.player.soundSynth(ZULRAH_HAZARD_SPAWN_SOUND)
        run.clouds += ActiveCloud(tile, clock.cycle + ZULRAH_CLOUD_LIFETIME, visual)
    }

    fun tick(run: ZulrahArenaState) {
        val expired = run.clouds.filter { it.endCycle <= clock.cycle }
        if (expired.isNotEmpty()) {
            expired.forEachIndexed { index, cloud ->
                world.locDelPrivate(run.player, cloud.visual)
                run.player.soundSynth(
                    if (index % 2 == 0) ZULRAH_CLOUD_DISSIPATE_SOUND_A
                    else ZULRAH_CLOUD_DISSIPATE_SOUND_B
                )
            }
            run.clouds.removeAll(expired.toSet())
        }
        val exposed =
            run.clouds.any { cloud -> run.player.coords.chebyshevDistance(cloud.center) <= 1 }
        if (!exposed || run.state in setOf(ZulrahState.DYING, ZulrahState.COMPLETED)) return
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = roll(run, 4) + 1,
            hitmark = hitmark_groups.venom,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            validity = HitValidity { live(run) },
            context = HitContext.of(HitAttribution(HitOrigin.Environment), run.hazardScope),
        )
    }

    private fun spawnSnakeling(run: ZulrahArenaState, tile: CoordGrid) {
        val magic = roll(run, 3) == 0
        val npc =
            spawnNpc(
                run.player,
                run.session,
                if (magic) ZulrahAssets.SNAKELING_MAGIC else ZulrahAssets.SNAKELING_MELEE,
                tile,
            )
        npc.baseHitpointsLvl = 1
        npc.hitpoints = 1
        npc.apRangeOverride = if (magic) 8 else 1
        npc.apRequiresLineOfSight = true
        npc.actionDelay = clock.cycle + ZULRAH_SNAKELING_SPAWN_LOCK_TICKS
        npc.anim(ZulrahAssets.SNAKELING_ATTACK)
        run.player.soundSynth(ZULRAH_HAZARD_SPAWN_SOUND)
        run.snakelings[npc] = magic
        run.encounter.own(npc)
        val lifetime = encounters.of(npc)
        lifetime.onCleanup {
            run.snakelings.remove(npc)
            run.encounter.disown(npc)
        }
        lifetime.scheduleAt(queues, clock, clock.cycle + ZULRAH_SNAKELING_LIFETIME) {
            if (run.snakelings.remove(npc) != null) {
                run.encounter.disown(npc)
                if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
            }
        }
        npc.apPlayer2(run.player, interactions)
    }

    private fun spawnNpc(
        player: Player,
        session: InstanceSession,
        internal: String,
        coords: CoordGrid,
    ): Npc {
        val npc = Npc(requireNpc(internal), coords)
        npc.mode = null
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, ZULRAH_NPC_LIFETIME)
        npc.respawns = false
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun localCoord(session: InstanceSession, tile: ShrineTile): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(0, tile.x shr 6, tile.z shr 6, tile.x and 63, tile.z and 63),
            )
        )

    private fun launchTileProjectile(
        source: Npc,
        tile: CoordGrid,
        graphic: String,
        controller: String,
    ): ProjAnim {
        val projectile =
            ProjAnim.fromBoundsToCoord(
                source.bounds(),
                tile,
                graphic.asRSCM(RSCMType.SPOTANIM),
                controller,
            )
        world.projAnim(projectile)
        return projectile
    }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Zulrah npc definition: $internal"
        }

    private fun roll(run: ZulrahArenaState, bound: Int): Int = run.selectionRandom.of(bound)
}
