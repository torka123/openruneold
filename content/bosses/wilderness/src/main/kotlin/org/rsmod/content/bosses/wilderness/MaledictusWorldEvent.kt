package org.rsmod.content.bosses.wilderness

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class MaledictusWorldScript @Inject constructor(private val events: MaledictusWorldEvent) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { events.tick() }
        onCommand("revstatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the live Revenant Maledictus world-event state"
            invalidArgs = "Use as ::revstatus"
            cheat { if (args.isEmpty()) showStatus() }
        }
        onCommand("revboss") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Control the live Revenant Maledictus world event"
            invalidArgs = "Use as ::revboss <warn|spawn|despawn>"
            cheat { controlBoss() }
        }
    }

    private fun Cheat.showStatus() {
        val status = events.snapshot()
        player.mes("Maledictus state: ${status.state}; area=${status.area ?: "not reserved"}.")
        player.mes(
            "Warning in ${formatTicks(status.ticksUntilWarning)}; " +
                "spawn in ${formatTicks(status.ticksUntilSpawn)}; " +
                "despawn in ${formatTicks(status.ticksUntilDespawn)}."
        )
        status.activeTile?.let { player.mes("Staff tile: ${it.x}, ${it.z}, plane ${it.level}.") }
    }

    private fun Cheat.controlBoss() {
        val result =
            when (args.firstOrNull()?.lowercase()) {
                "warn" -> events.forceWarning()
                "spawn" -> events.forceSpawn()
                "despawn" -> events.forceDespawn()
                else -> "Use ::revboss warn, ::revboss spawn, or ::revboss despawn."
            }
        player.mes(result)
    }

    private fun formatTicks(ticks: Int?): String =
        ticks?.let { "${it * SERVER_TICK_MILLIS / 1000}s" } ?: "n/a"
}

