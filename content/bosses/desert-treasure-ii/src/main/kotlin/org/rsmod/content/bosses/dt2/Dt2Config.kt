package org.rsmod.content.bosses.dt2

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val DT2_INSTANCE_PREFIX = "desert_treasure_ii_"
/** Long-lived instance NPCs are replaced by the encounter manager, never the public respawner. */
internal const val DT2_NPC_LIFETIME = Int.MAX_VALUE - 1
internal const val DT2_DROP_DURATION = 18_000
internal const val DT2_RESPAWN_TICKS = 32
/** One server tick is 600ms; 17 ticks is the closest representable interval to ten seconds. */
internal const val WHISPERER_RESPAWN_TICKS = 17
internal const val VARDORVIS_RESPAWN_TICKS = 17
internal const val VARDORVIS_QTE_INTERFACE = 833
internal const val LEVIATHAN_RESPAWN_TICKS = 36
internal const val DT2_SANITY_INTERFACE = 834

internal enum class Dt2Difficulty {
    NORMAL,
    AWAKENED,
}

internal enum class Dt2Boss(
    val displayName: String,
    val command: String,
    val alias: String,
    val regionId: Int,
    val enter: RegionLocal,
    val exit: CoordGrid,
    val spawn: RegionLocal,
    val normalNpc: String,
    val normalNpcId: Int,
    val awakenedNpcId: Int,
    val normalHitpoints: Int,
    val awakenedHitpoints: Int,
) {
    WHISPERER(
        displayName = "The Whisperer",
        command = "whisperer",
        alias = "whisp",
        regionId = 10595,
        enter = RegionLocal(0, 41, 99, 32, 46),
        exit = CoordGrid(2683, 6433, 0),
        spawn = RegionLocal(0, 41, 99, 31, 32),
        normalNpc = "npc.whisperer",
        normalNpcId = 12204,
        awakenedNpcId = 12204,
        normalHitpoints = 900,
        awakenedHitpoints = 2_700,
    ),
    VARDORVIS(
        displayName = "Vardorvis",
        command = "vardorvis",
        alias = "vard",
        regionId = 4405,
        enter = RegionLocal(0, 17, 53, 31, 36),
        exit = CoordGrid(1117, 3428, 0),
        spawn = RegionLocal(0, 17, 53, 40, 25),
        normalNpc = "npc.vardorvis",
        normalNpcId = 12223,
        awakenedNpcId = 12223,
        normalHitpoints = 700,
        awakenedHitpoints = 1_400,
    ),
    LEVIATHAN(
        displayName = "The Leviathan",
        command = "leviathan",
        alias = "lev",
        regionId = 8291,
        enter = RegionLocal(0, 32, 99, 19, 32),
        exit = CoordGrid(2067, 6370, 0),
        spawn = RegionLocal(0, 32, 99, 30, 33),
        normalNpc = "npc.leviathan",
        normalNpcId = 12214,
        awakenedNpcId = 12214,
        normalHitpoints = 900,
        awakenedHitpoints = 2_700,
    ),
    DUKE(
        displayName = "Duke Sucellus",
        command = "duke",
        alias = "dt2di",
        regionId = 12132,
        enter = RegionLocal(0, 47, 100, 31, 33),
        exit = CoordGrid(3039, 6432, 0),
        spawn = RegionLocal(0, 47, 100, 28, 52),
        normalNpc = "npc.duke_sucellus_awake",
        normalNpcId = 12191,
        awakenedNpcId = 12195,
        normalHitpoints = 485,
        awakenedHitpoints = 1_697,
    );

    val key: String
        get() = DT2_INSTANCE_PREFIX + command

    fun hitpoints(difficulty: Dt2Difficulty) =
        if (difficulty == Dt2Difficulty.AWAKENED) awakenedHitpoints else normalHitpoints
}

internal fun Dt2Boss.area(): InstanceArea =
    InstanceArea.copyRegions(
        regionIds = listOf(regionId),
        enterCoord = enter,
        exitCoord = exit,
        copyAllLevels = this == Dt2Boss.LEVIATHAN || this == Dt2Boss.DUKE,
    )

