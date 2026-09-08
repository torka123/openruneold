package org.rsmod.content.minigames.toa

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val TOA_INSTANCE_KEY = "tombs_of_amascut"
internal const val TOA_MAX_PARTY_SIZE = 8
internal val TOA_ENTRANCE = CoordGrid(3359, 9113, 0)

internal val TOA_ENTRY_COMPLETIONS = AttributeKey<Int>(persistenceKey = "toa_entry_completions")
internal val TOA_NORMAL_COMPLETIONS = AttributeKey<Int>(persistenceKey = "toa_normal_completions")
internal val TOA_EXPERT_COMPLETIONS = AttributeKey<Int>(persistenceKey = "toa_expert_completions")
internal val TOA_BEST_TICKS = AttributeKey<Int>(persistenceKey = "toa_best_ticks")
internal val TOA_INVOCATION_PRESETS =
    AttributeKey<MutableList<String>>(persistenceKey = "toa_invocation_presets_v1")

internal object ToaLocs {
    const val GROUPING_OBELISK = 46068
    const val INVOCATION_BOARD = 46073
    const val START = 46089
    const val CHALLENGE_BARRIER = 45135
    const val SCABARAS_PATH = 46155
    const val SCABARAS_QUICK = 46156
    const val APMEKEN_PATH = 46158
    const val APMEKEN_QUICK = 46159
    const val CRONDIS_PATH = 46161
    const val CRONDIS_QUICK = 46162
    const val HET_PATH = 46164
    const val HET_QUICK = 46165
    const val WARDENS_PATH_LOCKED = 46167
    const val WARDENS_PATH = 46168
    const val KEPHRI_CRYSTAL = 45505
    const val ZEBAK_CRYSTAL = 45506
    const val BABA_CRYSTAL = BabaAssets.COMPLETION_TELEPORT_LOC
    const val BABA_SARCOPHAGUS_CLOSED = BabaAssets.CLOSED_SARCOPHAGUS_LOC
    const val BABA_SARCOPHAGUS_OPEN = BabaAssets.OPEN_SARCOPHAGUS_LOC
    const val BABA_BANANA_PILE = BabaAssets.BANANA_PILE_LOC
    const val BABA_THRONE = BabaAssets.THRONE_LOC
    const val AKKHA_CRYSTAL = 45866
    const val WARDENS_CRYSTAL = 45579
    const val REWARD_ENTRY = 45138
    const val BANK_CAMEL = 46075
    const val REWARD_NICHE_VARBIT = 14319
    val rewardNiches = setOf(46082, 46083)

    const val LEAVE_RAID = 45128
    const val CRONDIS_LEAVE_RAID = 45453
    val leaveRaid = setOf(LEAVE_RAID, CRONDIS_LEAVE_RAID)
    val returnToLobby = setOf(45129, 45144, 45543, 45844, 46055)
    val rewardSarcophagi = setOf(46220, 46218, 44547, 44545, 29994, 46216, 46215, 46219, 46217)
    val scabarasButtons = setOf(45338, 45355)
    val scabarasTiles = (45356..45364).toSet()
    val scabarasPassages = setOf(45337, 45343, 45344)
    val crondis = CrondisAssets.locIds + setOf(45396)
    val apmeken = setOf(45494, 45495, 45497, 45498, 45500)
    val hetMovableMirrors = setOf(45455)
    val hetDirtyMirrors = setOf(45457)
    val hetBreakableBarriers = setOf(45462, 45464)
    val hetSealNpcs = setOf(11706, 11707)
    val hetPickaxeStatues =
        setOf(
            45468,
            45469,
            45470,
            45471,
            45472,
            45473,
            45474,
            45475,
            45476,
            45477,
            45478,
            45479,
            45480,
            45481,
            45482,
            45483,
            45484,
            55808,
            55809,
        )
    val pathEntrances =
        mapOf(
            SCABARAS_PATH to ToaPath.SCABARAS,
            SCABARAS_QUICK to ToaPath.SCABARAS,
            APMEKEN_PATH to ToaPath.APMEKEN,
            APMEKEN_QUICK to ToaPath.APMEKEN,
            CRONDIS_PATH to ToaPath.CRONDIS,
            CRONDIS_QUICK to ToaPath.CRONDIS,
            HET_PATH to ToaPath.HET,
            HET_QUICK to ToaPath.HET,
        )
    val bossCrystals =
        mapOf(
            KEPHRI_CRYSTAL to ToaPath.SCABARAS,
            ZEBAK_CRYSTAL to ToaPath.CRONDIS,
            BABA_CRYSTAL to ToaPath.APMEKEN,
            AKKHA_CRYSTAL to ToaPath.HET,
        )
    val disabledPathEntrances = emptySet<Int>()
    val disabledBossCrystals = emptySet<Int>()
}

