package org.rsmod.content.minigames.tob

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val TOB_INSTANCE_KEY = "theatre_of_blood"
internal const val TOB_MAX_PARTY_SIZE = 5
internal val TOB_LOBBY = CoordGrid(3669, 3219, 0)

internal val TOB_ENTRY_COMPLETIONS = AttributeKey<Int>(persistenceKey = "tob_entry_completions")
internal val TOB_NORMAL_COMPLETIONS = AttributeKey<Int>(persistenceKey = "tob_normal_completions")
internal val TOB_HARD_COMPLETIONS = AttributeKey<Int>(persistenceKey = "tob_hard_completions")
internal val TOB_ENTRY_BEST = AttributeKey<Int>(persistenceKey = "tob_entry_best_ticks")
internal val TOB_NORMAL_BEST = AttributeKey<Int>(persistenceKey = "tob_normal_best_ticks")
internal val TOB_HARD_BEST = AttributeKey<Int>(persistenceKey = "tob_hard_best_ticks")
internal val TOB_LIL_ZIK_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "tob_lil_zik_unlocked")

internal object TobLocs {
    const val ENTRANCE = 32653
    const val NOTICE_BOARD = 32655
    const val MAIDEN_BARRIER = 32755
    const val RESIGN = 32756
    const val DAWNBRINGER_CHEST = 32741
    const val FORMIDABLE_PASSAGE = 33113
    const val XARPUS_EXHUMED = 32743
    const val VERZIK_PILLAR = 32687
    const val VERZIK_WEB = 32734
    const val XARPUS_DOOR = 32751
    const val VERZIK_THRONE_VISIBLE = 32686
    const val VERZIK_THRONE_EMPTY = 32736
    const val VERZIK_THRONE_TRANSFORMING = 32737
    const val TREASURE_ROOM = 32738
    const val VERZIK_THRONE_VISIBLE_VARBIT = 6400
    const val VERZIK_THRONE_LOCAL_X = 30
    const val VERZIK_THRONE_LOCAL_Z = 36
    const val SUPPLY_CHEST = 32758
    const val STRATEGY_TABLE = 33014
    const val TELEPORT_CRYSTAL = 32996
    val rewardChests = setOf(32990, 32994, 33086, 33087, 33088, 33089, 33090, 35836)
    val rewardChestVarbits =
        linkedMapOf(33086 to 6450, 33087 to 6451, 33088 to 6452, 33089 to 6453, 33090 to 6454)
    val passages = setOf(FORMIDABLE_PASSAGE, XARPUS_DOOR, TREASURE_ROOM)
    val all =
        passages +
            rewardChests +
            setOf(
                ENTRANCE,
                NOTICE_BOARD,
                MAIDEN_BARRIER,
                RESIGN,
                DAWNBRINGER_CHEST,
                SUPPLY_CHEST,
                XARPUS_EXHUMED,
                VERZIK_PILLAR,
                VERZIK_WEB,
                VERZIK_THRONE_VISIBLE,
                VERZIK_THRONE_EMPTY,
                VERZIK_THRONE_TRANSFORMING,
                STRATEGY_TABLE,
                TELEPORT_CRYSTAL,
            )
}

internal object TobItems {
    const val DAWNBRINGER = 22516
    const val INSULATED_BOOTS = 7159
}

internal object TobNpcs {
    /**
     * Verzik's seated forms are cache morph parents, not model-only morph children. Spawning a
     * child directly loses the parent's size, idle sequence, turn speed, and morph state.
     */
    fun verzikInitial(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10830
            TobMode.NORMAL -> 8369
            TobMode.HARD -> 10847
        }

    fun boss(room: TobRoom, mode: TobMode): Int =
        when (room) {
            TobRoom.MAIDEN ->
                when (mode) {
                    TobMode.ENTRY -> 10814
                    TobMode.NORMAL -> 8360
                    TobMode.HARD -> 10822
                }
            TobRoom.BLOAT ->
                when (mode) {
                    TobMode.ENTRY -> 10812
                    TobMode.NORMAL -> 8359
                    TobMode.HARD -> 10813
                }
            TobRoom.NYLOCAS ->
                when (mode) {
                    TobMode.ENTRY -> 10788
                    TobMode.NORMAL -> 8356
                    TobMode.HARD -> 10809
                }
            TobRoom.SOTETSEG ->
                when (mode) {
                    TobMode.ENTRY -> 10865
                    TobMode.NORMAL -> 8388
                    TobMode.HARD -> 10868
                }
            TobRoom.XARPUS ->
                when (mode) {
                    TobMode.ENTRY -> 10768
                    TobMode.NORMAL -> 8340
                    TobMode.HARD -> 10772
                }
            TobRoom.VERZIK -> verzikPhases(mode).first()
            TobRoom.TREASURE -> error("Treasure has no boss")
        }

