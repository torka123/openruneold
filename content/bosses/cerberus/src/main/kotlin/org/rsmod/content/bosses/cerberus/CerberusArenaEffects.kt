package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.formulas.attributes.DamageReductionAttributes
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * Arena-only mechanics. BossSpec owns attack selection/cadence; encounter and instance own
 * lifetime.
 */
internal class CerberusArenaEffects
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val reductions: DamageReductionAttributeCollector,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
    private val arena: CerberusArena,
) {
    fun launchSouls(run: CerberusRun) {
        val encounter = run.encounter
        run.playAttackAnimation(
            CerberusAssets.HOWL_ANIMATION,
            CerberusRules.SPECIAL_ANIMATION_LOCK_TICKS,
        )
        run.boss.say("Aaarrrooooooo")
        run.player.soundSynth(CerberusAssets.SOUL_HOWL_SOUND)
        val order = randomSoulOrder()
        order.forEachIndexed { index, style ->
            val spawn =
                arena.localCoord(
                    run.session,
                    CERBERUS_SOUL_SPAWN_X[index],
                    CerberusRules.SOUL_SPAWN_LOCAL_Z,
                )
            val attackTile =
                arena.localCoord(
                    run.session,
                    CERBERUS_SOUL_SPAWN_X[index],
                    CerberusRules.SOUL_ATTACK_LOCAL_Z,
                )
            val soul = Npc(requireNpc(soulNpcId(style)), spawn)
            // Only the encounter's fixed approach/retreat paths may move a Soul.
            soul.mode = NpcMode.None
            soul.ignoreCombatInteractions = true
            soul.setHunt(0)
            soul.movementLocked = true
            soul.moveRestrict = MoveRestrict.BlockedNormal
            soul.lockFacing(attackTile.translateZ(-1))
            soul.hideAllOps()
            soul.assignSpawnOwner(run.player, clock.cycle)
            npcs.add(soul, Int.MAX_VALUE)
            // Repository registration sets this from duration; the encounter owns the lifecycle.
            soul.respawns = false
            instances.attachNpc(run.session.id, soul)
            run.souls += soul
            encounter.own(soul)
            run.soulWaveSpawned = true
            soul.anim(CerberusAssets.SOUL_READY_ANIMATION)
            encounter.schedule(worldQueues, CerberusRules.SOUL_APPROACH_DELAY_TICKS) {
                if (run.isCurrent(instances, arena, encounter) && soul.isSlotAssigned) {
                    soul.movementLocked = false
                    soul.anim(CerberusAssets.SOUL_FLY_ANIMATION)
                    soul.walk(soulPath(spawn, towardBoss = true))
                } else {
                    removeSoul(run, soul)
                }
            }
            val attackDelay =
                CerberusRules.SOUL_APPROACH_DELAY_TICKS +
                    CerberusRules.SOUL_WALK_DISTANCE +
                    CerberusRules.SOUL_FIRST_ATTACK_DELAY_TICKS +
                    index * CerberusRules.SOUL_COMPONENT_GAP_TICKS
            encounter.schedule(worldQueues, attackDelay) {
                if (!run.isCurrent(instances, arena, encounter) || !soul.isSlotAssigned) {
                    removeSoul(run, soul)
                    return@schedule
                }
                if (soul.coords != attackTile) {
                    soul.teleport(collision, attackTile)
                }
                soul.abortRoute()
                soul.movementLocked = true
                launchSoulHit(run, soul, style)
                encounter.schedule(worldQueues, CerberusRules.SOUL_RETREAT_DELAY_TICKS) {
                    if (!run.isCurrent(instances, arena, encounter) || !soul.isSlotAssigned) {
                        removeSoul(run, soul)
                        return@schedule
                    }
                    soul.movementLocked = false
                    soul.clearFacingLock()
                    soul.anim(CerberusAssets.SOUL_FLY_ANIMATION)
                    soul.walk(soulPath(attackTile, towardBoss = false))
                    encounter.schedule(worldQueues, CerberusRules.SOUL_WALK_DISTANCE + 1) {
                        removeSoul(run, soul)
                    }
                }
            }
        }
    }

    private fun soulPath(start: CoordGrid, towardBoss: Boolean): List<CoordGrid> {
        val direction = if (towardBoss) -1 else 1
        return (1..CerberusRules.SOUL_WALK_DISTANCE).map { start.translateZ(it * direction) }
    }

    private fun randomSoulOrder(): List<CerberusAttackStyle> =
        CerberusRules.soulOrder(random.of(3), random.of(2))

    private fun launchSoulHit(run: CerberusRun, soul: Npc, style: CerberusAttackStyle) {
        val encounter = run.encounter
        val target = run.player
        val protectedAtLaunch = isProtected(target, style)
        // The projectile targets the player; the Soul keeps its fixed lane-facing direction.
        soul.anim(soulAttackAnimation(style))
        val (spotanim, profile) = soulProjectile(style)
        val projectile = ProjAnim.fromCoordToPlayer(soul.coords, target, spotanim, profile)
        world.projAnim(projectile)
        val delay = projectile.serverCycles.coerceAtLeast(1)
        if (protectedAtLaunch) {
            val drain =
                CerberusRules.soulPrayerDrain(
                    hasSpectral = target.lefthand.isType("obj.spectral"),
                    hasWard = ArceuusSpellState.hasWard(target, clock.cycle),
                )
            run.negatedSoulAttacks++
            encounter.schedule(worldQueues, delay) {
                if (run.isCurrent(instances, arena, encounter)) {
                    target.statSub("stat.prayer", constant = drain, percent = 0)
                }
            }
            return
        }
        val damage =
            CerberusRules.soulUnprotectedDamage(
                hasElysian =
                    DamageReductionAttributes.ElysianProc in reductions.collectNvP(target, random)
            )
        target.queueHit(
            source = soul,
            delay = delay,
            type = hitType(style),
            damage = damage,
            validity = HitValidity { run.isCurrent(instances, arena, encounter) },
            modifier = CerberusFixedHitModifier,
            impactHandler =
                HitImpactHandler { impact ->
                    if (style == CerberusAttackStyle.MELEE && impact.damage > 0) {
                        run.tookMeleeDamage = true
                    }
                },
        )
    }

    private fun removeSoul(run: CerberusRun, soul: Npc) {
        run.encounter.disown(soul)
        run.souls.remove(soul)
        soul.clearInteraction()
        if (soul.isSlotAssigned) npcs.del(soul, Int.MAX_VALUE)
    }

    fun launchLava(run: CerberusRun) {
        val bossSource = arena.sourceCoords(run.session, run.boss.coords) ?: return
        run.playAttackAnimation(
            CerberusAssets.LAVA_ANIMATION,
            CerberusRules.SPECIAL_ANIMATION_LOCK_TICKS,
        )
        run.boss.say("Grrrrrrrrrrrrrr")
        val candidates = buildList {
            for (x in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X) {
                for (z in CERBERUS_CHAMBER_MIN_Z..CERBERUS_CHAMBER_MAX_Z) {
                    val tile = arena.localCoord(run.session, x, z)
                    if (
                        CerberusRules.isLavaTileLegal(
                            x,
                            z,
                            bossSource.x and 63,
                            bossSource.z and 63,
                            run.boss.visType.size,
                        ) && collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)
                    ) {
                        add(tile)
                    }
                }
            }
        }
        val tiles = CerberusRules.lavaTiles(run.player.coords, candidates) { random.of(it) }
        val heads = listOf(CerberusHead.CENTRE, CerberusHead.LEFT, CerberusHead.RIGHT)
        tiles.forEachIndexed { index, tile ->
            val (sourceX, sourceZ) =
                CerberusRules.bossProjectileSource(
                    run.boss.coords.x,
                    run.boss.coords.z,
                    run.boss.visType.size,
                    tile.x,
                    tile.z,
                    heads[index],
                )
            val source = CoordGrid(sourceX, sourceZ, run.boss.coords.level)
            val profile = CerberusAssets.LAVA_PROJECTILE
            val distance = source.chebyshevDistance(tile)
            val projectile =
                ProjAnim(
                    spotanim = CerberusAssets.LAVA_FLAME_SPOTANIM,
                    startHeight = profile.startHeight,
                    endHeight = profile.endHeight,
                    startTime = profile.delay,
                    endTime = ProjAnim.calculateEndTime(profile, distance),
                    angle = profile.angle,
                    progress = profile.progress,
                    sourceIndex = 0,
                    targetIndex = 0,
                    startCoord = source,
                    endCoord = tile,
                )
            world.projAnim(projectile)
            run.pendingLava +=
                CerberusPendingLava(
                    tile,
                    CerberusRules.projectileImpactCycle(clock.cycle, projectile.serverCycles),
                )
        }
    }

    fun processLava(run: CerberusRun) {
        val ready = run.pendingLava.filter { it.activateCycle <= clock.cycle }
        run.pendingLava.removeAll(ready.toSet())
        ready.forEach { pending ->
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(CerberusAssets.LAVA_LIQUID_SPOTANIM),
                pending.tile,
            )
            if (run.player.coords == pending.tile) {
                queueLavaHit(run, CerberusRules.LAVA_INITIAL_DAMAGE)
            }
            run.activeLava +=
                CerberusActiveLava(
                    pending.tile,
                    clock.cycle + CerberusRules.LAVA_ACTIVE_TICKS,
                    clock.cycle + CerberusRules.LAVA_DAMAGE_INTERVAL_TICKS,
                )
        }

        val expired = run.activeLava.filter { it.endCycle <= clock.cycle }
        expired.forEach { lava ->
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(CerberusAssets.LAVA_FLAME_SPOTANIM),
                lava.tile,
            )
        }
        run.activeLava.removeAll(expired.toSet())
        run.activeLava.forEach { lava ->
            if (clock.cycle < lava.nextDamageCycle) return@forEach
            lava.nextDamageCycle = clock.cycle + CerberusRules.LAVA_DAMAGE_INTERVAL_TICKS
            if (run.player.coords == lava.tile) {
                val maxHit =
                    CerberusRules.lavaMaximumDamage(
                        hasDragonfireShield = hasDragonfireShield(run.player),
                        protectFromMagic = isProtected(run.player, CerberusAttackStyle.MAGIC),
                        hasRegularAntifire = run.player.vars["varbit.antifire_potion"] > 0,
                        hasSuperAntifire = run.player.vars["varbit.super_antifire_potion"] > 0,
                    )
                queueLavaHit(run, maxHit)
            }
        }
    }

    private fun queueLavaHit(run: CerberusRun, damage: Int) {
        if (!run.isCurrent(instances, arena)) return
        val encounter = run.encounter
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            validity = HitValidity { run.isCurrent(instances, arena, encounter) },
            modifier = CerberusFixedHitModifier,
            impactHandler =
                HitImpactHandler { impact -> if (impact.damage > 0) run.tookLavaDamage = true },
        )
    }

    fun queueFixedDamage(run: CerberusRun, damage: Int) {
        if (!run.player.isSlotAssigned || run.player.hitpoints <= 0) return
        if (!run.boss.isSlotAssigned) return
        val encounter = run.encounter
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            validity =
                HitValidity { run.isCurrent(instances, arena, encounter, activeOnly = false) },
            modifier = CerberusFixedHitModifier,
        )
    }

    private fun hasDragonfireShield(player: Player): Boolean =
        DRAGONFIRE_SHIELDS.any { player.lefthand.isType(it) }

    fun clearHazards(run: CerberusRun) {
        run.pendingLava.clear()
        run.activeLava.clear()
        run.souls.toList().forEach { removeSoul(run, it) }
    }

    fun spawnFireWall(run: CerberusRun) {
        clearFireWall(run)
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        for (localX in CERBERUS_FIRE_WALL_MIN_X..CERBERUS_FIRE_WALL_MAX_X) {
            val loc =
                LocInfo(
                    LocLayerConstants.of(shape.id),
                    arena.localCoord(run.session, localX, CERBERUS_FIRE_WALL_Z),
                    LocEntity(CerberusAssets.FIRE_WALL_LOC_ID, shape.id, angle.id),
                )
            world.locAddPrivate(run.player, loc)
            run.fireWallLocs += loc
        }
    }

    fun clearFireWall(run: CerberusRun) {
        run.fireWallLocs.forEach { world.locDelPrivate(run.player, it) }
        run.fireWallLocs.clear()
    }

    private fun isProtected(player: Player, style: CerberusAttackStyle): Boolean =
        when (style) {
            CerberusAttackStyle.MAGIC -> player.vars["varbit.prayer_protectfrommagic"] == 1
            CerberusAttackStyle.RANGED -> player.vars["varbit.prayer_protectfrommissiles"] == 1
            CerberusAttackStyle.MELEE -> player.vars["varbit.prayer_protectfrommelee"] == 1
        }

    private fun hitType(style: CerberusAttackStyle): HitType =
        when (style) {
            CerberusAttackStyle.MAGIC -> HitType.Magic
            CerberusAttackStyle.RANGED -> HitType.Ranged
            CerberusAttackStyle.MELEE -> HitType.Melee
        }

    private fun soulNpcId(style: CerberusAttackStyle): Int =
        when (style) {
            CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_SOUL_NPC_ID
            CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_SOUL_NPC_ID
            CerberusAttackStyle.MELEE -> CerberusAssets.MELEE_SOUL_NPC_ID
        }

    private fun soulProjectile(
        style: CerberusAttackStyle
    ): Pair<Int, dev.openrune.types.ProjAnimType> =
        when (style) {
            CerberusAttackStyle.RANGED ->
                CerberusAssets.RANGED_SOUL_PROJECTILE_SPOTANIM to
                    CerberusAssets.RANGED_SOUL_PROJECTILE
            CerberusAttackStyle.MAGIC ->
                CerberusAssets.MAGIC_SOUL_PROJECTILE_SPOTANIM to
                    CerberusAssets.MAGIC_SOUL_PROJECTILE
            CerberusAttackStyle.MELEE ->
                CerberusAssets.MELEE_SOUL_PROJECTILE_SPOTANIM to
                    CerberusAssets.MELEE_SOUL_PROJECTILE
        }

    private fun soulAttackAnimation(style: CerberusAttackStyle): Int =
        when (style) {
            CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_SOUL_ATTACK_ANIMATION
            CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_SOUL_ATTACK_ANIMATION
            CerberusAttackStyle.MELEE -> CerberusAssets.MELEE_SOUL_ATTACK_ANIMATION
        }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Cerberus NPC definition: $id" }

    private companion object {
        val CERBERUS_SOUL_SPAWN_X = intArrayOf(23, 24, 25)

        val DRAGONFIRE_SHIELDS =
            setOf(
                "obj.antidragonbreathshield",
                "obj.dragonfire_shield",
                "obj.dragonfire_shield_uncharged",
                "obj.dragonfire_ward",
                "obj.dragonfire_ward_uncharged",
                "obj.wyvern_shield",
                "obj.wyvern_shield_uncharged",
            )
    }
}
