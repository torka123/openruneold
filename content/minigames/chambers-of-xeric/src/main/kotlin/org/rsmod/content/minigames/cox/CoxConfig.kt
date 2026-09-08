package org.rsmod.content.minigames.cox

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val COX_INSTANCE_KEY = "chambers_of_xeric"
internal const val COX_MAX_PARTY_SIZE = 100
internal const val COX_ROOM_POINTS = 1_000
internal const val COX_PUZZLE_ACTIONS = 4
internal const val COX_OLM_HAND_DEATH_TICKS = 2
internal const val COX_OLM_PHASE_SUBMERGED_TICKS = 30
internal const val COX_OLM_FINAL_HAND_REVIVE_WINDOW_TICKS = 25
internal const val COX_CHALLENGE_LIMIT_TICKS = 65 * 100
internal const val COX_REWARD_MULTIPLIER = 1.0

internal val COX_LOBBY = CoordGrid(1254, 3566, 0)

internal val COX_COMPLETION_COUNT =
    AttributeKey<Int>(persistenceKey = "chambers_of_xeric_completion_count")
internal val COX_CHALLENGE_COMPLETION_COUNT =
    AttributeKey<Int>(persistenceKey = "chambers_of_xeric_challenge_completion_count")
internal val COX_PERSONAL_BEST =
    AttributeKey<Int>(persistenceKey = "chambers_of_xeric_personal_best_ticks")
internal val COX_CHALLENGE_PERSONAL_BEST =
    AttributeKey<Int>(persistenceKey = "chambers_of_xeric_challenge_personal_best_ticks")
internal val COX_OLMLET_UNLOCKED =
    AttributeKey<Boolean>(persistenceKey = "chambers_of_xeric_olmlet_unlocked")

/** Numeric IDs are backed by docs/raids/cox/reason231-to-openrune240-cox-id-map.json. */
internal object CoxLocs {
    const val LOWER_FLOOR_HOLE = 29734
    const val OLM_FLOOR_HOLE = 29735
    const val RECRUITING_BOARD = 29776
    const val ENTRANCE = 29777
    const val STEPS = 29778
    const val PASSAGE = 29789
    const val SHIMMERING_BARRIER = 29749
    const val TIGHTROPE = 29750
    const val KEYSTONE_CRYSTAL = 29751
    const val THIEVING_CHEST = 29742
    const val TROUGH = 29746
    const val BRAZIER = 29747
    const val GLOWING_CRYSTAL = 29757
    const val HERB_PATCH = 29765
    const val SPIRIT_TENDRILS = 29768
    const val STORAGE_UNIT = 30107
    const val GOURD_TREE = 29772
    const val WEEDS = 29773
    const val ANCIENT_CHEST = 30028
    const val REWARD_BEAM_BASIC = 30029
    const val REWARD_BEAM_UNIQUE = 30030
    const val ENERGY_WELL = 29878
    const val OLM_BARRIER = 29879
    const val OLM_HEAD_RISE = 29880
    const val OLM_HEAD_IDLE = 29881
    const val OLM_HEAD_DORMANT = 29882
    const val OLM_LEFT_HAND_RISE = 29883
    const val OLM_LEFT_HAND_IDLE = 29884
    const val OLM_LEFT_HAND_DORMANT = 29885
    const val OLM_RIGHT_HAND_RISE = 29886
    const val OLM_RIGHT_HAND_IDLE = 29887
    const val OLM_RIGHT_HAND_DORMANT = 29888
    const val FISHING_SPOT = 29889
    const val MYSTIC_BARRIER = 29796
    const val VANGUARD_CRYSTAL = 30017
    const val MUTTADILE_BARRIER = 30018
    const val MUTTADILE_MEAT_TREE = 30012
    const val MUTTADILE_MEAT_TREE_DEAD = 30013
    const val OLM_CRYSTAL_WARNING = 30033
    const val OLM_CRYSTAL_ERUPTION = 30034

