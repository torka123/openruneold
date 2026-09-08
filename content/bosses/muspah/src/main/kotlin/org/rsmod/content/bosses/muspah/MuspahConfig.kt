package org.rsmod.content.bosses.muspah

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal const val MUSPAH_INSTANCE_KEY = "phantom_muspah"
internal const val MUSPAH_MAX_HITPOINTS = 850
internal const val MUSPAH_SHIELD_POINTS = 75
internal const val MUSPAH_RANGED_THRESHOLD = 100
internal const val MUSPAH_MELEE_THRESHOLD = 80
internal const val MUSPAH_LATER_SWAP_POSITIVE_HITS = 4
internal const val MUSPAH_FIRST_SPECIAL_HITPOINTS = 637
internal const val MUSPAH_SECOND_SPECIAL_HITPOINTS = 425
internal const val MUSPAH_SHIELD_TRANSITION_HITPOINTS = 127
internal const val MUSPAH_HOMING_DURATION = 34
internal const val MUSPAH_RESPAWN_TICKS = 50
internal const val MUSPAH_NPC_LIFETIME = Int.MAX_VALUE
internal const val MUSPAH_DROP_DURATION = 18_000
internal const val MUSPAH_RANGED_ATTACK_RATE = 6
internal const val MUSPAH_MELEE_ATTACK_RATE = 5
internal const val MUSPAH_SHIELD_ATTACK_RATE = 5
internal const val MUSPAH_FINAL_ATTACK_RATE = 5
internal const val MUSPAH_FINAL_SPIKE_ATTACKS = 4
internal const val MUSPAH_AIR_WEAKNESS_PERCENT = 65
internal const val MUSPAH_FREEZE_RESISTANCE_PERCENT = 33

/** Enable only when Secrets of the North has a real persisted quest state in this server. */
internal const val MUSPAH_REQUIRE_SECRETS_OF_NORTH = false

/** Verified cache tile directly beside entrance loc 46597 in Ghorrock Dungeon. */
internal val MUSPAH_OUTSIDE = CoordGrid(2909, 10316, 0)

internal val MUSPAH_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(11330),
        enterCoord = RegionLocal(0, 44, 66, 41, 34),
        exitCoord = MUSPAH_OUTSIDE,
    )

internal fun muspahSpec(activeType: dev.openrune.types.NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Phantom Muspah",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private repeatable Phantom Muspah encounter",
        )
        .withArea(MUSPAH_AREA, settingsRowId = 0)

internal enum class MuspahState {
    STARTING,
    RANGED,
    MELEE,
    TRANSFORMING,
    LIGHTNING_CLOUDS,
    HOMING_SPIKES,
    SHOCKWAVE,
    PRAYER_SHIELD,
    FINAL_STAND,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

internal enum class MuspahForm {
    RANGED,
    MELEE,
    SHIELD,
    FINAL,
    TELEPORT,
}

internal enum class MuspahSpecial {
    LIGHTNING_CLOUDS,
    HOMING_SPIKES,
}

internal enum class MuspahAttack {
    MELEE,
    RANGED,
    CHARGED_MAGIC,
}

internal object MuspahAssets {
    const val RANGED = "npc.muspah"
    const val MELEE = "npc.muspah_melee"
    const val SHIELD = "npc.muspah_soulsplit"
    const val FINAL = "npc.muspah_final"
    const val TELEPORT = "npc.muspah_teleport"

    const val IDLE = "seq.npc_muspah_idle_01"
    const val WALK_FORWARD = "seq.npc_muspah_walk_forward_01"
    const val WALK_BACKWARD = "seq.npc_muspah_walk_backward_01"
    const val ATTACK_MAGIC = "seq.npc_muspah_attack_magic_01"
    const val ATTACK_MELEE = "seq.npc_muspah_attack_melee_01"
    const val ATTACK_RANGED = "seq.npc_muspah_attack_ranged_01"
    const val ATTACK_SUMMON = "seq.npc_muspah_attack_summon_01"
    const val ATTACK_EXPLOSION = "seq.npc_muspah_attack_explosion_01"
    const val TELEPORT_DISAPPEAR = "seq.npc_muspah_teleport_disappear_01"
    const val TELEPORT_ATTACK = "seq.npc_muspah_teleport_attack_01"
    const val TELEPORT_APPEAR = "seq.npc_muspah_teleport_appear_01"
    const val TRANSFORM_DISAPPEAR_1 = "seq.npc_muspah_transform_disappear_01"
    const val TRANSFORM_APPEAR_1 = "seq.npc_muspah_transform_appear_01"
    const val TRANSFORM_DISAPPEAR_2 = "seq.npc_muspah_transform_disappear_02"
    const val TRANSFORM_APPEAR_2 = "seq.npc_muspah_transform_appear_02"
    const val SPAWN = "seq.npc_muspah_spawn_01"
    const val DEATH_1 = "seq.npc_muspah_death_01"
    const val DEATH_2 = "seq.npc_muspah_death_02"

    const val MAGIC_TRAVEL = "spotanim.projectile_muspah_attack_magic_01"
    const val MAGIC_IMPACT = "spotanim.impact_muspah_attack_magic_01"
    const val RANGED_TRAVEL = "spotanim.projectile_muspah_attack_ranged_01"
    const val RANGED_IMPACT = "spotanim.muspah_range_impact"
    const val SHOCKWAVE = "spotanim.projectile_muspah_attack_explode_03"
    const val LIGHTNING = "spotanim.vfx_muspah_teleport_attack_01"
    const val SPIKE_WARNING = "spotanim.vfx_muspah_spike_warning_01"

    const val SPIKE_PRE = "loc.muspah_spike_pre"
    const val SPIKE = "loc.muspah_spike"
    const val SPIKE_FOLLOW = "loc.muspah_spike_follow"

    const val ENTRANCE_ID = 46597
    const val EXIT_ID = 46598
    const val ESCAPE_ID = 46599
    const val SCOREBOARD_ID = 46700

    val npcForms = listOf(RANGED, MELEE, SHIELD, FINAL, TELEPORT)
    val sequences =
        listOf(
            IDLE,
            WALK_FORWARD,
            WALK_BACKWARD,
            ATTACK_MAGIC,
            ATTACK_MELEE,
            ATTACK_RANGED,
            ATTACK_SUMMON,
            ATTACK_EXPLOSION,
            TELEPORT_DISAPPEAR,
            TELEPORT_ATTACK,
            TELEPORT_APPEAR,
            TRANSFORM_DISAPPEAR_1,
            TRANSFORM_APPEAR_1,
            TRANSFORM_DISAPPEAR_2,
            TRANSFORM_APPEAR_2,
            SPAWN,
            DEATH_1,
            DEATH_2,
        )
    val spotanims =
        listOf(
            MAGIC_TRAVEL,
            MAGIC_IMPACT,
            RANGED_TRAVEL,
            RANGED_IMPACT,
            SHOCKWAVE,
            LIGHTNING,
            SPIKE_WARNING,
        )
}

internal data class MovingSpike(var loc: LocInfo, var nextMoveCycle: Int)

internal data class LightningCloud(
    var tile: CoordGrid,
    val deltaX: Int,
    val deltaZ: Int,
    val endCycle: Int,
    var nextMoveCycle: Int,
)

internal val MUSPAH_PERSONAL_BEST_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "phantom_muspah_personal_best_ticks")
