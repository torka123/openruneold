package org.rsmod.content.minigames.fightcaves

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceRegionRotation
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

private val FIGHT_CAVE_COMPLETIONS =
    AttributeKey<Int>(persistenceKey = "fight_cave_collection_log_completions")

@Singleton
internal class FightCaveRunManager
@Inject
constructor(
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val objs: ObjRepository,
    private val collision: CollisionFlagMap,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var nextGeneration = 1L

    fun teleportToEntrance(player: Player): Boolean {
        runs[player.uid]?.let { abort(player, "admin entrance teleport", teleport = true) }
        val current = instances.sessionForPlayer(player)
        if (current != null && current.key != FIGHT_CAVE_INSTANCE_KEY) {
            player.mes("Leave your current instance before teleporting to the Fight Caves.")
            return false
        }
        if (current != null) {
            instances.leave(player, current, clock.cycle)
        }
        prepareTeleport(player)
        PathingEntityCommon.telejump(player, collision, FIGHT_CAVE_OUTSIDE)
        player.faceSquare(FIGHT_CAVE_ENTRANCE_OBJECT)
        player.mes(
            "You have been teleported outside the TzHaar Fight Cave. Enter the cave to begin."
        )
        return true
    }

    fun startFromEntrance(player: Player): Boolean {
        if (player.stat("stat.hitpoints") <= 0) {
            player.mes("You cannot enter the Fight Caves while dead.")
            return false
        }
        if (player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Fight Caves.")
            return false
        }
        if (runs.containsKey(player.uid)) {
            player.mes("You are already in the Fight Caves.")
            return false
        }
        if (instances.sessionForPlayer(player) != null) {
            player.mes("Leave your current instance before entering the Fight Caves.")
            return false
        }
        val invalid = firstInvalidDefinition()
        if (invalid != null) {
            log.error { "[FightCave] startup rejected missing=$invalid" }
            player.mes("The Fight Caves are temporarily unavailable: $invalid is missing.")
            return false
        }

        val result =
            instances.create(
                owner = player,
                key = FIGHT_CAVE_INSTANCE_KEY,
                spec = INSTANCE_SPEC,
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            val reason =
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Fight Caves instance could not be created."
            player.mes(reason)
            return false
        }

        prepareTeleport(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val progress = FightCaveProgress(nextGeneration++)
        val run =
            Run(
                player = player,
                instance = result.session,
                instanceEntry = result.enter,
                progress = progress,
                countdownEnd = clock.cycle + FIGHT_CAVE_COUNTDOWN_TICKS,
            )
        runs[player.uid] = run
        player.mes("<col=ff9900>The Fight Caves will begin in 10 seconds.</col>")
        log.info {
            "[FightCave] player=${player.displayName} instance=${result.session.id} " +
                "generation=${progress.generation} rotation=${FIGHT_CAVE_ROTATION.name} countdown=10s"
        }
        return true
    }

    fun exit(player: Player): Boolean {
        val run = runs[player.uid]
        if (run == null) {
            player.mes("You are not in an active Fight Caves run.")
            return false
        }
        abort(player, "used cave exit", teleport = true)
        player.mes("You leave the Fight Caves. Your progress has been lost.")
        return true
    }

    fun isActive(player: Player): Boolean = runs[player.uid]?.progress?.isTerminal == false

    fun isFightCaveNpc(npc: Npc): Boolean = findRun(npc) != null

    fun isOwnedFightCaveNpc(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        return npc.isSpawnOwnedBy(player) && run.owns(npc)
    }

    fun beforeNpcDeath(npc: Npc) {
        val run = findRun(npc) ?: return
        val monster = run.active[npc] ?: return
        if (monster != FightCaveMonster.TzKek || !run.preparedSplits.add(npc)) return

        val children = splitChildCoords(npc.coords)
        for (coords in children) {
            spawnAt(run, FightCaveMonster.TzKekSpawn, coords, active = true, aggressive = true)
        }
        log.info {
            "[FightCave] player=${run.player.displayName} wave=${run.wave.displayNumber}/7 " +
                "Tz-Kek split childrenAdded=${children.size} beforeParentRemoval=true"
        }
    }

    fun afterNpcDeath(npc: Npc) {
        val run = findRun(npc) ?: return
        if (run.healers.remove(npc)) {
            run.interruptedHealers.remove(npc)
            return
        }
        val monster = run.active.remove(npc) ?: return
        run.preparedSplits.remove(npc)
        log.info {
            "[FightCave] player=${run.player.displayName} wave=${run.wave.displayNumber}/7 " +
                "npcDeath=${monster.displayName} remaining=${run.active.size}"
        }
        if (monster == FightCaveMonster.TzTokJad) {
            complete(run)
            return
        }
        if (run.active.isEmpty() && run.progress.finishWave()) {
            run.transitionCycle = clock.cycle + FIGHT_CAVE_INTERMISSION_TICKS
            run.player.mes(
                "<col=ff9900>Wave ${run.wave.displayNumber}/7 complete. Next wave in 10 seconds.</col>"
            )
            log.info {
                "[FightCave] player=${run.player.displayName} wave=${run.wave.displayNumber}/7 complete"
            }
        }
    }

    fun interruptHealer(player: Player, healer: Npc) {
        val run = runs[player.uid] ?: return
        if (healer !in run.healers || !healer.isSpawnOwnedBy(player)) return
        if (run.interruptedHealers.add(healer)) {
            healer.opPlayer2(player, interactions)
            log.info {
                "[FightCave] player=${player.displayName} healerPulled=${healer.uid} " +
                    "interrupted=${run.interruptedHealers.size}/4"
            }
        }
    }

    fun tryMejKotHeal(mejKot: Npc) {
        val run = findRun(mejKot) ?: return
        if (run.active[mejKot] != FightCaveMonster.YtMejKot) return
        val last = run.lastMejHealCycle[mejKot] ?: Int.MIN_VALUE
        if (clock.cycle - last < MEJ_HEAL_INTERVAL) return
        val target =
            run.active.keys
                .asSequence()
                .filter { it.isVisible && it.hitpoints > 0 }
                .filter { it.hitpoints * 2 < it.baseHitpointsLvl }
                .filter { it === mejKot || mejKot.distanceTo(it) <= MEJ_HEAL_RANGE }
                .maxByOrNull { it.baseHitpointsLvl - it.hitpoints } ?: return
        run.lastMejHealCycle[mejKot] = clock.cycle
        mejKot.anim("seq.lizard_cleric_heal")
        target.spotanim(TZHAAR_HEAL_SPOTANIM)
        val amount = target.heal(MEJ_HEAL_AMOUNT, showHitsplat = true)
        if (amount > 0) {
            log.info {
                "[FightCave] player=${run.player.displayName} Yt-MejKot healed=${target.id} amount=$amount"
            }
        }
    }

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (run.progress.isTerminal) return
        val currentInstance = instances.sessionForPlayer(player)
        if (currentInstance?.id != run.instance.id) {
            abort(
                player,
                "instance membership changed coords=${player.coords.toConventionalString()} " +
                    "expected=${run.instance.id} actual=${currentInstance?.id ?: "none"}",
                teleport = true,
            )
            return
        }
        val unexpected =
            (run.active.keys + run.healers).firstOrNull {
                it.isSlotAssigned && !it.isVisible && it.hitpoints > 0
            }
        if (unexpected != null) {
            player.mes(
                "The Fight Caves run was stopped because a monster disappeared unexpectedly."
            )
            abort(player, "unexpected npc removal id=${unexpected.id}", teleport = true)
            return
        }

        when (run.progress.state) {
            FightCaveRunState.COUNTDOWN -> {
                if (clock.cycle >= run.countdownEnd && run.progress.beginWave()) {
                    if (!spawnWaveSafely(run)) return
                }
            }
            FightCaveRunState.BETWEEN_WAVES -> {
                val transition = run.transitionCycle
                if (transition != null && clock.cycle >= transition && run.progress.advanceWave()) {
                    run.transitionCycle = null
                    if (!spawnWaveSafely(run)) return
                }
            }
            FightCaveRunState.JAD_PHASE -> tickJad(run)
            else -> Unit
        }
        tickAggression(run)
        tickHealers(run)
    }

    fun handleLogout(player: Player) {
        abort(player, "logout", teleport = false)
    }

    fun handleDeath(player: Player) {
        abort(player, "death", teleport = false)
    }

    fun debugStatus(player: Player): String {
        val run = runs[player.uid] ?: return "Fight Caves: no active run."
        val active =
            run.active.entries.joinToString(prefix = "[", postfix = "]") { (npc, monster) ->
                "${monster.displayName}(${npc.id})"
            }
        val jad = run.active.entries.firstOrNull { it.value == FightCaveMonster.TzTokJad }?.key
        val healerIds = run.healers.joinToString(prefix = "[", postfix = "]") { it.id.toString() }
        val wave = run.wave
        val seconds =
            when (run.progress.state) {
                FightCaveRunState.COUNTDOWN -> ticksToSeconds(run.countdownEnd - clock.cycle)
                FightCaveRunState.BETWEEN_WAVES ->
                    ticksToSeconds((run.transitionCycle ?: clock.cycle) - clock.cycle)
                else -> 0
            }
        return "Fight Caves session=${run.progress.generation} instance=${run.instance.id} " +
            "state=${run.progress.state} rotation=${run.rotation.name} age=${clock.cycle - run.startCycle}t " +
            "wave=${wave.displayNumber}/7 (OSRS ${wave.osrsNumber}) countdown=${seconds}s " +
            "active=${run.active.size}$active jad=${jad?.id ?: "none"}:${jad?.hitpoints ?: 0}/${jad?.baseHitpointsLvl ?: 0} " +
            "healers=${run.healers.size}$healerIds spawned=${run.healersSpawned} " +
            "orangeTile=${run.orangeJadSpawn ?: "not saved"}"
    }

    fun forceWave(player: Player, displayWave: Int): Boolean {
        val run = runs[player.uid]
        if (run == null) {
            player.mes("Enter the Fight Caves before using the wave command.")
            return false
        }
        val index = displayWave - 1
        if (index !in FIGHT_CAVE_WAVES.indices) return false
        clearNpcs(run)
        run.healersSpawned = false
        run.interruptedHealers.clear()
        run.lastJadHitpoints = 0
        run.transitionCycle = null
        if (index == FIGHT_CAVE_WAVES.lastIndex && run.orangeJadSpawn == null) {
            run.orangeJadSpawn =
                surroundingSpawnCandidates(
                        player = run.player.coords,
                        npcSize = FightCaveMonster.TzTokJad.footprintSize,
                        preferred = FightCaveSpawnPoint.South,
                    )
                    .firstOrNull { run.canSpawn(it, FightCaveMonster.TzTokJad.footprintSize) }
            checkNotNull(run.orangeJadSpawn) { "No player-centered debug spawn tile for Jad." }
            log.info {
                "[FightCave] player=${player.displayName} debug wave 7 used player-centered Jad tile"
            }
        }
        if (!run.progress.forceWave(index)) return false
        if (!spawnWaveSafely(run)) return false
        log.info { "[FightCave] player=${player.displayName} adminForceWave=$displayWave" }
        return true
    }

    fun abort(player: Player, reason: String, teleport: Boolean) {
        val run = runs.remove(player.uid) ?: return
        run.progress.abort()
        clearNpcs(run)
        if (teleport) {
            prepareTeleport(player)
            if (instances.sessionForPlayer(player) === run.instance) {
                instances.leave(player, run.instance, clock.cycle)
            }
            PathingEntityCommon.telejump(player, collision, FIGHT_CAVE_OUTSIDE)
        }
        log.info {
            "[FightCave] player=${player.displayName} instance=${run.instance.id} " +
                "generation=${run.progress.generation} aborted reason=$reason"
        }
    }

    private fun spawnWaveSafely(run: Run): Boolean =
        try {
            spawnWave(run)
            true
        } catch (error: Exception) {
            log.error(error) {
                "[FightCave] player=${run.player.displayName} instance=${run.instance.id} " +
                    "wave=${run.wave.displayNumber}/7 failed to start; run rolled back"
            }
            run.player.mes(
                "The Fight Caves wave could not start. Your run was safely reset; please try again."
            )
            abort(run.player, "wave ${run.wave.displayNumber} spawn failure", teleport = true)
            false
        }

    private fun spawnWave(run: Run) {
        check(run.active.isEmpty() && run.healers.isEmpty()) {
            "Fight Cave wave started with existing NPCs: active=${run.active.size}, healers=${run.healers.size}"
        }
        val wave = run.wave
        for (spawn in wave.spawns) {
            val preferred =
                if (spawn.monster == FightCaveMonster.TzTokJad) {
                    listOf(
                        checkNotNull(run.orangeJadSpawn) {
                            "Jad spawn tile was not recorded from Wave 6."
                        }
                    )
                } else {
                    surroundingSpawnCandidates(
                        player = run.player.coords,
                        npcSize = spawn.monster.footprintSize,
                        preferred = spawn.point,
                    )
                }
            val npc =
                spawnValidated(run, spawn.monster, preferred, active = true, aggressive = true)
            if (spawn.monster == FightCaveMonster.KetZekOrange) {
                run.orangeJadSpawn = npc.coords
                log.info {
                    "[FightCave] player=${run.player.displayName} orangeKetTile=${npc.coords} savedForJad=true"
                }
            }
        }
        run.lastJadHitpoints =
            run.active.entries.firstOrNull { it.value == FightCaveMonster.TzTokJad }?.key?.hitpoints
                ?: 0
        run.player.mes(
            "<col=ff9900>Fight Caves - Wave ${wave.displayNumber}/7 " +
                "(OSRS Wave ${wave.osrsNumber})</col>"
        )
        log.info {
            "[FightCave] player=${run.player.displayName} instance=${run.instance.id} " +
                "wave=${wave.displayNumber}/7 osrs=${wave.osrsNumber} spawned=${run.active.size} " +
                "formation=surround-player center=${run.player.coords}"
        }
    }

    private fun tickJad(run: Run) {
        val jad =
            run.active.entries.firstOrNull { it.value == FightCaveMonster.TzTokJad }?.key ?: return
        val previous = run.lastJadHitpoints.takeIf { it > 0 } ?: jad.baseHitpointsLvl
        if (
            !run.healersSpawned && crossedHalfHealth(previous, jad.hitpoints, jad.baseHitpointsLvl)
        ) {
            spawnJadHealers(run, jad)
        }
        run.lastJadHitpoints = jad.hitpoints
    }

    private fun spawnJadHealers(run: Run, jad: Npc) {
        if (run.healersSpawned) return
        run.healersSpawned = true
        val offsets = listOf(-1 to 0, jad.size to 0, 0 to -1, 0 to jad.size)
        val directions =
            listOf(
                FightCaveSpawnPoint.West,
                FightCaveSpawnPoint.East,
                FightCaveSpawnPoint.South,
                FightCaveSpawnPoint.North,
            )
        for ((index, offset) in offsets.withIndex()) {
            val (dx, dz) = offset
            val preferred =
                listOf(jad.coords.translate(dx, dz)) +
                    surroundingSpawnCandidates(
                        player = jad.coords,
                        npcSize = FightCaveMonster.YtHurKot.footprintSize,
                        preferred = directions[index],
                    )
            spawnValidated(
                run,
                FightCaveMonster.YtHurKot,
                preferred,
                active = false,
                aggressive = false,
            )
        }
        run.nextHealerHealCycle = clock.cycle + HEALER_INTERVAL
        run.player.mes("<col=ff0000>Four Yt-HurKot have appeared to heal TzTok-Jad!</col>")
        log.info {
            "[FightCave] player=${run.player.displayName} Jad thresholdCrossed=${jad.hitpoints}/${jad.baseHitpointsLvl} " +
                "healersSpawned=${run.healers.size} exactlyOnce=true"
        }
    }

    private fun tickHealers(run: Run) {
        if (!run.healersSpawned || clock.cycle < run.nextHealerHealCycle) return
        run.nextHealerHealCycle = clock.cycle + HEALER_INTERVAL
        val jad =
            run.active.entries.firstOrNull { it.value == FightCaveMonster.TzTokJad }?.key ?: return
        if (!jad.isVisible || jad.hitpoints <= 0) return
        for (healer in run.healers.toList()) {
            if (!healer.isVisible || healer.hitpoints <= 0 || healer in run.interruptedHealers)
                continue
            if (healer.distanceTo(jad) > 1) {
                healer.walk(jad.coords)
                continue
            }
            healer.anim("seq.lizard_cleric_heal")
            jad.spotanim(TZHAAR_HEAL_SPOTANIM)
            jad.heal(HEALER_HEAL_AMOUNT, showHitsplat = true)
        }
    }

    private fun spawnValidated(
        run: Run,
        monster: FightCaveMonster,
        preferred: List<CoordGrid>,
        active: Boolean,
        aggressive: Boolean,
    ): Npc {
        val type = npcType(monster)
        val candidates = preferred.distinct()
        val actual = candidates.firstOrNull { run.canSpawn(it, type.size) }
        checkNotNull(actual) {
            "No verified spawn tile for ${monster.displayName} in instance ${run.instance.id}."
        }
        return spawnAt(run, monster, actual, active, aggressive)
    }

    private fun spawnAt(
        run: Run,
        monster: FightCaveMonster,
        coords: CoordGrid,
        active: Boolean,
        aggressive: Boolean,
    ): Npc {
        val type = npcType(monster)
        val npc = Npc(type, coords)
        npc.mode = if (aggressive) type.defaultMode else null
        npc.apRangeOverride = monster.attackRange
        npc.assignSpawnOwner(run.player, clock.cycle)
        npcs.add(npc, NPC_LIFETIME_TICKS)
        instances.attachNpc(run.instance.id, npc)
        if (active) run.active[npc] = monster else run.healers += npc
        if (aggressive) {
            engageOwner(run, npc, monster)
        }
        log.info {
            "[FightCave] player=${run.player.displayName} wave=${run.wave.displayNumber}/7 " +
                "npc=${monster.displayName} uid=${npc.uid} spawn=$coords center=${run.player.coords} " +
                "mode=${npc.mode}"
        }
        return npc
    }

    private fun tickAggression(run: Run) {
        for ((npc, monster) in run.active) {
            if (!npc.isVisible || npc.hitpoints <= 0) continue
            val expectedMode =
                if (monster.attackRange > 1) NpcMode.ApPlayer2 else NpcMode.OpPlayer2
            if (npc.mode != expectedMode) {
                engageOwner(run, npc, monster)
            }
        }
    }

    private fun engageOwner(run: Run, npc: Npc, monster: FightCaveMonster) {
        if (monster.attackRange > 1) {
            npc.apRequiresLineOfSight = false
            npc.apPlayer2(run.player, interactions)
        } else {
            npc.opPlayer2(run.player, interactions)
        }
    }

    private fun complete(run: Run) {
        if (!run.progress.complete() || runs[run.player.uid] !== run) return
        runs.remove(run.player.uid)
        clearNpcs(run)
        val destination = awardFireCape(run.player)
        val completionCount = run.player.attr.getOrDefault(FIGHT_CAVE_COMPLETIONS, 0) + 1
        run.player.attr[FIGHT_CAVE_COMPLETIONS] = completionCount
        collectionLog.record(
            player = run.player,
            itemId = FIRE_CAPE.asRSCM(RSCMType.OBJ),
            amount = 1,
            source =
                CollectionLogAwardSource(
                    CollectionLogAwardKind.MinigameReward,
                    "fight-caves",
                ),
            eventId = "${run.player.uid}:$completionCount",
        )
        prepareTeleport(run.player)
        if (instances.sessionForPlayer(run.player) === run.instance) {
            instances.leave(run.player, run.instance, clock.cycle)
        }
        PathingEntityCommon.telejump(run.player, collision, FIGHT_CAVE_OUTSIDE)
        run.player.mes(
            "<col=ff9900>Congratulations! You defeated TzTok-Jad and earned one Fire cape.</col>"
        )
        if (destination == "bank")
            run.player.mes("Your inventory was full, so the cape was sent to your bank.")
        if (destination == "ground")
            run.player.mes("Your inventory and bank were full; the cape is on the ground outside.")
        log.info {
            "[FightCave] player=${run.player.displayName} instance=${run.instance.id} " +
                "generation=${run.progress.generation} completed reward=1xFireCape destination=$destination"
        }
    }

    private fun awardFireCape(player: Player): String {
        if (player.invAdd(player.inv, FIRE_CAPE, 1).success) return "inventory"
        val bank = player.invMap.getOrPut("inv.bank")
        if (player.invAdd(bank, FIRE_CAPE, 1).success) return "bank"
        player.invAddOrDrop(objs, FIRE_CAPE, 1, coords = FIGHT_CAVE_OUTSIDE)
        return "ground"
    }

    private fun clearNpcs(run: Run) {
        for (npc in run.active.keys + run.healers) {
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        }
        run.active.clear()
        run.healers.clear()
        run.interruptedHealers.clear()
        run.preparedSplits.clear()
        run.lastMejHealCycle.clear()
    }

    private fun findRun(npc: Npc): Run? = runs.values.firstOrNull { it.owns(npc) }

    private fun npcType(monster: FightCaveMonster) =
        requireNotNull(ServerCacheManager.getNpc(monster.internalName.asRSCM(RSCMType.NPC))) {
                "Missing Fight Cave NPC ${monster.internalName} (${monster.cacheId})"
            }
            .also {
                check(it.id == monster.cacheId) {
                    "Fight Cave NPC mapping drifted: ${monster.internalName} expected=${monster.cacheId} actual=${it.id}"
                }
                check(it.size == monster.footprintSize) {
                    "Fight Cave NPC size drifted: ${monster.internalName} expected=${monster.footprintSize} actual=${it.size}"
                }
            }

    private fun firstInvalidDefinition(): String? =
        FightCaveMonster.entries.firstNotNullOfOrNull { monster ->
            runCatching { npcType(monster) }.exceptionOrNull()?.let { monster.displayName }
        }

    private fun prepareTeleport(player: Player) {
        player.combatClearQueue()
        player.clearInteractionRoute()
    }

    private fun ticksToSeconds(ticks: Int): Int = (ticks.coerceAtLeast(0) * 3 + 4) / 5

    private data class Run(
        val player: Player,
        val instance: InstanceSession,
        val instanceEntry: CoordGrid,
        val progress: FightCaveProgress,
        val countdownEnd: Int,
        val rotation: FightCaveRotation = FIGHT_CAVE_ROTATION,
        val startCycle: Int = countdownEnd - FIGHT_CAVE_COUNTDOWN_TICKS,
        val active: LinkedHashMap<Npc, FightCaveMonster> = linkedMapOf(),
        val healers: LinkedHashSet<Npc> = linkedSetOf(),
        val interruptedHealers: MutableSet<Npc> = mutableSetOf(),
        val preparedSplits: MutableSet<Npc> = mutableSetOf(),
        val lastMejHealCycle: MutableMap<Npc, Int> = mutableMapOf(),
        var transitionCycle: Int? = null,
        var orangeJadSpawn: CoordGrid? = null,
        var healersSpawned: Boolean = false,
        var lastJadHitpoints: Int = 0,
        var nextHealerHealCycle: Int = Int.MAX_VALUE,
    ) {
        val wave: FightCaveWave
            get() = rotation.waves[progress.waveIndex]

        fun owns(npc: Npc): Boolean = npc in active || npc in healers

        fun toInstance(source: CoordGrid): CoordGrid =
            instanceEntry.translate(
                source.x - FIGHT_CAVE_TEMPLATE_ENTRY.x,
                source.z - FIGHT_CAVE_TEMPLATE_ENTRY.z,
                source.level - FIGHT_CAVE_TEMPLATE_ENTRY.level,
            )

        fun canSpawn(coords: CoordGrid, size: Int): Boolean {
            val min = toInstance(FIGHT_CAVE_TEMPLATE_MIN)
            val max = toInstance(FIGHT_CAVE_TEMPLATE_MAX)
            if (
                coords.level != min.level ||
                    coords.x < min.x ||
                    coords.z < min.z ||
                    coords.x + size - 1 > max.x ||
                    coords.z + size - 1 > max.z
            ) {
                return false
            }
            for (dx in 0 until size) {
                for (dz in 0 until size) {
                    val flags = collisionFlags[coords.x + dx, coords.z + dz, coords.level]
                    if (flags and BLOCKED_SPAWN_FLAGS != 0) return false
                }
            }
            if (rectanglesOverlap(coords, size, player.coords, player.size)) return false
            return (active.keys + healers).none { other ->
                rectanglesOverlap(coords, size, other.coords, other.size)
            }
        }

        companion object {
            lateinit var collisionFlags: CollisionFlagMap

            private const val BLOCKED_SPAWN_FLAGS =
                CollisionFlag.LOC or CollisionFlag.BLOCK_WALK

            private fun rectanglesOverlap(
                a: CoordGrid,
                aSize: Int,
                b: CoordGrid,
                bSize: Int,
            ): Boolean =
                a.level == b.level &&
                    a.x <= b.x + bSize - 1 &&
                    a.x + aSize - 1 >= b.x &&
                    a.z <= b.z + bSize - 1 &&
                    a.z + aSize - 1 >= b.z
        }
    }

    init {
        Run.collisionFlags = collision
    }

    private companion object {
        const val NPC_LIFETIME_TICKS = 60_000
        const val HEALER_INTERVAL = 4
        const val HEALER_HEAL_AMOUNT = 5
        const val MEJ_HEAL_INTERVAL = 8
        const val MEJ_HEAL_RANGE = 4
        const val MEJ_HEAL_AMOUNT = 10
        const val FIRE_CAPE = "obj.tzhaar_cape_fire"

        val INSTANCE_SPEC =
            InstanceSettings(
                    maxPlayers = 1,
                    reclaimTicks = 1,
                    graceTicks = 1,
                    destroyWhenEmpty = true,
                    description = "Shortened seven-wave TzHaar Fight Caves",
                )
                .withArea(
                    InstanceArea.copyRegions(
                        regionIds = listOf(9551, 9552),
                        level = 0,
                        rotation = InstanceRegionRotation.NONE,
                        enterCoord =
                            RegionLocal(
                                level = FIGHT_CAVE_TEMPLATE_ENTRY.level,
                                regionZoneX = FIGHT_CAVE_TEMPLATE_ENTRY.mx,
                                regionZoneZ = FIGHT_CAVE_TEMPLATE_ENTRY.mz,
                                localX = FIGHT_CAVE_TEMPLATE_ENTRY.lx,
                                localZ = FIGHT_CAVE_TEMPLATE_ENTRY.lz,
                            ),
                        exitCoord = FIGHT_CAVE_OUTSIDE,
                    ),
                    settingsRowId = 0,
                )
    }
}