/** Single game-thread-owned Maledictus state for one world. */
@Singleton
internal class MaledictusWorldEvent
@Inject
constructor(
    private val deps: BossDeps,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
) {
    private val logger = InlineLogger()
    private val timeline = MaledictusTimeline()
    private var initialized = false
    private var validatedSpawnTiles: Map<MaledictusSpawn, List<CoordGrid>> = emptyMap()
    private var reserved: ReservedMaledictusSpawn? = null
    private var activeNpc: Npc? = null
    private var activeDefinition: MaledictusSpawn? = null

    fun tick() {
        val now = deps.mapClock.cycle
        if (!initialized) {
            validateSpawnAreas()
            timeline.scheduleAfter(now)
            initialized = true
        }
        val active = activeNpc
        if (active != null) {
            if (!active.isSlotAssigned) {
                clearActiveAndSchedule(now)
                return
            }
            if (active.hitpoints <= 0) return
            if (timeline.dueDespawn(now)) expire(active, now)
            return
        }
        if (timeline.dueWarning(now) && !issueWarning(now)) {
            timeline.scheduleAfter(now)
            return
        }
        if (timeline.dueSpawn(now)) spawnReserved(now)
    }

    fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        if (npc.visType.internalName != MALEDICTUS_INTERNAL_NAME || activeNpc !== npc) return
        val contributors =
            npc.damageContributions
                .sortedByDamageDescending()
                .filterIsInstance<DamageContributor.ByPlayer>()
                .distinctBy { it.uuid }
                .mapNotNull { it.resolve(deps.playerList) }
        val top = npc.damageContributions.topPlayer(deps.playerList) ?: context.hero
        rewardTopDamager(top, npc)
        contributors.filter { it !== top }.forEach(::rewardParticipant)
        deps.encounterRegistry.remove(npc)
        activeNpc = null
        activeDefinition = null
        reserved = null
        timeline.finish(deps.mapClock.cycle)
    }

    fun forceWarning(): String {
        if (activeNpc?.isSlotAssigned == true) return "Maledictus is already active."
        val now = deps.mapClock.cycle
        timeline.forceWarning(now)
        reserved = null
        return if (issueWarning(now)) {
            "The live Maledictus warning was sent; spawn is due in 60 seconds."
        } else {
            timeline.scheduleAfter(now)
            "No valid Maledictus spawn footprint is currently available."
        }
    }

    fun forceSpawn(): String {
        if (activeNpc?.isSlotAssigned == true) return "Maledictus is already active."
        val now = deps.mapClock.cycle
        reserved =
            reserveSpawn() ?: return "No valid Maledictus spawn footprint is currently available."
        timeline.forceSpawn(now)
        return if (spawnReserved(now)) "Maledictus spawned through the live controller."
        else "The reserved Maledictus area became blocked before spawning."
    }

    fun forceDespawn(): String {
        val active = activeNpc ?: return "There is no active Maledictus."
        expire(active, deps.mapClock.cycle)
        return "The active Maledictus was safely despawned."
    }

    private fun issueWarning(now: Int): Boolean {
        if (activeNpc?.isSlotAssigned == true || timeline.warned) return false
        val selected = reserved ?: reserveSpawn() ?: return false
        reserved = selected
        timeline.markWarned()
        broadcast(
            "The Revenant Maledictus will spawn in the " +
                "${selected.definition.section.warningName} area of the Revenant Caves in 60 seconds!"
        )
        return true
    }

    private fun spawnReserved(now: Int): Boolean {
        if (activeNpc?.isSlotAssigned == true) return false
        val selected =
            reserved
                ?: reserveSpawn()
                ?: run {
                    timeline.scheduleAfter(now)
                    return false
                }
        val spawn =
            selected.takeIf { isSafeSpawn(it.tile) }
                ?: reserveSpawn(selected.definition.section)
                ?: run {
                    reserved = null
                    timeline.scheduleAfter(now)
                    return false
                }
        val type =
            requireNotNull(ServerCacheManager.getNpc(MALEDICTUS_NPC.asRSCM(RSCMType.NPC))) {
                "Active cache is missing $MALEDICTUS_NPC"
            }
        val npc =
            Npc(type, spawn.tile).also {
                it.mode = type.defaultMode
                deps.npcRepo.add(it, Int.MAX_VALUE)
                it.respawns = false
            }
        activeNpc = npc
        activeDefinition = spawn.definition
        reserved = spawn
        timeline.markSpawned(now)
        broadcast(
            "The Revenant Maledictus has appeared in the " +
                "${spawn.definition.section.warningName} area of the Revenant Caves!"
        )
        return true
    }

    private fun reserveSpawn(
        requiredSection: RevenantCaveSection? = null
    ): ReservedMaledictusSpawn? {
        val candidates =
            validatedSpawnTiles.flatMap { (definition, tiles) ->
                if (requiredSection != null && definition.section != requiredSection) {
                    emptyList()
                } else {
                    tiles.filter(::isSafeSpawn).map { ReservedMaledictusSpawn(definition, it) }
                }
            }
        return deps.random.pickOrNull(candidates)
    }

    private fun validateSpawnAreas() {
        validatedSpawnTiles =
            MALEDICTUS_SPAWNS.mapNotNull { definition ->
                    val safeTiles = discoverStaticSpawnTiles(definition)
                    if (safeTiles.isEmpty()) null else definition to safeTiles
                }
                .toMap()
        check(validatedSpawnTiles.isNotEmpty()) {
            "No wall-free, multi-combat Maledictus spawn footprints exist in the active map."
        }
        val missing = MALEDICTUS_SPAWNS.filterNot(validatedSpawnTiles::containsKey)
        check(missing.isEmpty()) {
            "Maledictus spawn areas have no wall-free 5x5 footprint: " +
                missing.joinToString { it.announcementName }
        }
        logger.info {
            "Validated ${validatedSpawnTiles.size} Maledictus multi-combat spawn areas: " +
                validatedSpawnTiles.entries.joinToString { (definition, tiles) ->
                    "${definition.section.warningName}=" + tiles.joinToString { "${it.x},${it.z}" }
                }
        }
    }

    private fun discoverStaticSpawnTiles(definition: MaledictusSpawn): List<CoordGrid> {
        val maxX = definition.roamArea.max.x - MALEDICTUS_SIZE + 1
        val maxZ = definition.roamArea.max.z - MALEDICTUS_SIZE + 1
        if (maxX < definition.roamArea.min.x || maxZ < definition.roamArea.min.z) return emptyList()
        return sequence {
                for (x in definition.roamArea.min.x..maxX) {
                    for (z in definition.roamArea.min.z..maxZ) {
                        yield(CoordGrid(x, z, definition.tile.level))
                    }
                }
            }
            .filter(::isStaticSafeSpawn)
            .sortedWith(
                compareBy<CoordGrid> { it.chebyshevDistance(definition.tile) }
                    .thenBy { it.x }
                    .thenBy { it.z }
            )
            .take(SAFE_TILES_PER_AREA)
            .toList()
    }

    private fun isSafeSpawn(tile: CoordGrid): Boolean =
        isStaticSafeSpawn(tile) && !overlapsNpc(tile)

    /**
     * A tile-only check is insufficient for a size-five NPC: cave walls are edge collision and do
     * not necessarily set BLOCK_WALK on either adjoining tile. Validate every tile and internal
     * edge, then require two full-size exits so the boss cannot be reserved in a wall or corridor.
     */
    private fun isStaticSafeSpawn(tile: CoordGrid): Boolean {
        val footprint = buildList {
            for (dx in 0 until MALEDICTUS_SIZE) {
                for (dz in 0 until MALEDICTUS_SIZE) add(tile.translate(dx, dz))
            }
        }
        if (
            footprint.any {
                !areaChecker.inArea(REVENANT_CAVES_AREA, it) ||
                    !areaChecker.inArea(MULTIWAY_AREA, it) ||
                    !deps.collision.isZoneValid(it) ||
                    deps.collision.isWalkBlocked(it)
            }
        ) {
            return false
        }
        for (dx in 0 until MALEDICTUS_SIZE) {
            for (dz in 0 until MALEDICTUS_SIZE) {
                val current = tile.translate(dx, dz)
                if (dx < MALEDICTUS_SIZE - 1 && !deps.collision.canStep(current, Direction.East)) {
                    return false
                }
                if (dz < MALEDICTUS_SIZE - 1 && !deps.collision.canStep(current, Direction.North)) {
                    return false
                }
            }
        }
        val fullSizeExits =
            CARDINAL_DIRECTIONS.count { direction ->
                val destination = tile.translate(direction.xOff, direction.zOff)
                deps.collision.canStep(tile, direction, size = MALEDICTUS_SIZE) &&
                    footprint(destination).all {
                        areaChecker.inArea(REVENANT_CAVES_AREA, it) &&
                            areaChecker.inArea(MULTIWAY_AREA, it)
                    }
            }
        return fullSizeExits >= MIN_FULL_SIZE_EXITS
    }

    private fun footprint(tile: CoordGrid): List<CoordGrid> = buildList {
        for (dx in 0 until MALEDICTUS_SIZE) {
            for (dz in 0 until MALEDICTUS_SIZE) add(tile.translate(dx, dz))
        }
    }

    private fun overlapsNpc(tile: CoordGrid): Boolean {
        val maxX = tile.x + MALEDICTUS_SIZE - 1
        val maxZ = tile.z + MALEDICTUS_SIZE - 1
        return deps.npcRepo
            .findAll(ZoneKey.from(tile), 1)
            .filter { it.isSlotAssigned }
            .any { npc ->
                val npcMaxX = npc.coords.x + npc.visType.size - 1
                val npcMaxZ = npc.coords.z + npc.visType.size - 1
                npc.coords.x <= maxX &&
                    npcMaxX >= tile.x &&
                    npc.coords.z <= maxZ &&
                    npcMaxZ >= tile.z
            }
    }

    private fun expire(npc: Npc, now: Int) {
        if (activeNpc !== npc) return
        deps.encounterRegistry.remove(npc)
        if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        activeNpc = null
        activeDefinition = null
        reserved = null
        timeline.finish(now)
        broadcast("The Revenant Maledictus has faded away from the Revenant Caves.")
    }

    private fun clearActiveAndSchedule(now: Int) {
        activeNpc?.let(deps.encounterRegistry::remove)
        activeNpc = null
        activeDefinition = null
        reserved = null
        timeline.finish(now)
    }

    private fun rewardTopDamager(player: Player, npc: Npc) {
        val artifactId = if (deps.random.of(2) == 0) ANCIENT_EMBLEM_ID else ANCIENT_TOTEM_ID
        val artifact =
            ServerCacheManager.getItem(artifactId)
                ?: error("Active cache is missing Maledictus artefact item $artifactId")
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add(artifact, npc.coords, duration, player)
        repeat(MaledictusRewardConfig.REVENANT_DRAGON_ROLLS) {
            val entry =
                MaledictusRewardConfig.selectDragonMain(
                    deps.random.of(MaledictusRewardConfig.dragonMainTotalWeight)
                )
            val count =
                if (entry.quantity.first == entry.quantity.last) {
                    entry.quantity.first
                } else {
                    deps.random.of(entry.quantity)
                }
            objRepo.add(entry.obj, npc.coords, duration, player, count)
        }
        player.mes(
            "Your damage earned an ancient artefact and two revenant dragon loot rolls."
        )
    }

    private fun rewardParticipant(player: Player) {
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add(MaledictusRewardConfig.PARTICIPANT_RESTORE, player.coords, duration, player)
        repeat(MaledictusRewardConfig.PARTICIPANT_FOOD_COUNT) {
            val food = deps.random.pick(MaledictusRewardConfig.PARTICIPANT_FOOD)
            objRepo.add(food, player.coords, duration, player)
        }
        player.mes("Your contribution earned you blighted supplies from the Maledictus.")
    }

    private fun broadcast(message: String) {
        deps.playerList.forEach { it.mes(message, ChatType.Broadcast) }
    }

    internal fun snapshot(): MaledictusWorldSnapshot {
        val now = deps.mapClock.cycle
        return MaledictusWorldSnapshot(
            state =
                when {
                    activeNpc?.isSlotAssigned == true -> "ACTIVE"
                    timeline.warned -> "WARNING"
                    else -> "PENDING"
                },
            area =
                (activeDefinition ?: reserved?.definition)?.section?.warningName?.let {
                    "$it area of the Revenant Caves"
                },
            activeTile = activeNpc?.takeIf { it.isSlotAssigned }?.coords,
            ticksUntilWarning = timeline.ticksUntilWarning(now),
            ticksUntilSpawn = timeline.ticksUntilSpawn(now),
            ticksUntilDespawn = timeline.ticksUntilDespawn(now),
        )
    }
}