    val progression = setOf(LOWER_FLOOR_HOLE, OLM_FLOOR_HOLE, PASSAGE, SHIMMERING_BARRIER)
    val puzzle = setOf(TIGHTROPE, THIEVING_CHEST, TROUGH, BRAZIER, GLOWING_CRYSTAL)
    val resources =
        setOf(
            HERB_PATCH,
            SPIRIT_TENDRILS,
            STORAGE_UNIT,
            GOURD_TREE,
            WEEDS,
            ENERGY_WELL,
            FISHING_SPOT,
        )
    val all =
        progression +
            puzzle +
            resources +
            setOf(
                RECRUITING_BOARD,
                ENTRANCE,
                STEPS,
                ANCIENT_CHEST,
                REWARD_BEAM_BASIC,
                REWARD_BEAM_UNIQUE,
                KEYSTONE_CRYSTAL,
                MYSTIC_BARRIER,
                VANGUARD_CRYSTAL,
                MUTTADILE_BARRIER,
                MUTTADILE_MEAT_TREE,
                MUTTADILE_MEAT_TREE_DEAD,
                OLM_CRYSTAL_WARNING,
                OLM_CRYSTAL_ERUPTION,
                OLM_BARRIER,
                OLM_HEAD_RISE,
                OLM_HEAD_IDLE,
                OLM_HEAD_DORMANT,
                OLM_LEFT_HAND_RISE,
                OLM_LEFT_HAND_IDLE,
                OLM_LEFT_HAND_DORMANT,
                OLM_RIGHT_HAND_RISE,
                OLM_RIGHT_HAND_IDLE,
                OLM_RIGHT_HAND_DORMANT,
            )
}

internal object CoxItems {
    const val KEYSTONE_CRYSTAL = 20_884
}

internal object CoxNpcs {
    const val OLM_RIGHT_HAND = 7553
    const val OLM_HEAD = 7554
    const val OLM_LEFT_HAND = 7555
    const val TEKTON = 7540
    const val VASA = 7566
    const val VESPULA = 7530
    const val VANGUARD_DORMANT = 7525
    const val VANGUARD_TRANSITION = 7526
    const val VANGUARD_MELEE = 7527
    const val VANGUARD_RANGED = 7528
    const val VANGUARD_MAGIC = 7529
    const val MUTTADILE_LARGE_UNDERWATER = 7561
    const val MUTTADILE_SMALL = 7562
    const val MUTTADILE_LARGE = 7563
    const val MUTTADILE_MEAT_TREE = 7564
    const val DEATHLY_RANGER = 7559
    const val DEATHLY_MAGE = 7560
    const val SHAMAN = 7573
    const val SKELETAL_MYSTIC = 7604
    const val SKELETAL_MYSTIC_ALT = 7605
    const val SKELETAL_MYSTIC_ALT_2 = 7606
    const val GUARDIAN = 7569
    const val GUARDIAN_ALT = 7570
    const val ICE_DEMON_FROZEN = 7584
    const val ICE_DEMON = 7585
    const val JEWELLED_CRAB = 7576
    const val SCAVENGER = 7548
    const val SCAVENGER_ALT = 7549
    const val VESPULA_PORTAL = 7533

    val olm = setOf(OLM_RIGHT_HAND, OLM_HEAD, OLM_LEFT_HAND)
    val all =
        setOf(
            TEKTON,
            VASA,
            VESPULA,
            VANGUARD_DORMANT,
            VANGUARD_TRANSITION,
            VANGUARD_MELEE,
            VANGUARD_RANGED,
            VANGUARD_MAGIC,
            MUTTADILE_LARGE_UNDERWATER,
            MUTTADILE_SMALL,
            MUTTADILE_LARGE,
            DEATHLY_RANGER,
            DEATHLY_MAGE,
            SHAMAN,
            SKELETAL_MYSTIC,
            SKELETAL_MYSTIC_ALT,
            SKELETAL_MYSTIC_ALT_2,
            GUARDIAN,
            GUARDIAN_ALT,
            ICE_DEMON_FROZEN,
            ICE_DEMON,
            JEWELLED_CRAB,
            SCAVENGER,
            SCAVENGER_ALT,
            VESPULA_PORTAL,
        ) + olm

    val assets = all + MUTTADILE_MEAT_TREE

    fun forRoom(room: CoxRoom): List<Int> =
        when (room) {
            CoxRoom.SCAVENGERS -> listOf(SCAVENGER, SCAVENGER, SCAVENGER)
            CoxRoom.SHAMANS -> listOf(SHAMAN, SHAMAN)
            CoxRoom.VASA -> listOf(VASA)
            CoxRoom.VANGUARDS -> listOf(VANGUARD_MELEE, VANGUARD_RANGED, VANGUARD_MAGIC)
            CoxRoom.SKELETAL_MYSTICS -> listOf(SKELETAL_MYSTIC, SKELETAL_MYSTIC, SKELETAL_MYSTIC)
            CoxRoom.TEKTON -> listOf(TEKTON)
            CoxRoom.MUTTADILES -> listOf(MUTTADILE_SMALL, MUTTADILE_LARGE)
            CoxRoom.GUARDIANS -> listOf(GUARDIAN, GUARDIAN_ALT)
            CoxRoom.VESPULA -> listOf(VESPULA, VESPULA_PORTAL)
            CoxRoom.ICE_DEMON -> listOf(ICE_DEMON)
            else -> emptyList()
        }
}

internal data class CoxCombatProfile(
    val style: CoxCombatStyle,
    val maxHit: Int,
    val attackRate: Int,
)

