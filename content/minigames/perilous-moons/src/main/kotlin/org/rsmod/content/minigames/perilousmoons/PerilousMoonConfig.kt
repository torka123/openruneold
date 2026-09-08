package org.rsmod.content.minigames.perilousmoons

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val MOON_MAX_HITPOINTS = 500
internal const val MOON_GAME_TICK_MILLIS = 600
internal const val MOON_STANDARD_ATTACKS_PER_SPECIAL = 6
internal const val MOON_ATTACKS_PER_GLYPH = 2
internal const val MOON_ATTACK_RATE = 6
/** OpenRune's deferred player-hit queue requires a strictly positive cycle delay. */
internal const val MOON_MECHANIC_HIT_DELAY = 1
/** Ten real seconds rounded up to the next authoritative 600 ms game tick. */
internal const val MOON_RESPAWN_TICKS = 17
/** Revision-240 npc.pmoon_boss_threat_circle occupies a 2x2 footprint. */
internal const val MOON_GLYPH_SIZE = 2
internal const val BLOOD_RAIN_MAX_HIT = 10
internal const val BLOOD_RAIN_PULSES = 6
internal const val BLOOD_JAGUAR_CYCLES = 7
internal const val BLOOD_JAGUAR_CYCLE_TICKS = 4
internal const val BLOOD_JAGUAR_CLEAVES = BLOOD_JAGUAR_CYCLES - 1
internal const val BLOOD_JAGUAR_POOL_SPAWN_OFFSET = 1
internal const val BLOOD_JAGUAR_POOL_DAMAGE_OFFSET = 3
internal const val BLOOD_JAGUAR_OUTWARD_TILES = 2
internal const val BLOOD_JAGUAR_MAX_SIPHON = 15
internal const val BLOOD_JAGUAR_MAX_PLAYER_HEAL = 25
internal const val BLUE_FREEZE_SAFE_ATTACKS = 2
internal const val BLUE_FREEZE_NO_WEAPON_MIN_HIT = 3
internal const val BLUE_FREEZE_NO_WEAPON_MAX_HIT = 15
internal const val BLUE_FREEZE_SPIKE_DELAY = 2
internal const val BLUE_FREEZE_MAX_FAILURE = 28
internal const val BLUE_STORM_HEAL_CHECKS = 5
internal const val ECLIPSE_CLONE_GROUPS = 5
internal const val ECLIPSE_CLONES_PER_GROUP = 3
internal const val ECLIPSE_CLONE_FLAT_ARMOUR = 4
internal const val MOON_CURSE_CAP = 127
internal const val MOON_CURSE_DECAY_TICKS = 5
internal const val MOON_NPC_LIFETIME = 60_000
internal const val MOON_NORMAL_VISIBLE_STATE = 0
internal const val MOON_ENRAGED_VISIBLE_STATE = 1

internal val MOON_SUBDUED_FLAGS = AttributeKey<Int>(persistenceKey = "perilous_moons_subdued_flags")
internal val ECLIPSE_UNIQUE_FLAGS =
    AttributeKey<Int>(persistenceKey = "perilous_moons_eclipse_unique_flags")
internal val BLOOD_UNIQUE_FLAGS =
    AttributeKey<Int>(persistenceKey = "perilous_moons_blood_unique_flags")
internal val BLUE_UNIQUE_FLAGS =
    AttributeKey<Int>(persistenceKey = "perilous_moons_blue_unique_flags")
internal val FROZEN_WEAPON_ID =
    AttributeKey<Int>(persistenceKey = "perilous_moons_frozen_weapon_id")
internal val FROZEN_WEAPON_COUNT =
    AttributeKey<Int>(persistenceKey = "perilous_moons_frozen_weapon_count")
internal val FROZEN_WEAPON_VARS =
    AttributeKey<Int>(persistenceKey = "perilous_moons_frozen_weapon_vars")

internal enum class MoonEncounterState {
    WAITING,
    SPAWNING,
    STANDARD_COMBAT,
    SPECIAL_ATTACK_ONE,
    SPECIAL_ATTACK_TWO,
    SUBDUED,
    RESPAWNING,
    RESETTING,
}

