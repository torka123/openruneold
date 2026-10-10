package org.rsmod.content.minigames.corruptedgauntlet

/**
 * Revision-240 client/cache contract for Corrupted Gauntlet.
 *
 * All values here were cross-checked against OpenRune's active revision-240 cache. The runtime
 * should refer to this manifest instead of scattering raw cache ids through encounter scripts.
 */
internal object CorruptedGauntletConfig {
    const val MAP_REGION = 7_768
    const val MAP_PLANE = 1
    const val GRID_SIZE = 7
    const val ROOM_SIZE = 16
    const val PREPARATION_TICKS = 750
    const val GAME_TICK_MILLIS = 600
    const val HUNLLEF_MAX_HITPOINTS = 1_000
    const val HUNLLEF_ATTACK_INTERVAL = 5
    const val HUNLLEF_STYLE_ATTACKS = 4
    const val HUNLLEF_PROTECTION_HITS = 6
    const val HUNLLEF_TORNADO_LIFETIME = 20
    const val FLOOR_WARNING_TICKS = 5
    const val FLOOR_ACTIVE_TICKS = 6
    const val COMPLETION_COMMON_ROLLS = 3
    const val COMPLETION_SHARD_MIN = 7
    const val COMPLETION_SHARD_MAX = 12
    const val MAX_INSTANCE_PLAYERS = 1

    /** Public lobby tile immediately outside the Corrupted Gauntlet portal. */
    const val LOBBY_X = 3_032
    const val LOBBY_Z = 6_127
    const val LOBBY_LEVEL = 1

    object SourceChunks {
        const val MIDDLE_X = 240
        const val EDGE_X = 242
        const val CORNER_X = 244
        const val START_X = 246
        const val START_Z = 708
        const val BOSS_X = 246
        const val BOSS_Z = 710
        val roomVariantsZ = intArrayOf(704, 706, 708, 710)
    }

    object Npc {
        const val HUNLLEF_MELEE_PROTECTED = 9_035
        const val HUNLLEF_RANGED_PROTECTED = 9_036
        const val HUNLLEF_MAGIC_PROTECTED = 9_037
        const val HUNLLEF_DEATH = 9_038
        const val TORNADO = 9_039
        const val RAT = 9_040
        const val SPIDER = 9_041
        const val BAT = 9_042
        const val UNICORN = 9_043
        const val SCORPION = 9_044
        const val WOLF = 9_045
        const val BEAR = 9_046
        const val DRAGON = 9_047
        const val DARK_BEAST = 9_048

        val all =
            setOf(
                HUNLLEF_MELEE_PROTECTED,
                HUNLLEF_RANGED_PROTECTED,
                HUNLLEF_MAGIC_PROTECTED,
                HUNLLEF_DEATH,
                TORNADO,
                RAT,
                SPIDER,
                BAT,
                UNICORN,
                SCORPION,
                WOLF,
                BEAR,
                DRAGON,
                DARK_BEAST,
            )
    }

    object Loc {
        const val UNLIT_NODE_RIGHT = 35_998
        const val UNLIT_NODE_LEFT = 35_999
        const val LIT_NODE_RIGHT = 36_000
        const val LIT_NODE_LEFT = 36_001
        const val CORRUPTED_SCEPTRE = 35_964
        const val EXIT_PLATFORM = 35_965
        const val SINGING_BOWL = 35_966
        const val ORE = 35_967
        const val DEPLETED_ORE = 35_968
        const val ROOTS = 35_969
        const val DEPLETED_ROOTS = 35_970
        const val FISHING_SPOT = 35_971
        const val DEPLETED_FISHING_SPOT = 35_972
        const val GRYM = 35_973
        const val DEPLETED_GRYM = 35_974
        const val LINUM = 35_975
        const val DEPLETED_LINUM = 35_976
        const val TOOL_STORAGE = 35_977
        const val RECIPES = 35_978
        const val EGNIOL_BOOK = 35_979
        const val RANGE = 35_980
        const val PUMP = 35_981
        const val BOSS_BARRIER = 35_982
        const val BOSS_ESCAPE_BARRIER = 35_983
        const val LOBBY_ENTRANCE_PLATFORM = 35_984
        const val LOBBY_EXIT = 35_985
        const val ENTRANCE = 35_986
        const val COMBAT_DUMMY = 35_987
        const val REWARD_CHEST_CLOSED = 36_087
        const val REWARD_CHEST_OPEN = 36_088
        const val SAFE_FLOOR = 36_046
        const val WARNING_FLOOR = 36_047
        const val DAMAGING_FLOOR = 36_048
        const val SCOREBOARD = 36_060
        const val PORTAL = 36_081
        const val LOBBY_PLATFORM = 36_082
        const val ENABLED_ENTRANCE = 36_084

