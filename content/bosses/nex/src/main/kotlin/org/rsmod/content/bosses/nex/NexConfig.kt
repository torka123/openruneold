package org.rsmod.content.bosses.nex

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val NEX_MAX_HITPOINTS = 3400
internal const val NEX_BODYGUARD_HITPOINTS = 500
internal const val NEX_REAVER_HITPOINTS = 150
internal const val NEX_ATTACK_RATE = 4
internal const val NEX_RESPAWN_TICKS = 50
internal const val NEX_DROP_DURATION = 18_000

internal val NEX_BANK_TELEPORT = CoordGrid(2900, 5203, 0)
internal val NEX_BANK_RETURN = CoordGrid(2908, 5203, 0)
internal val NEX_ARENA_ENTRY = CoordGrid(2910, 5203, 0)
internal val NEX_SPAWN = CoordGrid(2924, 5202, 0)
internal val NEX_BARRIER = CoordGrid(2909, 5202, 0)
internal val NEX_ALTAR = CoordGrid(2939, 5202, 0)

internal data class NexArena(
    val name: String,
    val spawn: CoordGrid,
    val xRange: IntRange,
    val zRange: IntRange,
) {
    fun contains(tile: CoordGrid): Boolean =
        tile.level == spawn.level && tile.x in xRange && tile.z in zRange
}

internal val NEX_CHAMBER_ARENA =
    NexArena(name = "Ancient Prison", spawn = NEX_SPAWN, xRange = 2910..2940, zRange = 5189..5217)

internal enum class NexState {
    IDLE,
    SPAWNING,
    SMOKE,
    FUMUS,
    SHADOW,
    UMBRA,
    BLOOD,
    CRUOR,
    ICE,
    GLACIES,
    ZAROS,
    DYING,
    RESETTING,
}

internal enum class NexSpecial {
    CHOKE,
    DRAG,
    SMOKE_DASH,
    SHADOW_SMASH,
    EMBRACE_DARKNESS,
    BLOOD_SIPHON,
    BLOOD_SACRIFICE,
    CONTAINMENT,
    ICE_PRISON,
}

internal enum class NexOverhead {
    SOUL_SPLIT,
    DEFLECT_MELEE,
    NONE,
}

internal object NexAssets {
    const val NEX = "npc.nex"
    const val NEX_SPAWNING = "npc.nex_start"
    const val NEX_SOUL_SPLIT = "npc.nex_soulsplit"
    const val NEX_DEFLECT = "npc.nex_deflect"
    const val NEX_DYING = "npc.nex_dead"
    const val FUMUS = "npc.fumus"
    const val UMBRA = "npc.umbra"
    const val CRUOR = "npc.cruor"
    const val GLACIES = "npc.glacies"
    const val BLOOD_REAVER = "npc.blood_reaver_nex"

    const val DASH = "seq.nex_dash_attack"
    const val TURMOIL = "seq.nex_turmoil"
    const val ATTACK = "seq.nex_attack"
    const val BLAST = "seq.nex_blast_away"
    const val SIPHON = "seq.nex_blood_siphon"
    const val DEATH = "seq.nex_death"
    const val DEFEND = "seq.nex_defend"
    const val SMASH = "seq.nex_smash_attack"
    const val SPIN = "seq.nex_spin_out"
    const val ALT_CAST = "seq.nex_alternate_cast_attack"
    const val CAST = "seq.nex_cast_attack"
    const val SUMMON = "seq.nex_summon"
    const val BARRIER = "seq.nex_prison_barrier"

    const val BARRIER_CONTROLLER = 42967
    const val BARRIER_BUSY_CONTROLLER = 42968
    val BARRIER_FORMS = listOf(42937, 42938, 42939, 42940, 42941)
    const val ZAROS_ALTAR = 42965
    const val SHADOW_SMASH_LOC = 42942
    const val ICE_PRISON_LOC = 42943
    const val ICE_PRISON_ALT_LOC = 42944

    val npcForms =
        listOf(
            NEX,
            NEX_SPAWNING,
            NEX_SOUL_SPLIT,
            NEX_DEFLECT,
            NEX_DYING,
            FUMUS,
            UMBRA,
            CRUOR,
            GLACIES,
            BLOOD_REAVER,
        )
    val sequences =
        listOf(
            DASH,
            TURMOIL,
            ATTACK,
            BLAST,
            SIPHON,
            DEATH,
            DEFEND,
            SMASH,
            SPIN,
            ALT_CAST,
            CAST,
            SUMMON,
            BARRIER,
        )
}

internal data class NexContribution(var damage: Int = 0, var firstCycle: Int = Int.MAX_VALUE)

internal data class NexAchievementFlags(
    var smokeDashHit: Boolean = false,
    var shadowSmashHit: Boolean = false,
    var bloodSiphonHeal: Boolean = false,
    var iceSpecialHit: Boolean = false,
    var playerDeath: Boolean = false,
    var coughActiveAtKill: Boolean = false,
)

internal val NEXLING_UNLOCKED: AttributeKey<Boolean> =
    AttributeKey(persistenceKey = "nexling_unlocked")
