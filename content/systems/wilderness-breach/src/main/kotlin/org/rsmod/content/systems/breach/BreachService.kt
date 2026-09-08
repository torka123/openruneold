@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.systems.breach

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid

internal val BREACH_PK_POINTS = AttributeKey<Int>(persistenceKey = "wilderness_breach_pk_points_v1")
internal val BREACH_KILLS = AttributeKey<Int>(persistenceKey = "wilderness_breach_kills_v1")
internal val BREACH_MVPS = AttributeKey<Int>(persistenceKey = "wilderness_breach_mvps_v1")

@Singleton
internal class BreachService
@Inject
constructor(
    private val config: BreachConfig,
    private val deps: BossDeps,
    private val areas: AreaChecker,
    private val locs: LocRepository,
    private val objs: ObjRepository,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()
    private val random = SecureRandom()
    private var initialized = false
    private var nextBoundaryMillis = nextHalfHourBoundary(System.currentTimeMillis())
    private var event: BreachEvent? = null
    private var activeNpc: Npc? = null
    private var breachLoc: LocInfo? = null
    private var lastConflictBoundary = 0L
    private val recentDeaths = mutableMapOf<Int, Int>()
    private val resolvedPremium = mutableMapOf<BreachBossKind, List<ResolvedLoot>>()
    private val resolvedPets = mutableMapOf<BreachBossKind, ResolvedLoot>()
    private lateinit var resolvedContributor: List<ResolvedLoot>

    fun tick() {
        if (!initialized) initialize()
        val now = System.currentTimeMillis()
        val current = event
        if (current == null) {
            if (now >= nextBoundaryMillis - config.warningFiveMillis) {
                event = createScheduledEvent(nextBoundaryMillis)
                announceFiveMinute(event!!)
            }
            return
        }
        if (
            now >= nextHalfHourBoundary(current.scheduledAtMillis) &&
                lastConflictBoundary != current.scheduledAtMillis
        ) {
            lastConflictBoundary = current.scheduledAtMillis
            log.warn {
                "[Breach] Active event ${current.id} crossed a schedule boundary; no duplicate spawned."
            }
        }
        when (current.state) {
            BreachState.FIVE_MINUTE_WARNING ->
                if (now >= current.scheduledAtMillis - config.warningOneMillis) {
                    current.state = BreachState.ONE_MINUTE_WARNING
                    current.stateStartedAtMillis = now
                    broadcast(
                        "A Wilderness Breach opens in one minute at ${location(current).displayName}!"
                    )
                }
            BreachState.ONE_MINUTE_WARNING ->
                if (now >= current.scheduledAtMillis) beginOpening(current, now)
            BreachState.OPENING ->
                if (now - current.stateStartedAtMillis >= config.openingMillis)
                    spawnBoss(current, now)
            BreachState.ACTIVE -> tickActive(current, now)
            BreachState.COLLAPSING ->
                if (now - current.stateStartedAtMillis >= config.collapseMillis) cleanup(current)
            BreachState.IDLE -> cleanup(current)
        }
    }

    fun prepareHit(npc: Npc, player: Player, damage: Int) {
        val current = event ?: return
        if (damage <= 0 || !owns(npc) || !current.hitpointScaledAccounts.add(player.accountId)) {
            return
        }
        val oldMaximum = npc.baseHitpointsLvl
        val newMaximum =
            scaledBreachHitpoints(
                baseHitpoints = boss(current).hitpoints,
                hitpointsPerAttacker = config.hitpointsPerAttacker,
                maximumHitpoints = config.maximumScaledHitpoints,
                attackerCount = current.hitpointScaledAccounts.size,
            )
        if (newMaximum > oldMaximum) {
            npc.baseHitpointsLvl = newMaximum
            npc.hitpoints =
                (npc.hitpoints.toLong() + (newMaximum - oldMaximum))
                    .coerceAtMost(newMaximum.toLong())
                    .toInt()
        }
        log.info {
            "[Breach] HP scale id=${current.id} attackers=${current.hitpointScaledAccounts.size} hp=$newMaximum account=${player.accountId}."
        }
    }

    fun recordHit(npc: Npc, player: Player, damage: Int) {
        val current = event ?: return
        if (!isCurrent(current, npc) || damage <= 0 || current.rewardsDistributed) return
        val cycle = deps.mapClock.cycle
        val uuid = player.uuid ?: return
        val entry =
            current.contributions.getOrPut(player.accountId) {
                BreachContribution(
                    accountId = player.accountId,
                    playerUuid = uuid,
                    username = player.username.ifBlank { player.displayName },
                    firstHitTick = cycle,
                    lastHitTick = cycle,
                )
            }
        if (entry.playerUuid != uuid) {
            log.warn { "[Breach] Rejected account/uuid drift for account=${player.accountId}." }
            return
        }
        entry.effectiveDamage =
            (entry.effectiveDamage + damage.toLong()).coerceAtMost(Int.MAX_VALUE.toLong())
        entry.lastHitTick = cycle
        defendAnimation(current.boss)?.let(npc::anim)
        if (npc.hitpoints <= 0) complete(current, npc)
    }

    fun inspect(player: Player) {
        val current = event
        if (current == null) {
            player.mes(
                "The Wilderness Breach is dormant. The next opening is ${formatUtc(nextBoundaryMillis)} UTC."
            )
            return
        }
        val where = location(current)
        val npc = activeNpc
        val remaining =
            when (current.state) {
                BreachState.ACTIVE ->
                    ((config.activeMillis - (System.currentTimeMillis() - current.spawnedAtMillis))
                        .coerceAtLeast(0) / 1_000)
                else -> 0
            }
        val total = current.contributions.values.sumOf(BreachContribution::effectiveDamage)
        val mine = current.contributions[player.accountId]?.effectiveDamage ?: 0
        player.mes(
            "Breach: ${current.boss.displayName} at ${where.displayName} (level ${where.wildernessLevel})."
        )
        player.mes(
            "State: ${current.state}; HP: ${npc?.hitpoints?.coerceAtLeast(0) ?: 0}/${npc?.baseHitpointsLvl ?: boss(current).hitpoints}; ${remaining}s remain."
        )
        player.mes(
            "Your effective damage: $mine (${contributionBasisPoints(mine, total) / 100.0}%). Every damage awards ${config.pointsPerDamage} PK Points."
        )
        player.mes(
            "MVP receives guaranteed premium loot; every other contributor rolls at their exact damage share."
        )
    }

    fun status(): List<String> {
        val current = event
        if (current == null) {
            return listOf("Breach state=IDLE; next=${formatUtc(nextBoundaryMillis)} UTC.")
        }
        val npc = activeNpc
        return listOf(
            "Breach id=${current.id}; state=${current.state}; boss=${current.boss.displayName}; location=${current.locationIndex + 1}:${location(current).displayName}.",
            "HP=${npc?.hitpoints ?: 0}/${npc?.baseHitpointsLvl ?: boss(current).hitpoints}; contributors=${current.contributions.size}; totalDamage=${current.contributions.values.sumOf(BreachContribution::effectiveDamage)}.",
        )
    }

    fun teleport(player: Player): String {
        val current = event ?: return "There is no announced or active Wilderness Breach."
        if (
            current.state != BreachState.FIVE_MINUTE_WARNING &&
                current.state != BreachState.ONE_MINUTE_WARNING &&
                current.state != BreachState.OPENING &&
                current.state != BreachState.ACTIVE
        ) {
            return "There is no announced or active Wilderness Breach."
        }
        val arena = location(current)
        PathingEntityCommon.teleport(player, deps.collision, arena.arrivalTile)
        return "You teleport to ${current.boss.displayName} at ${arena.displayName}."
    }

    fun forceStart(bossName: String?, locationIndex: Int?): String {
        if (event != null) return "A Breach event is already running or announced."
        val selectedBoss =
            bossName
                ?.takeUnless { it.equals("random", true) }
                ?.let { requested ->
                    BreachBossKind.entries.firstOrNull {
                        it.name.equals(requested.replace('-', '_'), true) ||
                            it.displayName.replace(" ", "").equals(requested.replace(" ", ""), true)
                    }
                        ?: return "Unknown boss. Use cerberus, corrupted_hunllef, whisperer, vardorvis, or random."
                } ?: BreachBossKind.entries[random.nextInt(BreachBossKind.entries.size)]
        val index = locationIndex?.minus(1) ?: random.nextInt(config.locations.size)
        if (index !in config.locations.indices)
            return "Location must be 1-${config.locations.size}."
        val now = System.currentTimeMillis()
        val forced =
            BreachEvent(
                scheduledAtMillis = now,
                boss = selectedBoss,
                locationIndex = index,
                state = BreachState.ONE_MINUTE_WARNING,
                stateStartedAtMillis = now,
            )
        event = forced
        beginOpening(forced, now)
        return "Forced ${selectedBoss.displayName} Breach opening at ${config.locations[index].displayName}."
    }

    fun forceStop(): String {
        val current = event ?: return "There is no Breach event to stop."
        beginCollapse(current, killed = false, reason = "Staff closed the Wilderness Breach.")
        return "Breach ${current.id} is collapsing and will be cleaned safely."
    }

    fun goto(player: Player, oneBasedIndex: Int): String {
        val arena =
            config.locations.getOrNull(oneBasedIndex - 1)
                ?: return "Location must be 1-${config.locations.size}."
        PathingEntityCommon.teleport(player, deps.collision, arena.arrivalTile)
        return "Teleported to ${arena.displayName} for staff testing."
    }

    fun nextText(): String = "Next scheduled Breach: ${formatUtc(nextBoundaryMillis)} UTC."

    fun lootSimulation(basisPoints: Int): String {
        if (basisPoints !in 1..10_000) return "Use basis points from 1 to 10000."
        var successes = 0
        repeat(10_000) { if (random.nextInt(10_000) < basisPoints) successes++ }
        return "Roat contributor simulation at ${basisPoints / 100.0}%: $successes/10000 standard-table passes; MVP is always guaranteed premium loot."
    }

    fun owns(npc: Npc): Boolean = activeNpc === npc && event?.state == BreachState.ACTIVE

    fun bossTypes() = config.bosses.values.mapNotNull { ServerCacheManager.getNpc(it.npcId) }

    fun breachTypes() =
        listOfNotNull(
            ServerCacheManager.getObject(BREACH_OPEN_LOC_ID),
            ServerCacheManager.getObject(BREACH_CLOSED_LOC_ID),
        )

    private fun initialize() {
        validateAssets()
        validateLocations()
        initialized = true
        log.info {
            "[Breach] Controller ready; next UTC boundary=${formatUtc(nextBoundaryMillis)}."
        }
    }

    private fun validateAssets() {
        requireNotNull(ServerCacheManager.getObject(BREACH_OPEN_LOC_ID)) {
            "Missing revision-240 Breach loc $BREACH_OPEN_LOC_ID"
        }
        requireNotNull(ServerCacheManager.getObject(BREACH_CLOSED_LOC_ID)) {
            "Missing revision-240 Breach loc $BREACH_CLOSED_LOC_ID"
        }
        config.bosses.values.forEach {
            requireNotNull(ServerCacheManager.getNpc(it.npcId)) { "Missing Breach NPC ${it.npcId}" }
        }
        resolvedContributor = resolveLoot(config.contributorLoot, "contributor")
        require(resolvedContributor.isNotEmpty()) {
            "Breach contributor loot table resolved no cache items."
        }
        config.bosses.forEach { (kind, boss) ->
            resolvedPremium[kind] = resolveLoot(boss.premiumLoot, "${kind.name} premium")
            require(resolvedPremium.getValue(kind).isNotEmpty()) {
                "$kind premium table resolved no cache items."
            }
            val pet = boss.petNames.firstNotNullOfOrNull(::resolveSingle)
            requireNotNull(pet) {
                "$kind pet configuration resolved no cache items: ${boss.petNames}"
            }
            resolvedPets[kind] = pet
        }
    }

    private fun resolveLoot(entries: List<BreachLootEntry>, table: String): List<ResolvedLoot> =
        entries.mapNotNull { entry ->
            val resolved = resolveSingle(entry.internalName)
            if (resolved == null) {
                log.error { "[Breach] Missing $table item ${entry.internalName}; entry disabled." }
                null
            } else {
                resolved.copy(
                    minimum = entry.minimum,
                    maximum = entry.maximum,
                    weight = entry.weight,
                )
            }
        }

    private fun resolveSingle(internal: String): ResolvedLoot? =
        runCatching {
                val id = internal.asRSCM(RSCMType.OBJ)
                ServerCacheManager.getItem(id)?.let { ResolvedLoot(it.id, internal, 1, 1, 1) }
            }
            .getOrNull()

    private fun validateLocations() {
        val maxBossSize = config.bosses.values.maxOf { ServerCacheManager.getNpc(it.npcId)!!.size }
        config.locations.forEachIndexed { index, arena ->
            val allTiles = buildList {
                for (x in
                    (arena.center.x - arena.combatRadius)..(arena.center.x + arena.combatRadius)) {
                    for (z in
                        (arena.center.z - arena.combatRadius)..(arena.center.z +
                                arena.combatRadius)) {
                        add(CoordGrid(x, z, 0))
                    }
                }
            }
            check(
                allTiles.all {
                    areas.inArea(WILDERNESS_AREA, it) && areas.inArea(MULTIWAY_AREA, it)
                }
            ) {
                "Breach location ${arena.key} does not keep its complete ${arena.combatRadius}-tile radius in overworld multi-combat."
            }
            val usable =
                allTiles.count {
                    deps.collision.isZoneValid(it) && !deps.collision.isWalkBlocked(it)
                }
            check(usable * 100 / allTiles.size >= 75) {
                "Breach location ${arena.key} has only $usable/${allTiles.size} usable tiles."
            }
            check(safeFootprint(arena.bossSpawn, maxBossSize)) {
                "Breach location ${arena.key} has no safe ${maxBossSize}x$maxBossSize boss footprint at ${arena.bossSpawn}."
            }
            log.info {
                "[Breach] Validated location ${index + 1} ${arena.key}: center=${arena.center.x},${arena.center.z},0 " +
                    "level=${arena.wildernessLevel} multi=${allTiles.size}/${allTiles.size} usable=$usable/${allTiles.size}."
            }
        }
    }

    private fun safeFootprint(tile: CoordGrid, size: Int): Boolean =
        (0 until size).all { dx ->
            (0 until size).all { dz ->
                val current = tile.translate(dx, dz)
                areas.inArea(WILDERNESS_AREA, current) &&
                    areas.inArea(MULTIWAY_AREA, current) &&
                    deps.collision.isZoneValid(current) &&
                    !deps.collision.isWalkBlocked(current)
            }
        }

    private fun createScheduledEvent(boundary: Long): BreachEvent =
        BreachEvent(
            scheduledAtMillis = boundary,
            boss = BreachBossKind.entries[random.nextInt(BreachBossKind.entries.size)],
            locationIndex = random.nextInt(config.locations.size),
        )

    private fun announceFiveMinute(current: BreachEvent) {
        broadcast(
            "A Wilderness Breach is forming at ${location(current).displayName}! ${current.boss.displayName} arrives in five minutes."
        )
        log.info {
            "[Breach] scheduled id=${current.id} at=${formatUtc(current.scheduledAtMillis)} " +
                "locationRoll=${current.locationIndex + 1} bossRoll=${current.boss.ordinal + 1}."
        }
    }

    private fun beginOpening(current: BreachEvent, now: Long) {
        if (
            event?.id != current.id ||
                current.state == BreachState.OPENING ||
                current.state == BreachState.ACTIVE
        )
            return
        removeBreachLoc()
        val arena = location(current)
        val type = requireNotNull(ServerCacheManager.getObject(BREACH_OPEN_LOC_ID))
        breachLoc =
            locs.add(
                arena.objectTile,
                type,
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        breachLoc?.let { deps.worldRepo.locAnim(it, BREACH_OPEN_SEQUENCE) }
        current.state = BreachState.OPENING
        current.stateStartedAtMillis = now
        broadcast(
            "The Wilderness Breach tears open at ${arena.displayName}! ${current.boss.displayName} is emerging."
        )
    }

    private fun spawnBoss(current: BreachEvent, now: Long) {
        if (
            event?.id != current.id ||
                current.state != BreachState.OPENING ||
                current.bossSpawned ||
                current.rewardsDistributed ||
                activeNpc?.isSlotAssigned == true
        )
            return
        val definition = boss(current)
        val arena = location(current)
        val base = requireNotNull(ServerCacheManager.getNpc(definition.npcId))
        check(safeFootprint(arena.bossSpawn, base.size)) {
            "Breach spawn footprint became blocked at ${arena.bossSpawn}."
        }
        val type = base.copy(hitpoints = definition.hitpoints, regenRate = Int.MAX_VALUE)
        val npc =
            Npc(type, arena.bossSpawn).also {
                it.mode = NpcMode.None
                it.respawns = false
                it.suppressRewards = true
                deps.npcRepo.add(it, Int.MAX_VALUE)
            }
        activeNpc = npc
        current.bossSpawned = true
        current.state = BreachState.ACTIVE
        current.stateStartedAtMillis = now
        current.spawnedAtMillis = now
        current.nextAttackTick = deps.mapClock.cycle + 3
        current.lastValidTargetTick = deps.mapClock.cycle
        breachLoc?.let { deps.worldRepo.locAnim(it, BREACH_LOOP_SEQUENCE) }
        broadcast(
            "${current.boss.displayName} has emerged from the Breach at ${arena.displayName} (level ${arena.wildernessLevel} Wilderness)!"
        )
        log.info {
            "[Breach] active id=${current.id} npc=${type.id} hp=${type.hitpoints} tile=${arena.bossSpawn}."
        }
    }

    private fun tickActive(current: BreachEvent, now: Long) {
        val npc = activeNpc
        if (npc == null) {
            beginCollapse(current, killed = false, reason = "The Wilderness Breach destabilized.")
            return
        }
        if (npc.hitpoints <= 0) {
            complete(current, npc)
            return
        }
        if (!npc.isSlotAssigned) {
            beginCollapse(current, killed = false, reason = "The Wilderness Breach destabilized.")
            return
        }
        if (now - current.spawnedAtMillis >= config.activeMillis) {
            beginCollapse(
                current,
                killed = false,
                reason = "${current.boss.displayName} escaped as the Wilderness Breach collapsed!",
            )
            return
        }
        val cycle = deps.mapClock.cycle
        markRecentDeaths(current, cycle)
        current.contributions.values.removeIf {
            cycle - it.lastHitTick > config.contributionRetentionTicks
        }
        val arena = location(current)
        if (!arena.contains(npc.coords, arena.leashRadius)) {
            PathingEntityCommon.teleport(npc, deps.collision, arena.bossSpawn)
            log.warn {
                "[Breach] Leash recovery id=${current.id} from=${npc.coords} to=${arena.bossSpawn}."
            }
        }
        val targets = targetPlayers(current)
        if (targets.isEmpty()) {
            if (cycle - current.lastValidTargetTick >= config.noTargetResetTicks) {
                if (npc.coords != arena.bossSpawn)
                    PathingEntityCommon.teleport(npc, deps.collision, arena.bossSpawn)
                npc.hitpoints = npc.baseHitpointsLvl
                current.contributions.clear()
                current.lastValidTargetTick = cycle
                log.info {
                    "[Breach] Full no-target reset id=${current.id}; health and contribution cleared."
                }
            }
            return
        }
        current.lastValidTargetTick = cycle
        if (cycle < current.nextAttackTick) return
        attack(current, npc, targets)
        current.nextAttackTick = cycle + boss(current).attackDelayTicks.coerceAtLeast(4)
    }

    private fun attack(current: BreachEvent, npc: Npc, targets: List<Player>) {
        val primary = targets[current.attackNumber % targets.size]
        npc.facePlayer(primary)
        val roll = random.nextInt(100) + 1
        when (current.boss) {
            BreachBossKind.CERBERUS -> attackCerberus(current, npc, targets, primary, roll)
            BreachBossKind.CORRUPTED_HUNLLEF -> attackHunllef(current, npc, targets, roll)
            BreachBossKind.WHISPERER -> attackWhisperer(current, npc, targets, roll)
            BreachBossKind.VARDORVIS -> attackVardorvis(current, npc, targets, primary, roll)
        }
        current.attackNumber++
    }

    private fun attackCerberus(
        current: BreachEvent,
        npc: Npc,
        targets: List<Player>,
        primary: Player,
        roll: Int,
    ) {
        when (roll) {
            in 1..35 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Magic,
                    35,
                    4492,
                    MAGIC_TRAVEL,
                    MAGIC_IMPACT,
                )
            in 36..65 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Ranged,
                    35,
                    4493,
                    FIRE_TRAVEL,
                    FIRE_IMPACT,
                )
            in 66..78 -> {
                npc.anim(4490)
                listOf(HitType.Magic, HitType.Ranged, HitType.Melee).forEachIndexed { index, style
                    ->
                    delayedDirect(
                        current,
                        npc,
                        listOf(primary),
                        style,
                        24,
                        index * 2 + 1,
                        bypass = false,
                    )
                }
            }
            in 79..90 ->
                delayedTiles(current, npc, targets.map(Player::coords), 31, 4501, CERBERUS_FLAME, 1)
            else -> {
                npc.anim(4494)
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Magic,
                    32,
                    4494,
                    MAGIC_TRAVEL,
                    MAGIC_IMPACT,
                )
                delayedDirect(current, npc, targets, HitType.Ranged, 32, 3, bypass = false)
            }
        }
    }

    private fun attackHunllef(current: BreachEvent, npc: Npc, targets: List<Player>, roll: Int) {
        when (roll) {
            in 1..42 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Ranged,
                    40,
                    8419,
                    FIRE_TRAVEL,
                    FIRE_IMPACT,
                )
            in 43..84 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Magic,
                    40,
                    8754,
                    MAGIC_TRAVEL,
                    MAGIC_IMPACT,
                )
            in 85..94 ->
                delayedTiles(current, npc, targets.map(Player::coords), 32, 8423, FIRE_IMPACT, 1)
            else ->
                delayedTiles(
                    current,
                    npc,
                    targets.flatMap { ring(it.coords, 1) },
                    35,
                    8418,
                    CERBERUS_FLAME,
                    0,
                )
        }
    }

    private fun attackWhisperer(current: BreachEvent, npc: Npc, targets: List<Player>, roll: Int) {
        when (roll) {
            in 1..40 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Magic,
                    38,
                    10241,
                    MAGIC_TRAVEL,
                    MAGIC_IMPACT,
                )
            in 41..72 -> {
                npc.anim(10235)
                val waves = targets.flatMap { lineThrough(it.coords, location(current)) }
                delayedTiles(current, npc, waves, 34, null, FIRE_IMPACT, 0, HitType.Ranged)
            }
            in 73..88 ->
                delayedTiles(
                    current,
                    npc,
                    targets.flatMap { ring(it.coords, 1) },
                    36,
                    10254,
                    CERBERUS_FLAME,
                    0,
                )
            else -> {
                npc.anim(10250)
                delayedDirect(current, npc, targets, HitType.Typeless, 38, 3, bypass = true)
            }
        }
    }

    private fun attackVardorvis(
        current: BreachEvent,
        npc: Npc,
        targets: List<Player>,
        primary: Player,
        roll: Int,
    ) {
        when (roll) {
            in 1..35 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Ranged,
                    36,
                    10365,
                    FIRE_TRAVEL,
                    FIRE_IMPACT,
                )
            in 36..65 ->
                projectileVolley(
                    current,
                    npc,
                    targets,
                    HitType.Magic,
                    34,
                    10348,
                    MAGIC_TRAVEL,
                    MAGIC_IMPACT,
                )
            in 66..85 ->
                delayedTiles(
                    current,
                    npc,
                    targets.map(Player::coords),
                    34,
                    10353,
                    CERBERUS_FLAME,
                    0,
                )
            else -> {
                npc.anim(10340)
                val cleave =
                    validPlayers(current).filter {
                        it.coords.chebyshevDistance(primary.coords) <= 2
                    }
                delayedDirect(current, npc, cleave, HitType.Melee, 36, 1, bypass = false)
            }
        }
    }

    private fun projectileVolley(
        current: BreachEvent,
        npc: Npc,
        targets: List<Player>,
        type: HitType,
        maxHit: Int,
        animation: Int,
        travel: String,
        impact: String,
    ) {
        npc.anim(animation)
        targets.distinctBy(Player::accountId).forEach { target ->
            val projectile =
                deps.worldRepo.projAnimSourced(
                    npc,
                    target,
                    SpotanimType(travel.asRSCM(RSCMType.SPOTANIM)),
                    BREACH_PROJECTILE,
                )
            val id = current.id
            deps.worldQueues.add(projectile.serverCycles.coerceAtLeast(1)) {
                if (!isCurrent(id, npc) || !validTarget(target, current)) return@add
                target.spotanim(impact)
                target.queueHit(
                    npc,
                    1,
                    type,
                    random.nextInt(maxHit + 1),
                    modifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
            }
        }
    }

    private fun delayedTiles(
        current: BreachEvent,
        npc: Npc,
        tiles: List<CoordGrid>,
        maxHit: Int,
        animation: Int?,
        graphic: String,
        radius: Int,
        type: HitType = HitType.Typeless,
    ) {
        animation?.let(npc::anim)
        val arena = location(current)
        val marked = tiles.distinct().filter { arena.contains(it) }
        marked.forEach {
            deps.worldRepo.spotanimMap(SpotanimType(graphic.asRSCM(RSCMType.SPOTANIM)), it)
        }
        val id = current.id
        deps.worldQueues.add(3) {
            if (!isCurrent(id, npc)) return@add
            validPlayers(current).forEach { player ->
                if (marked.any { it.chebyshevDistance(player.coords) <= radius }) {
                    player.queueHit(
                        npc,
                        1,
                        type,
                        random.nextInt(maxHit + 1),
                        modifier =
                            if (type == HitType.Typeless) NpcBypassProtectionPrayerPlayerHitModifier
                            else NpcPartialProtectionPrayerPlayerHitModifier(50),
                    )
                }
            }
        }
    }

    private fun delayedDirect(
        current: BreachEvent,
        npc: Npc,
        targets: List<Player>,
        type: HitType,
        maxHit: Int,
        delay: Int,
        bypass: Boolean,
    ) {
        val id = current.id
        deps.worldQueues.add(delay) {
            if (!isCurrent(id, npc)) return@add
            targets
                .distinctBy(Player::accountId)
                .filter { validTarget(it, current) }
                .forEach { player ->
                    player.queueHit(
                        npc,
                        1,
                        type,
                        random.nextInt(maxHit + 1),
                        modifier =
                            if (bypass) NpcBypassProtectionPrayerPlayerHitModifier
                            else NpcPartialProtectionPrayerPlayerHitModifier(50),
                    )
                }
        }
    }

    private fun complete(current: BreachEvent, npc: Npc) {
        if (event?.id != current.id || current.rewardsDistributed) return
        npc.respawns = false
        current.rewardsDistributed = true
        val cycle = deps.mapClock.cycle
        val eligible =
            current.contributions.values.filter { contribution ->
                cycle - contribution.lastHitTick <= config.contributionRetentionTicks &&
                    eligiblePlayer(contribution.accountId, current, cycle) != null
            }
        val decisions =
            roatRewardDecisions(
                eligible,
                config.pointsPerDamage,
                config.petDenominator,
                contributionRoll = { bound -> random.nextLong(bound) },
                petRoll = { denominator -> random.nextInt(denominator) },
            )
        decisions.forEach { decision ->
            if (!current.paidAccounts.add(decision.accountId)) return@forEach
            val player = eligiblePlayer(decision.accountId, current, cycle) ?: return@forEach
            reward(player, current, decision, npc.coords)
        }
        val mvp = decisions.firstOrNull(RoatRewardDecision::mvp)
        val mvpName = eligible.firstOrNull { it.accountId == mvp?.accountId }?.username ?: "none"
        broadcast(
            "${current.boss.displayName} was defeated at ${location(current).displayName}! MVP: $mvpName with ${mvp?.damage ?: 0} effective damage."
        )
        log.info {
            "[Breach] killed id=${current.id} total=${eligible.sumOf(BreachContribution::effectiveDamage)} " +
                "contributors=${eligible.size} paid=${current.paidAccounts.size} mvp=$mvpName."
        }
        beginCollapse(current, killed = true, reason = null)
    }

    private fun reward(
        player: Player,
        current: BreachEvent,
        decision: RoatRewardDecision,
        dropTile: CoordGrid,
    ) {
        player.attr[BREACH_PK_POINTS] =
            ((player.attr[BREACH_PK_POINTS] ?: 0).toLong() + decision.pkPoints)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        player.attr[BREACH_KILLS] = (player.attr[BREACH_KILLS] ?: 0) + 1
        if (decision.mvp) player.attr[BREACH_MVPS] = (player.attr[BREACH_MVPS] ?: 0) + 1
        val tile =
            if (location(current).contains(player.coords, config.rewardRadius)) player.coords
            else dropTile
        val main =
            when {
                decision.mvp -> weighted(resolvedPremium.getValue(current.boss))
                decision.mainReward -> weighted(resolvedContributor)
                else -> null
            }
        val mainAward = main?.let { drop(player, tile, it) }
        val petAward =
            if (decision.pet) drop(player, tile, resolvedPets.getValue(current.boss)) else null
        val reference = "wilderness-breach:${current.boss.name.lowercase()}"
        mainAward?.let {
            collectionLog.record(
                player = player,
                itemId = it.first,
                amount = it.second,
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.SpecialContainer,
                        reference,
                    ),
                eventId = "${current.id}:${player.accountId}:main",
            )
        }
        petAward?.let {
            collectionLog.record(
                player = player,
                itemId = it.first,
                amount = it.second,
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.PetGrant,
                        reference,
                    ),
                eventId = "${current.id}:${player.accountId}:pet",
            )
        }
        player.mes(
            "Your ${decision.damage} effective damage earned ${decision.pkPoints} PK Points" +
                when {
                    decision.mvp -> " and guaranteed MVP premium loot."
                    decision.mainReward -> " and a successful contribution-share loot roll."
                    else -> "; your independent loot roll did not pass this time."
                }
        )
        if (decision.pet)
            broadcast(
                "${player.displayName} received the Breach pet roll from ${current.boss.displayName}!"
            )
        log.info {
            "[Breach] reward event=${current.id} account=${player.accountId} damage=${decision.damage} " +
                "points=${decision.pkPoints} mvp=${decision.mvp} main=${main?.internalName} pet=${decision.pet}."
        }
    }

    private fun drop(player: Player, tile: CoordGrid, entry: ResolvedLoot): Pair<Int, Int> {
        val type = requireNotNull(ServerCacheManager.getItem(entry.itemId))
        val count =
            if (entry.minimum == entry.maximum) entry.minimum
            else random.nextInt(entry.maximum - entry.minimum + 1) + entry.minimum
        objs.add(type, tile, config.ownerDropDurationTicks, player, count, config.ownerRevealTicks)
        return type.id to count
    }

    private fun weighted(entries: List<ResolvedLoot>): ResolvedLoot {
        var roll = random.nextInt(entries.sumOf(ResolvedLoot::weight))
        entries.forEach { entry ->
            if (roll < entry.weight) return entry
            roll -= entry.weight
        }
        return entries.last()
    }

    private fun beginCollapse(current: BreachEvent, killed: Boolean, reason: String?) {
        if (event?.id != current.id || current.state == BreachState.COLLAPSING) return
        if (!killed)
            activeNpc?.let {
                it.respawns = false
                if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE)
                activeNpc = null
            }
        current.state = BreachState.COLLAPSING
        current.stateStartedAtMillis = System.currentTimeMillis()
        breachLoc?.let { deps.worldRepo.locAnim(it, BREACH_CLOSE_SEQUENCE) }
        reason?.let(::broadcast)
    }

    private fun cleanup(current: BreachEvent) {
        if (event?.id != current.id) return
        activeNpc?.respawns = false
        activeNpc?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        activeNpc = null
        removeBreachLoc()
        current.contributions.clear()
        current.hitpointScaledAccounts.clear()
        recentDeaths.clear()
        log.info { "[Breach] cleanup id=${current.id}; next boundary recalculated." }
        event = null
        nextBoundaryMillis = nextHalfHourBoundary(System.currentTimeMillis())
        lastConflictBoundary = 0
    }

    private fun removeBreachLoc() {
        breachLoc?.let { locs.del(it, Int.MAX_VALUE) }
        breachLoc = null
    }

    private fun markRecentDeaths(current: BreachEvent, cycle: Int) {
        deps.playerList.forEach { player ->
            if (
                player.isSlotAssigned &&
                    player.hitpoints <= 0 &&
                    location(current).contains(player.coords, config.rewardRadius)
            ) {
                recentDeaths[player.accountId] = cycle
            }
        }
        recentDeaths.entries.removeIf { cycle - it.value > config.recentDeathGraceTicks }
    }

    private fun eligiblePlayer(accountId: Int, current: BreachEvent, cycle: Int): Player? {
        val player =
            deps.playerList.firstOrNull { it.accountId == accountId && it.isSlotAssigned }
                ?: return null
        val nearby =
            player.hitpoints > 0 && location(current).contains(player.coords, config.rewardRadius)
        val recentDeath =
            cycle - (recentDeaths[accountId] ?: Int.MIN_VALUE) <= config.recentDeathGraceTicks
        return player.takeIf { nearby || recentDeath }
    }

    private fun targetPlayers(current: BreachEvent): List<Player> {
        val valid = validPlayers(current)
        if (valid.isEmpty()) return emptyList()
        val byAccount = valid.associateBy(Player::accountId)
        val contributors =
            current.contributions.values
                .sortedByDescending(BreachContribution::effectiveDamage)
                .mapNotNull { byAccount[it.accountId] }
        val others = valid.filterNot { it.accountId in current.contributions }
        val ordered = (contributors + others).distinctBy(Player::accountId)
        val count = targetCount(ordered.size)
        if (count == 0) return emptyList()
        val offset = current.attackNumber % ordered.size
        return List(count) { ordered[(offset + it) % ordered.size] }
    }

    private fun validPlayers(current: BreachEvent): List<Player> =
        deps.playerList.filter { validTarget(it, current) }

    private fun validTarget(player: Player, current: BreachEvent): Boolean =
        player.isSlotAssigned &&
            player.hitpoints > 0 &&
            player.isValidTarget() &&
            location(current).contains(player.coords)

    private fun isCurrent(current: BreachEvent, npc: Npc): Boolean =
        event?.id == current.id && current.state == BreachState.ACTIVE && activeNpc === npc

    private fun isCurrent(id: java.util.UUID, npc: Npc): Boolean =
        event?.id == id &&
            event?.state == BreachState.ACTIVE &&
            activeNpc === npc &&
            npc.isSlotAssigned &&
            npc.hitpoints > 0

    private fun ring(tile: CoordGrid, radius: Int): List<CoordGrid> = buildList {
        for (dx in -radius..radius) {
            add(tile.translate(dx, -radius))
            add(tile.translate(dx, radius))
        }
        for (dz in (-radius + 1) until radius) {
            add(tile.translate(-radius, dz))
            add(tile.translate(radius, dz))
        }
    }

    private fun lineThrough(tile: CoordGrid, arena: BreachLocation): List<CoordGrid> =
        (-arena.combatRadius..arena.combatRadius).map {
            arena.center.translate(it, tile.z - arena.center.z)
        }

    private fun boss(current: BreachEvent): BreachBossConfig = config.bosses.getValue(current.boss)

    private fun location(current: BreachEvent): BreachLocation =
        config.locations[current.locationIndex]

    private fun defendAnimation(kind: BreachBossKind): Int? =
        when (kind) {
            BreachBossKind.CERBERUS -> 4489
            BreachBossKind.CORRUPTED_HUNLLEF -> null
            BreachBossKind.WHISPERER -> 10231
            BreachBossKind.VARDORVIS -> 10339
        }

    private fun formatUtc(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime()
            .toString()
            .replace('T', ' ')

    private fun broadcast(message: String) {
        deps.playerList.forEach { it.mes(message, ChatType.Broadcast) }
    }
}

