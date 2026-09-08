package org.rsmod.content.systems.breach

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import org.rsmod.map.CoordGrid

internal data class BreachLootEntry(
    val internalName: String,
    val minimum: Int,
    val maximum: Int,
    val weight: Int,
) {
    init {
        require(internalName.startsWith("obj."))
        require(minimum > 0 && maximum >= minimum && weight > 0)
    }
}

internal data class BreachBossConfig(
    val kind: BreachBossKind,
    val npcId: Int,
    val hitpoints: Int,
    val attackDelayTicks: Int,
    val maxHit: Int,
    val premiumLoot: List<BreachLootEntry>,
    val petNames: List<String>,
) {
    init {
        require(npcId > 0 && hitpoints > 0 && attackDelayTicks >= 4 && maxHit > 0)
        require(premiumLoot.isNotEmpty() && petNames.isNotEmpty())
    }
}

internal data class BreachLocation(
    val key: String,
    val displayName: String,
    val center: CoordGrid,
    val bossSpawn: CoordGrid,
    val objectTile: CoordGrid,
    val wildernessLevel: Int,
    val combatRadius: Int,
    val leashRadius: Int,
    val arrivalTile: CoordGrid,
) {
    init {
        require(center.level == 0 && bossSpawn.level == 0 && objectTile.level == 0)
        require(combatRadius >= 10 && leashRadius > combatRadius)
        require(center.chebyshevDistance(bossSpawn) <= combatRadius)
        require(center.chebyshevDistance(objectTile) <= combatRadius)
    }

    fun contains(tile: CoordGrid, radius: Int = combatRadius): Boolean =
        tile.level == center.level && tile.chebyshevDistance(center) <= radius
}

