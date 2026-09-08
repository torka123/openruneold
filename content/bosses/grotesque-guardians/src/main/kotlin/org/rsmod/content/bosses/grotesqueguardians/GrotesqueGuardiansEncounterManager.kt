package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
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
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class GrotesqueGuardiansEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val accuracy: AccuracyFormulae,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
) {
    private val runs = mutableMapOf<PlayerUid, Run>()

    fun validateAssets() {
        (GrotesqueAssets.DAWN_FORMS + GrotesqueAssets.DUSK_FORMS).forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) {
                "Missing revision-240 Guardian NPC $id"
            }
        }
        (intArrayOf(GrotesqueAssets.ROOF_ENTRANCE, GrotesqueAssets.CLOISTER_BELL) +
                GrotesqueAssets.ENERGY_SPHERES)
            .forEach { id ->
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 Guardian location $id"
                }
            }
        GrotesqueAssets.ANIMATIONS.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) { "Missing Guardian animation $id" }
        }
    }

    fun enter(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the rooftop.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val dawnType = requireNpc(GrotesqueAssets.DAWN_PHASE_ONE)
        val duskType = requireNpc(GrotesqueAssets.DUSK_PHASE_ONE)
        val result =
            instances.create(
                owner = player,
                key = GROTESQUE_INSTANCE_KEY,
                spec = grotesqueSpec(dawnType, duskType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Slayer Tower roof is unavailable."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        player.faceDirection(Direction.North)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val dawn = spawnGuardian(player, result.session, dawnType, GuardianRole.DAWN)
        val dusk = spawnGuardian(player, result.session, duskType, GuardianRole.DUSK)
        runs[player.uid] = Run(player, result.session, dawn, dusk)
        player.mes("Ring the Cloister Bell to awaken the Grotesque Guardians.")
        return true
    }

    fun ringBell(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.phase != GrotesquePhase.DORMANT) {
            player.mes("Concentrate on your fight!")
            return false
        }
        player.anim(GrotesqueAssets.PLAYER_RING_ANIMATION)
        run.phase = GrotesquePhase.DAWN_ONE
        run.generation++
        run.dawn.anim(GrotesqueAssets.DAWN_DESCEND_ANIMATION)
        run.dusk.anim(GrotesqueAssets.DUSK_TRANSFORM_ANIMATION)
        run.dawn.actionDelay = clock.cycle + 2
        run.dusk.actionDelay = clock.cycle + 2
        run.dawn.apPlayer2(player, interactions)
        run.dusk.apPlayer2(player, interactions)
        player.mes("The Grotesque Guardians awaken.")
        return true
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (!access.npc.isSpawnOwnedBy(target) || access.actionDelay > clock.cycle) return
        when {
            access.npc === run.dawn &&
                run.phase in setOf(GrotesquePhase.DAWN_ONE, GrotesquePhase.DAWN_THREE) ->
                dawnAttack(run)
            access.npc === run.dusk &&
                run.phase in
                    setOf(
                        GrotesquePhase.DAWN_ONE,
                        GrotesquePhase.DUSK_TWO,
                        GrotesquePhase.DAWN_THREE,
                        GrotesquePhase.DUSK_FOUR,
                    ) -> duskAttack(run)
            else -> return
        }
        access.actionDelay = clock.cycle + GrotesqueGuardiansRules.ATTACK_RATE_TICKS
    }

    private fun dawnAttack(run: Run) {
        run.dawnAttacks++
        if (run.dawnAttacks % 4 == 0) {
            launchStoneBall(run)
            return
        }
        val melee = distanceToFootprint(run.dawn, run.player) <= 1 && run.dawnAttacks % 2 == 0
        if (melee) {
            run.dawn.anim(GrotesqueAssets.DAWN_MELEE_ANIMATION)
            queueStandardHit(
                run,
                run.dawn,
                GuardianAttackStyle.MELEE,
                GrotesqueGuardiansRules.DAWN_MELEE_MAX_HIT,
                1,
            )
        } else {
            run.dawn.anim(GrotesqueAssets.DAWN_RANGED_ANIMATION)
            repeat(2) { index ->
                launchRanged(
                    run,
                    run.dawn,
                    GrotesqueGuardiansRules.DAWN_RANGED_MAX_HIT,
                    index,
                    phaseFour = false,
                )
            }
        }
    }

    private fun duskAttack(run: Run) {
        run.duskAttacks++
        when (run.phase) {
            GrotesquePhase.DUSK_TWO -> {
                if (run.duskAttacks % 3 == 0) {
                    launchBlindingExplosion(run)
                } else {
                    run.dusk.anim(
                        if (run.duskAttacks % 2 == 0) GrotesqueAssets.DUSK_MELEE_ANIMATION
                        else GrotesqueAssets.DUSK_MELEE_SECOND_ANIMATION
                    )
                    queueStandardHit(
                        run,
                        run.dusk,
                        GuardianAttackStyle.MELEE,
                        GrotesqueGuardiansRules.DUSK_PHASE_TWO_MAX_HIT,
                        1,
                    )
                }
                if (clock.cycle >= run.nextDebrisCycle) launchDebris(run)
            }
            GrotesquePhase.DUSK_FOUR -> {
                if (clock.cycle >= run.nextPrisonCycle) {
                    launchPrison(run)
                } else if (run.duskAttacks % 2 == 0) {
                    run.dusk.anim(GrotesqueAssets.DUSK_RANGED_ANIMATION)
                    repeat(2) { index ->
                        launchRanged(
                            run,
                            run.dusk,
                            GrotesqueGuardiansRules.DUSK_PHASE_FOUR_RANGED_MAX_HIT,
                            index,
                            phaseFour = true,
                        )
                    }
                } else {
                    run.dusk.anim(GrotesqueAssets.DUSK_PHASE_FOUR_MELEE_ANIMATION)
                    queueStandardHit(
                        run,
                        run.dusk,
                        GuardianAttackStyle.MELEE,
                        GrotesqueGuardiansRules.DUSK_PHASE_FOUR_MELEE_MAX_HIT,
                        1,
                    )
                }
            }
            else -> {
                run.dusk.anim(GrotesqueAssets.DUSK_MELEE_ANIMATION)
                queueStandardHit(
                    run,
                    run.dusk,
                    GuardianAttackStyle.MELEE,
                    GrotesqueGuardiansRules.DUSK_PHASE_TWO_MAX_HIT,
                    1,
                )
            }
        }
    }

    private fun queueStandardHit(
        run: Run,
        source: Npc,
        style: GuardianAttackStyle,
        maxHit: Int,
        delay: Int,
    ) {
        source.facePlayer(run.player)
        val accurate =
            when (style) {
                GuardianAttackStyle.MELEE ->
                    accuracy.rollMeleeAccuracy(source, run.player, MeleeAttackType.Slash, random)
                GuardianAttackStyle.RANGED ->
                    accuracy.rollRangedAccuracy(source, run.player, random)
            }
        val damage = if (accurate) random.of(maxHit + 1) else 0
        val protected = isProtected(run.player, style)
        run.player.queueHit(
            source = source,
            delay = delay.coerceAtLeast(1),
            type = if (style == GuardianAttackStyle.MELEE) HitType.Melee else HitType.Ranged,
            damage = damage,
            modifier =
                NpcCalculatedProtectionPrayerPlayerHitModifier(
                    protectedAtCalculation = protected,
                    reductionPercent = 80,
                ),
        )
    }

    private fun launchRanged(
        run: Run,
        source: Npc,
        maxHit: Int,
        componentDelay: Int,
        phaseFour: Boolean,
    ) {
        val projectile =
            ProjAnim.fromNpcToPlayer(
                source,
                run.player,
                GrotesqueAssets.RANGED_PROJECTILE,
                RANGED_PROFILE,
            )
        world.projAnim(projectile)
        val accurate = accuracy.rollRangedAccuracy(source, run.player, random)
        val protected = run.player.vars["varbit.prayer_protectfrommissiles"] == 1
        val damage =
            if (phaseFour && componentDelay == 0) {
                GrotesqueGuardiansRules.phaseFourRangedFirstHit(accurate, protected)
            } else if (phaseFour) {
                val first = if (accurate) random.of(maxHit + 1) else 0
                GrotesqueGuardiansRules.phaseFourRangedSecondHit(
                    accurate,
                    first,
                    random.of(first + 1),
                )
            } else if (accurate) {
                random.of(maxHit + 1)
            } else 0
        run.player.queueHit(
            source = source,
            delay = projectile.serverCycles.coerceAtLeast(1) + componentDelay,
            type = HitType.Ranged,
            damage = damage,
            modifier =
                if (phaseFour) NpcBypassProtectionPrayerPlayerHitModifier
                else
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedAtCalculation = protected,
                        reductionPercent = 80,
                    ),
        )
    }

    private fun launchStoneBall(run: Run) {
        run.dawn.anim(GrotesqueAssets.DAWN_SPHERE_ANIMATION)
        val tile = run.player.coords
        world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.DEBRIS_SHADOW), tile)
        val generation = run.generation
        worldQueues.add(3) {
            if (!isCurrent(run, generation)) return@add
            world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.DEBRIS_IMPACT), tile)
            if (
                GrotesqueGuardiansRules.inDebrisArea(
                    run.player.coords.x,
                    run.player.coords.z,
                    tile.x,
                    tile.z,
                )
            ) {
                run.player.queueHit(
                    source = run.dawn,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(16),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
                if (
                    GrotesqueGuardiansRules.directlyUnderDebris(
                        run.player.coords.x,
                        run.player.coords.z,
                        tile.x,
                        tile.z,
                    )
                ) {
                    run.player.actionDelay =
                        maxOf(
                            run.player.actionDelay,
                            clock.cycle + GrotesqueGuardiansRules.DEBRIS_STUN_TICKS,
                        )
                    run.player.mes("You have been trapped in stone!")
                }
            }
        }
    }

    private fun launchBlindingExplosion(run: Run) {
        run.dusk.anim(GrotesqueAssets.DUSK_EXPLOSION_ANIMATION)
        val generation = run.generation
        worldQueues.add(2) {
            if (!isCurrent(run, generation) || distanceToFootprint(run.dusk, run.player) > 1)
                return@add
            run.player.queueHit(
                source = run.dusk,
                delay = 1,
                type = HitType.Typeless,
                damage = 20 + random.of(14),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun launchDebris(run: Run) {
        run.nextDebrisCycle = clock.cycle + 25
        val generation = run.generation
        repeat(3) { wave ->
            worldQueues.add(wave) {
                if (!isCurrent(run, generation)) return@add
                repeat(3) {
                    val tile = randomArenaTile(run)
                    world.spotanimMapPrivate(
                        run.player,
                        SpotanimType(GrotesqueAssets.DEBRIS_SHADOW),
                        tile,
                    )
                    worldQueues.add(3) {
                        if (!isCurrent(run, generation)) return@add
                        world.spotanimMapPrivate(
                            run.player,
                            SpotanimType(GrotesqueAssets.DEBRIS_IMPACT),
                            tile,
                        )
                        if (
                            GrotesqueGuardiansRules.inDebrisArea(
                                run.player.coords.x,
                                run.player.coords.z,
                                tile.x,
                                tile.z,
                            )
                        ) {
                            run.player.queueHit(
                                source = run.dawn,
                                delay = 1,
                                type = HitType.Typeless,
                                damage = 15 + random.of(8),
                                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                            )
                            if (
                                GrotesqueGuardiansRules.directlyUnderDebris(
                                    run.player.coords.x,
                                    run.player.coords.z,
                                    tile.x,
                                    tile.z,
                                )
                            ) {
                                run.player.actionDelay =
                                    maxOf(
                                        run.player.actionDelay,
                                        clock.cycle + GrotesqueGuardiansRules.DEBRIS_STUN_TICKS,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (run == null || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        val role =
            if (npc === run.dawn) GuardianRole.DAWN
            else if (npc === run.dusk) GuardianRole.DUSK else null
        if (role == null || !GrotesqueGuardiansRules.canDamage(role, run.phase)) {
            hit.damage = 0
            return
        }
        if (role == GuardianRole.DUSK && hit.type != HitType.Melee) {
            hit.damage = 0
            player.mes("Dusk's stone armour resists that attack.")
            return
        }
        if (role == GuardianRole.DAWN && hit.type == HitType.Melee && !isHalberd(player)) {
            hit.damage = 0
            player.mes("Dawn is flying too high for that weapon.")
            return
        }
        when (run.phase) {
            GrotesquePhase.DAWN_ONE,
            GrotesquePhase.DUSK_TWO -> {
                hit.damage = GrotesqueGuardiansRules.capDamageAtThreshold(npc.hitpoints, hit.damage)
                if (npc.hitpoints - hit.damage <= GrotesqueGuardiansRules.PHASE_THRESHOLD) {
                    if (run.phase == GrotesquePhase.DAWN_ONE) beginPhaseTwo(run)
                    else beginLightning(run)
                }
            }
            GrotesquePhase.DAWN_THREE -> {
                hit.damage = GrotesqueGuardiansRules.capDawnFinalDamage(npc.hitpoints, hit.damage)
                if (npc.hitpoints - hit.damage <= GrotesqueGuardiansRules.FINISH_THRESHOLD) {
                    player.mes("Dawn is weakened. Finish her with a rock hammer.")
                }
            }
            else -> Unit
        }
    }

    private fun beginPhaseTwo(run: Run) {
        if (run.phase != GrotesquePhase.DAWN_ONE) return
        run.phase = GrotesquePhase.DAWN_ASCENDING
        run.generation++
        run.dawn.clearInteraction()
        run.dawn.anim(GrotesqueAssets.DAWN_ASCEND_ANIMATION)
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_DEFENSIVE), Int.MAX_VALUE)
        run.dawn.movementLocked = true
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_PHASE_TWO), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.phase = GrotesquePhase.DUSK_TWO
        run.duskAttacks = 0
        run.nextDebrisCycle = clock.cycle + 8
        run.dusk.apPlayer2(run.player, interactions)
    }

    private fun beginLightning(run: Run) {
        if (run.phase != GrotesquePhase.DUSK_TWO) return
        run.phase = GrotesquePhase.LIGHTNING_TRANSITION
        run.generation++
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_PHASE_THREE), Int.MAX_VALUE)
        run.dawn.anim(GrotesqueAssets.DAWN_DESCEND_ANIMATION)
        run.dawn.movementLocked = true
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_LIGHTNING), Int.MAX_VALUE)
        run.dusk.anim(GrotesqueAssets.DUSK_AOE_ANIMATION)
        run.dusk.movementLocked = true
        val warning = clock.cycle
        val tiles = linkedSetOf(run.player.coords)
        while (tiles.size < 18) tiles += randomArenaTile(run)
        run.lightning += tiles.map { Lightning(it, warning + 2, warning + 10) }
        run.lightning.forEach { world.spotanimMapPrivate(run.player, SpotanimType(1416), it.tile) }
        run.stateCycle = clock.cycle + 12
    }

    private fun finishLightning(run: Run) {
        run.lightning.clear()
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_PHASE_THREE), Int.MAX_VALUE)
        run.dawn.movementLocked = false
        run.dawn.actionDelay = clock.cycle + 1
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_DEFENSIVE), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.dusk.actionDelay = clock.cycle + 1
        run.phase = GrotesquePhase.DAWN_THREE
        run.dawnAttacks = 0
        spawnSpheres(run)
        run.dawn.apPlayer2(run.player, interactions)
        run.dusk.apPlayer2(run.player, interactions)
    }

    private fun spawnSpheres(run: Run) {
        clearSpheres(run)
        GrotesqueAssets.ENERGY_SPHERES.forEach { id ->
            val tile = randomArenaTile(run)
            val loc =
                LocInfo(
                    LocLayerConstants.of(LocShape.CentrepieceStraight.id),
                    tile,
                    LocEntity(id, LocShape.CentrepieceStraight.id, LocAngle.North.id),
                )
            world.locAddPrivate(run.player, loc)
            run.spheres += EnergySphere(loc, clock.cycle)
        }
    }

    fun absorbSphere(player: Player, coords: CoordGrid): Boolean {
        val run = runs[player.uid] ?: return false
        val sphere = run.spheres.firstOrNull { it.loc.coords == coords } ?: return false
        world.locDelPrivate(player, sphere.loc)
        run.spheres.remove(sphere)
        player.mes("You absorb the energy sphere.")
        return true
    }

    fun useHammer(player: Player, npc: Npc, item: String): Boolean {
        val run = runs[player.uid] ?: return false
        if (npc !== run.dawn || run.phase != GrotesquePhase.DAWN_THREE) return false
        if (item !in GrotesqueGuardiansRules.ROCK_HAMMERS) {
            player.mes("You need a rock hammer to finish Dawn.")
            return false
        }
        if (run.dawn.hitpoints > GrotesqueGuardiansRules.FINISH_THRESHOLD) {
            player.mes("Dawn is not weak enough to finish.")
            return false
        }
        run.phase = GrotesquePhase.DAWN_DYING
        run.generation++
        clearSpheres(run)
        run.dawn.clearInteraction()
        run.dawn.transmog(requireNpc(GrotesqueAssets.DAWN_DEATH), Int.MAX_VALUE)
        run.dawn.anim(GrotesqueAssets.DAWN_DEATH_ANIMATION)
        run.stateCycle = clock.cycle + 5
        return true
    }

    private fun beginPhaseFour(run: Run) {
        run.dusk.transmog(requireNpc(GrotesqueAssets.DUSK_PHASE_FOUR), Int.MAX_VALUE)
        run.dusk.movementLocked = false
        run.dusk.actionDelay = clock.cycle + 2
        run.phase = GrotesquePhase.DUSK_FOUR
        run.duskAttacks = 0
        run.nextPrisonCycle = clock.cycle
        run.dusk.apPlayer2(run.player, interactions)
    }

    private fun launchPrison(run: Run) {
        run.nextPrisonCycle = clock.cycle + 42
        run.dusk.anim(GrotesqueAssets.DUSK_PRISON_ANIMATION)
        val centre = run.player.coords
        val safe = CoordGrid(centre.x + 2, centre.z, centre.level)
        val flames = linkedSetOf<CoordGrid>()
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (kotlin.math.abs(dx) != 2 && kotlin.math.abs(dz) != 2) continue
                val tile = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                if (tile != safe) flames += tile
            }
        }
        flames.forEach {
            world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.PRISON_FLAME), it)
        }
        val generation = run.generation
        worldQueues.add(GrotesqueGuardiansRules.PRISON_ESCAPE_TICKS) {
            if (!isCurrent(run, generation)) return@add
            if (run.player.coords.chebyshevDistance(centre) <= 1) {
                val damage = 45 + random.of(21)
                run.player.queueHit(
                    source = run.dusk,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
                run.dusk.hitpoints =
                    GrotesqueGuardiansRules.healAfterPrisonDamage(run.dusk.hitpoints, damage)
            }
        }
    }

    fun tickAll() {
        runs.values.toList().forEach { run ->
            if (!run.player.isSlotAssigned || run.phase == GrotesquePhase.INVALIDATED)
                return@forEach
            if (run.phase == GrotesquePhase.LIGHTNING_TRANSITION) {
                tickLightning(run)
                if (clock.cycle >= run.stateCycle) finishLightning(run)
            }
            if (run.phase == GrotesquePhase.DAWN_DYING && clock.cycle >= run.stateCycle) {
                beginPhaseFour(run)
            }
            if (run.phase == GrotesquePhase.DAWN_THREE) tickSpheres(run)
            if (run.phase == GrotesquePhase.RESPAWNING && clock.cycle >= run.stateCycle) {
                reset(run)
            }
            keepAggressive(run)
        }
    }

    private fun tickLightning(run: Run) {
        val tile = run.player.coords
        if (
            run.lightning.any {
                clock.cycle in it.activeCycle..it.endCycle &&
                    GrotesqueGuardiansRules.inLightningArea(tile.x, tile.z, it.tile.x, it.tile.z)
            }
        ) {
            run.player.queueHit(
                source = run.dusk,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    random.of(
                        GrotesqueGuardiansRules.LIGHTNING_MIN_HIT..GrotesqueGuardiansRules
                                .LIGHTNING_MAX_HIT
                    ),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun tickSpheres(run: Run) {
        val returning =
            run.spheres.filter {
                GrotesqueGuardiansRules.sphereState(clock.cycle - it.spawnCycle) ==
                    SphereState.RETURNING
            }
        returning.forEach { sphere ->
            world.locDelPrivate(run.player, sphere.loc)
            run.spheres.remove(sphere)
            run.dawn.hitpoints =
                (run.dawn.hitpoints + GrotesqueGuardiansRules.SPHERE_HEAL).coerceAtMost(
                    GrotesqueGuardiansRules.HITPOINTS
                )
        }
    }

    private fun keepAggressive(run: Run) {
        if (run.phase in setOf(GrotesquePhase.DAWN_ONE, GrotesquePhase.DAWN_THREE)) {
            if (run.dawn.mode == null && run.dawn.hitpoints > 0)
                run.dawn.apPlayer2(run.player, interactions)
        }
        if (
            run.phase in
                setOf(
                    GrotesquePhase.DAWN_ONE,
                    GrotesquePhase.DUSK_TWO,
                    GrotesquePhase.DAWN_THREE,
                    GrotesquePhase.DUSK_FOUR,
                )
        ) {
            if (run.dusk.mode == null && run.dusk.hitpoints > 0)
                run.dusk.apPlayer2(run.player, interactions)
        }
    }

    fun beginDuskDeath(npc: Npc): CoordGrid? {
        val run = runs.values.firstOrNull { it.dusk === npc } ?: return null
        if (run.phase != GrotesquePhase.DUSK_FOUR) return null
        run.phase = GrotesquePhase.DYING
        run.generation++
        clearSpheres(run)
        run.dawn.clearInteraction()
        run.dusk.clearInteraction()
        run.player.combatClearQueue()
        return run.player.coords
    }

    fun finishDuskDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.dusk === npc } ?: return
        run.phase = GrotesquePhase.RESPAWNING
        run.stateCycle = clock.cycle + GrotesqueGuardiansRules.RESPAWN_TICKS
    }

    private fun reset(run: Run) {
        run.generation++
        clearSpheres(run)
        if (run.dawn.isSlotAssigned) npcs.del(run.dawn, Int.MAX_VALUE)
        if (run.dusk.isSlotAssigned) npcs.del(run.dusk, Int.MAX_VALUE)
        run.dawn =
            spawnGuardian(
                run.player,
                run.session,
                requireNpc(GrotesqueAssets.DAWN_PHASE_ONE),
                GuardianRole.DAWN,
            )
        run.dusk =
            spawnGuardian(
                run.player,
                run.session,
                requireNpc(GrotesqueAssets.DUSK_PHASE_ONE),
                GuardianRole.DUSK,
            )
        run.phase = GrotesquePhase.DORMANT
        run.dawnAttacks = 0
        run.duskAttacks = 0
        run.player.mes("The rooftop falls quiet. Ring the bell when you are ready.")
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.dawn === npc || it.dusk === npc }

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let {
            (it.dawn === npc || it.dusk === npc) && npc.isSpawnOwnedBy(player)
        } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Guardians encounter."
        val role =
            if (npc === run.dawn) GuardianRole.DAWN
            else if (npc === run.dusk) GuardianRole.DUSK
            else return "That is not part of your encounter."
        return if (GrotesqueGuardiansRules.canDamage(role, run.phase)) null
        else if (run.phase == GrotesquePhase.DORMANT) "Ring the Cloister Bell first."
        else "That Guardian cannot be harmed during this phase."
    }

    fun leave(player: Player): Boolean {
        val run = runs.remove(player.uid) ?: return false
        run.phase = GrotesquePhase.INVALIDATED
        clearRun(run)
        val exit = instances.leave(player, run.session, clock.cycle)
        if (player.isSlotAssigned) PathingEntityCommon.telejump(player, collision, exit)
        prepare(player)
        player.mes("You leave the Slayer Tower roof.")
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = runs[player.uid] ?: return RaidLeaveResult.Unhandled
        if (run.session.id != expected) return RaidLeaveResult.Unhandled
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        val run = runs[player.uid] ?: return
        if (run.session.id != instanceId) return
        runs.remove(player.uid)
        run.phase = GrotesquePhase.INVALIDATED
        clearRun(run)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) =
        membershipEnded(player, instanceId)

    fun logout(player: Player) {
        val run = runs[player.uid] ?: return
        if (instances.sessionForPlayer(player)?.id == run.session.id)
            instances.handleLogout(player, clock.cycle)
        else membershipEnded(player, run.session.id)
    }

    fun playerDied(player: Player) {
        val run = runs.remove(player.uid) ?: return
        run.phase = GrotesquePhase.INVALIDATED
        clearRun(run)
    }

    private fun clearRun(run: Run) {
        run.generation++
        prepare(run.player)
        clearSpheres(run)
        run.lightning.clear()
        run.dawn.clearInteraction()
        run.dusk.clearInteraction()
        if (run.dawn.isSlotAssigned) npcs.del(run.dawn, Int.MAX_VALUE)
        if (run.dusk.isSlotAssigned) npcs.del(run.dusk, Int.MAX_VALUE)
    }

    private fun clearSpheres(run: Run) {
        run.spheres.toList().forEach { world.locDelPrivate(run.player, it.loc) }
        run.spheres.clear()
    }

    private fun spawnGuardian(
        player: Player,
        session: InstanceSession,
        type: dev.openrune.types.NpcServerType,
        role: GuardianRole,
    ): Npc {
        val tile =
            if (role == GuardianRole.DAWN)
                localCoord(session, GROTESQUE_DAWN_LOCAL_X, GROTESQUE_DAWN_LOCAL_Z)
            else localCoord(session, GROTESQUE_DUSK_LOCAL_X, GROTESQUE_DUSK_LOCAL_Z)
        val npc = Npc(type, tile)
        npc.baseHitpointsLvl = GrotesqueGuardiansRules.HITPOINTS
        npc.hitpoints = GrotesqueGuardiansRules.HITPOINTS
        npc.respawns = false
        npc.movementLocked = false
        npc.moveRestrict = MoveRestrict.PassThru
        npc.apRangeOverride = if (role == GuardianRole.DAWN) 10 else 1
        npc.apRequiresLineOfSight = true
        npc.maxOpRangeOverride = 64
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, Int.MAX_VALUE)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun localCoord(session: InstanceSession, x: Int, z: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(
                    GROTESQUE_SOURCE_LEVEL,
                    GROTESQUE_SOURCE_MAP_X,
                    GROTESQUE_SOURCE_MAP_Z,
                    x,
                    z,
                ),
            )
        )

    private fun randomArenaTile(run: Run): CoordGrid =
        localCoord(
            run.session,
            random.of(GROTESQUE_ARENA_MIN_X..GROTESQUE_ARENA_MAX_X),
            random.of(GROTESQUE_ARENA_MIN_Z..GROTESQUE_ARENA_MAX_Z),
        )

    private fun isCurrent(run: Run, generation: Int = run.generation): Boolean =
        runs[run.player.uid] === run &&
            run.generation == generation &&
            run.player.isSlotAssigned &&
            run.player.hitpoints > 0 &&
            run.phase !in
                setOf(
                    GrotesquePhase.DORMANT,
                    GrotesquePhase.INVALIDATED,
                    GrotesquePhase.DYING,
                    GrotesquePhase.RESPAWNING,
                )

    private fun isHalberd(player: Player): Boolean {
        val weapon = player.righthand
        val internal =
            weapon?.let { ServerCacheManager.getItem(it.id)?.internalName } ?: return false
        return internal.contains("halberd") || internal.contains("golembane")
    }

    private fun isProtected(player: Player, style: GuardianAttackStyle): Boolean =
        when (style) {
            GuardianAttackStyle.MELEE -> player.vars["varbit.prayer_protectfrommelee"] == 1
            GuardianAttackStyle.RANGED -> player.vars["varbit.prayer_protectfrommissiles"] == 1
        }

    private fun distanceToFootprint(npc: Npc, player: Player): Int {
        val minX = npc.coords.x
        val minZ = npc.coords.z
        val maxX = minX + npc.visType.size - 1
        val maxZ = minZ + npc.visType.size - 1
        val dx =
            when {
                player.coords.x < minX -> minX - player.coords.x
                player.coords.x > maxX -> player.coords.x - maxX
                else -> 0
            }
        val dz =
            when {
                player.coords.z < minZ -> minZ - player.coords.z
                player.coords.z > maxZ -> player.coords.z - maxZ
                else -> 0
            }
        return maxOf(dx, dz)
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Guardian NPC $id" }

    private data class EnergySphere(val loc: LocInfo, val spawnCycle: Int)

    private data class Lightning(val tile: CoordGrid, val activeCycle: Int, val endCycle: Int)

    private data class Run(
        val player: Player,
        val session: InstanceSession,
        var dawn: Npc,
        var dusk: Npc,
        var phase: GrotesquePhase = GrotesquePhase.DORMANT,
        var generation: Int = 1,
        var stateCycle: Int = 0,
        var dawnAttacks: Int = 0,
        var duskAttacks: Int = 0,
        var nextDebrisCycle: Int = 0,
        var nextPrisonCycle: Int = 0,
        val spheres: MutableList<EnergySphere> = mutableListOf(),
        val lightning: MutableList<Lightning> = mutableListOf(),
    )

    private companion object {
        val RANGED_PROFILE =
            ProjAnimType(
                startHeight = 90,
                endHeight = 40,
                delay = 0,
                angle = 32,
                lengthAdjustment = 0,
                progress = 0,
                stepMultiplier = 5,
            )
    }
}