    fun maidenForms(mode: TobMode): List<Int> =
        when (mode) {
            TobMode.ENTRY -> (10814..10818).toList()
            TobMode.NORMAL -> (8360..8364).toList()
            TobMode.HARD -> (10822..10826).toList()
        }

    fun nylocasBossStyles(mode: TobMode): IntArray =
        when (mode) {
            TobMode.ENTRY -> intArrayOf(10787, 10789, 10788)
            TobMode.NORMAL -> intArrayOf(8355, 8357, 8356)
            TobMode.HARD -> intArrayOf(10808, 10810, 10809)
        }

    fun nylocasActiveStyleId(spawnId: Int, visibleId: Int, mode: TobMode): Int =
        if (spawnId in nylocasBossStyles(mode)) visibleId else spawnId

    fun verzikPhases(mode: TobMode): List<Int> =
        when (mode) {
            TobMode.ENTRY -> listOf(10831, 10833, 10835)
            TobMode.NORMAL -> listOf(8370, 8372, 8374)
            TobMode.HARD -> listOf(10848, 10850, 10852)
        }

    fun verzikTransitions(mode: TobMode): List<Int> =
        when (mode) {
            TobMode.ENTRY -> listOf(10832, 10834)
            TobMode.NORMAL -> listOf(8371, 8373)
            TobMode.HARD -> listOf(10849, 10851)
        }

    fun verzikDeath(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10836
            TobMode.NORMAL -> 8375
            TobMode.HARD -> 10853
        }

    fun nylocasStyles(mode: TobMode): IntArray =
        when (mode) {
            TobMode.ENTRY -> intArrayOf(10774, 10775, 10776)
            TobMode.NORMAL -> intArrayOf(8342, 8343, 8344)
            TobMode.HARD -> intArrayOf(10791, 10792, 10793)
        }

    fun nylocasBigIncomingStyles(mode: TobMode): IntArray =
        when (mode) {
            TobMode.ENTRY -> intArrayOf(10777, 10778, 10779)
            TobMode.NORMAL -> intArrayOf(8345, 8346, 8347)
            TobMode.HARD -> intArrayOf(10794, 10795, 10796)
        }

    fun nylocasFightingStyles(mode: TobMode): IntArray =
        when (mode) {
            TobMode.ENTRY -> intArrayOf(10780, 10781, 10782)
            TobMode.NORMAL -> intArrayOf(8348, 8349, 8350)
            TobMode.HARD -> intArrayOf(10797, 10798, 10799)
        }

    fun nylocasBigFightingStyles(mode: TobMode): IntArray =
        when (mode) {
            TobMode.ENTRY -> intArrayOf(10783, 10784, 10785)
            TobMode.NORMAL -> intArrayOf(8351, 8352, 8353)
            TobMode.HARD -> intArrayOf(10800, 10801, 10802)
        }

    fun nylocasWaveStyles(mode: TobMode): Set<Int> = buildSet {
        addAll(nylocasStyles(mode).toList())
        addAll(nylocasBigIncomingStyles(mode).toList())
        addAll(nylocasFightingStyles(mode).toList())
        addAll(nylocasBigFightingStyles(mode).toList())
    }

    fun nylocasStyleIndex(id: Int, mode: TobMode): Int? {
        val groups =
            listOf(
                nylocasStyles(mode),
                nylocasBigIncomingStyles(mode),
                nylocasFightingStyles(mode),
                nylocasBigFightingStyles(mode),
                nylocasBossStyles(mode),
            )
        return groups.firstNotNullOfOrNull { group -> group.indexOf(id).takeIf { it >= 0 } }
    }

    fun nylocasSplitStyle(id: Int, mode: TobMode): Int? {
        val style =
            nylocasBigIncomingStyles(mode).indexOf(id).takeIf { it >= 0 }
                ?: nylocasBigFightingStyles(mode).indexOf(id).takeIf { it >= 0 }
                ?: return null
        return nylocasFightingStyles(mode)[style]
    }

    fun maidenMatomenos(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10820
            TobMode.NORMAL -> 8366
            TobMode.HARD -> 10828
        }