internal data class BreachConfig(
    val warningFiveMillis: Long,
    val warningOneMillis: Long,
    val openingMillis: Long,
    val activeMillis: Long,
    val collapseMillis: Long,
    val contributionRetentionTicks: Int,
    val recentDeathGraceTicks: Int,
    val noTargetResetTicks: Int,
    val rewardRadius: Int,
    val pointsPerDamage: Int,
    val petDenominator: Int,
    val hitpointsPerAttacker: Int,
    val maximumScaledHitpoints: Int,
    val ownerDropDurationTicks: Int,
    val ownerRevealTicks: Int,
    val bosses: Map<BreachBossKind, BreachBossConfig>,
    val locations: List<BreachLocation>,
    val contributorLoot: List<BreachLootEntry>,
) {
    companion object {
        fun load(): BreachConfig {
            val file =
                Paths.get(
                    System.getProperty(
                        "openrune.breach.config",
                        ".data/config/breach-system.properties",
                    )
                )
            val p = Properties()
            if (Files.isRegularFile(file)) Files.newInputStream(file).use(p::load)
            fun text(key: String, default: String): String =
                System.getProperty("openrune.breach.$key") ?: p.getProperty(key, default).trim()
            fun int(key: String, default: Int): Int = text(key, default.toString()).toInt()
            fun seconds(key: String, default: Int): Long = int(key, default) * 1_000L
            fun loot(key: String, default: String): List<BreachLootEntry> =
                text(key, default).split(';').filter(String::isNotBlank).map { token ->
                    val values = token.split(':').map(String::trim)
                    require(values.size == 3) { "$key entries must be obj.name:min-max:weight" }
                    val amount = values[1].split('-').map(String::toInt)
                    require(amount.size == 2)
                    BreachLootEntry(values[0], amount[0], amount[1], values[2].toInt())
                }
            fun boss(
                kind: BreachBossKind,
                prefix: String,
                npc: Int,
                baseHp: Int,
                delay: Int,
                maxHit: Int,
                premium: String,
                pets: String,
            ): BreachBossConfig =
                BreachBossConfig(
                    kind,
                    int("$prefix.npc-id", npc),
                    int("$prefix.hitpoints", baseHp),
                    int("$prefix.attack-delay-ticks", delay),
                    int("$prefix.max-hit", maxHit),
                    loot("$prefix.premium-loot", premium),
                    text("$prefix.pet-names", pets)
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank),
                )

            val bossList =
                listOf(
                    boss(
                        BreachBossKind.CERBERUS,
                        "cerberus",
                        13_657,
                        30_000,
                        7,
                        35,
                        "obj.primordial_crystal:1-1:1;obj.pegasian_crystal:1-1:1;obj.eternal_crystal:1-1:1;obj.smouldering_stone:1-1:1",
                        "obj.hell_pet",
                    ),
                    boss(
                        BreachBossKind.CORRUPTED_HUNLLEF,
                        "hunllef",
                        9_035,
                        30_000,
                        7,
                        40,
                        "obj.prif_weapon_seed_enhanced:1-1:1;obj.prif_armour_seed:1-1:3;obj.crystal_seed_old:1-1:3",
                        "obj.gauntletpet",
                    ),
                    boss(
                        BreachBossKind.WHISPERER,
                        "whisperer",
                        12_204,
                        30_000,
                        7,
                        38,
                        "obj.bellator_vestige:1-1:1;obj.soulreaper_axe_staff:1-1:1;obj.virtus_mask:1-1:2;obj.virtus_top:1-1:2;obj.virtus_legs:1-1:2",
                        "obj.whispererpet",
                    ),
                    boss(
                        BreachBossKind.VARDORVIS,
                        "vardorvis",
                        13_656,
                        30_000,
                        6,
                        36,
                        "obj.ultor_vestige:1-1:1;obj.soulreaper_axe_head:1-1:1;obj.virtus_mask:1-1:2;obj.virtus_top:1-1:2;obj.virtus_legs:1-1:2",
                        "obj.vardorvispet",
                    ),
                )
            val locations =
                listOf(
                    BreachLocation(
                        "bandit_outskirts",
                        "the Bandit Camp outskirts",
                        CoordGrid(3056, 3655, 0),
                        CoordGrid(3060, 3655, 0),
                        CoordGrid(3053, 3655, 0),
                        17,
                        10,
                        14,
                        CoordGrid(3056, 3643, 0),
                    ),
                    BreachLocation(
                        "southeast_wastes",
                        "the level 11 Wilderness plain",
                        CoordGrid(3200, 3600, 0),
                        CoordGrid(3204, 3600, 0),
                        CoordGrid(3197, 3600, 0),
                        11,
                        14,
                        20,
                        CoordGrid(3200, 3588, 0),
                    ),
                    BreachLocation(
                        "lava_maze_east",
                        "the eastern Lava Maze",
                        CoordGrid(3250, 3810, 0),
                        CoordGrid(3254, 3810, 0),
                        CoordGrid(3247, 3810, 0),
                        37,
                        10,
                        14,
                        CoordGrid(3250, 3798, 0),
                    ),
                    BreachLocation(
                        "rogues_east",
                        "the deep eastern Wilderness",
                        CoordGrid(3350, 3920, 0),
                        CoordGrid(3350, 3920, 0),
                        CoordGrid(3347, 3920, 0),
                        51,
                        10,
                        14,
                        CoordGrid(3350, 3908, 0),
                    ),
                )
            return BreachConfig(
                    warningFiveMillis = seconds("timeline.five-minute-warning-seconds", 300),
                    warningOneMillis = seconds("timeline.one-minute-warning-seconds", 60),
                    openingMillis = seconds("timeline.opening-seconds", 10),
                    activeMillis = seconds("timeline.active-minutes", 15) * 60,
                    collapseMillis = seconds("timeline.collapse-seconds", 5),
                    contributionRetentionTicks = int("contribution.retention-ticks", 300),
                    recentDeathGraceTicks = int("contribution.death-grace-ticks", 50),
                    noTargetResetTicks = int("combat.no-target-reset-ticks", 100),
                    rewardRadius = int("reward.radius", 18),
                    pointsPerDamage = int("reward.pk-points-per-damage", 4),
                    petDenominator = int("reward.pet-denominator", 750),
                    hitpointsPerAttacker = int("combat.hitpoints-per-attacker", 5_000),
                    maximumScaledHitpoints = int("combat.maximum-scaled-hitpoints", 65_000),
                    ownerDropDurationTicks = int("reward.drop-duration-ticks", 300),
                    ownerRevealTicks = int("reward.owner-reveal-ticks", 150),
                    bosses = bossList.associateBy(BreachBossConfig::kind),
                    locations = locations,
                    contributorLoot =
                        loot(
                            "reward.contributor-loot",
                            "obj.staff_of_light:1-1:1;obj.abyssal_dagger:1-1:1;obj.bloodrune:250-500:3;obj.deathrune:250-500:3;obj.soulrune:100-250:3;obj.mantaray:20-40:4;obj.mcannonball:250-500:4",
                        ),
                )
                .also { config ->
                    require(config.warningFiveMillis > config.warningOneMillis)
                    require(config.warningOneMillis > 0 && config.openingMillis > 0)
                    require(config.activeMillis > 0 && config.collapseMillis > 0)
                    require(config.contributionRetentionTicks > 0 && config.rewardRadius > 0)
                    require(config.pointsPerDamage > 0 && config.petDenominator > 0)
                    require(config.locations.size == 4 && config.bosses.size == 4)
                    require(config.hitpointsPerAttacker > 0)
                    require(
                        config.maximumScaledHitpoints >=
                            config.bosses.values.maxOf(BreachBossConfig::hitpoints)
                    )
                    require(config.ownerRevealTicks in 1 until config.ownerDropDurationTicks)
                }
        }
    }
}