internal object ToaNpcs {
    const val COMPLETION_SPIRIT = 11689
    const val HELPFUL_SPIRIT = 11694
    const val OSMUMTEN = 11693
    const val KEPHRI = 11719
    const val ZEBAK = ZebakAssets.BODY
    const val ZEBAK_TAIL = ZebakAssets.TAIL
    const val ZEBAK_ENRAGED = ZebakAssets.ENRAGED_BODY
    const val ZEBAK_DEAD = ZebakAssets.DEAD_BODY
    const val ZEBAK_DEAD_TAIL = ZebakAssets.DEAD_TAIL
    const val CRONDIS_PALM = CrondisAssets.PALM
    const val CRONDIS_PALM_COMPLETE = CrondisAssets.PALM_COMPLETE
    const val CRONDIS_CROCODILE = CrondisAssets.CROCODILE
    const val BABA = 11778
    const val AKKHA = AkkhaAssets.SPAWN
    const val WARDEN_OBELISK = WardenObelisk.ATTACKABLE
    const val ELIDINIS_WARDEN = 11753
    const val ELIDINIS_WARDEN_RANGE = 11754
    const val WARDEN_CORE = 11771
    const val TUMEKEN_WARDEN = 11762
    const val WARDEN_SKULL = 11772
    const val AKKHA_WHITE_ORB = AkkhaAssets.ENRAGE_ORB
    val wardenPhases =
        setOf(
            WARDEN_OBELISK,
            WardenObelisk.EXHAUSTED,
            ElidinisWarden.CHARGING,
            TumekenWarden.CHARGING,
            ElidinisWarden.MAGIC_FORM,
            ElidinisWarden.RANGED_FORM,
            ElidinisWarden.CORE_EJECTED,
            ElidinisWarden.DESTROYED,
            ElidinisWarden.FINAL_FORM,
            ElidinisWarden.FINAL_INVULNERABLE,
            TumekenWarden.RANGED_FORM,
            TumekenWarden.MAGIC_FORM,
            TumekenWarden.CORE_EJECTED,
            TumekenWarden.DESTROYED,
            TumekenWarden.FINAL_FORM,
            TumekenWarden.FINAL_INVULNERABLE,
            WardenCores.ELIDINIS,
            WardenCores.TUMEKEN,
            WardenEnergySiphons.ACTIVE,
            WardenEnergySiphons.DISABLED,
            TumekenWarden.ZEBAK_PHANTOM,
            TumekenWarden.BABA_PHANTOM,
            TumekenWarden.KEPHRI_PHANTOM,
            TumekenWarden.AKKHA_PHANTOM,
        )
    val apmekenWaveTypes =
        listOf(11709, 11710, 11711, 11712, 11713, 11714, 11715, 11716, 11717, 11718)
    val kephriAdds = setOf(11723, 11724, 11725, 11726, 11727, 11728, 11729)
    val zebakHazards =
        ZebakAssets.npcIds - setOf(ZEBAK, ZEBAK_TAIL, ZEBAK_ENRAGED, ZEBAK_DEAD, ZEBAK_DEAD_TAIL)
    val babaHazards = (BabaAssets.BABOON..BabaAssets.SARCOPHAGUS).toSet()
    val akkhaBossForms = (AkkhaAssets.SPAWN..AkkhaAssets.ENRAGE_DUMMY).toSet()
    val akkhaShadows =
        setOf(AkkhaAssets.SHADOW, AkkhaAssets.SHADOW_ENRAGE, AkkhaAssets.SHADOW_ENRAGE_DUMMY)
    val akkhaForms = akkhaBossForms + akkhaShadows
    val allCombat: Set<Int> = buildSet {
        add(KEPHRI)
        add(KephriEncounter.RECOVERING)
        add(KephriEncounter.FINAL)
        add(KephriEncounter.DEAD)
        add(ZEBAK)
        addAll(CrondisAssets.npcIds + ZebakAssets.npcIds)
        add(BABA)
        addAll(BabaAssets.npcIds)
        add(AKKHA)
        addAll(wardenPhases)
        addAll(apmekenWaveTypes)
        addAll(kephriAdds)
        addAll(zebakHazards)
        addAll(babaHazards)
        addAll(akkhaForms)
        add(AKKHA_WHITE_ORB)
        add(ScabarasRevision240RuntimeLayout.RANGED_SCARAB_NPC)
        add(ScabarasRevision240RuntimeLayout.obelisks.attackableNpc)
        add(ScabarasRevision240RuntimeLayout.obelisks.hitNpc)
        // 8367 is ToB's normal-mode blood spawn in this cache and already owns the global
        // player-attack event. Zebak blood spawns are instance hazards, not normal combat NPCs.
        addAll(setOf(11774, 11775))
    }
}

