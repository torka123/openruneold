package org.rsmod.content.bosses.royaltitans

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.sql.SQLException
import java.util.UUID
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.anim
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireLoc
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireNpc
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.spot
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.Participant
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitScope
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class RoyalTitansEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val objects: ObjRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val players: PlayerList,
    private val drops: DropTableRegistry,
    private val bossHpBar: BossHpBarScript,
    private val statistics: RoyalTitansStatistics,
    private val phases: RoyalTitansPhaseProgram,
    private val arena: RoyalTitansArena,
    private val walls: RoyalTitansWalls,
    private val elementals: RoyalTitansElementals,
    private val tileAttacks: RoyalTitansTileAttacks,
    private val platforms: RoyalTitansPlatforms,
    private val landings: RoyalTitansLandings,
    private val reinvigoration: RoyalTitansReinvigoration,
) {
    private val log = InlineLogger()
    private val RUN_STATE = InstanceStateKey<Run>("royal-titans-encounter")
    private val pendingDeaths = mutableMapOf<UUID, Pair<Int, UUID>>()
    // Retry ownership must outlive an empty/destroyed instance. This is not a durable outbox.
    private val pendingCompletions = mutableMapOf<UUID, PendingCompletion>()

    fun validateAssets() {
        RoyalTitansAssets.npcIds.forEach(::requireNpc)
        RoyalTitansAssets.locIds.forEach(::requireLoc)
        RoyalTitansAssets.sequenceIds.forEach(::anim)
        RoyalTitansAssets.spotanimIds.forEach(::spot)
        requireNotNull(
            ServerCacheManager.getHealthBar(
                RSCM.run { RoyalTitansAssets.AREA_TIMER_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            )
        ) {
            "Missing Royal Titans Area timer headbar"
        }
    }

    private fun run(player: Player): Run? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == ROYAL_TITANS_INSTANCE_KEY }
            ?.stateOrNull(RUN_STATE)
            ?.takeUnless { it.retired }

    private fun run(npc: Npc): Run? =
        instances
            .instanceForNpc(npc)
            ?.let(instances::sessionForId)
            ?.takeIf { it.key == ROYAL_TITANS_INSTANCE_KEY }
            ?.stateOrNull(RUN_STATE)
            ?.takeUnless { it.retired }

    private fun activeRuns(): List<Run> =
        instances
            .sessionsForKey(ROYAL_TITANS_INSTANCE_KEY)
            .mapNotNull { it.stateOrNull(RUN_STATE) }
            .filterNot { it.retired }

    fun onJoin(player: Player, session: InstanceSession) {
        val first = session.stateOrNull(RUN_STATE) == null
        val run =
            session.state(RUN_STATE) {
                Run(session = session).also { state -> session.onCleanup { retire(state) } }
            }
        if (player.accountHash in run.participants) return
        prepare(player)
        run.participants[player.accountHash] = participant(player, session)
        if (first) {
            spawnTitans(run)
            player.mes("Branda the Fire Queen and Eldric the Ice King turn toward the arena.")
            log.info { "[RoyalTitans] instance=${session.id} owner=${player.displayName}" }
        } else {
            arena.online(run).forEach {
                it.mes("${player.displayName} joins the Royal Titans encounter.")
            }
        }
    }

    fun canJoin(session: InstanceSession): Boolean {
        val run = session.stateOrNull(RUN_STATE)?.takeUnless { it.retired } ?: return false
        return RoyalTitansRules.canJoin(
            run.branda?.hitpoints ?: 0,
            run.eldric?.hitpoints ?: if (run.stage == RoyalTitansStage.INTRO) 600 else 0,
            run.stage,
        )
    }

    fun onLeave(player: Player, instanceId: InstanceId) {
        val run = instances.sessionForId(instanceId)?.stateOrNull(RUN_STATE) ?: return
        // Native death teleports before cleanup hooks and restores HP afterward.
        if (player.hitpoints <= 0) recordDeath(player, run)
        releaseParticipant(player, run)
        if (run.session.occupants.isEmpty()) retire(run)
        else
            arena
                .online(run)
                .firstOrNull { it.hitpoints > 0 }
                ?.let { remaining ->
                    listOfNotNull(run.branda, run.eldric).forEach { phases.retarget(it, remaining) }
                }
    }

    /** Statistics observation only. InstanceDeathCleanupHook owns membership/region teardown. */
    fun recordDeath(player: Player) {
        run(player)?.let { recordDeath(player, it) }
    }

    private fun recordDeath(player: Player, run: Run) {
        val participant = run.participants[player.accountHash] ?: return
        if (participant.deathRecorded) return
        participant.deathRecorded = true
        pendingDeaths[UUID.randomUUID()] = player.accountId to run.completionId
        flushDeaths()
    }

    private fun releaseParticipant(player: Player, run: Run) {
        val participant = run.participants.remove(player.accountHash) ?: return
        participant.hitScope.close()
        if (player.combatHitScope === participant.hitScope) player.combatHitScope = null
        run.lastBossTarget.remove(player.accountHash)?.let {
            bossHpBar.onClose(player, it, instant = true)
        }
        prepare(player)
    }

    fun tickAll() {
        flushDeaths()
        flushCompletions()
        activeRuns().forEach(::tick)
    }

    private fun flushDeaths() {
        for ((id, death) in pendingDeaths.toMap()) {
            try {
                statistics.death(id, death.first, death.second)
                pendingDeaths.remove(id)
            } catch (failure: SQLException) {
                // Native unsafe-death cleanup must still run if persistence is unavailable.
                log.error(failure) { "Royal Titans death persistence pending: $id" }
                return
            }
        }
    }

    private fun tick(run: Run) {
        if (arena.online(run).isEmpty()) return
        walls.expireWalls(run)
        elementals.processPendingSummons(run)
        elementals.processElementals(run)
        tileAttacks.process(run)
        elementals.removeFinishedDeaths(run)

        if (
            run.stage in
                setOf(RoyalTitansStage.INTRO, RoyalTitansStage.RANGED, RoyalTitansStage.MELEE) &&
                RoyalTitansRules.shouldEnterArena(
                    run.branda?.hitpoints ?: ROYAL_TITANS_MAX_HITPOINTS,
                    run.eldric?.hitpoints ?: ROYAL_TITANS_MAX_HITPOINTS,
                )
        ) {
            beginTransition(run)
        }

        when (run.stage) {
            RoyalTitansStage.INTRO -> tickIntro(run)
            RoyalTitansStage.RANGED -> tickRanged(run)
            RoyalTitansStage.MELEE -> tickMelee(run)
            RoyalTitansStage.TRANSITION -> tickTransition(run)
            RoyalTitansStage.ARENA,
            RoyalTitansStage.FINAL_BRANDA,
            RoyalTitansStage.FINAL_ELDRIC -> tickArena(run)
            RoyalTitansStage.COMPLETE -> tickComplete(run)
        }
        run.stageTick++
        if (run.rangedGrace > 0) run.rangedGrace--
        updateRangedVulnerability(run)
        updateTargetHud(run)
    }

    private fun updateTargetHud(run: Run) {
        arena.online(run).forEach { player ->
            val targeted =
                (player.interaction as? InteractionNpc)?.target?.takeIf {
                    (it === run.branda || it === run.eldric) && it.hitpoints > 0
                }
            val previous = run.lastBossTarget[player.accountHash]
            if (previous !== targeted) {
                previous?.let { bossHpBar.onClose(player, it, instant = true) }
                if (targeted == null) run.lastBossTarget.remove(player.accountHash)
                else {
                    run.lastBossTarget[player.accountHash] = targeted
                    bossHpBar.onOpen(player, targeted)
                }
            }
            targeted?.let { bossHpBar.onUpdate(player, it) }
        }
    }

    private fun updateRangedVulnerability(run: Run) {
        // The inherited accuracy adapter remains separately documented; state is per Titan.
        listOfNotNull(run.branda, run.eldric).forEach { npc ->
            val moving =
                phases.movement(npc).rangedVulnerable &&
                    run.stage in
                        setOf(
                            RoyalTitansStage.INTRO,
                            RoyalTitansStage.RANGED,
                            RoyalTitansStage.MELEE,
                        )
            npc.incomingRangedAccuracyMultiplier =
                if (moving || run.areaAttack != null || run.rangedGrace > 0) 6.0 else 1.0
        }
    }

    private fun tickIntro(run: Run) {
        if (run.eldric == null && clock.cycle >= run.eldricSpawnCycle) {
            run.eldric = spawnTitan(run, RoyalTitansAssets.ELDRIC_ACTIVE, ROYAL_TITANS_ELDRIC_SPAWN)
        }
        platforms.tick(run)
        if (run.stageTick >= RoyalTitansRules.INTRO_TICKS) setStage(run, RoyalTitansStage.RANGED)
    }

    private fun tickRanged(run: Run) {
        if (
            !run.elementalsUnlocked &&
                ((run.branda?.hitpoints ?: 600) <= 500 || (run.eldric?.hitpoints ?: 600) <= 500)
        ) {
            run.elementalsUnlocked = true
            listOfNotNull(run.branda, run.eldric).forEach {
                phases.movement(it).summonPending = true
            }
        }
        platforms.tick(run)
    }

    private fun tickMelee(run: Run) {
        tickRanged(run)
    }

    private fun beginTransition(run: Run) {
        listOfNotNull(run.branda, run.eldric).forEach(phases::teleporting)
        walls.clearWalls(run)
        elementals.clearElementals(run)
        landings.clearArenaFloors(run)
        setStage(run, RoyalTitansStage.TRANSITION)
        run.branda?.apply {
            clearInteraction()
            movementLocked = true
            anim(RoyalTitansDefinitions.anim(RoyalTitansAssets.BRANDA_TELEPORT))
            spotanim(spot(RoyalTitansAssets.TELEPORT_GFX), slot = 2)
            say("Time to finish this!")
        }
        landings.landingTelegraph(run, RoyalElement.FIRE)
    }

    private fun tickTransition(run: Run) {
        val target = arena.online(run).firstOrNull { it.hitpoints > 0 } ?: return
        for (npc in listOfNotNull(run.branda, run.eldric)) {
            when (phases.consumeTransition(npc, target)) {
                RoyalTitansAction.BRANDA_LAND -> {
                    run.branda?.let {
                        PathingEntityCommon.telejump(it, collision, arena.local(run, 30, 35))
                        it.moveRestrict = it.type.moveRestrict
                        it.anim(anim(RoyalTitansAssets.BRANDA_ARENA_SPAWN))
                        it.spotanim(spot(RoyalTitansAssets.BRANDA_SPAWN_GFX), slot = 2)
                        it.setBodyModels(listOf(RoyalTitansAssets.BRANDA_ARENA_BODY_MODEL))
                        it.faceSquare(arena.local(run, 31, 31))
                        it.movementLocked = true
                    }
                    landings.spawnBrandaArenaFloors(run)
                    landings.resolveTitanLanding(run, RoyalElement.FIRE)
                }
                RoyalTitansAction.ELDRIC_CAST -> {
                    run.eldric?.apply {
                        clearInteraction()
                        movementLocked = true
                        anim(RoyalTitansDefinitions.anim(RoyalTitansAssets.ELDRIC_TELEPORT))
                        say("This is no game anymore!")
                    }
                    landings.landingTelegraph(run, RoyalElement.ICE)
                }
                RoyalTitansAction.ELDRIC_LAND -> {
                    run.eldric?.let {
                        PathingEntityCommon.telejump(it, collision, arena.local(run, 30, 25))
                        it.moveRestrict = it.type.moveRestrict
                        it.anim(anim(RoyalTitansAssets.ELDRIC_ARENA_SPAWN))
                        it.setBodyModels(listOf(RoyalTitansAssets.ELDRIC_ARENA_BODY_MODEL))
                        it.faceSquare(arena.local(run, 30, 30))
                        it.movementLocked = true
                    }
                    landings.spawnEldricArenaFloors(run)
                    landings.resolveTitanLanding(run, RoyalElement.ICE)
                    setStage(run, RoyalTitansStage.ARENA)
                }
                else -> Unit
            }
        }
    }

    private fun tickArena(run: Run) {
        val target = arena.online(run).firstOrNull { it.hitpoints > 0 } ?: return
        // The native per-actor phase clock owns readiness. Consume only presentation here,
        // after existing hazards, preserving pre-input/LateCycle order and paired Blast geometry.
        for ((npc, element) in
            listOf(run.branda to RoyalElement.FIRE, run.eldric to RoyalElement.ICE)) {
            if (npc == null || npc.hitpoints <= 0) continue
            when (phases.consume(npc, target, clock.cycle - run.stageTick - 1)) {
                RoyalTitansAction.CHARGE -> tileAttacks.start(run)
                RoyalTitansAction.SUMMON -> elementals.scheduleSummon(run, npc, element)
                RoyalTitansAction.STOMP -> tileAttacks.stomp(run, npc, element, true)
                else -> Unit
            }
        }
    }

    private fun tickComplete(run: Run) {
        commitCompletion(run)
        reinvigoration.tick(run) { reset(run) }
    }

    fun canAttackTitan(npc: Npc, target: Player): Boolean =
        run(npc)?.let { run ->
            (npc === run.branda || npc === run.eldric) && run(target) === run
        } == true

    fun canAttackElemental(npc: Npc, target: Player): Boolean =
        run(npc)?.let { run -> npc in run.elementals && target.accountHash in run.participants } ==
            true

    fun modifyNpcHit(player: Player?, npc: Npc, hit: HitBuilder) {
        val run = run(npc)
        if (run == null || (player != null && player.accountHash !in run.participants)) {
            hit.damage = 0
            return
        }
        // Native damage remains legal independently of its owner's contribution eligibility.
        // The shared hit context preserves the actual origin; typeless is not a provenance tag.
        if (player == null) {
            if (
                (npc !== run.branda && npc !== run.eldric) ||
                    run.stage in
                        setOf(
                            RoyalTitansStage.INTRO,
                            RoyalTitansStage.TRANSITION,
                            RoyalTitansStage.COMPLETE,
                        )
            ) {
                hit.damage = 0
            }
            return
        }
        if (run.walls.any { it.hitbox === npc }) {
            hit.damage = 0
            return
        }
        run.elementals[npc]?.let { elemental ->
            val spell = hit.secondaryType()
            if (
                hit.type == HitType.Magic &&
                    spell != null &&
                    RoyalTitansRules.opposingSpell(elemental.element, spell.internalName)
            ) {
                // Damage comes from the native spell calculation. Special accuracy must be
                // resolved by the spell content before presentation, never rerolled here.
                if (hit.damage > 0)
                    elementals.splashElementals(run, player, npc, elemental.element, hit.damage)
            }
            return
        }
        val titanElement =
            when (npc) {
                run.branda -> RoyalElement.FIRE
                run.eldric -> RoyalElement.ICE
                else -> null
            }
        if (
            titanElement == null ||
                run.stage in
                    setOf(
                        RoyalTitansStage.INTRO,
                        RoyalTitansStage.TRANSITION,
                        RoyalTitansStage.COMPLETE,
                    )
        ) {
            hit.damage = 0
            return
        }
        // HUD values are resolved HP, never a prediction made when this hit is queued.
    }

    fun canCastOnWall(player: Player, npc: Npc): Boolean {
        val run = run(npc) ?: return false
        return npc.isSlotAssigned &&
            arena.isPresent(run, player) &&
            run.walls.any { it.hitbox === npc }
    }

    /** Called by the wall's native hit queue at projectile impact, not the launch-time modifier. */
    fun wallHit(npc: Npc, hit: Hit) {
        val run = run(npc) ?: return
        if (!hit.isFromPlayer || hit.type != HitType.Magic) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!canCastOnWall(player, npc)) return
        val spell = hit.secondaryType() ?: return
        if (!RoyalTitansWallSpells.accepts(npc.visType.id, spell.internalName)) return
        val wall = run.walls.firstOrNull { it.hitbox === npc } ?: return
        walls.clearWallTriplet(run, wall)
    }

    /** The native impact event is emitted after HP capping/subtraction, before death execution. */
    fun npcHit(npc: Npc, hit: Hit) {
        val run = run(npc) ?: return
        if (!hit.isFromPlayer || hit.damage <= 0) return
        val player = hit.resolvePlayerSource(players) ?: return
        if (!arena.isPresent(run, player)) return
        if (npc in run.elementals) {
            if (npc.hitpoints == 0) elementals.onElementalKilled(run, player, npc)
        }
    }

    fun beginTitanDeath(npc: Npc): Run? {
        val run = run(npc) ?: return null
        if (npc !== run.branda && npc !== run.eldric) return null
        if (run.dyingTitans.add(npc)) {
            phases.retire(npc)
            npc.clearInteraction()
            npc.movementLocked = true
        }
        return run
    }

    fun finishTitanDeath(npc: Npc) {
        val run = run(npc) ?: return
        if (!run.dyingTitans.remove(npc)) return
        arena.online(run).forEach { player ->
            if (run.lastBossTarget[player.accountHash] === npc)
                bossHpBar.onClose(player, npc, instant = true)
        }
        val element = if (npc === run.branda) RoyalElement.FIRE else RoyalElement.ICE
        if (element == RoyalElement.FIRE) run.branda = null else run.eldric = null
        val corpse = npc
        corpse.queueList.clear()
        corpse.noneMode()
        val corpseType =
            requireNpc(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_CORPSE
                else RoyalTitansAssets.ELDRIC_CORPSE
            )
        corpse.transmog(corpseType, Int.MAX_VALUE)
        corpse.resetBodyModels()
        // Inactive cache types have 500 HP for native noncombat interaction validity. They are
        // no longer active Titans; their own queue handlers reject combat without a new headbar.
        corpse.copyStats(corpseType)
        corpse.hideAllOps()
        corpse.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_INACTIVE
                else RoyalTitansAssets.ELDRIC_INACTIVE
            )
        )
        run.corpses[corpse] = element
        when {
            // Defeating one Titan does not restart the surviving Titan's rotation clock.
            run.branda != null && run.eldric == null -> run.stage = RoyalTitansStage.FINAL_BRANDA
            run.eldric != null && run.branda == null -> run.stage = RoyalTitansStage.FINAL_ELDRIC
            run.branda == null && run.eldric == null -> complete(run, npc)
        }
    }

    fun finishElementalDeath(npc: Npc) {
        run(npc)?.let { elementals.finishElementalDeath(it, npc) }
    }

    private fun complete(run: Run, lastTitan: Npc) {
        if (run.completion != null) return
        run.hitScope.invalidate()
        run.participants.values.forEach { it.hitScope.invalidate() }
        walls.clearWalls(run)
        elementals.clearElementals(run)
        setStage(run, RoyalTitansStage.COMPLETE)
        val duration =
            RoyalTitansTime.elapsed(requireNotNull(run.spawnCycles[lastTitan]), clock.cycle)
        run.completion =
            RoyalTitansCompletion(
                run.completionId,
                duration,
                arena
                    .online(run)
                    .filter { contribution(it) > 0 }
                    .map {
                        RoyalTitansFinisher(
                            it.accountId,
                            it.displayName,
                            personalBestEligible = true,
                        )
                    }
                    .distinctBy { it.accountId },
            )
        arena.online(run).forEach {
            it.combatClearQueue()
            it.clearInteraction()
            run.lastBossTarget.remove(it.accountHash)?.let { target ->
                bossHpBar.onClose(it, target, instant = true)
            }
        }
        commitCompletion(run)
    }

    private fun commitCompletion(run: Run) {
        val completion = run.completion ?: return
        if (run.completionCommitted) return
        pendingCompletions.putIfAbsent(completion.encounterId, PendingCompletion(completion))
        flushCompletions()
    }

    private fun flushCompletions() {
        for ((id, pending) in pendingCompletions.toMap()) {
            if (clock.cycle < pending.retryCycle) continue
            val receipts =
                try {
                    statistics.complete(pending.completion)
                } catch (failure: SQLException) {
                    pending.retryCycle = clock.cycle + 10
                    log.error(failure) { "Royal Titans completion persistence pending: $id" }
                    continue
                }
            pendingCompletions.remove(id)
            val run = activeRuns().firstOrNull { it.completion?.encounterId == id }
            if (run != null) {
                run.completionCommitted = true
                run.corpses.keys.forEach { it.showAllOps() }
            }
            // Refresh an online finisher's projection even after exit/reconnect. Never reopen an
            // abandoned run or advertise its expired loot to someone outside that exact run.
            for (player in players) {
                val receipt = receipts[player.accountId] ?: continue
                VarPlayerIntMapSetter.set(
                    player,
                    "varp.total_royal_titan_kills",
                    receipt.snapshot.personalKills,
                )
                if (run == null || run(player) !== run) continue
                player.mes(
                    "Your Royal Titans kill count is: <col=ff0000>${receipt.snapshot.personalKills}</col>."
                )
                player.mes(
                    RoyalTitansTime.durationMessage(pending.completion.elapsedTicks, receipt)
                )
                player.mes(
                    "You are eligible to take loot from either Branda the Fire Queen or Eldric the Ice King."
                )
            }
        }
    }

    fun claim(player: Player, corpse: Npc, choice: ClaimChoice) {
        val run = run(corpse) ?: return player.mes("That Titan is no longer available.")
        if (run(player) !== run) return player.mes("That Titan belongs to another encounter.")
        val element = run.corpses[corpse] ?: return player.mes("That Titan cannot be claimed.")
        if (run.stage != RoyalTitansStage.COMPLETE || !run.completionCommitted)
            return player.mes("Defeat both Titans before claiming a reward.")
        if (contribution(player) <= 0) return player.mes("You did not contribute to this victory.")
        if (!run.claimed.add(player.accountHash))
            return player.mes("You have already claimed this victory's reward.")
        when (choice) {
            ClaimChoice.LOOT -> rollLoot(player, corpse)
            ClaimChoice.PAGES ->
                objects.add("obj.desiccated_page", player.coords, 200, player, 10 + random.of(10))
            ClaimChoice.SACRIFICE -> {
                if (random.of(1500) == 0)
                    objects.add("obj.rtbrandapet", player.coords, 200, player, 1)
                player.mes("You sacrifice your loot for an improved pet chance.")
            }
        }
        if (choice != ClaimChoice.SACRIFICE)
            player.mes(
                "You take your reward from ${if (element == RoyalElement.FIRE) "Branda" else "Eldric"}."
            )
        if (reinvigoration.start(run)) {
            arena.online(run).forEach {
                it.mes(
                    "<col=ef1020>The Royal Titans will reinvigorate themselves in 18 seconds.</col>"
                )
            }
        }
    }

    private fun rollLoot(player: Player, corpse: Npc) {
        val activeType =
            requireNpc(
                if (runElement(corpse) == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_ACTIVE
                else RoyalTitansAssets.ELDRIC_ACTIVE
            )
        val proxy = Npc(activeType, corpse.coords)
        when (val result = drops.forNpc(proxy)?.roll(player, ArgMap())?.flatten()) {
            is RollResult.Single -> giveDrop(player, result.result)
            is RollResult.ListOf -> result.results.forEach { giveDrop(player, it) }
            else -> player.mes("No reward table was available.")
        }
    }

    private fun runElement(corpse: Npc): RoyalElement =
        run(corpse)?.corpses?.get(corpse) ?: RoyalElement.FIRE

    private fun giveDrop(player: Player, drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        objects.add(obj, player.coords, 200, player, drop.rollCount(random))
    }

    fun finishAuxiliaryDeath(npc: Npc) {
        val run = run(npc) ?: return
        if (npc in run.elementals) finishElementalDeath(npc)
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = run(npc) ?: return "That creature is not part of this encounter."
        if (player.accountHash !in run.participants)
            return "That creature belongs to another encounter."
        if (npc in run.corpses) return "Choose a reward option instead."
        if (npc === run.branda || npc === run.eldric) {
            return if (
                run.stage in
                    setOf(
                        RoyalTitansStage.RANGED,
                        RoyalTitansStage.MELEE,
                        RoyalTitansStage.ARENA,
                        RoyalTitansStage.FINAL_BRANDA,
                        RoyalTitansStage.FINAL_ELDRIC,
                    )
            )
                null
            else "The Titan cannot be attacked during this transition."
        }
        if (npc in run.elementals || run.walls.any { it.hitbox === npc }) return null
        return "You cannot attack that."
    }

    fun isEncounterNpc(npc: Npc): Boolean = (run(npc) != null)

    fun isParticipant(player: Player, npc: Npc): Boolean =
        run(npc)?.let { run(player) === it } == true

    fun isActive(player: Player): Boolean = (run(player) != null)

    /** Native processed Titan damage only; helpers are deliberately excluded. */
    fun contribution(player: Player): Int {
        val run = run(player) ?: return 0
        return (listOfNotNull(run.branda, run.eldric) + run.corpses.keys).distinct().sumOf {
            it.damageContributions.damageBy(player)
        }
    }

    fun status(player: Player): String {
        val run = run(player) ?: return "Royal Titans: no active encounter."
        return "Royal Titans ${run.stage}: Branda ${run.branda?.hitpoints ?: 0}/600, Eldric ${run.eldric?.hitpoints ?: 0}/600, walls=${run.walls.size}, elementals=${run.elementals.size}, contribution=${contribution(player)}."
    }

    private fun spawnTitans(run: Run) {
        run.completionId = UUID.randomUUID()
        run.completion = null
        run.completionCommitted = false
        run.spawnCycles.clear()
        run.branda = spawnTitan(run, RoyalTitansAssets.BRANDA_ACTIVE, ROYAL_TITANS_BRANDA_SPAWN)
        run.eldric = null
        run.eldricSpawnCycle = clock.cycle + 2
    }

    private fun spawnTitan(run: Run, id: Int, local: RegionLocal): Npc {
        val tile = requireNotNull(instances.localCoord(run.session, local))
        val npc = arena.spawnNpc(run, id, tile, ROYAL_TITANS_MAX_HITPOINTS)
        phases.prepare(npc, arena.online(run).first { it.hitpoints > 0 }) {
            // Native encounter deletion must detach the pair reference before another LateCycle
            // can look up the removed actor and accidentally create a fresh combat identity.
            if (run.branda === npc) run.branda = null
            if (run.eldric === npc) run.eldric = null
            run.spawnCycles.remove(npc)
        }
        // The fire/ice platforms carry BLOCK_WALK in region 11669. Titans walk on those
        // blocked tiles; players and elementals retain normal terrain restrictions. Use the
        // same native restriction for route selection and step execution, preserving loc walls.
        npc.moveRestrict = MoveRestrict.Blocked
        run.spawnCycles[npc] = clock.cycle
        npc.anim(
            anim(
                if (id == RoyalTitansAssets.BRANDA_ACTIVE) RoyalTitansAssets.BRANDA_ARENA_SPAWN
                else RoyalTitansAssets.ELDRIC_ARENA_SPAWN
            )
        )
        npc.faceSquare(arena.local(run, if (id == RoyalTitansAssets.BRANDA_ACTIVE) 32 else 28, 30))
        npc.mode = NpcMode.None
        return npc
    }

    private fun setStage(run: Run, stage: RoyalTitansStage) {
        run.stage = stage
        run.stageTick = -1
    }

    private fun reset(run: Run) {
        reinvigoration.cancel(run)
        run.hitScope.invalidate()
        run.participants.values.forEach { it.hitScope.invalidate() }
        run.generation++
        run.participants.values.forEach { it.deathRecorded = false }
        walls.clearWalls(run)
        elementals.clearElementals(run)
        run.areaAttack?.let { tileAttacks.end(run, it) }
        run.corpses.keys.toList().forEach { arena.removeNpc(run, it) }
        run.corpses.clear()
        landings.clearArenaFloors(run)
        run.claimed.clear()
        run.elementalsUnlocked = false
        run.rangedGrace = 0
        run.creditedElementalKills.clear()
        elementals.clearDying(run)
        run.lastBossTarget.clear()
        run.dyingTitans.clear()
        spawnTitans(run)
        setStage(run, RoyalTitansStage.INTRO)
        arena.online(run).forEach { it.mes("The Royal Titans reinvigorate themselves.") }
    }

    private fun retire(run: Run) {
        if (run.retired) return
        run.retired = true
        reinvigoration.cancel(run)
        listOfNotNull(run.branda, run.eldric).forEach(phases::retire)
        run.hitScope.close()
        arena.online(run).forEach { releaseParticipant(it, run) }
        run.participants.values.forEach { it.hitScope.close() }
        run.participants.clear()
        run.areaAttack?.let { tileAttacks.end(run, it) }
        walls.clearWalls(run)
        elementals.clearElementals(run)
        landings.clearArenaFloors(run)
        run.dyingElementals.clear()
        run.removableElementals.clear()
        run.lastBossTarget.clear()
        // Native destruction owns Titans/corpses and all remaining actor deletion.
        // Stop interaction immediately during the shared deferred logout reclaim interval.
        instances.npcsForInstance(run.session.id).forEach {
            it.clearInteraction()
            it.mode = NpcMode.None
            it.abortRoute()
            it.resetBodyModels()
        }
        log.info { "[RoyalTitans] instance=${run.session.id} generation=${run.generation} retired" }
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun participant(player: Player, session: InstanceSession): Participant {
        val base = requireNotNull(instances.localCoord(session, RegionLocal(0, 45, 149, 0, 0)))
        val scope = HitScope(Bounds(base, 64, 64), player.clientDisconnected)
        player.combatHitScope = scope
        return Participant(scope)
    }

    private data class PendingCompletion(
        val completion: RoyalTitansCompletion,
        var retryCycle: Int = Int.MIN_VALUE,
    )
}

internal enum class ClaimChoice {
    LOOT,
    SACRIFICE,
    PAGES,
}
