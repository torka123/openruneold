package org.rsmod.content.systems.voting

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import org.rsmod.map.CoordGrid

internal data class VoteBossArena(
    val name: String,
    val npcId: Int,
    val fallbackNpcIds: List<Int>,
    val spawn: CoordGrid,
    val teleport: CoordGrid,
    val min: CoordGrid,
    val max: CoordGrid,
    val wildernessLevel: Int,
    val hitpoints: Int,
    val attackDelayTicks: Int,
    val targetCap: Int,
    val enragePercent: Int,
    val maxHit: Int,
    val loot: List<VoteLootEntry>,
) {
    init {
        require(min.level == max.level && spawn.level == min.level && teleport.level == min.level)
        require(contains(spawn) && contains(teleport))
        require(hitpoints > 0 && attackDelayTicks > 0 && targetCap > 0)
        require(enragePercent in 1..99 && maxHit > 0)
        require(loot.isNotEmpty() && loot.all { it.weight > 0 })
    }

    fun contains(tile: CoordGrid): Boolean =
        tile.level == min.level && tile.x in min.x..max.x && tile.z in min.z..max.z
}

internal data class VoteLootEntry(
    val itemId: Int,
    val minimum: Int,
    val maximum: Int,
    val weight: Int,
    val rare: Boolean,
) {
    init {
        require(itemId >= 0)
        require(minimum > 0 && maximum >= minimum)
        require(weight > 0)
    }
}

internal data class VoteSystemConfig(
    val votePageUrl: String,
    val claimEndpoint: String,
    val apiToken: String,
    val apiTokenHeader: String,
    val allowInsecureApi: Boolean,
    val requestTimeout: Duration,
    val claimCooldownMillis: Long,
    val votePointsPerVote: Int,
    val rewardBoxItemId: Int,
    val rewardBoxesPerVote: Int,
    val raffleIntervalMillis: Long,
    val rafflePrizeItemId: Int,
    val rafflePrizeQuantity: Int,
    val threshold: Int,
    val progressMilestones: Set<Int>,
    val warningMillis: Long,
    val despawnMillis: Long,
    val voteBossPoints: Int,
    val minimumDamage: Int,
    val statePath: Path,
    val zuk: VoteBossArena,
    val zukAlternate: VoteBossArena,
    val sol: VoteBossArena,
) {
    fun arenas(kind: VoteBossKind): List<VoteBossArena> =
        if (kind == VoteBossKind.TZKAL_ZUK) listOf(zuk, zukAlternate) else listOf(sol)

    fun arena(kind: VoteBossKind, index: Int = 0): VoteBossArena {
        val arenas = arenas(kind)
        return arenas[Math.floorMod(index, arenas.size)]
    }

    companion object {
        fun load(): VoteSystemConfig {
            val path =
                Paths.get(
                    System.getProperty(
                        "openrune.vote.config",
                        ".data/config/vote-system.properties",
                    )
                )
            val p = Properties()
            if (Files.isRegularFile(path)) Files.newInputStream(path).use(p::load)
            fun text(key: String, default: String = ""): String =
                System.getProperty("openrune.vote.$key") ?: p.getProperty(key, default).trim()
            fun int(key: String, default: Int): Int = text(key, default.toString()).toInt()
            fun long(key: String, default: Long): Long = text(key, default.toString()).toLong()
            fun bool(key: String, default: Boolean): Boolean =
                text(key, default.toString()).toBooleanStrict()
            fun coord(key: String, default: String): CoordGrid {
                val values = text(key, default).split(',').map(String::trim).map(String::toInt)
                require(values.size == 3) { "$key must be x,z,plane" }
                return CoordGrid(values[0], values[1], values[2])
            }
            fun bounds(key: String, default: String): Pair<CoordGrid, CoordGrid> {
                val values = text(key, default).split(',').map(String::trim).map(String::toInt)
                require(values.size == 5) { "$key must be minX,minZ,maxX,maxZ,plane" }
                return CoordGrid(values[0], values[1], values[4]) to
                    CoordGrid(values[2], values[3], values[4])
            }
            fun loot(key: String, default: String): List<VoteLootEntry> =
                text(key, default).split(';').filter(String::isNotBlank).map { token ->
                    val values = token.split(':').map(String::trim)
                    require(values.size == 4) { "$key entry must be itemId:min-max:weight:rare" }
                    val quantity = values[1].split('-').map(String::toInt)
                    require(quantity.size == 2)
                    VoteLootEntry(
                        values[0].toInt(),
                        quantity[0],
                        quantity[1],
                        values[2].toInt(),
                        values[3].toBooleanStrict(),
                    )
                }
            fun arena(prefix: String, defaults: ArenaDefaults): VoteBossArena {
                val (min, max) = bounds("$prefix.bounds", defaults.bounds)
                return VoteBossArena(
                    name = text("$prefix.name", defaults.name),
                    npcId = int("$prefix.npc-id", defaults.npcId),
                    fallbackNpcIds =
                        text("$prefix.fallback-npc-ids", defaults.fallbackNpcIds)
                            .split(',')
                            .filter(String::isNotBlank)
                            .map(String::trim)
                            .map(String::toInt),
                    spawn = coord("$prefix.spawn", defaults.spawn),
                    teleport = coord("$prefix.teleport", defaults.teleport),
                    min = min,
                    max = max,
                    wildernessLevel = int("$prefix.wilderness-level", defaults.wildernessLevel),
                    hitpoints = int("$prefix.hitpoints", defaults.hitpoints),
                    attackDelayTicks = int("$prefix.attack-delay-ticks", defaults.attackDelayTicks),
                    targetCap = int("$prefix.target-cap", defaults.targetCap),
                    enragePercent = int("$prefix.enrage-percent", defaults.enragePercent),
                    maxHit = int("$prefix.max-hit", defaults.maxHit),
                    loot = loot("$prefix.loot", defaults.loot),
                )
            }

            val token =
                text("api.token.env", "VOTE_API_TOKEN")
                    .takeIf(String::isNotBlank)
                    ?.let(System::getenv) ?: text("api.token")
            val config =
                VoteSystemConfig(
                    votePageUrl = text("vote.url", "https://example.com/vote?username={username}"),
                    claimEndpoint = text("api.claim-url"),
                    apiToken = token,
                    apiTokenHeader = text("api.token-header", "Authorization"),
                    allowInsecureApi = bool("api.allow-insecure", false),
                    requestTimeout = Duration.ofSeconds(long("api.timeout-seconds", 10)),
                    claimCooldownMillis = long("claim.cooldown-seconds", 10) * 1_000L,
                    votePointsPerVote = int("reward.vote-points", 1),
                    rewardBoxItemId = int("reward.box-item-id", 6199),
                    rewardBoxesPerVote = int("reward.boxes-per-vote", 1),
                    raffleIntervalMillis = long("raffle.interval-hours", 12) * 3_600_000L,
                    rafflePrizeItemId = int("raffle.prize-item-id", 6199),
                    rafflePrizeQuantity = int("raffle.prize-quantity", 2),
                    threshold = int("boss.threshold", 100),
                    progressMilestones =
                        text("boss.progress-milestones", "25,50,75")
                            .split(',')
                            .filter(String::isNotBlank)
                            .map(String::trim)
                            .map(String::toInt)
                            .toSet(),
                    warningMillis = long("boss.warning-seconds", 60) * 1_000L,
                    despawnMillis = long("boss.despawn-minutes", 20) * 60_000L,
                    voteBossPoints = int("boss.reward-points", 5),
                    minimumDamage = int("boss.minimum-damage", 25),
                    statePath = Paths.get(text("state.path", ".data/vote-system/state.json")),
                    zuk = arena("zuk", ZUK_DEFAULTS),
                    zukAlternate = arena("zuk-2", ZUK_ALTERNATE_DEFAULTS),
                    sol = arena("sol", SOL_DEFAULTS),
                )
            require(config.threshold > 0 && config.minimumDamage > 0)
            require(config.votePointsPerVote > 0 && config.rewardBoxesPerVote > 0)
            require(config.rewardBoxItemId >= 0 && config.rafflePrizeItemId >= 0)
            require(config.rafflePrizeQuantity > 0 && config.raffleIntervalMillis > 0)
            require(config.voteBossPoints > 0 && config.despawnMillis > 0)
            require(config.claimCooldownMillis >= 0 && config.warningMillis >= 0)
            require(config.progressMilestones.all { it in 1 until config.threshold })
            require(
                config.claimEndpoint.isBlank() ||
                    config.allowInsecureApi ||
                    config.claimEndpoint.startsWith("https://")
            ) {
                "Vote claim endpoint must use HTTPS (or api.allow-insecure=true for local testing)."
            }
            return config
        }
    }
}

