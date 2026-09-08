package org.rsmod.content.bosses.royaltitans

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class RoyalTitansEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val objects: ObjRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val interactions: AiPlayerInteractions,
    private val players: PlayerList,
    private val drops: DropTableRegistry,
    private val bossHpBar: BossHpBarScript,
    private val npcHitModifier: StandardNpcHitModifier,
    private val wornBonuses: WornBonuses,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<InstanceId, Run>()
    private val participantRuns = mutableMapOf<Long, Run>()
    private val npcRuns = mutableMapOf<Npc, Run>()
    private var generation = 1L

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

    fun enter(player: Player): Boolean {
        if (!canTravel(player)) return false
        val brandaType = requireNpc(RoyalTitansAssets.BRANDA_ACTIVE)
        val eldricType = requireNpc(RoyalTitansAssets.ELDRIC_ACTIVE)
        val result =
            instances.create(
                owner = player,
                key = ROYAL_TITANS_INSTANCE_KEY,
                spec = royalTitansSpec(brandaType, eldricType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Royal Titans arena is unavailable."
            )
            return false
        }

        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val participant = Participant(player.accountHash, player.uid)
        val run =
            Run(
                generation = generation++,
                session = result.session,
                ownerHash = player.accountHash,
                participants = mutableMapOf(player.accountHash to participant),
            )
        runs[result.session.id] = run
        participantRuns[player.accountHash] = run
        spawnTitans(run)
        player.mes("Branda the Fire Queen and Eldric the Ice King turn toward the arena.")
        log.info { "[RoyalTitans] generation=${run.generation} owner=${player.displayName}" }
        return true
    }

    fun join(player: Player, leaderName: String): Boolean {
        if (!canTravel(player)) return false
        val run =
            runs.values.firstOrNull { candidate ->
                online(candidate).any {
                    it.displayName.equals(leaderName.trim(), ignoreCase = true) ||
                        it.username.equals(leaderName.trim(), ignoreCase = true)
                }
            }
        if (run == null) {
            player.mes("That player does not have an available Royal Titans instance.")
            return false
        }
        val brandaHp = run.branda?.hitpoints ?: 0
        val eldricHp = run.eldric?.hitpoints ?: 0
        if (!RoyalTitansRules.canJoin(brandaHp, eldricHp, run.stage)) {
            player.mes("That fight has progressed too far for another player to join.")
            return false
        }
        val result = instances.join(player, run.session, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "You cannot join that fight."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, run.session, clock.cycle)
        run.participants[player.accountHash] = Participant(player.accountHash, player.uid)
        participantRuns[player.accountHash] = run
        keepAggressive(run)
        online(run).forEach { it.mes("${player.displayName} joins the Royal Titans encounter.") }
        return true
    }

    fun escape(player: Player): Boolean = removeParticipant(player, "left", teleport = true)

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        val run = participantRuns[player.accountHash] ?: return
        if (run.session.id != instanceId) return
        removeParticipant(player, "teleported out", teleport = false)
    }

    fun removeParticipant(
        player: Player,
        reason: String,
        teleport: Boolean,
        logout: Boolean = false,
    ): Boolean {
        val run = participantRuns.remove(player.accountHash) ?: return false
        run.participants.remove(player.accountHash)
        if (!logout) {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
            prepare(player)
            if (reason == "left") player.mes("You leave the Royal Titans' arena.")
        }
        if (run.participants.isEmpty()) abort(run, reason)
        return true
    }

    fun tickAll() {
        runs.values.toList().forEach(::tick)
    }

    private fun tick(run: Run) {
        if (online(run).isEmpty()) return
        updateRangedVulnerability(run)
        expireWalls(run)
        processPendingSummons(run)
        processElementals(run)
        processAreaAttack(run)

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
    }

    private fun updateRangedVulnerability(run: Run) {
        val multiplier =
            if (
                RoyalTitansRules.hasRangedAccuracyBoost(
                    run.stage,
                    run.areaAttack != null,
                    run.rangedGrace,
                )
            )
                6.0
            else 1.0
        run.branda?.incomingRangedAccuracyMultiplier = multiplier
        run.eldric?.incomingRangedAccuracyMultiplier = multiplier
    }

    private fun tickIntro(run: Run) {
        if (run.eldric == null && clock.cycle >= run.eldricSpawnCycle) {
            run.eldric = spawnTitan(run, RoyalTitansAssets.ELDRIC_ACTIVE, ROYAL_TITANS_ELDRIC_SPAWN)
        }
        if (run.stageTick >= RoyalTitansRules.INTRO_TICKS) setStage(run, RoyalTitansStage.RANGED)
    }

    private fun tickRanged(run: Run) {
        when (run.stageTick) {
            0 -> retreat(run.branda, local(run, 24, 30))
            2 -> retreat(run.eldric, local(run, 36, 30))
            RoyalTitansRules.BRANDA_WALL_CAST_TICK ->
                run.branda?.let { castWall(run, it, RoyalElement.FIRE) }
            RoyalTitansRules.ELDRIC_WALL_CAST_TICK ->
                run.eldric?.let { castWall(run, it, RoyalElement.ICE) }
            RoyalTitansRules.ADVANCE_TICK -> {
                advance(run.branda, local(run, 28, 30))
                advance(run.eldric, local(run, 33, 30))
            }
        }
        if (run.stageTick >= 9 && (run.stageTick - 9) % 6 == 0) harmlessInterTitanCast(run)
        if (
            !run.elementalsUnlocked &&
                ((run.branda?.hitpoints ?: 600) <= 500 || (run.eldric?.hitpoints ?: 600) <= 500)
        ) {
            run.elementalsUnlocked = true
            run.branda?.let { scheduleSummon(run, it, RoyalElement.FIRE) }
            run.eldric?.let { scheduleSummon(run, it, RoyalElement.ICE) }
        }
        if (run.stageTick >= RoyalTitansRules.RANGED_PHASE_TICKS)
            setStage(run, RoyalTitansStage.MELEE)
    }

    private fun tickMelee(run: Run) {
        keepAggressive(run)
        if (run.stageTick in setOf(4, 11, 18)) {
            run.branda?.takeIf { it.hitpoints > 0 }?.let { stomp(run, it, RoyalElement.FIRE) }
            run.eldric?.takeIf { it.hitpoints > 0 }?.let { stomp(run, it, RoyalElement.ICE) }
        }
        if (run.stageTick >= RoyalTitansRules.MELEE_PHASE_TICKS)
            setStage(run, RoyalTitansStage.RANGED)
    }

    private fun beginTransition(run: Run) {
        clearWalls(run)
        clearElementals(run)
        clearArenaFloors(run)
        setStage(run, RoyalTitansStage.TRANSITION)
        run.branda?.apply {
            clearInteraction()
            movementLocked = true
            anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_TELEPORT))
            spotanim(spot(RoyalTitansAssets.TELEPORT_GFX))
        }
    }

    private fun tickTransition(run: Run) {
        when (run.stageTick) {
            RoyalTitansRules.BRANDA_TRANSITION_TICKS -> {
                run.branda?.let {
                    PathingEntityCommon.telejump(it, collision, local(run, 30, 35))
                    it.anim(anim(RoyalTitansAssets.BRANDA_ARENA_SPAWN))
                    it.spotanim(spot(RoyalTitansAssets.BRANDA_SPAWN_GFX))
                    it.faceSquare(local(run, 30, 30))
                    it.movementLocked = true
                }
                spawnBrandaArenaFloors(run)
                resolveTitanLanding(run, RoyalElement.FIRE)
            }
            RoyalTitansRules.ELDRIC_TRANSITION_CAST_TICK -> {
                run.eldric?.apply {
                    clearInteraction()
                    movementLocked = true
                    anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.ELDRIC_TELEPORT))
                }
            }
            RoyalTitansRules.ELDRIC_TRANSITION_TICKS -> {
                run.eldric?.let {
                    PathingEntityCommon.telejump(it, collision, local(run, 30, 25))
                    it.anim(anim(RoyalTitansAssets.ELDRIC_ARENA_SPAWN))
                    it.faceSquare(local(run, 30, 30))
                    it.movementLocked = true
                }
                spawnEldricArenaFloors(run)
                resolveTitanLanding(run, RoyalElement.ICE)
                setStage(run, RoyalTitansStage.ARENA)
            }
        }
    }

    private fun resolveTitanLanding(run: Run, element: RoyalElement) {
        online(run).forEach { player ->
            if (inTitanLanding(run, player.coords, element)) {
                hit(run, player, random.of(16) + 15, HitType.Typeless, bypassPrayer = true)
                PathingEntityCommon.telejump(player, collision, local(run, 31, 30))
            }
        }
    }

    private fun tickArena(run: Run) {
        val opening =
            if (run.stage == RoyalTitansStage.ARENA) RoyalTitansRules.ARENA_OPENING_TICKS
            else RoyalTitansRules.FINAL_OPENING_TICKS
        if (run.stageTick < opening) return
        val elapsed = run.stageTick - opening
        val cycle =
            if (elapsed < RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS) elapsed
            else
                (elapsed - RoyalTitansRules.FIRST_ARENA_CYCLE_TICKS) %
                    RoyalTitansRules.ARENA_CYCLE_TICKS
        when (cycle) {
            0 -> startAreaAttack(run)
            14 -> {
                run.branda
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { scheduleSummon(run, it, RoyalElement.FIRE) }
                run.eldric
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { scheduleSummon(run, it, RoyalElement.ICE) }
            }
            21,
            28 -> {
                run.branda
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { stomp(run, it, RoyalElement.FIRE, arenaWide = true) }
                run.eldric
                    ?.takeIf { it.hitpoints > 0 }
                    ?.let { stomp(run, it, RoyalElement.ICE, arenaWide = true) }
            }
        }
    }

    private fun tickComplete(run: Run) {
        if (run.stageTick == RoyalTitansRules.RESET_WARNING_TICK) {
            online(run).forEach { it.mes("The defeated Titans begin to stir.") }
        }
        if (run.stageTick >= RoyalTitansRules.CORPSE_RESET_TICKS) reset(run)
    }

    private fun castWall(run: Run, titan: Npc, element: RoyalElement) {
        val target = online(run).minByOrNull { distance(it.coords, titan.coords) } ?: return
        titan.faceSquare(target.coords)
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_WALL_CAST
                else RoyalTitansAssets.ELDRIC_WALL_CAST
            )
        )
        titan.spotanim(spot(RoyalTitansAssets.WALL_CAST_GFX))
        val row = localZ(run, target.coords).coerceIn(27, 34)
        (0..4).forEach { index ->
            val tile = local(run, 29 + index, row)
            world.spotanimMap(
                SpotanimType(
                    if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_START_GFX
                    else RoyalTitansAssets.ICE_WALL_START_GFX
                ),
                tile,
            )
        }
        run.pendingWalls +=
            PendingWall(clock.cycle + RoyalTitansRules.WALL_TELEGRAPH_TICKS, element, row)
    }

    private fun formWall(run: Run, pending: PendingWall) {
        (0..4).forEach { index ->
            val tile = local(run, 29 + index, pending.row)
            val occupant = online(run).firstOrNull { it.coords == tile }
            if (occupant != null) {
                hit(
                    run,
                    occupant,
                    random.of(RoyalTitansRules.WALL_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                )
                return@forEach
            }
            run.elementals.entries
                .firstOrNull { it.key.coords == tile && it.value.element != pending.element }
                ?.key
                ?.queueDeath()
            val loc =
                locs.add(
                    tile,
                    requireLoc(
                        if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL
                        else RoyalTitansAssets.ICE_WALL
                    ),
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            val hitbox =
                spawnNpc(
                    run,
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_HITBOX
                    else RoyalTitansAssets.ICE_WALL_HITBOX,
                    tile,
                    hitpoints = 1,
                    movementLocked = true,
                )
            run.walls +=
                WallSegment(
                    pending.element,
                    pending.row,
                    index,
                    loc,
                    hitbox,
                    clock.cycle + RoyalTitansRules.WALL_LIFETIME_TICKS,
                )
            world.spotanimMap(
                SpotanimType(
                    if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_ACTIVE_GFX
                    else RoyalTitansAssets.ICE_WALL_ACTIVE_GFX
                ),
                tile,
            )
            world.soundArea(
                tile,
                if (pending.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_FORM_SOUND
                else RoyalTitansAssets.ICE_WALL_FORM_SOUND,
            )
        }
    }

    private fun expireWalls(run: Run) {
        val pending = run.pendingWalls.iterator()
        while (pending.hasNext()) {
            val wall = pending.next()
            if (clock.cycle < wall.formCycle) continue
            pending.remove()
            formWall(run, wall)
        }
        run.walls.filter { clock.cycle >= it.expireCycle }.toList().forEach { clearWall(run, it) }
    }

    private fun clearWallTriplet(run: Run, wall: WallSegment) {
        // RSProx 2283 confirms one successful cast removes the logical five-segment row.
        run.walls
            .filter { it.element == wall.element && it.row == wall.row }
            .toList()
            .forEach { clearWall(run, it) }
    }

    private fun clearWall(run: Run, wall: WallSegment) {
        if (!run.walls.remove(wall)) return
        locs.del(wall.loc, Int.MAX_VALUE)
        npcRuns.remove(wall.hitbox)
        if (wall.hitbox.isSlotAssigned) npcs.del(wall.hitbox, Int.MAX_VALUE)
        world.spotanimMap(
            SpotanimType(
                if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_END_GFX
                else RoyalTitansAssets.ICE_WALL_END_GFX
            ),
            wall.loc.coords,
        )
        world.soundArea(
            wall.loc.coords,
            if (wall.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_WALL_BREAK_SOUND
            else RoyalTitansAssets.ICE_WALL_BREAK_SOUND,
        )
    }

    private fun scheduleSummon(run: Run, titan: Npc, element: RoyalElement) {
        val active = run.elementals.values.count { it.element == element }
        if (active >= 3) return
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_SUMMON
                else RoyalTitansAssets.ELDRIC_SUMMON
            )
        )
        if (element == RoyalElement.FIRE)
            titan.spotanim(spot(RoyalTitansAssets.ELEMENTAL_SUMMON_GFX))
        run.pendingSummons +=
            PendingSummon(clock.cycle + RoyalTitansRules.SUMMON_DELAY_TICKS, element)
    }

    private fun processPendingSummons(run: Run) {
        val iterator = run.pendingSummons.iterator()
        while (iterator.hasNext()) {
            val summon = iterator.next()
            if (clock.cycle < summon.spawnCycle) continue
            iterator.remove()
            val baseZ = if (summon.element == RoyalElement.FIRE) 33 else 27
            val targets = online(run).filter { it.hitpoints > 0 }
            val packTarget =
                if (targets.isEmpty()) null else targets[run.packSequence++ % targets.size]
            for (x in listOf(29, 31, 33)) {
                if (run.elementals.values.count { it.element == summon.element } >= 3) break
                val npc =
                    spawnNpc(
                        run,
                        if (summon.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL
                        else RoyalTitansAssets.ICE_ELEMENTAL,
                        local(run, x, baseZ),
                        hitpoints = RoyalTitansRules.ELEMENTAL_HITPOINTS,
                    )
                npc.anim(
                    anim(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN
                    )
                )
                npc.spotanim(
                    spot(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_GFX
                    )
                )
                run.elementals[npc] =
                    ElementalState(
                        summon.element,
                        clock.cycle + RoyalTitansRules.ELEMENTAL_LIFETIME_TICKS,
                    )
                packTarget?.let { npc.apPlayer2(it, interactions) }
            }
            online(run).forEach {
                it.mes(
                    if (summon.element == RoyalElement.FIRE) "Branda summons fire elementals!"
                    else "Eldric summons ice elementals!"
                )
            }
            world.soundArea(
                local(run, 31, baseZ),
                if (summon.element == RoyalElement.FIRE)
                    RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_SOUND
                else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_SOUND,
            )
        }
    }

    private fun processElementals(run: Run) {
        run.elementals.toMap().forEach { (npc, state) ->
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) return@forEach
            if (clock.cycle >= state.explodeCycle) {
                npc.anim(
                    anim(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLODE
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLODE
                    )
                )
                world.spotanimMap(
                    SpotanimType(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLOSION_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLOSION_GFX
                    ),
                    npc.coords,
                )
                online(run)
                    .filter { distance(it.coords, npc.coords) <= 1 }
                    .forEach {
                        hit(
                            run,
                            it,
                            8 + random.of(3),
                            HitType.Typeless,
                            bypassPrayer = true,
                            source = npc,
                        )
                    }
                npc.queueDeath()
            } else if (npc.mode == null) {
                targetFor(run, npc)?.let { npc.apPlayer2(it, interactions) }
            }
        }
    }

    private fun startAreaAttack(run: Run) {
        if (run.areaAttack != null) return
        val survivor =
            when (run.stage) {
                RoyalTitansStage.FINAL_BRANDA -> RoyalElement.FIRE
                RoyalTitansStage.FINAL_ELDRIC -> RoyalElement.ICE
                else -> null
            }
        val (safeX, safeZ) = RoyalTitansRules.safeTile(survivor, run.areaSequence++)
        val safeTile = local(run, safeX, safeZ)
        val safeLoc =
            locs.add(
                safeTile,
                requireLoc(RoyalTitansAssets.SAFE_TILE),
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.GroundDecor,
            )
        val timer = spawnNpc(run, RoyalTitansAssets.AREA_TIMER, safeTile, 1, movementLocked = true)
        val timerHeadbar =
            requireNotNull(
                ServerCacheManager.getHealthBar(
                    RSCM.run { RoyalTitansAssets.AREA_TIMER_HEADBAR.asRSCM(RSCMType.HEADBAR) }
                )
            )
        timer.showHeadbar(
            Headbar.fromNoSource(
                self = timerHeadbar.id,
                public = timerHeadbar.id,
                startFill = 0,
                endFill = timerHeadbar.segments.coerceIn(1, 255),
                startTime = 0,
                endTime = 210,
            )
        )
        run.branda
            ?.takeIf { it.hitpoints > 0 }
            ?.apply {
                anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_AREA_CHARGE))
                spotanim(spot(RoyalTitansAssets.AREA_CHARGE_GFX))
            }
        run.eldric
            ?.takeIf { it.hitpoints > 0 }
            ?.apply {
                anim(this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.ELDRIC_AREA_CHARGE))
            }
        val executeCycle = clock.cycle + RoyalTitansRules.AREA_CHARGE_TICKS
        listOfNotNull(run.branda, run.eldric).forEach {
            it.clearInteraction()
            it.actionDelay = executeCycle + 6
        }
        run.areaAttack = AreaAttack(safeX, safeZ, executeCycle, safeLoc, timer)
    }

    private fun processAreaAttack(run: Run) {
        val attack = run.areaAttack ?: return
        val elapsed = clock.cycle - attack.impactCycle
        if (elapsed < 0) return
        if (elapsed == 0) {
            run.branda
                ?.takeIf { it.hitpoints > 0 }
                ?.apply {
                    anim(
                        this@RoyalTitansEncounterManager.anim(RoyalTitansAssets.BRANDA_AREA_EXECUTE)
                    )
                    spotanim(spot(RoyalTitansAssets.AREA_EXECUTE_GFX))
                }
            run.eldric
                ?.takeIf { it.hitpoints > 0 }
                ?.anim(anim(RoyalTitansAssets.ELDRIC_AREA_EXECUTE))
            world.soundArea(
                local(run, attack.safeX, attack.safeZ),
                RoyalTitansAssets.AREA_PULSE_SOUND,
                delay = 28,
            )
            world.soundArea(
                local(run, attack.safeX, attack.safeZ),
                RoyalTitansAssets.AREA_PULSE_SOUND,
                delay = 88,
            )
            world.soundArea(
                local(run, attack.safeX, attack.safeZ),
                RoyalTitansAssets.AREA_PULSE_SOUND,
                delay = 148,
            )
            return
        }
        if (elapsed !in setOf(1, 3, 5)) return
        if (elapsed == 1) removeAreaTimer(run, attack)
        for (x in 29..33) {
            for (z in 28..34) {
                if (RoyalTitansRules.isSafeTile(x, z, attack.safeX, attack.safeZ)) continue
                val element =
                    when (run.stage) {
                        RoyalTitansStage.FINAL_BRANDA -> RoyalElement.FIRE
                        RoyalTitansStage.FINAL_ELDRIC -> RoyalElement.ICE
                        else -> if (z > attack.safeZ) RoyalElement.FIRE else RoyalElement.ICE
                    }
                world.spotanimMap(
                    SpotanimType(
                        if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_AREA_PROJECTILE
                        else RoyalTitansAssets.ICE_AREA_PROJECTILE
                    ),
                    local(run, x, z),
                )
            }
        }
        online(run)
            .filter {
                inArena(run, it.coords) &&
                    !RoyalTitansRules.isSafeTile(
                        localX(run, it.coords),
                        localZ(run, it.coords),
                        attack.safeX,
                        attack.safeZ,
                    )
            }
            .forEach {
                hit(
                    run,
                    it,
                    random.of(RoyalTitansRules.AREA_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                )
            }
        if (elapsed == 5) endAreaAttack(run, attack)
    }

    private fun removeAreaTimer(run: Run, attack: AreaAttack) {
        if (!attack.timer.isSlotAssigned) return
        npcRuns.remove(attack.timer)
        npcs.del(attack.timer, Int.MAX_VALUE)
    }

    private fun endAreaAttack(run: Run, attack: AreaAttack) {
        if (run.areaAttack !== attack) return
        locs.del(attack.safeLoc, Int.MAX_VALUE)
        removeAreaTimer(run, attack)
        run.areaAttack = null
        run.rangedGrace = RoyalTitansRules.AREA_GRACE_TICKS
    }

    private fun stomp(
        run: Run,
        titan: Npc,
        element: RoyalElement,
        arenaWide: Boolean =
            run.stage in
                setOf(
                    RoyalTitansStage.ARENA,
                    RoyalTitansStage.FINAL_BRANDA,
                    RoyalTitansStage.FINAL_ELDRIC,
                ),
    ) {
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP
                else RoyalTitansAssets.ELDRIC_STOMP
            )
        )
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_SOUND,
        )
        val tiles =
            if (arenaWide) {
                (29..33).flatMap { x -> (28..34).map { z -> local(run, x, z) } }
            } else {
                val zRange = titan.coords.z..(titan.coords.z + 2)
                val xRange =
                    if (element == RoyalElement.FIRE) (titan.coords.x + 3)..(titan.coords.x + 7)
                    else (titan.coords.x - 5)..(titan.coords.x - 1)
                xRange.flatMap { x -> zRange.map { z -> CoordGrid(x, z, titan.coords.level) } }
            }
        tiles.forEach { tile ->
            val column =
                if (arenaWide) {
                    if (element == RoyalElement.FIRE) tiles.maxOf { it.z } - tile.z
                    else tile.z - tiles.minOf { it.z }
                } else {
                    if (element == RoyalElement.FIRE) tile.x - tiles.minOf { it.x }
                    else tiles.maxOf { it.x } - tile.x
                }
            world.spotanimMap(
                SpotanimType(RoyalTitansAssets.STOMP_TILE_GFX),
                tile,
                delay = (column * 2).coerceAtLeast(0),
            )
        }
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_IMPACT_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_IMPACT_SOUND,
            delay = 1,
        )
        val final = run.stage in setOf(RoyalTitansStage.FINAL_BRANDA, RoyalTitansStage.FINAL_ELDRIC)
        online(run)
            .filter { it.coords in tiles }
            .forEach { player ->
                val protected = player.vars["varbit.prayer_protectfrommelee"] == 1
                val roll = random.of(RoyalTitansRules.STOMP_MAX_HIT + 1)
                val damage =
                    if (!final && protected) 0
                    else RoyalTitansRules.finalStompDamage(roll, protected)
                hit(run, player, damage, HitType.Melee, bypassPrayer = true, source = titan)
            }
    }

    fun attackTitan(access: org.rsmod.api.npc.access.StandardNpcAccess, target: Player) {
        val run = npcRuns[access.npc] ?: return
        if (target.accountHash !in run.participants) return
        run.lastBossTarget[target.accountHash] = access.npc
        bossHpBar.onOpen(target, access.npc)
        bossHpBar.onUpdate(target, access.npc, access.npc.hitpoints, ROYAL_TITANS_MAX_HITPOINTS)
        if (
            run.stage in
                setOf(
                    RoyalTitansStage.ARENA,
                    RoyalTitansStage.FINAL_BRANDA,
                    RoyalTitansStage.FINAL_ELDRIC,
                )
        ) {
            access.actionDelay = clock.cycle + 7
            return
        }
        access.actionDelay = clock.cycle + 7
    }

    fun attackElemental(access: org.rsmod.api.npc.access.StandardNpcAccess, target: Player) {
        val run = npcRuns[access.npc] ?: return
        val state = run.elementals[access.npc] ?: return
        if (target.accountHash !in run.participants) return
        access.npc.anim(anim(if (state.element == RoyalElement.FIRE) 11947 else 11950))
        target.queueHit(
            source = access.npc,
            delay = 1,
            type = HitType.Melee,
            damage = random.of(5),
            modifier = StandardPlayerHitModifier,
        )
        access.actionDelay = clock.cycle + 4
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = npcRuns[npc]
        if (run == null || player.accountHash !in run.participants) {
            hit.damage = 0
            return
        }
        run.walls
            .firstOrNull { it.hitbox === npc }
            ?.let { wall ->
                val spell = hit.secondaryType()
                if (
                    spell != null &&
                        RoyalTitansRules.attackableWall(wall.element, spell.internalName)
                ) {
                    clearWallTriplet(run, wall)
                    player.actionDelay = clock.cycle
                } else {
                    player.mes(
                        if (wall.element == RoyalElement.FIRE)
                            "Use Water Blast or stronger, or Ancient ice magic, to douse this wall."
                        else "Use Fire Blast or stronger to melt these icicles."
                    )
                }
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
                val magicBonus = wornBonuses.offensiveMagicBonus(player)
                val voidMage = isVoidMage(player)
                val landed =
                    random.randomDouble() <
                        RoyalTitansRules.elementalMagicHitChance(magicBonus, voidMage)
                val baseMax = RoyalTitansRules.elementalSpellBaseMaxHit(spell.internalName)
                hit.damage =
                    when {
                        !landed -> 0
                        RoyalTitansRules.elementalGuaranteedMax(magicBonus, voidMage) &&
                            baseMax != null -> (baseMax * 2).coerceAtMost(npc.hitpoints)
                        hit.damage > 0 -> hit.damage.coerceAtMost(npc.hitpoints)
                        baseMax != null -> (random.of(baseMax + 1) * 2).coerceAtMost(npc.hitpoints)
                        else -> 0
                    }
                if (hit.damage > 0)
                    splashElementals(run, player, npc, elemental.element, hit.damage)
            }
            val previousImpact = hit.impactHandler
            hit.impactHandler = HitImpactHandler { impact ->
                previousImpact?.onImpact(impact)
                if (impact.damage > 0 && npc.hitpoints <= 0) onElementalKilled(run, player)
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
        if (
            run.stage in
                setOf(RoyalTitansStage.RANGED, RoyalTitansStage.MELEE, RoyalTitansStage.INTRO)
        ) {
            hit.damage =
                RoyalTitansRules.capTransitionDamage(
                    npc.hitpoints,
                    hit.damage,
                    transitioned = false,
                )
        }
        run.lastBossTarget[player.accountHash] = npc
        bossHpBar.onOpen(player, npc)
        bossHpBar.onUpdate(
            player,
            npc,
            (npc.hitpoints - hit.damage).coerceAtLeast(0),
            ROYAL_TITANS_MAX_HITPOINTS,
        )
        if (hit.damage > 0) {
            run.contribution[player.accountHash] =
                (run.contribution[player.accountHash] ?: 0) + hit.damage
        }
    }

    private fun isVoidMage(player: Player): Boolean =
        EquipmentChecks.isVoidMageHelm(player.hat) &&
            EquipmentChecks.isVoidTop(player.torso) &&
            EquipmentChecks.isVoidRobe(player.legs) &&
            EquipmentChecks.isVoidGloves(player.hands)

    private fun onElementalKilled(run: Run, player: Player) {
        if (run.elementalKillCycle != clock.cycle) {
            run.elementalKillCycle = clock.cycle
            run.sameTickElementalKills = 0
        }
        run.sameTickElementalKills++
        val remaining = RoyalTitansRules.elementalKillAttackDelay(run.sameTickElementalKills)
        player.actionDelay = minOf(player.actionDelay, clock.cycle + remaining)
    }

    private fun splashElementals(
        run: Run,
        player: Player,
        primary: Npc,
        element: RoyalElement,
        damage: Int,
    ) {
        if (damage <= 0) return
        run.elementals.entries
            .filter {
                it.key !== primary &&
                    it.value.element == element &&
                    distance(it.key.coords, primary.coords) <= 1
            }
            .forEach { (npc, _) ->
                val splash = damage.coerceAtMost(npc.hitpoints)
                npc.queueHit(
                    source = player,
                    delay = RoyalTitansRules.ELEMENTAL_SPLASH_HIT_DELAY_TICKS,
                    type = HitType.Magic,
                    damage = splash,
                    modifier = npcHitModifier,
                    impactHandler =
                        HitImpactHandler { impact ->
                            if (impact.damage > 0 && npc.hitpoints <= 0)
                                onElementalKilled(run, player)
                        },
                )
            }
    }

    fun beginTitanDeath(npc: Npc): Run? {
        val run = npcRuns[npc] ?: return null
        if (npc !== run.branda && npc !== run.eldric) return null
        if (run.dyingTitans.add(npc)) {
            npc.clearInteraction()
            npc.movementLocked = true
        }
        return run
    }

    fun finishTitanDeath(npc: Npc) {
        val run = npcRuns.remove(npc) ?: return
        if (!run.dyingTitans.remove(npc)) return
        online(run).forEach { player ->
            if (run.lastBossTarget[player.accountHash] === npc)
                bossHpBar.onClose(player, npc, instant = true)
        }
        val element = if (npc === run.branda) RoyalElement.FIRE else RoyalElement.ICE
        if (element == RoyalElement.FIRE) run.branda = null else run.eldric = null
        val corpse =
            spawnNpc(
                run,
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_CORPSE
                else RoyalTitansAssets.ELDRIC_CORPSE,
                if (element == RoyalElement.FIRE) local(run, 30, 35) else local(run, 30, 25),
                hitpoints = 1,
                movementLocked = true,
            )
        run.corpses[corpse] = element
        if (element == RoyalElement.FIRE) {
            run.brandaCorpse = corpse
            replaceArenaFloors(run, RoyalElement.FIRE)
        } else {
            run.eldricCorpse = corpse
            replaceArenaFloors(run, RoyalElement.ICE)
        }
        run.areaAttack?.let { endAreaAttack(run, it) }
        when {
            run.branda != null && run.eldric == null -> setStage(run, RoyalTitansStage.FINAL_BRANDA)
            run.eldric != null && run.branda == null -> setStage(run, RoyalTitansStage.FINAL_ELDRIC)
            run.branda == null && run.eldric == null -> complete(run)
        }
    }

    fun finishElementalDeath(npc: Npc) {
        val run = npcRuns.remove(npc) ?: return
        val state = run.elementals.remove(npc) ?: return
        npc.anim(
            anim(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH
            )
        )
        npc.spotanim(
            spot(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH_GFX
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH_GFX
            )
        )
    }

    private fun complete(run: Run) {
        clearWalls(run)
        clearElementals(run)
        setStage(run, RoyalTitansStage.COMPLETE)
        online(run).forEach {
            it.combatClearQueue()
            it.clearInteraction()
            run.lastBossTarget.remove(it.accountHash)?.let { target ->
                bossHpBar.onClose(it, target, instant = true)
            }
            it.mes("The Royal Titans will reinvigorate themselves in 18 seconds.")
            it.mes("Choose Loot, Sacrifice, or Take pages from either defeated Titan.")
        }
    }

    fun claim(player: Player, corpse: Npc, choice: ClaimChoice) {
        val run = npcRuns[corpse] ?: return player.mes("That Titan is no longer available.")
        val element = run.corpses[corpse] ?: return player.mes("That Titan cannot be claimed.")
        if (run.stage != RoyalTitansStage.COMPLETE)
            return player.mes("Defeat both Titans before claiming a reward.")
        if ((run.contribution[player.accountHash] ?: 0) <= 0)
            return player.mes("You did not contribute to this victory.")
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
        npcRuns[corpse]?.corpses?.get(corpse) ?: RoyalElement.FIRE

    private fun giveDrop(player: Player, drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        objects.add(obj, player.coords, 200, player, drop.rollCount(random))
    }

    fun finishAuxiliaryDeath(npc: Npc) {
        val run = npcRuns[npc] ?: return
        if (npc in run.elementals) finishElementalDeath(npc)
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = npcRuns[npc] ?: return "That creature is not part of this encounter."
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

    fun isEncounterNpc(npc: Npc): Boolean = npcRuns.containsKey(npc)

    fun isParticipant(player: Player, npc: Npc): Boolean =
        npcRuns[npc]?.participants?.containsKey(player.accountHash) == true

    fun isActive(player: Player): Boolean = participantRuns.containsKey(player.accountHash)

    fun status(player: Player): String {
        val run = participantRuns[player.accountHash] ?: return "Royal Titans: no active encounter."
        return "Royal Titans ${run.stage}: Branda ${run.branda?.hitpoints ?: 0}/600, Eldric ${run.eldric?.hitpoints ?: 0}/600, walls=${run.walls.size}, elementals=${run.elementals.size}, contribution=${run.contribution[player.accountHash] ?: 0}."
    }

    private fun spawnTitans(run: Run) {
        run.branda = spawnTitan(run, RoyalTitansAssets.BRANDA_ACTIVE, ROYAL_TITANS_BRANDA_SPAWN)
        run.eldric = null
        run.eldricSpawnCycle = clock.cycle + 2
    }

    private fun spawnTitan(run: Run, id: Int, local: RegionLocal): Npc {
        val tile = requireNotNull(instances.localCoord(run.session, local))
        val npc = spawnNpc(run, id, tile, ROYAL_TITANS_MAX_HITPOINTS)
        npc.apRangeOverride = 20
        npc.maxOpRangeOverride = 20
        return npc
    }

    private fun spawnNpc(
        run: Run,
        id: Int,
        tile: CoordGrid,
        hitpoints: Int,
        movementLocked: Boolean = false,
    ): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.mode = null
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npc.movementLocked = movementLocked
        npcs.add(npc, ROYAL_TITANS_NPC_LIFETIME)
        instances.attachNpc(run.session.id, npc)
        npcRuns[npc] = run
        return npc
    }

    private fun keepAggressive(run: Run) {
        val targets = online(run).filter { it.hitpoints > 0 }
        if (targets.isEmpty()) return
        listOfNotNull(run.branda, run.eldric).forEachIndexed { index, npc ->
            if (run.stage == RoyalTitansStage.MELEE && npc.mode == null) {
                npc.apPlayer2(targets[index % targets.size], interactions)
            }
        }
    }

    private fun harmlessInterTitanCast(run: Run) {
        val branda = run.branda ?: return
        val eldric = run.eldric ?: return
        branda.faceSquare(eldric.coords)
        eldric.faceSquare(branda.coords)
        branda.anim(anim(RoyalTitansAssets.BRANDA_WALL_CAST))
        eldric.anim(anim(RoyalTitansAssets.ELDRIC_WALL_CAST))
        world.projAnim(projectile(branda, eldric, RoyalTitansAssets.FIRE_AREA_PROJECTILE))
        world.projAnim(projectile(eldric, branda, RoyalTitansAssets.ICE_AREA_PROJECTILE))
    }

    private fun projectile(source: Npc, target: Npc, graphic: Int) =
        ProjAnim(
            spotanim = graphic,
            startHeight = 80,
            endHeight = 80,
            startTime = 0,
            endTime = 90,
            angle = 30,
            progress = 0,
            sourceIndex = source.slotId + 1,
            targetIndex = target.slotId + 1,
            startCoord = source.coords,
            endCoord = target.coords,
        )

    private fun retreat(npc: Npc?, destination: CoordGrid) {
        npc ?: return
        npc.clearInteraction()
        npc.movementLocked = false
        npc.anim(
            anim(
                if (npc.id == RoyalTitansAssets.BRANDA_ACTIVE) RoyalTitansAssets.BRANDA_WALK
                else RoyalTitansAssets.ELDRIC_WALK
            )
        )
        npc.walk(destination)
    }

    private fun advance(npc: Npc?, destination: CoordGrid) {
        npc ?: return
        npc.movementLocked = false
        npc.walk(destination)
    }

    private fun spawnBrandaArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_FLOOR_WEST)
        run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_FLOOR_EAST)
    }

    private fun spawnEldricArenaFloors(run: Run) {
        run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_FLOOR_WEST)
        run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_FLOOR_EAST)
    }

    private fun replaceArenaFloors(run: Run, element: RoyalElement) {
        val relevant =
            run.arenaFloors.filter {
                if (element == RoyalElement.FIRE) localZ(run, it.coords) == 35
                else localZ(run, it.coords) == 25
            }
        relevant.forEach {
            locs.del(it, Int.MAX_VALUE)
            run.arenaFloors.remove(it)
        }
        if (element == RoyalElement.FIRE) {
            run.arenaFloors += spawnLoc(run, 29, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 35, RoyalTitansAssets.BRANDA_DEATH_FLOOR_EAST)
        } else {
            run.arenaFloors += spawnLoc(run, 29, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_WEST)
            run.arenaFloors += spawnLoc(run, 33, 25, RoyalTitansAssets.ELDRIC_DEATH_FLOOR_EAST)
        }
    }

    private fun spawnLoc(run: Run, x: Int, z: Int, id: Int): LocInfo =
        locs.add(
            local(run, x, z),
            requireLoc(id),
            Int.MAX_VALUE,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )

    private fun clearArenaFloors(run: Run) {
        run.arenaFloors.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.arenaFloors.clear()
    }

    private fun setStage(run: Run, stage: RoyalTitansStage) {
        run.stage = stage
        run.stageTick = -1
    }

    private fun reset(run: Run) {
        clearWalls(run)
        clearElementals(run)
        run.areaAttack?.let { endAreaAttack(run, it) }
        run.corpses.keys.toList().forEach { removeNpc(run, it) }
        run.corpses.clear()
        run.brandaCorpse = null
        run.eldricCorpse = null
        clearArenaFloors(run)
        run.claimed.clear()
        run.contribution.clear()
        run.elementalsUnlocked = false
        run.areaSequence = 0
        spawnTitans(run)
        setStage(run, RoyalTitansStage.INTRO)
        online(run).forEach { it.mes("The Royal Titans reinvigorate themselves.") }
    }

    private fun clearWalls(run: Run) {
        run.pendingWalls.clear()
        run.walls.toList().forEach { clearWall(run, it) }
    }

    private fun clearElementals(run: Run) {
        run.pendingSummons.clear()
        run.elementals.keys.toList().forEach { removeNpc(run, it) }
        run.elementals.clear()
    }

    private fun removeNpc(run: Run, npc: Npc) {
        npcRuns.remove(npc)
        run.elementals.remove(npc)
        run.corpses.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun abort(run: Run, reason: String) {
        runs.remove(run.session.id)
        run.areaAttack?.let { endAreaAttack(run, it) }
        clearWalls(run)
        clearElementals(run)
        clearArenaFloors(run)
        (listOfNotNull(run.branda, run.eldric, run.brandaCorpse, run.eldricCorpse) +
                run.corpses.keys)
            .distinct()
            .forEach { removeNpc(run, it) }
        online(run).forEach { player ->
            run.lastBossTarget[player.accountHash]?.let { target ->
                bossHpBar.onClose(player, target, instant = true)
            }
        }
        run.participants.keys.forEach(participantRuns::remove)
        instances.terminate(run.session)
        log.info { "[RoyalTitans] generation=${run.generation} abort=$reason" }
    }

    private fun hit(
        run: Run,
        player: Player,
        damage: Int,
        type: HitType,
        bypassPrayer: Boolean,
        source: Npc? = null,
    ) {
        val attacker = source ?: run.branda ?: run.eldric ?: return
        player.queueHit(
            source = attacker,
            delay = 1,
            type = type,
            damage = damage.coerceAtLeast(0),
            modifier =
                if (bypassPrayer) NpcBypassProtectionPrayerPlayerHitModifier
                else StandardPlayerHitModifier,
        )
    }

    private fun canTravel(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Royal Titans' arena.")
            return false
        }
        if (
            participantRuns.containsKey(player.accountHash) ||
                instances.sessionForPlayer(player) != null
        ) {
            player.mes("You already have an active instance.")
            return false
        }
        return true
    }

    private fun targetFor(run: Run, npc: Npc): Player? =
        online(run).minByOrNull { distance(it.coords, npc.coords) }

    private fun online(run: Run): List<Player> =
        run.participants.values
            .mapNotNull { participant ->
                participant.uid.resolve(players)
                    ?: players.firstOrNull {
                        it.accountHash == participant.accountHash && it.isSlotAssigned
                    }
            }
            .filter { it.isSlotAssigned && !it.pendingLogout && !it.loggingOut }

    private fun inArena(run: Run, tile: CoordGrid): Boolean {
        val sw = local(run, 29, 28)
        val ne = local(run, 33, 34)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    private fun inTitanLanding(run: Run, tile: CoordGrid, element: RoyalElement): Boolean {
        val x = localX(run, tile)
        val z = localZ(run, tile)
        return x in 29..33 &&
            when (element) {
                RoyalElement.FIRE -> z in 35..37
                RoyalElement.ICE -> z in 25..27
            }
    }

    private fun local(run: Run, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(run.session, RegionLocal(0, 45, 149, x, z)))

    private fun localX(run: Run, tile: CoordGrid): Int = tile.x - local(run, 0, 0).x

    private fun localZ(run: Run, tile: CoordGrid): Int = tile.z - local(run, 0, 0).z

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 Royal Titans NPC $id"
        }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing revision-240 Royal Titans location $id"
        }

    private fun anim(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) {
            "Missing Royal Titans sequence $id"
        }

    private fun spot(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing Royal Titans spotanim $id"
        }

    internal data class Participant(val accountHash: Long, var uid: PlayerUid)

    internal data class Run(
        val generation: Long,
        val session: InstanceSession,
        val ownerHash: Long,
        val participants: MutableMap<Long, Participant>,
        var branda: Npc? = null,
        var eldric: Npc? = null,
        var brandaCorpse: Npc? = null,
        var eldricCorpse: Npc? = null,
        var stage: RoyalTitansStage = RoyalTitansStage.INTRO,
        var stageTick: Int = 0,
        var elementalsUnlocked: Boolean = false,
        var areaSequence: Int = 0,
        var packSequence: Int = 0,
        var eldricSpawnCycle: Int = Int.MAX_VALUE,
        var sameTickElementalKills: Int = 0,
        var elementalKillCycle: Int = -1,
        var areaAttack: AreaAttack? = null,
        var rangedGrace: Int = 0,
        val contribution: MutableMap<Long, Int> = mutableMapOf(),
        val claimed: MutableSet<Long> = mutableSetOf(),
        val lastBossTarget: MutableMap<Long, Npc> = mutableMapOf(),
        val walls: MutableList<WallSegment> = mutableListOf(),
        val pendingWalls: MutableList<PendingWall> = mutableListOf(),
        val elementals: MutableMap<Npc, ElementalState> = mutableMapOf(),
        val pendingSummons: MutableList<PendingSummon> = mutableListOf(),
        val corpses: MutableMap<Npc, RoyalElement> = mutableMapOf(),
        val dyingTitans: MutableSet<Npc> = mutableSetOf(),
        val arenaFloors: MutableList<LocInfo> = mutableListOf(),
    )

    internal data class PendingWall(val formCycle: Int, val element: RoyalElement, val row: Int)

    internal data class WallSegment(
        val element: RoyalElement,
        val row: Int,
        val index: Int,
        val loc: LocInfo,
        val hitbox: Npc,
        val expireCycle: Int,
    )

    internal data class PendingSummon(val spawnCycle: Int, val element: RoyalElement)

    internal data class ElementalState(val element: RoyalElement, val explodeCycle: Int)

    internal data class AreaAttack(
        val safeX: Int,
        val safeZ: Int,
        val impactCycle: Int,
        val safeLoc: LocInfo,
        val timer: Npc,
    )
}

internal enum class ClaimChoice {
    LOOT,
    SACRIFICE,
    PAGES,
}
