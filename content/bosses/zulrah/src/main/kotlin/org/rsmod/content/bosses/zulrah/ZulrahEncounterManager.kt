package org.rsmod.content.bosses.zulrah

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class ZulrahEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val combat: NvPCombat,
    private val world: WorldRepository,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun validateAssets() {
        (ZulrahAssets.forms + ZulrahAssets.snakelings + ZulrahAssets.priestesses).forEach(
            ::requireNpc
        )
        ZulrahAssets.sequences.forEach { internal ->
            requireNotNull(ServerCacheManager.getAnim(internal.asRSCM(RSCMType.SEQ))) {
                "Missing Zulrah sequence: $internal"
            }
        }
        ZulrahAssets.spotanims.forEach { internal -> internal.asRSCM(RSCMType.SPOTANIM) }
        ZulrahAssets.projectileControllers.forEach { internal ->
            requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
                "Missing Zulrah projectile controller: $internal"
            }
        }
        ZulrahAssets.sounds.forEach { internal -> internal.asRSCM(RSCMType.SYNTH) }
        (ZulrahAssets.boats + ZulrahAssets.EXIT + ZulrahAssets.CLOUD_LOC).forEach { internal ->
            requireNotNull(ServerCacheManager.getObject(internal.asRSCM(RSCMType.LOC))) {
                "Missing Zulrah loc: $internal"
            }
        }
    }

    fun teleportDock(player: Player): Boolean {
        val session = instances.sessionForPlayer(player)
        if (session != null && session.key != ZULRAH_INSTANCE_KEY) {
            player.mes("Leave your current instance before travelling to Zul-Andra.")
            return false
        }
        runs[player.uid]?.let { abort(player, "staff travel command", teleport = false) }
        if (session != null) instances.leave(player, session, clock.cycle)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, ZULRAH_DOCK)
        player.mes("You arrive at the Zul-Andra dock. Board the boat to enter the shrine.")
        return true
    }

    fun enter(player: Player): Boolean = start(player, null, 1, null)

    fun enterDebug(player: Player, rotation: Int, phase: Int, seed: Long): Boolean =
        start(player, rotation, phase, seed)

    private fun start(player: Player, forcedRotation: Int?, phase: Int, seed: Long?): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering Zulrah's shrine.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        if (ZULRAH_REQUIRE_REGICIDE) {
            player.mes("You must complete Regicide before travelling to Zulrah's shrine.")
            return false
        }
        val green = requireNpc(ZulrahAssets.GREEN)
        val rotation =
            forcedRotation?.let(ZulrahRotations::byNumber)
                ?: ZulrahRotations.byNumber(random.of(ZulrahRotations.all.size) + 1)
        val phaseIndex = (phase - 1).coerceIn(0, rotation.phases.lastIndex)
        val result =
            instances.create(
                owner = player,
                key = ZULRAH_INSTANCE_KEY,
                spec = zulrahSpec(green),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "The shrine is unavailable."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = ZULRAH_DROP_DURATION
        val phaseData = rotation.phases[phaseIndex]
        val boss =
            spawnNpc(
                player,
                result.session,
                internalFor(phaseData.form),
                phaseCoord(result.session, phaseData),
            )
        boss.baseHitpointsLvl = ZULRAH_MAX_HITPOINTS
        boss.hitpoints = ZULRAH_MAX_HITPOINTS
        boss.baseDefenceLvl = 300
        boss.defenceLvl = 300
        boss.movementLocked = true
        boss.apRangeOverride = 32
        boss.apRequiresLineOfSight = true
        boss.anim(if (phaseIndex == 0) ZulrahAssets.SPAWN else ZulrahAssets.EMERGE)
        faceForPhase(result.session, boss, phaseData.position)
        val run =
            Run(
                player = player,
                session = result.session,
                generation = generation++,
                boss = boss,
                rotation = rotation,
                phaseIndex = phaseIndex,
                runtimePhase = phaseData,
                state = ZulrahState.EMERGING,
                transitionCycle =
                    clock.cycle +
                        if (phaseIndex == 0) ZULRAH_INITIAL_EMERGE_TICKS else ZULRAH_EMERGE_TICKS,
                nextActionCycle = clock.cycle,
                startCycle = clock.cycle,
                debug = forcedRotation != null,
                seededRandom = seed?.let(::Random),
            )
        runs[player.uid] = run
        player.mes("Zulrah emerges from the swamp.")
        if (run.debug) {
            player.mes(
                "Debug encounter: rotation ${rotation.number}, phase ${phaseIndex + 1}, seed ${seed ?: 0}. Rewards are disabled."
            )
        }
        log.info {
            "[Zulrah] player=${player.displayName} generation=${run.generation} instance=${result.session.id} rotation=${rotation.number} debug=${run.debug}"
        }
        return true
    }

    fun tickAll() {
        runs.values.toList().forEach { tick(it.player) }
    }

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (
            !player.isSlotAssigned ||
                player.pendingLogout ||
                player.loggingOut ||
                player.hitpoints <= 0
        )
            return
        if (
            instances.sessionForPlayer(player)?.id != run.session.id || !inArena(run, player.coords)
        ) {
            abort(player, "left the shrine", teleport = false)
            return
        }
        processPendingHazards(run)
        processCloudDamage(run)
        processSnakelingExpiry(run)
        when (run.state) {
            ZulrahState.EMERGING -> if (clock.cycle >= run.transitionCycle) finishEmerge(run)
            ZulrahState.ACTIVE -> tickPhase(run)
            ZulrahState.DIVING -> if (clock.cycle >= run.transitionCycle) finishDive(run)
            ZulrahState.RESPAWNING -> if (clock.cycle >= run.transitionCycle) respawn(run)
            ZulrahState.DYING,
            ZulrahState.COMPLETED,
            ZulrahState.DISPOSED -> Unit
        }
    }

    private fun finishEmerge(run: Run) {
        run.state = ZulrahState.ACTIVE
        run.actionIndex = 0
        run.actionProgress = 0
        run.nextActionCycle = clock.cycle
        run.boss.apPlayer2(run.player, interactions)
        tickPhase(run)
    }

    private fun tickPhase(run: Run) {
        if (clock.cycle < run.nextActionCycle) return
        if (run.actionIndex >= run.runtimePhase.actions.size) {
            beginDive(run)
            return
        }
        when (val action = run.runtimePhase.actions[run.actionIndex]) {
            is ZulrahAction.Delay -> {
                run.actionIndex++
                run.nextActionCycle = clock.cycle + action.ticks
            }
            is ZulrahAction.StandardAttack,
            is ZulrahAction.JadAttack -> keepAggressive(run)
            is ZulrahAction.TailSwipe -> launchTail(run, action)
            is ZulrahAction.VenomCloudVolley -> launchCloudVolley(run, action)
            is ZulrahAction.SnakelingVolley -> launchSnakelingVolley(run, action)
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (run.state != ZulrahState.ACTIVE || run.boss !== access.npc) return
        val action = run.runtimePhase.actions.getOrNull(run.actionIndex) ?: return
        val style =
            when (action) {
                is ZulrahAction.StandardAttack ->
                    when (run.runtimePhase.form) {
                        ZulrahForm.SERPENTINE -> ZulrahAttackStyle.RANGED
                        ZulrahForm.TANZANITE -> ZulrahRules.blueStyle(roll(run, 3))
                        ZulrahForm.MAGMA -> return
                    }
                is ZulrahAction.JadAttack ->
                    ZulrahRules.jadStyles(action.count, action.startsWith)[run.actionProgress]
                else -> return
            }
        directAttack(access, run, target, style)
    }

    private fun directAttack(
        access: StandardNpcAccess,
        run: Run,
        target: Player,
        style: ZulrahAttackStyle,
    ) {
        val protectedAtLaunch =
            target.vars[
                    if (style == ZulrahAttackStyle.MAGIC) "varbit.prayer_protectfrommagic"
                    else "varbit.prayer_protectfrommissiles"] == 1
        val projectile =
            playerProjectile(
                run.boss,
                target,
                if (style == ZulrahAttackStyle.MAGIC) ZulrahAssets.MAGIC_PROJECTILE
                else ZulrahAssets.RANGED_PROJECTILE,
                ZulrahAssets.ATTACK_PROJECTILE_CONTROLLER,
            )
        var accurateAtLaunch = false
        val combatAttack =
            if (style == ZulrahAttackStyle.MAGIC) CombatAttack.NpcMagic(ZULRAH_MAX_HIT)
            else CombatAttack.NpcRanged(RangedAttackType.Standard)
        combat.attack(
            access,
            target,
            combatAttack,
            NpcAttackOverride(
                attackAnimation = ZulrahAssets.ATTACK,
                maxHit = ZULRAH_MAX_HIT,
                attackRate = ZULRAH_ATTACK_RATE,
                impactDelay = projectile.serverCycles,
                hitModifier =
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedAtLaunch,
                        reductionPercent = 100,
                    ),
                onAttack = {
                    world.projAnim(projectile)
                    target.soundSynth(
                        if (style == ZulrahAttackStyle.MAGIC) ZULRAH_MAGIC_SEND_SOUND
                        else ZULRAH_RANGED_SEND_SOUND
                    )
                    advanceDirectAttack(run)
                },
                onSuccessfulAttack = { _, _ -> accurateAtLaunch = true },
                onImpact = { _, _ ->
                    target.soundSynth(
                        if (style == ZulrahAttackStyle.MAGIC) ZULRAH_MAGIC_IMPACT_SOUND
                        else ZULRAH_RANGED_IMPACT_SOUND
                    )
                    if (accurateAtLaunch) PlayerVenom.tryVenom(target)
                },
            ),
        )
    }

    private fun advanceDirectAttack(run: Run) {
        val action = run.runtimePhase.actions.getOrNull(run.actionIndex) ?: return
        val total =
            when (action) {
                is ZulrahAction.StandardAttack -> action.count
                is ZulrahAction.JadAttack -> action.count
                else -> return
            }
        run.actionProgress++
        if (run.actionProgress >= total) {
            run.actionProgress = 0
            run.actionIndex++
        }
        armNextAction(run, ZULRAH_ATTACK_RATE)
    }

    private fun launchCloudVolley(run: Run, action: ZulrahAction.VenomCloudVolley) {
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
        run.pendingHazards +=
            PendingHazard(
                clock.cycle + primaryProjectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
                primary,
                HazardKind.CLOUD,
            )
        run.pendingHazards +=
            PendingHazard(
                clock.cycle + secondaryProjectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
                secondary,
                HazardKind.CLOUD,
            )
        run.actionIndex++
        armNextAction(run, ZULRAH_ATTACK_RATE)
    }

    private fun launchSnakelingVolley(run: Run, action: ZulrahAction.SnakelingVolley) {
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
        run.pendingHazards +=
            PendingHazard(
                clock.cycle + projectile.endTime / CLIENT_CYCLES_PER_GAME_TICK,
                tile,
                HazardKind.SNAKELING,
            )
        run.actionIndex++
        armNextAction(run, ZULRAH_ATTACK_RATE)
    }

    private fun launchTail(run: Run, action: ZulrahAction.TailSwipe) {
        val target = run.player.coords
        run.boss.faceSquare(target)
        run.boss.anim(
            if (run.actionProgress % 2 == 0) ZulrahAssets.TAIL_LEFT else ZulrahAssets.TAIL_RIGHT
        )
        run.pendingTails += PendingTail(clock.cycle + ZULRAH_TAIL_WINDUP_TICKS, target)
        run.actionProgress++
        if (run.actionProgress >= action.count) {
            run.actionProgress = 0
            run.actionIndex++
        }
        run.nextActionCycle = clock.cycle + ZULRAH_TAIL_WINDUP_TICKS + ZULRAH_TAIL_RECOVERY_TICKS
    }

    private fun processPendingHazards(run: Run) {
        val tails = run.pendingTails.filter { it.impactCycle <= clock.cycle }
        run.pendingTails.removeAll(tails.toSet())
        for (tail in tails) {
            val onSafespot =
                ZULRAH_TAIL_SAFESPOTS.any { safe ->
                    localCoord(run.session, safe) == run.player.coords
                }
            val hit =
                ZulrahRules.tailDamage(
                    !onSafespot &&
                        ZulrahRules.inTailTarget(
                            run.player.coords.x,
                            run.player.coords.z,
                            tail.tile.x,
                            tail.tile.z,
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
                    impactHandler =
                        HitImpactHandler { impact ->
                            if (runs[run.player.uid] === run && impact.damage > 0) {
                                PlayerVenom.tryVenom(run.player)
                            }
                        },
                )
                run.player.applyShoveStun(stunCycles = 5, postStunImmunityCycles = 1)
            }
        }
        val hazards = run.pendingHazards.filter { it.impactCycle <= clock.cycle }
        run.pendingHazards.removeAll(hazards.toSet())
        for (hazard in hazards) {
            when (hazard.kind) {
                HazardKind.CLOUD -> spawnCloud(run, hazard.tile)
                HazardKind.SNAKELING -> spawnSnakeling(run, hazard.tile)
            }
        }
    }

    private fun spawnCloud(run: Run, tile: CoordGrid) {
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

    private fun processCloudDamage(run: Run) {
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
        )
    }

    private fun spawnSnakeling(run: Run, tile: CoordGrid) {
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
        run.snakelings[npc] = SnakelingState(magic, clock.cycle + ZULRAH_SNAKELING_LIFETIME)
        npc.apPlayer2(run.player, interactions)
    }

    fun attackSnakeling(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        val state = run.snakelings[access.npc] ?: return
        if (!access.npc.isSpawnOwnedBy(target)) return
        var accurate = false
        val projectile =
            if (state.magic) {
                ProjAnim.fromNpcToPlayer(
                    access.npc,
                    target,
                    ZulrahAssets.SNAKELING_MAGIC_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                    projectileType(ZulrahAssets.SNAKELING_MAGIC_PROJECTILE_CONTROLLER),
                )
            } else null
        val attack =
            if (state.magic) CombatAttack.NpcMagic(ZULRAH_SNAKELING_MAX_HIT)
            else CombatAttack.NpcMelee(MeleeAttackType.Stab)
        combat.attack(
            access,
            target,
            attack,
            NpcAttackOverride(
                attackAnimation = ZulrahAssets.SNAKELING_ATTACK,
                maxHit = ZULRAH_SNAKELING_MAX_HIT,
                attackRate = ZULRAH_SNAKELING_ATTACK_RATE,
                impactDelay = projectile?.serverCycles,
                onAttack = {
                    if (projectile != null) world.projAnim(projectile)
                    target.soundSynth(
                        if (state.magic) ZULRAH_RANGED_IMPACT_SOUND
                        else ZULRAH_SNAKELING_IMPACT_SOUND
                    )
                },
                onSuccessfulAttack = { _, _ -> accurate = true },
                onImpact = { _, _ ->
                    if (state.magic) target.soundSynth(ZULRAH_SNAKELING_IMPACT_SOUND)
                    if (accurate) PlayerVenom.tryVenom(target)
                },
            ),
        )
    }

    private fun processSnakelingExpiry(run: Run) {
        val expired =
            run.snakelings.filter { (npc, state) ->
                !npc.isSlotAssigned || npc.hitpoints <= 0 || clock.cycle >= state.expireCycle
            }
        for ((npc, _) in expired) {
            run.snakelings.remove(npc)
            if (npc.isSlotAssigned && npc.hitpoints > 0) npcs.del(npc, Int.MAX_VALUE)
        }
    }

    private fun beginDive(run: Run) {
        run.state = ZulrahState.DIVING
        run.boss.anim(ZulrahAssets.DIVE)
        run.boss.mode = null
        run.transitionCycle = clock.cycle + ZULRAH_DIVE_TICKS
    }

    private fun finishDive(run: Run) {
        run.phaseIndex++
        if (run.phaseIndex >= run.rotation.phases.size) {
            run.completedRotations++
            run.rotation = ZulrahRotations.byNumber(roll(run, ZulrahRotations.all.size) + 1)
            // The final green phase is also phase one of the next rotation.
            run.phaseIndex = 1
        }
        run.runtimePhase = run.rotation.phases[run.phaseIndex]
        run.boss.transmog(requireNpc(internalFor(run.runtimePhase.form)), Int.MAX_VALUE)
        run.boss.teleport(collision, phaseCoord(run.session, run.runtimePhase))
        faceForPhase(run.session, run.boss, run.runtimePhase.position)
        run.boss.anim(ZulrahAssets.EMERGE)
        run.state = ZulrahState.EMERGING
        run.transitionCycle = clock.cycle + ZULRAH_EMERGE_TICKS
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.owns(npc) }

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { it.owns(npc) && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Zulrah encounter."
        if (!run.owns(npc)) return "That creature belongs to another encounter."
        if (npc === run.boss && run.state != ZulrahState.ACTIVE) {
            return "Zulrah cannot be attacked while beneath the swamp."
        }
        return null
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (run == null || !run.owns(npc) || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            if (run.state !in setOf(ZulrahState.ACTIVE, ZulrahState.DIVING, ZulrahState.EMERGING)) {
                hit.damage = 0
                return
            }
            hit.damage =
                ZulrahRules.cappedDamage(hit.damage) { range ->
                    range.first + roll(run, range.count())
                }
        }
    }

    fun beginBossDeath(npc: Npc): Run? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (
            run.state in
                setOf(
                    ZulrahState.DYING,
                    ZulrahState.RESPAWNING,
                    ZulrahState.COMPLETED,
                    ZulrahState.DISPOSED,
                )
        )
            return null
        run.state = ZulrahState.DYING
        clearHazards(run)
        run.killTicks = (clock.cycle - run.startCycle).coerceAtLeast(0)
        if (!run.debug) {
            val count = run.player.vars["varp.total_snakeboss_kills"] + 1
            VarPlayerIntMapSetter.set(run.player, "varp.total_snakeboss_kills", count)
            run.killcount = count
        }
        return run
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (runs[run.player.uid] !== run) return
        if (!run.debug) {
            val oldPb = run.player.attr[ZULRAH_PERSONAL_BEST_TICKS]
            val personalBest = oldPb == null || run.killTicks < oldPb
            if (personalBest) run.player.attr[ZULRAH_PERSONAL_BEST_TICKS] = run.killTicks
            val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
            run.player.mes(
                "<col=ff9900>Zulrah kill ${run.killcount}: ${ZulrahRules.formatTicks(run.killTicks)}.$suffix</col>"
            )
        } else {
            run.player.mes("Debug Zulrah defeated. No rewards, kill count, or record were awarded.")
        }
        if (run.debug) {
            run.state = ZulrahState.COMPLETED
        } else {
            run.state = ZulrahState.RESPAWNING
            run.transitionCycle = clock.cycle + ZULRAH_RESPAWN_TICKS
            run.player.combatClearQueue()
            run.player.mes("Zulrah will respawn in 10 seconds.")
        }
    }

    fun finishSnakelingDeath(npc: Npc) {
        runs.values.firstOrNull { npc in it.snakelings }?.snakelings?.remove(npc)
    }

    fun shouldAwardRewards(npc: Npc): Boolean =
        runs.values.firstOrNull { it.boss === npc }?.debug == false

    fun dropTile(npc: Npc): CoordGrid =
        runs.values
            .firstOrNull { it.boss === npc }
            ?.let { run -> ZulrahRules.rewardTile(run.player.coords, npc.coords) } ?: npc.coords

    fun entryScene(player: Player): ZulrahEntryScene? {
        val run = runs[player.uid] ?: return null
        return ZulrahEntryScene(
            camera = localCoord(run.session, ShrineTile(2268, 3058)),
            focus = localCoord(run.session, ShrineTile(2266, 3073)),
            cameraHeight = 1_800,
            focusHeight = 0,
            durationTicks = ZULRAH_ENTRY_CUTSCENE_TICKS,
        )
    }

    fun isActive(player: Player): Boolean = runs[player.uid]?.state != ZulrahState.DISPOSED

    fun escape(player: Player): Boolean {
        if (!runs.containsKey(player.uid)) return false
        abort(player, "left the shrine", teleport = true)
        player.mes("You leave Zulrah's shrine.")
        return true
    }

    fun abort(player: Player, reason: String, teleport: Boolean, logout: Boolean = false) {
        val run = runs.remove(player.uid) ?: return
        run.state = ZulrahState.DISPOSED
        clearHazards(run)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        player.lootDropDuration = null
        if (logout) {
            instances.handleLogout(player, clock.cycle)
        } else {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
        }
        prepare(player)
        log.info {
            "[Zulrah] player=${player.displayName} generation=${run.generation} abort=$reason"
        }
    }

    fun debugStatus(player: Player): String {
        val run = runs[player.uid] ?: return "Zulrah: no active encounter."
        val action =
            run.runtimePhase.actions.getOrNull(run.actionIndex)?.javaClass?.simpleName ?: "none"
        return "Zulrah rotation=${run.rotation.number} phase=${run.phaseIndex + 1}/${run.rotation.phases.size} " +
            "form=${run.runtimePhase.form} state=${run.state} action=$action:${run.actionProgress} " +
            "tick=${clock.cycle - run.startCycle} hp=${run.boss.hitpoints} clouds=${run.clouds.size} snakelings=${run.snakelings.size}"
    }

    private fun clearHazards(run: Run) {
        run.pendingHazards.clear()
        run.pendingTails.clear()
        run.clouds.forEach { world.locDelPrivate(run.player, it.visual) }
        run.clouds.clear()
        run.snakelings.keys.toList().forEach { npc ->
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        }
        run.snakelings.clear()
    }

    private fun respawn(run: Run) {
        if (runs[run.player.uid] !== run || !run.player.isSlotAssigned) return
        if (instances.sessionForPlayer(run.player)?.id != run.session.id) {
            abort(run.player, "left before Zulrah respawned", teleport = false)
            return
        }
        val rotation = ZulrahRotations.byNumber(random.of(ZulrahRotations.all.size) + 1)
        val phaseIndex = 0
        val phaseData = rotation.phases[phaseIndex]
        val boss =
            spawnNpc(
                run.player,
                run.session,
                internalFor(phaseData.form),
                phaseCoord(run.session, phaseData),
            )
        configureBoss(run.session, boss, phaseData, initial = true)
        run.boss = boss
        run.rotation = rotation
        run.phaseIndex = phaseIndex
        run.runtimePhase = phaseData
        run.state = ZulrahState.EMERGING
        run.transitionCycle = clock.cycle + ZULRAH_INITIAL_EMERGE_TICKS
        run.nextActionCycle = clock.cycle
        run.startCycle = clock.cycle
        run.actionIndex = 0
        run.actionProgress = 0
        run.completedRotations = 0
        run.killTicks = 0
        run.pendingHazards.clear()
        run.pendingTails.clear()
        run.player.combatClearQueue()
        run.player.clearInteraction()
        run.player.mes("Zulrah emerges from the swamp.")
        log.info {
            "[Zulrah] player=${run.player.displayName} generation=${run.generation} respawn rotation=${rotation.number}"
        }
    }

    private fun configureBoss(
        session: InstanceSession,
        boss: Npc,
        phase: ZulrahPhase,
        initial: Boolean,
    ) {
        boss.baseHitpointsLvl = ZULRAH_MAX_HITPOINTS
        boss.hitpoints = ZULRAH_MAX_HITPOINTS
        boss.baseDefenceLvl = 300
        boss.defenceLvl = 300
        boss.movementLocked = true
        boss.apRangeOverride = 32
        boss.apRequiresLineOfSight = true
        boss.anim(if (initial) ZulrahAssets.SPAWN else ZulrahAssets.EMERGE)
        faceForPhase(session, boss, phase.position)
    }

    private fun keepAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
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

    private fun phaseCoord(session: InstanceSession, phase: ZulrahPhase): CoordGrid =
        localCoord(session, ShrineTile(phase.position.x, phase.position.z))

    private fun faceForPhase(session: InstanceSession, boss: Npc, position: ZulrahPosition) {
        boss.faceSquare(localCoord(session, ShrineTile(position.faceX, position.faceZ)))
    }

    private fun localCoord(session: InstanceSession, tile: ShrineTile): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(0, tile.x shr 6, tile.z shr 6, tile.x and 63, tile.z and 63),
            )
        )

    private fun inArena(run: Run, tile: CoordGrid): Boolean {
        val southWest = localCoord(run.session, ShrineTile(2256, 3056))
        val northEast = localCoord(run.session, ShrineTile(2279, 3079))
        return tile.level == southWest.level &&
            tile.x in minOf(southWest.x, northEast.x)..maxOf(southWest.x, northEast.x) &&
            tile.z in minOf(southWest.z, northEast.z)..maxOf(southWest.z, northEast.z)
    }

    private fun playerProjectile(
        source: Npc,
        target: Player,
        graphic: String,
        controller: String,
    ): ProjAnim =
        ProjAnim.fromBoundsToPlayer(
            source.bounds(),
            target,
            graphic.asRSCM(RSCMType.SPOTANIM),
            controller,
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

    private fun armNextAction(run: Run, regularDelay: Int) {
        val delay =
            if (run.actionIndex >= run.runtimePhase.actions.size) ZULRAH_PHASE_END_TICKS
            else regularDelay
        run.nextActionCycle = clock.cycle + delay
    }

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
            "Missing Zulrah projectile controller: $internal"
        }

    private fun internalFor(form: ZulrahForm): String =
        when (form) {
            ZulrahForm.SERPENTINE -> ZulrahAssets.GREEN
            ZulrahForm.MAGMA -> ZulrahAssets.RED
            ZulrahForm.TANZANITE -> ZulrahAssets.BLUE
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Zulrah npc definition: $internal"
        }

    private fun roll(run: Run, bound: Int): Int =
        run.seededRandom?.nextInt(bound) ?: random.of(bound)

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        val generation: Long,
        var boss: Npc,
        var rotation: ZulrahRotation,
        var phaseIndex: Int,
        var runtimePhase: ZulrahPhase,
        var state: ZulrahState,
        var transitionCycle: Int,
        var nextActionCycle: Int,
        var startCycle: Int,
        val debug: Boolean,
        val seededRandom: Random?,
        var actionIndex: Int = 0,
        var actionProgress: Int = 0,
        var completedRotations: Int = 0,
        var killcount: Int = 0,
        var killTicks: Int = 0,
        val pendingHazards: MutableList<PendingHazard> = mutableListOf(),
        val pendingTails: MutableList<PendingTail> = mutableListOf(),
        val clouds: MutableList<ActiveCloud> = mutableListOf(),
        val snakelings: MutableMap<Npc, SnakelingState> = linkedMapOf(),
    ) {
        fun owns(npc: Npc): Boolean = npc === boss || npc in snakelings
    }
}

internal enum class ZulrahState {
    EMERGING,
    ACTIVE,
    DIVING,
    DYING,
    RESPAWNING,
    COMPLETED,
    DISPOSED,
}

internal enum class HazardKind {
    CLOUD,
    SNAKELING,
}

internal data class PendingHazard(val impactCycle: Int, val tile: CoordGrid, val kind: HazardKind)

internal data class PendingTail(val impactCycle: Int, val tile: CoordGrid)

internal data class ActiveCloud(val center: CoordGrid, val endCycle: Int, val visual: LocInfo)

internal data class SnakelingState(val magic: Boolean, val expireCycle: Int)

internal data class ZulrahEntryScene(
    val camera: CoordGrid,
    val focus: CoordGrid,
    val cameraHeight: Int,
    val focusHeight: Int,
    val durationTicks: Int,
)