internal class MaledictusTimeline {
    var warningTick: Int = Int.MAX_VALUE
        private set

    var spawnTick: Int = Int.MAX_VALUE
        private set

    var despawnTick: Int = Int.MAX_VALUE
        private set

    var warned: Boolean = false
        private set

    var active: Boolean = false
        private set

    fun scheduleAfter(now: Int) {
        warningTick = now + MALEDICTUS_WARNING_DELAY_TICKS
        spawnTick = now + MALEDICTUS_SPAWN_INTERVAL_TICKS
        despawnTick = Int.MAX_VALUE
        warned = false
        active = false
    }

    fun forceWarning(now: Int) {
        warningTick = now
        spawnTick = now + MALEDICTUS_WARNING_LEAD_TICKS
        despawnTick = Int.MAX_VALUE
        warned = false
        active = false
    }

    fun forceSpawn(now: Int) {
        warningTick = Int.MAX_VALUE
        spawnTick = now
        despawnTick = Int.MAX_VALUE
        warned = true
        active = false
    }

    fun markWarned() {
        warned = true
    }

    fun markSpawned(now: Int) {
        active = true
        despawnTick = now + MALEDICTUS_ACTIVE_LIFETIME_TICKS
    }

    fun finish(now: Int) = scheduleAfter(now)