    fun bloodSpawn(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10821
            TobMode.NORMAL -> 8367
            TobMode.HARD -> 10829
        }

    fun verzikTornado(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10846
            TobMode.NORMAL -> 8386
            TobMode.HARD -> 10863
        }

    fun verzikAthanatos(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10844
            TobMode.NORMAL -> 8384
            TobMode.HARD -> 10861
        }

    fun verzikMatomenos(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10845
            TobMode.NORMAL -> 8385
            TobMode.HARD -> 10862
        }

    fun verzikWeb(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10837
            TobMode.NORMAL -> 8376
            TobMode.HARD -> 10854
        }

    fun supportingPillar(mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> 10840
            TobMode.NORMAL -> 8379
            TobMode.HARD -> 10857
        }

    fun verzikBombs(mode: TobMode): IntArray = nylocasBigIncomingStyles(mode)

    const val SOTETSEG_TORNADO = 8389

    val allCore: Set<Int> = buildSet {
        TobMode.entries.forEach { mode ->
            TobRoom.entries
                .filter { it.category != TobRoomCategory.REWARD }
                .forEach { add(boss(it, mode)) }
            addAll(maidenForms(mode))
            add(verzikInitial(mode))
            addAll(verzikPhases(mode))
            addAll(verzikTransitions(mode))
            add(verzikDeath(mode))
            add(verzikTornado(mode))
            add(verzikAthanatos(mode))
            add(verzikMatomenos(mode))
            add(verzikWeb(mode))
            add(supportingPillar(mode))
            addAll(verzikBombs(mode).toList())
            addAll(nylocasBossStyles(mode).toList())
            addAll(nylocasStyles(mode).toList())
            addAll(nylocasWaveStyles(mode))
            add(maidenMatomenos(mode))
            add(bloodSpawn(mode))
        }

        add(SOTETSEG_TORNADO)
    }
}

internal object TobAssets {
    const val MAIDEN_BLOOD_CAST = 8091
    const val MAIDEN_MAGIC_CAST = 8092
    const val MAIDEN_BLOOD_PROJECTILE = 1578
    const val MAIDEN_MAGIC_PROJECTILE = 1577
    const val MAIDEN_BLOOD_POOL = 1579

    const val BLOAT_FLIES = 1568
    const val BLOAT_FALLING_BODY_A = 1572
    const val BLOAT_FALLING_BODY_B = 1573
    const val BLOAT_DOWN = 8082

    const val NYLOCAS_MAGIC_CAST = 7989
    const val NYLOCAS_RANGED_CAST = 7999
    const val NYLOCAS_MELEE_ATTACK = 8004
    const val NYLOCAS_MAGIC_IDLE = 7988
    const val NYLOCAS_RANGED_IDLE = 7993
    const val NYLOCAS_MELEE_IDLE = 8002
    const val NYLOCAS_MAGIC_DEATH = 7991
    const val NYLOCAS_RANGED_DEATH = 7998
    const val NYLOCAS_MELEE_DEATH = 8005
    const val NYLOCAS_MAGIC_DETONATE = 7992
    const val NYLOCAS_RANGED_DETONATE = 8000
    const val NYLOCAS_MELEE_DETONATE = 8006

    fun nylocasAttackAnimation(style: Int): Int =
        when (style) {
            0 -> NYLOCAS_MELEE_ATTACK
            1 -> NYLOCAS_RANGED_CAST
            else -> NYLOCAS_MAGIC_CAST
        }

    fun nylocasDetonateAnimation(id: Int, mode: TobMode): Int =
        when (TobNpcs.nylocasStyleIndex(id, mode)) {
            0 -> NYLOCAS_MELEE_DETONATE
            1 -> NYLOCAS_RANGED_DETONATE
            else -> NYLOCAS_MAGIC_DETONATE
        }

    const val NYLOCAS_MAGIC_PROJECTILE = 1580
    const val NYLOCAS_RANGED_PROJECTILE = 1560

    const val SOTETSEG_MAZE_TRANSITION = 8138
    const val SOTETSEG_ATTACK = 8139
    const val SOTETSEG_PLAYER_KNOCKBACK = 1157
    const val SOTETSEG_BIG_BALL = 1604
    const val SOTETSEG_PATH = 1605
    const val SOTETSEG_MAGIC_PROJECTILE = 1606
    const val SOTETSEG_RANGED_PROJECTILE = 1607
    const val SOTETSEG_BLUE_PROJECTILE = 1609

