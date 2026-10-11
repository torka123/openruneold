package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal enum class HydraRunState {
    OBSERVATION,
    ACTIVE,
    TRANSITION,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

internal data class HydraPendingPoison(val tile: CoordGrid, val impactCycle: Int)

internal data class HydraPoisonPool(val tile: CoordGrid, val endCycle: Int)

internal data class HydraLightningCurrent(var tile: CoordGrid, val endCycle: Int)

internal class HydraRun(
    val session: InstanceSession,
    val player: Player,
    val boss: Npc,
    var encounter: BossEncounter,
    var initialStyle: HydraAttackStyle,
) {
    var phase = HydraPhase.GREEN
    var state = HydraRunState.OBSERVATION
    var lastBasicStyle = initialStyle
    var weakened = false
    var wrongVentStacks = 0
    var basicAttacks = 0
    var transformEndsAt = 0
    var pendingRespawn = false
    var stunImmuneUntilCycle = 0
    var nextVentCycle = 0
    var pendingLightningCycle = Int.MAX_VALUE
    var lastPoolDamageCycle = -1
    var flameEndCycle = 0
    var trackingFlame: CoordGrid? = null
    var fireBurnsRemaining = 0
    var nextBurnCycle = 0
    var lastFireContactCycle = -1
    val pendingPoison = mutableListOf<HydraPendingPoison>()
    val poisonPools = mutableListOf<HydraPoisonPool>()
    val lightningCurrents = mutableListOf<HydraLightningCurrent>()
    val flameTiles = linkedSetOf<CoordGrid>()
}

@Singleton
internal class HydraEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val world: WorldRepository,
) {
    private val runsByInstance = mutableMapOf<InstanceId, HydraRun>()
    private val runsByNpc = java.util.IdentityHashMap<Npc, HydraRun>()

    fun registerExtensions() {
        deps.extensionRegistry.register(HYDRA_BASIC_HANDLER) { _, npc, _, params ->
            val style = params as? HydraAttackStyle ?: return@register
            onBasicAttack(npc, style)
        }
        deps.extensionRegistry.register(HYDRA_SPECIAL_HANDLER) { _, npc, _, params ->
            val phase = params as? HydraPhase ?: return@register
            launchSpecial(npc, phase)
        }
    }

    fun onJoin(player: Player, session: InstanceSession) {
        val boss =
            instances.npcsForInstance(session.id).singleOrNull { it.type.id == HydraAssets.GREEN }
                ?: return
        val initialStyle = if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED
        val encounter = deps.encounterRegistry.of(boss)
        val run = HydraRun(session, player, boss, encounter, initialStyle)
        runsByInstance[session.id] = run
        runsByNpc[boss] = run
        prepareObservation(run)
        encounter.transitionTo(abilityName(HydraPhase.GREEN, initialStyle), clock.cycle)
    }

    fun onLeave(player: Player, instanceId: InstanceId) {
        val run = runsByInstance[instanceId] ?: return
        if (run.player === player) release(run)
    }

    fun onEnded(instanceId: InstanceId) {
        runsByInstance.remove(instanceId)?.let(::release)
    }

    fun onDelete(npc: Npc) {
        runsByNpc.remove(npc)?.let { run ->
            runsByInstance.remove(run.session.id)
            clearHazards(run)
            run.state = HydraRunState.INVALIDATED
        }
    }

    fun onRespawn(npc: Npc) {
        val run = runsByNpc[npc] ?: return
        if (run.state == HydraRunState.RESPAWNING) run.pendingRespawn = true
    }

    fun onLateCycle() {
        for (run in runsByInstance.values.toList()) {
            if (run.state == HydraRunState.INVALIDATED) continue
            if (run.pendingRespawn) {
                finishRespawn(run)
                continue
            }
            processPoison(run)
            processLightning(run)
            processFlame(run)
            when (run.state) {
                HydraRunState.OBSERVATION -> Unit
                HydraRunState.ACTIVE -> {
                    processVent(run)
                    keepAggressive(run)
                }
                HydraRunState.TRANSITION -> {
                    if (clock.cycle >= run.transformEndsAt) finishTransition(run)
                }
                HydraRunState.DYING,
                HydraRunState.RESPAWNING,
                HydraRunState.INVALIDATED -> Unit
            }
        }
    }

    suspend fun crossDoor(access: org.rsmod.api.player.protect.ProtectedAccess) {
        val run = runFor(access.player) ?: return
        when (run.state) {
            HydraRunState.OBSERVATION -> {
                val chamber = resolve(run, CHAMBER_LOCAL_X, CHAMBER_LOCAL_Z)
                access.telejump(chamber)
                activate(run)
            }
            HydraRunState.RESPAWNING -> access.player.mes("The chamber is open while the Alchemical Hydra reforms.")
            HydraRunState.ACTIVE,
            HydraRunState.TRANSITION -> access.player.mes("The alchemical doors are locked during the fight.")
            HydraRunState.DYING,
            HydraRunState.INVALIDATED -> Unit
        }
    }

    fun leave(player: Player): Boolean {
        val run = runFor(player) ?: return false
        release(run)
        return true
    }

    fun status(player: Player): String {
        val run = runFor(player) ?: return "Hydra: no active encounter."
        return "Hydra: ${run.phase.name.lowercase()}, ${run.state.name.lowercase()}, " +
            "${run.boss.hitpoints}/${HydraRules.MAX_HITPOINTS} HP, " +
            "weakened=${run.weakened}, wrong vents=${run.wrongVentStacks}."
    }

    fun isEncounterNpc(npc: Npc): Boolean = runsByNpc.containsKey(npc)

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runFor(player)?.let { it.boss === npc && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runFor(player) ?: return "That Alchemical Hydra is not in your private encounter."
        if (run.boss !== npc) return "That Hydra belongs to another encounter."
        return if (run.state == HydraRunState.ACTIVE) null
        else "The Alchemical Hydra cannot be attacked from the observation room or during a transition."
    }

    fun modifyIncoming(npc: Npc, hit: HitBuilder) {
        val run = runsByNpc[npc] ?: return
        if (run.state != HydraRunState.ACTIVE || !npc.isSpawnOwnedBy(run.player)) {
            hit.damage = 0
            return
        }
        hit.damage = HydraRules.incomingDamage(hit.damage, run.weakened || run.phase == HydraPhase.FINAL)
        hit.damage = HydraRules.capAtPhaseFloor(run.phase, npc.hitpoints, hit.damage)
    }

    fun onHit(npc: Npc, damage: Int) {
        val run = runsByNpc[npc] ?: return
        val threshold = HydraRules.threshold(run.phase) ?: return
        if (run.state == HydraRunState.ACTIVE && damage > 0 && npc.hitpoints <= threshold) beginTransition(run)
    }

    fun beginDeath(npc: Npc): CoordGrid? {
        val run = runsByNpc[npc] ?: return null
        if (run.phase != HydraPhase.FINAL || run.state != HydraRunState.ACTIVE) return null
        run.state = HydraRunState.DYING
        deps.encounterRegistry.remove(npc)
        clearHazards(run)
        run.player.combatClearQueue()
        npc.anim(RSCM.getReverseMapping(RSCMType.SEQ, HydraAssets.DEATH_ANIM))
        return run.player.coords
    }

    fun afterDeath(npc: Npc) {
        val run = runsByNpc[npc] ?: return
        if (run.state != HydraRunState.DYING) return
        run.state = HydraRunState.RESPAWNING
        val kills = run.player.vars["varp.kc_hydra"]
        run.player.mes("Alchemical Hydra kill $kills. Its drops land at your feet.")
        run.player.mes("The Alchemical Hydra will reform in about 26 seconds.")
    }

    fun rollBasicHit(npc: Npc, phase: HydraPhase): Int {
        val run = runsByNpc[npc] ?: return random.of(HydraRules.basicMaxHit(phase, false) + 1)
        val maxHit = HydraRules.empoweredDamage(HydraRules.basicMaxHit(phase, run.weakened), run.wrongVentStacks)
        return random.of(maxHit + 1)
    }

    fun basicHitCount(npc: Npc, phase: HydraPhase): Int =
        runsByNpc[npc]?.let { HydraRules.basicHitCount(phase, it.weakened) } ?: 1

    private fun onBasicAttack(npc: Npc, style: HydraAttackStyle) {
        val run = runsByNpc[npc] ?: return
        if (run.state != HydraRunState.ACTIVE) return
        run.lastBasicStyle = style
        run.basicAttacks++
        if (HydraRules.specialDue(run.basicAttacks)) {
            run.encounter.forceNext(specialName(run.phase))
        }
    }

    private fun launchSpecial(npc: Npc, phase: HydraPhase) {
        val run = runsByNpc[npc] ?: return
        if (run.state != HydraRunState.ACTIVE || run.phase != phase) return
        when (phase) {
            HydraPhase.GREEN -> launchPoison(run, final = false)
            HydraPhase.BLUE -> launchLightning(run)
            HydraPhase.RED -> launchFlame(run)
            HydraPhase.FINAL -> launchPoison(run, final = true)
        }
    }

    @OptIn(InternalApi::class)
    private fun prepareObservation(run: HydraRun) {
        val boss = run.boss
        val green = requireNotNull(ServerCacheManager.getNpc(HydraAssets.GREEN))
        boss.transmog(green, Int.MAX_VALUE)
        boss.assignUid()
        boss.copyStats(green)
        boss.baseHitpointsLvl = HydraRules.MAX_HITPOINTS
        boss.hitpoints = HydraRules.MAX_HITPOINTS
        boss.mode = null
        boss.movementLocked = true
        boss.apRangeOverride = 5
        boss.apRequiresLineOfSight = true
        boss.hideAllOps()
        boss.assignSpawnOwner(run.player, clock.cycle)
    }

    private fun activate(run: HydraRun) {
        if (run.state != HydraRunState.OBSERVATION) return
        run.state = HydraRunState.ACTIVE
        run.nextVentCycle = clock.cycle + HydraRules.VENT_INITIAL_DELAY
        run.boss.movementLocked = false
        run.boss.showAllOps()
        run.boss.actionDelay = clock.cycle + 1
        keepAggressive(run)
        run.player.mes("The alchemical doors lock behind you. The Hydra attacks!")
    }

    @OptIn(InternalApi::class)
    private fun beginTransition(run: HydraRun) {
        val nextPhase = HydraRules.nextPhase(run.phase) ?: return
        run.state = HydraRunState.TRANSITION
        run.transformEndsAt = clock.cycle + HydraRules.TRANSITION_TICKS
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        val transitionType = requireNotNull(ServerCacheManager.getNpc(HydraRules.transitionNpc(run.phase)))
        run.boss.transmog(transitionType, Int.MAX_VALUE)
        run.boss.assignUid()
        run.boss.anim(RSCM.getReverseMapping(RSCMType.SEQ, transitionAnimation(run.phase)))
        run.player.combatClearQueue()
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.trackingFlame = null
        run.basicAttacks = 0
        run.encounter.interrupt(clock.cycle)
        run.player.mes("The Alchemical Hydra changes form.")
        check(nextPhase != run.phase)
    }

    @OptIn(InternalApi::class)
    private fun finishTransition(run: HydraRun) {
        val next = HydraRules.nextPhase(run.phase) ?: return
        val hp = run.boss.hitpoints
        val activeType = requireNotNull(ServerCacheManager.getNpc(HydraRules.activeNpc(next)))
        run.boss.transmog(activeType, Int.MAX_VALUE)
        run.boss.assignUid()
        run.boss.copyStats(activeType)
        run.boss.baseHitpointsLvl = HydraRules.MAX_HITPOINTS
        run.boss.hitpoints = hp
        run.phase = next
        run.initialStyle = if (next == HydraPhase.FINAL) run.lastBasicStyle.opposite() else run.lastBasicStyle
        run.encounter.transitionTo(abilityName(next, run.initialStyle), clock.cycle)
        run.encounter.lastAbilityTick = clock.cycle - if (next == HydraPhase.FINAL) HydraRules.FINAL_ATTACK_RATE else HydraRules.NORMAL_ATTACK_RATE
        run.encounter.busyUntil = clock.cycle
        run.state = HydraRunState.ACTIVE
        run.weakened = next == HydraPhase.FINAL
        run.wrongVentStacks = 0
        run.basicAttacks = 0
        run.nextVentCycle = clock.cycle + HydraRules.VENT_INITIAL_DELAY
        run.boss.anim(RSCM.getReverseMapping(RSCMType.SEQ, emergeAnimation(next)))
        run.boss.movementLocked = false
        run.boss.actionDelay = clock.cycle + 1
        keepAggressive(run)
    }

    private fun finishRespawn(run: HydraRun) {
        run.pendingRespawn = false
        clearHazards(run)
        run.state = HydraRunState.OBSERVATION
        run.phase = HydraPhase.GREEN
        run.initialStyle = if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED
        run.lastBasicStyle = run.initialStyle
        run.weakened = false
        run.wrongVentStacks = 0
        run.basicAttacks = 0
        run.encounter = deps.encounterRegistry.of(run.boss)
        run.encounter.transitionTo(abilityName(HydraPhase.GREEN, run.initialStyle), clock.cycle)
        prepareObservation(run)
        run.player.combatClearQueue()
        run.player.mes("The Alchemical Hydra has reformed. Cross the doors to fight again.")
    }

    private fun processVent(run: HydraRun) {
        if (clock.cycle < run.nextVentCycle) return
        run.nextVentCycle = clock.cycle + HydraRules.VENT_INTERVAL
        val touched = HydraVent.entries.firstOrNull { vent ->
            val tile = resolve(run, vent.x, vent.z)
            ventTouchesBoss(run.boss, tile)
        } ?: return
        if (touched == HydraRules.requiredVent(run.phase)) {
            if (!run.weakened) run.player.mes("The correct chemical neutralises the Hydra's defence.")
            run.weakened = true
        } else {
            run.wrongVentStacks = HydraRules.applyWrongVentStack(run.wrongVentStacks)
            run.player.mes("The wrong chemical empowers the Hydra.")
        }
    }

    private fun ventTouchesBoss(boss: Npc, tile: CoordGrid): Boolean =
        tile.x in boss.coords.x until boss.coords.x + boss.visType.size &&
            tile.z in boss.coords.z until boss.coords.z + boss.visType.size

    private fun launchPoison(run: HydraRun, final: Boolean) {
        run.boss.facePlayer(run.player)
        run.boss.anim(RSCM.getReverseMapping(RSCMType.SEQ, if (final) HydraAssets.FINAL_POISON_ANIM else HydraAssets.GREEN_POISON_ANIM))
        val target = local(run, run.player.coords)
        HydraRules.poisonCenters(target.x, target.z, final).forEach { (x, z) ->
            val tile = tile(run, x, z)
            val projectile = ProjAnim.fromNpcToCoord(run.boss, tile, HydraAssets.POISON_PROJECTILE, SPECIAL_PROJECTILE)
            world.projAnim(projectile)
            run.pendingPoison += HydraPendingPoison(tile, clock.cycle + projectile.serverCycles)
        }
    }

    private fun processPoison(run: HydraRun) {
        val landed = run.pendingPoison.filter { it.impactCycle <= clock.cycle }
        run.pendingPoison.removeAll(landed.toSet())
        for (poison in landed) {
            mapSpot(HydraAssets.POISON_SPLASH, poison.tile)
            mapSpot(HydraAssets.POISON_POOL, poison.tile)
            if (HydraRules.poisonSplash(run.player.coords.x, run.player.coords.z, poison.tile.x, poison.tile.z)) {
                queueTypeless(run, random.of(8))
                val direct = run.player.coords == poison.tile
                PlayerPoison.tryPoison(
                    run.player,
                    severity = PlayerPoison.severityForInitialDamage(if (direct) 6 else 4),
                )
            }
            run.poisonPools += HydraPoisonPool(poison.tile, clock.cycle + HydraRules.POISON_POOL_TICKS)
        }
        run.poisonPools.removeAll { it.endCycle <= clock.cycle }
        if (run.player.coords in run.poisonPools.map(HydraPoisonPool::tile) && run.lastPoolDamageCycle != clock.cycle) {
            run.lastPoolDamageCycle = clock.cycle
            mapSpot(HydraAssets.POISON_POOL, run.player.coords)
            queueTypeless(run, random.of(13))
        }
    }

    private fun launchLightning(run: HydraRun) {
        run.boss.facePlayer(run.player)
        run.boss.anim(RSCM.getReverseMapping(RSCMType.SEQ, HydraAssets.BLUE_LIGHTNING_ANIM))
        val centre = resolve(run, 23, 28)
        world.projAnim(ProjAnim.fromNpcToCoord(run.boss, centre, HydraAssets.LIGHTNING_CENTER, SPECIAL_PROJECTILE))
        run.pendingLightningCycle = clock.cycle + 2
    }

    private fun processLightning(run: HydraRun) {
        if (run.pendingLightningCycle <= clock.cycle && run.lightningCurrents.isEmpty()) {
            run.pendingLightningCycle = Int.MAX_VALUE
            listOf(28 to 23, 28 to 32, 18 to 23, 17 to 32).forEach { (x, z) ->
                run.lightningCurrents += HydraLightningCurrent(tile(run, x, z), clock.cycle + HydraRules.LIGHTNING_TICKS)
            }
        }
        if (run.lightningCurrents.isEmpty()) return
        var struck = false
        val iterator = run.lightningCurrents.iterator()
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (current.endCycle <= clock.cycle) {
                iterator.remove()
                continue
            }
            val (x, z) = HydraRules.stepToward(current.tile.x, current.tile.z, run.player.coords.x, run.player.coords.z)
            current.tile = tileFromWorld(run, x, z)
            mapSpot(HydraAssets.LIGHTNING_CURRENT, current.tile)
            if (current.tile == run.player.coords) struck = true
        }
        if (struck) {
            queueTypeless(run, random.of(23))
            if (clock.cycle >= run.stunImmuneUntilCycle) stun(run)
            run.lightningCurrents.clear()
        }
    }

    private fun launchFlame(run: HydraRun) {
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.boss.teleport(collision, resolve(run, BOSS_LOCAL_X, BOSS_LOCAL_Z))
        run.boss.facePlayer(run.player)
        run.boss.anim(RSCM.getReverseMapping(RSCMType.SEQ, HydraAssets.RED_FLAME_ANIM))
        val target = local(run, run.player.coords)
        val centre = resolve(run, BOSS_LOCAL_X + 3, BOSS_LOCAL_Z + 3)
        val vertical = abs(target.z - centre.z) >= abs(target.x - centre.x)
        val walls = linkedSetOf<CoordGrid>()
        for (offset in -2..2) {
            if (vertical) {
                walls += tile(run, target.x - 2, target.z + offset)
                walls += tile(run, target.x + 2, target.z + offset)
            } else {
                walls += tile(run, target.x + offset, target.z - 2)
                walls += tile(run, target.x + offset, target.z + 2)
            }
        }
        for (tile in walls) {
            world.projAnim(ProjAnim.fromNpcToCoord(run.boss, tile, HydraAssets.FLAME_PROJECTILE, SPECIAL_PROJECTILE))
            mapSpot(HydraAssets.FLAME_TILE, tile)
        }
        run.flameTiles.clear()
        run.flameTiles += walls
        run.flameEndCycle = clock.cycle + HydraRules.FLAME_TICKS
        run.trackingFlame = centre
    }

    private fun processFlame(run: HydraRun) {
        run.flameTiles.removeAll { clock.cycle >= run.flameEndCycle }
        val tracking = run.trackingFlame
        if (tracking != null && clock.cycle < run.flameEndCycle) {
            val (x, z) = HydraRules.stepToward(tracking.x, tracking.z, run.player.coords.x, run.player.coords.z)
            val next = tileFromWorld(run, x, z)
            run.trackingFlame = next
            run.flameTiles += next
            mapSpot(HydraAssets.FLAME_TILE, next)
        } else if (tracking != null) {
            run.trackingFlame = null
            run.boss.movementLocked = false
            keepAggressive(run)
        }
        if (run.player.coords in run.flameTiles && run.fireBurnsRemaining == 0 && run.lastFireContactCycle != clock.cycle) {
            run.lastFireContactCycle = clock.cycle
            queueTypeless(run, random.of(26))
            run.fireBurnsRemaining = 5
            run.nextBurnCycle = clock.cycle + 2
        }
        if (run.fireBurnsRemaining > 0 && clock.cycle >= run.nextBurnCycle) {
            queueTypeless(run, 5)
            run.fireBurnsRemaining--
            run.nextBurnCycle = clock.cycle + 2
        }
    }

    private fun stun(run: HydraRun) {
        run.player.frozen = true
        run.player.routeDestination.clear()
        run.stunImmuneUntilCycle = clock.cycle + 6
        deps.worldQueues.add(5) {
            if (run.player.isSlotAssigned && runsByInstance[run.session.id] === run) run.player.frozen = false
        }
    }

    private fun queueTypeless(run: HydraRun, damage: Int) {
        if (!run.player.isSlotAssigned || run.player.hitpoints <= 0 || !run.boss.isSlotAssigned) return
        val encounter = run.encounter
        val empowered = HydraRules.empoweredDamage(damage, run.wrongVentStacks)
        deps.worldQueues.add(1) {
            if (
                runsByInstance[run.session.id] !== run ||
                    !deps.encounterRegistry.isActive(encounter) ||
                    !run.player.isSlotAssigned ||
                    run.player.hitpoints <= 0 ||
                    !run.boss.isSlotAssigned
            ) {
                return@add
            }
            run.player.queueImpactHit(
                source = run.boss,
                delay = 0,
                type = HitType.Typeless,
                damage = empowered,
                modifier = deps.playerHitModifier,
            )
        }
    }

    private fun keepAggressive(run: HydraRun) {
        if (run.state == HydraRunState.ACTIVE && run.boss.isSlotAssigned && run.boss.hitpoints > 0 && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun release(run: HydraRun) {
        run.state = HydraRunState.INVALIDATED
        clearHazards(run)
        run.boss.clearInteraction()
        deps.encounterRegistry.remove(run.boss)
        runsByNpc.remove(run.boss)
        runsByInstance.remove(run.session.id)
    }

    private fun clearHazards(run: HydraRun) {
        run.pendingPoison.clear()
        run.poisonPools.clear()
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.flameTiles.clear()
        run.trackingFlame = null
        run.fireBurnsRemaining = 0
    }

    private fun runFor(player: Player): HydraRun? = instances.sessionForPlayer(player)?.let { runsByInstance[it.id] }

    private fun resolve(run: HydraRun, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.resolveCoord(
                run.session,
                CoordGrid(SOURCE_REGION_ZONE_X * 64 + localX, SOURCE_REGION_ZONE_Z * 64 + localZ, 0),
            ),
        )

    private fun tile(run: HydraRun, localX: Int, localZ: Int): CoordGrid =
        resolve(
            run,
            localX.coerceIn(HydraRules.MIN_CHAMBER_X, HydraRules.MAX_CHAMBER_X),
            localZ.coerceIn(HydraRules.MIN_CHAMBER_Z, HydraRules.MAX_CHAMBER_Z),
        )

    private fun tileFromWorld(run: HydraRun, worldX: Int, worldZ: Int): CoordGrid {
        val origin = run.boss.spawnCoords
        val localX = worldX - origin.x + BOSS_LOCAL_X
        val localZ = worldZ - origin.z + BOSS_LOCAL_Z
        return tile(run, localX, localZ)
    }

    private fun local(run: HydraRun, coord: CoordGrid): CoordGrid {
        val origin = run.boss.spawnCoords
        return CoordGrid(coord.x - origin.x + BOSS_LOCAL_X, coord.z - origin.z + BOSS_LOCAL_Z, 0)
    }

    private fun mapSpot(id: Int, tile: CoordGrid) {
        world.spotanimMap(SpotanimType(id), tile)
    }

    private fun transitionAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> HydraAssets.GREEN_TRANSITION_ANIM
            HydraPhase.BLUE -> HydraAssets.BLUE_TRANSITION_ANIM
            HydraPhase.RED -> HydraAssets.RED_TRANSITION_ANIM
            HydraPhase.FINAL -> HydraAssets.DEATH_ANIM
        }

    private fun emergeAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> HydraAssets.GREEN_RANGE_ANIM
            HydraPhase.BLUE -> HydraAssets.BLUE_EMERGE_ANIM
            HydraPhase.RED -> HydraAssets.RED_EMERGE_ANIM
            HydraPhase.FINAL -> HydraAssets.FINAL_EMERGE_ANIM
        }

    private companion object {
        private const val SOURCE_REGION_ZONE_X = 21
        private const val SOURCE_REGION_ZONE_Z = 160
        private const val BOSS_LOCAL_X = 20
        private const val BOSS_LOCAL_Z = 25
        private const val CHAMBER_LOCAL_X = 13
        private const val CHAMBER_LOCAL_Z = 18
        private val SPECIAL_PROJECTILE = dev.openrune.types.ProjAnimType(
            startHeight = 48,
            endHeight = 0,
            delay = 20,
            angle = 20,
            lengthAdjustment = 45,
            progress = 0,
            stepMultiplier = 5,
        )
    }
}

internal fun HydraRun.isOwnedBy(player: Player): Boolean = boss.isSpawnOwnedBy(player)