internal fun Dt2Boss.spec(activeType: dev.openrune.types.NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = displayName,
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private repeatable $displayName encounter",
            // Leviathan currently has only an administrator/debug entry route. Enable records
            // when a verified production entry and reward flow is connected.
            recordsEligible = this != Dt2Boss.LEVIATHAN,
        )
        .withArea(area(), settingsRowId = 0)

internal enum class Dt2State {
    PREPARING,
    STARTING,
    ACTIVE,
    SPECIAL,
    STUNNED,
    ENRAGED,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

internal enum class Dt2AttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal enum class WhispererSpecial {
    CORRUPTED_SEEDS,
    SCREECH,
    SOUL_SIPHON,
}

internal enum class VardorvisSpecial {
    AXES,
    DARTING_SPIKES,
    HEAD_GAZE,
    STRANGLE,
}

internal enum class LeviathanSpecial {
    VOLLEY_ROAR,
    LIGHTNING_BARRAGE,
    SMOKE_BLAST,
}

internal enum class DukeSpecial {
    FREEZING_GAZE,
    GAS_FLARE,
    BILE,
    ORB_OF_DARKNESS,
}

internal object Dt2Assets {
    const val WHISPERER_SPAWN_ID = 12203
    const val WHISPERER_ACTIVE_ID = 12204
    const val WHISPERER_MELEE_ID = 12205
    const val WHISPERER_SPAWN = "npc.whisperer_spawn"
    const val WHISPERER = "npc.whisperer"
    const val WHISPERER_MELEE = "npc.whisperer_melee"
    const val WHISPERER_TENTACLE = 12208
    const val WHISPERER_SOUL = 12211
    const val WHISPERER_SOUL_VULNERABLE = 12212
    const val WHISPERER_SCREECH_SHADOW = 12209
    const val WHISPERER_SCREECH_SAFE = 12210
    const val WHISPERER_SEED_SHADOW_WEAK = 47573
    const val WHISPERER_SEED_SHADOW_DANGER = 47574
    const val WHISPERER_SEED_REGULAR = 47575
    const val WHISPERER_SCREECH_PILLAR = 47577
    const val WHISPERER_RANGED_PROJECTILE = 2444
    const val WHISPERER_MAGIC_PROJECTILE = 2445
    const val WHISPERER_MAGIC_IMPACT = 2446

    const val VARDORVIS = "npc.vardorvis"
    const val VARDORVIS_LARGE_TENDRIL = 12225
    const val VARDORVIS_STATIC_AXE = 12229
    const val VARDORVIS_EXIT = 47603
    const val VARDORVIS_EXIT_DISABLED = 47604
    const val VARDORVIS_MELEE_ANIM = 10340
    const val VARDORVIS_DASH_ANIM = 10341
    const val VARDORVIS_STRANGLE_START_ANIM = 10342
    const val VARDORVIS_STRANGLE_LOOP_ANIM = 10343
    const val VARDORVIS_STRANGLE_END_ANIM = 10344
    const val VARDORVIS_SPAWN_ANIM = 10345
    const val VARDORVIS_DEATH_ANIM = 10346
    const val VARDORVIS_HEAD_ANIM = 10348
    const val VARDORVIS_HEAD_PROJECTILE = 2521
    const val VARDORVIS_HEAD_CAST = 2550
    const val VARDORVIS_HEAD_IMPACT = 2552
    val VARDORVIS_SAFE_ENTRY = RegionLocal(0, 17, 53, 31, 36)
    val VARDORVIS_EXIT_TILE = RegionLocal(0, 17, 53, 30, 36)
    const val VARDORVIS_AXE = 12227
    const val VARDORVIS_HEAD = 12226

    const val LEVIATHAN = "npc.leviathan"
    const val LEVIATHAN_TAIL_WEST = 12216
    const val LEVIATHAN_TAIL_SOUTH = 12217
    const val LEVIATHAN_TAIL_EAST = 12218
    const val LEVIATHAN_PATHFINDER = 12220
    const val LEVIATHAN_TORNADO = 12222