internal enum class MoonHelperRole {
    GLYPH,
    BLOOD_POOL,
    BLOOD_JAGUAR,
    ICE_BLOCK,
    WINTER_STORM,
    MOON_SHIELD,
    ECLIPSE_CLONE,
}

internal enum class PerilousMoon(
    val displayName: String,
    val bossNpc: String,
    val bossNpcId: Int,
    val decorativeNpcId: Int,
    /** Exact cache/map center marker. */
    val center: CoordGrid,
    /** Revision-240 map statue base tile and verified walkable hub-side approach tile. */
    val entryStatue: String,
    val entryStatueCoords: CoordGrid,
    val statueApproach: CoordGrid,
    val arrival: CoordGrid,
    val postFight: CoordGrid,
    val flatArmour: Int,
    val idleAnimation: String,
    val standardAnimation: String,
    val spawnAnimation: String,
    val disappearAnimation: String,
    val appearAnimation: String,
    val subdueAnimation: String,
    val uniqueItems: List<String>,
) {
    Blood(
        displayName = "Blood Moon",
        bossNpc = "npc.pmoon_boss_blood_moon",
        bossNpcId = 13016,
        decorativeNpcId = 13011,
        center = CoordGrid(1392, 9632, 0),
        entryStatue = MoonAssets.BLOOD_ENTRY_STATUE,
        entryStatueCoords = CoordGrid(1410, 9631, 0),
        statueApproach = CoordGrid(1411, 9631, 0),
        arrival = CoordGrid(1382, 9632, 0),
        postFight = CoordGrid(1385, 9585, 0),
        flatArmour = -2,
        idleAnimation = "seq.npc_djinn_01_idle",
        standardAnimation = "seq.npc_djinn_01_combo",
        spawnAnimation = "seq.npc_djinn_01_spawn",
        disappearAnimation = "seq.npc_djinn_01_teleport_idle_disappear",
        appearAnimation = "seq.npc_djinn_01_teleport_idle_appear",
        subdueAnimation = "seq.npc_djinn_01_ending",
        uniqueItems =
            listOf(
                "obj.dual_macuahuitl",
                "obj.blood_moon_helm",
                "obj.blood_moon_chestplate",
                "obj.blood_moon_tassets",
            ),
    ),
    Blue(
        displayName = "Blue Moon",
        bossNpc = "npc.pmoon_boss_blue_moon",
        bossNpcId = 13017,
        decorativeNpcId = 13013,
        center = CoordGrid(1440, 9680, 0),
        entryStatue = MoonAssets.BLUE_ENTRY_STATUE,
        entryStatueCoords = CoordGrid(1439, 9659, 0),
        statueApproach = CoordGrid(1439, 9658, 0),
        arrival = CoordGrid(1440, 9669, 0),
        postFight = CoordGrid(1395, 9589, 0),
        flatArmour = -5,
        idleAnimation = "seq.npc_djinn_01_idle",
        standardAnimation = "seq.npc_djinn_01_combo",
        spawnAnimation = "seq.npc_djinn_01_spawn",
        disappearAnimation = "seq.npc_djinn_01_teleport_idle_disappear",
        appearAnimation = "seq.npc_djinn_01_teleport_idle_appear",
        subdueAnimation = "seq.npc_djinn_01_ending",
        uniqueItems =
            listOf(
                "obj.frostmoon_spear",
                "obj.frost_moon_helm",
                "obj.frost_moon_chestplate",
                "obj.frost_moon_tassets",
            ),
    ),
    Eclipse(
        displayName = "Eclipse Moon",
        bossNpc = "npc.pmoon_boss_eclipse_moon",
        bossNpcId = 13018,
        decorativeNpcId = 13012,
        center = CoordGrid(1488, 9632, 0),
        entryStatue = MoonAssets.ECLIPSE_ENTRY_STATUE,
        entryStatueCoords = CoordGrid(1467, 9631, 0),
        statueApproach = CoordGrid(1467, 9631, 0),
        arrival = CoordGrid(1499, 9632, 0),
        postFight = CoordGrid(1447, 9589, 0),
        flatArmour = 6,
        idleAnimation = "seq.npc_djinn_02_idle",
        standardAnimation = "seq.npc_djinn_02_combo",
        spawnAnimation = "seq.npc_djinn_02_spawn",
        disappearAnimation = "seq.npc_djinn_02_teleport_idle_disappear",
        appearAnimation = "seq.npc_djinn_02_teleport_idle_appear",
        subdueAnimation = "seq.npc_djinn_02_ending",
        uniqueItems =
            listOf(
                "obj.eclipse_atlatl",
                "obj.eclipse_moon_helm",
                "obj.eclipse_moon_chestplate",
                "obj.eclipse_moon_tassets",
            ),
    );

    val bossSouthWest: CoordGrid
        get() = center.translate(-2, -2)

    val roomMin: CoordGrid
        get() = center.translate(-15, -15)

    val roomMax: CoordGrid
        get() = center.translate(15, 15)

    val bit: Int
        get() = 1 shl ordinal

    val glyphs: List<CoordGrid>
        get() =
            listOf(
                center.translate(0, -8),
                center.translate(-6, -6),
                center.translate(-8, 0),
                center.translate(-6, 6),
                center.translate(0, 8),
                center.translate(6, 6),
                center.translate(8, 0),
                center.translate(6, -6),
            )
}

