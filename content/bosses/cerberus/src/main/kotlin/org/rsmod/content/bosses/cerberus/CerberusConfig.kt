package org.rsmod.content.bosses.cerberus

import dev.openrune.types.NpcServerType
import dev.openrune.types.ProjAnimType
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val CERBERUS_INSTANCE_KEY = "cerberus_lair"
internal const val CERBERUS_SOURCE_MAP_X = 19
internal const val CERBERUS_SOURCE_MAP_Z = 19
internal const val CERBERUS_SOURCE_REGION = (CERBERUS_SOURCE_MAP_X shl 8) or CERBERUS_SOURCE_MAP_Z

internal const val CERBERUS_ENTRY_LOCAL_X = 24
internal const val CERBERUS_ENTRY_LOCAL_Z = 10
internal const val CERBERUS_BOSS_LOCAL_X = 22
internal const val CERBERUS_BOSS_LOCAL_Z = 34
// The western room polygon starts north of the entrance passage. The fire wall is deliberately
// separate: waking Cerberus at the wall made the entire stand animation finish off-screen.
internal const val CERBERUS_CHAMBER_MIN_X = 0
internal const val CERBERUS_CHAMBER_MAX_X = 43
internal const val CERBERUS_CHAMBER_MIN_Z = 27
internal const val CERBERUS_CHAMBER_MAX_Z = 58
internal const val CERBERUS_FIRE_WALL_MIN_X = 23
internal const val CERBERUS_FIRE_WALL_MAX_X = 25
internal const val CERBERUS_FIRE_WALL_Z = 26

internal val CERBERUS_PUBLIC_EXIT = CoordGrid(1310, 1249, 0)

internal val CERBERUS_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(CERBERUS_SOURCE_REGION),
        enterCoord =
            RegionLocal(
                0,
                CERBERUS_SOURCE_MAP_X,
                CERBERUS_SOURCE_MAP_Z,
                CERBERUS_ENTRY_LOCAL_X,
                CERBERUS_ENTRY_LOCAL_Z,
            ),
        exitCoord = CERBERUS_PUBLIC_EXIT,
    )

internal fun cerberusSpec(activeType: NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Cerberus",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private Cerberus encounter",
            recordsEligible = false,
        )
        .withArea(CERBERUS_AREA, settingsRowId = 0)

internal enum class CerberusPhase {
    SITTING,
    STANDING,
    ACTIVE,
    RESETTING,
    DYING,
    RESPAWNING,
    COMPLETE,
    INVALIDATED,
}

internal enum class CerberusAttack {
    BASIC,
    TRIPLE,
    SOULS,
    LAVA,
}

internal enum class CerberusAttackStyle {
    MAGIC,
    RANGED,
    MELEE,
}

internal enum class CerberusHead {
    LEFT,
    CENTRE,
    RIGHT,
}

internal enum class CerberusTask {
    UNREQUIRED_ANTIFIRE,
    VETERAN,
    GHOST_BUSTER,
    ANTI_BITE_MECHANICS,
    MASTER,
    AROOO_NO_MORE,
}

internal object CerberusAssets {
    const val ACTIVE_NPC_ID = 5862
    const val SITTING_NPC_ID = 5863
    const val RESETTING_NPC_ID = 5866
    const val RANGED_SOUL_NPC_ID = 5867
    const val MAGIC_SOUL_NPC_ID = 5868
    const val MELEE_SOUL_NPC_ID = 5869
    const val FIRE_WALL_LOC_ID = 23105

    const val IDLE_ANIMATION = 4484
    const val SITTING_ANIMATION = 4485
    const val STAND_ANIMATION = 4486
    const val SIT_ANIMATION = 4487
    const val WALK_ANIMATION = 4488
    const val DEFEND_ANIMATION = 4489

    const val RANGED_ANIMATION = 4490
    const val MELEE_ANIMATION = 4491
    const val MAGIC_ANIMATION = 4492
    const val TRIPLE_SPRAY_ANIMATION = 4493
    const val HOWL_ANIMATION = 4494
    const val DEATH_ANIMATION = 4495
    const val LAVA_ANIMATION = 4501

    const val SOUL_READY_ANIMATION = 8526
    const val SOUL_FLY_ANIMATION = 8527
    const val MELEE_SOUL_ATTACK_ANIMATION = 8528
    const val MAGIC_SOUL_ATTACK_ANIMATION = 8529
    const val RANGED_SOUL_ATTACK_ANIMATION = 8530
    const val SOUL_DEFEND_ANIMATION = 8531

    const val MAGIC_CAST_SPOTANIM = 1241
    const val MAGIC_PROJECTILE_SPOTANIM = 1242
    const val MAGIC_IMPACT_SPOTANIM = 1243
    const val RANGED_IMPACT_SPOTANIM = 1244
    const val RANGED_PROJECTILE_SPOTANIM = 1245
    const val LAVA_LIQUID_SPOTANIM = 1246
    const val LAVA_FLAME_SPOTANIM = 1247
    const val RANGED_SOUL_PROJECTILE_SPOTANIM = 34
    const val MAGIC_SOUL_PROJECTILE_SPOTANIM = 100
    const val MELEE_SOUL_PROJECTILE_SPOTANIM = 1248

    // Revision-240 visual ids with the permitted donor timing translated to client packet units.
    val MAGIC_PROJECTILE =
        ProjAnimType(
            startHeight = 70,
            endHeight = 20,
            delay = 30,
            angle = 11,
            lengthAdjustment = 45,
            progress = 64,
            stepMultiplier = 15,
        )

    val RANGED_PROJECTILE =
        ProjAnimType(
            startHeight = 70,
            endHeight = 20,
            delay = 30,
            angle = 11,
            lengthAdjustment = 45,
            progress = 64,
            stepMultiplier = 15,
        )

    val RANGED_SOUL_PROJECTILE =
        ProjAnimType(
            startHeight = 25,
            endHeight = 20,
            delay = 0,
            angle = 14,
            lengthAdjustment = 10,
            progress = 0,
            stepMultiplier = 5,
        )

    val MAGIC_SOUL_PROJECTILE =
        ProjAnimType(
            startHeight = 25,
            endHeight = 20,
            delay = 27,
            angle = 14,
            lengthAdjustment = 10,
            progress = 0,
            stepMultiplier = 5,
        )

    val MELEE_SOUL_PROJECTILE =
        ProjAnimType(
            startHeight = 25,
            endHeight = 20,
            delay = 0,
            angle = 53,
            lengthAdjustment = 10,
            progress = 0,
            stepMultiplier = 5,
        )

    // 1247 is the travelling lava marker; 1246 is the map impact/pool graphic.
    val LAVA_PROJECTILE =
        ProjAnimType(
            startHeight = 0,
            endHeight = 0,
            delay = 30,
            angle = 127,
            lengthAdjustment = 30,
            progress = 0,
            stepMultiplier = 10,
        )

    const val MAGIC_CAST_SOUND = 162
    const val MAGIC_IMPACT_SOUND = 163
    const val SOUL_HOWL_SOUND = 911
}