        val resourceNodes = setOf(ORE, ROOTS, FISHING_SPOT, GRYM, LINUM)
        val unlitNodes = setOf(UNLIT_NODE_RIGHT, UNLIT_NODE_LEFT)
        val depletedResourceNodes =
            setOf(
                DEPLETED_ORE,
                DEPLETED_ROOTS,
                DEPLETED_FISHING_SPOT,
                DEPLETED_GRYM,
                DEPLETED_LINUM,
            )
    }

    object Obj {
        const val SCEPTRE = 23_820
        const val AXE = 23_821
        const val PICKAXE = 23_822
        const val HARPOON = 23_823
        const val SHARDS = 23_824
        const val DUST = 23_830
        const val SPIKE = 23_831
        const val BOWSTRING = 23_832
        const val ORB = 23_833
        const val WEAPON_FRAME = 23_834
        const val GRYM_LEAF = 23_835
        const val LINUM = 23_836
        const val ORE = 23_837
        const val BARK = 23_838
        const val VIAL = 23_879
        const val VIAL_OF_WATER = 23_880
        const val UNFINISHED_POTION = 23_881
        const val POTION_ONE_DOSE = 23_882
        const val POTION_TWO_DOSE = 23_883
        const val POTION_THREE_DOSE = 23_884
        const val POTION_FOUR_DOSE = 23_885
        const val BASIC_HELM = 23_840
        const val ATTUNED_HELM = 23_841
        const val PERFECTED_HELM = 23_842
        const val BASIC_BODY = 23_843
        const val ATTUNED_BODY = 23_844
        const val PERFECTED_BODY = 23_845
        const val BASIC_LEGS = 23_846
        const val ATTUNED_LEGS = 23_847
        const val PERFECTED_LEGS = 23_848
        const val BASIC_HALBERD = 23_849
        const val ATTUNED_HALBERD = 23_850
        const val PERFECTED_HALBERD = 23_851
        const val BASIC_STAFF = 23_852
        const val ATTUNED_STAFF = 23_853
        const val PERFECTED_STAFF = 23_854
        const val BASIC_BOW = 23_855
        const val ATTUNED_BOW = 23_856
        const val PERFECTED_BOW = 23_857
        const val TELEPORT_CRYSTAL = 23_858
        const val CAPE = 23_859
        const val PESTLE_AND_MORTAR = 23_865
        const val RAW_PADDLEFISH = 23_872
        const val BURNT_PADDLEFISH = 23_873
        const val PADDLEFISH = 23_874
        const val CRYSTAL_PADDLEFISH = 25_958
        const val ESCAPE_CRYSTAL = 25_959

        val runOnly = (23_820..23_885).toSet() + setOf(CRYSTAL_PADDLEFISH, ESCAPE_CRYSTAL)
    }

    object Sequence {
        const val HUNLLEF_TORNADO_CAST = 8_418
        const val HUNLLEF_ATTACK = 8_419
        const val HUNLLEF_TRAMPLE = 8_420
        const val HUNLLEF_DEATH = 8_421
        const val HUNLLEF_DEATH_END = 8_422
        const val HUNLLEF_SWITCH_TO_MAGIC = 8_754
        const val HUNLLEF_SWITCH_TO_RANGED = 8_755
    }