internal object ToaAssets {
    const val KEPHRI_BOMB = 2266
    const val KEPHRI_BUG = 2147
    const val KEPHRI_EGG = 2164
    const val KEPHRI_EGG_ALT = 2165
    const val KEPHRI_SCARAB_MAGIC = 2168
    const val KEPHRI_SCARAB_RANGE = 2152
    const val AKKHA_MAGIC = AkkhaAssets.MAGIC_TRAVEL
    const val AKKHA_RANGE = AkkhaAssets.RANGE_TRAVEL
    const val BABA_SARCOPHAGUS = BabaAssets.SARCOPHAGUS_ENERGY
    const val BABA_BOULDER = BabaAssets.ROCKFALL
    const val ZEBAK_MAGIC = ZebakAssets.MAGIC_SPLIT
    const val ZEBAK_RANGE = ZebakAssets.RANGE_SPLIT
    const val WARDEN_SWORD = 2204
    const val WARDEN_ARROW = 2206
    const val WARDEN_BLUE_BALL = 2208
    const val WARDEN_FLOOR = 2220
    const val WARDEN_MAGIC = WardenPhaseTwoMechanics.MAGIC_PROJECTILE
    const val WARDEN_MAGIC_IMPACT = WardenPhaseTwoMechanics.MAGIC_IMPACT
    const val WARDEN_DEATH_ORB = WardenPhaseTwoMechanics.LIGHTNING_SKULL_PROJECTILE
    const val WARDEN_PHASE_ONE_BIG_BALL = 2237
    const val WARDEN_PHASE_ONE_SMALL_BALL = 2238
    const val WARDEN_CHARGE_INDICATOR = 2227
    const val WARDEN_RANGE = WardenPhaseTwoMechanics.RANGED_PROJECTILE
    const val WARDEN_STONE_PROJECTILE = WardenPhaseTwoMechanics.STONE_PROJECTILE
    const val WARDEN_STONE_IMPACT = WardenPhaseTwoMechanics.STONE_GRAPHIC
    const val WARDEN_TUMEKEN_CORE_PROJECTILE = WardenPhaseTwoMechanics.TUMEKEN_CORE_PROJECTILE
    const val WARDEN_ELIDINIS_CORE_PROJECTILE = WardenPhaseTwoMechanics.ELIDINIS_CORE_PROJECTILE
    const val WARDEN_SIPHON_TO_BOSS = 2226
    const val WARDEN_BEAM_WARNING = WardenPhaseTwoMechanics.BEAM_WARNING
    const val WARDEN_BEAM_ACTIVE = WardenPhaseTwoMechanics.BEAM_ACTIVE
    const val WARDEN_WINDMILL_WARNING = WardenPhaseTwoMechanics.WINDMILL_WARNING

