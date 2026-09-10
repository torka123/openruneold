package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.attributes.DamageReductionAttributes
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.combat.formulas.maxhit.MaxHitOperations
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
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

@Singleton
internal class CerberusEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val accuracy: AccuracyFormulae,
    private val reductions: DamageReductionAttributeCollector,
    private val bonuses: WornBonuses,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
    private val routeFactory: RouteFactory,
    private val arena: CerberusArena,
) {
    private fun run(player: Player): Run? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == CERBERUS_INSTANCE_KEY }
            ?.stateOrNull(RUN_STATE)

    private fun run(npc: Npc): Run? =
        instances
            .instanceForNpc(npc)
            ?.let(instances::sessionForId)
            ?.takeIf { it.key == CERBERUS_INSTANCE_KEY }
            ?.stateOrNull(RUN_STATE)

    fun validateAssets() {
        val boss = requireNpc(CerberusAssets.ACTIVE_NPC_ID)
        require(boss.id == 5862) { "Revision-240 Cerberus attacking id changed: ${boss.id}" }
        require(boss.size == 5) { "Cerberus must occupy a 5x5 footprint." }
        require(boss.hitpoints == CerberusRules.HITPOINTS) {
            "Cerberus must have ${CerberusRules.HITPOINTS} hitpoints."
        }
        listOf(
                CerberusAssets.SITTING_NPC_ID,
                CerberusAssets.RESETTING_NPC_ID,
                CerberusAssets.RANGED_SOUL_NPC_ID,
                CerberusAssets.MAGIC_SOUL_NPC_ID,
                CerberusAssets.MELEE_SOUL_NPC_ID,
            )
            .forEach { id -> requireNotNull(ServerCacheManager.getNpc(id)) { "Missing npc $id" } }
        requireNotNull(ServerCacheManager.getObject(CerberusAssets.FIRE_WALL_LOC_ID)) {
            "Missing Cerberus fire-wall object ${CerberusAssets.FIRE_WALL_LOC_ID}"
        }
        listOf(
                CerberusAssets.IDLE_ANIMATION,
                CerberusAssets.SITTING_ANIMATION,
                CerberusAssets.STAND_ANIMATION,
                CerberusAssets.SIT_ANIMATION,
                CerberusAssets.WALK_ANIMATION,
                CerberusAssets.DEFEND_ANIMATION,
                CerberusAssets.RANGED_ANIMATION,
                CerberusAssets.MELEE_ANIMATION,
                CerberusAssets.MAGIC_ANIMATION,
                CerberusAssets.TRIPLE_SPRAY_ANIMATION,
                CerberusAssets.HOWL_ANIMATION,
                CerberusAssets.DEATH_ANIMATION,
                CerberusAssets.LAVA_ANIMATION,
                CerberusAssets.SOUL_READY_ANIMATION,
                CerberusAssets.SOUL_FLY_ANIMATION,
                CerberusAssets.MELEE_SOUL_ATTACK_ANIMATION,
                CerberusAssets.RANGED_SOUL_ATTACK_ANIMATION,
                CerberusAssets.MAGIC_SOUL_ATTACK_ANIMATION,
                CerberusAssets.SOUL_DEFEND_ANIMATION,
            )
            .forEach { id -> requireNotNull(ServerCacheManager.getAnim(id)) { "Missing anim $id" } }
        listOf(
                CerberusAssets.MAGIC_CAST_SPOTANIM,
                CerberusAssets.MAGIC_PROJECTILE_SPOTANIM,
                CerberusAssets.MAGIC_IMPACT_SPOTANIM,
                CerberusAssets.RANGED_IMPACT_SPOTANIM,
                CerberusAssets.RANGED_PROJECTILE_SPOTANIM,
                CerberusAssets.LAVA_LIQUID_SPOTANIM,
                CerberusAssets.LAVA_FLAME_SPOTANIM,
                CerberusAssets.RANGED_SOUL_PROJECTILE_SPOTANIM,
                CerberusAssets.MAGIC_SOUL_PROJECTILE_SPOTANIM,
                CerberusAssets.MELEE_SOUL_PROJECTILE_SPOTANIM,
            )
            .forEach(::resolvedSpotanim)
    }

    fun onJoin(player: Player, session: InstanceSession) {
        session.state(RUN_STATE) {
            val boss =
                instances.npcsForInstance(session.id).single {
                    it.type.id == CerberusAssets.SITTING_NPC_ID
                }
            configureBoss(player, boss, true)
            boss.anim(CerberusAssets.SITTING_ANIMATION)
            val run = Run(player, session, boss)
            session.onCleanup { retireRun(run) }
            player.mes("You enter Cerberus's lair. Approach the chamber to wake her.")
            run
        }
    }

    fun onLeave(player: Player, instanceId: InstanceId) {
        val run = instances.sessionForId(instanceId)?.stateOrNull(RUN_STATE) ?: return
        if (run.player === player) retireRun(run)
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = run(target) ?: return
        if (run.boss !== access.npc || !run.boss.isSpawnOwnedBy(target)) return
        if (run.phase != CerberusPhase.ACTIVE || access.actionDelay > clock.cycle) return
        if (!isCurrent(run) || !inChamber(run)) return

        val attackNumber = run.attackNumber
        val forcedAttack = run.forcedAttack.also { run.forcedAttack = null }
        val selectedAttack =
            CerberusRules.selectAttack(
                attackNumber = attackNumber,
                hitpoints = run.boss.hitpoints,
                forcedAttack = forcedAttack,
                forcedSkip = run.forcedSkip,
                nextSkipRoll = { random.of(it) },
            )
        run.forcedSkip = null
        when (selectedAttack) {
            CerberusAttack.TRIPLE -> launchTriple(run)
            CerberusAttack.SOULS -> launchSouls(run)
            CerberusAttack.LAVA -> launchLava(run)
            CerberusAttack.BASIC -> launchBasic(access, run)
        }
        run.attackNumber++
        access.actionDelay = clock.cycle + CerberusRules.recoveryTicks(selectedAttack)
    }

    private fun launchBasic(access: StandardNpcAccess, run: Run) {
        val inMeleeRange =
            CerberusRules.distanceToFootprint(
                run.boss.coords.x,
                run.boss.coords.z,
                run.boss.visType.size,
                run.player.coords.x,
                run.player.coords.z,
            ) == 1
        val allowed = CerberusRules.ordinaryStyles(inMeleeRange)
        val forced = run.forcedNormalStyle.also { run.forcedNormalStyle = null }
        val style = forced?.takeIf { it in allowed } ?: random.pick(allowed)
        playAttackAnimation(run, animationFor(style), CerberusRules.BASIC_ANIMATION_LOCK_TICKS)
        launchHit(
            run,
            style,
            if (style == CerberusAttackStyle.MELEE) {
                CerberusHead.CENTRE
            } else {
                randomOuterHead()
            },
        )
    }

    private fun launchTriple(run: Run) {
        val generation = run.generation
        val firstHead = randomOuterHead()
        val secondHead =
            if (firstHead == CerberusHead.LEFT) CerberusHead.RIGHT else CerberusHead.LEFT
        playAttackAnimation(
            run,
            CerberusAssets.TRIPLE_SPRAY_ANIMATION,
            CerberusRules.TRIPLE_ANIMATION_LOCK_TICKS,
        )
        launchHit(run, CerberusAttackStyle.MAGIC, firstHead)
        worldQueues.add(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS) {
            if (isCurrent(run, generation)) {
                launchHit(run, CerberusAttackStyle.RANGED, secondHead)
            }
        }
        worldQueues.add(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS * 2) {
            if (isCurrent(run, generation)) {
                launchHit(run, CerberusAttackStyle.MELEE, CerberusHead.CENTRE)
            }
        }
    }

    private fun launchHit(run: Run, style: CerberusAttackStyle, head: CerberusHead) {
        if (!isCurrent(run)) return
        val generation = run.generation
        val npc = run.boss
        val target = run.player
        val protectedAtLaunch = isProtected(target, style)
        npc.facePlayer(target)
        val accurate =
            when (style) {
                CerberusAttackStyle.MAGIC -> accuracy.rollMagicAccuracy(npc, target, random)
                CerberusAttackStyle.RANGED -> accuracy.rollRangedAccuracy(npc, target, random)
                CerberusAttackStyle.MELEE ->
                    accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Stab, random)
            }
        val damage =
            if (accurate) {
                val defenceBonus =
                    when (style) {
                        CerberusAttackStyle.MELEE -> bonuses.defensiveStabBonus(target)
                        CerberusAttackStyle.MAGIC -> bonuses.defensiveMagicBonus(target)
                        CerberusAttackStyle.RANGED -> bonuses.defensiveRangedBonus(target)
                    }
                val maximum =
                    MaxHitOperations.applyDamageReductions(
                        CerberusRules.BASIC_MAX_HIT,
                        defenceBonus,
                        reductions.collectNvP(target, random),
                    )
                random.of(maximum + 1)
            } else 0
        val hitType =
            when (style) {
                CerberusAttackStyle.MAGIC -> HitType.Magic
                CerberusAttackStyle.RANGED -> HitType.Ranged
                CerberusAttackStyle.MELEE -> HitType.Melee
            }
        val impactSpot =
            when (style) {
                CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_IMPACT_SPOTANIM
                CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_IMPACT_SPOTANIM
                CerberusAttackStyle.MELEE -> null
            }
        val delay =
            when (style) {
                CerberusAttackStyle.MAGIC -> {
                    npc.spotanim(resolvedSpotanim(CerberusAssets.MAGIC_CAST_SPOTANIM))
                    target.soundSynth(CerberusAssets.MAGIC_CAST_SOUND)
                    launchBossProjectile(
                        run,
                        CerberusAssets.MAGIC_PROJECTILE_SPOTANIM,
                        CerberusAssets.MAGIC_PROJECTILE,
                        head,
                    )
                }
                CerberusAttackStyle.RANGED ->
                    launchBossProjectile(
                        run,
                        CerberusAssets.RANGED_PROJECTILE_SPOTANIM,
                        CerberusAssets.RANGED_PROJECTILE,
                        head,
                    )
                CerberusAttackStyle.MELEE -> 1
            }
        target.queueHit(
            source = npc,
            delay = delay.coerceAtLeast(1),
            type = hitType,
            damage = damage,
            validity = HitValidity { isCurrent(run, generation) },
            modifier =
                NpcCalculatedProtectionPrayerPlayerHitModifier(
                    protectedAtCalculation = protectedAtLaunch,
                    reductionPercent = 100,
                ),
            impactHandler =
                HitImpactHandler { impact ->
                    impactSpot?.let { id ->
                        if (target.isSlotAssigned && target.hitpoints > 0) {
                            target.spotanim(resolvedSpotanim(id))
                            if (style == CerberusAttackStyle.MAGIC) {
                                target.soundSynth(CerberusAssets.MAGIC_IMPACT_SOUND)
                            }
                        }
                    }
                    if (style == CerberusAttackStyle.MELEE && impact.damage > 0) {
                        run.tookMeleeDamage = true
                    }
                },
        )
    }

    private fun launchBossProjectile(
        run: Run,
        spotanim: Int,
        type: dev.openrune.types.ProjAnimType,
        head: CerberusHead,
    ): Int {
        val (sourceX, sourceZ) =
            CerberusRules.bossProjectileSource(
                run.boss.coords.x,
                run.boss.coords.z,
                run.boss.visType.size,
                run.player.coords.x,
                run.player.coords.z,
                head,
            )
        val source = CoordGrid(sourceX, sourceZ, run.boss.coords.level)
        // A coordinate-only source produced damage on revision 240 but no visible travel graphic.
        // Keep the head-aware launch tile while attaching the projectile to Cerberus's NPC slot,
        // matching the working native NPC projectile path used by the combat system.
        val projectile =
            ProjAnim.fromNpcToPlayer(run.boss, run.player, resolvedSpotanim(spotanim), type)
                .copy(startCoord = source)
        world.projAnim(projectile)
        return projectile.serverCycles.coerceAtLeast(1)
    }

    private fun playAttackAnimation(run: Run, animation: Int, lockTicks: Int) {
        run.attackAnimationUntilCycle =
            maxOf(run.attackAnimationUntilCycle, clock.cycle + lockTicks)
        run.boss.anim(animation, priority = CerberusRules.ATTACK_ANIMATION_PRIORITY)
    }

    private fun launchSouls(run: Run) {
        val generation = run.generation
        playAttackAnimation(
            run,
            CerberusAssets.HOWL_ANIMATION,
            CerberusRules.SPECIAL_ANIMATION_LOCK_TICKS,
        )
        run.boss.say("Aaarrrooooooo")
        run.player.soundSynth(CerberusAssets.SOUL_HOWL_SOUND)
        val order = randomSoulOrder()
        order.forEachIndexed { index, style ->
            val spawn =
                localCoord(
                    run.session,
                    CERBERUS_SOUL_SPAWN_X[index],
                    CerberusRules.SOUL_SPAWN_LOCAL_Z,
                )
            val attackTile =
                localCoord(
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
            run.soulWaveSpawned = true
            soul.anim(CerberusAssets.SOUL_READY_ANIMATION)
            worldQueues.add(CerberusRules.SOUL_APPROACH_DELAY_TICKS) {
                if (isCurrent(run, generation) && soul.isSlotAssigned) {
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
            worldQueues.add(attackDelay) {
                if (!isCurrent(run, generation) || !soul.isSlotAssigned) {
                    removeSoul(run, soul)
                    return@add
                }
                if (soul.coords != attackTile) {
                    soul.teleport(collision, attackTile)
                }
                soul.abortRoute()
                soul.movementLocked = true
                launchSoulHit(run, soul, style)
                worldQueues.add(CerberusRules.SOUL_RETREAT_DELAY_TICKS) {
                    if (!isCurrent(run, generation) || !soul.isSlotAssigned) {
                        removeSoul(run, soul)
                        return@add
                    }
                    soul.movementLocked = false
                    soul.clearFacingLock()
                    soul.anim(CerberusAssets.SOUL_FLY_ANIMATION)
                    soul.walk(soulPath(attackTile, towardBoss = false))
                    worldQueues.add(CerberusRules.SOUL_WALK_DISTANCE + 1) { removeSoul(run, soul) }
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

    private fun launchSoulHit(run: Run, soul: Npc, style: CerberusAttackStyle) {
        val generation = run.generation
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
            worldQueues.add(delay) {
                if (isCurrent(run, generation)) {
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
            validity = HitValidity { isCurrent(run, generation) },
            modifier = CerberusFixedHitModifier,
            impactHandler =
                HitImpactHandler { impact ->
                    if (style == CerberusAttackStyle.MELEE && impact.damage > 0) {
                        run.tookMeleeDamage = true
                    }
                },
        )
    }

    private fun removeSoul(run: Run, soul: Npc) {
        run.souls.remove(soul)
        soul.clearInteraction()
        if (soul.isSlotAssigned) npcs.del(soul, Int.MAX_VALUE)
    }

    private fun launchLava(run: Run) {
        val bossSource = arena.sourceCoords(run.session, run.boss.coords) ?: return
        playAttackAnimation(
            run,
            CerberusAssets.LAVA_ANIMATION,
            CerberusRules.SPECIAL_ANIMATION_LOCK_TICKS,
        )
        run.boss.say("Grrrrrrrrrrrrrr")
        val candidates = buildList {
            for (x in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X) {
                for (z in CERBERUS_CHAMBER_MIN_Z..CERBERUS_CHAMBER_MAX_Z) {
                    val tile = localCoord(run.session, x, z)
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
                PendingLava(
                    tile,
                    CerberusRules.projectileImpactCycle(clock.cycle, projectile.serverCycles),
                )
        }
    }

    fun tickAll() {
        instances
            .sessionsForKey(CERBERUS_INSTANCE_KEY)
            .mapNotNull { it.stateOrNull(RUN_STATE) }
            .forEach { run ->
                if (run.pendingRespawn) {
                    run.pendingRespawn = false
                    respawn(run)
                }
                // Native instance hooks own membership/death/teleport retirement. This pass only
                // processes Cerberus's remaining phase/arena mechanics during the strangler step.
                if (
                    run.phase == CerberusPhase.INVALIDATED ||
                        run.player.hitpoints <= 0 ||
                        !run.player.isSlotAssigned ||
                        run(run.player) !== run
                )
                    return@forEach
                val source = arena.sourceCoords(run.session, run.player.coords) ?: return@forEach
                val localX = source.x and 63
                val localZ = source.z and 63
                val previous = run.lastPlayerCoord?.let { arena.sourceCoords(run.session, it) }
                val crossedFireWall =
                    previous != null &&
                        run.fireWallLocs.isNotEmpty() &&
                        CerberusRules.crossedFireWall(
                            previous.x and 63,
                            previous.z and 63,
                            localX,
                            localZ,
                        )
                val inside = inChamber(run)
                when (run.phase) {
                    CerberusPhase.SITTING -> if (inside) beginStanding(run)
                    CerberusPhase.STANDING -> {
                        if (!inside) beginReset(run)
                        else if (clock.cycle >= run.stateCycle) activate(run)
                    }
                    CerberusPhase.ACTIVE -> {
                        if (hasLeftActiveArena(localX, localZ)) {
                            beginReset(run)
                        } else {
                            processLava(run)
                            keepAggressive(run)
                        }
                    }
                    CerberusPhase.RESETTING -> processReset(run)
                    CerberusPhase.RESPAWNING -> Unit
                    CerberusPhase.DYING,
                    CerberusPhase.COMPLETE,
                    CerberusPhase.INVALIDATED -> Unit
                }
                // Outward crossing can reset the fight above. Bind its hit to that new generation.
                if (crossedFireWall) {
                    queueFixedDamage(run, CerberusRules.FIRE_WALL_DAMAGE)
                }
                run.wasInsideChamber = inside
                run.lastPlayerCoord = run.player.coords
            }
    }

    private fun inChamber(run: Run): Boolean {
        val source = arena.sourceCoords(run.session, run.player.coords) ?: return false
        return CerberusRules.inChamber(source.x and 63, source.z and 63)
    }

    private fun hasLeftActiveArena(localX: Int, localZ: Int): Boolean =
        localX !in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X ||
            localZ <= CERBERUS_FIRE_WALL_Z ||
            localZ > CERBERUS_CHAMBER_MAX_Z

    private fun beginStanding(run: Run) {
        run.phase = CerberusPhase.STANDING
        run.generation++
        run.stateCycle = clock.cycle + CerberusRules.STAND_TRANSITION_TICKS
        run.boss.facePlayer(run.player)
        run.boss.anim(CerberusAssets.STAND_ANIMATION)
        run.boss.movementLocked = true
    }

    private fun activate(run: Run) {
        run.boss.transmog(requireNpc(CerberusAssets.ACTIVE_NPC_ID), Int.MAX_VALUE)
        run.boss.hitpoints = CerberusRules.HITPOINTS
        run.boss.movementLocked = false
        run.boss.moveRestrict = MoveRestrict.PassThru
        run.boss.showAllOps()
        spawnFireWall(run)
        run.phase = CerberusPhase.ACTIVE
        run.attackNumber = 1
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun processLava(run: Run) {
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
                ActiveLava(
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

    private fun queueLavaHit(run: Run, damage: Int) {
        if (!isCurrent(run)) return
        val generation = run.generation
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            validity = HitValidity { isCurrent(run, generation) },
            modifier = CerberusFixedHitModifier,
            impactHandler =
                HitImpactHandler { impact -> if (impact.damage > 0) run.tookLavaDamage = true },
        )
    }

    private fun queueFixedDamage(run: Run, damage: Int) {
        if (!run.player.isSlotAssigned || run.player.hitpoints <= 0) return
        if (!run.boss.isSlotAssigned) return
        val generation = run.generation
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            validity = HitValidity { isCurrent(run, generation, activeOnly = false) },
            modifier = CerberusFixedHitModifier,
        )
    }

    private fun hasDragonfireShield(player: Player): Boolean =
        DRAGONFIRE_SHIELDS.any { player.lefthand.isType(it) }

    private fun beginReset(run: Run) {
        run.generation++
        run.phase = CerberusPhase.RESETTING
        run.stateCycle = clock.cycle + CerberusRules.RESET_TRANSITION_TICKS
        run.resetDeadlineCycle = clock.cycle + CerberusRules.RESET_WALK_TIMEOUT_TICKS
        run.resetArrived = false
        run.resetSitting = false
        run.attackNumber = 1
        run.forcedAttack = null
        run.forcedNormalStyle = null
        run.forcedSkip = null
        run.attackAnimationUntilCycle = 0
        run.player.combatClearQueue()
        clearHazards(run)
        if (!run.boss.isSlotAssigned) return
        run.boss.clearInteraction()
        run.boss.routeDestination.clear()
        run.boss.transmog(requireNpc(CerberusAssets.RESETTING_NPC_ID), Int.MAX_VALUE)
        run.boss.movementLocked = false
        run.boss.hideAllOps()
        val home = localCoord(run.session, CERBERUS_BOSS_LOCAL_X, CERBERUS_BOSS_LOCAL_Z)
        if (run.boss.coords == home) {
            run.resetArrived = true
        } else {
            val generation = run.generation
            run.boss.anim(CerberusAssets.WALK_ANIMATION)
            run.boss.walkTo(routeFactory, home, passThroughEntities = true) {
                if (
                    run(run.player) === run &&
                        run.generation == generation &&
                        run.phase == CerberusPhase.RESETTING
                ) {
                    run.resetArrived = true
                }
            }
        }
    }

    private fun processReset(run: Run) {
        if (!run.boss.isSlotAssigned) return
        if (!run.resetArrived && clock.cycle >= run.resetDeadlineCycle) {
            run.boss.teleport(
                collision,
                localCoord(run.session, CERBERUS_BOSS_LOCAL_X, CERBERUS_BOSS_LOCAL_Z),
            )
            run.resetArrived = true
        }
        if (!run.resetArrived || clock.cycle < run.stateCycle) return
        if (!run.resetSitting) {
            run.resetSitting = true
            run.stateCycle = clock.cycle + CerberusRules.RESET_TRANSITION_TICKS
            run.boss.movementLocked = true
            run.boss.anim(
                CerberusAssets.SIT_ANIMATION,
                priority = CerberusRules.ATTACK_ANIMATION_PRIORITY,
            )
            return
        }
        finishReset(run)
    }

    private fun finishReset(run: Run) {
        if (!run.boss.isSlotAssigned) return
        clearFireWall(run)
        run.boss.transmog(requireNpc(CerberusAssets.SITTING_NPC_ID), Int.MAX_VALUE)
        run.boss.baseHitpointsLvl = CerberusRules.HITPOINTS
        run.boss.hitpoints = CerberusRules.HITPOINTS
        run.boss.movementLocked = true
        run.boss.showAllOps()
        run.boss.anim(CerberusAssets.SITTING_ANIMATION)
        run.phase = CerberusPhase.SITTING
        run.soulWaveSpawned = false
        run.negatedSoulAttacks = 0
        run.tookLavaDamage = false
        run.tookMeleeDamage = false
    }

    fun onNativeRespawn(npc: Npc) {
        val run = run(npc) ?: return
        if (run.boss === npc && run.phase == CerberusPhase.RESPAWNING) run.pendingRespawn = true
    }

    private fun respawn(run: Run) {
        run.generation++
        run.rewardEligible = true
        run.forcedAttack = null
        run.forcedNormalStyle = null
        run.forcedSkip = null
        run.attackAnimationUntilCycle = 0
        configureBoss(run.player, run.boss, run.rewardEligible)
        run.boss.anim(CerberusAssets.SITTING_ANIMATION)
        run.phase = CerberusPhase.SITTING
        run.attackNumber = 1
        run.soulWaveSpawned = false
        run.negatedSoulAttacks = 0
        run.tookLavaDamage = false
        run.tookMeleeDamage = false
    }

    fun beginBossDeath(npc: Npc): Boolean {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return false
        if (run.phase != CerberusPhase.ACTIVE) return false
        run.phase = CerberusPhase.DYING
        run.generation++
        run.player.combatClearQueue()
        clearHazards(run)
        clearFireWall(run)
        npc.clearInteraction()
        return true
    }

    fun finishBossDeath(npc: Npc) {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return
        if (run.phase != CerberusPhase.DYING) return
        awardCombatAchievements(run)
        run.phase = CerberusPhase.RESPAWNING
    }

    private fun awardCombatAchievements(run: Run) {
        if (!run.rewardEligible) return
        val player = run.player
        val kills = player.vars["varp.total_cerberus_kills"]
        CerberusRules.completedTasks(
                kills = kills,
                soulWaveSpawned = run.soulWaveSpawned,
                negatedSoulAttacks = run.negatedSoulAttacks,
                tookLavaDamage = run.tookLavaDamage,
                tookMeleeDamage = run.tookMeleeDamage,
            )
            .forEach { task ->
                val (varbit, name) =
                    when (task) {
                        CerberusTask.UNREQUIRED_ANTIFIRE ->
                            CA_NO_LAVA_DAMAGE to "Unrequired Antifire"
                        CerberusTask.VETERAN -> CA_KILLCOUNT_75 to "Cerberus Veteran"
                        CerberusTask.GHOST_BUSTER -> CA_GHOST_BUSTER to "Ghost Buster"
                        CerberusTask.ANTI_BITE_MECHANICS ->
                            CA_NO_MELEE_DAMAGE to "Anti-Bite Mechanics"
                        CerberusTask.MASTER -> CA_KILLCOUNT_150 to "Cerberus Master"
                        CerberusTask.AROOO_NO_MORE -> CA_NO_SOULS to "Arooo No More"
                    }
                completeCombatAchievement(player, varbit, name)
            }
    }

    private fun completeCombatAchievement(player: Player, varbit: String, name: String) {
        if (player.vars[varbit] != 0) return
        VarPlayerIntMapSetter.set(player, varbit, 1)
        val count = (player.vars[CA_TOTAL] + 1).coerceAtMost(6)
        VarPlayerIntMapSetter.set(player, CA_TOTAL, count)
        player.mes("<col=ff981f>Combat Achievement completed: $name</col>")
    }

    fun defend(npc: Npc) {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return
        if (
            CerberusRules.canPlayDefendAnimation(
                phase = run.phase,
                currentCycle = clock.cycle,
                attackAnimationUntilCycle = run.attackAnimationUntilCycle,
                hitpoints = npc.hitpoints,
            )
        ) {
            npc.anim(CerberusAssets.DEFEND_ANIMATION)
        }
    }

    fun debug(player: Player, args: List<String>): String {
        val run = run(player) ?: return "You do not have an active Cerberus encounter."
        val operation = args.firstOrNull()?.lowercase() ?: "status"
        if (operation == "status") {
            val candidate = CerberusRules.specialCandidate(run.attackNumber, run.boss.hitpoints)
            return "Cerberus phase=${run.phase} hp=${run.boss.hitpoints}/${CerberusRules.HITPOINTS} " +
                "counter=${run.attackNumber} generation=${run.generation} " +
                "next=${candidate ?: CerberusAttack.BASIC} " +
                "souls=${run.souls.count { it.isSlotAssigned }} lava=${run.activeLava.size} " +
                "eligible=${run.rewardEligible}."
        }
        taintDebugRun(run)
        return when (operation) {
            "hp" -> {
                val value = args.getOrNull(1)?.toIntOrNull()
                if (value == null || value !in 1..CerberusRules.HITPOINTS) {
                    "Use ::cerbdebug hp <1-${CerberusRules.HITPOINTS}>."
                } else {
                    run.boss.hitpoints = value
                    "Cerberus hitpoints set to $value; this kill is reward-ineligible."
                }
            }
            "counter" -> {
                val value = args.getOrNull(1)?.toIntOrNull()
                if (value == null || value < 1) {
                    "Use ::cerbdebug counter <1+>."
                } else {
                    run.attackNumber = value
                    "Cerberus attack counter set to $value; this kill is reward-ineligible."
                }
            }
            "next" -> {
                val token = args.getOrNull(1)?.lowercase()
                val forced =
                    when (token) {
                        "basic" -> CerberusAttack.BASIC to null
                        "magic",
                        "mage" -> CerberusAttack.BASIC to CerberusAttackStyle.MAGIC
                        "ranged",
                        "range" -> CerberusAttack.BASIC to CerberusAttackStyle.RANGED
                        "melee" -> CerberusAttack.BASIC to CerberusAttackStyle.MELEE
                        "triple" -> CerberusAttack.TRIPLE to null
                        "souls",
                        "soul" -> CerberusAttack.SOULS to null
                        "lava" -> CerberusAttack.LAVA to null
                        else -> null
                    }
                if (forced == null) {
                    "Use ::cerbdebug next basic|magic|ranged|melee|triple|souls|lava."
                } else {
                    run.forcedAttack = forced.first
                    run.forcedNormalStyle = forced.second
                    "Cerberus will use ${token ?: "basic"} next; this kill is reward-ineligible."
                }
            }
            "skip" -> {
                val value =
                    when (args.getOrNull(1)?.lowercase()) {
                        "on",
                        "true",
                        "force" -> true
                        "off",
                        "false",
                        "suppress" -> false
                        else -> null
                    }
                if (value == null) {
                    "Use ::cerbdebug skip force|suppress."
                } else {
                    run.forcedSkip = value
                    "Next eligible special skip forced to $value; this kill is reward-ineligible."
                }
            }
            "clear" -> {
                run.forcedAttack = null
                run.forcedNormalStyle = null
                run.forcedSkip = null
                "Cerberus debug overrides cleared; this kill remains reward-ineligible."
            }
            else ->
                "Use ::cerbdebug status|hp <value>|counter <value>|next <attack>|skip force|suppress|clear."
        }
    }

    private fun taintDebugRun(run: Run) {
        run.rewardEligible = false
        run.boss.suppressRewards = true
    }

    fun isEncounterNpc(npc: Npc): Boolean =
        run(npc)?.let { it.boss === npc || npc in it.souls } == true

    fun isParticipant(player: Player, npc: Npc): Boolean =
        run(player)?.let { (it.boss === npc || npc in it.souls) && npc.isSpawnOwnedBy(player) } ==
            true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = run(player) ?: return "You do not have an active Cerberus encounter."
        if (npc in run.souls) return "The summoned souls cannot be attacked."
        if (run.boss !== npc) return "That Cerberus belongs to another encounter."
        if (!inChamber(run)) return "Enter Cerberus's chamber to attack her."
        if (run.phase != CerberusPhase.ACTIVE) return "Cerberus cannot be attacked right now."
        return null
    }

    private fun keepAggressive(run: Run) {
        if (run.phase != CerberusPhase.ACTIVE) return
        if (!run.boss.isSlotAssigned || run.boss.hitpoints <= 0 || run.boss.mode != null) return
        if (
            CerberusRules.distanceToFootprint(
                run.boss.coords.x,
                run.boss.coords.z,
                run.boss.visType.size,
                run.player.coords.x,
                run.player.coords.z,
            ) <= CerberusRules.ATTACK_RANGE
        ) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun configureBoss(player: Player, npc: Npc, rewardEligible: Boolean) {
        npc.transmog(requireNpc(CerberusAssets.SITTING_NPC_ID), Int.MAX_VALUE)
        npc.baseHitpointsLvl = CerberusRules.HITPOINTS
        npc.hitpoints = CerberusRules.HITPOINTS
        npc.mode = null
        npc.movementLocked = true
        npc.moveRestrict = MoveRestrict.PassThru
        npc.apRangeOverride = CerberusRules.ATTACK_RANGE
        npc.maxOpRangeOverride = 64
        npc.apRequiresLineOfSight = true
        npc.combatImmunities.toxins = true
        npc.suppressRewards = !rewardEligible
        npc.assignSpawnOwner(player, clock.cycle)
        npc.showAllOps()
    }

    private fun localCoord(session: InstanceSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(0, CERBERUS_SOURCE_MAP_X, CERBERUS_SOURCE_MAP_Z, localX, localZ),
            )
        )

    private fun isCurrent(
        run: Run,
        generation: Int = run.generation,
        activeOnly: Boolean = true,
    ): Boolean =
        run(run.player) === run &&
            instances.sessionForPlayer(run.player)?.id == run.session.id &&
            arena.contains(run.session, run.player.coords) &&
            arena.contains(run.session, run.boss.coords) &&
            run.player.coords.level == run.boss.coords.level &&
            run.generation == generation &&
            (!activeOnly || run.phase == CerberusPhase.ACTIVE) &&
            run.boss.isSlotAssigned &&
            run.boss.hitpoints > 0 &&
            run.player.isSlotAssigned &&
            run.player.hitpoints > 0

    private fun retireRun(run: Run) {
        if (run.phase == CerberusPhase.INVALIDATED) return
        run.phase = CerberusPhase.INVALIDATED
        run.generation++
        run.player.combatClearQueue()
        clearHazards(run)
        clearFireWall(run)
        run.boss.clearInteraction()
        // Native instance teardown owns the boss; do not race its immediate/deferred reclaim.
    }

    private fun clearHazards(run: Run) {
        run.pendingLava.clear()
        run.activeLava.clear()
        run.souls.toList().forEach { removeSoul(run, it) }
    }

    private fun spawnFireWall(run: Run) {
        clearFireWall(run)
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        for (localX in CERBERUS_FIRE_WALL_MIN_X..CERBERUS_FIRE_WALL_MAX_X) {
            val loc =
                LocInfo(
                    LocLayerConstants.of(shape.id),
                    localCoord(run.session, localX, CERBERUS_FIRE_WALL_Z),
                    LocEntity(CerberusAssets.FIRE_WALL_LOC_ID, shape.id, angle.id),
                )
            world.locAddPrivate(run.player, loc)
            run.fireWallLocs += loc
        }
    }

    private fun clearFireWall(run: Run) {
        run.fireWallLocs.forEach { world.locDelPrivate(run.player, it) }
        run.fireWallLocs.clear()
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Cerberus NPC definition: $id" }

    private fun resolvedSpotanim(id: Int): Int {
        CerberusSpotanimValidation.requireLoaded(id)
        val internal =
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing Cerberus spotanim mapping: $id"
            }
        return internal.asRSCM(RSCMType.SPOTANIM)
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

    private fun randomOuterHead(): CerberusHead =
        if (random.of(2) == 0) CerberusHead.LEFT else CerberusHead.RIGHT

    private fun animationFor(style: CerberusAttackStyle): Int =
        when (style) {
            CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_ANIMATION
            CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_ANIMATION
            CerberusAttackStyle.MELEE -> CerberusAssets.MELEE_ANIMATION
        }

    private data class PendingLava(val tile: CoordGrid, val activateCycle: Int)

    private data class ActiveLava(val tile: CoordGrid, val endCycle: Int, var nextDamageCycle: Int)

    private data class Run(
        val player: Player,
        val session: InstanceSession,
        val boss: Npc,
        var pendingRespawn: Boolean = false,
        var phase: CerberusPhase = CerberusPhase.SITTING,
        var attackNumber: Int = 1,
        var generation: Int = 1,
        var stateCycle: Int = 0,
        var wasInsideChamber: Boolean = false,
        var lastPlayerCoord: CoordGrid? = null,
        var resetDeadlineCycle: Int = 0,
        var resetArrived: Boolean = false,
        var resetSitting: Boolean = false,
        var attackAnimationUntilCycle: Int = 0,
        var forcedAttack: CerberusAttack? = null,
        var forcedNormalStyle: CerberusAttackStyle? = null,
        var forcedSkip: Boolean? = null,
        var soulWaveSpawned: Boolean = false,
        var negatedSoulAttacks: Int = 0,
        var tookLavaDamage: Boolean = false,
        var rewardEligible: Boolean = true,
        var tookMeleeDamage: Boolean = false,
        val pendingLava: MutableList<PendingLava> = mutableListOf(),
        val activeLava: MutableList<ActiveLava> = mutableListOf(),
        val souls: MutableList<Npc> = mutableListOf(),
        val fireWallLocs: MutableList<LocInfo> = mutableListOf(),
    )

    private companion object {
        val RUN_STATE = InstanceStateKey<Run>("cerberus-arena")

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

        const val CA_KILLCOUNT_75 = "varbit.ca_task_cerberus_killcount_1_completed"
        const val CA_KILLCOUNT_150 = "varbit.ca_task_cerberus_killcount_2_completed"
        const val CA_GHOST_BUSTER = "varbit.ca_task_cerberus_mechanical_1_completed"
        const val CA_NO_SOULS = "varbit.ca_task_cerberus_mechanical_2_completed"
        const val CA_NO_LAVA_DAMAGE = "varbit.ca_task_cerberus_perfection_1_completed"
        const val CA_NO_MELEE_DAMAGE = "varbit.ca_task_cerberus_perfection_2_completed"
        const val CA_TOTAL = "varbit.ca_total_tasks_completed_cerberus"
    }
}
