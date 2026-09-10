package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPhaseClock
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class CerberusEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val routeFactory: RouteFactory,
    private val arena: CerberusArena,
    private val deps: BossDeps,
    private val phaseClock: BossPhaseClock,
    private val effects: CerberusArenaEffects,
) {
    private fun run(player: Player): CerberusRun? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == CERBERUS_INSTANCE_KEY }
            ?.stateOrNull(CERBERUS_RUN_STATE)

    private fun run(npc: Npc): CerberusRun? =
        instances
            .instanceForNpc(npc)
            ?.let(instances::sessionForId)
            ?.takeIf { it.key == CERBERUS_INSTANCE_KEY }
            ?.stateOrNull(CERBERUS_RUN_STATE)

    fun onJoin(player: Player, session: InstanceSession) {
        session.state(CERBERUS_RUN_STATE) {
            val boss =
                instances.npcsForInstance(session.id).single {
                    it.type.id == CerberusAssets.SITTING_NPC_ID
                }
            configureBoss(player, boss, true)
            boss.anim(CerberusAssets.SITTING_ANIMATION)
            val run = CerberusRun(player, session, boss, deps.encounterRegistry.of(boss), clock)
            run.encounter.onCleanup { effects.clearHazards(run) }
            phaseClock.bind(boss, player)
            session.onCleanup { retireRun(run) }
            player.mes("You enter Cerberus's lair. Approach the chamber to wake her.")
            run
        }
    }

    fun onLeave(player: Player, instanceId: InstanceId) {
        val run = instances.sessionForId(instanceId)?.stateOrNull(CERBERUS_RUN_STATE) ?: return
        if (run.player === player) retireRun(run)
    }

    fun isActiveTarget(npc: Npc, target: Player): Boolean =
        run(target)?.let {
            it.boss === npc &&
                npc.isSpawnOwnedBy(target) &&
                it.phase == CerberusPhase.ACTIVE &&
                it.isCurrent(instances, arena) &&
                inChamber(it)
        } == true

    fun canAttack(npc: Npc, target: Player): Boolean {
        if (!isActiveTarget(npc, target)) return false
        val run = requireNotNull(run(target))
        val encounter = run.encounter
        if (
            npc.actionDelay > clock.cycle ||
                clock.cycle - encounter.lastAbilityTick < encounter.expectedAttackRate()
        )
            return true

        // Admin-only diagnostics use native forced abilities. Production has no private selector,
        // attack count, skip RNG or scheduler. An offset preserves arbitrary diagnostic counters
        // without mutating the shared encounter's actual commit count.
        val forced = run.forcedAttack
        val skip = run.forcedSkip
        if (forced == null && skip == null && run.diagnosticCounterOffset == 0) return true
        val candidate = CerberusRules.specialCandidate(run.attackNumber, npc.hitpoints)
        val selected =
            when {
                forced != null ->
                    when (forced) {
                        CerberusAttack.BASIC ->
                            run.forcedNormalStyle
                                ?.takeIf {
                                    it != CerberusAttackStyle.MELEE ||
                                        CerberusRules.distanceToFootprint(
                                            npc.coords.x,
                                            npc.coords.z,
                                            npc.visType.size,
                                            target.coords.x,
                                            target.coords.z,
                                        ) == 1
                                }
                                ?.name
                                ?.lowercase() ?: "basic"
                        else -> forced.name.lowercase()
                    }
                skip != null && candidate in listOf(CerberusAttack.SOULS, CerberusAttack.LAVA) ->
                    if (skip) "basic" else requireNotNull(candidate).name.lowercase()
                run.diagnosticCounterOffset != 0 ->
                    when (candidate) {
                        CerberusAttack.SOULS -> "souls-candidate"
                        CerberusAttack.LAVA -> "lava-candidate"
                        CerberusAttack.TRIPLE -> "triple"
                        else -> "basic"
                    }
                else -> null
            }
        selected?.let(encounter::forceNextAbility)
        run.forcedAttack = null
        run.forcedNormalStyle = null
        run.forcedSkip = null
        return true
    }

    fun attackPresentation(npc: Npc, presentation: Any?) {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return
        if (presentation == CerberusAttack.TRIPLE) {
            run.playAttackAnimation(
                CerberusAssets.TRIPLE_SPRAY_ANIMATION,
                CerberusRules.TRIPLE_ANIMATION_LOCK_TICKS,
            )
        } else if (presentation is CerberusAttackStyle) {
            run.playAttackAnimation(
                animationFor(presentation),
                CerberusRules.BASIC_ANIMATION_LOCK_TICKS,
            )
        }
    }

    fun recordMeleeImpact(npc: Npc) {
        run(npc)?.tookMeleeDamage = true
    }

    fun souls(npc: Npc) {
        run(npc)?.takeIf { it.isCurrent(instances, arena) }?.let(effects::launchSouls)
    }

    fun lava(npc: Npc) {
        run(npc)?.takeIf { it.isCurrent(instances, arena) }?.let(effects::launchLava)
    }

    fun tickAll() {
        instances
            .sessionsForKey(CERBERUS_INSTANCE_KEY)
            .mapNotNull { it.stateOrNull(CERBERUS_RUN_STATE) }
            .forEach { run ->
                if (run.pendingRespawn) {
                    run.pendingRespawn = false
                    respawn(run)
                }
                // Native instances own membership/retirement and BossPhaseClock owns deadlines.
                // LateCycle preserves coordinate snapshots and ready-phase presentation timing.
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
                        else if (run.encounter.currentPhaseName == "standing-ready") activate(run)
                    }
                    CerberusPhase.ACTIVE -> {
                        if (hasLeftActiveArena(localX, localZ)) {
                            beginReset(run)
                        } else {
                            effects.processLava(run)
                            keepAggressive(run)
                        }
                    }
                    CerberusPhase.RESETTING -> processReset(run)
                    CerberusPhase.RESPAWNING -> Unit
                    CerberusPhase.DYING,
                    CerberusPhase.COMPLETE,
                    CerberusPhase.INVALIDATED -> Unit
                }
                // Outward crossing can reset the fight above. Bind its hit to that new encounter.
                if (crossedFireWall) {
                    effects.queueFixedDamage(run, CerberusRules.FIRE_WALL_DAMAGE)
                }
                run.lastPlayerCoord = run.player.coords
            }
    }

    private fun inChamber(run: CerberusRun): Boolean {
        val source = arena.sourceCoords(run.session, run.player.coords) ?: return false
        return CerberusRules.inChamber(source.x and 63, source.z and 63)
    }

    private fun hasLeftActiveArena(localX: Int, localZ: Int): Boolean =
        localX !in CERBERUS_CHAMBER_MIN_X..CERBERUS_CHAMBER_MAX_X ||
            localZ <= CERBERUS_FIRE_WALL_Z ||
            localZ > CERBERUS_CHAMBER_MAX_Z

    private fun beginStanding(run: CerberusRun) {
        renewEncounter(run)
        run.phase = CerberusPhase.STANDING
        run.boss.facePlayer(run.player)
        run.boss.anim(CerberusAssets.STAND_ANIMATION)
        run.boss.movementLocked = true
    }

    private fun activate(run: CerberusRun) {
        run.boss.transmog(requireNpc(CerberusAssets.ACTIVE_NPC_ID), Int.MAX_VALUE)
        run.boss.hitpoints = CerberusRules.HITPOINTS
        run.boss.movementLocked = false
        run.boss.moveRestrict = MoveRestrict.PassThru
        run.boss.showAllOps()
        effects.spawnFireWall(run)
        run.phase = CerberusPhase.ACTIVE
        run.diagnosticCounterOffset = 0
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun beginReset(run: CerberusRun) {
        renewEncounter(run)
        run.phase = CerberusPhase.RESETTING
        run.resetArrived = false
        run.diagnosticCounterOffset = 0
        run.forcedAttack = null
        run.forcedNormalStyle = null
        run.forcedSkip = null
        run.attackAnimationUntilCycle = 0
        run.player.combatClearQueue()
        effects.clearHazards(run)
        if (!run.boss.isSlotAssigned) return
        run.boss.clearInteraction()
        run.boss.routeDestination.clear()
        run.boss.transmog(requireNpc(CerberusAssets.RESETTING_NPC_ID), Int.MAX_VALUE)
        run.boss.movementLocked = false
        run.boss.hideAllOps()
        val home = arena.localCoord(run.session, CERBERUS_BOSS_LOCAL_X, CERBERUS_BOSS_LOCAL_Z)
        if (run.boss.coords == home) {
            run.resetArrived = true
        } else {
            val encounter = run.encounter
            run.boss.anim(CerberusAssets.WALK_ANIMATION)
            run.boss.walkTo(routeFactory, home, passThroughEntities = true) {
                if (
                    run(run.player) === run &&
                        run.encounter === encounter &&
                        !encounter.isEnded &&
                        run.phase == CerberusPhase.RESETTING
                ) {
                    run.resetArrived = true
                }
            }
        }
    }

    private fun renewEncounter(run: CerberusRun) {
        deps.encounterRegistry.remove(run.boss)
        run.encounter = deps.encounterRegistry.of(run.boss)
        run.encounter.onCleanup { effects.clearHazards(run) }
        phaseClock.bind(run.boss, run.player)
    }

    private fun processReset(run: CerberusRun) {
        if (!run.boss.isSlotAssigned) return
        when (run.encounter.currentPhaseName) {
            "reset-ready" -> finishReset(run)
            "reset-travel",
            "reset-timeout" -> {
                // Retain the characterized draft fallback; OSRS path parity remains unverified.
                if (!run.resetArrived && run.encounter.currentPhaseName == "reset-timeout") {
                    run.boss.teleport(
                        collision,
                        arena.localCoord(run.session, CERBERUS_BOSS_LOCAL_X, CERBERUS_BOSS_LOCAL_Z),
                    )
                    run.resetArrived = true
                }
                if (!run.resetArrived) return
                run.encounter.transitionTo("reset-sit", clock.cycle)
                run.boss.anim(
                    CerberusAssets.SIT_ANIMATION,
                    priority = CerberusRules.ATTACK_ANIMATION_PRIORITY,
                )
            }
        }
    }

    private fun finishReset(run: CerberusRun) {
        if (!run.boss.isSlotAssigned) return
        effects.clearFireWall(run)
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

    private fun respawn(run: CerberusRun) {
        run.encounter = deps.encounterRegistry.of(run.boss)
        run.encounter.onCleanup { effects.clearHazards(run) }
        phaseClock.bind(run.boss, run.player)
        run.rewardEligible = true
        run.forcedAttack = null
        run.forcedNormalStyle = null
        run.forcedSkip = null
        run.attackAnimationUntilCycle = 0
        configureBoss(run.player, run.boss, run.rewardEligible)
        run.boss.anim(CerberusAssets.SITTING_ANIMATION)
        run.phase = CerberusPhase.SITTING
        run.diagnosticCounterOffset = 0
        run.soulWaveSpawned = false
        run.negatedSoulAttacks = 0
        run.tookLavaDamage = false
        run.tookMeleeDamage = false
    }

    fun beginBossDeath(npc: Npc): Boolean {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return false
        if (run.phase != CerberusPhase.ACTIVE) return false
        deps.encounterRegistry.remove(npc)
        run.phase = CerberusPhase.DYING
        run.player.combatClearQueue()
        effects.clearHazards(run)
        effects.clearFireWall(run)
        npc.clearInteraction()
        return true
    }

    fun finishBossDeath(npc: Npc) {
        val run = run(npc)?.takeIf { it.boss === npc } ?: return
        if (run.phase != CerberusPhase.DYING) return
        awardCombatAchievements(run)
        run.phase = CerberusPhase.RESPAWNING
    }

    private fun awardCombatAchievements(run: CerberusRun) {
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
                "counter=${run.attackNumber} phaseStarted=${run.encounter.phaseEnteredTick} " +
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
                    run.diagnosticCounterOffset = value - 1 - run.encounter.phaseAbilityCount
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

    private fun taintDebugRun(run: CerberusRun) {
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

    private fun keepAggressive(run: CerberusRun) {
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

    private fun retireRun(run: CerberusRun) {
        if (run.phase == CerberusPhase.INVALIDATED) return
        deps.encounterRegistry.remove(run.boss)
        run.phase = CerberusPhase.INVALIDATED
        run.player.combatClearQueue()
        effects.clearHazards(run)
        effects.clearFireWall(run)
        run.boss.clearInteraction()
        // Native instance teardown owns the boss; do not race its immediate/deferred reclaim.
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Cerberus NPC definition: $id" }

    private fun animationFor(style: CerberusAttackStyle): Int =
        when (style) {
            CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_ANIMATION
            CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_ANIMATION
            CerberusAttackStyle.MELEE -> CerberusAssets.MELEE_ANIMATION
        }

    private companion object {
        const val CA_KILLCOUNT_75 = "varbit.ca_task_cerberus_killcount_1_completed"
        const val CA_KILLCOUNT_150 = "varbit.ca_task_cerberus_killcount_2_completed"
        const val CA_GHOST_BUSTER = "varbit.ca_task_cerberus_mechanical_1_completed"
        const val CA_NO_SOULS = "varbit.ca_task_cerberus_mechanical_2_completed"
        const val CA_NO_LAVA_DAMAGE = "varbit.ca_task_cerberus_perfection_1_completed"
        const val CA_NO_MELEE_DAMAGE = "varbit.ca_task_cerberus_perfection_2_completed"
        const val CA_TOTAL = "varbit.ca_total_tasks_completed_cerberus"
    }
}