    const val LEVIATHAN_START_HANDHOLDS = 47593
    const val LEVIATHAN_SCOREBOARD = 49475
    const val LEVIATHAN_EXIT_ROWBOAT = 49213
    val LEVIATHAN_INSIDE_ENTRY = RegionLocal(0, 32, 99, 23, 32)
    const val LEVIATHAN_EXIT_ROWBOAT_ACTIVE = 49214
    val LEVIATHAN_EXIT_ROWBOATS = listOf(LEVIATHAN_EXIT_ROWBOAT, LEVIATHAN_EXIT_ROWBOAT_ACTIVE)
    // The live capture crosses local z=40, but the revision-240 stair collision
    // blocks that tile in the copied room. Start on the last reachable tile so
    // the player never clips into the old forced-teleport boundary.
    val LEVIATHAN_COMBAT_TRIGGER = RegionLocal(0, 32, 99, 27, 39)
    val LEVIATHAN_PATHFINDER_START = RegionLocal(0, 32, 99, 26, 41)
    val LEVIATHAN_TORNADO_START = RegionLocal(0, 32, 99, 41, 44)
    val LEVIATHAN_TAIL_ACTORS =
        listOf(
            LEVIATHAN_TAIL_WEST to RegionLocal(0, 32, 99, 5, 19),
            LEVIATHAN_TAIL_WEST to RegionLocal(0, 32, 99, 47, 21),
            LEVIATHAN_TAIL_WEST to RegionLocal(0, 32, 99, 27, 57),
            LEVIATHAN_TAIL_SOUTH to RegionLocal(0, 32, 99, 31, 10),
            LEVIATHAN_TAIL_EAST to RegionLocal(0, 32, 99, 48, 53),
        )

    const val LEVIATHAN_DEBRIS_BLOCKER = 47590
    const val LEVIATHAN_DEBRIS_WARNING = 2475
    const val LEVIATHAN_DEBRIS_TRANSIENT = 2476
    const val LEVIATHAN_DEBRIS_BREAK = 2474
    const val LEVIATHAN_TORNADO_GRAPHIC = 2507
    const val LEVIATHAN_RANGED_CAST = 2484
    const val LEVIATHAN_RANGED_PROJECTILE = 2487
    const val LEVIATHAN_MELEE_CAST = 2485
    const val LEVIATHAN_MELEE_PROJECTILE = 2488
    const val LEVIATHAN_MAGIC_CAST = 2486
    const val LEVIATHAN_MAGIC_PROJECTILE = 2489
    const val LEVIATHAN_RANGED_IMPACT = 2490
    const val LEVIATHAN_MELEE_IMPACT = 2491
    const val LEVIATHAN_MAGIC_IMPACT = 2492
    const val LEVIATHAN_CURRENT_CHARGE = 2493
    const val LEVIATHAN_CURRENT_IMPACT = 2494
    const val LEVIATHAN_MIDI = 750
    const val LEVIATHAN_APPEARANCE_SOUND = 7037
    const val LEVIATHAN_MAGIC_ATTACK_SOUND = 7030
    const val LEVIATHAN_RANGED_ATTACK_SOUND = 7041
    const val LEVIATHAN_ROAR_SOUND = 7043
    const val LEVIATHAN_DEBRIS_LAND_SOUND = 3308
    const val LEVIATHAN_DEBRIS_RUMBLE_SOUND = 7035
    const val LEVIATHAN_LIGHTNING_CHARGE = 2503
    const val LEVIATHAN_LIGHTNING_IMPACT = 2504
    const val LEVIATHAN_LIGHTNING_PROJECTILE = 2506
    const val LEVIATHAN_SMOKE_EXPLOSION = 2505
    val LEVIATHAN_DEBRIS = listOf(2477, 2478, 2479, 2480)

    const val DUKE_SLEEPING = "npc.duke_sucellus_asleep"
    const val DUKE_AWAKE = "npc.duke_sucellus_awake"
    const val DUKE_ORB = 11708