private data class ArenaDefaults(
    val name: String,
    val npcId: Int,
    val fallbackNpcIds: String,
    val spawn: String,
    val teleport: String,
    val bounds: String,
    val wildernessLevel: Int,
    val hitpoints: Int,
    val attackDelayTicks: Int,
    val targetCap: Int,
    val enragePercent: Int,
    val maxHit: Int,
    val loot: String,
)

private val ZUK_DEFAULTS =
    ArenaDefaults(
        "the western Lava Maze basin",
        7706,
        "",
        "3086,3852,0",
        "3096,3852,0",
        "3068,3834,3104,3870,0",
        42,
        1_200,
        8,
        12,
        35,
        ZUK_MAX_HIT,
        "995:100000-250000:50:false;6529:1000-3000:30:false;6570:1-1:1:true",
    )

private val ZUK_ALTERNATE_DEFAULTS =
    ArenaDefaults(
        "the eastern Lava Maze basin",
        7706,
        "",
        "3367,3935,0",
        "3352,3935,0",
        "3347,3915,3387,3955,0",
        52,
        1_200,
        8,
        12,
        35,
        ZUK_MAX_HIT,
        "995:100000-250000:50:false;6529:1000-3000:30:false;6570:1-1:1:true",
    )

private val SOL_DEFAULTS =
    ArenaDefaults(
        "Sol Heredit arena",
        12821,
        "12827",
        "3290,3866,0",
        "3286,3862,0",
        "3278,3856,3308,3882,0",
        44,
        1_500,
        7,
        12,
        35,
        42,
        "995:120000-300000:50:false;28924:100-300:30:false;28947:1-1:1:true",
    )