/**
 * Revision 240 does not carry usable server combat levels for the imported CoX NPCs. These are the
 * donor NPCs' real base levels before the raid's normal party/challenge scaling is applied.
 */
internal data class CoxNpcBaseStats(
    val hitpoints: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
)

internal fun coxBaseStats(npcId: Int): CoxNpcBaseStats? =
    when (npcId) {
        CoxNpcs.VANGUARD_DORMANT,
        CoxNpcs.VANGUARD_TRANSITION,
        CoxNpcs.VANGUARD_MELEE,
        CoxNpcs.VANGUARD_RANGED,
        CoxNpcs.VANGUARD_MAGIC -> CoxNpcBaseStats(280, 225, 225, 240, 225, 225)
        CoxNpcs.DEATHLY_RANGER -> CoxNpcBaseStats(120, 1, 1, 1, 120, 1)
        CoxNpcs.DEATHLY_MAGE -> CoxNpcBaseStats(120, 1, 1, 1, 1, 120)
        CoxNpcs.MUTTADILE_LARGE_UNDERWATER,
        CoxNpcs.MUTTADILE_LARGE -> CoxNpcBaseStats(250, 375, 375, 330, 375, 375)
        CoxNpcs.MUTTADILE_SMALL -> CoxNpcBaseStats(250, 225, 225, 207, 225, 1)
        CoxNpcs.SKELETAL_MYSTIC,
        CoxNpcs.SKELETAL_MYSTIC_ALT,
        CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> CoxNpcBaseStats(160, 200, 200, 160, 1, 200)
        CoxNpcs.GUARDIAN -> CoxNpcBaseStats(250, 140, 140, 100, 1, 1)
        CoxNpcs.GUARDIAN_ALT -> CoxNpcBaseStats(375, 210, 210, 150, 1, 1)
        CoxNpcs.OLM_LEFT_HAND -> CoxNpcBaseStats(600, 250, 175, 175, 250, 175)
        CoxNpcs.OLM_RIGHT_HAND -> CoxNpcBaseStats(600, 250, 175, 175, 250, 87)
        CoxNpcs.OLM_HEAD -> CoxNpcBaseStats(800, 250, 150, 150, 250, 250)
        else -> null
    }

internal enum class CoxCombatStyle {
    MELEE,
    RANGED,
    MAGIC,
    OLM,
    PASSIVE,
}

internal fun combatProfile(npcId: Int): CoxCombatProfile =
    when (npcId) {
        CoxNpcs.TEKTON,
        CoxNpcs.MUTTADILE_SMALL,
        CoxNpcs.MUTTADILE_LARGE,
        CoxNpcs.GUARDIAN,
        CoxNpcs.GUARDIAN_ALT -> CoxCombatProfile(CoxCombatStyle.MELEE, 45, 4)
        CoxNpcs.VANGUARD_MELEE -> CoxCombatProfile(CoxCombatStyle.MELEE, 18, 5)
        CoxNpcs.VANGUARD_RANGED -> CoxCombatProfile(CoxCombatStyle.RANGED, 28, 5)
        CoxNpcs.DEATHLY_RANGER -> CoxCombatProfile(CoxCombatStyle.RANGED, 28, 5)
        CoxNpcs.VANGUARD_MAGIC -> CoxCombatProfile(CoxCombatStyle.MAGIC, 22, 5)
        CoxNpcs.DEATHLY_MAGE -> CoxCombatProfile(CoxCombatStyle.MAGIC, 28, 5)
        CoxNpcs.SKELETAL_MYSTIC,
        CoxNpcs.SKELETAL_MYSTIC_ALT,
        CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> CoxCombatProfile(CoxCombatStyle.MAGIC, 44, 5)
        CoxNpcs.SHAMAN -> CoxCombatProfile(CoxCombatStyle.RANGED, 35, 4)
        CoxNpcs.VASA,
        CoxNpcs.VESPULA -> CoxCombatProfile(CoxCombatStyle.MAGIC, 35, 4)
        CoxNpcs.OLM_HEAD -> CoxCombatProfile(CoxCombatStyle.OLM, 42, 4)
        CoxNpcs.OLM_LEFT_HAND,
        CoxNpcs.OLM_RIGHT_HAND,
        CoxNpcs.VANGUARD_DORMANT,
        CoxNpcs.VANGUARD_TRANSITION,
        CoxNpcs.MUTTADILE_LARGE_UNDERWATER ->
            CoxCombatProfile(
                CoxCombatStyle.MAGIC,
                MuttadileCombatRules.SUBMERGED_MAGIC_MAX_HIT,
                MuttadileCombatRules.ATTACK_RATE,
            )
        else -> CoxCombatProfile(CoxCombatStyle.MELEE, 20, 4)
    }
