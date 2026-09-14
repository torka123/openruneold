package org.rsmod.content.bosses.doom

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.hit.visHitmark
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.prayer.ProtectionPrayerLock
import org.rsmod.api.player.prayer.disableProtectionPrayers
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.specials.energy.SpecialAttackEnergyRegeneration
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class DoomEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val bossEncounters: EncounterRegistry,
    private val accuracy: AccuracyFormulae,
    private val wornBonuses: WornBonuses,
    private val world: WorldRepository,
    private val bossHpBar: BossHpBarScript,
    private val rewards: DoomRewardService,
    private val stats: DoomScoreboardService,
    private val routeFactory: RouteFactory,
    private val worldQueues: WorldQueueList,
    private val queueProcess: WorldQueueListProcess,
) {
    private val log = InlineLogger()
    private val stepValidator = StepValidator(collision)
    private val RUN_STATE = InstanceStateKey<Run>("doom-of-mokhaiotl-encounter")
    private var generation = 1L

    private fun run(player: Player): Run? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == DOOM_INSTANCE_KEY && !it.isEnded }
            ?.stateOrNull(RUN_STATE)
            ?.takeIf { !it.retired && it.player === player && it.playerUid == player.uid }

    private fun run(npc: Npc): Run? =
        instances
            .instanceForNpc(npc)
            ?.let(instances::sessionForId)
            ?.takeIf { it.key == DOOM_INSTANCE_KEY && !it.isEnded }
            ?.stateOrNull(RUN_STATE)
            ?.takeUnless { it.retired }

    private fun activeRuns(): List<Run> =
        instances
            .sessionsForKey(DOOM_INSTANCE_KEY)
            .filterNot { it.isEnded }
            .mapNotNull { it.stateOrNull(RUN_STATE) }
            .filterNot { it.retired }

    private fun attachRun(run: Run) {
        check(run.session.state(RUN_STATE) { run } === run)
        bossEncounters.of(run.boss).apply {
            initializeDecisionSeed(run.generation)
            transitionTo(DOOM_INACTIVE_PHASE, clock.cycle)
            val encounter = this
            run.phases =
                DoomCombatPhases(this, clock, worldQueues, run.delve) {
                    run(run.player) === run && bossEncounters.isAttached(run.boss, encounter)
                }
            run.attacks =
                state("doom.attack-queues") {
                    DoomAttackQueues(worldQueues, queueProcess, clock) {
                        run(run.player) === run && bossEncounters.isAttached(run.boss, encounter)
                    }
                }
            onCleanup(run.attacks::close)
            lastAbilityTick = clock.cycle - expectedAttackRate()
        }
        // Session teardown removes the native indexes and typed state before invoking this closure.
        run.session.onCleanup { retire(run) }
    }

    fun validateAssets() {
        rewards.validateAssets()
        DoomAssets.npcIds.forEach {
            requireNotNull(ServerCacheManager.getNpc(it)) { "Missing Doom NPC $it" }
        }
        DoomAssets.locIds.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) { "Missing Doom location $it" }
        }
        DoomAssets.sequences.forEach(::anim)
        DoomAssets.spotanims.forEach(::spotInternal)
        listOf(DoomAssets.CHARGE_HEADBAR, DoomAssets.SHIELD_HEADBAR).forEach { gameval ->
            requireNotNull(
                ServerCacheManager.getHealthBar(RSCM.run { gameval.asRSCM(RSCMType.HEADBAR) })
            ) {
                "Missing Doom headbar $gameval"
            }
        }
        listOf(
                "obj.demon_tear",
                "obj.mokhaiotl_cloth",
                "obj.eye_of_ayak_uncharged",
                "obj.avernic_treads",
            )
            .forEach {
                requireNotNull(
                    ServerCacheManager.getItem(
                        dev.openrune.rscm.RSCM.run { it.asRSCM(RSCMType.OBJ) }
                    )
                ) {
                    "Missing Doom reward $it"
                }
            }
    }

    fun teleportOutside(player: Player): Boolean {
        val current = instances.sessionForPlayer(player)
        if (current != null && current.key != DOOM_INSTANCE_KEY) {
            player.mes("Leave your current instance before travelling to the Mokhaiotl delve.")
            return false
        }
        run(player)?.let { abort(player, "admin travel", teleport = false) }
        instances.sessionForPlayer(player)?.let { instances.leave(player, it, clock.cycle) }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, DOOM_OUTSIDE)
        player.mes("You arrive outside the Doom of Mokhaiotl delve.")
        return true
    }

    fun enter(player: Player, awaitPrompt: Boolean = false): Boolean {
        if (rewards.hasPending(player)) {
            player.mes("Claim the rewards in the lobby chest before beginning another delve.")
            return false
        }
        return createRun(
            player = player,
            delve = 1,
            allowProtected = awaitPrompt,
            awaitPrompt = awaitPrompt,
        )
    }

    fun debugEnter(player: Player, delve: Int): Boolean {
        if (delve !in 1..8) {
            player.mes("Choose a Doom delve from 1 to 8.")
            return false
        }
        if (rewards.hasPending(player)) {
            player.mes("Claim your pending Doom rewards before starting an isolated test delve.")
            return false
        }
        val currentSession = instances.sessionForPlayer(player)
        if (currentSession != null && currentSession.key != DOOM_INSTANCE_KEY) {
            player.mes("Leave your current instance before starting a Doom test delve.")
            return false
        }
        run(player)?.let { abort(player, "admin direct-delve test", teleport = false) }
        instances.sessionForPlayer(player)?.let { instances.leave(player, it, clock.cycle) }
        val created = createRun(player, delve, allowProtected = true, debug = true)
        if (created) {
            player.mes(
                "<col=ffff00>Isolated delve $delve test: rewards and account records are disabled.</col>"
            )
        }
        return created
    }

    fun beginDescend(player: Player): Descent? {
        val current = run(player) ?: return null
        if (current.state != DoomState.DEFEATED) {
            player.mes("Defeat the Doom of Mokhaiotl before descending further.")
            return null
        }
        val next = current.delve + 1
        val arenaSouthWest = local(current.session, current.delve, 22, 28)
        val carriedAcid =
            if (!DoomPersistenceRules.shouldCarryAcidInto(next)) {
                emptyList()
            } else {
                current.acid.map {
                    ArenaOffset(it.tile.x - arenaSouthWest.x, it.tile.z - arenaSouthWest.z)
                }
            }
        val demonicCharge = current.demonicCharge
        log.info {
            "[Doom] player=${player.displayName} generation=${current.generation} descend-start=${current.delve}->$next"
        }
        val descent =
            Descent(
                nextDelve = next,
                carriedAcid = carriedAcid,
                demonicCharge = demonicCharge,
                debug = current.debug,
                runId = current.runId,
                chainStartCycle = current.chainStartCycle,
            )
        // Move through the public lobby for one hidden server tick. This guarantees the old
        // dynamic scene and its NPC removals are flushed before a new copied region is allocated.
        endInstance(current, teleport = true)
        return descent
    }

    fun finishDescend(player: Player, descent: Descent, awaitPrompt: Boolean = false): Boolean {
        val created =
            createRun(
                player,
                descent.nextDelve,
                allowProtected = true,
                carriedAcid = descent.carriedAcid,
                demonicCharge = descent.demonicCharge,
                debug = descent.debug,
                awaitPrompt = awaitPrompt,
                runId = descent.runId,
                chainStartCycle = descent.chainStartCycle,
            )
        if (!created) {
            PathingEntityCommon.telejump(player, collision, DOOM_LOBBY)
            SpecialAttackEnergyRegeneration.restart(player)
            player.mes(
                "The next delve could not be prepared. You have been returned safely to the lobby."
            )
        }
        log.info {
            "[Doom] player=${player.displayName} descend-complete=${descent.nextDelve} created=$created"
        }
        return created
    }

    fun inspect(player: Player) {
        player.mes(rewards.summary(player))
        val run = run(player)
        if (run?.state == DoomState.DEFEATED) {
            player.mes("You may claim the rewards now or descend to delve ${run.delve + 1}.")
        }
    }

    fun canDescend(player: Player) = run(player)?.state == DoomState.DEFEATED

    fun currentDelve(player: Player): Int? = run(player)?.delve

    fun advanceEntryPrompt(player: Player): Boolean {
        val run = run(player) ?: return false
        if (run.state != DoomState.STARTING || run.bossRevealCycle != Int.MAX_VALUE) return false
        val revealCycle = DoomActivationRules.spawnCycle(clock.cycle)
        run.bossRevealCycle = revealCycle
        run.entryPromptFallbackCycle = Int.MAX_VALUE
        run.startCycle = revealCycle
        run.boss.lifecycleRevealCycle = revealCycle
        return true
    }

    fun pendingRewards(player: Player) = rewards.pending(player)

    fun pendingRewardValue(player: Player) = rewards.pendingValue(player)

    fun hasUniqueRewardRisk(player: Player) = rewards.hasUniqueRisk(player)

    fun scoreboardRead(player: Player) = stats.read(player)

    fun scoreboardGeneral(player: Player) = stats.general(player)

    fun scoreboardDelves(player: Player) = stats.delve(player)

    fun scoreboardSnapshot(player: Player) = stats.snapshot(player)

    fun recordDeath(player: Player) {
        run(player)?.let(::recordDeath)
    }

    private fun recordDeath(run: Run) {
        if (run.deathRecorded) return
        run.deathRecorded = true
        val player = run.player
        val hasCompletedRewards = rewards.pending(player).isNotEmpty()
        stats.recordDeath(player, "${run.runId}:death")
        rewards.clear(player)
        // Native PlayerDeath first selects the realm respawn. Preserve Doom's lobby return;
        // InstanceDeathCleanupHook and keyed leave still own membership/region teardown.
        PathingEntityCommon.telejump(player, collision, run.session.placement.exitCoord)
        player.mes(
            if (hasCompletedRewards)
                "Every reward accumulated during this delve has been forfeited."
            else "You return to the Mokhaiotl lobby."
        )
    }

    fun claim(player: Player): Boolean {
        val claimed = rewards.claim(player, player.coords)
        if (!claimed) return false
        finishRewardClaim(player)
        return true
    }

    fun claimToBank(player: Player, bank: org.rsmod.game.inv.Inventory): Boolean {
        val claimed = rewards.claimToBank(player, bank)
        if (!claimed) return false
        finishRewardClaim(player)
        return true
    }

    private fun finishRewardClaim(player: Player) {
        val run = run(player)
        if (run != null && run.state == DoomState.DEFEATED) {
            abort(player, "rewards claimed", teleport = true)
        }
    }

    private fun createRun(
        player: Player,
        delve: Int,
        allowProtected: Boolean = false,
        carriedAcid: List<ArenaOffset> = emptyList(),
        demonicCharge: Int = 0,
        debug: Boolean = false,
        awaitPrompt: Boolean = false,
        runId: String = UUID.randomUUID().toString(),
        chainStartCycle: Int = clock.cycle,
    ): Boolean {
        if (player.hitpoints <= 0 || (!allowProtected && player.isAccessProtected)) {
            player.mes("Finish what you are doing before entering the delve.")
            return false
        }
        if ((run(player) != null) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val type = requireNpc(DoomAssets.BOSS)
        val result =
            instances.create(
                player,
                DOOM_INSTANCE_KEY,
                doomSpec(delve, type),
                InstanceAccess.Private,
                clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Mokhaiotl delve is unavailable."
            )
            return false
        }
        SpecialAttackEnergyRegeneration.pause(player)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = DOOM_DROP_DURATION
        val bossRevealCycle =
            if (awaitPrompt) Int.MAX_VALUE else DoomActivationRules.spawnCycle(clock.cycle)
        val boss =
            spawnNpc(player, result.session, DoomAssets.BOSS, local(result.session, delve, 29, 35))
        npcs.hide(boss, if (awaitPrompt) Int.MAX_VALUE else DoomRules.BOSS_SPAWN_DELAY_TICKS)
        boss.baseHitpointsLvl = DoomRules.hitpoints(delve)
        boss.hitpoints = DoomRules.hitpoints(delve)
        boss.infoProtocol.setCombatLevel(DoomRules.combatLevel(delve))
        boss.baseAttackLvl = 300
        boss.attackLvl = 300
        boss.baseStrengthLvl = 190
        boss.strengthLvl = 190
        boss.baseDefenceLvl = 90
        boss.defenceLvl = 90
        boss.baseMagicLvl = 275
        boss.magicLvl = 275
        boss.baseRangedLvl = 110
        boss.rangedLvl = 110
        boss.movementLocked = true
        boss.apRangeOverride = DoomRules.BOSS_ARENA_ATTACK_RANGE
        boss.maxOpRangeOverride = DoomRules.BOSS_ARENA_MAX_OP_RANGE
        boss.apRequiresLineOfSight = DoomRules.BOSS_ATTACK_REQUIRES_LINE_OF_SIGHT
        val run =
            Run(
                player,
                result.session,
                generation++,
                delve,
                boss,
                startCycle = bossRevealCycle,
                bossRevealCycle = bossRevealCycle,
                entryPromptFallbackCycle =
                    if (awaitPrompt) clock.cycle + ENTRY_PROMPT_TIMEOUT_TICKS else Int.MAX_VALUE,
                startTile = player.coords,
                startingPrayerMask = prayerMask(player),
                debug = debug,
                runId = runId,
                chainStartCycle = chainStartCycle,
            )
        attachRun(run)
        run.demonicCharge = demonicCharge
        val arenaSouthWest = local(result.session, delve, 22, 28)
        carriedAcid.forEach { offset ->
            val tile = arenaSouthWest.translate(offset.x, offset.z)
            spawnLoc(tile, DoomAssets.ACID_ONE, Int.MAX_VALUE)?.let { loc ->
                run.acid += ActiveAcid(tile, Int.MAX_VALUE, loc)
            }
        }
        if (!awaitPrompt) {
            player.mes("<col=ff0000>Delve level: ${if (delve > 8) "8+ ($delve)" else delve}</col>")
        }
        log.info {
            "[Doom] player=${player.displayName} generation=${run.generation} delve=$delve instance=${run.session.id}"
        }
        return true
    }

    fun tickAll() {
        activeRuns().forEach { tick(it.player) }
    }

    fun tick(player: Player) {
        val run = run(player) ?: return
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (instances.sessionForPlayer(player)?.id != run.session.id) {
            abort(player, "left instance unexpectedly", teleport = false)
            return
        }

        run.rocks.removeAll { it.endCycle <= clock.cycle }
        if (!processFallingRocks(run)) return
        processPendingHits(run)
        processPendingBeam(run)
        processPendingAcid(run)
        processAcid(run)
        processLarvae(run)
        processVolatileEarth(run)
        processMovingShield(run)
        processActiveShockwaves(run)
        val defenceFloor = run.boss.baseDefenceLvl - 30
        if (run.boss.defenceLvl < defenceFloor) run.boss.defenceLvl = defenceFloor
        when (run.state) {
            DoomState.STARTING -> {
                if (
                    run.bossRevealCycle == Int.MAX_VALUE &&
                        clock.cycle >= run.entryPromptFallbackCycle
                ) {
                    run.bossRevealCycle = DoomActivationRules.spawnCycle(clock.cycle)
                    run.startCycle = run.bossRevealCycle
                    run.boss.lifecycleRevealCycle = run.bossRevealCycle
                }
                if (clock.cycle >= run.bossRevealCycle) {
                    run.boss.anim(anim(DoomAssets.ANIM_BURROW_EMERGE))
                    run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW_EMERGE))
                    activate(run)
                }
            }
            DoomState.ACTIVE -> {
                keepAggressive(run)
                val transition =
                    DoomPhasePriority.select(
                        shockwaveDue =
                            DoomRules.profile(run.delve).shockwaveEnabled &&
                                !run.shockwavePreparing &&
                                run.phases.shockwaveDue,
                        shockwavePreparing = run.shockwavePreparing,
                        shieldReady =
                            run.shieldAttacksRemaining == 0 &&
                                run.postShockwaveAttacksRemaining == 0,
                    )
                when (transition) {
                    DoomPhaseTransition.SHOCKWAVE -> {
                        when {
                            run.shockwavePreparing -> processShockwave(run)
                            !run.shockwavePreparing -> beginShockwavePreparation(run)
                        }
                    }
                    DoomPhaseTransition.SHIELD -> beginShieldPhase(run)
                    DoomPhaseTransition.NONE -> Unit
                }
            }
            DoomState.MELEE_CHARGE,
            DoomState.SHIELDED -> processCharge(run)
            DoomState.SHIELD_TRANSITION -> processShieldTransition(run)
            DoomState.SHOCKWAVE -> processShockwave(run)
            DoomState.BURROWING -> processBurrow(run)
            DoomState.DYING,
            DoomState.DEFEATED,
            DoomState.INVALIDATED -> Unit
        }
    }

    fun prepareAttack(npc: Npc, target: Player): Boolean {
        val run = run(target) ?: return false
        if (run.boss !== npc || run.state != DoomState.ACTIVE) return false
        if (npc.actionDelay > clock.cycle) return false
        if (run.rockVolleysRemaining > 0) return false
        if (run.shockwavePreparing && run.preShockwaveAttacksRemaining <= 0) return false

        val forceRock =
            (run.shockwavePreparing && run.forceRockBeforeShockwave) ||
                (run.postShockwaveAttacksRemaining > 0 && run.forceRockAfterShockwave)
        val encounter = bossEncounters.existing(npc) ?: return false
        encounter.prepareDecisionInputs(
            clock.cycle,
            buildMap {
                coincidentStandardStyle(run)?.let { put(DOOM_STYLE_INPUT, it.name) }
                if (run.shockwavePreparing || run.postShockwaveAttacksRemaining > 0) {
                    put(DOOM_CHARGE_INPUT, DOOM_NO_CHARGE)
                }
            },
        )
        if (
            forceRock &&
                !encounter.forceNextAbility(doomAbility(run.delve, DoomPrimaryAttack.ROCKS))
        )
            return false
        run.launchedPrimary = null
        npc.facePlayer(target)
        return true
    }

    fun attackParameters(npc: Npc, target: Player, primary: DoomPrimaryAttack): NpcAttackOverride {
        val run = checkNotNull(run(target))
        check(run.boss === npc)
        // Retain the former admission gate without tying an already-fired hit to combat mode.
        val launchAllowed = target.isValidTarget() && npc.isInCombat()
        return NpcAttackOverride(
            applyModifierOnImpact = primary == DoomPrimaryAttack.STANDARD,
            hitModifier = StandardPlayerHitModifier,
            hitValidity = HitValidity { launchAllowed },
            onAttack = { run.launchedPrimary = primary },
        )
    }

    fun rockAttack(access: StandardNpcAccess, target: Player) {
        val run = run(target) ?: return
        if (run.boss !== access.npc || run.state != DoomState.ACTIVE) return
        beginRockAttack(access, run)
        run.launchedPrimary = DoomPrimaryAttack.ROCKS
        bossEncounters.existing(access.npc)?.markDecisionLaunchAccepted()
    }

    fun afterAttack(npc: Npc, target: Player) {
        val run = run(target) ?: return
        if (run.boss !== npc) return
        val primary = run.launchedPrimary ?: return
        run.launchedPrimary = null
        val encounter = bossEncounters.existing(npc) ?: return
        val freeAttack = encounter.decisionResult(DOOM_PRIMARY_STREAM)?.drew == true
        val count = encounter.decisionDrawCount(DOOM_PRIMARY_STREAM)
        val cadence =
            when {
                run.delve <= 2 -> 6
                run.delve <= 4 -> 5
                run.delve <= 6 -> 4
                else -> 3
            }
        val spawn = freeAttack && count >= 4 && (count - 4) % cadence == 0L
        if (spawn) spawnLarvae(run)
        val charge = encounter.decisionResult(DOOM_CHARGE_STREAM)?.choice == DOOM_CHARGE
        if (DOOM_COMBAT_DEBUG) {
            log.info {
                "[DoomCombat] generation=${run.generation} delve=${run.delve} " +
                    "state=${run.state} turn=$count primary=$primary consumed=$freeAttack " +
                    "larvae=$spawn meleeCharge=$charge"
            }
        }
        if (charge) run.phases.armMeleeChargeAt(clock.cycle + 2)
        val forcedRock =
            encounter.lastAbilityName == doomAbility(run.delve, DoomPrimaryAttack.ROCKS)
        recordRotationAttack(run, primary, forcedRock, primary != DoomPrimaryAttack.TONGUE)
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = run(player)
        if (
            run == null ||
                !npc.isSpawnOwnedBy(player) ||
                (!hit.isFromPlayer && !hit.isFromNpc) ||
                hit.damage < 0
        ) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            when (run.state) {
                DoomState.MELEE_CHARGE -> {
                    if (hit.isFromPlayer && hit.type == HitType.Melee) {
                        hit.damage += wornBonuses.strengthBonus(player).coerceAtLeast(0) / 5
                        armPositiveBossImpact(run, hit) {
                            if (
                                !DoomIncomingAttackRules.interruptsMeleeCharge(
                                    state = run.state,
                                    hitType = hit.type,
                                    damage = hit.damage,
                                    isFromPlayer = hit.isFromPlayer,
                                )
                            ) {
                                return@armPositiveBossImpact
                            }
                            run.meleePunishUntilCycle = clock.cycle + 2
                            if (run.delve <= 8 && run.boss.hitpoints <= 0) {
                                applyHolyWaterFinisher(run)
                            } else {
                                sprayAcidAfterImpact(run)
                            }
                            interruptCharge(run, DoomState.ACTIVE)
                            run.boss.actionDelay =
                                maxOf(
                                    run.boss.actionDelay,
                                    clock.cycle + DoomRules.meleePunishDelay(run.delve),
                                )
                            armShieldPhaseAfterImpact(run)
                        }
                    } else {
                        hit.damage = 0
                    }
                }
                DoomState.SHIELDED -> {
                    val demonbane = isShieldDemonbane(hit)
                    hit.damage =
                        DoomShieldRuntimeRules.acceptedDamage(
                            demonbane = demonbane,
                            rolledDamage = hit.damage,
                            remainingShield = run.shieldHitpoints,
                        )
                    armShieldImpact(run, hit)
                }
                DoomState.SHIELD_TRANSITION -> {
                    // Resolve at the actual impact cycle: a launched attack can still strike
                    // normal health before the shield forms, or the shield if it forms first.
                    armShieldTransitionImpact(run, hit)
                }
                DoomState.BURROWING -> {
                    if (!hit.isFromPlayer) {
                        hit.damage = 0
                    } else {
                        armPositiveBossImpact(run, hit) {
                            if (
                                DoomEncounterRuntimeRules.shouldResetBurrowChargeOnHit(
                                    currentState = run.state,
                                    bossAssigned = run.boss.isSlotAssigned,
                                    beamPending = run.pendingBeamImpactCycle != Int.MAX_VALUE,
                                    bossAlive = run.boss.hitpoints > 0,
                                )
                            )
                                resetCharge(run)
                        }
                    }
                }
                DoomState.ACTIVE -> {
                    val recurrentMeleePunish =
                        hit.isFromPlayer &&
                            DoomIncomingAttackRules.isRecurrentMeleePunish(
                                state = run.state,
                                hitType = hit.type,
                                currentCycle = clock.cycle,
                                punishUntilCycle = run.meleePunishUntilCycle,
                            )
                    if (recurrentMeleePunish) {
                        hit.damage += wornBonuses.strengthBonus(player).coerceAtLeast(0) / 5
                    }
                    val lethalMeleePunish =
                        recurrentMeleePunish &&
                            run.delve <= 8 &&
                            hit.damage > 0 &&
                            run.boss.hitpoints - hit.damage <= 0
                    armPositiveBossImpact(run, hit) {
                        if (run.state != DoomState.ACTIVE) return@armPositiveBossImpact
                        cancelPendingBeam(run)
                        if (lethalMeleePunish && run.boss.hitpoints <= 0) {
                            applyHolyWaterFinisher(run)
                        } else {
                            sprayAcidAfterImpact(run)
                        }
                        armShieldPhaseAfterImpact(run)
                    }
                }
                else -> hit.damage = 0
            }
            return
        }
        val larva = run.larvae[npc]
        if (larva != null) {
            if (clock.cycle < larva.activeCycle) {
                hit.damage = 0
                return
            }
            val correct =
                DoomIncomingAttackRules.isCorrectLarvaStyle(
                    hitType = hit.type,
                    requiredStyle = larva.style,
                    protectedStyles = larva.protectedStyles,
                )
            val demonbane = isDemonbaneAttack(hit)
            val specialWeapon = larvaSpecialWeapon(hit)
            val lethalWeapon =
                demonbane ||
                    DoomIncomingAttackRules.specialWeaponCanOneShot(specialWeapon, larva.style)
            if (
                lethalWeapon &&
                    (demonbane ||
                        DoomIncomingAttackRules.specialWeaponRemovesAttackDelay(specialWeapon))
            ) {
                player.actionDelay = clock.cycle
            }
            hit.damage =
                DoomIncomingAttackRules.larvaDamage(
                    incomingDamage = hit.damage,
                    currentHitpoints = npc.hitpoints,
                    correctStyle = correct,
                    lethalWeapon = lethalWeapon,
                )
            if (hit.damage >= npc.hitpoints && hit.damage > 0) {
                npc.abortRoute()
                npc.movementLocked = true
                larva.lethalClaimExpiryCycle = clock.cycle + LARVA_LETHAL_CLAIM_TICKS
                val existing = hit.impactHandler
                hit.impactHandler = HitImpactHandler { impact ->
                    existing?.onImpact(impact)
                    if (impact.target === npc && run(player) === run && run.larvae[npc] === larva) {
                        larva.lethalClaimExpiryCycle = Int.MAX_VALUE
                    }
                    if (
                        impact.target === npc &&
                            impact.damage > 0 &&
                            npc.hitpoints == 0 &&
                            run(player) === run &&
                            run.larvae[npc] === larva
                    ) {
                        npc.abortRoute()
                        npc.movementLocked = true
                        npc.anim(anim(DoomAssets.ANIM_LARVA_DEATH))
                    }
                }
            }
            return
        }
        if (npc in run.volatiles) {
            val attackableCycle = run.volatileAttackableCycles[npc]
            if (
                run.shield != null ||
                    run.pendingEarthShield != null ||
                    (attackableCycle != null && clock.cycle < attackableCycle)
            ) {
                hit.damage = 0
            } else {
                hit.damage = player.vars["varp.com_maxhit"].coerceAtLeast(1)
                player.actionDelay = clock.cycle
                if (hit.damage >= npc.hitpoints) {
                    val existing = hit.impactHandler
                    hit.impactHandler = HitImpactHandler { impact ->
                        existing?.onImpact(impact)
                        if (
                            impact.target === npc &&
                                impact.damage > 0 &&
                                run(player) === run &&
                                npc in run.volatiles
                        ) {
                            selectVolatileEarth(run, npc)
                        }
                    }
                }
            }
            return
        }
        if (npc === run.shield) {
            hit.damage = 0
            return
        }
        hit.damage = 0
    }

    private fun armPositiveBossImpact(run: Run, hit: HitBuilder, action: () -> Unit) {
        val existing = hit.impactHandler
        hit.impactHandler = HitImpactHandler { impact ->
            existing?.onImpact(impact)
            if (
                impact.target === run.boss &&
                    impact.damage > 0 &&
                    run(run.player) === run &&
                    run.boss.isSlotAssigned
            ) {
                bossHpBar.onUpdate(
                    run.player,
                    run.boss,
                    run.boss.hitpoints,
                    run.boss.baseHitpointsLvl,
                )
                action()
            }
        }
    }

    fun beginNpcDeath(npc: Npc): Run? {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return null
        if (run.state in setOf(DoomState.DYING, DoomState.DEFEATED, DoomState.INVALIDATED))
            return null
        when (DoomEncounterRuntimeRules.bossDeathQueueAction(run.state, run.boss.hitpoints)) {
            DoomBossDeathQueueAction.FINISH_SHIELD -> {
                run.boss.clearQueue("queue.death")
                finishShieldPhase(run)
                return null
            }
            DoomBossDeathQueueAction.IGNORE -> {
                run.boss.clearQueue("queue.death")
                return null
            }
            DoomBossDeathQueueAction.ENCOUNTER_DEATH -> Unit
        }
        bossHpBar.onClose(run.player, run.boss, instant = true)
        run.state = DoomState.DYING
        SpecialAttackEnergyRegeneration.pause(run.player)
        clearChargeHeadbar(run)
        cleanupHazards(run, keepBoss = true, keepAcid = true)
        run.boss.anim(anim(DoomAssets.ANIM_DESPAWN))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_DESPAWN))
        run.player.combatClearQueue()
        run.player.clearInteraction()
        return run
    }

    fun finishNpcDeath(run: Run) {
        // Native death deletes the NPC/index before resuming this continuation. The retained
        // session token still authorizes exactly this defeated delve, never a replacement run.
        if (run(run.player) !== run || run.state != DoomState.DYING) return
        val resultId = "${run.runId}:${run.delve}"
        if (!run.debug) {
            rewards.accumulate(
                player = run.player,
                delve = run.delve,
                runId = run.runId,
                resultId = resultId,
            )
            val count = (run.player.attr[DOOM_KILL_COUNT] ?: 0) + 1
            run.player.attr[DOOM_KILL_COUNT] = count
            val best = run.player.attr[DOOM_BEST_DELVE] ?: 0
            if (run.delve > best) run.player.attr[DOOM_BEST_DELVE] = run.delve
        }
        val ticks = clock.cycle - run.startCycle
        if (!run.debug) {
            stats.recordCompletion(
                player = run.player,
                resultId = resultId,
                delve = run.delve,
                floorTicks = ticks,
                chainTicks = clock.cycle - run.chainStartCycle,
            )
        }
        val arenaSouthWest = local(run.session, run.delve, 22, 28)
        val arenaNorthEast = local(run.session, run.delve, 40, 46)
        val holeId =
            if (rewards.hasUniqueRisk(run.player)) DoomAssets.UNIQUE_BURROW_HOLE
            else DoomAssets.BURROW_HOLE
        val holeCandidates =
            DoomRewardHoleRules.candidates(
                bossSouthWest = run.boss.coords,
                bossSize = run.boss.size,
                arenaSouthWest = arenaSouthWest,
                arenaNorthEast = arenaNorthEast,
            )
        val placement =
            holeCandidates
                .asSequence()
                .filter { isOpenRewardHole(run, it) }
                .mapNotNull { tile -> spawnLoc(tile, holeId, Int.MAX_VALUE)?.let { tile to it } }
                .firstOrNull()
        run.hole = placement?.second
        run.hole?.let { world.locAnim(it, anim(DoomAssets.ANIM_HOLE_SPAWN)) }
        if (run.hole == null) {
            log.error {
                "[Doom] reward-hole placement failed player=${run.player.displayName} " +
                    "generation=${run.generation} delve=${run.delve}"
            }
            run.player.mes(
                "The reward burrow could not surface. Leave the instance and claim your rewards in the lobby."
            )
            abort(run.player, "reward hole placement failed", teleport = true, clearRewards = false)
            return
        }
        run.state = DoomState.DEFEATED
        run.player.mes(
            "<col=ff9900>Doom of Mokhaiotl delve ${run.delve} complete in ${DoomRules.formatTicks(ticks)}.</col>"
        )
        run.player.mes("Investigate the burrow to view rewards, or descend to continue deeper.")
    }

    fun beginAuxiliaryDeath(npc: Npc): Run? =
        run(npc)?.takeIf { npc in it.larvae || npc in it.volatiles || npc === it.shield }

    fun finishAuxiliaryDeath(npc: Npc, run: Run) {
        if (run(run.player) !== run) return
        val larva = run.larvae.remove(npc)
        if (larva != null) {
            val explosion = DoomLarvaRules.explosionTiles(npc.coords, larva.giant)
            renderLarvaExplosion(explosion)
            val caughtBoss =
                run.boss.isSlotAssigned &&
                    explosion.any { tile -> tileWithinNpcFootprint(tile, run.boss) }
            if (caughtBoss && run.boss.isSlotAssigned && !inTransitionHazardGrace(run)) {
                when {
                    run.state == DoomState.SHIELDED -> {
                        applyShieldExplosionDamage(run, 100)
                    }
                    else -> applyLarvaBossDamage(run, random.of(5..10))
                }
            } else if (run.player.coords in explosion && !inTransitionHazardGrace(run)) {
                mechanicHit(run, 21)
            }
            return
        }
        if (run.volatiles.remove(npc)) {
            run.volatileAttackableCycles.remove(npc)
            return
        }
        if (npc === run.shield) run.shield = null
    }

    fun isActive(player: Player) = run(player) != null

    fun leave(player: Player): Boolean {
        if (run(player) == null) return false
        abort(player, "explicit leave", teleport = true, clearRewards = false)
        player.mes("You leave the Doom of Mokhaiotl delve.")
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = run(player) ?: return RaidLeaveResult.Unhandled
        if (run.session.id != expected) return RaidLeaveResult.Unhandled
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        // Native leave has already cleared the player index; the keyed event retains session ID.
        val session = instances.sessionForId(instanceId) ?: return
        if (session.key != DOOM_INSTANCE_KEY) return
        val run = session.stateOrNull(RUN_STATE) ?: return
        if (run.retired || run.player !== player || run.playerUid != player.uid) return
        // Native unsafe death can teleport before cleanup hooks, while HP is still zero.
        if (player.hitpoints <= 0) recordDeath(run)
        retire(run)
    }

    fun isEncounterNpc(npc: Npc) =
        run(npc)?.let {
            it.boss === npc || npc in it.larvae || npc in it.volatiles || npc === it.shield
        } == true

    fun isOwnedBy(player: Player, npc: Npc) =
        run(player)?.let {
            npc.isSpawnOwnedBy(player) &&
                (it.boss === npc || npc in it.larvae || npc in it.volatiles || npc === it.shield)
        } == true

    fun forcePlayerAccuracy(player: Player, npc: Npc): Boolean =
        run(player)?.let { run ->
            npc === run.boss &&
                npc.isSpawnOwnedBy(player) &&
                run.state in setOf(DoomState.MELEE_CHARGE, DoomState.SHIELDED, DoomState.BURROWING)
        } == true

    fun consumeMeleeChargeAttackDelayBypass(player: Player, npc: Npc): Boolean {
        val run = run(player) ?: return false
        if (
            !DoomMeleeChargeAttackDelayRules.mayClaim(
                state = run.state,
                bossOwnedByPlayer = npc === run.boss && npc.isSpawnOwnedBy(player),
                alreadyClaimed = run.meleeChargeAttackClaimed,
            )
        ) {
            return false
        }
        run.meleeChargeAttackClaimed = true
        return true
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = run(player) ?: return "You do not have an active Mokhaiotl encounter."
        if (!isOwnedBy(player, npc)) return "That creature belongs to another encounter."
        if (npc === run.shield) return "The moving earthen shield cannot be attacked."
        if (npc in run.volatiles) {
            val attackableCycle = run.volatileAttackableCycles[npc]
            if (attackableCycle != null && clock.cycle < attackableCycle) {
                return "The volatile earth is still materialising."
            }
            if (run.shield != null || run.pendingEarthShield != null)
                return "The remaining volatile earth is no longer vulnerable."
        }
        run.larvae[npc]?.let {
            if (clock.cycle < it.activeCycle) return "The demonic energy is still materialising."
        }
        if (npc !== run.boss) return null
        return when (run.state) {
            DoomState.STARTING -> {
                advanceEntryPrompt(player)
                "The Doom of Mokhaiotl is still emerging."
            }
            DoomState.MELEE_CHARGE,
            DoomState.SHIELD_TRANSITION,
            DoomState.SHIELDED,
            DoomState.BURROWING -> null
            DoomState.ACTIVE -> null
            DoomState.SHOCKWAVE -> "Survive the shockwave before attacking again."
            else -> "The Doom of Mokhaiotl cannot be attacked right now."
        }
    }

    fun abort(player: Player, reason: String, teleport: Boolean, clearRewards: Boolean = false) {
        val run = run(player) ?: return
        if (clearRewards) rewards.clear(player)
        endInstance(run, teleport && player.isSlotAssigned)
        log.info {
            "[Doom] player=${player.displayName} generation=${run.generation} abort=$reason"
        }
    }

    fun debugStatus(player: Player): String {
        val run = run(player) ?: return "Doom: no active encounter. ${rewards.summary(player)}"
        return "Doom generation=${run.generation} delve=${run.delve} state=${run.state} " +
            "hp=${run.boss.hitpoints} shield=${run.shieldHitpoints} charge=${run.chargeProgress} " +
            "larvae=${run.larvae.size} demonicCharge=${run.demonicCharge} " +
            "volatile=${run.volatiles.size} rocks=${run.rocks.size} acid=${run.acid.size} " +
            "rewards=${rewards.pending(player).size}"
    }

    private fun activate(run: Run) {
        bossHpBar.setLabel(run.player, run.boss, DoomBossHud.label(run.delve))
        bossHpBar.onOpen(run.player, run.boss)
        bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.boss.baseHitpointsLvl)
        SpecialAttackEnergyRegeneration.restart(run.player)
        run.startCycle = clock.cycle
        run.state = DoomState.ACTIVE
        armNextShockwave(run)
        run.boss.movementLocked = true
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun renderLarvaExplosion(tile: CoordGrid, giant: Boolean) {
        val radius = if (giant) 2 else 1
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                world.spotanimMap(
                    SpotanimType(DoomAssets.GFX_BURROW_EXPLOSION_AOE),
                    tile.translate(dx, dz),
                )
            }
        }
    }

    private fun keepAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.mode == null)
            run.boss.apPlayer2(run.player, interactions)
    }

    private fun coincidentStandardStyle(run: Run): DoomAttackStyle? {
        val enabledStyles =
            if (run.delve == 1) {
                listOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
            } else {
                DoomAttackStyle.entries
            }
        val impactCycle = clock.cycle + DoomRules.standardProjectileTravelTicks(run.delve)
        val candidates =
            DoomImpactStyleRules.candidates(
                enabledStyles = enabledStyles,
                rockStylesByImpact = scheduledRockStyles(run),
                normalImpactCycle = impactCycle,
            )
        return candidates.singleOrNull()
    }

    private fun scheduledRockStyles(run: Run): List<Pair<Int, DoomAttackStyle>> = buildList {
        run.pendingHits.forEach { pending -> add(pending.impactCycle to pending.style) }
        run.fallingRocks.forEach { falling ->
            val debrisImpactCycle =
                if (falling.shadowImpactCycle != Int.MAX_VALUE) {
                    falling.shadowImpactCycle
                } else {
                    DoomCombatTimeline.rockShadowImpactCycle(falling.landCycle)
                }
            val timing = DoomRockFollowupRules.timing(run.delve, falling.volleyIndex)
            for (index in falling.followupStage until timing.size) {
                add(
                    debrisImpactCycle + timing[index].impactOffsetFromDebris to
                        falling.followupStyles[index]
                )
            }
        }
    }

    private fun beginRockAttack(access: StandardNpcAccess, run: Run) {
        reserveAttackTurn(access, run)
        run.rockMagic = random.of(2) == 0
        run.attacks.beginRockVolleys(
            count = DoomRules.rockSets(run.delve),
            tied = run.delve >= 8,
            eligible = { run.state in setOf(DoomState.ACTIVE, DoomState.MELEE_CHARGE) },
            launch = { index -> launchRockVolley(run, index) },
        )
    }

    private fun launchRockVolley(run: Run, volleyIndex: Int) {
        if (run.rockVolleysRemaining <= 0 || !run.boss.isSlotAssigned) return
        val magic = run.rockMagic ?: return
        val playerSnapshot = run.player.coords
        val splitTile = DoomRockTargeting.splitTile(playerSnapshot, bossCentre(run))
        val projectile = if (magic) DoomAssets.GFX_MAGIC_ROCK else DoomAssets.GFX_RANGED_ROCK
        val firstStyle = if (magic) DoomAttackStyle.MAGIC else DoomAttackStyle.RANGED
        val followupStyles =
            DoomRockFollowupRules.alternatingStyles(
                firstStyle,
                DoomRules.rockProjectiles(run.delve),
            )

        run.boss.anim(anim(DoomAssets.ANIM_ROCK_THROW))
        launchRockProjectile(bossCentre(run), splitTile, projectile, magic, run.delve, volleyIndex)
        run.fallingRocks +=
            FallingRock(
                tile = splitTile,
                magic = magic,
                landCycle =
                    DoomCombatTimeline.rockExplosionCycle(clock.cycle, run.delve, volleyIndex),
                followupStyles = followupStyles,
                volleyIndex = volleyIndex,
            )
    }

    private fun processFallingRocks(run: Run): Boolean {
        run.attacks.processRockVolleys()
        if (run.state == DoomState.ACTIVE && run.phases.consumeMeleeChargeReady()) {
            beginMeleeCharge(run)
        }

        val iterator = run.fallingRocks.iterator()
        while (iterator.hasNext()) {
            val falling = iterator.next()
            if (!falling.landed && clock.cycle >= falling.landCycle) {
                val reserved =
                    run.fallingRocks
                        .asSequence()
                        .filter { it !== falling && it.landed }
                        .flatMap { it.shadowTiles.asSequence() }
                        .toSet()
                val targetAtDetonation =
                    DoomRockTargeting.targetAtDetonation(
                        projectileImpactTile = falling.tile,
                        playerTileAtDetonation = run.player.coords,
                    )
                val plan =
                    DoomRockTargeting.tryPlan(
                        playerAtDetonation = targetAtDetonation,
                        candidates = rockShadowCandidates(run, falling.tile),
                        reserved = reserved,
                    )
                        ?: run {
                            log.error {
                                "[Doom] no valid rubble plan run=${run.runId} delve=${run.delve}"
                            }
                            abort(
                                run.player,
                                "rubble placement failed",
                                teleport = true,
                                clearRewards = false,
                            )
                            return false
                        }
                falling.rubbleTile = plan.target
                falling.shadowTiles += plan.tiles
                val impact =
                    if (falling.magic) DoomAssets.GFX_MAGIC_ROCK_IMPACT
                    else DoomAssets.GFX_RANGED_ROCK_IMPACT
                world.spotanimMap(
                    mapSpot(impact),
                    falling.tile,
                    height = ROCK_BREAK_SPOTANIM_HEIGHT,
                )
                world.soundArea(
                    falling.tile,
                    DoomAssets.SOUND_ROCK_BREAK,
                    loops = 1,
                    radius = 10,
                    size = 10,
                )
                // Doom turns overheads off on the rock explosion, but OSRS allows the player
                // to re-enable them immediately for the fully-accurate follow-up projectiles.
                // The shared helper installs a temporary lock for other encounters; clear it
                // here so only Doom has the immediate re-pray behavior.
                run.player.disableProtectionPrayers()
                ProtectionPrayerLock.clear(run.player)
                falling.shadowTiles.forEach { shadow ->
                    world.spotanimMap(mapSpot(DoomAssets.GFX_DEBRIS_SHADOW), shadow)
                    val graphic = random.of(DoomAssets.GFX_DEBRIS_START..DoomAssets.GFX_DEBRIS_END)
                    val debris =
                        ProjAnim.fromCoordToCoord(
                                falling.tile,
                                shadow,
                                RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                                "projanim.magic_spell",
                            )
                            .copy(
                                startHeight = 500,
                                endHeight = 0,
                                startTime = 0,
                                endTime = 60 + distance(falling.tile, shadow) * 3,
                                angle = 0,
                                progress = 0,
                                sourceIndex = 0,
                                targetIndex = 0,
                            )
                    world.projAnim(debris)
                }
                falling.landed = true
                falling.shadowImpactCycle = DoomCombatTimeline.rockShadowImpactCycle(clock.cycle)
            }

            if (
                falling.landed && !falling.shadowsLanded && clock.cycle >= falling.shadowImpactCycle
            ) {
                falling.shadowTiles.forEach { tile ->
                    val impactDelay = distance(falling.tile, tile) * 3
                    world.spotanimMap(
                        mapSpot(DoomAssets.GFX_DEBRIS_IMPACT),
                        tile,
                        delay = impactDelay,
                    )
                    world.soundArea(
                        tile,
                        DoomAssets.SOUND_DEBRIS_IMPACT,
                        delay = impactDelay,
                        loops = 1,
                        radius = 1,
                        size = 0,
                    )
                }
                val hitByDebris = run.player.coords in falling.shadowTiles
                falling.protectedAtImpact = false
                if (hitByDebris) {
                    mechanicHit(run = run, minimum = 15, maximum = 20)
                }
                falling.shadowsLanded = true
                falling.permanentRockCycle = clock.cycle + 1
                if (falling.protectedAtImpact) falling.rockSpawned = true
            }
            if (
                falling.shadowsLanded &&
                    !falling.rockSpawned &&
                    clock.cycle >= falling.permanentRockCycle
            ) {
                // Unprotected debris becomes permanent cover for later Car Slams.
                val rubbleTile = falling.rubbleTile ?: falling.tile
                clearAcidAt(run, rubbleTile)
                if (run.rocks.any { it.loc.coords == rubbleTile }) {
                    falling.rockSpawned = true
                } else {
                    val angle =
                        falling.rockAngle
                            ?: LocAngle.values()[random.of(0..3)].also { falling.rockAngle = it }
                    val rock = spawnLoc(rubbleTile, DoomAssets.ROCK, Int.MAX_VALUE, angle)
                    if (rock != null) {
                        run.rocks += ActiveRock(rock, Int.MAX_VALUE)
                        falling.rockSpawned = true
                    } else {
                        falling.rockSpawnAttempts++
                        val alternatives =
                            falling.shadowTiles.filter {
                                it != rubbleTile && isOpenRockShadow(run, it)
                            }
                        if (alternatives.isNotEmpty()) {
                            val nextIndex = (falling.rockSpawnAttempts - 1) % alternatives.size
                            falling.rubbleTile = alternatives[nextIndex]
                        }
                        if (falling.rockSpawnAttempts >= ROCK_SPAWN_MAX_ATTEMPTS) {
                            log.error {
                                "[Doom] rubble spawn exhausted run=${run.runId} delve=${run.delve}"
                            }
                            abort(
                                run.player,
                                "rubble spawn failed",
                                teleport = true,
                                clearRewards = false,
                            )
                            return false
                        }
                        if (
                            falling.rockSpawnAttempts == 1 ||
                                falling.rockSpawnAttempts % ROCK_SPAWN_RETRY_LOG_INTERVAL == 0
                        ) {
                            log.warn {
                                "[Doom] rubble spawn rejected; retrying " +
                                    "run=${run.runId} delve=${run.delve} tile=${rubbleTile} " +
                                    "attempt=${falling.rockSpawnAttempts}"
                            }
                        }
                    }
                }
            }
            if (falling.shadowsLanded) {
                val timing = DoomRockFollowupRules.timing(run.delve, falling.volleyIndex)
                while (falling.followupStage < timing.size) {
                    val index = falling.followupStage
                    val shot = timing[index]
                    val launchCycle = falling.shadowImpactCycle + shot.launchOffsetFromDebris
                    if (clock.cycle < launchCycle) break
                    val style = falling.followupStyles[index]
                    val followupMagic = style == DoomAttackStyle.MAGIC
                    val source =
                        DoomRockTargeting.followupSource(
                            debrisTiles = falling.shadowTiles,
                            rubbleTile = falling.rubbleTile ?: falling.tile,
                            index = index,
                            fallback = falling.tile,
                        )
                    launchRockFollowup(source, run.player, followupMagic, shot.travelTicks)
                    scheduleStyleHit(
                        run,
                        PendingStyleHit(
                            impactCycle = falling.shadowImpactCycle + shot.impactOffsetFromDebris,
                            style = style,
                            maxHit = DoomRules.standardMaxHit(run.delve),
                            guaranteedAccuracy = DoomRockFollowupRules.GUARANTEED_ACCURACY,
                        ),
                    )
                    falling.followupStage++
                }
            }
            if (
                falling.shadowsLanded &&
                    falling.followupStage >= DoomRules.rockProjectiles(run.delve) &&
                    falling.rockSpawned
            ) {
                iterator.remove()
            }
        }
        if (run.fallingRocks.isEmpty() && run.rockVolleysRemaining == 0) run.rockMagic = null
        return true
    }

    private fun processPendingHits(run: Run) {
        run.attacks.processHits()
    }

    private fun scheduleStyleHit(run: Run, pending: PendingStyleHit) {
        run.attacks.scheduleHit(pending) {
            val accurate =
                pending.guaranteedAccuracy ||
                    when (pending.style) {
                        DoomAttackStyle.MELEE ->
                            accuracy.rollMeleeAccuracy(
                                run.boss,
                                run.player,
                                MeleeAttackType.Crush,
                                random,
                            )
                        DoomAttackStyle.RANGED ->
                            accuracy.rollRangedAccuracy(run.boss, run.player, random)
                        DoomAttackStyle.MAGIC ->
                            accuracy.rollMagicAccuracy(run.boss, run.player, random)
                    }
            val damageRange =
                DoomEncounterRuntimeRules.delayedStyleDamageRange(
                    maxHit = pending.maxHit,
                    guaranteedAccuracy = pending.guaranteedAccuracy,
                    accurate = accurate,
                )
            run.player.queueImpactHit(
                source = run.boss,
                delay = 1,
                type = hitType(pending.style),
                damage = damageRange?.let(random::of) ?: 0,
                modifier = StandardPlayerHitModifier,
            )
        }
    }

    private fun launchTileProjectile(
        source: Npc,
        tile: CoordGrid,
        graphic: Int,
        controller: String,
    ): ProjAnim {
        val proj =
            ProjAnim.fromNpcToCoord(
                source,
                tile,
                RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                projectileType(controller),
            )
        world.projAnim(proj)
        return proj
    }

    private fun launchRockProjectile(
        source: CoordGrid,
        tile: CoordGrid,
        graphic: Int,
        magic: Boolean,
        delve: Int,
        volleyIndex: Int,
    ) {
        val projectile =
            ProjAnim.fromCoordToCoord(
                    source,
                    tile,
                    RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                    if (magic) "projanim.magic_spell" else "projanim.arrow",
                )
                .copy(
                    startHeight = 340,
                    endHeight = 500,
                    startTime = 60,
                    endTime =
                        if (delve >= 8) 180 + volleyIndex * 90 else if (delve >= 5) 180 else 210,
                    angle = 50,
                    progress = 124,
                    sourceIndex = 0,
                    targetIndex = 0,
                )
        world.projAnim(projectile)
    }

    private fun launchRockFollowup(
        source: CoordGrid,
        target: Player,
        magic: Boolean,
        travelTicks: Int,
    ) {
        val graphic =
            if (magic) DoomAssets.GFX_MAGIC_PROJECTILE else DoomAssets.GFX_RANGED_PROJECTILE
        val projectile =
            ProjAnim.fromCoordToPlayer(
                    source,
                    target,
                    RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                    if (magic) "projanim.magic_spell" else "projanim.arrow",
                )
                .copy(startHeight = 100, endHeight = 100, startTime = 0, endTime = travelTicks * 30)
        world.projAnim(projectile)
    }

    private fun launchPlayerProjectile(
        source: CoordGrid,
        target: Player,
        graphic: Int,
        controller: String,
    ): Int {
        val proj =
            ProjAnim.fromCoordToPlayer(
                source,
                target,
                RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                controller,
            )
        world.projAnim(proj)
        return proj.serverCycles.coerceAtLeast(1)
    }

    private fun beginMeleeCharge(run: Run) {
        run.state = DoomState.MELEE_CHARGE
        run.meleeChargeAttackClaimed = false
        run.boss.movementLocked = true
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_CHARGE))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BEAM_CHARGE))
        resetCharge(run)
        run.player.mes("The Doom begins charging. Interrupt it with a melee attack!")
    }

    private fun processCharge(run: Run) {
        if (!run.boss.isSlotAssigned) return
        if (run.pendingBeamImpactCycle != Int.MAX_VALUE) return
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_CHARGE_LOOP))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BEAM_CHARGE))
        run.phases.observeChargeProgress()
        run.attacks.processShieldLarvae()
        if (run.phases.consumeBeamReady()) finishCharge(run)
    }

    private fun resetCharge(run: Run) {
        run.phases.cancelMeleeCharge()
        run.meleeChargeAttackClaimed = false
        run.phases.resetBeamCharge()
        showChargeHeadbar(run)
    }

    private fun showChargeHeadbar(run: Run) {
        val type =
            ServerCacheManager.getHealthBar(
                RSCM.run { DoomAssets.CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            ) ?: return
        run.boss.showHeadbar(
            Headbar.fromNoSource(
                self = type.id,
                public = type.id,
                startFill = 0,
                endFill = type.segments.coerceIn(1, 255),
                startTime = 0,
                endTime = DoomRules.chargeTicks(run.state) * 30,
            )
        )
    }

    private fun clearChargeHeadbar(run: Run) {
        val type =
            ServerCacheManager.getHealthBar(
                RSCM.run { DoomAssets.CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            ) ?: return
        run.boss.showHeadbar(
            Headbar.fromNoSource(
                self = type.id,
                public = type.id,
                startFill = 0,
                endFill = 0,
                startTime = 0,
                endTime = 1,
            )
        )
    }

    private fun interruptCharge(run: Run, resume: DoomState) {
        run.phases.cancelMeleeCharge()
        run.meleeChargeAttackClaimed = false
        run.phases.stopBeamCharge()
        cancelPendingBeam(run)
        clearChargeHeadbar(run)
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_CANCEL))
        if (resume == DoomState.ACTIVE) restoreNormal(run)
    }

    private fun finishCharge(run: Run) {
        if (run.pendingBeamImpactCycle != Int.MAX_VALUE) return
        run.phases.cancelMeleeCharge()
        run.meleeChargeAttackClaimed = false
        val firingState = run.state
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_FIRE))
        clearChargeHeadbar(run)
        val travel = launchBeamSegments(run).coerceAtLeast(1)
        val maximum = DoomRules.beamMaxHit(run.delve)
        run.pendingBeamImpactCycle = clock.cycle + travel
        run.pendingBeamMaxHit = maximum
        run.pendingBeamState = firingState
        run.pendingBeamHit = mechanicFixedHit(run, maximum, delay = travel)
        run.phases.stopBeamCharge(clearProgress = false)
    }

    private fun launchBeamSegments(run: Run): Int {
        fun launch(graphic: Int, startTime: Int, endTime: Int): Int {
            val projectile =
                ProjAnim.fromNpcToPlayer(
                        run.boss,
                        run.player,
                        RSCM.run { spotInternal(graphic).asRSCM(RSCMType.SPOTANIM) },
                        projectileType("projanim.magic_spell"),
                    )
                    .copy(
                        startHeight = 100,
                        endHeight = 100,
                        startTime = startTime,
                        endTime = endTime,
                    )
            world.projAnim(projectile)
            return projectile.serverCycles
        }
        return listOf(
                launch(DoomAssets.GFX_BEAM_HEAD, startTime = 0, endTime = 25),
                launch(DoomAssets.GFX_BEAM_MIDDLE, startTime = 1, endTime = 26),
                launch(DoomAssets.GFX_BEAM_MIDDLE, startTime = 1, endTime = 27),
                launch(DoomAssets.GFX_BEAM_MIDDLE, startTime = 1, endTime = 28),
                launch(DoomAssets.GFX_BEAM_END, startTime = 2, endTime = 29),
            )
            .max()
    }

    private fun processPendingBeam(run: Run) {
        if (
            run.pendingBeamImpactCycle == Int.MAX_VALUE || clock.cycle < run.pendingBeamImpactCycle
        ) {
            return
        }
        val pendingHit = run.pendingBeamHit
        if (
            pendingHit != null && DoomBeamQueue.containsIdentity(run.player.queueList, pendingHit)
        ) {
            return
        }
        val firingState = run.pendingBeamState
        run.pendingBeamImpactCycle = Int.MAX_VALUE
        run.pendingBeamMaxHit = 0
        run.pendingBeamState = null
        run.pendingBeamHit = null
        world.spotanimMap(SpotanimType(DoomAssets.GFX_BEAM_IMPACT), run.player.coords)
        run.phases.stopBeamCharge()
        if (!run.boss.isSlotAssigned) return
        when (firingState) {
            DoomState.SHIELDED ->
                if (
                    DoomEncounterRuntimeRules.shouldFinishShieldPhaseAfterBeam(
                        firingState = firingState,
                        currentState = run.state,
                        bossAssigned = run.boss.isSlotAssigned,
                    )
                ) {
                    finishShieldPhase(run, beamFired = true)
                }
            DoomState.BURROWING ->
                if (
                    DoomEncounterRuntimeRules.shouldRestartBurrowCharge(
                        firingState = firingState,
                        currentState = run.state,
                        bossAssigned = run.boss.isSlotAssigned,
                    )
                ) {
                    resetCharge(run)
                }
            DoomState.MELEE_CHARGE -> restoreNormal(run)
            else -> restoreNormal(run)
        }
    }

    private fun cancelPendingBeam(run: Run) {
        run.pendingBeamHit?.let { pending ->
            DoomBeamQueue.removeIdentity(run.player.queueList, pending)
        }
        run.pendingBeamImpactCycle = Int.MAX_VALUE
        run.pendingBeamMaxHit = 0
        run.pendingBeamState = null
        run.pendingBeamHit = null
    }

    private fun reserveAttackTurn(access: StandardNpcAccess, run: Run) {
        access.actionDelay = access.mapClock + DoomRules.attackRate(run.delve)
    }

    private fun restoreNormal(run: Run) {
        restoreNormalHitpoints(run)
        run.boss.transmog(requireNpc(DoomAssets.BOSS), Int.MAX_VALUE)
        run.boss.suppressHitpointHeadbar = false
        run.boss.movementLocked = true
        run.state = DoomState.ACTIVE
        keepAggressive(run)
    }

    private fun beginShieldPhase(run: Run) {
        run.normalHitpoints =
            DoomShieldRuntimeRules.captureNormalHitpoints(
                storedHitpoints = run.normalHitpoints,
                currentHitpoints = run.boss.hitpoints,
            )
        run.shieldHitpoints = DoomRules.SHIELD_HITPOINTS
        run.shieldAttacksRemaining = -1
        clearNormalHitpointHeadbar(run)
        run.boss.suppressHitpointHeadbar = true
        run.shieldEpoch++
        run.state = DoomState.SHIELD_TRANSITION
        val encounter = checkNotNull(bossEncounters.existing(run.boss))
        val epoch = run.shieldEpoch
        run.phases.armShield(epoch) {
            run(run.player) === run &&
                bossEncounters.isAttached(run.boss, encounter) &&
                run.shieldEpoch == epoch &&
                run.state == DoomState.SHIELD_TRANSITION
        }
        syncShieldBossHud(run)
        run.phases.cancelMeleeCharge()
        run.meleeChargeAttackClaimed = false
        run.phases.stopBeamCharge(clearProgress = false)
        clearChargeHeadbar(run)
        run.boss.scriptedDamageGate = true
        run.boss.movementLocked = true
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_CHARGE))
        run.player.mes("The Doom raises a 500-hitpoint demonic shield. Use demonbane attacks!")
    }

    private fun processShieldTransition(run: Run) {
        if (!run.phases.consumeShieldReady(run.shieldEpoch)) return
        run.normalHitpoints =
            DoomShieldRuntimeRules.captureNormalHitpoints(
                storedHitpoints = run.normalHitpoints,
                currentHitpoints = run.boss.hitpoints,
            )
        run.state = DoomState.SHIELDED
        run.boss.anim(anim(DoomAssets.ANIM_BEAM_CANCEL))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BEAM_CHARGE))
        run.boss.transmog(requireNpc(DoomAssets.SHIELDED), Int.MAX_VALUE)
        run.boss.baseHitpointsLvl = DoomRules.SHIELD_HITPOINTS
        run.shieldHitpoints = DoomRules.SHIELD_HITPOINTS
        run.boss.hitpoints = run.shieldHitpoints
        run.boss.suppressHitpointHeadbar = true
        run.boss.suppressHitpointsRegen = true
        run.shieldLarvaStream = null
        val epoch = run.shieldEpoch
        run.attacks.armShieldLarvae(
            tick = DoomCombatTimeline.firstShieldLarvaSpawnCycle(clock.cycle),
            eligible = { run.state == DoomState.SHIELDED && run.shieldEpoch == epoch },
        ) {
            spawnLarvae(run, shieldPhase = true)
            val interval = random.of(DoomRules.shieldLarvaIntervalRange(run.delve))
            DoomCombatTimeline.nextShieldLarvaSpawnCycle(clock.cycle, run.delve, interval)
        }
        showShieldHitpointHeadbar(run)
        syncShieldBossHud(run)
        resetCharge(run)
    }

    private fun finishShieldPhase(run: Run, beamFired: Boolean = false) {
        run.shieldEpoch++
        run.boss.clearQueue("queue.death")
        clearChargeHeadbar(run)
        if (!beamFired) run.boss.anim(anim(DoomAssets.ANIM_BEAM_CANCEL))
        restoreNormalHitpoints(run)
        run.boss.transmog(requireNpc(DoomAssets.BOSS), Int.MAX_VALUE)
        run.boss.suppressHitpointHeadbar = false
        run.boss.scriptedDamageGate = false
        run.boss.suppressHitpointsRegen = false
        run.shieldHitpoints = 0
        run.attacks.cancelShieldLarvae()
        run.shieldLarvaStream = null
        run.phases.stopBeamCharge()
        if (DoomRules.profile(run.delve).carEnabled) beginBurrow(run) else restoreNormal(run)
        syncNormalBossHud(run)
    }

    private fun armShieldImpact(run: Run, hit: HitBuilder) {
        val existing = hit.impactHandler
        val shieldEpoch = run.shieldEpoch
        hit.impactHandler = HitImpactHandler { impact ->
            existing?.onImpact(impact)
            if (impact.target !== run.boss) return@HitImpactHandler
            resolveShieldImpact(run, shieldEpoch, impact.damage)
        }
    }

    private fun armShieldTransitionImpact(run: Run, hit: HitBuilder) {
        val existing = hit.impactHandler
        val shieldEpoch = run.shieldEpoch
        val demonbane = isShieldDemonbane(hit)
        hit.impactHandler = HitImpactHandler { impact ->
            existing?.onImpact(impact)
            if (impact.target !== run.boss || run(run.player) !== run) {
                return@HitImpactHandler
            }
            val resolution =
                DoomShieldTransitionRules.resolve(
                    epochMatches = shieldEpoch == run.shieldEpoch,
                    impactState = run.state,
                    demonbane = demonbane,
                    impactDamage = impact.damage,
                )
            when (resolution.destination) {
                DoomShieldTransitionDestination.NORMAL_HEALTH -> {
                    run.normalHitpoints = run.boss.hitpoints
                    sprayAcidAfterImpact(run)
                }
                DoomShieldTransitionDestination.SHIELD -> {
                    when {
                        resolution.requiresScriptedDamage ->
                            applyShieldExplosionDamage(run, resolution.shieldDamage)
                        resolution.damagesShield ->
                            resolveShieldImpact(
                                run = run,
                                shieldEpoch = shieldEpoch,
                                damage = resolution.shieldDamage,
                                resetChargeOnImpact = resolution.resetsCharge,
                            )
                    }
                }
                DoomShieldTransitionDestination.STALE_PHASE ->
                    restoreStaleShieldImpact(run, impact.damage)
            }
        }
    }

    private fun resolveShieldImpact(
        run: Run,
        shieldEpoch: Int,
        damage: Int,
        resetChargeOnImpact: Boolean = true,
    ) {
        if (run(run.player) !== run || !run.boss.isSlotAssigned) return
        if (shieldEpoch != run.shieldEpoch || run.state != DoomState.SHIELDED) {
            restoreStaleShieldImpact(run, damage)
            return
        }
        run.shieldHitpoints = run.boss.hitpoints.coerceIn(0, DoomRules.SHIELD_HITPOINTS)
        showShieldHitpointHeadbar(run)
        syncShieldBossHud(run)
        if (resetChargeOnImpact && damage > 0) {
            resetCharge(run)
            cancelPendingBeam(run)
        }
        if (run.shieldHitpoints == 0) {
            run.boss.clearQueue("queue.death")
            finishShieldPhase(run)
        }
    }

    private fun applyShieldExplosionDamage(run: Run, requestedDamage: Int) {
        if (run.state != DoomState.SHIELDED || !run.boss.isSlotAssigned) return
        val previous = run.shieldHitpoints
        val remaining = DoomShieldRuntimeRules.remainingShield(previous, requestedDamage)
        val damage = previous - remaining
        run.shieldHitpoints = remaining
        run.boss.hitpoints = remaining
        showScriptedShieldDamage(run, damage)
        if (damage > 0) {
            resetCharge(run)
            cancelPendingBeam(run)
        }
        if (remaining == 0) {
            run.boss.clearQueue("queue.death")
            finishShieldPhase(run)
        }
    }

    private fun restoreStaleShieldImpact(run: Run, damage: Int) {
        if (
            damage <= 0 ||
                !run.boss.isSlotAssigned ||
                run.state in setOf(DoomState.DYING, DoomState.DEFEATED, DoomState.INVALIDATED)
        ) {
            return
        }
        run.boss.clearQueue("queue.death")
        run.boss.hitpoints =
            DoomShieldRuntimeRules.restoredHitpointsAfterStaleShieldImpact(
                currentHitpoints = run.boss.hitpoints,
                maximumHitpoints = run.boss.baseHitpointsLvl,
                appliedDamage = damage,
            )
        showCurrentHitpointHeadbar(run.boss)
        bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.boss.baseHitpointsLvl)
    }

    private fun showScriptedShieldDamage(run: Run, damage: Int) {
        if (damage <= 0) return
        val hitmarkGroup = run.boss.visHitmark()
        val lit = RSCM.run { hitmarkGroup.lit.asRSCM(RSCMType.HITMARK) }
        val tint = hitmarkGroup.tint?.let { RSCM.run { it.asRSCM(RSCMType.HITMARK) } } ?: lit
        run.boss.showHitmark(Hitmark.fromNoSource(lit, lit, tint, damage, 0))
        showShieldHitpointHeadbar(run)
        syncShieldBossHud(run)
    }

    private fun showShieldHitpointHeadbar(run: Run) {
        val headbar =
            ServerCacheManager.getHealthBar(
                RSCM.run { DoomAssets.SHIELD_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            ) ?: return
        val fill =
            (run.shieldHitpoints.coerceIn(0, DoomRules.SHIELD_HITPOINTS) * headbar.segments /
                    DoomRules.SHIELD_HITPOINTS)
                .coerceIn(0, headbar.segments)
        run.boss.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun clearNormalHitpointHeadbar(run: Run) {
        val headbar = run.boss.visHeadbar(params.headbar)
        run.boss.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = 0,
                endFill = 0,
                startTime = 0,
                endTime = 1,
            )
        )
    }

    private fun showCurrentHitpointHeadbar(npc: Npc) {
        val maximum = npc.baseHitpointsLvl.coerceAtLeast(1)
        val headbar = npc.visHeadbar(params.headbar)
        val fill =
            (npc.hitpoints.coerceIn(0, maximum) * headbar.segments / maximum).coerceIn(
                0,
                headbar.segments,
            )
        npc.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun syncShieldBossHud(run: Run) {
        bossHpBar.setLabel(run.player, run.boss, DoomBossHud.shieldLabel())
        bossHpBar.onOpenCustom(
            player = run.player,
            npc = run.boss,
            currentValue = run.shieldHitpoints,
            maximumValue = DoomRules.SHIELD_HITPOINTS,
            style = DoomBossHud.SHIELD_STYLE,
        )
    }

    private fun syncNormalBossHud(run: Run) {
        if (DoomBossHud.showsNormalHealth(run.state)) {
            bossHpBar.setLabel(run.player, run.boss, DoomBossHud.label(run.delve))
            bossHpBar.onOpen(run.player, run.boss)
            bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.boss.baseHitpointsLvl)
        } else {
            bossHpBar.onClose(run.player, run.boss, instant = true)
        }
    }

    private fun restoreNormalHitpoints(run: Run) {
        val maximum = DoomRules.hitpoints(run.delve)
        val restored =
            DoomShieldRuntimeRules.restoreNormalHitpoints(
                storedHitpoints = run.normalHitpoints,
                maximumHitpoints = maximum,
            ) ?: return
        run.boss.baseHitpointsLvl = maximum
        run.boss.hitpoints = restored
        run.normalHitpoints = -1
    }

    private fun recordRotationAttack(
        run: Run,
        primary: DoomPrimaryAttack,
        usedForcedRock: Boolean,
        advancesPhaseCounters: Boolean,
    ) {
        if (!advancesPhaseCounters) return
        if (run.shieldAttacksRemaining > 0) {
            run.shieldAttacksRemaining =
                DoomShieldRules.attacksRemainingAfter(run.shieldAttacksRemaining, primary)
        }
        if (run.shockwavePreparing && run.preShockwaveAttacksRemaining > 0) {
            run.preShockwaveAttacksRemaining--
            if (usedForcedRock) run.forceRockBeforeShockwave = false
        }
        if (run.postShockwaveAttacksRemaining > 0) {
            run.postShockwaveAttacksRemaining--
            if (usedForcedRock) run.forceRockAfterShockwave = false
        }
    }

    private fun armShieldPhaseAfterImpact(run: Run) {
        if (
            DoomShieldRules.shouldArmAfterImpact(run.delve, run.shieldTriggered, run.boss.hitpoints)
        ) {
            run.shieldTriggered = true
            run.shieldAttacksRemaining = DoomShieldRules.initialStandardAttacksRemaining()
        }
    }

    private fun sprayAcidAfterImpact(run: Run) {
        if (
            run.boss.hitpoints <= 0 ||
                !DoomRules.profile(run.delve).acidEnabled ||
                !DoomEncounterRuntimeRules.acidPhaseAllowed(run.state)
        ) {
            return
        }
        sprayAcid(run)
    }

    private fun sprayAcid(run: Run) {
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val (dx, dz) = directions[random.of(directions.indices)]
        val count = random.of(DoomRules.acidTilesPerHit(run.delve))
        val origin = DoomGeometry.centre(run.boss.coords, run.boss.size)
        val pattern =
            listOf(3 to 0, 4 to -1, 4 to 1, 5 to -2, 5 to 2, 6 to 0, 7 to -1, 7 to 1, 8 to 0)
        val candidates =
            pattern
                .map { (forward, lateral) ->
                    origin.translate(dx * forward + dz * lateral, dz * forward + dx * lateral)
                }
                .filter { isOpenAcidTile(run, it) }
                .shuffledByGameRandom(random)
        candidates.take(count).forEach { tile ->
            val projectile =
                launchTileProjectile(
                    run.boss,
                    tile,
                    DoomAssets.GFX_ACID_PROJECTILE,
                    "projanim.magic_spell",
                )
            run.pendingAcid +=
                PendingAcid(
                    tile,
                    DoomAssets.ACID_ONE,
                    clock.cycle + projectile.serverCycles.coerceAtLeast(1),
                    DoomRules.acidSplashGraphic(dx, dz),
                )
        }
    }

    private fun isOpenAcidTile(run: Run, tile: CoordGrid): Boolean {
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        if (
            tile.level != southWest.level ||
                tile.x !in southWest.x..northEast.x ||
                tile.z !in southWest.z..northEast.z ||
                !collision.isZoneValid(tile) ||
                collision.isWalkBlocked(tile) ||
                DoomGeometry.contains(run.boss.coords, tile, run.boss.size)
        ) {
            return false
        }
        if (run.rocks.any { it.loc.coords == tile }) return false
        if (run.fallingRocks.any { it.tile == tile }) return false
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        return true
    }

    private fun processPendingAcid(run: Run) {
        val iterator = run.pendingAcid.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (clock.cycle < pending.impactCycle) continue
            // A projectile launched by a valid normal-state hit remains valid if the shield
            // transition completes before it lands (captured at doom-3340 tick 2976).
            world.spotanimMap(SpotanimType(pending.graphic), pending.tile)
            if (run.acid.none { it.tile == pending.tile }) {
                spawnLoc(pending.tile, pending.locId, Int.MAX_VALUE)?.let {
                    run.acid += ActiveAcid(pending.tile, Int.MAX_VALUE, it)
                }
            }
            iterator.remove()
        }
    }

    private fun processAcid(run: Run) {
        run.acid.removeAll { it.endCycle <= clock.cycle }
        if (inTransitionHazardGrace(run)) return
        if (
            !DoomEncounterRuntimeRules.acidHitAllowed(
                state = run.state,
                currentCycle = clock.cycle,
                lastHitCycle = run.lastAcidHitCycle,
            )
        ) {
            return
        }
        if (run.acid.any { it.tile == run.player.coords }) {
            run.lastAcidHitCycle = clock.cycle
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(1..DoomRules.ACID_MAX_HIT),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                impactHandler =
                    HitImpactHandler { impact ->
                        if (run(run.player) === run && impact.damage > 0) {
                            PlayerVenom.tryVenom(run.player)
                        }
                    },
            )
        }
    }

    private fun spawnLarvae(run: Run, shieldPhase: Boolean = false) {
        val context = if (shieldPhase) DoomLarvaContext.SHIELD else DoomLarvaContext.OUTSIDE_SHIELD
        val profile = DoomLarvaRules.profile(run.delve, context)
        val desiredCount = DoomRules.profile(run.delve).outsideLarvaCount
        val activeOutsideCount =
            run.larvae.count { (npc, larva) -> !larva.shieldPhase && npc.isSlotAssigned }
        // Outside larvae arrive as one stable wave. Do not refill a partially killed wave on
        // every subsequent boss attack; that was the visible disappear-and-reappear loop.
        if (!shieldPhase && activeOutsideCount > 0) return
        // Shield larvae are cadence-driven and may overlap while an earlier larva is still alive.
        val count = if (shieldPhase) 1 else desiredCount
        if (count == 0) return
        val spawnTiles = larvaSpawnTiles(run, shieldPhase, count)
        if (spawnTiles.size != count) {
            log.error {
                "[Doom] larva placement failed generation=${run.generation} delve=${run.delve} " +
                    "shield=$shieldPhase requested=$count available=${spawnTiles.size}"
            }
            abort(run.player, "larva placement failed", teleport = true, clearRewards = false)
            return
        }
        spawnTiles.forEach { spawn ->
            val styleOptions = DoomLarvaRules.styleOptions(run.delve, context)
            val style = styleOptions[random.of(styleOptions.indices)]
            val protectedStyles =
                when {
                    DoomLarvaRules.twoProtectionEnabled(run.delve) && style != null ->
                        DoomAttackStyle.entries.filterTo(linkedSetOf()) { it != style }
                    DoomLarvaRules.protectionEnabled(run.delve) ->
                        setOf(DoomAttackStyle.entries[random.of(DoomAttackStyle.entries.indices)])
                    else -> emptySet()
                }
            val id =
                when {
                    profile.giant && style == DoomAttackStyle.MAGIC -> DoomAssets.GIANT_MAGIC
                    profile.giant -> DoomAssets.GIANT_RANGED
                    style == DoomAttackStyle.RANGED -> DoomAssets.LARVA_RANGED
                    style == DoomAttackStyle.MAGIC -> DoomAssets.LARVA_MAGIC
                    style == DoomAttackStyle.MELEE -> DoomAssets.LARVA_MELEE
                    else -> DoomAssets.LARVA_NEUTRAL
                }
            val npc = spawnNpc(run.player, run.session, id, spawn)
            npc.baseHitpointsLvl = profile.hitpoints
            npc.hitpoints = npc.baseHitpointsLvl
            // The encounter applies its own one-damage cap and demonbane one-hit rule.
            // Keep the shared post-script flat-armour step from reducing a lethal hit.
            npc.vars["varn.flat_armour"] = 0
            npc.movementLocked = true
            npc.hideAllOps()
            npc.anim(anim(DoomAssets.ANIM_LARVA_SPAWN))
            DoomLarvaRules.headIconIndices(style, protectedStyles).forEachIndexed { slot, index ->
                npc.infoProtocol.setHeadIcon(slot, DoomAssets.LARVA_HEADICON_GROUP, index)
            }
            val movementCycle = DoomCombatTimeline.larvaMovementCycle(clock.cycle)
            val activeCycle = DoomCombatTimeline.larvaAttackableCycle(clock.cycle)
            run.larvae[npc] =
                Larva(
                    style = style,
                    giant = profile.giant,
                    protectedStyles = protectedStyles,
                    shieldPhase = shieldPhase,
                    spawnCycle = clock.cycle,
                    movementCycle = movementCycle,
                    activeCycle = activeCycle,
                    lastProgressTile = spawn,
                    lastProgressCycle = clock.cycle,
                    lastRouteCycle = clock.cycle,
                )
            world.spotanimMap(SpotanimType(DoomAssets.GFX_LARVA_SPAWN), spawn)
        }
    }

    private fun processLarvae(run: Run) {
        val iterator = run.larvae.iterator()
        while (iterator.hasNext()) {
            val (npc, larva) = iterator.next()
            if (!npc.isSlotAssigned) {
                iterator.remove()
                continue
            }
            if (!run.boss.isSlotAssigned) continue
            if (!DoomEncounterRuntimeRules.mayAdvanceLarva(npc.hitpoints)) continue
            if (larva.lethalClaimExpiryCycle != Int.MAX_VALUE) {
                if (clock.cycle < larva.lethalClaimExpiryCycle) continue
                larva.lethalClaimExpiryCycle = Int.MAX_VALUE
                npc.movementLocked = false
                if (larva.crawlStarted) {
                    larva.lastRouteCycle = clock.cycle
                    routeLarvaToBoss(run, npc)
                }
            }

            val bossSurrounded =
                DoomLarvaRules.isBossSurroundedByRocks(
                    bossSouthWest = run.boss.coords,
                    bossSize = run.boss.size,
                    rockTiles = run.rocks.mapTo(hashSetOf()) { it.loc.coords },
                )
            if (
                DoomLarvaRules.shouldDespawn(
                    larva.spawnCycle,
                    clock.cycle,
                    run.bossHasRepositioned,
                    bossSurrounded,
                )
            ) {
                npc.abortRoute()
                npcs.del(npc, Int.MAX_VALUE)
                iterator.remove()
                continue
            }

            if (!larva.crawlStarted && clock.cycle >= larva.movementCycle) {
                larva.crawlStarted = true
                npc.defaultMoveSpeed = MoveSpeed.Crawl
                npc.movementLocked = false
                larva.targetTile = run.boss.coords
                larva.lastRouteCycle = clock.cycle
                routeLarvaToBoss(run, npc)
            }
            if (clock.cycle < larva.activeCycle) continue
            if (!larva.opsVisible) {
                larva.opsVisible = true
                npc.showAllOps()
            }

            val distanceToBoss =
                DoomGeometry.distanceToFootprint(run.boss.coords, npc.coords, run.boss.size)
            val reachedBoss = DoomLarvaRouteRules.hasReachedBoss(distanceToBoss)
            if (reachedBoss) {
                val healing = DoomRules.larvaHeal(run.demonicCharge, larva.giant)
                when (DoomLarvaHealingRules.pool(run.state)) {
                    DoomLarvaHealPool.STORED_NORMAL -> {
                        run.normalHitpoints =
                            (run.normalHitpoints + healing).coerceAtMost(
                                DoomRules.hitpoints(run.delve)
                            )
                    }
                    DoomLarvaHealPool.ACTIVE_BOSS -> {
                        run.boss.heal(healing)
                        if (run.state == DoomState.SHIELD_TRANSITION) {
                            run.normalHitpoints = run.boss.hitpoints
                        }
                        if (DoomBossHud.showsNormalHealth(run.state)) {
                            bossHpBar.onUpdate(
                                run.player,
                                run.boss,
                                run.boss.hitpoints,
                                run.boss.baseHitpointsLvl,
                            )
                        }
                    }
                }
                if (!inTransitionHazardGrace(run)) {
                    mechanicFixedHit(
                        run,
                        DoomRules.larvaReachDamage(run.delve, run.demonicCharge, larva.giant),
                    )
                }
                run.demonicCharge++
                world.spotanimMap(SpotanimType(DoomAssets.GFX_LARVA_PLAYER_IMPACT), npc.coords)
                npcs.del(npc, Int.MAX_VALUE)
                iterator.remove()
                continue
            }

            if (npc.coords != larva.lastProgressTile) {
                larva.lastProgressTile = npc.coords
                larva.lastProgressCycle = clock.cycle
            }

            val currentTarget = run.boss.coords
            val targetChanged = larva.targetTile != currentTarget
            val stalled =
                clock.cycle - larva.lastProgressCycle >= LARVA_REPATH_TICKS &&
                    clock.cycle - larva.lastRouteCycle >= LARVA_REPATH_TICKS
            if (targetChanged || stalled) {
                larva.lastRouteCycle = clock.cycle
                if (routeLarvaToBoss(run, npc)) {
                    larva.targetTile = currentTarget
                }
            }
        }
    }

    private fun routeLarvaToBoss(run: Run, npc: Npc): Boolean {
        // Doom larvae are scripted movers. Their cache default is Wander, which otherwise aborts
        // this route on the next NPC mode tick and sends them toward an arbitrary arena edge.
        npc.mode = NpcMode.None
        npc.moveRestrict = MoveRestrict.PassThru
        val route = routeFactory.create(npc.avatar, bossCentre(run))
        val final = route.lastOrNull()
        if (final != null) {
            val finalTile = CoordGrid(final.x, final.z, final.level)
            val finalDistance =
                DoomGeometry.distanceToFootprint(run.boss.coords, finalTile, run.boss.size)
            if (DoomLarvaRouteRules.accept(route.success, route.size, finalDistance)) {
                npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
                return true
            }
        }
        // Never replace a rejected collision route with a blind straight line. Npc.walk does not
        // pathfind that list and can slide a blocked larva into a corner; retry a real route.
        return false
    }

    private fun larvaSpawnTiles(run: Run, shieldPhase: Boolean, count: Int): List<CoordGrid> =
        if (shieldPhase) listOfNotNull(shieldLarvaSpawnTile(run))
        else outsideLarvaSpawnTiles(run, count)

    private fun outsideLarvaSpawnTiles(run: Run, count: Int): List<CoordGrid> {
        val boss = bossCentre(run)
        val player = run.player.coords
        // OSRS chooses larvae from the player's side of the arena, with a farther-side
        // fallback. Keep the candidate order deterministic; only the chosen open tile varies
        // when a rock/acid wall blocks the preferred stream.
        val sideX = player.x.compareTo(boss.x)
        val sideZ = player.z.compareTo(boss.z).let { if (it == 0) -1 else it }
        val preferred = clampArena(run, player.translate(sideX * 6, sideZ * 6))
        val farther = clampArena(run, player.translate(sideX * 9, sideZ * 9))
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        val candidates: List<CoordGrid> =
            buildList {
                    add(preferred)
                    for (radius in 1..4) {
                        add(preferred.translate(-radius, 0))
                        add(preferred.translate(radius, 0))
                        add(preferred.translate(0, -radius))
                        add(preferred.translate(0, radius))
                        add(preferred.translate(-radius, -radius))
                        add(preferred.translate(-radius, radius))
                        add(preferred.translate(radius, -radius))
                        add(preferred.translate(radius, radius))
                    }
                    add(farther)
                    for (radius in 1..4) {
                        add(farther.translate(-radius, 0))
                        add(farther.translate(radius, 0))
                        add(farther.translate(0, -radius))
                        add(farther.translate(0, radius))
                    }
                    // Deterministic perimeter fallback, starting on the player's side.
                    val xRange =
                        if (sideX >= 0) southWest.x..northEast.x else northEast.x downTo southWest.x
                    val zRange =
                        if (sideZ >= 0) southWest.z..northEast.z else northEast.z downTo southWest.z
                    for (x in xRange) {
                        add(
                            CoordGrid(
                                x,
                                if (sideZ >= 0) southWest.z else northEast.z,
                                southWest.level,
                            )
                        )
                    }
                    for (z in zRange) {
                        add(
                            CoordGrid(
                                if (sideX >= 0) southWest.x else northEast.x,
                                z,
                                southWest.level,
                            )
                        )
                    }
                    addAll(
                        buildList {
                                for (x in southWest.x..northEast.x) {
                                    for (z in southWest.z..northEast.z) {
                                        add(CoordGrid(x, z, southWest.level))
                                    }
                                }
                            }
                            .sortedWith(
                                compareBy<CoordGrid>(
                                    { distance(it, preferred) },
                                    { it.x },
                                    { it.z },
                                )
                            )
                    )
                }
                .map { clampArena(run, it) }
                .distinct()

        val open =
            candidates.filter { candidate ->
                isOpenLarvaSpawn(run, candidate) && distance(candidate, player) >= 3
            }
        val preferredSide =
            open
                .filter { DoomLarvaPlacementRules.isBeyondPlayerOnBossSide(boss, player, it) }
                .toSet()
        return DoomLarvaPlacementRules.selectWave(
            count = count,
            orderedCandidates = open,
            preferredCandidates = preferredSide,
            adjacent = ::adjacentLarvaTiles,
        )
    }

    private fun adjacentLarvaTiles(tile: CoordGrid): List<CoordGrid> =
        listOf(
            tile.translate(-1, -1),
            tile.translate(-1, 0),
            tile.translate(-1, 1),
            tile.translate(0, -1),
            tile.translate(0, 1),
            tile.translate(1, -1),
            tile.translate(1, 0),
            tile.translate(1, 1),
        )

    private fun shieldLarvaSpawnTile(run: Run): CoordGrid? {
        val tilesByDirection =
            DoomLarvaPlacementRules.allShieldStreams.associateWith { direction ->
                shieldLarvaStreamTile(run, direction)
            }
        val available = tilesByDirection.filterValues { it != null }.keys
        val direction =
            DoomLarvaPlacementRules.selectShieldStream(run.shieldLarvaStream, available)
                ?: return null
        run.shieldLarvaStream = direction
        return tilesByDirection[direction]
    }

    private fun shieldLarvaStreamTile(run: Run, direction: DoomLarvaSpawnDirection): CoordGrid? {
        val centre = bossCentre(run)
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        val anchor =
            CoordGrid(
                when (direction.deltaX) {
                    -1 -> southWest.x
                    1 -> northEast.x
                    else -> centre.x
                },
                when (direction.deltaZ) {
                    -1 -> southWest.z
                    1 -> northEast.z
                    else -> centre.z
                },
                southWest.level,
            )
        val inwardX = -direction.deltaX
        val inwardZ = -direction.deltaZ
        val perpendicularX = -direction.deltaZ
        val perpendicularZ = direction.deltaX
        for (inward in 0..3) {
            for (lateral in listOf(0, -1, 1, -2, 2)) {
                val candidate =
                    anchor.translate(
                        inwardX * inward + perpendicularX * lateral,
                        inwardZ * inward + perpendicularZ * lateral,
                    )
                if (isOpenLarvaSpawn(run, candidate)) return candidate
            }
        }
        return null
    }

    private fun isOpenLarvaSpawn(run: Run, tile: CoordGrid): Boolean {
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        if (
            tile.level != southWest.level ||
                tile.x !in southWest.x..northEast.x ||
                tile.z !in southWest.z..northEast.z
        ) {
            return false
        }
        if (tile == run.player.coords) return false
        if (DoomGeometry.distanceToFootprint(run.boss.coords, tile, run.boss.size) == 0)
            return false
        if (run.rocks.any { it.loc.coords == tile }) return false
        if (run.fallingRocks.any { it.tile == tile }) return false
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        if (
            !collision.isZoneValid(tile) ||
                collision.isWalkBlocked(tile) ||
                collision[tile.x, tile.z, tile.level] and CollisionFlag.BLOCK_NPCS != 0
        )
            return false
        return run.larvae.keys.none { it.isSlotAssigned && it.coords == tile }
    }

    private fun bossCentre(run: Run): CoordGrid =
        DoomGeometry.centre(run.boss.coords, run.boss.size)

    private fun applyLarvaBossDamage(run: Run, damage: Int) {
        if (damage <= 0 || !run.boss.isSlotAssigned) return
        val remaining = (run.boss.hitpoints - damage).coerceAtLeast(0)
        run.boss.hitpoints = remaining
        if (run.state == DoomState.SHIELD_TRANSITION) run.normalHitpoints = remaining
        if (run.state in setOf(DoomState.ACTIVE, DoomState.MELEE_CHARGE)) {
            armShieldPhaseAfterImpact(run)
        }
        if (DoomBossHud.showsNormalHealth(run.state)) {
            bossHpBar.onUpdate(run.player, run.boss, remaining, run.boss.baseHitpointsLvl)
        } else {
            syncShieldBossHud(run)
        }
        if (remaining == 0) {
            run.boss.queueDeath()
        } else {
            sprayAcidAfterImpact(run)
        }
    }

    private fun beginShockwavePreparation(run: Run) {
        if (!DoomRules.profile(run.delve).shockwaveEnabled) return
        if (run.shockwavePreparing) return
        run.phases.shockwavePreparing = true
        run.preShockwaveAttacksRemaining = DoomRules.preShockwaveAttacks(run.delve)
        run.forceRockBeforeShockwave = true
        val occupied = linkedSetOf<CoordGrid>()
        if (!DoomRules.profile(run.delve).volatileEarthEnabled) return
        var attempts = 0
        while (occupied.size < DoomRules.VOLATILE_EARTH_COUNT && attempts++ < 600) {
            val candidate = randomArenaTile(run, run.player.coords, 9)
            if (isOpenVolatileSpawn(run, candidate, occupied)) occupied += candidate
        }
        if (occupied.size < DoomRules.VOLATILE_EARTH_COUNT) {
            val southWest = local(run.session, run.delve, 22, 28)
            val northEast = local(run.session, run.delve, 40, 46)
            for (x in southWest.x..northEast.x) {
                for (z in southWest.z..northEast.z) {
                    val candidate = CoordGrid(x, z, southWest.level)
                    if (isOpenVolatileSpawn(run, candidate, occupied)) occupied += candidate
                    if (occupied.size == DoomRules.VOLATILE_EARTH_COUNT) break
                }
                if (occupied.size == DoomRules.VOLATILE_EARTH_COUNT) break
            }
        }
        occupied.forEach { spawnVolatile(run, it) }
        run.player.mes(
            "Volatile earth erupts around the arena. Destroy two pieces to form a shield!"
        )
    }

    private fun armNextShockwave(run: Run) {
        if (DoomRules.profile(run.delve).shockwaveEnabled) {
            run.phases.armShockwaveAt(DoomCombatTimeline.firstShockwaveCycle(clock.cycle))
        }
    }

    private fun processShockwave(run: Run) {
        // The native phase graph has already marked this edge ready before input. Packets and
        // pulse snapshots remain after processActiveShockwaves at the original LateCycle point.
        when (run.phases.advanceShockwavePacket() ?: return) {
            DoomShockwavePacket.PREPARE -> {
                run.boss.anim(anim(DoomAssets.ANIM_SHOCKWAVE_PREPARE))
                clearVolatileEarth(run)
            }
            DoomShockwavePacket.CHARGE -> {
                run.boss.anim(anim(DoomAssets.ANIM_SHOCKWAVE_CHARGE))
            }
            DoomShockwavePacket.SLAM -> {
                run.boss.anim(anim(DoomAssets.ANIM_SHOCKWAVE_SLAM))
                val pulseCycle = DoomCombatTimeline.shockwavePulseCycle(clock.cycle)
                run.activeShockwaves +=
                    ActiveShockwave(DoomGeometry.centre(run.boss.coords, run.boss.size), pulseCycle)
            }
            DoomShockwavePacket.RECOVER -> {
                run.boss.anim(anim(DoomAssets.ANIM_SHOCKWAVE_RECOVER))
                armNextShockwave(run)
                run.shield?.let { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
                run.shield = null
                run.shieldDestination = null
                run.firstVolatileTile = null
                run.postShockwaveAttacksRemaining = DoomRules.POST_SHOCKWAVE_ATTACKS
                run.forceRockAfterShockwave = true
                restoreNormal(run)
                return
            }
        }
        run.phases.armShockwavePacket()
    }

    private fun renderShockwave(run: Run): Set<CoordGrid> {
        val floor =
            DoomShockwaveGeometry.candidateTiles(run.boss.coords).toCollection(linkedSetOf())
        floor.forEach { tile ->
            val graphic = random.of(DoomAssets.GFX_SHOCKWAVE_START..DoomAssets.GFX_SHOCKWAVE_END)
            world.spotanimMap(
                SpotanimType(graphic),
                tile,
                delay = DoomShockwaveGeometry.clientDelay(run.boss.coords, tile),
            )
        }
        return floor
    }

    /**
     * Each captured pulse is one server event whose complete floor field uses client-cycle delays.
     * Resolving one tile per 600 ms server tick would make the hazard much slower than its visuals.
     */
    private fun processActiveShockwaves(run: Run) {
        val iterator = run.activeShockwaves.iterator()
        while (iterator.hasNext()) {
            val wave = iterator.next()
            if (clock.cycle < wave.pulseCycle) continue
            val floor = renderShockwave(run)
            if (run.player.coords in floor && !isInsideMovingShield(run)) {
                mechanicHit(
                    run = run,
                    minimum = DoomRules.SHOCKWAVE_MIN_HIT,
                    maximum = DoomRules.SHOCKWAVE_MAX_HIT,
                )
            }
            iterator.remove()
        }
    }

    private fun isOpenVolatileSpawn(run: Run, tile: CoordGrid, occupied: Set<CoordGrid>): Boolean {
        if (tile in occupied || tile == run.player.coords) return false
        if (
            !collision.isZoneValid(tile) ||
                collision.isWalkBlocked(tile) ||
                collision[tile.x, tile.z, tile.level] and CollisionFlag.BLOCK_NPCS != 0
        )
            return false
        if (tileWithinNpcFootprint(tile, run.boss)) return false
        if (run.rocks.any { it.loc.coords == tile }) return false
        if (run.fallingRocks.any { it.tile == tile }) return false
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        if (run.larvae.keys.any { it.isSlotAssigned && it.coords == tile }) return false
        return true
    }

    private fun spawnVolatile(run: Run, tile: CoordGrid) {
        val npc = spawnNpc(run.player, run.session, DoomAssets.VOLATILE_EARTH, tile)
        npc.baseHitpointsLvl = 1
        npc.hitpoints = 1
        npc.movementLocked = true
        npc.hideAllOps()
        npc.anim(anim(DoomAssets.ANIM_VOLATILE_SPAWN))
        run.volatiles += npc
        run.volatileAttackableCycles[npc] = DoomCombatTimeline.volatileAttackableCycle(clock.cycle)
    }

    private fun selectVolatileEarth(run: Run, selected: Npc) {
        if (run.pendingEarthShield != null || run.shield != null) return
        val destination = run.firstVolatileTile
        if (destination == null) {
            run.firstVolatileTile = selected.coords
            run.firstVolatileNpc = selected
            if (selected.isSlotAssigned) selected.anim(anim(DoomAssets.ANIM_VOLATILE_SELECTED))
            return
        }
        if (
            selected === run.firstVolatileNpc ||
                !DoomVolatileEarthRules.isDistinctSelection(destination, selected.coords)
        ) {
            return
        }
        run.volatiles.forEach { volatile ->
            if (
                volatile !== selected &&
                    volatile !== run.firstVolatileNpc &&
                    volatile.isSlotAssigned
            ) {
                volatile.hideAllOps()
                volatile.anim(anim(DoomAssets.ANIM_VOLATILE_UNUSED))
            }
        }
        run.volatileAttackableCycles.clear()
        run.pendingEarthShield =
            PendingEarthShield(
                spawnCentre = selected.coords,
                destinationCentre = destination,
                spawnCycle = DoomCombatTimeline.earthShieldSpawnCycle(clock.cycle),
            )
    }

    private fun processVolatileEarth(run: Run) {
        val activationIterator = run.volatileAttackableCycles.iterator()
        while (activationIterator.hasNext()) {
            val (volatile, attackableCycle) = activationIterator.next()
            if (!volatile.isSlotAssigned || volatile !in run.volatiles) {
                activationIterator.remove()
            } else if (clock.cycle >= attackableCycle) {
                volatile.showAllOps()
                activationIterator.remove()
            }
        }
        val pending = run.pendingEarthShield ?: return
        if (clock.cycle < pending.spawnCycle) return
        run.pendingEarthShield = null
        if (run.shield == null) {
            spawnShield(run, pending.spawnCentre, pending.destinationCentre)
        }
    }

    private fun clearVolatileEarth(run: Run) {
        run.volatiles.toList().forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        run.volatiles.clear()
        run.volatileAttackableCycles.clear()
        run.pendingEarthShield = null
        run.firstVolatileNpc = null
        run.firstVolatileTile = null
    }

    private fun isInsideMovingShield(run: Run): Boolean {
        val shield = run.shield?.takeIf { it.isSlotAssigned } ?: return false
        return DoomShieldGeometry.protects(shield.coords, shield.size, run.player.coords)
    }

    private fun spawnShield(run: Run, spawnCentre: CoordGrid, destinationCentre: CoordGrid) {
        val spawn = DoomVolatileEarthRules.shieldSouthWest(spawnCentre)
        val destination = DoomVolatileEarthRules.shieldSouthWest(destinationCentre)
        val npc = spawnNpc(run.player, run.session, DoomAssets.EARTH_SHIELD, spawn)
        npc.baseHitpointsLvl = 20
        npc.hitpoints = 20
        npc.movementLocked = false
        npc.defaultMoveSpeed = if (run.delve == 1) MoveSpeed.Crawl else MoveSpeed.Walk
        npc.anim(anim(DoomAssets.ANIM_EARTH_SHIELD_SPAWN))
        run.shield = npc
        run.shieldDestination = destination
        run.shieldEndpointCycle = Int.MAX_VALUE
        run.volatiles.forEach { volatile -> if (volatile.isSlotAssigned) volatile.hideAllOps() }
        npc.moveRestrict = MoveRestrict.PassThru
        npc.walk(DoomTileLine.inclusive(spawn, destination).drop(1))
        run.player.mes("The volatile earth hardens into a protective shield.")
    }

    private fun processMovingShield(run: Run) {
        val shield = run.shield ?: return
        if (!shield.isSlotAssigned) {
            run.shield = null
            return
        }
        val clearingTile = DoomGeometry.centre(shield.coords, shield.size)
        val acidIterator = run.acid.iterator()
        while (acidIterator.hasNext()) {
            val acid = acidIterator.next()
            if (acid.tile == clearingTile) {
                locs.del(acid.loc, Int.MAX_VALUE)
                acidIterator.remove()
            }
        }
        val destination = run.shieldDestination ?: return
        if (distance(shield.coords, destination) == 0) {
            if (run.shieldEndpointCycle == Int.MAX_VALUE) {
                run.shieldEndpointCycle = clock.cycle
            }
            if (
                DoomEncounterRuntimeRules.shouldDespawnMovingShield(
                    currentCycle = clock.cycle,
                    endpointCycle = run.shieldEndpointCycle,
                )
            ) {
                npcs.del(shield, Int.MAX_VALUE)
                run.shield = null
                run.shieldDestination = null
                run.shieldEndpointCycle = Int.MAX_VALUE
            }
        } else {
            run.shieldEndpointCycle = Int.MAX_VALUE
        }
    }

    private fun beginBurrow(run: Run) {
        if (DoomPersistenceRules.shouldClearAcidAtCarStart(run.delve)) {
            clearAcid(run)
            run.pendingAcid.clear()
        }
        run.state = DoomState.BURROWING
        run.phases.stopBeamCharge()
        run.phases.armCarPacket()
        run.carPlan = null
        run.carSegmentIndex = 0
        run.pendingCarTrample = null
        run.trampledThisZoom = false
        run.boss.movementLocked = true
        run.boss.anim(anim(DoomAssets.ANIM_BURROW))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW))
    }

    private fun transformBurrow(run: Run) {
        run.boss.transmog(requireNpc(DoomAssets.BURROWED), Int.MAX_VALUE)
        run.boss.movementLocked = true
        resetCharge(run)
        run.phases.advanceCarPhase()
        run.phases.armCarPacket()
    }

    private fun activateBurrow(run: Run) {
        val profile = DoomRules.profile(run.delve)
        if (!spawnCarRocks(run, profile.carRockCount)) {
            abort(run.player, "car rock placement failed", teleport = true, clearRewards = false)
            return
        }
        run.phases.advanceCarPhase()
        run.phases.armCarPacket()
    }

    private fun spawnCarRocks(run: Run, count: Int): Boolean {
        if (count <= 0) return true
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        val staged = mutableListOf<ActiveRock>()
        val candidates =
            buildList {
                    for (x in southWest.x..northEast.x) {
                        for (z in southWest.z..northEast.z) {
                            add(CoordGrid(x, z, southWest.level))
                        }
                    }
                }
                .filterTo(mutableListOf()) { isOpenCarRockSpawn(run, it) }
        var spawned = 0
        while (spawned < count && candidates.isNotEmpty()) {
            val tile = candidates.removeAt(random.of(candidates.indices))
            val loc = spawnLoc(tile, DoomAssets.ROCK, Int.MAX_VALUE) ?: continue
            staged += ActiveRock(loc, Int.MAX_VALUE)
            spawned++
        }
        if (spawned < count) {
            log.warn {
                "[Doom] car-rock capacity generation=${run.generation} delve=${run.delve} " +
                    "requested=$count spawned=$spawned"
            }
            staged.forEach { locs.del(it.loc, Int.MAX_VALUE) }
            return false
        }
        run.rocks += staged
        return true
    }

    private fun telegraphZoom(run: Run) {
        val eyeTile =
            DoomCarEyeSelection.select(
                bossSouthWest = run.boss.coords,
                playerTile = run.player.coords,
                minimum =
                    local(run.session, run.delve, 22, 28)
                        .translate(DoomCarGeometry.BOSS_HALF_SIZE, DoomCarGeometry.BOSS_HALF_SIZE),
                maximum =
                    local(run.session, run.delve, 40, 46)
                        .translate(-DoomCarGeometry.BOSS_HALF_SIZE, -DoomCarGeometry.BOSS_HALF_SIZE),
            )
        val plan =
            DoomCarGeometry.plan(
                startSouthWest = run.boss.coords,
                eyeTile = eyeTile,
                delve = run.delve,
            )
        run.carPlan = plan
        if (plan.segments.isEmpty()) {
            log.error {
                "[Doom] invalid zero-distance car plan generation=${run.generation} " +
                    "delve=${run.delve} boss=${run.boss.coords} eye=$eyeTile"
            }
            abort(run.player, "invalid car path", teleport = true, clearRewards = false)
            return
        }
        val schedule = DoomCarTimeline.schedule(clock.cycle, run.delve, plan.segments.size)
        run.phases.prepareCarZoom(schedule)
        run.carSegmentIndex = 0
        run.pendingCarTrample = null
        run.trampledThisZoom = false
        run.boss.movementLocked = true
        world.spotanimMap(SpotanimType(DoomAssets.GFX_BURROW_TARGET_SPAWN), eyeTile)
        world.spotanimMap(
            SpotanimType(DoomAssets.GFX_BURROW_TARGET),
            eyeTile,
            delay = schedule.persistentEyeClientDelay,
        )
        if (run.phases.carPacket != DoomCarPacket.DASH_WAIT) run.phases.advanceCarPhase()
        run.phases.armCarPacketAt(schedule.dashStartCycle)
    }

    private fun beginCarDash(run: Run) {
        run.phases.advanceCarPhase()
        run.boss.movementLocked = false
        run.boss.anim(anim(DoomAssets.ANIM_BURROW_MOVEMENT))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW_MOVEMENT))
        processCarDashSegment(run)
    }

    private fun processBurrow(run: Run) {
        resolveDueCarTrample(run)
        if (run.phases.carPacket != DoomCarPacket.TRANSFORM_WAIT) updateBurrowCharge(run)
        launchDueCarProjectiles(run)
        if (!run.phases.consumeCarPacketReady()) return
        when (run.phases.carPacket) {
            DoomCarPacket.TRANSFORM_WAIT -> transformBurrow(run)
            DoomCarPacket.ROCK_WAIT -> activateBurrow(run)
            DoomCarPacket.EYE_WAIT -> telegraphZoom(run)
            DoomCarPacket.DASH_WAIT -> beginCarDash(run)
            DoomCarPacket.DASHING -> processCarDashSegment(run)
            DoomCarPacket.PROJECTILES -> scheduleCarSlam(run)
            DoomCarPacket.SLAM_WAIT -> emitCarSlamWave(run)
            DoomCarPacket.RECOVERY -> completeZoom(run)
            null -> Unit
        }
    }

    private fun updateBurrowCharge(run: Run) {
        if (run.beamFireCycle == Int.MAX_VALUE) return
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW_CHARGE))
        run.phases.observeChargeProgress()
        if (run.phases.consumeBeamReady()) finishCharge(run)
    }

    private fun processCarDashSegment(run: Run) {
        val plan =
            run.carPlan
                ?: run {
                    completeZoom(run)
                    return
                }
        if (run.carSegmentIndex >= plan.segments.size) {
            finishCarDash(run)
            return
        }
        val segment = plan.segments[run.carSegmentIndex]
        val isFinalSegment = run.carSegmentIndex == plan.segments.lastIndex
        PathingEntityCommon.exactMove(
            run.boss,
            segment.startSouthWest,
            segment.endSouthWest,
            delay1 = 0,
            delay2 = segment.clientCycles,
            dir = carExactMoveAngle(segment.startSouthWest, segment.endSouthWest),
            collision = collision,
        )
        val impactCycle =
            run.phases.carSchedule?.trampleImpactCycles?.getOrNull(run.carSegmentIndex)
                ?: (clock.cycle + 1)
        run.pendingCarTrample = PendingCarTrample(segment, impactCycle, isFinalSegment)
        run.phases.armCarTrampleAt(impactCycle)
        run.carSegmentIndex++
        if (!isFinalSegment) {
            run.phases.armCarPacketAt(clock.cycle + 1)
            return
        }
        run.boss.movementLocked = true
        finishCarDash(run)
    }

    private fun finishCarDash(run: Run) {
        run.boss.movementLocked = true
        val profile = DoomRules.profile(run.delve)
        if (!profile.carProjectilesEnabled) {
            run.phases.advanceCarPhase()
            run.phases.armCarPacketAt(run.pendingCarTrample?.impactCycle ?: (clock.cycle + 1))
            return
        }
        val slamPacketCycle = run.phases.carSchedule?.slamPacketCycle
        if (slamPacketCycle == null) {
            completeZoom(run)
            return
        }
        run.phases.advanceCarPhase()
        run.phases.armCarPacketAt(slamPacketCycle.coerceAtLeast(clock.cycle))
    }

    private fun launchDueCarProjectiles(run: Run) {
        repeat(run.phases.consumeCarProjectiles()) { launchCarProjectile(run) }
    }

    private fun launchCarProjectile(run: Run) {
        val encounter = bossEncounters.existing(run.boss) ?: return
        val decision =
            encounter.selectScheduledDecision(doomStyleSelector(run.delve), clock.cycle, run.player)
                ?: return
        val style = DoomAttackStyle.valueOf(decision.choice)
        val graphic =
            if (style == DoomAttackStyle.MAGIC) DoomAssets.GFX_MAGIC_PROJECTILE
            else if (style == DoomAttackStyle.RANGED) DoomAssets.GFX_RANGED_PROJECTILE
            else DoomAssets.GFX_MELEE_PROJECTILE
        val delay =
            launchPlayerProjectile(
                run.boss.coords,
                run.player,
                graphic,
                if (style == DoomAttackStyle.MAGIC) "projanim.magic_spell" else "projanim.arrow",
            )
        scheduleStyleHit(
            run,
            PendingStyleHit(
                impactCycle = clock.cycle + delay.coerceAtLeast(1),
                style = style,
                maxHit = DoomRules.burrowMaxHit(run.delve),
                guaranteedAccuracy = DoomCarAttackRules.PROJECTILES_GUARANTEED_ACCURACY,
            ),
        )
    }

    private fun resolveDueCarTrample(run: Run) {
        val pending = run.pendingCarTrample ?: return
        if (!run.phases.consumeCarTrampleReady()) return
        val (segment, impactCycle, finalSegment) = pending
        run.pendingCarTrample = null
        removeRocksCrossedByCar(run, segment)
        if (
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = segment,
                impactCycle = impactCycle,
                currentCycle = clock.cycle,
                playerTile = run.player.coords,
                alreadyTrampled = run.trampledThisZoom,
            )
        ) {
            run.trampledThisZoom = true
            mechanicFixedHit(run, DoomRules.trampleDamage(run.delve))
        }
        if (finalSegment) run.bossHasRepositioned = true
        if (finalSegment && run.player.coords in DoomCarGeometry.footprint(segment.endSouthWest)) {
            shoveFromCar(run)
        }
    }

    private fun scheduleCarSlam(run: Run) {
        val schedule = run.phases.carSchedule ?: return completeZoom(run)
        val clientDelay = schedule.slamClientDelay ?: return completeZoom(run)
        val waveCycle = schedule.waveCycle ?: return completeZoom(run)
        run.boss.anim(anim(DoomAssets.ANIM_BURROW_EXPLOSION), delay = clientDelay)
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW_EXPLOSION), delay = clientDelay)
        run.phases.advanceCarPhase()
        run.phases.armCarPacketAt(waveCycle.coerceAtLeast(clock.cycle))
    }

    private fun emitCarSlamWave(run: Run) {
        val arenaTiles = shockwaveArenaTiles(run)
        val rockTiles =
            run.rocks
                .asSequence()
                .filter { it.endCycle > clock.cycle }
                .map { it.loc.coords }
                .toList()
        val resolution =
            DoomCarGeometry.resolveSlam(
                bossSouthWest = run.boss.coords,
                playerTile = run.player.coords,
                rocks = rockTiles,
                arenaTiles = arenaTiles,
            )
        removeCarRocks(run, resolution.destroyedRocks)
        if (resolution.centreRock == null) {
            renderCarSlamWave(resolution.origin, resolution.exposedTiles)
            if (resolution.damagesPlayer) {
                mechanicHit(
                    run = run,
                    minimum = DoomRules.SHOCKWAVE_MIN_HIT,
                    maximum = DoomRules.SHOCKWAVE_MAX_HIT,
                )
            }
        }
        run.phases.advanceCarPhase()
        run.phases.armCarPacketAt(
            run.phases.carSchedule?.nextEyeCycle?.coerceAtLeast(clock.cycle) ?: (clock.cycle + 1)
        )
    }

    private fun renderCarSlamWave(origin: CoordGrid, exposedTiles: Set<CoordGrid>) {
        exposedTiles.forEach { tile ->
            val graphic = random.of(DoomAssets.GFX_SHOCKWAVE_START..DoomAssets.GFX_SHOCKWAVE_END)
            world.spotanimMap(
                SpotanimType(graphic),
                tile,
                delay = DoomCarGeometry.slamPropagationClientDelay(origin, tile),
            )
        }
    }

    private fun shockwaveArenaTiles(run: Run): List<CoordGrid> {
        val southWest = local(run.session, run.delve, 22, 28).translate(-3, -3)
        val northEast = local(run.session, run.delve, 40, 46).translate(3, 3)
        return buildList {
            for (x in southWest.x..northEast.x) {
                for (z in southWest.z..northEast.z) {
                    add(CoordGrid(x, z, southWest.level))
                }
            }
        }
    }

    private fun completeZoom(run: Run) {
        run.carPlan = null
        run.carSegmentIndex = 0
        run.pendingCarTrample = null
        if (run.phases.finishCarZoom()) {
            telegraphZoom(run)
            return
        }
        clearChargeHeadbar(run)
        run.boss.pendingExactMove = null
        PathingEntityCommon.telejump(run.boss, collision, local(run.session, run.delve, 29, 35))
        run.bossHasRepositioned = false
        run.boss.transmog(requireNpc(DoomAssets.BOSS), Int.MAX_VALUE)
        run.boss.anim(anim(DoomAssets.ANIM_BURROW_EMERGE))
        run.boss.spotanim(spotInternal(DoomAssets.GFX_BURROW_EMERGE))
        run.phases.stopBeamCharge()
        run.boss.movementLocked = true
        val preparationCycle = DoomCombatTimeline.postCarShockwavePreparationCycle(clock.cycle)
        run.phases.armShockwaveAt(preparationCycle)
        run.boss.actionDelay = maxOf(run.boss.actionDelay, preparationCycle)
        keepAggressive(run)
    }

    private fun removeRocksCrossedByCar(run: Run, segment: DoomCarExactSegment) {
        val crossed = DoomCarGeometry.intersectedRocks(segment, run.rocks.map { it.loc.coords })
        removeCarRocks(run, crossed)
    }

    private fun removeCarRocks(run: Run, tiles: Set<CoordGrid>) {
        if (tiles.isEmpty()) return
        val iterator = run.rocks.iterator()
        while (iterator.hasNext()) {
            val rock = iterator.next()
            if (rock.loc.coords !in tiles) continue
            locs.del(rock.loc, Int.MAX_VALUE)
            iterator.remove()
        }
    }

    private fun shoveFromCar(run: Run) {
        val centre = DoomCarGeometry.centre(run.boss.coords)
        var dx = (run.player.coords.x - centre.x).coerceIn(-1, 1)
        var dz = (run.player.coords.z - centre.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) {
            val finalSegment = run.carPlan?.segments?.lastOrNull()
            dx =
                finalSegment?.let { (it.endSouthWest.x - it.startSouthWest.x).coerceIn(-1, 1) } ?: 1
            dz =
                finalSegment?.let { (it.endSouthWest.z - it.startSouthWest.z).coerceIn(-1, 1) } ?: 0
        }
        val directions =
            (listOf(dx to dz) +
                    listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1))
                .distinct()
        val footprint = DoomCarGeometry.footprint(run.boss.coords)
        val destination =
            directions
                .asSequence()
                .map { (x, z) -> clampArena(run, centre.translate(x * 3, z * 3)) }
                .firstOrNull { tile -> tile !in footprint && canShoveTo(run, tile) } ?: return
        run.player.combatClearQueue()
        run.player.clearInteraction()
        PathingEntityCommon.teleport(run.player, collision, destination)
    }

    private fun clampCarEye(run: Run, tile: CoordGrid): CoordGrid {
        val southWest =
            local(run.session, run.delve, 22, 28)
                .translate(DoomCarGeometry.BOSS_HALF_SIZE, DoomCarGeometry.BOSS_HALF_SIZE)
        val northEast =
            local(run.session, run.delve, 40, 46)
                .translate(-DoomCarGeometry.BOSS_HALF_SIZE, -DoomCarGeometry.BOSS_HALF_SIZE)
        return CoordGrid(
            tile.x.coerceIn(southWest.x, northEast.x),
            tile.z.coerceIn(southWest.z, northEast.z),
            southWest.level,
        )
    }

    private fun carExactMoveAngle(start: CoordGrid, end: CoordGrid): Int {
        val dx = end.x - start.x
        val dz = end.z - start.z
        if (dx == 0 && dz == 0) return 0
        return (1024 + (atan2(dx.toDouble(), dz.toDouble()) * CAR_ANGLE_SCALE).roundToInt()) and
            2047
    }

    private fun mechanicHit(run: Run, maximum: Int, delay: Int = 1) =
        mechanicHit(run, 0, maximum, delay)

    private fun mechanicFixedHit(run: Run, damage: Int, delay: Int = 1): Hit {
        require(delay > 0) { "Doom hit delays must be positive. (delay=$delay)" }
        return run.player.queueHit(
            source = run.boss,
            delay = delay,
            type = HitType.Typeless,
            damage = damage.coerceAtLeast(0),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun mechanicHit(run: Run, minimum: Int, maximum: Int, delay: Int = 1) {
        require(delay > 0) { "Doom hit delays must be positive. (delay=$delay)" }
        val low = minimum.coerceAtLeast(0)
        val high = maximum.coerceAtLeast(low)
        run.player.queueHit(
            source = run.boss,
            delay = delay,
            type = HitType.Typeless,
            damage = random.of(low..high),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun nearRock(run: Run, tile: CoordGrid) =
        run.rocks.any { it.endCycle > clock.cycle && distance(it.loc.coords, tile) <= 1 }

    private fun shoveFrom(run: Run, tile: CoordGrid) {
        val bossFootprint = DoomFootprintGeometry.tiles(run.boss.coords, run.boss.size)
        val candidates =
            listOf(
                    tile.translate(-1, -1),
                    tile.translate(0, -1),
                    tile.translate(1, -1),
                    tile.translate(-1, 0),
                    tile.translate(1, 0),
                    tile.translate(-1, 1),
                    tile.translate(0, 1),
                    tile.translate(1, 1),
                )
                .map { clampArena(run, it) }
                .distinct()
                .filter { it != tile && it !in bossFootprint && canShoveTo(run, it) }
        if (candidates.isEmpty()) return
        run.player.clearInteraction()
        PathingEntityCommon.teleport(
            run.player,
            collision,
            candidates[random.of(candidates.indices)],
        )
    }

    private fun canShoveTo(run: Run, destination: CoordGrid): Boolean {
        if (
            destination == run.player.coords ||
                clampArena(run, destination) != destination ||
                destination.level != run.player.coords.level
        ) {
            return false
        }
        var current = run.player.coords
        while (current != destination) {
            val stepX = destination.x.compareTo(current.x)
            val stepZ = destination.z.compareTo(current.z)
            val next = current.translate(stepX, stepZ)
            if (
                !collision.isZoneValid(next) ||
                    collision.isWalkBlocked(next) ||
                    run.rocks.any { it.loc.coords == next } ||
                    run.larvae.keys.any { it.isSlotAssigned && it.coords == next } ||
                    run.volatiles.any { it.isSlotAssigned && tileWithinNpcFootprint(next, it) } ||
                    run.shield?.let { it.isSlotAssigned && tileWithinNpcFootprint(next, it) } ==
                        true ||
                    !stepValidator.canTravel(
                        level = current.level,
                        x = current.x,
                        z = current.z,
                        offsetX = stepX,
                        offsetZ = stepZ,
                        size = 1,
                        extraFlag = CollisionFlag.BLOCK_NPCS,
                    )
            ) {
                return false
            }
            current = next
        }
        return true
    }

    private fun prayerMask(player: Player): Int =
        (player.vars["varbit.prayer_protectfrommelee"] shl 2) or
            (player.vars["varbit.prayer_protectfrommissiles"] shl 1) or
            player.vars["varbit.prayer_protectfrommagic"]

    private fun isProtected(player: Player, style: DoomAttackStyle): Boolean =
        when (style) {
            DoomAttackStyle.MELEE -> player.vars["varbit.prayer_protectfrommelee"] == 1
            DoomAttackStyle.RANGED -> player.vars["varbit.prayer_protectfrommissiles"] == 1
            DoomAttackStyle.MAGIC -> player.vars["varbit.prayer_protectfrommagic"] == 1
        }

    private fun hitType(style: DoomAttackStyle): HitType =
        when (style) {
            DoomAttackStyle.MELEE -> HitType.Melee
            DoomAttackStyle.RANGED -> HitType.Ranged
            DoomAttackStyle.MAGIC -> HitType.Magic
        }

    private fun isDemonbaneAttack(hit: HitBuilder): Boolean {
        if (DoomIncomingAttackRules.isDemonbaneWeapon(hit.righthandType())) return true
        val spell = hit.secondaryType() ?: return false
        return MagicSpellChecks.isDemonbaneSpell(spell)
    }

    private fun larvaSpecialWeapon(hit: HitBuilder): DoomLarvaSpecialWeapon {
        val weapon = hit.righthandType()
        return when {
            weapon?.let { runCatching { it.isType("obj.eye_of_ayak") }.getOrDefault(false) } ==
                true -> DoomLarvaSpecialWeapon.EYE_OF_AYAK
            weapon?.let { runCatching { it.isType("obj.holy_water") }.getOrDefault(false) } ==
                true -> DoomLarvaSpecialWeapon.HOLY_WATER
            else -> DoomLarvaSpecialWeapon.NONE
        }
    }

    private fun isShieldDemonbane(hit: HitBuilder): Boolean = isDemonbaneAttack(hit)

    private fun applyHolyWaterFinisher(run: Run) {
        if (run.holyWaterApplied) return
        run.holyWaterApplied = true
        val landingTiles =
            DoomHolyWaterRules.landingTiles(bossCentre(run)).map { clampArena(run, it) }.distinct()
        landingTiles.forEach { landing -> clearAcidAround(run, landing, 1) }
        /*
         * The supplied Leagues capture does not identify the rev-240 holy-water projectile asset.
         * Keep the live landing geometry and acid clearing deterministic without inventing a fake
         * visual ID; a focused non-Leagues capture can add the exact projectile later.
         */
        run.player.statHeal(
            "stat.hitpoints",
            constant = DoomRules.HOLY_WATER_HITPOINTS,
            percent = 0,
        )
        run.player.statHeal("stat.prayer", constant = DoomRules.HOLY_WATER_PRAYER, percent = 0)
        val currentEnergy = run.player.vars["varp.sa_energy"]
        VarPlayerIntMapSetter.set(
            run.player,
            "varp.sa_energy",
            (currentEnergy + DoomRules.HOLY_WATER_SPECIAL_ENERGY).coerceAtMost(1000),
        )
        run.player.mes(
            "The holy water restores ${DoomRules.HOLY_WATER_HITPOINTS} Hitpoints, " +
                "${DoomRules.HOLY_WATER_PRAYER} Prayer and 25% special energy."
        )
    }

    private fun clearAcidAt(run: Run, tile: CoordGrid) {
        val iterator = run.acid.iterator()
        while (iterator.hasNext()) {
            val acid = iterator.next()
            if (acid.tile == tile) {
                locs.del(acid.loc, Int.MAX_VALUE)
                iterator.remove()
            }
        }
        run.pendingAcid.removeAll { it.tile == tile }
    }

    private fun clearAcidAround(run: Run, tile: CoordGrid, radius: Int) {
        val iterator = run.acid.iterator()
        while (iterator.hasNext()) {
            val acid = iterator.next()
            if (distance(acid.tile, tile) > radius) continue
            locs.del(acid.loc, Int.MAX_VALUE)
            iterator.remove()
        }
    }

    private fun clearAcid(run: Run) {
        run.acid.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.acid.clear()
    }

    private fun spawnNpc(player: Player, session: InstanceSession, id: Int, tile: CoordGrid): Npc {
        val npc = Npc(requireNpc(id), tile)
        // All Doom actors are controlled by the encounter state machine. The boss explicitly
        // switches to ApPlayer2 when activated; auxiliaries must never inherit cache Wander.
        npc.mode = NpcMode.None
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, DOOM_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun spawnLoc(
        tile: CoordGrid,
        id: Int,
        duration: Int,
        angle: LocAngle = LocAngle.North,
    ): LocInfo? {
        val type = ServerCacheManager.getObject(id) ?: return null
        val shape = LocShape.CentrepieceStraight
        val loc =
            LocInfo(LocLayerConstants.of(shape.id), tile, LocEntity(type.id, shape.id, angle.id))
        return loc.takeIf { locs.add(it, duration) }
    }

    private fun randomArenaTile(run: Run, around: CoordGrid, radius: Int): CoordGrid =
        clampArena(run, around.translate(random.of(-radius..radius), random.of(-radius..radius)))

    private fun isOpenRewardHole(run: Run, tile: CoordGrid): Boolean {
        if (tile == run.player.coords || clampArena(run, tile) != tile) return false
        if (!collision.isZoneValid(tile) || collision.isWalkBlocked(tile)) return false
        if (run.acid.any { it.tile == tile }) return false
        if (run.volatiles.any { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) }) {
            return false
        }
        if (run.shield?.let { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) } == true) {
            return false
        }
        return true
    }

    private fun isOpenCarRockSpawn(run: Run, tile: CoordGrid): Boolean {
        if (tile == run.player.coords || clampArena(run, tile) != tile) return false
        if (!collision.isZoneValid(tile) || collision.isWalkBlocked(tile)) return false
        if (tile in DoomCarGeometry.footprint(run.boss.coords)) return false
        if (run.rocks.any { it.loc.coords == tile }) return false
        if (
            run.fallingRocks.any {
                it.tile == tile || it.rubbleTile == tile || tile in it.shadowTiles
            }
        ) {
            return false
        }
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        if (run.larvae.keys.any { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) })
            return false
        if (run.acid.any { it.tile == tile } || run.pendingAcid.any { it.tile == tile })
            return false
        if (run.volatiles.any { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) })
            return false
        if (run.shield?.let { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) } == true)
            return false
        return true
    }

    private fun isOpenRockShadow(run: Run, tile: CoordGrid): Boolean {
        if (clampArena(run, tile) != tile) return false
        if (!collision.isZoneValid(tile) || collision.isWalkBlocked(tile)) return false
        if (tileWithinNpcFootprint(tile, run.boss)) return false
        if (run.rocks.any { it.loc.coords == tile }) return false
        if (run.larvae.keys.any { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) })
            return false
        if (run.volatiles.any { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) })
            return false
        if (run.shield?.let { it.isSlotAssigned && tileWithinNpcFootprint(tile, it) } == true)
            return false
        return true
    }

    private fun rockShadowCandidates(run: Run, around: CoordGrid): List<CoordGrid> {
        val southWest = local(run.session, run.delve, 22, 28)
        val northEast = local(run.session, run.delve, 40, 46)
        val minX = (around.x - 4).coerceAtLeast(southWest.x)
        val maxX = (around.x + 4).coerceAtMost(northEast.x)
        val minZ = (around.z - 4).coerceAtLeast(southWest.z)
        val maxZ = (around.z + 4).coerceAtMost(northEast.z)
        return buildList {
                repeat(64) { add(randomArenaTile(run, around, 4)) }
                for (x in minX..maxX) {
                    for (z in minZ..maxZ) {
                        add(CoordGrid(x, z, southWest.level))
                    }
                }
                for (x in southWest.x..northEast.x) {
                    for (z in southWest.z..northEast.z) {
                        add(CoordGrid(x, z, southWest.level))
                    }
                }
            }
            .asSequence()
            .distinct()
            .filter { isOpenRockShadow(run, it) }
            .toList()
    }

    private fun clampArena(run: Run, tile: CoordGrid): CoordGrid {
        val sw = local(run.session, run.delve, 22, 28)
        val ne = local(run.session, run.delve, 40, 46)
        return CoordGrid(tile.x.coerceIn(sw.x, ne.x), tile.z.coerceIn(sw.z, ne.z), sw.level)
    }

    private fun local(session: InstanceSession, delve: Int, x: Int, z: Int): CoordGrid {
        val arena = arenaForDelve(delve)
        return requireNotNull(
            instances.localCoord(session, RegionLocal(0, arena.regionX, arena.regionZ, x, z))
        )
    }

    private fun endInstance(run: Run, teleport: Boolean) {
        val exit = instances.leave(run.player, run.session, clock.cycle)
        if (teleport) PathingEntityCommon.telejump(run.player, collision, exit)
    }

    private fun retire(run: Run) {
        if (run.retired) return
        run.retired = true
        run.state = DoomState.INVALIDATED
        run.launchedPrimary = null
        cleanupHazards(run)
        prepare(run.player)
        run.player.lootDropDuration = null
        SpecialAttackEnergyRegeneration.restart(run.player)
    }

    private fun cleanupHazards(
        run: Run,
        keepBoss: Boolean = false,
        keepAcid: Boolean = false,
        keepShield: Boolean = false,
        keepVolatiles: Boolean = false,
    ) {
        bossHpBar.onClose(run.player, run.boss, instant = true)
        if (!keepAcid) clearAcid(run)
        run.rocks.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.rocks.clear()
        run.fallingRocks.clear()
        run.attacks.clear()
        run.pendingAcid.clear()
        run.carPlan = null
        run.phases.retireCarZoom()
        run.carSegmentIndex = 0
        run.pendingCarTrample = null
        run.boss.pendingExactMove = null
        run.rockMagic = null
        run.phases.cancelMeleeCharge()
        run.meleeChargeAttackClaimed = false
        run.phases.stopBeamCharge()
        run.activeShockwaves.clear()
        cancelPendingBeam(run)
        clearChargeHeadbar(run)
        run.hole?.let { locs.del(it, Int.MAX_VALUE) }
        run.hole = null
        run.larvae.keys.toList().forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        run.larvae.clear()
        run.shieldLarvaStream = null
        if (!keepVolatiles) {
            clearVolatileEarth(run)
        }
        if (!keepShield) {
            run.shield?.let { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
            run.shield = null
            run.shieldDestination = null
            run.shieldEndpointCycle = Int.MAX_VALUE
        }
        if (!keepBoss && run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Doom NPC $id" }

    private fun anim(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing Doom sequence $id" }

    private fun spotInternal(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing Doom spotanim $id"
        }

    private fun mapSpot(id: Int) =
        SpotanimType(RSCM.run { spotInternal(id).asRSCM(RSCMType.SPOTANIM) })

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(
            ServerCacheManager.getProjectile(
                dev.openrune.rscm.RSCM.run { internal.asRSCM(RSCMType.PROJANIM) }
            )
        ) {
            "Missing projectile controller $internal"
        }

    private fun distance(a: CoordGrid, b: CoordGrid) = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun tileWithinNpcFootprint(tile: CoordGrid, npc: Npc): Boolean =
        DoomGeometry.contains(npc.coords, tile, npc.size)

    private fun larvaExplosionTiles(origin: CoordGrid, giant: Boolean): Set<CoordGrid> {
        val offsets = if (giant) -1..2 else -1..1
        return buildSet {
            offsets.forEach { x -> offsets.forEach { z -> add(origin.translate(x, z)) } }
        }
    }

    private fun renderLarvaExplosion(tiles: Set<CoordGrid>) {
        tiles.forEach { world.spotanimMap(SpotanimType(DoomAssets.GFX_BURROW_EXPLOSION_AOE), it) }
    }

    private fun inBurrowHazardGrace(run: Run): Boolean =
        run.state == DoomState.BURROWING && run.phases.inCarHazardGrace

    private fun inTransitionHazardGrace(run: Run): Boolean =
        run.state == DoomState.SHIELD_TRANSITION || inBurrowHazardGrace(run)

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private companion object {
        private const val DOOM_COMBAT_DEBUG = false

        // RSProx MapAnim 3386/3387 uses height 244 when the boulder breaks into debris.
        private const val ROCK_BREAK_SPOTANIM_HEIGHT = 244

        private const val LARVA_REPATH_TICKS = 4
        private const val ENTRY_PROMPT_TIMEOUT_TICKS = 8
        private const val LARVA_LETHAL_CLAIM_TICKS = 10
        private const val ROCK_SPAWN_RETRY_LOG_INTERVAL = 5
        private const val ROCK_SPAWN_MAX_ATTEMPTS = 3
        private const val CAR_ANGLE_SCALE = 325.94932345220167
    }

    internal data class ActiveAcid(val tile: CoordGrid, val endCycle: Int, val loc: LocInfo)

    internal data class PendingCarTrample(
        val segment: DoomCarExactSegment,
        val impactCycle: Int,
        val finalSegment: Boolean,
    )

    internal data class PendingAcid(
        val tile: CoordGrid,
        val locId: Int,
        val impactCycle: Int,
        val graphic: Int,
    )

    internal data class ArenaOffset(val x: Int, val z: Int)

    internal data class Descent(
        val nextDelve: Int,
        val carriedAcid: List<ArenaOffset>,
        val demonicCharge: Int,
        val debug: Boolean,
        val runId: String,
        val chainStartCycle: Int,
    )

    internal data class ActiveRock(val loc: LocInfo, val endCycle: Int)

    internal data class FallingRock(
        var tile: CoordGrid,
        var rubbleTile: CoordGrid? = null,
        val magic: Boolean,
        val landCycle: Int,
        val followupStyles: List<DoomAttackStyle>,
        val volleyIndex: Int,
        var landed: Boolean = false,
        var followupStage: Int = 0,
        var shadowImpactCycle: Int = Int.MAX_VALUE,
        var permanentRockCycle: Int = Int.MAX_VALUE,
        var shadowsLanded: Boolean = false,
        var rockSpawned: Boolean = false,
        var rockSpawnAttempts: Int = 0,
        var rockAngle: LocAngle? = null,
        var protectedAtImpact: Boolean = false,
        val shadowTiles: MutableSet<CoordGrid> = linkedSetOf(),
    )

    internal data class Larva(
        val style: DoomAttackStyle?,
        val giant: Boolean,
        val protectedStyles: Set<DoomAttackStyle>,
        val shieldPhase: Boolean,
        val spawnCycle: Int,
        val movementCycle: Int,
        val activeCycle: Int,
        var crawlStarted: Boolean = false,
        var opsVisible: Boolean = false,
        var targetTile: CoordGrid? = null,
        var lastProgressTile: CoordGrid,
        var lastProgressCycle: Int,
        var lastRouteCycle: Int,
        var lethalClaimExpiryCycle: Int = Int.MAX_VALUE,
    )

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        val generation: Long,
        val delve: Int,
        var boss: Npc,
        var startCycle: Int,
        var bossRevealCycle: Int,
        var entryPromptFallbackCycle: Int = Int.MAX_VALUE,
        val startTile: CoordGrid,
        val startingPrayerMask: Int,
        val debug: Boolean = false,
        val runId: String,
        val chainStartCycle: Int,
        val playerUid: PlayerUid = player.uid,
        var retired: Boolean = false,
        var deathRecorded: Boolean = false,
        var launchedPrimary: DoomPrimaryAttack? = null,
        val activeShockwaves: MutableList<ActiveShockwave> = mutableListOf(),
        var meleeChargeAttackClaimed: Boolean = false,
        var shieldTriggered: Boolean = false,
        var shieldAttacksRemaining: Int = -1,
        var pendingBeamImpactCycle: Int = Int.MAX_VALUE,
        var pendingBeamMaxHit: Int = 0,
        var pendingBeamState: DoomState? = null,
        var pendingBeamHit: Hit? = null,
        var shieldHitpoints: Int = 0,
        var shieldEpoch: Int = 0,
        var normalHitpoints: Int = -1,
        var shieldLarvaStream: DoomLarvaSpawnDirection? = null,
        var meleePunishUntilCycle: Int = Int.MIN_VALUE,
        var holyWaterApplied: Boolean = false,
        var demonicCharge: Int = 0,
        var rockMagic: Boolean? = null,
        var firstVolatileTile: CoordGrid? = null,
        var firstVolatileNpc: Npc? = null,
        var pendingEarthShield: PendingEarthShield? = null,
        var shield: Npc? = null,
        var shieldDestination: CoordGrid? = null,
        var shieldEndpointCycle: Int = Int.MAX_VALUE,
        var preShockwaveAttacksRemaining: Int = 0,
        var forceRockBeforeShockwave: Boolean = false,
        var postShockwaveAttacksRemaining: Int = 0,
        var forceRockAfterShockwave: Boolean = false,
        var carPlan: DoomCarPlan? = null,
        var carSegmentIndex: Int = 0,
        var pendingCarTrample: PendingCarTrample? = null,
        var trampledThisZoom: Boolean = false,
        var bossHasRepositioned: Boolean = false,
        var lastAcidHitCycle: Int = Int.MIN_VALUE,
        var hole: LocInfo? = null,
        val acid: MutableList<ActiveAcid> = mutableListOf(),
        val pendingAcid: MutableList<PendingAcid> = mutableListOf(),
        val rocks: MutableList<ActiveRock> = mutableListOf(),
        val fallingRocks: MutableList<FallingRock> = mutableListOf(),
        val larvae: MutableMap<Npc, Larva> = linkedMapOf(),
        val volatiles: MutableSet<Npc> = linkedSetOf(),
        val volatileAttackableCycles: MutableMap<Npc, Int> = linkedMapOf(),
    ) {
        lateinit var phases: DoomCombatPhases
        lateinit var attacks: DoomAttackQueues
        private var outcome: DoomSessionOutcome? = DoomSessionOutcome.STARTING

        val rockVolleysRemaining: Int
            get() = attacks.rockVolleysRemaining

        val nextShieldLarvaCycle: Int
            get() = attacks.nextShieldLarvaCycle

        val pendingHits: List<PendingStyleHit>
            get() = attacks.pendingHits

        val pendingMeleeChargeCycle: Int
            get() = phases.pendingMeleeChargeCycle

        val beamFireCycle: Int
            get() = phases.beamFireCycle

        val chargeProgress: Int
            get() = phases.chargeProgress

        val shockwavePreparing: Boolean
            get() = phases.shockwavePreparing

        var state: DoomState
            get() = outcome?.state ?: phases.state
            set(value) {
                val sessionOutcome = DoomSessionOutcome.entries.firstOrNull { it.state == value }
                if (sessionOutcome != null) {
                    phases.retireReadiness()
                    outcome = sessionOutcome
                } else {
                    check(outcome == null || outcome == DoomSessionOutcome.STARTING) {
                        "A retired Doom session cannot resume combat."
                    }
                    phases.state = value
                    outcome = null
                }
            }
    }

    internal data class PendingEarthShield(
        val spawnCentre: CoordGrid,
        val destinationCentre: CoordGrid,
        val spawnCycle: Int,
    )

    internal data class ActiveShockwave(val origin: CoordGrid, val pulseCycle: Int)

    internal data class PendingStyleHit(
        val impactCycle: Int,
        val style: DoomAttackStyle,
        val maxHit: Int,
        val guaranteedAccuracy: Boolean = false,
    )
}

private fun <T> List<T>.shuffledByGameRandom(random: GameRandom): List<T> {
    val shuffled = toMutableList()
    for (index in shuffled.lastIndex downTo 1) {
        val swapIndex = random.of(0..index)
        if (swapIndex != index) {
            val value = shuffled[index]
            shuffled[index] = shuffled[swapIndex]
            shuffled[swapIndex] = value
        }
    }
    return shuffled
}

internal enum class DoomBossDeathQueueAction {
    ENCOUNTER_DEATH,
    FINISH_SHIELD,
    IGNORE,
}

internal object DoomEncounterRuntimeRules {
    fun bossDeathQueueAction(state: DoomState, hitpoints: Int): DoomBossDeathQueueAction =
        when {
            state == DoomState.SHIELDED && hitpoints <= 0 -> DoomBossDeathQueueAction.FINISH_SHIELD
            hitpoints > 0 -> DoomBossDeathQueueAction.IGNORE
            else -> DoomBossDeathQueueAction.ENCOUNTER_DEATH
        }

    fun delayedStyleDamageRange(
        maxHit: Int,
        guaranteedAccuracy: Boolean,
        accurate: Boolean,
    ): IntRange? {
        if (!guaranteedAccuracy && !accurate) return null
        return DoomAccuracyRules.damageRange(maxHit, guaranteedAccuracy)
    }

    fun acidPhaseAllowed(state: DoomState): Boolean =
        state == DoomState.ACTIVE || state == DoomState.MELEE_CHARGE

    fun shouldFinishShieldPhaseAfterBeam(
        firingState: DoomState,
        currentState: DoomState,
        bossAssigned: Boolean,
    ): Boolean =
        bossAssigned && firingState == DoomState.SHIELDED && currentState == DoomState.SHIELDED

    fun acidHitAllowed(state: DoomState, currentCycle: Int, lastHitCycle: Int): Boolean {
        if (!acidPhaseAllowed(state)) return false
        return lastHitCycle == Int.MIN_VALUE || currentCycle > lastHitCycle
    }

    fun shouldRestartBurrowCharge(
        firingState: DoomState?,
        currentState: DoomState,
        bossAssigned: Boolean,
    ): Boolean =
        bossAssigned && firingState == DoomState.BURROWING && currentState == DoomState.BURROWING

    fun shouldResetBurrowChargeOnHit(
        currentState: DoomState,
        bossAssigned: Boolean,
        beamPending: Boolean,
        bossAlive: Boolean,
    ): Boolean = bossAssigned && bossAlive && !beamPending && currentState == DoomState.BURROWING

    fun shouldDespawnMovingShield(currentCycle: Int, endpointCycle: Int): Boolean =
        endpointCycle != Int.MAX_VALUE && currentCycle > endpointCycle + 1

    fun mayAdvanceLarva(hitpoints: Int): Boolean = hitpoints > 0
}

internal object DoomLarvaRouteRules {
    fun hasReachedBoss(distanceToBoss: Int): Boolean = distanceToBoss == 0

    fun accept(success: Boolean, waypointCount: Int, finalDistanceToBoss: Int): Boolean =
        success && waypointCount > 0 && hasReachedBoss(finalDistanceToBoss)
}