    val sequences = buildSet {
        addAll(
            setOf(
                9577,
                9578,
                9579,
                9581,
                KephriEncounter.DEATH_ANIMATION,
                9587,
                9588,
                9589,
                KephriEggs.SPAWN_ANIMATION,
                KephriScarabAgile.ATTACK_ANIMATION,
                KephriScarabAgile.SPAWN_ANIMATION,
                KephriScarabMager.DETONATE_ANIMATION,
                832,
                9490,
                KephriSwarms.SPAWN_ANIMATION,
                9624,
                9628,
                9632,
                WardenPhaseTwoMechanics.MELEE_ATTACK_ANIMATION,
                9660,
                WardenPhaseTwoMechanics.MAGIC_ATTACK_ANIMATION,
                9662,
                9670,
                9672,
                WardenPhaseTwoMechanics.OBELISK_LIGHTNING_ANIMATION,
                WardenPhaseTwoMechanics.OBELISK_CONVERGING_BEAM_ANIMATION,
                WardenPhaseTwoMechanics.OBELISK_WINDMILL_ANIMATION,
                9675,
                9677,
                9678,
                9682,
                9683,
                9684,
                9685,
                9743,
                9744,
                9749,
                9770,
                KephriDungStrike.PLAYER_THROW_ANIMATION,
                KephriDungStrike.PLAYER_DUNG_MOVE_ANIMATION,
                WardenPhaseTwoMechanics.STONE_PLAYER_ANIMATION,
            )
        )
        addAll(CrondisAssets.sequenceIds)
        addAll(ZebakAssets.sequenceIds)
        addAll(AkkhaAssets.sequenceIds)
        addAll(BabaAssets.sequenceIds)
    }
    val spotanims = buildSet {
        addAll(
            setOf(
                KEPHRI_BOMB,
                KephriBombs.OVERHEAD_PROJECTILE,
                KephriBombs.SHADOW,
                KephriBombs.IMPACT,
                KephriEncounter.SHIELD_GRAPHIC,
                KephriEncounter.RECOVERY_SHIELD_GRAPHIC,
                KephriEncounter.SHIELD_BREAK_GRAPHIC,
                KephriEncounter.REFORM_GRAPHIC,
                KEPHRI_BUG,
                KEPHRI_EGG,
                KEPHRI_EGG_ALT,
                KephriEggs.HATCH_ANIMATION,
                KephriScarabMelee.HEAL_PROJECTILE,
                KephriScarabMager.CHARGE_GRAPHIC,
                KephriScarabMager.CHARGED_GRAPHIC,
                KEPHRI_SCARAB_MAGIC,
                KEPHRI_SCARAB_RANGE,
                KephriDungStrike.DUNG_GRAPHIC,
                KephriDungStrike.PLAYER_FLIES_GRAPHIC,
                KephriDungStrike.PLAYER_STUN_GRAPHIC,
                1766,
                302,
                317,
                AKKHA_MAGIC,
                AKKHA_RANGE,
                AkkhaAssets.MAGIC_IMPACT,
                AkkhaAssets.LIGHTNING,
                AkkhaAssets.DARKNESS,
                AkkhaAssets.BURN,
                AkkhaAssets.FREEZE,
                AkkhaAssets.ENRAGE_ORB_IMPACT,
                BABA_SARCOPHAGUS,
                BABA_BOULDER,
                ZEBAK_MAGIC,
                ZEBAK_RANGE,
                WARDEN_SWORD,
                WARDEN_ARROW,
                WARDEN_BLUE_BALL,
                WARDEN_FLOOR,
                WARDEN_MAGIC,
                WARDEN_MAGIC_IMPACT,
                WARDEN_DEATH_ORB,
                WARDEN_PHASE_ONE_BIG_BALL,
                WARDEN_PHASE_ONE_SMALL_BALL,
                WARDEN_CHARGE_INDICATOR,
                WARDEN_RANGE,
                WARDEN_STONE_PROJECTILE,
                WARDEN_STONE_IMPACT,
                WARDEN_TUMEKEN_CORE_PROJECTILE,
                WARDEN_ELIDINIS_CORE_PROJECTILE,
                WARDEN_SIPHON_TO_BOSS,
                WARDEN_BEAM_WARNING,
                WARDEN_BEAM_ACTIVE,
                WARDEN_WINDMILL_WARNING,
                WardenEnergySiphons.LANDING_GRAPHIC,
                WardenEnergySiphons.FAILURE_GRAPHIC,
                WardenPhaseThreeTransition.DESTROYED_WARDEN_SPOTANIM,
                WardenPhaseThreeTransition.FINAL_WARDEN_SPOTANIM,
                WardenFloor.WARNING_GRAPHIC,
                WardenFloor.BREAK_GRAPHIC,
                WardenFloor.REMOVE_PROJECTILE,
                WardenFloor.LIGHTNING_GRAPHIC,
            )
        )
        addAll(CrondisAssets.spotanimIds)
        addAll(ZebakAssets.spotanimIds)
        addAll(BabaAssets.spotanimIds)
    }
}
