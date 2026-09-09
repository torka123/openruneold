package org.rsmod.content.bosses.vorkath

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Arena-specific fireball, acid and zombified-spawn effects. No ordinary attack selection, instance
 * membership, phase engine or private deadline queue: native scopes own every callback.
 */
@Singleton
internal class VorkathArenaEffects
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val world: WorldRepository,
) {
    private val log = InlineLogger()
    internal val spawns =
        VorkathSpawnEffects(clock, instances, npcs, collision, random, world, this)

    internal fun schedule(run: VorkathArenaState, tick: Int, action: () -> Unit) {
        run.combat.scheduleAt(deps.worldQueues, clock, tick) { if (validTarget(run)) action() }
    }

    private fun scheduleSpecialStep(run: VorkathArenaState) {
        schedule(run, clock.cycle + 1) {
            when (run.state) {
                VorkathState.ACID_SPECIAL -> tickAcid(run)
                VorkathState.ZOMBIFIED_SPAWN_SPECIAL -> spawns.tickSpawn(run)
                else -> return@schedule
            }
            if (
                run.state == VorkathState.ACID_SPECIAL ||
                    run.state == VorkathState.ZOMBIFIED_SPAWN_SPECIAL
            )
                scheduleSpecialStep(run)
        }
    }

    fun launchFireball(run: VorkathArenaState) {
        if (!validTarget(run)) return
        run.boss.actionDelay = clock.cycle + VORKATH_ATTACK_RATE
        run.boss.anim(VorkathAssets.RANGED_UP_ANIM, delay = 2)
        val launch = PendingFireball(clock.cycle + 1)
        run.pendingFireballs += launch
        schedule(run, launch.launchCycle) {
            run.pendingFireballs.remove(launch)
            val tile = run.player.coords
            launchTileProjectile(run, tile, VorkathProjectiles.FIREBALL)
            scheduleTileHit(
                run,
                PendingTileHit(
                    clock.cycle + VorkathProjectiles.FIREBALL.impactTicks,
                    tile,
                    0,
                    121,
                    VorkathTileAttack.FIREBALL,
                ),
            )
        }
    }

    fun beginSpecial(run: VorkathArenaState) {
        if (!validTarget(run)) return
        val special = run.nextSpecial
        run.nextSpecial = VorkathRules.nextSpecial(special)
        run.shotsFired = 0
        run.specialStartCycle = clock.cycle
        run.state =
            if (special == VorkathSpecial.ACID) VorkathState.ACID_SPECIAL
            else VorkathState.ZOMBIFIED_SPAWN_SPECIAL
        when (special) {
            VorkathSpecial.ACID -> beginAcid(run)
            VorkathSpecial.ZOMBIFIED_SPAWN -> spawns.beginSpawn(run)
        }
        run.boss.mode = null
        scheduleSpecialStep(run)
    }

    private fun beginAcid(run: VorkathArenaState) {
        run.boss.anim(VorkathAssets.ACID_ANIM)
        clearAcid(run)
        val selected =
            VorkathAcidLayout.select(
                boss = run.boss.coords,
                player = run.player.coords,
                isWalkable = { collision[it] and CollisionFlag.BLOCK_WALK == 0 },
                choose = { it[random.of(it.size)] },
            )
        run.acidSafeLane +=
            VorkathAcidLayout.exitLane(run.boss.coords).filter {
                it !in selected && collision[it] and CollisionFlag.BLOCK_WALK == 0
            }
        selected.forEach { tile ->
            launchTileProjectile(run, tile, VorkathProjectiles.ACID)
            val pool = PendingAcidPool(clock.cycle + 3, tile)
            run.pendingAcidPools += pool
            schedule(run, pool.impactCycle) {
                if (run.pendingAcidPools.remove(pool)) formAcidPool(run, pool)
            }
        }
    }

    private fun tickAcid(run: VorkathArenaState) {
        if (run.player.coords in run.acidTiles) {
            val damage = random.of(1..10)
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                impactHandler =
                    org.rsmod.game.hit.HitImpactHandler { impact ->
                        val heal = (clock.cycle + 1) to impact.damage
                        run.pendingHeals += heal
                        schedule(run, heal.first) {
                            run.pendingHeals.remove(heal)
                            run.boss.heal(heal.second, showHitsplat = true)
                        }
                    },
                validity = hitValidity(run),
            )
        }
        if (
            run.shotsFired < VORKATH_ACID_SHOTS &&
                clock.cycle >= run.specialStartCycle + 4 + run.shotsFired
        ) {
            val tile = run.player.coords
            run.boss.facePlayer(run.player)
            val delay = launchTileProjectile(run, tile, VorkathProjectiles.RAPID_FIRE)
            scheduleTileHit(
                run,
                PendingTileHit(
                    impactCycle = clock.cycle + delay,
                    tile = tile,
                    minimum = 25,
                    maximum = 41,
                    kind = VorkathTileAttack.RAPID_FIRE,
                ),
            )
            run.shotsFired++
            if (run.shotsFired == VORKATH_ACID_SHOTS) clearAcid(run)
        }
        if (clock.cycle >= run.specialStartCycle + 33) finishSpecial(run, recoveryTicks = 0)
    }

    private fun scheduleTileHit(run: VorkathArenaState, hit: PendingTileHit) {
        // Diagnostic metadata only: execution/cancellation belongs to BossEncounter.scheduleAt.
        run.pendingHits += hit
        schedule(run, hit.impactCycle) {
            run.pendingHits.remove(hit)
            val graphic =
                if (hit.kind == VorkathTileAttack.FIREBALL) VorkathAssets.FIREBALL_IMPACT
                else VorkathAssets.RAPID_FIRE_IMPACT
            val height = if (hit.kind == VorkathTileAttack.FIREBALL) 38 else 30
            world.spotanimMap(SpotanimType(graphic), hit.tile, height)
            if (hit.kind == VorkathTileAttack.FIREBALL)
                world.soundArea(hit.tile, VorkathAssets.FIREBALL_IMPACT_SOUND, radius = 12)
            else world.soundArea(hit.tile, VorkathAssets.RAPID_FIRE_IMPACT_SOUND, radius = 12)
            val distance = run.player.coords.chebyshevDistance(hit.tile)
            val maximum =
                when (hit.kind) {
                    VorkathTileAttack.FIREBALL -> VorkathRules.fireballMaximum(distance)
                    VorkathTileAttack.RAPID_FIRE -> if (distance == 0) hit.maximum else 0
                }
            if (maximum <= 0) return@schedule
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    if (hit.kind == VorkathTileAttack.FIREBALL)
                        VorkathRules.fireballDamage(random.of(0..hit.maximum), distance)
                    else random.of(hit.minimum..maximum),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = hitValidity(run),
            )
        }
    }

    private fun formAcidPool(run: VorkathArenaState, pool: PendingAcidPool) {
        if (pool.tile in run.acidTiles) return
        val visual =
            locs.add(
                pool.tile,
                acidPoolType(),
                Int.MAX_VALUE,
                LocAngle[random.of(4)],
                LocShape.CentrepieceStraight,
            )
        run.acidTiles += pool.tile
        run.acidVisuals += visual
    }

    internal fun finishSpecial(run: VorkathArenaState, recoveryTicks: Int = 1) {
        clearAcid(run)
        spawns.clearSpawn(run)
        spawns.releaseFreeze(run)
        run.state = VorkathState.ACTIVE
        run.specialStartCycle = Int.MAX_VALUE
        run.boss.actionDelay = clock.cycle + recoveryTicks
        run.boss.apPlayer2(run.player, interactions)
    }

    fun clearMechanics(run: VorkathArenaState) {
        run.hitEpoch++
        run.combat.runCleanupActions()
        clearAcid(run)
        run.pendingHits.clear()
        run.pendingFireballs.clear()
        run.pendingHeals.clear()
        spawns.clearSpawn(run)
        run.retiringSpawns.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        run.retiringSpawns.clear()
        run.spawnRetireCycle = Int.MAX_VALUE
        spawns.releaseFreeze(run)
        run.boss.clearInteraction()
        run.boss.clearFacingLock()
        run.boss.mode = null
        run.boss.vars["varn.attacking_player"] = PlayerUid.NULL.packed
        run.boss.vars["varn.aggressive_player"] = PlayerUid.NULL.packed
        if (run.player.vars["varp.aggressive_npc"] == run.boss.uid.packed)
            VarPlayerIntMapSetter.set(run.player, "varp.aggressive_npc", NpcUid.NULL.packed)
        run.player.combatClearQueue()
        run.player.clearInteraction()
        run.player.resetSpotanim()
    }

    private fun clearAcid(run: VorkathArenaState) {
        run.acidVisuals.forEach { locs.del(it, Int.MAX_VALUE) }
        run.acidVisuals.clear()
        run.acidTiles.clear()
        run.acidSafeLane.clear()
        run.pendingAcidPools.clear()
    }

    fun inArena(run: VorkathArenaState): Boolean {
        if (instances.sessionForPlayer(run.player) !== run.session) return false
        // Session regionIds are allocation IDs, not packed OSRS map-square coordinates.
        val origin = instances.localCoord(run.session, RegionLocal(0, 35, 63, 0, 0)) ?: return false
        val tile = run.player.coords
        return tile.level == origin.level &&
            tile.x in origin.x until (origin.x + 64) &&
            tile.z in origin.z until (origin.z + 64)
    }

    fun validTarget(run: VorkathArenaState): Boolean =
        instances.sessionForPlayer(run.player)?.stateOrNull(VORKATH_ARENA_STATE) === run &&
            inArena(run) &&
            run.player.isSlotAssigned &&
            !run.player.pendingLogout &&
            !run.player.loggingOut &&
            run.player.hitpoints > 0 &&
            run.boss.isSlotAssigned &&
            run.boss.hitpoints > 0 &&
            run.state in VORKATH_COMBAT_STATES

    fun hitValidity(run: VorkathArenaState): HitValidity {
        val epoch = run.hitEpoch
        return HitValidity { run.hitEpoch == epoch && validTarget(run) }
    }

    fun bossCoord(session: InstanceSession): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 35, 63, 29, 30)))

    fun spawnNpc(
        player: Player,
        session: InstanceSession,
        internal: String,
        coords: CoordGrid,
    ): Npc {
        val type = requireNpc(internal)
        val npc = Npc(type, coords)
        npc.mode = null
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, VORKATH_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    internal fun launchTileProjectile(
        run: VorkathArenaState,
        tile: CoordGrid,
        spec: VorkathProjectile,
    ): Int {
        world.projAnim(spec.build(run.boss, tile, facingTile = run.player.coords))
        return spec.impactTicks
    }

    fun acidPoolType(): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(VORKATH_ACID_POOL_LOC_ID)) {
            "Missing Vorkath acid-pool loc: $VORKATH_ACID_POOL_LOC_ID"
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Vorkath npc definition: $internal"
        }
}
