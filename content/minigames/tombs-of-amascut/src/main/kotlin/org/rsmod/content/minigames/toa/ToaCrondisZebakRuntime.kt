package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.sign
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class ToaCrondisZebakRuntime
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val objects: ObjRepository,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val routeFactory: RouteFactory,
    private val collision: CollisionFlagMap,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val supplies: ToaSupplies,
    private val bossDeps: BossDeps,
    private val npcAccess: StandardNpcAccessLauncher,
    private val events: EventBus,
    private val worldQueueProcess: WorldQueueListProcess,
) {
    /** A synchronous dispatch token, owned and retired by the native boss encounter. */
    private class OrdinaryDispatch {
        var opportunity: OrdinaryOpportunity? = null
    }

    private data class OrdinaryOpportunity(
        val raid: ToaRaidSession,
        val target: Player,
        val targetCount: Int,
        val tick: Int,
        val generation: Int,
    )

    fun registerOrdinaryCombat(
        context: ScriptContext,
        onModifyHit: NpcHitEvents.Modify.() -> Unit,
    ) {
        bossDeps.extensionRegistry.register(TOA_ZEBAK_ORDINARY_LAUNCH) { _, npc, target, params ->
            val encounter = bossDeps.encounterRegistry.of(npc)
            val opportunity = checkNotNull(ordinaryOpportunity(npc, target))
            launchZebakOrdinary(opportunity, npc, params as ZebakAttackStyle)
            encounter.attackRateOverride = ordinaryAttackRate(opportunity.raid)
        }
        BossCombat.register(
            context,
            zebakBossSpec(),
            bossDeps,
            ownerSlug = "toa-zebak",
            canTarget = { npc, target -> ordinaryOpportunity(npc, target) != null },
            onModifyHit = onModifyHit,
        )
    }

    private fun ordinaryOpportunity(npc: Npc, target: Player): OrdinaryOpportunity? {
        val encounter = bossDeps.encounterRegistry.existing(npc) ?: return null
        val dispatch = encounter.stateOrNull<OrdinaryDispatch>(TOA_ZEBAK_DISPATCH) ?: return null
        return dispatch.opportunity?.takeIf {
            it.target === target &&
                it.tick == clock.cycle &&
                it.raid.zebak.body === npc &&
                it.generation == it.raid.zebak.generation &&
                instances.instanceForNpc(npc) == it.raid.instance.id &&
                bossDeps.encounterRegistry.isCurrent(npc, encounter)
        }
    }

    private fun ordinaryAttackRate(raid: ToaRaidSession): Int =
        ZebakTuning.attackInterval(
            raid.pathLevels[ToaPath.CRONDIS] ?: 0,
            raid.zebak.enrageTriggered,
        )

    /** Special transitions replace the old deadline, including when recovery shortens it. */
    private fun replaceOrdinaryDeadline(raid: ToaRaidSession, deadline: Int) {
        val body = raid.zebak.body ?: return
        val encounter = bossDeps.encounterRegistry.of(body)
        encounter.attackRateOverride = ordinaryAttackRate(raid)
        encounter.lastAbilityTick = deadline - encounter.expectedAttackRate()
        body.actionDelay = deadline
    }

    private val crondisGroundObjects = mutableMapOf<Any, MutableList<Obj>>()
    private val zebakAcidLocs = mutableMapOf<Any, MutableList<LocInfo>>()
    private val zebakBodyLocs = mutableMapOf<Any, MutableList<LocInfo>>()
    private val zebakRockLocs = mutableMapOf<Any, MutableMap<Int, LocInfo>>()
    private val crondisTraceRaids = mutableSetOf<Any>()
    private val zebakTraceRaids = mutableSetOf<Any>()
    private val waterContainerType: String by lazy {
        RSCM.getReverseMapping(RSCMType.OBJ, CrondisAssets.WATER_CONTAINER)
    }

    fun initializeCrondis(raid: ToaRaidSession) {
        check(raid.challenge?.stage == ToaChallengeStage.ACTIVE) {
            "Crondis initialized before its red barrier was crossed."
        }
        cleanupCrondis(raid)
        val local = CrondisLayout.palmLocal
        val palm =
            spawnNpc(
                raid,
                CrondisAssets.PALM,
                roomCoord(raid, ToaRoom.CRONDIS, local.x, local.z),
                ToaNpcRole.HAZARD,
            )
        lockMovement(palm)
        raid.crondis.start(raid.startSnapshot.partySize, palm, clock.cycle)
        spawnCrondisContainers(raid)
        message(raid, "Fill the palm with " + raid.crondis.requiredWater + " water units.")
    }

    fun fillCrondisContainer(
        player: Player,
        raid: ToaRaidSession,
        waterfall: BoundLocInfo,
    ): Boolean {
        if (
            raid.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                raid.crondis.state != CrondisState.ACTIVE
        ) {
            return false
        }
        if (!hasWaterContainer(player, raid)) {
            player.mes("You need a water container from the start of the path.")
            return false
        }
        if (waterfall.id == CrondisAssets.WATERFALL_DRY) {
            player.mes("This waterfall is still recovering.")
            return false
        }
        val readyAt = raid.crondis.waterfallReadyAt[waterfall.coords] ?: 0
        if (clock.cycle < readyAt) {
            player.mes("This waterfall is still recovering.")
            return false
        }
        val added = raid.crondis.container(player.uid).fill()
        if (added == 0) {
            player.mes("Your container is already full.")
            return true
        }
        raid.crondis.waterfallReadyAt[waterfall.coords] =
            clock.cycle + CrondisTuning.WATERFALL_COOLDOWN_TICKS
        locs.change(
            waterfall,
            requireNotNull(ServerCacheManager.getObject(CrondisAssets.WATERFALL_DRY)),
            CrondisTuning.WATERFALL_COOLDOWN_TICKS,
        )
        player.mes("You collect 100 water units. The waterfall begins recovering.")
        return true
    }

    fun waterCrondisPalm(player: Player, palm: Npc, raid: ToaRaidSession): Boolean {
        if (
            raid.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                raid.crondis.palm !== palm ||
                raid.crondis.state != CrondisState.ACTIVE
        ) {
            return false
        }
        if (!hasWaterContainer(player, raid)) {
            player.mes("You need a water container to water the palm.")
            return false
        }
        val container = raid.crondis.container(player.uid)
        if (container.units <= 0) {
            player.mes("Your water container is empty.")
            return false
        }
        val accepted = raid.crondis.depositWater(container)
        raid.awardRewardActionPoints(
            player.accountHash,
            ToaRewardPointRoom.CRONDIS,
            ToaRewardPointRules.crondisWaterPoints(accepted),
        )
        player.mes(
            "You add " +
                accepted +
                " water units (" +
                raid.crondis.water +
                "/" +
                raid.crondis.requiredWater +
                ")."
        )
        updateCrondisPalm(raid)
        if (raid.crondis.state == CrondisState.COMPLETE) completeCrondis(raid)
        return true
    }

    fun checkCrondisContainer(player: Player, raid: ToaRaidSession?): Boolean {
        if (
            raid?.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                !hasWaterContainer(player, raid)
        )
            return false
        player.mes("Your container holds " + raid.crondis.container(player.uid).units + " units.")
        return true
    }

    fun emptyCrondisContainer(player: Player, raid: ToaRaidSession?): Boolean {
        if (
            raid?.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                !hasWaterContainer(player, raid)
        )
            return false
        val emptied = raid.crondis.container(player.uid).empty()
        player.mes(if (emptied == 0) "Your container is already empty." else "You empty it.")
        return true
    }

    fun destroyCrondisContainer(player: Player, raid: ToaRaidSession?): Boolean {
        if (
            raid?.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                !hasWaterContainer(player, raid)
        )
            return false
        check(supplies.consumeChallengeItem(player, raid, CrondisAssets.WATER_CONTAINER))
        raid.crondis.containers.remove(player.uid)
        val index = raid.participants.values.indexOfFirst { it.uid == player.uid }.coerceAtLeast(0)
        spawnCrondisContainer(raid, player, index)
        player.mes("You destroy the container. Another appears nearby.")
        return true
    }

    fun takeCrondisContainer(player: Player, raid: ToaRaidSession, obj: Obj): Boolean {
        if (
            raid.currentRoom != ToaRoom.CRONDIS ||
                raid.challenge?.stage != ToaChallengeStage.ACTIVE ||
                raid.crondis.state != CrondisState.ACTIVE ||
                raid.participant(player.uid)?.currentRoom != ToaRoom.CRONDIS
        ) {
            return false
        }
        val tracked = crondisGroundObjects[raid.instance.id] ?: return false
        if (
            obj.type != CrondisAssets.WATER_CONTAINER ||
                obj !in tracked ||
                !obj.isVisibleTo(player) ||
                !obj.isOriginalOwner(player)
        ) {
            return false
        }
        val vars = supplies.ownershipVars(raid)
        val transaction =
            player.invAdd(
                player.inv,
                obj.type,
                obj.count,
                vars = vars,
                strict = true,
                autoCommit = false,
            )
        if (transaction.failure) {
            player.mes("You need more inventory space.")
            return true
        }
        if (!objects.del(obj, Int.MAX_VALUE)) return true
        transaction.commitAll()
        tracked.remove(obj)
        supplies.recordIssuedChallengeItem(raid, player.accountHash, obj.type, obj.count, vars)
        return true
    }

    fun canContinueFromCrondis(player: Player, raid: ToaRaidSession): Boolean {
        if (raid.currentRoom != ToaRoom.CRONDIS) return false
        if (raid.crondis.state != CrondisState.COMPLETE) {
            player.mes(
                "The palm still needs " +
                    (raid.crondis.requiredWater - raid.crondis.water) +
                    " water units."
            )
            return false
        }
        return true
    }

    fun tickCrondis(raid: ToaRaidSession) {
        val state = raid.crondis
        if (state.state != CrondisState.ACTIVE) return
        val active = activePlayers(raid)
        val palm = state.palm ?: return
        processCrondisObstacles(raid, active, palm)
        state.crocodiles
            .filter { !it.isSlotAssigned || it.hitpoints <= 0 }
            .forEach { expired ->
                state.crocodileNextAttackAt.remove(expired.hashCode())
                state.crocodiles.remove(expired)
            }
        if (state.crocodileSpawnDue(clock.cycle)) {
            spawnCrondisCrocodileWave(raid)
            state.scheduleNextCrocodileWave(clock.cycle)
        }
        state.crocodiles.forEach { crocodile ->
            if (!crocodile.isSlotAssigned || crocodile.hitpoints <= 0) return@forEach
            val waterTarget =
                active
                    .asSequence()
                    .filter { state.container(it.uid).units > 0 }
                    .filter {
                        it.coords.chebyshevDistance(crocodile.coords) <=
                            CrondisTuning.CROCODILE_AGGRO_RANGE
                    }
                    .minWithOrNull(
                        compareBy<Player> { it.coords.chebyshevDistance(crocodile.coords) }
                            .thenBy { it.slotId }
                    )
            if (waterTarget != null) {
                processCrondisCrocodilePlayerAttack(raid, crocodile, waterTarget)
                return@forEach
            }
            if (state.water > 0) {
                processCrondisCrocodilePalmAttack(raid, crocodile, palm)
                return@forEach
            }
            val provokedTarget =
                active
                    .asSequence()
                    .filter { it.uid in state.crocodileProvokedPlayers }
                    .minWithOrNull(
                        compareBy<Player> { it.coords.chebyshevDistance(crocodile.coords) }
                            .thenBy { it.slotId }
                    )
            if (provokedTarget != null) {
                processCrondisCrocodilePlayerAttack(raid, crocodile, provokedTarget)
            } else {
                crocodile.abortRoute()
                crocodile.clearInteractionRoute()
            }
        }
    }

    fun onCrondisCrocodileKilled(raid: ToaRaidSession, npc: Npc): Boolean {
        val removed = raid.crondis.crocodiles.remove(npc)
        if (removed) raid.crondis.crocodileNextAttackAt.remove(npc.hashCode())
        return removed
    }

    fun initializeZebak(raid: ToaRaidSession) {
        cleanupZebak(raid)
        val bodyTile =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.BODY_LOCAL_X,
                ZebakRoomLayout.BODY_LOCAL_Z,
            )
        val tailTile =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.TAIL_LOCAL_X,
                ZebakRoomLayout.TAIL_LOCAL_Z,
            )
        val body = spawnNpc(raid, ZebakAssets.BODY, bodyTile, ToaNpcRole.BOSS, Direction.East)
        val tail = spawnNpc(raid, ZebakAssets.TAIL, tailTile, ToaNpcRole.HAZARD, Direction.East)
        val pathLevel = raid.pathLevels[ToaPath.CRONDIS] ?: 0
        val hitpoints =
            ToaRules.hitpoints(
                ZebakTuning.BASE_HITPOINTS,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                pathLevel,
            )
        body.baseHitpointsLvl = hitpoints
        body.hitpoints = hitpoints
        body.baseDefenceLvl =
            ToaRules.combatStat(ZebakTuning.BASE_DEFENCE, raid.raidLevel, pathLevel)
        body.defenceLvl = body.baseDefenceLvl
        body.suppressHitpointsRegen = true
        lockMovement(body)
        lockMovement(tail)
        zebakBodyLocs[raid.instance.id] =
            mutableListOf(
                addZebakLoc(raid, ZebakAssets.BODY_BLOCKER_LOC, bodyTile),
                addZebakLoc(raid, ZebakAssets.BODY_BLOCKER_LOC, tailTile),
            )
        raid.zebak.start(body, tail)
        // Bind before the first player hit can lazily create the encounter with global RNG.
        val encounter = bossDeps.encounterRegistry.of(body, ZebakSelectionRandom(raid.zebak))
        val generation = raid.zebak.generation
        val phases =
            encounter.state("toa.zebak-phases") {
                ZebakCombatPhases(
                    encounter,
                    bossDeps.worldQueues,
                    worldQueueProcess,
                    clock,
                    valid = {
                        raid.zebak.generation == generation &&
                            raid.zebak.body === body &&
                            instances.instanceForNpc(body) == raid.instance.id &&
                            bossDeps.encounterRegistry.isCurrent(body, encounter)
                    },
                )
            }
        raid.zebak.bindPhases(phases)
        encounter.onCleanup(phases::close)
        val attackQueues =
            encounter.state("toa.zebak-split-queues") {
                ZebakAttackQueues(
                    bossDeps.worldQueues,
                    worldQueueProcess,
                    clock,
                    valid = { capturedGeneration ->
                        capturedGeneration == generation &&
                            raid.zebak.generation == generation &&
                            raid.zebak.body === body &&
                            raid.zebak.state !in
                                setOf(ZebakState.DYING, ZebakState.COMPLETE, ZebakState.RESET) &&
                            instances.instanceForNpc(body) == raid.instance.id &&
                            bossDeps.encounterRegistry.isCurrent(body, encounter)
                    },
                    removeVisual = { visual ->
                        if (visual.npc.isSlotAssigned) removeNpc(raid, visual.npc)
                    },
                )
            }
        raid.zebak.bindAttackQueues(attackQueues)
        encounter.onCleanup(attackQueues::close)
        val payloadQueues =
            encounter.state("toa.zebak-payload-queues") {
                ZebakPayloadQueues(
                    bossDeps.worldQueues,
                    worldQueueProcess,
                    clock,
                    raid.zebak,
                    valid = {
                        raid.zebak.generation == generation &&
                            raid.zebak.body === body &&
                            instances.instanceForNpc(body) == raid.instance.id &&
                            bossDeps.encounterRegistry.isCurrent(body, encounter)
                    },
                )
            }
        raid.zebak.bindPayloadQueues(payloadQueues)
        encounter.onCleanup(payloadQueues::close)
        if (ZebakInvocationRules.resolve(raid.startSnapshot.invocations).bloodMagic) {
            payloadQueues.armBlood(
                clock.cycle + ZebakTuning.EMERGE_TICKS + ZebakTuning.BLOOD_INTERVAL
            ) {
                launchZebakBlood(raid, body)
            }
        }
        replaceOrdinaryDeadline(raid, ZebakTimeline.firstAutoAt(clock.cycle))
        faceZebakEast(raid, body)
        animateZebak(raid, body, ZebakAssets.BODY_IDLE)
        message(raid, "Zebak surges out of the water!")
        trace(
            raid,
            "emerge_start",
            "hp=$hitpoints body=$bodyTile tail=$tailTile completes=${raid.zebak.emergesAt}",
        )
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder, raid: ToaRaidSession): Boolean {
        if (raid.currentRoom == ToaRoom.CRONDIS) {
            val state = raid.crondis
            if (npc.id == CrondisAssets.CROCODILE && npc in state.crocodiles) {
                state.crocodileProvokedPlayers += player.uid
                return true
            }
            if (npc.id in CrondisAssets.PALM..CrondisAssets.PALM_COMPLETE) {
                hit.damage = 0
                return true
            }
            return false
        }
        if (raid.currentRoom != ToaRoom.ZEBAK) return false
        val state = raid.zebak
        if (npc === state.body || npc.id in setOf(ZebakAssets.BODY, ZebakAssets.ENRAGED_BODY)) {
            if (state.playerState[player.uid]?.swimming == true) {
                hit.damage = 0
                player.mes("You cannot attack while swimming.")
                return true
            }
            if (state.state !in setOf(ZebakState.NORMAL, ZebakState.ENRAGED)) {
                hit.damage = 0
                return true
            }
            if (hit.damage > 0) {
                state.queueCrossedThresholds(
                    npc.hitpoints,
                    (npc.hitpoints - hit.damage).coerceAtLeast(0),
                    npc.baseHitpointsLvl,
                )
            }
            return true
        }
        if (npc.id in setOf(ZebakAssets.JUG, ZebakAssets.ROLLING_JUG)) {
            hit.damage = hit.damage.coerceAtMost(npc.hitpoints)
            return true
        }
        return npc.id in ZebakAssets.npcIds
    }

    fun onZebakNpcKilled(hero: Player, npc: Npc, raid: ToaRaidSession, role: ToaNpcRole): Boolean {
        if (raid.currentRoom != ToaRoom.ZEBAK) return false
        val state = raid.zebak
        if (role == ToaNpcRole.BOSS && npc === state.body) {
            beginZebakDeath(raid, hero)
            return true
        }
        if (npc.id in setOf(ZebakAssets.JUG, ZebakAssets.ROLLING_JUG)) {
            state.jugs.remove(npc.hashCode())
            splashJug(raid, npc.coords)
            return true
        }
        if (npc.id == ZebakAssets.ROCK) {
            state.rocks.remove(npc.hashCode())
            return true
        }
        return npc.id in ZebakAssets.npcIds
    }

    fun tickZebak(raid: ToaRaidSession): Player? {
        val state = raid.zebak
        if (state.state == ZebakState.DYING) {
            if (clock.cycle < state.completesAt) return null
            val body = state.body?.takeIf { it.isSlotAssigned }
            val tail = state.tail?.takeIf { it.isSlotAssigned }
            body?.transmog(
                requireNotNull(ServerCacheManager.getNpc(ZebakAssets.DEAD_BODY)),
                Int.MAX_VALUE,
            )
            tail?.transmog(
                requireNotNull(ServerCacheManager.getNpc(ZebakAssets.DEAD_TAIL)),
                Int.MAX_VALUE,
            )
            body?.anim(ZebakAssets.BODY_DEAD)
            tail?.anim(ZebakAssets.TAIL_DEAD)
            state.deadBody = body
            state.deadTail = tail
            state.completeDeath()
            return state.completedBy?.resolve(players)
        }
        val body = state.body ?: return null
        if (!body.isSlotAssigned || body.hitpoints <= 0) return null
        faceZebakEast(raid, body)
        if (state.state == ZebakState.EMERGING) {
            if (state.phases?.advance() != ZebakPhase.NORMAL) return null
            animateZebak(raid, body, ZebakAssets.BODY_IDLE)
            faceZebakEast(raid, body)
            trace(raid, "emerge_complete", "first_auto=${body.actionDelay}")
            return null
        }
        if (body.defenceLvl < ZebakTuning.MINIMUM_DRAINED_DEFENCE) {
            body.defenceLvl = ZebakTuning.MINIMUM_DRAINED_DEFENCE
        }
        state.phases?.processHazards()
        processZebakJugs(raid)
        processZebakWaterSplashes(raid)
        processZebakRockDeaths(raid)
        processZebakWaves(raid)
        raid.zebak.attackQueues?.processSplits()
        raid.zebak.attackQueues?.processVisuals()
        processZebakPlayerMovement(raid, body)
        processZebakSwimming(raid, body)
        processZebakBloodClouds(raid, body)
        processZebakAcid(raid, body)
        if (!state.enrageTriggered && body.hitpoints * 100 <= body.baseHitpointsLvl * 25) {
            beginZebakEnrage(raid, body)
        }
        if (
            state.state in setOf(ZebakState.NORMAL, ZebakState.ENRAGED) &&
                state.pendingThresholds.isNotEmpty()
        ) {
            prepareZebakSpecial(raid, body)
        }
        advanceZebakSpecial(raid, body)
        if (
            state.state in
                setOf(
                    ZebakState.NORMAL,
                    ZebakState.ENRAGED,
                    ZebakState.PREP_ROAR,
                    ZebakState.PREP_WAVES,
                    ZebakState.WAVES,
                )
        ) {
            dispatchZebakOrdinary(raid, body)
        }
        return null
    }

    fun pushJug(player: Player, npc: Npc, raid: ToaRaidSession): Boolean =
        moveJugFromPlayer(player, npc, raid, pull = false)

    fun pullJug(player: Player, npc: Npc, raid: ToaRaidSession): Boolean =
        moveJugFromPlayer(player, npc, raid, pull = true)

    fun hitJug(player: Player, npc: Npc, raid: ToaRaidSession): Boolean =
        if (raid.currentRoom == ToaRoom.ZEBAK && breakJug(raid, npc, npc.coords)) {
            player.faceSquare(npc.coords)
            true
        } else false

    fun climbZebakSteps(player: Player, steps: BoundLocInfo, raid: ToaRaidSession): Boolean {
        if (raid.currentRoom != ToaRoom.ZEBAK || steps.id != ZebakAssets.ROCK_STEPS) return false
        if (player !in roomPlayers(raid)) return false
        val route =
            ZebakRockSteps.routes.firstOrNull { candidate ->
                steps.coords ==
                    roomCoord(raid, ToaRoom.ZEBAK, candidate.stepLocalX, candidate.stepLocalZ)
            } ?: return false
        val runtime =
            raid.zebak.playerState.getOrPut(player.uid) {
                ZebakPlayerRuntime(previousTile = player.coords)
            }
        if (!runtime.swimming && insideZebakArena(raid, player.coords)) {
            player.mes("You are already on the platform.")
            return true
        }
        val landing = roomCoord(raid, ToaRoom.ZEBAK, route.landingLocalX, route.landingLocalZ)
        player.spotanim(ZebakAssets.CLIMB_SPLASH)
        PathingEntityCommon.telejump(player, collision, landing)
        finishZebakSwimming(raid, player, runtime, landing)
        player.mes("You climb back onto the platform.")
        return true
    }

    fun cleanup(raid: ToaRaidSession) {
        cleanupCrondis(raid)
        cleanupZebak(raid)
    }

    fun setCrondisTrace(raid: ToaRaidSession, enabled: Boolean) {
        if (enabled) crondisTraceRaids += raid.instance.id
        else crondisTraceRaids -= raid.instance.id
    }

    fun setZebakTrace(raid: ToaRaidSession, enabled: Boolean) {
        if (enabled) zebakTraceRaids += raid.instance.id else zebakTraceRaids -= raid.instance.id
    }

    fun crondisTrace(raid: ToaRaidSession): String =
        "Crondis tick=" +
            clock.cycle +
            " raid=" +
            raid.party.id +
            " state=" +
            raid.crondis.state +
            " water=" +
            raid.crondis.water +
            "/" +
            raid.crondis.requiredWater +
            " stage=" +
            raid.crondis.stage +
            " crocs=" +
            raid.crondis.crocodiles.count { it.isSlotAssigned } +
            " perfect=" +
            raid.crondis.perfectEligible +
            " clouds=" +
            raid.crondis.cloudTrails.size +
            " nextCloud=" +
            raid.crondis.nextCloudBatchAt +
            " waterfalls=" +
            raid.crondis.waterfallReadyAt.entries.joinToString {
                it.key.toString() + "@" + it.value
            } +
            " generation=" +
            raid.crondis.generation

    fun zebakTrace(raid: ToaRaidSession): String =
        "Zebak tick=" +
            clock.cycle +
            " raid=" +
            raid.party.id +
            " state=" +
            raid.zebak.state +
            " hp=" +
            (raid.zebak.body?.hitpoints ?: 0) +
            " target=" +
            raid.zebak.primaryTarget +
            " queued=" +
            raid.zebak.pendingThresholds.joinToString() +
            " acid=" +
            raid.zebak.acidTiles.size +
            " water=" +
            raid.zebak.waterTiles.size +
            " jugs=" +
            raid.zebak.jugs.size +
            " rocks=" +
            raid.zebak.rocks.size +
            " waves=" +
            raid.zebak.waves.size +
            " waveStage=" +
            raid.zebak.waveStage +
            " clouds=" +
            raid.zebak.bloodClouds.size +
            " enraged=" +
            raid.zebak.enrageTriggered +
            " generation=" +
            raid.zebak.generation

    private fun spawnCrondisContainers(raid: ToaRaidSession) {
        raid.participants.values.forEachIndexed { index, participant ->
            participant.uid.resolve(players)?.let { spawnCrondisContainer(raid, it, index) }
        }
        raid.crondis.containersSpawned = true
    }

    private fun spawnCrondisContainer(raid: ToaRaidSession, player: Player, index: Int) {
        val local = CrondisLayout.containerLocals[index % CrondisLayout.containerLocals.size]
        crondisGroundObjects.getOrPut(raid.instance.id) { mutableListOf() } +=
            objects.add(
                waterContainerType,
                roomCoord(raid, ToaRoom.CRONDIS, local.x, local.z),
                Int.MAX_VALUE,
                player,
                reveal = 0,
            )
    }

    private fun spawnCrondisCrocodileWave(raid: ToaRaidSession) {
        val state = raid.crondis
        val available = (CrondisTuning.CROCODILE_MAX_ALIVE - state.crocodiles.size).coerceAtLeast(0)
        val waveSize =
            CrondisTuning.crocodilesPerWave(state.originalPartySize).coerceAtMost(available)
        repeat(waveSize) {
            val local =
                CrondisLayout.crocodileSpawnLocals[
                        state.crocodileSpawnCursor % CrondisLayout.crocodileSpawnLocals.size]
            state.crocodileSpawnCursor =
                (state.crocodileSpawnCursor + 1) % CrondisLayout.crocodileSpawnLocals.size
            val tile = roomCoord(raid, ToaRoom.CRONDIS, local.x, local.z)
            val croc = spawnNpc(raid, CrondisAssets.CROCODILE, tile, ToaNpcRole.CHALLENGE)
            // Puzzle crocodiles retain their cache combat levels and HP; raid/path scaling does
            // not apply to this challenge actor.
            croc.maxOpRangeOverride = 32
            croc.mode = null
            croc.clearInteractionRoute()
            croc.abortRoute()
            state.crocodileNextAttackAt[croc.hashCode()] =
                clock.cycle + CrondisTuning.CROCODILE_ATTACK_INTERVAL
            state.crocodiles += croc
        }
    }

    private fun processCrondisCrocodilePalmAttack(raid: ToaRaidSession, crocodile: Npc, palm: Npc) {
        if (crocodile.coords.chebyshevDistance(palm.coords) > 2) {
            crocodile.walkTo(routeFactory, palm.coords, passThroughEntities = false)
            return
        }
        val state = raid.crondis
        val key = crocodile.hashCode()
        if (clock.cycle < (state.crocodileNextAttackAt[key] ?: 0)) return
        crocodile.faceSquare(palm.coords)
        playCrondisCrocodileAttack(crocodile, null)
        val requested =
            CrondisTuning.CROCODILE_PALM_MIN_HIT +
                state.nextInt(CrondisTuning.CROCODILE_PALM_DAMAGE_ROLLS)
        if (state.drainWater(requested) > 0) {
            state.perfectEligible = false
            updateCrondisPalm(raid)
        }
        state.crocodileNextAttackAt[key] = clock.cycle + CrondisTuning.CROCODILE_ATTACK_INTERVAL
    }

    private fun processCrondisCrocodilePlayerAttack(
        raid: ToaRaidSession,
        crocodile: Npc,
        target: Player,
    ) {
        if (crocodile.coords.chebyshevDistance(target.coords) > 1) {
            crocodile.walkTo(routeFactory, target.coords, passThroughEntities = false)
            return
        }
        val state = raid.crondis
        val key = crocodile.hashCode()
        if (clock.cycle < (state.crocodileNextAttackAt[key] ?: 0)) return
        crocodile.faceSquare(target.coords)
        playCrondisCrocodileAttack(crocodile, target)
        val successful =
            accuracy.rollMeleeAccuracy(crocodile, target, MeleeAttackType.Crush, random)
        val damage = if (successful) state.crocodileDamage(target.uid, clock.cycle) else 0
        val protecting = target.vars["varbit.prayer_protectfrommelee"] == 1
        queueDamage(
            raid,
            target,
            crocodile,
            damage,
            HitType.Melee,
            if (protecting) CrondisTuning.CROCODILE_MELEE_PRAYER_REDUCTION else null,
        )
        if (successful) {
            if (protecting) {
                target.statSub(
                    "stat.prayer",
                    constant = CrondisTuning.CROCODILE_PRAYER_DRAIN,
                    percent = 0,
                )
            }
            val spilled = state.container(target.uid).loseToTrap(state.nextSpillOutcome())
            if (spilled > 0) {
                state.perfectEligible = false
                target.mes("The crocodile's bite spills " + spilled + " water units.")
            }
        }
        state.crocodileNextAttackAt[key] = clock.cycle + CrondisTuning.CROCODILE_ATTACK_INTERVAL
    }

    private fun playCrondisCrocodileAttack(crocodile: Npc, target: Player?) {
        val configured = crocodile.visType.paramOrNull(params.attack_anim)?.id
        val animation =
            configured?.takeIf { ServerCacheManager.getAnim(it) != null }
                ?: crocodile.visType.standAnim.takeIf { ServerCacheManager.getAnim(it) != null }
        animation?.let(crocodile::anim)
        target?.let { player ->
            crocodile.visType.paramOrNull(params.attack_sound)?.let { player.soundSynth(it) }
        }
    }

    private fun processCrondisObstacles(raid: ToaRaidSession, active: List<Player>, source: Npc) {
        val state = raid.crondis
        while (clock.cycle >= state.nextCloudBatchAt) {
            state.spawnCloudBatch(state.nextCloudBatchAt)
            state.nextCloudBatchAt += CrondisTuning.CLOUD_BATCH_INTERVAL_TICKS
        }

        val dangerousSpikeLocals = linkedSetOf<CoordGrid>()
        CrondisLayout.paths.forEach { path ->
            path.emitters.forEach { emitter ->
                val phase = CrondisObstacleCycle.spikePhase(state.startedAt, clock.cycle, emitter)
                when (phase) {
                    CrondisSpikePhase.WARNING ->
                        animateCrondisLoc(
                            raid,
                            emitter.statueLocal,
                            CrondisAssets.STATUE,
                            CrondisAssets.STATUE_WARNING,
                        )
                    CrondisSpikePhase.EXTENDING ->
                        animateCrondisLoc(
                            raid,
                            emitter.spearLocal,
                            CrondisAssets.SPEAR,
                            CrondisAssets.SPEAR_EXTEND,
                        )
                    CrondisSpikePhase.DANGEROUS -> {
                        dangerousSpikeLocals += emitter.dangerousLocals
                        val offset =
                            Math.floorMod(
                                clock.cycle - state.startedAt - emitter.delay,
                                CrondisTuning.SPIKE_CYCLE_TICKS,
                            )
                        if (offset == 6) {
                            animateCrondisLoc(
                                raid,
                                emitter.spearLocal,
                                CrondisAssets.SPEAR,
                                CrondisAssets.SPEAR_IDLE,
                            )
                        }
                    }
                    CrondisSpikePhase.RETRACTING ->
                        animateCrondisLoc(
                            raid,
                            emitter.statueLocal,
                            CrondisAssets.STATUE,
                            CrondisAssets.SPEAR_IDLE,
                        )
                    else -> Unit
                }
            }
        }

        val dangerousCloudLocals = linkedSetOf<CoordGrid>()
        state.cloudTrails.forEach { trail ->
            repeat(CrondisTuning.CLOUD_TILE_COUNT) { tileIndex ->
                val local = trail.localTile(tileIndex)
                when (CrondisObstacleCycle.cloudPhase(trail, clock.cycle, tileIndex)) {
                    CrondisCloudPhase.WARNING ->
                        world.spotanimMap(
                            SpotanimType(CrondisAssets.CLOUD_TRAIL),
                            roomCoord(raid, ToaRoom.CRONDIS, local.x, local.z),
                        )
                    CrondisCloudPhase.DANGEROUS -> dangerousCloudLocals += local
                    else -> Unit
                }
            }
        }
        state.cloudTrails.removeAll { CrondisObstacleCycle.cloudExpired(it, clock.cycle) }

        val dangerousSpikes =
            dangerousSpikeLocals.mapTo(hashSetOf()) { roomCoord(raid, ToaRoom.CRONDIS, it.x, it.z) }
        val dangerousClouds =
            dangerousCloudLocals.mapTo(hashSetOf()) { roomCoord(raid, ToaRoom.CRONDIS, it.x, it.z) }
        active.forEach { player ->
            if (
                clock.cycle >= (state.playerSpikeReadyAt[player.uid] ?: 0) &&
                    CrondisObstacleCycle.occupiesDangerousTile(player.coords, dangerousSpikes)
            ) {
                applyCrondisObstacleHit(raid, player, source, CrondisObstacleKind.SPIKE)
                state.playerSpikeReadyAt[player.uid] =
                    clock.cycle + CrondisTuning.SPIKE_HIT_COOLDOWN_TICKS
            }
            if (
                clock.cycle >= (state.playerCloudReadyAt[player.uid] ?: 0) &&
                    CrondisObstacleCycle.occupiesDangerousTile(player.coords, dangerousClouds)
            ) {
                applyCrondisObstacleHit(raid, player, source, CrondisObstacleKind.CLOUD)
                state.playerCloudReadyAt[player.uid] =
                    clock.cycle + CrondisTuning.CLOUD_HIT_COOLDOWN_TICKS
            }
        }
    }

    private fun applyCrondisObstacleHit(
        raid: ToaRaidSession,
        player: Player,
        source: Npc,
        kind: CrondisObstacleKind,
    ) {
        val state = raid.crondis
        val minimum =
            if (kind == CrondisObstacleKind.SPIKE) {
                CrondisTuning.SPIKE_MIN_DAMAGE
            } else {
                CrondisTuning.CLOUD_MIN_DAMAGE
            }
        val damage =
            scaledDamage(raid, minimum + state.nextInt(CrondisTuning.OBSTACLE_DAMAGE_ROLLS))
        val lost = state.container(player.uid).loseToTrap(state.nextSpillOutcome())
        if (lost > 0) state.perfectEligible = false
        state.recordObstacleHit(player.uid, clock.cycle)
        queueDamage(raid, player, source, damage, HitType.Typeless, null)
        player.statSub("stat.defence", constant = 6, percent = 0)
        player.statSub("stat.agility", constant = 3, percent = 0)
        player.statSub("stat.prayer", constant = 2, percent = 0)
        val name = if (kind == CrondisObstacleKind.SPIKE) "spikes" else "poison cloud"
        player.mes(
            if (lost > 0) {
                "The $name strike you and spill $lost water units."
            } else {
                "The $name strike you."
            }
        )
    }

    private fun animateCrondisLoc(
        raid: ToaRaidSession,
        local: CoordGrid,
        locId: Int,
        sequenceId: Int,
    ) {
        val tile = roomCoord(raid, ToaRoom.CRONDIS, local.x, local.z)
        locs.findAll(tile).firstOrNull { it.id == locId }?.let { world.locAnim(it, sequenceId) }
    }

    private fun resetCrondisObstacleVisuals(raid: ToaRaidSession) {
        CrondisLayout.paths.flatMap(CrondisPathDefinition::emitters).forEach { emitter ->
            animateCrondisLoc(
                raid,
                emitter.statueLocal,
                CrondisAssets.STATUE,
                CrondisAssets.SPEAR_IDLE,
            )
            animateCrondisLoc(
                raid,
                emitter.spearLocal,
                CrondisAssets.SPEAR,
                CrondisAssets.SPEAR_IDLE,
            )
        }
    }

    private fun updateCrondisPalm(raid: ToaRaidSession) {
        val palm = raid.crondis.palm ?: return
        val id =
            when (raid.crondis.stage) {
                0 -> CrondisAssets.PALM
                1 -> CrondisAssets.PALM_STAGE_2
                2 -> CrondisAssets.PALM_STAGE_3
                3 -> CrondisAssets.PALM_STAGE_4
                else -> CrondisAssets.PALM_COMPLETE
            }
        if (palm.id != id)
            palm.transmog(requireNotNull(ServerCacheManager.getNpc(id)), Int.MAX_VALUE)
    }

    private fun completeCrondis(raid: ToaRaidSession) {
        updateCrondisPalm(raid)
        raid.crondis.completedAt = clock.cycle
        raid.crondis.cloudTrails.clear()
        raid.crondis.playerSpikeReadyAt.clear()
        raid.crondis.playerCloudReadyAt.clear()
        resetCrondisObstacleVisuals(raid)
        // The objective is complete, but route advancement is owned by ToaRaidManager after the
        // player uses the Continue passage. Reusing ToaChallengeState.complete here used to make
        // that
        // idempotent transition return early and permanently strand the party in Crondis.
        raid.crondis.crocodiles.toList().forEach { removeNpc(raid, it) }
        raid.crondis.crocodiles.clear()
        message(raid, "The palm is restored. The passage to Zebak is open.")
    }

    private fun prepareZebakSpecial(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val special = state.nextSpecial()
        state.pendingThresholds.removeFirst()
        cleanupZebakSpecialActors(raid)
        val phases = checkNotNull(state.phases)
        phases.enter(
            if (special == ZebakSpecial.WAVES) ZebakPhase.PREP_WAVES else ZebakPhase.PREP_ROAR
        )
        replaceOrdinaryDeadline(
            raid,
            clock.cycle +
                if (special == ZebakSpecial.WAVES) ZebakTuning.WAVE_FIRST_AUTO_DELAY
                else ZebakTuning.ROAR_FIRST_AUTO_DELAY,
        )
        state.preparationIncludesRocks = special == ZebakSpecial.ROAR
        state.pendingJugCount = ZebakTuning.jugCount(state.nextInt(ZebakTuning.JUG_COUNT_ROLLS))
        state.pendingRockCount =
            if (state.preparationIncludesRocks) ZebakTuning.rockCount(activePlayers(raid).size)
            else 0
        phases.prepareHazards(
            launch = { launchZebakPreparationHazards(raid) },
            land = { spawnZebakPreparationHazards(raid, body, state.preparationIncludesRocks) },
        )
        trace(
            raid,
            "special_prepare",
            "special=$special launch=${ZebakTimeline.hazardLaunchAt(clock.cycle)} " +
                "land=${ZebakTimeline.hazardLandAt(clock.cycle)} starts=${phases.deadline}",
        )
    }

    private fun launchZebakPreparationHazards(raid: ToaRaidSession) {
        val state = raid.zebak
        val source =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.PROJECTILE_ORIGIN_LOCAL_X,
                ZebakRoomLayout.PROJECTILE_ORIGIN_LOCAL_Z,
            )
        zebakAcidCenterTiles(raid).forEach { target ->
            sendZebakHazardProjectile(
                source,
                target,
                ZebakAssets.POISON_PROJECTILE,
                ZebakTuning.HAZARD_PROJECTILE_END_CYCLES,
            )
        }
        zebakJugTiles(raid, state.pendingJugCount).forEach { target ->
            sendZebakHazardProjectile(
                source,
                target,
                ZebakAssets.JUG_PROJECTILE,
                ZebakTuning.HAZARD_PROJECTILE_END_CYCLES,
            )
        }
        zebakRockTiles(raid, state.pendingRockCount).forEach { target ->
            sendZebakHazardProjectile(
                source,
                target,
                ZebakAssets.BOULDER_PROJECTILE,
                ZebakTuning.BOULDER_PROJECTILE_END_CYCLES,
            )
        }
        trace(
            raid,
            "hazards_launch",
            "jugs=${state.pendingJugCount} rocks=${state.pendingRockCount}",
        )
    }

    private fun spawnZebakPreparationHazards(
        raid: ToaRaidSession,
        body: Npc,
        includeRocks: Boolean,
    ) {
        val state = raid.zebak
        spawnZebakAcid(raid, body)
        zebakJugTiles(raid, state.pendingJugCount).forEach { tile ->
            world.spotanimMap(SpotanimType(ZebakAssets.ROAR_DUST), tile)
            val jug = spawnNpc(raid, ZebakAssets.JUG, tile, ToaNpcRole.ADD)
            jug.baseHitpointsLvl = ZebakTuning.JUG_HITPOINTS
            jug.hitpoints = ZebakTuning.JUG_HITPOINTS
            jug.suppressHitpointsRegen = true
            lockMovement(jug)
            state.jugs[jug.hashCode()] = ZebakJugRuntime(jug, jug.coords)
        }
        if (includeRocks) {
            zebakRockTiles(raid, state.pendingRockCount).forEach { tile ->
                val rock = spawnNpc(raid, ZebakAssets.ROCK, tile, ToaNpcRole.HAZARD)
                rock.baseHitpointsLvl = ZebakTuning.ROCK_HITPOINTS
                rock.hitpoints = ZebakTuning.ROCK_HITPOINTS
                rock.suppressHitpointsRegen = true
                lockMovement(rock)
                state.rocks[rock.hashCode()] = ZebakRockRuntime(rock)
                zebakRockLocs.getOrPut(raid.instance.id) { mutableMapOf() }[rock.hashCode()] =
                    addZebakLoc(raid, ZebakAssets.LIVE_ROCK_BLOCKER_LOC, rock.coords)
                world.spotanimMap(SpotanimType(ZebakAssets.ROCK_FALL), rock.coords)
            }
        }
        message(
            raid,
            if (includeRocks) {
                "Zebak covers the arena in acid. Prepare the jugs and take cover behind a rock."
            } else {
                "Zebak covers the arena in acid. Prepare the jugs for the incoming waves."
            },
        )
    }

    private fun zebakJugTiles(raid: ToaRaidSession, count: Int): List<CoordGrid> {
        val center = fightCenter(raid)
        val offsets = listOf(-9 to 2, 9 to 2, -5 to 8, 5 to 8, 0 to 10, -10 to 8, 10 to 8, 0 to 3)
        return offsets.take(count.coerceIn(0, offsets.size)).map { (x, z) ->
            CoordGrid(center.x + x, center.z + z, center.level)
        }
    }

    private fun zebakRockTiles(raid: ToaRaidSession, count: Int): List<CoordGrid> {
        val center = fightCenter(raid)
        return listOf(-7 to -5, 7 to -5, 0 to 6).take(count.coerceIn(0, 3)).map { (x, z) ->
            CoordGrid(center.x + x, center.z + z, center.level)
        }
    }

    private fun zebakAcidCenterTiles(raid: ToaRaidSession): List<CoordGrid> {
        val state = raid.zebak
        val upset = ToaInvocation.UPSET_STOMACH in raid.startSnapshot.invocations
        val pattern = ZebakAcidLayout.centerLocalTiles(upset)
        val count = (6 + activePlayers(raid).size).coerceAtMost(pattern.size)
        val rotation = state.nextThresholdIndex % pattern.size
        return List(count) { index ->
            val (localX, localZ) = pattern[(index + rotation) % pattern.size]
            roomCoord(raid, ToaRoom.ZEBAK, localX, localZ)
        }
    }

    private fun emitGreatRoarPulse(raid: ToaRaidSession, body: Npc, pulse: Int) {
        val state = raid.zebak
        val liveRocks = state.rocks.values.filter { it.npc.isSlotAssigned && it.hitpoints > 0 }
        emitGreatRoarFloor(raid, body, liveRocks)
        activePlayers(raid).forEach { player ->
            if (
                liveRocks.none {
                    ZebakGeometry.isCovered(body.coords, it.npc.coords, player.coords)
                }
            ) {
                val deltaX = player.coords.x - body.coords.x
                val deltaZ = player.coords.z - body.coords.z
                val pushX =
                    if (kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaZ)) deltaX.sign else 0
                val pushZ =
                    if (kotlin.math.abs(deltaZ) > kotlin.math.abs(deltaX)) deltaZ.sign else 0
                val pushed =
                    CoordGrid(
                        player.coords.x + pushX * ZebakTuning.ROAR_PUSH_TILES,
                        player.coords.z + pushZ * ZebakTuning.ROAR_PUSH_TILES,
                        player.coords.level,
                    )
                if (pushX != 0 || pushZ != 0)
                    PathingEntityCommon.telejump(player, collision, pushed)
                player.anim(4177)
                player.soundSynth(5888)
                queueDamage(
                    raid,
                    player,
                    body,
                    scaledDamage(
                        raid,
                        ZebakTuning.ROAR_DAMAGE_MIN + state.nextInt(ZebakTuning.ROAR_DAMAGE_ROLLS),
                    ),
                    HitType.Typeless,
                    null,
                )
            }
        }
        if (pulse == 1) {
            state.jugs.values.toList().forEach { jug ->
                if (jug.npc.isSlotAssigned) breakJug(raid, jug.npc, jug.tile)
            }
        }
        liveRocks.forEach { runtime ->
            runtime.hitpoints -= ZebakTuning.ROCK_ROAR_DAMAGE
            runtime.npc.hitpoints = runtime.hitpoints.coerceAtLeast(0)
            if (runtime.hitpoints <= 0 && runtime.destroysAt < 0) {
                runtime.npc.anim(7717)
                replaceZebakRockLoc(raid, runtime.npc, ZebakAssets.COLLAPSED_ROCK_LOC)
                runtime.destroysAt = clock.cycle + ZebakTuning.ROAR_ROCK_CLEANUP_DELAY
            }
        }
    }

    private fun startWaves(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        state.waveOccurrence++
        if (state.waveOccurrence > 1) {
            state.waveDirection =
                if (state.waveDirection == ZebakWaveDirection.NORTH) ZebakWaveDirection.SOUTH
                else ZebakWaveDirection.NORTH
        }
        state.waveHitAt.clear()
        animateZebak(raid, body, ZebakAssets.BODY_SPECIAL)
        message(raid, "Three tidal waves surge across the arena.")
    }

    private fun advanceZebakSpecial(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val phases = state.phases ?: return
        // Native owned dispatch snapshots one declared edge. Its new edge waits for another
        // eligible LateCycle, preserving the old pulse/row delay after an overdue transition.
        when (val advanced = phases.advance()) {
            ZebakPhase.ROAR_START -> animateZebak(raid, body, ZebakAssets.BODY_ROAR)
            ZebakPhase.ROAR_PULSE_1,
            ZebakPhase.ROAR_PULSE_2,
            ZebakPhase.ROAR_PULSE_3 -> emitGreatRoarPulse(raid, body, advanced.roarPulse)
            ZebakPhase.WAVE_START -> startWaves(raid, body)
            ZebakPhase.WAVE_ROW_1,
            ZebakPhase.WAVE_ROW_2,
            ZebakPhase.WAVE_ROW_3 -> {
                val gapWidth = ZebakTuning.waveGap(raid.pathLevels[ToaPath.CRONDIS] ?: 0)
                state.waveHitAt.clear()
                spawnWaveRow(raid, state.waveDirection, gapWidth)
            }
            ZebakPhase.NORMAL -> finishZebakSpecial(raid)
            null -> {
                if (phases.phase == ZebakPhase.WAVE_ROW_3 && state.waves.isEmpty()) {
                    finishZebakSpecial(raid)
                }
            }
            else -> Unit
        }
    }

    private fun spawnWaveRow(raid: ToaRaidSession, direction: ZebakWaveDirection, gapWidth: Int) {
        val laneCount = ZebakRoomLayout.WAVE_MAX_LOCAL_X - ZebakRoomLayout.WAVE_MIN_LOCAL_X + 1
        val gapStartLocalX =
            ZebakRoomLayout.WAVE_MIN_LOCAL_X + raid.zebak.nextInt(laneCount - gapWidth + 1)
        val startLocalZ = ZebakRoomLayout.waveStartLocalZ(direction)
        val facing =
            when (direction) {
                ZebakWaveDirection.NORTH -> Direction.North
                ZebakWaveDirection.SOUTH -> Direction.South
            }
        for (localX in ZebakRoomLayout.WAVE_MIN_LOCAL_X..ZebakRoomLayout.WAVE_MAX_LOCAL_X) {
            if (localX in gapStartLocalX until gapStartLocalX + gapWidth) continue
            val tile = roomCoord(raid, ToaRoom.ZEBAK, localX, startLocalZ)
            val wave = spawnNpc(raid, ZebakAssets.WAVE, tile, ToaNpcRole.HAZARD, facing)
            lockMovement(wave)
            wave.faceSquare(CoordGrid(tile.x, tile.z + direction.stepZ, tile.level))
            raid.zebak.waves[wave.hashCode()] =
                ZebakWaveRuntime(wave, tile, direction, gapStartLocalX, gapWidth)
        }
    }

    private fun processZebakWaves(raid: ToaRaidSession) {
        val southEdge =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.ARENA_CENTER_LOCAL_X,
                ZebakRoomLayout.WAVE_SOUTH_END_LOCAL_Z,
            )
        val northEdge =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.ARENA_CENTER_LOCAL_X,
                ZebakRoomLayout.WAVE_NORTH_END_LOCAL_Z,
            )
        raid.zebak.waves.values.toList().forEach { runtime ->
            val npc = runtime.npc
            if (!npc.isSlotAssigned) {
                raid.zebak.waves.remove(npc.hashCode())
                return@forEach
            }
            val next =
                CoordGrid(
                    runtime.tile.x,
                    runtime.tile.z + runtime.direction.stepZ,
                    runtime.tile.level,
                )
            val passedArena =
                when (runtime.direction) {
                    ZebakWaveDirection.NORTH -> next.z > northEdge.z
                    ZebakWaveDirection.SOUTH -> next.z < southEdge.z
                }
            if (passedArena) {
                raid.zebak.waves.remove(npc.hashCode())
                removeNpc(raid, npc)
                return@forEach
            }
            val previousWaveTile = runtime.tile
            runtime.tile = next
            PathingEntityCommon.telejump(npc, collision, next)
            npc.faceSquare(CoordGrid(next.x, next.z + runtime.direction.stepZ, next.level))
            // Current OSRS only sometimes clears acid as a row crosses it. The exact modern
            // probability is not public; the historical one-in-four rule remains seeded here.
            if (raid.zebak.nextInt(4) == 0) clearAcidAt(raid, next)
            raid.zebak.jugs.values
                .toList()
                .filter { it.npc.isSlotAssigned && it.tile == next }
                .forEach { jug ->
                    val pushed =
                        CoordGrid(jug.tile.x, jug.tile.z + runtime.direction.stepZ, jug.tile.level)
                    val rock = liveRockAt(raid, pushed)
                    when {
                        rock != null -> stageJugRockCollision(raid, jug, pushed)
                        insideZebakArena(raid, pushed) -> {
                            jug.tile = pushed
                            PathingEntityCommon.telejump(jug.npc, collision, pushed)
                        }
                        else -> sinkJug(raid, jug.npc, jug.tile)
                    }
                }
            activePlayers(raid).forEach { player ->
                val playerState =
                    raid.zebak.playerState.getOrPut(player.uid) {
                        ZebakPlayerRuntime(previousTile = player.coords)
                    }
                val crossed =
                    ZebakWaveCollision.crossed(
                        previousWaveTile,
                        next,
                        playerState.previousTile,
                        player.coords,
                    )
                if (!crossed || raid.zebak.waveHitAt[player.uid] == clock.cycle) return@forEach
                raid.zebak.waveHitAt[player.uid] = clock.cycle
                val pushed = ZebakWaveCollision.pushedTile(player.coords, runtime.direction)
                PathingEntityCommon.telejump(player, collision, pushed)
                if (!insideZebakArena(raid, pushed)) {
                    playerState.swimming = true
                    player.tempMoveSpeed = MoveSpeed.Walk
                    player.mes("The wave washes you into the water!")
                }
                queueDamage(
                    raid,
                    player,
                    raid.zebak.body ?: npc,
                    scaledDamage(
                        raid,
                        ZebakTuning.WAVE_MIN_DAMAGE +
                            raid.zebak.nextInt(ZebakTuning.WAVE_DAMAGE_ROLLS),
                    ),
                    HitType.Typeless,
                    null,
                )
            }
        }
    }

    private fun dispatchZebakOrdinary(raid: ToaRaidSession, body: Npc) {
        if (clock.cycle < body.actionDelay) return
        val encounter = bossDeps.encounterRegistry.of(body)
        if (clock.cycle - encounter.lastAbilityTick < encounter.expectedAttackRate()) return
        val state = raid.zebak
        val targets = zebakCombatTargets(raid)
        if (targets.isEmpty()) {
            replaceOrdinaryDeadline(raid, clock.cycle + 1)
            return
        }
        val meleeTargets = targets.filter { body.isWithinDistance(it, 1) }
        val target =
            meleeTargets.minByOrNull { it.coords.chebyshevDistance(body.coords) }
                ?: state.primaryTarget?.resolve(players)?.takeIf { it in targets }
                ?: targets.minByOrNull { it.coords.chebyshevDistance(body.coords) }!!
        state.primaryTarget = target.uid
        val dispatch = encounter.state(TOA_ZEBAK_DISPATCH, ::OrdinaryDispatch)
        check(dispatch.opportunity == null)
        dispatch.opportunity =
            OrdinaryOpportunity(raid, target, targets.size, clock.cycle, state.generation)
        try {
            // The stationary body has no suspended access workflow. This native launch completes
            // synchronously; its token rejects ordinary AI events outside this LateCycle slot.
            npcAccess.launch(body) { events.publish(this, AiPlayerEvents.Op2(target, body)) }
        } finally {
            dispatch.opportunity = null
        }
    }

    private fun launchZebakOrdinary(
        opportunity: OrdinaryOpportunity,
        body: Npc,
        style: ZebakAttackStyle,
    ) {
        val raid = opportunity.raid
        val state = raid.zebak
        val target = opportunity.target
        if (style == ZebakAttackStyle.MELEE) {
            animateZebak(raid, body, ZebakAssets.BODY_MELEE)
            queueDamage(
                raid,
                target,
                body,
                state.nextInt(scaledDamage(raid, ZebakTuning.MELEE_MAX_HIT) + 1),
                HitType.Melee,
                ZebakTuning.MELEE_PRAYER_REDUCTION,
            )
            if (state.nextInt(ZebakTuning.MELEE_BLEED_CHANCE) == 0) {
                state.playerState
                    .getOrPut(target.uid) { ZebakPlayerRuntime(previousTile = target.coords) }
                    .bleedMoves = ZebakTuning.MELEE_BLEED_MOVES
                target.mes("Zebak's bite leaves you bleeding when you move.")
            }
        } else {
            val ranged = style == ZebakAttackStyle.RANGED
            animateZebak(raid, body, ZebakAssets.BODY_RANGE)
            val initialProjectile =
                if (ranged) ZebakAssets.RANGE_PROJECTILE else ZebakAssets.MAGIC_PROJECTILE
            val originTile =
                roomCoord(
                    raid,
                    ToaRoom.ZEBAK,
                    ZebakRoomLayout.PROJECTILE_ORIGIN_LOCAL_X,
                    ZebakRoomLayout.PROJECTILE_ORIGIN_LOCAL_Z,
                )
            val splitTile =
                roomCoord(
                    raid,
                    ToaRoom.ZEBAK,
                    ZebakRoomLayout.PROJECTILE_SPLIT_LOCAL_X,
                    ZebakRoomLayout.PROJECTILE_SPLIT_LOCAL_Z,
                )
            sendZebakInitialProjectile(originTile, splitTile, initialProjectile)
            roomPlayers(raid).forEach { player ->
                player.soundSynth(
                    if (ranged) ZebakSounds.RANGE_LAUNCH else ZebakSounds.MAGIC_LAUNCH
                )
                player.soundSynth(
                    if (ranged) ZebakSounds.RANGE_SPLIT else ZebakSounds.MAGIC_SPLIT,
                    delay = 120,
                )
            }
            checkNotNull(state.attackQueues).scheduleSplit(
                ZebakSplitProjectileRuntime(
                    tile = splitTile,
                    style = style,
                    splitAt = ZebakTimeline.splitAt(clock.cycle),
                    generation = state.generation,
                )
            ) { split ->
                launchZebakSplit(raid, split)
            }
        }
        trace(
            raid,
            "auto",
            "style=$style primary=${target.uid} partyTargets=${opportunity.targetCount}",
        )
    }

    private fun launchZebakSplit(raid: ToaRaidSession, split: ZebakSplitProjectileRuntime) {
        val state = raid.zebak
        val ranged = split.style == ZebakAttackStyle.RANGED
        val splitGraphic = if (ranged) ZebakAssets.RANGE_SPLIT else ZebakAssets.MAGIC_SPLIT
        val breakGraphic =
            if (ranged) ZebakAssets.RANGE_SPLIT_VISUAL else ZebakAssets.MAGIC_SPLIT_VISUAL
        val impactGraphic = if (ranged) ZebakAssets.RANGE_IMPACT else ZebakAssets.MAGIC_IMPACT
        val helper =
            spawnNpc(
                raid,
                ZebakAssets.SPLIT_HELPER,
                roomCoord(
                    raid,
                    ToaRoom.ZEBAK,
                    ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_LOCAL_X,
                    ZebakRoomLayout.PROJECTILE_SPLIT_HELPER_LOCAL_Z,
                ),
                ToaNpcRole.HAZARD,
            )
        helper.ignoreCombatInteractions = true
        lockMovement(helper)
        helper.spotanim(breakGraphic, height = 750)
        checkNotNull(state.attackQueues)
            .scheduleVisual(
                ZebakSplitVisualRuntime(
                    npc = helper,
                    removesAt = ZebakTimeline.splitVisualRemoveAt(split.splitAt),
                    generation = state.generation,
                )
            )
        val targets = zebakCombatTargets(raid)
        targets.forEach { player ->
            val landingTile = player.coords
            sendZebakSplitProjectile(split.tile, player, splitGraphic)
            player.spotanim(impactGraphic, delay = 90, height = 90)
            player.soundSynth(ZebakSounds.IMPACT, delay = 90)
            raid.encounter.pendingHits +=
                ToaPendingHit(
                    target = player.uid,
                    tile = landingTile,
                    landsAt =
                        ZebakTimeline.projectileImpactAt(
                            split.splitAt - ZebakTuning.INITIAL_PROJECTILE_TICKS
                        ),
                    damage =
                        state.nextInt(
                            scaledDamage(
                                raid,
                                if (ranged) ZebakTuning.RANGE_MAX_HIT else ZebakTuning.MAGIC_MAX_HIT,
                            ) + 1
                        ),
                    impactGraphic = null,
                    requireTargetOnTile = false,
                    hitType = if (ranged) HitType.Ranged else HitType.Magic,
                    prayerReductionPercent = ZebakTuning.RANGE_MAGIC_PRAYER_REDUCTION,
                    generation = state.generation,
                )
        }
        trace(
            raid,
            "projectile_split",
            "style=${split.style} targets=${targets.size} impact=" +
                (clock.cycle + ZebakTuning.SPLIT_PROJECTILE_TICKS),
        )
    }

    private fun beginZebakEnrage(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        state.enrageTriggered = true
        state.discardThresholdsForEnrage()
        if (state.state in setOf(ZebakState.PREP_ROAR, ZebakState.PREP_WAVES)) {
            cleanupZebakSpecialActors(raid)
        }
        checkNotNull(state.phases).enter(ZebakPhase.ENRAGED)
        body.transmog(
            requireNotNull(ServerCacheManager.getNpc(ZebakAssets.ENRAGED_BODY)),
            Int.MAX_VALUE,
        )
        // Revision-223 level-0 packets keep the ordinary 9624/9625 attack pair after the
        // 11732 transmog. Enrage changes cadence, not the ordinary attack assets.
        animateZebak(raid, body, ZebakAssets.BODY_RANGE)
        replaceOrdinaryDeadline(raid, clock.cycle + ordinaryAttackRate(raid))
        message(raid, "Zebak becomes enraged!")
    }

    private fun spawnZebakAcid(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val upset = ToaInvocation.UPSET_STOMACH in raid.startSnapshot.invocations
        val pattern = ZebakAcidLayout.centerLocalTiles(upset)
        val count = (6 + activePlayers(raid).size).coerceAtMost(pattern.size)
        val rotation = state.nextThresholdIndex % pattern.size
        val radius = if (upset) ZebakTuning.UPSET_ACID_RADIUS else ZebakTuning.ACID_RADIUS
        var spawnedCenters = 0
        repeat(count) { index ->
            val (localX, localZ) = pattern[(index + rotation) % pattern.size]
            if (!ZebakRoomLayout.acidFootprintFits(localX, localZ, radius)) {
                return@repeat
            }
            val tile = roomCoord(raid, ToaRoom.ZEBAK, localX, localZ)
            sendProjectile(body, tile, ZebakAssets.ACID_PROJECTILE)
            val added = ZebakGeometry.applyAcid(tile, upset, state.acidTiles, state.waterTiles)
            spawnedCenters++
            added.forEachIndexed { acidIndex, acidTile ->
                removeAcidLocAt(raid, acidTile)
                val id = ZebakAssets.ACID_LOCS[(index + acidIndex) % ZebakAssets.ACID_LOCS.size]
                val type = requireNotNull(ServerCacheManager.getObject(id))
                val loc =
                    locs.add(
                        acidTile,
                        type,
                        Int.MAX_VALUE,
                        LocAngle.North,
                        LocShape.CentrepieceStraight,
                    )
                zebakAcidLocs.getOrPut(raid.instance.id) { mutableListOf() } += loc
            }
        }
        animateZebak(raid, body, ZebakAssets.BODY_ACID)
        trace(
            raid,
            "acid_pattern",
            "centers=$spawnedCenters axis=north_south upset=$upset tiles=${state.acidTiles.size}",
        )
    }

    private fun processZebakAcid(raid: ToaRaidSession, body: Npc) {
        activePlayers(raid).forEach { player ->
            val runtime =
                raid.zebak.playerState.getOrPut(player.uid) {
                    ZebakPlayerRuntime(previousTile = player.coords)
                }
            if (player.coords !in raid.zebak.acidTiles) {
                runtime.acidStack = 0
                runtime.nextAcidAt = clock.cycle
                return@forEach
            }
            if (clock.cycle < runtime.nextAcidAt) return@forEach
            if (runtime.acidStack == 0) {
                PlayerPoison.tryPoison(
                    player,
                    initialDamage = ZebakTuning.ACID_POISON_INITIAL_DAMAGE,
                    source = body,
                )
            }
            runtime.acidStack++
            runtime.nextAcidAt = clock.cycle + ZebakTuning.ACID_TICK_INTERVAL
            val damage =
                (ZebakTuning.ACID_BASE_DAMAGE +
                        (runtime.acidStack - 1) * ZebakTuning.ACID_RAMP_DAMAGE)
                    .coerceAtMost(ZebakTuning.ACID_DAMAGE_CAP)
            queueDamage(raid, player, body, scaledDamage(raid, damage), HitType.Typeless, null)
        }
    }

    private fun moveJugFromPlayer(
        player: Player,
        npc: Npc,
        raid: ToaRaidSession,
        pull: Boolean,
    ): Boolean {
        if (
            raid.currentRoom != ToaRoom.ZEBAK ||
                npc.id !in setOf(ZebakAssets.JUG, ZebakAssets.ROLLING_JUG)
        ) {
            return false
        }
        val existing = raid.zebak.jugs[npc.hashCode()] ?: return false
        if (!npc.isWithinDistance(player, 1) || existing.rolling || existing.breaksAt >= 0) {
            return false
        }
        val dx = (npc.coords.x - player.coords.x).sign
        val dz = (npc.coords.z - player.coords.z).sign
        val directionX = if (pull) -dx else dx
        val directionZ = if (pull) -dz else dz
        if (directionX == 0 && directionZ == 0) return false
        val runtime = existing
        runtime.rolling = true
        runtime.directionX = directionX
        runtime.directionZ = directionZ
        runtime.remainingSteps = ZebakTuning.JUG_ROLL_STEPS
        npc.transmog(
            requireNotNull(ServerCacheManager.getNpc(ZebakAssets.ROLLING_JUG)),
            Int.MAX_VALUE,
        )
        player.faceSquare(npc.coords)
        player.anim(ZebakAssets.JUG_PUSH_ANIMATION)
        return true
    }

    private fun processZebakJugs(raid: ToaRaidSession) {
        raid.zebak.jugs.values.toList().forEach { runtime ->
            val npc = runtime.npc
            if (!npc.isSlotAssigned) {
                raid.zebak.jugs.remove(npc.hashCode())
                return@forEach
            }
            if (runtime.breaksAt >= 0) {
                if (clock.cycle >= runtime.breaksAt) breakJug(raid, npc, runtime.tile)
                return@forEach
            }
            if (!runtime.rolling) return@forEach
            val next =
                CoordGrid(
                    runtime.tile.x + runtime.directionX,
                    runtime.tile.z + runtime.directionZ,
                    runtime.tile.level,
                )
            if (!insideZebakArena(raid, next)) {
                sinkJug(raid, npc, runtime.tile)
                return@forEach
            }
            val rock = liveRockAt(raid, next)
            if (rock != null) {
                stageJugRockCollision(raid, runtime, next)
                return@forEach
            }
            runtime.tile = next
            runtime.remainingSteps--
            PathingEntityCommon.telejump(npc, collision, next)
            if (runtime.remainingSteps <= 0) {
                runtime.rolling = false
                runtime.directionX = 0
                runtime.directionZ = 0
                npc.transmog(
                    requireNotNull(ServerCacheManager.getNpc(ZebakAssets.JUG)),
                    Int.MAX_VALUE,
                )
            }
        }
    }

    private fun stageJugRockCollision(
        raid: ToaRaidSession,
        runtime: ZebakJugRuntime,
        collisionTile: CoordGrid,
    ) {
        runtime.tile = collisionTile
        runtime.rolling = false
        runtime.remainingSteps = 0
        runtime.breaksAt = clock.cycle + 1
        PathingEntityCommon.telejump(runtime.npc, collision, collisionTile)
        trace(raid, "jug_rock_collision", "tile=$collisionTile breaks=${runtime.breaksAt}")
    }

    private fun processZebakWaterSplashes(raid: ToaRaidSession) {
        raid.zebak.waterSplashes.toList().forEach { splash ->
            if (clock.cycle < splash.clearsAt) return@forEach
            raid.zebak.waterSplashes.remove(splash)
            splash.tiles.forEach { tile ->
                if (tile in raid.zebak.acidTiles) {
                    clearAcidAt(raid, tile)
                    world.spotanimMap(SpotanimType(ZebakAssets.WATER_CLEANSE), tile)
                }
            }
        }
    }

    private fun processZebakRockDeaths(raid: ToaRaidSession) {
        raid.zebak.rocks.values.toList().forEach { runtime ->
            if (runtime.destroysAt < 0 || clock.cycle < runtime.destroysAt) return@forEach
            raid.zebak.rocks.remove(runtime.npc.hashCode())
            removeZebakRockLoc(raid, runtime.npc)
            if (runtime.npc.isSlotAssigned) {
                val tile = runtime.npc.coords
                removeNpc(raid, runtime.npc)
                world.spotanimMap(SpotanimType(2191), tile)
            }
        }
    }

    private fun emitGreatRoarFloor(raid: ToaRaidSession, body: Npc, rocks: List<ZebakRockRuntime>) {
        val covered =
            rocks.flatMapTo(linkedSetOf()) { rock ->
                (1..3).map { distance ->
                    val dx = (rock.npc.coords.x - body.coords.x).sign
                    val dz = (rock.npc.coords.z - body.coords.z).sign
                    if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
                        CoordGrid(
                            rock.npc.coords.x + dx * distance,
                            rock.npc.coords.z,
                            rock.npc.coords.level,
                        )
                    } else {
                        CoordGrid(
                            rock.npc.coords.x,
                            rock.npc.coords.z + dz * distance,
                            rock.npc.coords.level,
                        )
                    }
                }
            }
        for (localX in ZebakRoomLayout.ARENA_MIN_LOCAL_X..ZebakRoomLayout.ARENA_MAX_LOCAL_X) {
            for (localZ in ZebakRoomLayout.ARENA_MIN_LOCAL_Z..ZebakRoomLayout.ARENA_MAX_LOCAL_Z) {
                val tile = roomCoord(raid, ToaRoom.ZEBAK, localX, localZ)
                if (tile !in covered) {
                    val delay = body.coords.chebyshevDistance(tile).coerceAtLeast(1)
                    world.spotanimMap(SpotanimType(ZebakAssets.ROAR_DUST), tile, delay = delay)
                }
            }
        }
    }

    private fun liveRockAt(raid: ToaRaidSession, tile: CoordGrid): ZebakRockRuntime? =
        raid.zebak.rocks.values.firstOrNull {
            it.npc.isSlotAssigned && it.hitpoints > 0 && it.npc.coords == tile
        }

    private fun breakJug(raid: ToaRaidSession, npc: Npc, tile: CoordGrid): Boolean {
        if (npc.id !in setOf(ZebakAssets.JUG, ZebakAssets.ROLLING_JUG)) return false
        val runtime = raid.zebak.jugs.remove(npc.hashCode()) ?: return false
        if (runtime.npc.isSlotAssigned) removeNpc(raid, runtime.npc)
        world.spotanimMap(SpotanimType(ZebakAssets.JUG_BREAK), tile)
        activePlayers(raid).forEach { it.soundSynth(2496) }
        splashJug(raid, tile)
        trace(raid, "jug_break", "tile=$tile rolling=${runtime.rolling}")
        return true
    }

    private fun sinkJug(raid: ToaRaidSession, npc: Npc, tile: CoordGrid) {
        val runtime = raid.zebak.jugs.remove(npc.hashCode()) ?: return
        if (runtime.npc.isSlotAssigned) removeNpc(raid, runtime.npc)
        // A jug entering the surrounding water simply disappears. It neither splashes nor
        // creates the 5x5 cleansing footprint on the arena.
        trace(raid, "jug_sink", "tile=$tile")
    }

    private fun splashJug(raid: ToaRaidSession, tile: CoordGrid) {
        val radius =
            if (ToaInvocation.UPSET_STOMACH in raid.startSnapshot.invocations) {
                ZebakTuning.UPSET_WATER_RADIUS
            } else {
                ZebakTuning.WATER_RADIUS
            }
        val footprint = ZebakGeometry.footprint(tile, radius)
        raid.zebak.waterTiles.addAll(footprint)
        footprint.forEach { target ->
            sendProjectile(tile, target, ZebakAssets.JUG_SPLASH, travelTicks = 1)
        }
        raid.zebak.waterSplashes += ZebakWaterSplashRuntime(footprint, clock.cycle + 1)
    }

    private fun clearAcidAt(raid: ToaRaidSession, tile: CoordGrid) {
        raid.zebak.acidTiles.remove(tile)
        removeAcidLocAt(raid, tile)
    }

    private fun removeAcidLocAt(raid: ToaRaidSession, tile: CoordGrid) {
        val stored = zebakAcidLocs[raid.instance.id] ?: return
        stored
            .filter { it.coords == tile }
            .toList()
            .forEach { loc ->
                locs.del(loc, Int.MAX_VALUE)
                stored.remove(loc)
            }
    }

    private fun processZebakSwimming(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val active = roomPlayers(raid)
        val activeUids = active.mapTo(mutableSetOf()) { it.uid }
        active.forEach { player ->
            val runtime =
                state.playerState.getOrPut(player.uid) {
                    ZebakPlayerRuntime(previousTile = player.coords)
                }
            if (!runtime.swimming) return@forEach
            if (insideZebakArena(raid, player.coords)) {
                finishZebakSwimming(raid, player, runtime, player.coords)
                player.mes("You climb back onto the platform.")
                return@forEach
            }
            player.tempMoveSpeed = MoveSpeed.Walk
            if (player.uid !in state.waterCrocodiles) {
                val center = fightCenter(raid)
                val spawnZ = player.coords.z + if (player.coords.z >= center.z) 2 else -2
                val crocodile =
                    spawnNpc(
                        raid,
                        ZebakAssets.WATER_CROCODILE,
                        CoordGrid(player.coords.x, spawnZ, player.coords.level),
                        ToaNpcRole.HAZARD,
                    )
                crocodile.maxOpRangeOverride = 32
                val crocodileRuntime = ZebakWaterCrocodileRuntime(crocodile, player.uid)
                state.waterCrocodiles[player.uid] = crocodileRuntime
                state.payloadQueues?.armCrocodileBite(crocodileRuntime)
            }
        }
        state.payloadQueues?.processCrocodileReadiness()
        state.waterCrocodiles.entries.toList().forEach { (uid, runtime) ->
            val target = uid.resolve(players)
            val swimming = state.playerState[uid]?.swimming == true
            if (uid !in activeUids || target == null || !swimming || !runtime.npc.isSlotAssigned) {
                state.waterCrocodiles.remove(uid)
                state.payloadQueues?.cancelCrocodile(runtime)
                if (runtime.npc.isSlotAssigned) removeNpc(raid, runtime.npc)
                return@forEach
            }
            if (runtime.npc.isWithinDistance(target, 1)) {
                if (runtime.biteReady) {
                    queueDamage(
                        raid,
                        target,
                        runtime.npc,
                        scaledDamage(raid, ZebakTuning.WATER_CROCODILE_MAX_HIT),
                        HitType.Melee,
                        100,
                    )
                    state.payloadQueues?.armCrocodileBite(runtime)
                }
            } else if (clock.cycle % 2 == 0) {
                runtime.npc.walkTo(routeFactory, target.coords, passThroughEntities = false)
            }
        }
    }

    private fun launchZebakBlood(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val targets = activePlayers(raid)
        val invocationRules = ZebakInvocationRules.resolve(raid.startSnapshot.invocations)
        if (
            bossDeps.encounterRegistry.of(body).selectZebakBlood(clock.cycle) ==
                ZebakBloodChoice.BARRAGE
        ) {
            val target = targets[state.nextInt(targets.size)]
            sendProjectile(body, target, ZebakAssets.MAGIC_PROJECTILE)
            val radius = invocationRules.barrageRadius
            targets
                .filter { it.coords.chebyshevDistance(target.coords) <= radius }
                .forEach { player ->
                    state.body?.let { source ->
                        raid.encounter.pendingHits +=
                            ToaPendingHit(
                                target = player.uid,
                                tile = player.coords,
                                landsAt = clock.cycle + 2,
                                damage = scaledDamage(raid, 20),
                                impactGraphic = ZebakAssets.MAGIC_SPLIT_VISUAL,
                                requireTargetOnTile = false,
                                hitType = HitType.Magic,
                                prayerReductionPercent = ZebakTuning.RANGE_MAGIC_PRAYER_REDUCTION,
                                generation = state.generation,
                                zebakBloodHealing = true,
                            )
                    }
                }
            trace(raid, "blood_barrage", "target=" + target.uid + " radius=" + radius)
        } else {
            val count = invocationRules.bloodCloudCount
            val offsets = listOf(-5 to 0, 5 to 0, 0 to -5)
            repeat(count) { index ->
                val target = targets[index % targets.size]
                val offset = offsets[index]
                val cloud =
                    spawnNpc(
                        raid,
                        invocationRules.bloodCloudNpc,
                        CoordGrid(
                            target.coords.x + offset.first,
                            target.coords.z + offset.second,
                            target.coords.level,
                        ),
                        ToaNpcRole.HAZARD,
                    )
                cloud.baseHitpointsLvl = ZebakTuning.BLOOD_CLOUD_HITPOINTS
                cloud.hitpoints = ZebakTuning.BLOOD_CLOUD_HITPOINTS
                cloud.suppressHitpointsRegen = true
                val runtime = ZebakBloodCloudRuntime(cloud, target.uid)
                state.bloodClouds[cloud.hashCode()] = runtime
                state.payloadQueues?.addCloud(runtime)
            }
            trace(raid, "blood_clouds", "count=" + count)
        }
    }

    private fun processZebakBloodClouds(raid: ToaRaidSession, body: Npc) {
        val state = raid.zebak
        val targets = activePlayers(raid)
        val invocationRules = ZebakInvocationRules.resolve(raid.startSnapshot.invocations)
        if (!invocationRules.bloodMagic || targets.isEmpty()) return
        state.payloadQueues?.processBlood()
        // New clouds join this pass: decay and contact still happen in their spawn tick.
        state.payloadQueues?.processCloudReadiness()

        state.bloodClouds.values.toList().forEach { runtime ->
            val cloud = runtime.npc
            val target = runtime.target.resolve(players)
            runtime.hitpoints -= ZebakTuning.BLOOD_CLOUD_DECAY
            if (cloud.isSlotAssigned) cloud.hitpoints = runtime.hitpoints.coerceAtLeast(0)
            if (
                !cloud.isSlotAssigned || target == null || runtime.hitpoints <= 0 || runtime.expired
            ) {
                state.bloodClouds.remove(cloud.hashCode())
                state.payloadQueues?.cancelCloud(runtime)
                if (cloud.isSlotAssigned) removeNpc(raid, cloud)
                return@forEach
            }
            if (clock.cycle % 2 == 0) {
                cloud.walkTo(routeFactory, target.coords, passThroughEntities = false)
            }
            val contactRadius = invocationRules.barrageRadius
            if (
                cloud.coords.chebyshevDistance(target.coords) <= contactRadius &&
                    runtime.contactReady
            ) {
                val damage = scaledDamage(raid, 12)
                val dealt = queueDamage(raid, target, body, damage, HitType.Magic, 100)
                val healing =
                    ZebakInvocationRules.healAmount(dealt, invocationRules.arterialHealing)
                body.hitpoints = (body.hitpoints + healing).coerceAtMost(body.baseHitpointsLvl)
                state.payloadQueues?.armCloudContact(runtime)
            }
        }
    }

    private fun processZebakPlayerMovement(raid: ToaRaidSession, body: Npc) {
        val active = activePlayers(raid)
        raid.zebak.playerState.keys.retainAll(trackedPlayerUids(raid))
        active.forEach { player ->
            val runtime =
                raid.zebak.playerState.getOrPut(player.uid) {
                    ZebakPlayerRuntime(previousTile = player.coords)
                }
            val previous = runtime.previousTile
            if (previous != null && previous != player.coords && runtime.bleedMoves > 0) {
                queueDamage(
                    raid,
                    player,
                    body,
                    scaledDamage(raid, ZebakTuning.MELEE_BLEED_DAMAGE),
                    HitType.Typeless,
                    null,
                )
                runtime.bleedMoves--
            }
            runtime.previousTile = player.coords
        }
    }

    private fun finishZebakSwimming(
        raid: ToaRaidSession,
        player: Player,
        runtime: ZebakPlayerRuntime,
        landing: CoordGrid,
    ) {
        runtime.swimming = false
        runtime.previousTile = landing
        player.tempMoveSpeed = null
        raid.zebak.waterCrocodiles.remove(player.uid)?.let { crocodile ->
            raid.zebak.payloadQueues?.cancelCrocodile(crocodile)
            if (crocodile.npc.isSlotAssigned) removeNpc(raid, crocodile.npc)
        }
    }

    private fun beginZebakDeath(raid: ToaRaidSession, hero: Player) {
        val state = raid.zebak
        if (state.state in setOf(ZebakState.DYING, ZebakState.COMPLETE)) return
        state.beginDeath()
        state.completedBy = hero.uid
        state.completesAt = ZebakTimeline.deathTransformAt(clock.cycle)
        raid.encounter.pendingHits.removeAll { it.generation == state.generation }
        cleanupZebakCombatActors(raid)
        val body = state.body?.takeIf { it.isSlotAssigned }
        val tail = state.tail?.takeIf { it.isSlotAssigned }
        body?.hitpoints = 1
        body?.anim(ZebakAssets.BODY_DEATH)
        tail?.anim(ZebakAssets.TAIL_DEATH)
        message(raid, "Zebak collapses.")
    }

    private fun finishZebakSpecial(raid: ToaRaidSession) {
        cleanupZebakSpecialActors(raid, keepJugs = true)
        val phases = checkNotNull(raid.zebak.phases)
        val next = if (raid.zebak.enrageTriggered) ZebakPhase.ENRAGED else ZebakPhase.NORMAL
        if (phases.phase != next) phases.enter(next)
        replaceOrdinaryDeadline(raid, clock.cycle + ZebakTuning.SPECIAL_RECOVERY_TICKS)
    }

    private fun cleanupZebakSpecialActors(raid: ToaRaidSession, keepJugs: Boolean = false) {
        raid.zebak.waves.values
            .map { it.npc }
            .toList()
            .forEach { if (it.isSlotAssigned) removeNpc(raid, it) }
        raid.zebak.waves.clear()
        raid.zebak.rocks.values
            .map { it.npc }
            .toList()
            .forEach { if (it.isSlotAssigned) removeNpc(raid, it) }
        raid.zebak.rocks.clear()
        zebakRockLocs.remove(raid.instance.id)?.values?.forEach { locs.del(it, Int.MAX_VALUE) }
        raid.zebak.phases?.cancelHazards()
        raid.zebak.preparationIncludesRocks = false
        raid.zebak.pendingJugCount = 0
        raid.zebak.pendingRockCount = 0
        if (!keepJugs) {
            raid.zebak.jugs.values
                .map { it.npc }
                .toList()
                .forEach { if (it.isSlotAssigned) removeNpc(raid, it) }
            raid.zebak.jugs.clear()
        }
    }

    private fun cleanupZebakCombatActors(raid: ToaRaidSession) {
        raid.zebak.payloadQueues?.close()
        raid.zebak.phases?.close()
        cleanupZebakSpecialActors(raid)
        raid.zebak.bloodClouds.values
            .map { it.npc }
            .toList()
            .forEach { if (it.isSlotAssigned) removeNpc(raid, it) }
        raid.zebak.bloodClouds.clear()
        raid.zebak.waterCrocodiles.values
            .map { it.npc }
            .toList()
            .forEach { if (it.isSlotAssigned) removeNpc(raid, it) }
        raid.zebak.waterCrocodiles.clear()
        raid.zebak.attackQueues?.close()
        raid.zebak.playerState.keys
            .mapNotNull { it.resolve(players) }
            .forEach { it.tempMoveSpeed = null }
    }

    private fun cleanupCrondis(raid: ToaRaidSession) {
        if (!raid.instance.isEnded) resetCrondisObstacleVisuals(raid)
        crondisGroundObjects.remove(raid.instance.id)?.forEach { objects.del(it, Int.MAX_VALUE) }
        raid.participants.keys
            .mapNotNull { it.resolve(players) }
            .forEach { player ->
                supplies.removeIssuedChallengeItems(player, raid, CrondisAssets.WATER_CONTAINER)
            }
        crondisTraceRaids.remove(raid.instance.id)
        raid.crondis.reset()
    }

    private fun cleanupZebak(raid: ToaRaidSession) {
        val state = raid.zebak
        raid.encounter.pendingHits.removeAll { it.generation == state.generation }
        cleanupZebakCombatActors(raid)
        listOfNotNull(state.body, state.tail, state.deadBody, state.deadTail).distinct().forEach {
            if (it.isSlotAssigned) removeNpc(raid, it)
        }
        zebakAcidLocs.remove(raid.instance.id)?.forEach { locs.del(it, Int.MAX_VALUE) }
        zebakBodyLocs.remove(raid.instance.id)?.forEach { locs.del(it, Int.MAX_VALUE) }
        zebakRockLocs.remove(raid.instance.id)?.values?.forEach { locs.del(it, Int.MAX_VALUE) }
        zebakTraceRaids.remove(raid.instance.id)
        state.reset()
    }

    private fun addZebakLoc(raid: ToaRaidSession, id: Int, tile: CoordGrid): LocInfo =
        locs.add(
            tile,
            requireNotNull(ServerCacheManager.getObject(id)),
            Int.MAX_VALUE,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )

    private fun removeZebakRockLoc(raid: ToaRaidSession, rock: Npc) {
        val stored = zebakRockLocs[raid.instance.id] ?: return
        stored.remove(rock.hashCode())?.let { locs.del(it, Int.MAX_VALUE) }
        if (stored.isEmpty()) zebakRockLocs.remove(raid.instance.id)
    }

    private fun replaceZebakRockLoc(raid: ToaRaidSession, rock: Npc, id: Int) {
        removeZebakRockLoc(raid, rock)
        zebakRockLocs.getOrPut(raid.instance.id) { mutableMapOf() }[rock.hashCode()] =
            addZebakLoc(raid, id, rock.coords)
    }

    private fun lockMovement(npc: Npc) {
        npc.moveRestrict = MoveRestrict.NoMove
        npc.mode = null
        npc.abortRoute()
        npc.clearInteractionRoute()
    }

    private fun faceZebakEast(raid: ToaRaidSession, body: Npc) {
        val arenaCenter = fightCenter(raid)
        body.faceSquare(arenaCenter)
        raid.zebak.tail?.takeIf { it.isSlotAssigned }?.faceSquare(arenaCenter)
    }

    private fun animateZebak(raid: ToaRaidSession, body: Npc, bodySequence: Int) {
        body.anim(bodySequence)
        raid.zebak.tail
            ?.takeIf { it.isSlotAssigned }
            ?.anim(ZebakAnimationPairs.tailFor(bodySequence))
    }

    private fun queueDamage(
        raid: ToaRaidSession,
        player: Player,
        source: Npc,
        damage: Int,
        type: HitType,
        prayerReductionPercent: Int?,
    ): Int {
        val modifier =
            prayerReductionPercent?.let { base ->
                NpcPartialProtectionPrayerPlayerHitModifier(
                    ToaRules.prayerReductionPercent(base, raid.startSnapshot.invocations)
                )
            } ?: NpcBypassProtectionPrayerPlayerHitModifier
        return player
            .queueHit(
                source = source,
                delay = 1,
                type = type,
                damage = damage.coerceAtLeast(0),
                modifier = modifier,
                impactHandler =
                    prayerReductionPercent?.let {
                        ToaPrayerInvocationRules.deadlyImpactHandler(
                            player,
                            type,
                            raid.startSnapshot.invocations,
                        )
                    },
            )
            .damage
    }

    private fun sendProjectile(source: Npc, target: Player, graphic: Int) {
        val distance = source.coords.chebyshevDistance(target.coords).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 60,
                endHeight = 30,
                startTime = 0,
                endTime = 30 + distance * 5,
                angle = 15,
                progress = 32,
                sourceIndex = source.slotId + 1,
                targetIndex = -(target.slotId + 1),
                startCoord = source.coords,
                endCoord = target.coords,
            )
        )
    }

    private fun sendProjectile(source: CoordGrid, target: Player, graphic: Int) {
        val distance = source.chebyshevDistance(target.coords).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 30,
                endHeight = 30,
                startTime = 0,
                endTime = 30 + distance * 5,
                angle = 15,
                progress = 32,
                sourceIndex = 0,
                targetIndex = -(target.slotId + 1),
                startCoord = source,
                endCoord = target.coords,
            )
        )
    }

    private fun sendProjectile(
        source: Npc,
        target: CoordGrid,
        graphic: Int,
        travelTicks: Int? = null,
    ) {
        val distance = source.coords.chebyshevDistance(target).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 60,
                endHeight = 30,
                startTime = 0,
                endTime =
                    travelTicks?.let(ToaProjectileTiming::clientCyclesForServerTicks)
                        ?: (30 + distance * 5),
                angle = 15,
                progress = 32,
                sourceIndex = source.slotId + 1,
                targetIndex = 0,
                startCoord = source.coords,
                endCoord = target,
            )
        )
    }

    private fun sendZebakHazardProjectile(
        source: CoordGrid,
        target: CoordGrid,
        graphic: Int,
        endCycles: Int,
    ) {
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = ZebakTuning.HAZARD_PROJECTILE_START_HEIGHT,
                endHeight = ZebakTuning.HAZARD_PROJECTILE_END_HEIGHT,
                startTime = ZebakTuning.HAZARD_PROJECTILE_START_CYCLES,
                endTime = endCycles,
                angle = ZebakTuning.HAZARD_PROJECTILE_ANGLE,
                progress = ZebakTuning.HAZARD_PROJECTILE_PROGRESS,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = target,
            )
        )
    }

    /** Exact fixed-anchor projectile packet used by Zebak's ordinary magic/ranged launch. */
    private fun sendZebakInitialProjectile(source: CoordGrid, split: CoordGrid, graphic: Int) {
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 50,
                endHeight = 175,
                startTime = 60,
                endTime = 120,
                angle = 30,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = split,
            )
        )
    }

    /** The fan-out homes to the locked player entity; walking cannot dodge a normal attack. */
    private fun sendZebakSplitProjectile(source: CoordGrid, target: Player, graphic: Int) {
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 175,
                endHeight = 22,
                startTime = 0,
                endTime = 90,
                angle = 127,
                progress = 0,
                sourceIndex = 0,
                targetIndex = -(target.slotId + 1),
                startCoord = source,
                endCoord = target.coords,
            )
        )
    }

    private fun sendProjectile(
        source: CoordGrid,
        target: CoordGrid,
        graphic: Int,
        travelTicks: Int? = null,
    ) {
        val distance = source.chebyshevDistance(target).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 60,
                endHeight = 30,
                startTime = 0,
                endTime =
                    travelTicks?.let(ToaProjectileTiming::clientCyclesForServerTicks)
                        ?: (30 + distance * 5),
                angle = 15,
                progress = 32,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = target,
            )
        )
    }

    private fun spawnNpc(
        raid: ToaRaidSession,
        id: Int,
        tile: CoordGrid,
        role: ToaNpcRole,
        facing: Direction = Direction.South,
    ): Npc {
        val type = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing ToA npc " + id }
        return Npc(type, tile).also { npc ->
            ToaNpcStatScaling.apply(npc, raid, role)
            npc.respawnDir = facing
            npc.mode = type.defaultMode
            npcs.add(npc, Int.MAX_VALUE)
            npc.respawns = false
            instances.attachNpc(raid.instance.id, npc)
            raid.spawnedNpcs += npc
            raid.npcRoles[npc] = role
        }
    }

    private fun removeNpc(raid: ToaRaidSession, npc: Npc) {
        npc.mode = null
        npc.clearInteractionRoute()
        npc.abortRoute()
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        raid.spawnedNpcs.remove(npc)
        raid.npcRoles.remove(npc)
    }

    private fun hasWaterContainer(player: Player, raid: ToaRaidSession): Boolean =
        supplies.hasOwnedChallengeItem(player, raid, CrondisAssets.WATER_CONTAINER)

    private fun trackedPlayerUids(raid: ToaRaidSession) =
        raid.participants.values
            .filter { participant ->
                CrondisZebakParticipationRules.permitsStateRetention(
                    raid.currentRoom,
                    raid.challenge?.stage,
                    participant.currentRoom,
                    participant.spectating,
                )
            }
            .mapTo(mutableSetOf()) { it.uid }

    private fun roomPlayers(raid: ToaRaidSession): List<Player> =
        raid.participants.values
            .filter { participant ->
                CrondisZebakParticipationRules.permitsTracking(
                    raid.currentRoom,
                    raid.challenge?.stage,
                    participant.currentRoom,
                    participant.connected,
                    participant.spectating,
                )
            }
            .mapNotNull { it.uid.resolve(players) }

    private fun activePlayers(raid: ToaRaidSession): List<Player> =
        roomPlayers(raid).filter { player ->
            val insideChallengeBounds =
                raid.currentRoom == ToaRoom.CRONDIS &&
                    ToaChallengeRoomRules.isInside(raid, instances, ToaRoom.CRONDIS, player.coords)
            val insideBossArena =
                raid.currentRoom == ToaRoom.ZEBAK && insideZebakArena(raid, player.coords)
            CrondisZebakParticipationRules.permitsPosition(
                raid.currentRoom,
                insideChallengeBounds,
                insideBossArena,
            )
        }

    private fun zebakCombatTargets(raid: ToaRaidSession): List<Player> =
        if (raid.currentRoom != ToaRoom.ZEBAK) {
            emptyList()
        } else {
            roomPlayers(raid).filter { player ->
                CrondisZebakParticipationRules.permitsZebakCombatTarget(
                    insideZebakArena(raid, player.coords),
                    raid.zebak.playerState[player.uid]?.swimming == true,
                )
            }
        }

    private fun roomCoord(
        raid: ToaRaidSession,
        room: ToaRoom,
        localX: Int,
        localZ: Int,
    ): CoordGrid =
        requireNotNull(
            instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(room, localX, localZ))
        )

    private fun fightCenter(raid: ToaRaidSession): CoordGrid =
        roomCoord(
            raid,
            ToaRoom.ZEBAK,
            ZebakRoomLayout.ARENA_CENTER_LOCAL_X,
            ZebakRoomLayout.ARENA_CENTER_LOCAL_Z,
        )

    private fun insideZebakArena(raid: ToaRaidSession, tile: CoordGrid): Boolean {
        val southWest =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.ARENA_MIN_LOCAL_X,
                ZebakRoomLayout.ARENA_MIN_LOCAL_Z,
            )
        val northEast =
            roomCoord(
                raid,
                ToaRoom.ZEBAK,
                ZebakRoomLayout.ARENA_MAX_LOCAL_X,
                ZebakRoomLayout.ARENA_MAX_LOCAL_Z,
            )
        return tile.level == southWest.level &&
            tile.x in southWest.x..northEast.x &&
            tile.z in southWest.z..northEast.z
    }

    private fun scaledDamage(raid: ToaRaidSession, base: Int): Int =
        ToaRules.maxHit(base, raid.raidLevel, raid.pathLevels[ToaPath.CRONDIS] ?: 0)

    private fun message(raid: ToaRaidSession, text: String) =
        raid.participants.keys.mapNotNull { it.resolve(players) }.forEach { it.mes(text) }

    private fun trace(raid: ToaRaidSession, event: String, detail: String) {
        if (
            raid.instance.id in zebakTraceRaids ||
                System.getProperty("openrune.toa.zebak.trace") == "true"
        ) {
            println(
                "[ToA-Zebak] tick=" +
                    clock.cycle +
                    " raid=" +
                    raid.party.id +
                    " event=" +
                    event +
                    " " +
                    detail
            )
        }
    }
}