internal fun moonGlyphSouthWest(center: CoordGrid): CoordGrid =
    center.translate(-(MOON_GLYPH_SIZE / 2), -(MOON_GLYPH_SIZE / 2))

internal object MoonAssets {
    const val GLYPH_NPC = "npc.pmoon_boss_threat_circle"
    const val BLOOD_JAGUAR_NPC = "npc.pmoon_boss_jaguar"
    const val ICE_BLOCK_ONE_NPC = "npc.pmoon_boss_icicle_1"
    const val ICE_BLOCK_TWO_NPC = "npc.pmoon_boss_icicle_2"
    const val ICE_BLOCK_THREE_NPC = "npc.pmoon_boss_icicle_3"
    const val WINTER_STORM_NPC = "npc.pmoon_boss_winter_storm"
    const val MOON_SHIELD_NPC = "npc.pmoon_boss_eclipse_moon_shield"
    const val ECLIPSE_CLONE_NPC = "npc.pmoon_boss_eclipse_clone"
    const val DISARM_SPOTANIM = "spotanim.pmoon_boss_disarm"
    const val BLOOD_ENTRY_STATUE = "loc.pmoon_entry_statue_blood"
    const val BLUE_ENTRY_STATUE = "loc.pmoon_entry_statue_blue"
    const val ECLIPSE_ENTRY_STATUE = "loc.pmoon_entry_statue_eclipse"
    const val BLUE_BRAZIER_UNLIT = "loc.pmoon_boss_brazier_unlit"
    const val EXIT_STAIRS = "loc.pmoon_boss_exit_stairs"
    const val EXIT_STAIRS_TWO = "loc.pmoon_boss_exit_stairs_2"
    const val LUNAR_CHEST = "loc.chest_sun01_default01"
    const val LUNAR_CHEST_OPEN = "loc.chest_sun01_open01"
    const val FLOOR_SYMBOL_SPAWN_SPOTANIM = 2746
    const val FLOOR_SYMBOL_IDLE_SPOTANIM = 2747
    const val FLOOR_SYMBOL_END_SPOTANIM = 2748
}

internal val MOON_HELPER_NPCS =
    listOf(
        MoonAssets.GLYPH_NPC,
        MoonAssets.BLOOD_JAGUAR_NPC,
        MoonAssets.ICE_BLOCK_ONE_NPC,
        MoonAssets.ICE_BLOCK_TWO_NPC,
        MoonAssets.ICE_BLOCK_THREE_NPC,
        MoonAssets.WINTER_STORM_NPC,
        MoonAssets.MOON_SHIELD_NPC,
        MoonAssets.ECLIPSE_CLONE_NPC,
    )