    val bossForms =
        listOf(
            WHISPERER_SPAWN,
            WHISPERER,
            WHISPERER_MELEE,
            VARDORVIS,
            LEVIATHAN,
            DUKE_SLEEPING,
            DUKE_AWAKE,
        )
    val helperNpcIds =
        listOf(
            WHISPERER_TENTACLE,
            WHISPERER_SOUL,
            WHISPERER_SOUL_VULNERABLE,
            WHISPERER_SCREECH_SHADOW,
            WHISPERER_SCREECH_SAFE,
            VARDORVIS_LARGE_TENDRIL,
            VARDORVIS_STATIC_AXE,
            VARDORVIS_AXE,
            VARDORVIS_HEAD,
            LEVIATHAN_TAIL_WEST,
            LEVIATHAN_TAIL_SOUTH,
            LEVIATHAN_TAIL_EAST,
            LEVIATHAN_PATHFINDER,
            LEVIATHAN_TORNADO,
            DUKE_ORB,
        )

    val sequences =
        listOf(
            10174,
            10175,
            10176,
            10177,
            10178,
            10179,
            10180,
            10181,
            10182,
            10227,
            10228,
            10229,
            10230,
            10234,
            10235,
            10236,
            10237,
            10238,
            10239,
            10240,
            10241,
            10242,
            10243,
            10244,
            10245,
            10246,
            10247,
            10248,
            10250,
            10252,
            10254,
            10256,
            10259,
            10260,
            10261,
            10263,
            10264,
            10265,
            10266,
            10267,
            10268,
            10269,
            10270,
            10276,
            10278,
            10279,
            10280,
            10281,
            10282,
            10283,
            10284,
            10285,
            10286,
            10287,
            10288,
            10289,
            10290,
            10291,
            10293,
            10294,
            10296,
            10334,
            10335,
            10336,
            10346,
            10166,
            10337,
            10338,
            10340,
            10341,
            10342,
            10343,
            10344,
            10345,
            10348,
            10353,
            10354,
            10355,
            10364,
            10365,
            10366,
            5542,
            5545,
            9047,
            9799,
        )

    val spotanims =
        listOf(
            2298,
            2431,
            2432,
            2433,
            2434,
            2436,
            2444,
            2445,
            2446,
            2447,
            2448,
            2449,
            2450,
            2451,
            2452,
            2453,
            2454,
            2455,
            2456,
            2457,
            2458,
            2459,
            2460,
            2461,
            2462,
            2463,
            2464,
            2465,
            2466,
            2467,
            2468,
            2469,
            2470,
            2471,
            2472,
            2473,
            2474,
            2475,
            2476,
            2477,
            2478,
            2479,
            2480,
            2481,
            2482,
            2483,
            2484,
            2485,
            2486,
            2487,
            2488,
            2489,
            2490,
            2491,
            2492,
            2493,
            2494,
            2495,
            2496,
            2497,
            2498,
            2499,
            2500,
            2501,
            2502,
            2503,
            2504,
            2505,
            2506,
            2507,
            2508,
            2509,
            2511,
            2513,
            2514,
            2515,
            2516,
            2517,
            2518,
            2524,
            2549,
            2550,
            2551,
            2552,
            2510,
            2512,
            2520,
            2521,
            2523,
            2525,
            2527,
        )

    const val AWAKENERS_ORB = 28334
    const val MUSCA_MUSHROOM = 28341
    const val MUSCA_POWDER = 28342
    const val ARDER_MUSHROOM = 28345
    const val ARDER_POWDER = 28346
    const val SALAX_SALT = 28349
    const val ARDER_MUSCA_POISON = 28351
    const val BLACKSTONE_FRAGMENT = 28356
    const val PESTLE_AND_MORTAR = 233
    const val IRON_PICKAXE = 1267

    val preparationItems =
        listOf(
            MUSCA_MUSHROOM,
            MUSCA_POWDER,
            ARDER_MUSHROOM,
            ARDER_POWDER,
            SALAX_SALT,
            ARDER_MUSCA_POISON,
        )

    val exitLocs = listOf(47565, 47566, 47584, 47585, 47586, 47603, 47604) + LEVIATHAN_EXIT_ROWBOATS
    val whispererMechanicLocs =
        listOf(
            WHISPERER_SEED_SHADOW_WEAK,
            WHISPERER_SEED_SHADOW_DANGER,
            WHISPERER_SEED_REGULAR,
            WHISPERER_SCREECH_PILLAR,
        )
    val dukePrepLocs = listOf(47522, 47524, 47528, 47536, 47538, 47539, 47560, 47561)
}