private data class ResolvedLoot(
    val itemId: Int,
    val internalName: String,
    val minimum: Int,
    val maximum: Int,
    val weight: Int,
)

private const val WILDERNESS_AREA = "area.wilderness"
private const val MULTIWAY_AREA = "area.multiway"
private const val BREACH_OPEN_LOC_ID = 49_561
private const val BREACH_CLOSED_LOC_ID = 49_562
private const val BREACH_OPEN_SEQUENCE = "seq.vfx_dm_breach_spawn"
private const val BREACH_LOOP_SEQUENCE = "seq.vfx_dm_breach_loop"
private const val BREACH_CLOSE_SEQUENCE = "seq.vfx_dm_breach_despawn"
private const val MAGIC_TRAVEL = "spotanim.revenant_magic_travel"
private const val MAGIC_IMPACT = "spotanim.revenant_magic_impact"
private const val FIRE_TRAVEL = "spotanim.tzhaar_fire_launch_travel"
private const val FIRE_IMPACT = "spotanim.tzhaar_fire_launch_impact"
private const val CERBERUS_FLAME = "spotanim.vfx_flames_of_cerberus"

private val BREACH_PROJECTILE =
    ProjAnimType(
        startHeight = 64,
        endHeight = 32,
        delay = 0,
        angle = 16,
        lengthAdjustment = 10,
        progress = 0,
        stepMultiplier = 1,
    )