    const val XARPUS_POOL = 1654
    const val XARPUS_SPIT_PROJECTILE = 1644
    const val XARPUS_HEAL_PROJECTILE = 1550
    const val XARPUS_POOL_ANIM = 8065
    const val XARPUS_ATTACK_ANIM = 8059
    const val XARPUS_SCREECH_ANIM = 8061
    const val XARPUS_EXHUMED_END = 1549

    const val DAWNBRINGER_BASIC_CAST = 1543
    const val DAWNBRINGER_BASIC_PROJECTILE = 1544
    const val DAWNBRINGER_BASIC_IMPACT = 1545
    const val DAWNBRINGER_SPECIAL_CAST = 1546
    const val DAWNBRINGER_SPECIAL_PROJECTILE = 1547
    const val DAWNBRINGER_SPECIAL_IMPACT = 1548

    const val VERZIK_PILLAR_COLLAPSE = 8052
    const val VERZIK_PHASE_ONE_IDLE = 8107
    const val VERZIK_THRONE_TRANSFORM = 8108
    const val VERZIK_PHASE_ONE_ATTACK = 8109
    const val VERZIK_PHASE_ONE_DEFEND = 8110
    const val VERZIK_PHASE_ONE_DEATH = 8111
    const val VERZIK_PHASE_TWO_SPAWN = 8112
    const val VERZIK_PHASE_TWO_IDLE = 8113
    const val VERZIK_PHASE_TWO_ATTACK = 8114
    const val VERZIK_PHASE_TWO_LIGHTNING = 8115
    const val VERZIK_PHASE_TWO_MELEE = 8116
    const val VERZIK_PHASE_TWO_SUMMON = 8117
    const val VERZIK_PHASE_TWO_DEATH = 8118
    const val VERZIK_ATHANATOS_DEATH = 8078
    const val VERZIK_PHASE_THREE_SPAWN = 8119
    const val VERZIK_PHASE_THREE_IDLE = 8120
    const val VERZIK_PHASE_THREE_WALK = 8121
    const val VERZIK_PHASE_THREE_MELEE = 8123
    const val VERZIK_PHASE_THREE_MAGIC = 8124
    const val VERZIK_PHASE_THREE_RANGED = 8125
    const val VERZIK_POWERBLAST = 8126
    const val VERZIK_WEBSPIN = 8127
    const val VERZIK_PHASE_THREE_DEATH = 8128
    const val VERZIK_WEB_IDLE = 8130
    const val VERZIK_RANGED_VILE = 8131
    const val VERZIK_LIGHTNING_IMPACT = 8132
    const val VERZIK_BLOOD_PROJECTILE_ANIM = 8133
    const val VERZIK_BLOOD_IMPACT = 8134
    const val VERZIK_SUMMON = 14406
    const val VERZIK_PLAYER_KNOCKBACK = 1157

    const val VERZIK_PHASE_ONE_PROJECTILE = 1580
    const val VERZIK_PHASE_ONE_IMPACT = 1581
    const val VERZIK_PILLAR_IMPACT = 1582
    const val VERZIK_PHASE_TWO_RANGED_PROJECTILE = 1583
    const val VERZIK_PHASE_TWO_RANGED_IMPACT = 1584
    const val VERZIK_LIGHTNING_PROJECTILE = 1585
    const val VERZIK_ATHANATOS_PROJECTILE = 1586
    const val VERZIK_PHASE_TWO_BLOOD_PROJECTILE = 1591
    const val VERZIK_PHASE_TWO_BLOOD_IMPACT = 1592
    const val VERZIK_PHASE_THREE_RANGED_PROJECTILE = 1593
    const val VERZIK_PHASE_THREE_MAGIC_PROJECTILE = 1594
    const val VERZIK_YELLOW_POOL = 1595
    const val VERZIK_POWERBLAST_PROJECTILE = 1596
    const val VERZIK_WEB_PROJECTILE = 1601
    const val VERZIK_TORNADO_SPOTANIM = 1602