    object Projectile {
        const val HUNLLEF_MAGIC = 1_708
        const val HUNLLEF_RANGED = 1_712
        const val PRAYER_DISABLE = 1_714
        const val REFERENCE_START_HEIGHT = 214
        const val REFERENCE_END_HEIGHT = 124
        const val REFERENCE_START_TIME = 19
        const val REFERENCE_END_TIME = 69
    }

    object Interface {
        const val OVERLAY = 637
        const val MAP = 638
        const val SCOREBOARD = 639
        const val RECIPES = 640
        const val TRANSITION = 641
    }

    object ClientScript {
        const val MAP_INIT = 2_908
        const val MAP_UPDATE = 2_909
        const val MAP_START = 2_910
        const val ROOM_UPDATE = 2_911
        const val PLAYER_UPDATE = 2_912
        const val OVERLAY_INIT = 2_913
        const val TIMER_UPDATE = 2_914
        const val TIMER = 2_915
        const val TIMER_END = 2_916
        const val RECIPES = 2_917
        const val SELECT_RECIPE = 2_919
    }

    object VarBit {
        const val BOSS = 9_177
        const val MAP = 9_178
        const val PENDING = 9_179
        const val FIRST_ROOM_REVEAL = 9_240
        const val LAST_ROOM_REVEAL = 9_288
        const val CURRENT_ROOM_X = 9_289
        const val CURRENT_ROOM_Z = 9_290
        const val START_ROOM = 9_291
        const val ROOM_TYPE = 9_292
    }

    /** Normal completion count is also the native corrupted-entry eligibility signal. */
    const val NORMAL_COMPLETION_VARP = 2_353
    const val CORRUPTED_COMPLETION_VARP = 2_354
}

internal object CorruptedGauntletAssetManifest {
    val mapRegions = setOf(CorruptedGauntletConfig.MAP_REGION)
    val npcIds = CorruptedGauntletConfig.Npc.all
    val locIds = buildSet {
        addAll(35_964..36_060)
        add(CorruptedGauntletConfig.Loc.PORTAL)
        add(CorruptedGauntletConfig.Loc.LOBBY_PLATFORM)
        add(CorruptedGauntletConfig.Loc.ENABLED_ENTRANCE)
        add(CorruptedGauntletConfig.Loc.REWARD_CHEST_CLOSED)
        add(CorruptedGauntletConfig.Loc.REWARD_CHEST_OPEN)
    }
    val interfaceIds =
        setOf(
            CorruptedGauntletConfig.Interface.OVERLAY,
            CorruptedGauntletConfig.Interface.MAP,
            CorruptedGauntletConfig.Interface.SCOREBOARD,
            CorruptedGauntletConfig.Interface.RECIPES,
            CorruptedGauntletConfig.Interface.TRANSITION,
        )
    val clientScriptIds =
        (2_908..2_917).toSet() + CorruptedGauntletConfig.ClientScript.SELECT_RECIPE
    val varbitIds =
        setOf(
            CorruptedGauntletConfig.VarBit.BOSS,
            CorruptedGauntletConfig.VarBit.MAP,
            CorruptedGauntletConfig.VarBit.PENDING,
            CorruptedGauntletConfig.VarBit.CURRENT_ROOM_X,
            CorruptedGauntletConfig.VarBit.CURRENT_ROOM_Z,
            CorruptedGauntletConfig.VarBit.START_ROOM,
            CorruptedGauntletConfig.VarBit.ROOM_TYPE,
        ) +
            (CorruptedGauntletConfig.VarBit.FIRST_ROOM_REVEAL..CorruptedGauntletConfig.VarBit
                    .LAST_ROOM_REVEAL)
}