    fun dueWarning(now: Int): Boolean = !active && !warned && now >= warningTick

    fun dueSpawn(now: Int): Boolean = !active && warned && now >= spawnTick

    fun dueDespawn(now: Int): Boolean = active && now >= despawnTick

    fun ticksUntilWarning(now: Int): Int? =
        if (!active && !warned) (warningTick - now).coerceAtLeast(0) else null

    fun ticksUntilSpawn(now: Int): Int? = if (!active) (spawnTick - now).coerceAtLeast(0) else null

    fun ticksUntilDespawn(now: Int): Int? =
        if (active) (despawnTick - now).coerceAtLeast(0) else null
}

internal data class MaledictusWorldSnapshot(
    val state: String,
    val area: String?,
    val activeTile: CoordGrid?,
    val ticksUntilWarning: Int?,
    val ticksUntilSpawn: Int?,
    val ticksUntilDespawn: Int?,
)

internal enum class RevenantCaveSection {
    North,
    Middle,
    South,
}

internal val RevenantCaveSection.warningName: String
    get() =
        when (this) {
            RevenantCaveSection.North -> "northern"
            RevenantCaveSection.Middle -> "middle"
            RevenantCaveSection.South -> "southern"
        }

internal data class MaledictusRoamArea(val min: CoordGrid, val max: CoordGrid)