    val sequences =
        setOf(
            MAIDEN_BLOOD_CAST,
            MAIDEN_MAGIC_CAST,
            BLOAT_DOWN,
            NYLOCAS_MAGIC_IDLE,
            NYLOCAS_RANGED_IDLE,
            NYLOCAS_MELEE_IDLE,
            NYLOCAS_MAGIC_CAST,
            NYLOCAS_RANGED_CAST,
            NYLOCAS_MELEE_ATTACK,
            NYLOCAS_MAGIC_DEATH,
            NYLOCAS_RANGED_DEATH,
            NYLOCAS_MELEE_DEATH,
            NYLOCAS_MAGIC_DETONATE,
            NYLOCAS_RANGED_DETONATE,
            NYLOCAS_MELEE_DETONATE,
            SOTETSEG_MAZE_TRANSITION,
            SOTETSEG_ATTACK,
            SOTETSEG_PLAYER_KNOCKBACK,
            XARPUS_POOL_ANIM,
            XARPUS_ATTACK_ANIM,
            XARPUS_SCREECH_ANIM,
            VERZIK_PILLAR_COLLAPSE,
            VERZIK_PHASE_ONE_IDLE,
            VERZIK_THRONE_TRANSFORM,
            VERZIK_PHASE_ONE_ATTACK,
            VERZIK_PHASE_ONE_DEFEND,
            VERZIK_PHASE_ONE_DEATH,
            VERZIK_PHASE_TWO_SPAWN,
            VERZIK_PHASE_TWO_IDLE,
            VERZIK_PHASE_TWO_ATTACK,
            VERZIK_PHASE_TWO_LIGHTNING,
            VERZIK_PHASE_TWO_MELEE,
            VERZIK_PHASE_TWO_SUMMON,
            VERZIK_PHASE_TWO_DEATH,
            VERZIK_ATHANATOS_DEATH,
            VERZIK_PHASE_THREE_SPAWN,
            VERZIK_PHASE_THREE_IDLE,
            VERZIK_PHASE_THREE_WALK,
            VERZIK_PHASE_THREE_MELEE,
            VERZIK_PHASE_THREE_MAGIC,
            VERZIK_PHASE_THREE_RANGED,
            VERZIK_POWERBLAST,
            VERZIK_WEBSPIN,
            VERZIK_PHASE_THREE_DEATH,
            VERZIK_WEB_IDLE,
            VERZIK_RANGED_VILE,
            VERZIK_LIGHTNING_IMPACT,
            VERZIK_BLOOD_PROJECTILE_ANIM,
            VERZIK_BLOOD_IMPACT,
            VERZIK_SUMMON,
            VERZIK_PLAYER_KNOCKBACK,
        )
    val spotanims =
        setOf(
            MAIDEN_BLOOD_PROJECTILE,
            MAIDEN_MAGIC_PROJECTILE,
            MAIDEN_BLOOD_POOL,
            BLOAT_FLIES,
            BLOAT_FALLING_BODY_A,
            BLOAT_FALLING_BODY_B,
            NYLOCAS_MAGIC_PROJECTILE,
            NYLOCAS_RANGED_PROJECTILE,
            SOTETSEG_BIG_BALL,
            SOTETSEG_PATH,
            SOTETSEG_MAGIC_PROJECTILE,
            SOTETSEG_RANGED_PROJECTILE,
            SOTETSEG_BLUE_PROJECTILE,
            XARPUS_POOL,
            XARPUS_SPIT_PROJECTILE,
            XARPUS_HEAL_PROJECTILE,
            XARPUS_EXHUMED_END,
            DAWNBRINGER_BASIC_CAST,
            DAWNBRINGER_BASIC_PROJECTILE,
            DAWNBRINGER_BASIC_IMPACT,
            DAWNBRINGER_SPECIAL_CAST,
            DAWNBRINGER_SPECIAL_PROJECTILE,
            DAWNBRINGER_SPECIAL_IMPACT,
            VERZIK_PHASE_ONE_PROJECTILE,
            VERZIK_PHASE_ONE_IMPACT,
            VERZIK_PILLAR_IMPACT,
            VERZIK_PHASE_TWO_RANGED_PROJECTILE,
            VERZIK_PHASE_TWO_RANGED_IMPACT,
            VERZIK_LIGHTNING_PROJECTILE,
            VERZIK_ATHANATOS_PROJECTILE,
            VERZIK_PHASE_TWO_BLOOD_PROJECTILE,
            VERZIK_PHASE_TWO_BLOOD_IMPACT,
            VERZIK_PHASE_THREE_RANGED_PROJECTILE,
            VERZIK_PHASE_THREE_MAGIC_PROJECTILE,
            VERZIK_YELLOW_POOL,
            VERZIK_POWERBLAST_PROJECTILE,
            VERZIK_WEB_PROJECTILE,
            VERZIK_TORNADO_SPOTANIM,
        )
}