internal data class MaledictusSpawn(
    val tile: CoordGrid,
    val section: RevenantCaveSection,
    val compassDescription: String,
    val wildernessSection: String,
    val announcementName: String,
    val roamArea: MaledictusRoamArea,
    val fallbackTiles: List<CoordGrid>,
)

private data class ReservedMaledictusSpawn(val definition: MaledictusSpawn, val tile: CoordGrid)

internal const val SERVER_TICK_MILLIS = 600
internal const val MALEDICTUS_WARNING_LEAD_TICKS = 100
internal const val MALEDICTUS_SPAWN_INTERVAL_TICKS = 1_000
internal const val MALEDICTUS_WARNING_DELAY_TICKS =
    MALEDICTUS_SPAWN_INTERVAL_TICKS - MALEDICTUS_WARNING_LEAD_TICKS
internal const val MALEDICTUS_ACTIVE_LIFETIME_TICKS = 1_000
private const val REVENANT_CAVES_AREA = "area.revenant_caves"
private const val MULTIWAY_AREA = "area.multiway"
private const val MALEDICTUS_NPC = "npc.wild_cave_superior"
private const val MALEDICTUS_INTERNAL_NAME = "wild_cave_superior"
private const val MALEDICTUS_SIZE = 5
private const val SAFE_TILES_PER_AREA = 4
private const val MIN_FULL_SIZE_EXITS = 2
private const val ANCIENT_EMBLEM_ID = 21_807
private const val ANCIENT_TOTEM_ID = 21_810

internal data class MaledictusRewardEntry(
    val obj: String,
    val quantity: IntRange,
    val weight: Int,
)

/** Central reward configuration so the planned future drop rebalance has one edit point. */
internal object MaledictusRewardConfig {
    const val REVENANT_DRAGON_ROLLS: Int = 2
    const val PARTICIPANT_RESTORE: String = "obj.blighted_4dose2restore"
    const val PARTICIPANT_FOOD_COUNT: Int = 2
    val PARTICIPANT_FOOD: List<String> =
        listOf("obj.blighted_mantaray", "obj.blighted_anglerfish", "obj.blighted_karambwan")

    val DRAGON_MAIN: List<MaledictusRewardEntry> =
        listOf(
            MaledictusRewardEntry("obj.wild_cave_bracelet_uncharged", 1..1, 30),
            MaledictusRewardEntry("obj.battlestaff", 4..4, 10),
            MaledictusRewardEntry("obj.rune_full_helm", 2..2, 4),
            MaledictusRewardEntry("obj.rune_platebody", 2..2, 4),
            MaledictusRewardEntry("obj.rune_platelegs", 2..2, 4),
            MaledictusRewardEntry("obj.rune_kiteshield", 2..2, 4),
            MaledictusRewardEntry("obj.rune_warhammer", 2..2, 4),
            MaledictusRewardEntry("obj.dragon_platelegs", 1..2, 1),
            MaledictusRewardEntry("obj.dragon_plateskirt", 1..2, 1),
            MaledictusRewardEntry("obj.dragon_dagger", 2..2, 2),
            MaledictusRewardEntry("obj.dragon_longsword", 2..2, 2),
            MaledictusRewardEntry("obj.blighted_4dose2restore", 1..3, 6),
            MaledictusRewardEntry("obj.blighted_mantaray", 10..15, 8),
            MaledictusRewardEntry("obj.cert_coal", 30..60, 12),
            MaledictusRewardEntry("obj.adamantite_bar", 4..6, 12),
            MaledictusRewardEntry("obj.runite_ore", 2..4, 8),
            MaledictusRewardEntry("obj.runite_bar", 2..3, 6),
            MaledictusRewardEntry("obj.cert_yew_logs", 20..40, 8),
            MaledictusRewardEntry("obj.cert_magic_logs", 8..16, 4),
            MaledictusRewardEntry("obj.uncut_dragonstone", 2..5, 2),
            MaledictusRewardEntry("obj.lawrune", 20..45, 10),
            MaledictusRewardEntry("obj.deathrune", 30..60, 10),
            MaledictusRewardEntry("obj.bloodrune", 50..100, 10),
        )

    val dragonMainTotalWeight: Int = DRAGON_MAIN.sumOf(MaledictusRewardEntry::weight)

    fun selectDragonMain(roll: Int): MaledictusRewardEntry {
        require(roll in 0 until dragonMainTotalWeight)
        var remaining = roll
        for (entry in DRAGON_MAIN) {
            if (remaining < entry.weight) return entry
            remaining -= entry.weight
        }
        error("Unreachable Maledictus reward roll $roll")
    }
}

private val CARDINAL_DIRECTIONS =
    listOf(Direction.North, Direction.East, Direction.South, Direction.West)

internal val MALEDICTUS_SPAWNS =
    listOf(
        maledictusSpawn(
            3160,
            10188,
            RevenantCaveSection.North,
            "north-western",
            "level 30",
            3154,
            10182,
            3189,
            10211,
            3165 to 10188,
            3170 to 10188,
        ),
        maledictusSpawn(
            3222,
            10204,
            RevenantCaveSection.North,
            "north-eastern",
            "level 35",
            3216,
            10192,
            3257,
            10225,
            3227 to 10207,
            3235 to 10204,
        ),
        maledictusSpawn(
            3206,
            10208,
            RevenantCaveSection.North,
            "northern",
            "level 34",
            3198,
            10188,
            3227,
            10228,
            3201 to 10212,
            3212 to 10208,
        ),
        maledictusSpawn(
            3233,
            10167,
            RevenantCaveSection.Middle,
            "central-eastern",
            "level 30",
            3228,
            10158,
            3258,
            10185,
            3238 to 10167,
            3232 to 10173,
        ),
        maledictusSpawn(
            3158,
            10107,
            RevenantCaveSection.South,
            "south-western",
            "level 24",
            3147,
            10103,
            3180,
            10129,
            3163 to 10107,
            3155 to 10114,
        ),
        maledictusSpawn(
            3192,
            10108,
            RevenantCaveSection.South,
            "central-southern",
            "level 25",
            3186,
            10102,
            3211,
            10125,
            3198 to 10108,
            3203 to 10109,
        ),
        maledictusSpawn(
            3174,
            10148,
            RevenantCaveSection.Middle,
            "western",
            "level 28",
            3163,
            10142,
            3193,
            10167,
            3179 to 10148,
            3170 to 10155,
        ),
        maledictusSpawn(
            3205,
            10157,
            RevenantCaveSection.Middle,
            "central",
            "level 29",
            3194,
            10152,
            3224,
            10177,
            3210 to 10157,
            3197 to 10161,
        ),
        maledictusSpawn(
            3218,
            10123,
            RevenantCaveSection.Middle,
            "north-central",
            "level 27",
            3207,
            10118,
            3239,
            10142,
            3224 to 10123,
            3210 to 10124,
        ),
        maledictusSpawn(
            3244,
            10137,
            RevenantCaveSection.Middle,
            "eastern",
            "level 28",
            3237,
            10131,
            3260,
            10158,
            3249 to 10142,
            3239 to 10145,
        ),
        maledictusSpawn(
            3213,
            10079,
            RevenantCaveSection.South,
            "southern",
            "level 20",
            3199,
            10063,
            3235,
            10101,
            3207 to 10083,
            3219 to 10083,
        ),
    )

private fun maledictusSpawn(
    x: Int,
    z: Int,
    section: RevenantCaveSection,
    compass: String,
    wilderness: String,
    minX: Int,
    minZ: Int,
    maxX: Int,
    maxZ: Int,
    vararg fallbacks: Pair<Int, Int>,
): MaledictusSpawn =
    MaledictusSpawn(
        tile = CoordGrid(x, z),
        section = section,
        compassDescription = compass,
        wildernessSection = wilderness,
        announcementName = "the $compass, $wilderness section of the Revenant Caves",
        roamArea = MaledictusRoamArea(CoordGrid(minX, minZ), CoordGrid(maxX, maxZ)),
        fallbackTiles = fallbacks.map { (fallbackX, fallbackZ) -> CoordGrid(fallbackX, fallbackZ) },
    )
