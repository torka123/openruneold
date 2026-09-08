package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.types.NpcServerType
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val GROTESQUE_INSTANCE_KEY = "grotesque_guardians_roof"
internal const val GROTESQUE_SOURCE_MAP_X = 26
internal const val GROTESQUE_SOURCE_MAP_Z = 71
internal const val GROTESQUE_SOURCE_REGION =
    (GROTESQUE_SOURCE_MAP_X shl 8) or GROTESQUE_SOURCE_MAP_Z
internal const val GROTESQUE_SOURCE_LEVEL = 0

internal const val GROTESQUE_ENTRY_LOCAL_X = 33
internal const val GROTESQUE_ENTRY_LOCAL_Z = 23
internal const val GROTESQUE_BELL_LOCAL_X = 31
internal const val GROTESQUE_BELL_LOCAL_Z = 39
internal const val GROTESQUE_DUSK_LOCAL_X = 25
internal const val GROTESQUE_DUSK_LOCAL_Z = 29
internal const val GROTESQUE_DAWN_LOCAL_X = 37
internal const val GROTESQUE_DAWN_LOCAL_Z = 29
internal const val GROTESQUE_ARENA_MIN_X = 26
internal const val GROTESQUE_ARENA_MAX_X = 40
internal const val GROTESQUE_ARENA_MIN_Z = 24
internal const val GROTESQUE_ARENA_MAX_Z = 38

internal val GROTESQUE_PUBLIC_EXIT = CoordGrid(3428, 3541, 2)

internal val GROTESQUE_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(GROTESQUE_SOURCE_REGION),
        enterCoord =
            RegionLocal(
                GROTESQUE_SOURCE_LEVEL,
                GROTESQUE_SOURCE_MAP_X,
                GROTESQUE_SOURCE_MAP_Z,
                GROTESQUE_ENTRY_LOCAL_X,
                GROTESQUE_ENTRY_LOCAL_Z,
            ),
        exitCoord = GROTESQUE_PUBLIC_EXIT,
    )

internal fun grotesqueSpec(dawn: NpcServerType, dusk: NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(dawn, dusk),
            bossName = "Grotesque Guardians",
            recommendedCombat = 90..126,
            teamSize = 1,
            description = "Private Grotesque Guardians encounter",
            recordsEligible = false,
        )
        .withArea(GROTESQUE_AREA, settingsRowId = 0)

internal enum class GrotesquePhase {
    DORMANT,
    DAWN_ONE,
    DAWN_ASCENDING,
    DUSK_TWO,
    LIGHTNING_TRANSITION,
    DAWN_THREE,
    DAWN_DYING,
    DUSK_FOUR,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

internal enum class GuardianRole {
    DAWN,
    DUSK,
}

internal enum class GuardianAttackStyle {
    MELEE,
    RANGED,
}

internal object GrotesqueAssets {
    const val DUSK_PHASE_ONE = 7851
    const val DAWN_PHASE_ONE = 7852
    const val DAWN_DEFENSIVE = 7853
    const val DUSK_DEFENSIVE = 7854
    const val DUSK_LIGHTNING = 7855
    const val DUSK_PHASE_TWO = 7882
    const val DUSK_PHASE_THREE_TRANSITION = 7883
    const val DAWN_PHASE_THREE = 7884
    const val DAWN_DEATH = 7885
    const val DUSK_PHASE_FOUR_SPAWN = 7887
    const val DUSK_PHASE_FOUR = 7888
    const val DUSK_DEATH = 7889
    val DAWN_FORMS = intArrayOf(DAWN_PHASE_ONE, DAWN_DEFENSIVE, DAWN_PHASE_THREE, DAWN_DEATH)
    val DUSK_FORMS =
        intArrayOf(
            DUSK_PHASE_ONE,
            DUSK_DEFENSIVE,
            DUSK_LIGHTNING,
            DUSK_PHASE_TWO,
            DUSK_PHASE_THREE_TRANSITION,
            DUSK_PHASE_FOUR_SPAWN,
            DUSK_PHASE_FOUR,
            DUSK_DEATH,
        )

    const val ROOF_ENTRANCE = 31681
    const val CLOISTER_BELL = 31684
    val ENERGY_SPHERES = intArrayOf(31678, 31679, 31680)

    const val PLAYER_RING_ANIMATION = 390
    const val BELL_ANIMATION = 7748
    const val DAWN_MELEE_ANIMATION = 7769
    const val DAWN_RANGED_ANIMATION = 7770
    const val DAWN_SPHERE_ANIMATION = 7771
    const val DAWN_AOE_ANIMATION = 7772
    const val DAWN_ASCEND_ANIMATION = 7773
    const val DAWN_DESCEND_ANIMATION = 7774
    const val DAWN_DEATH_ANIMATION = 7776
    const val DUSK_MELEE_ANIMATION = 7785
    const val DUSK_MELEE_SECOND_ANIMATION = 7787
    const val DUSK_TRANSFORM_ANIMATION = 7790
    const val DUSK_AOE_ANIMATION = 7792
    const val DUSK_REVERT_ANIMATION = 7793
    const val DUSK_PRISON_ANIMATION = 7799
    const val DUSK_PHASE_FOUR_MELEE_ANIMATION = 7800
    const val DUSK_RANGED_ANIMATION = 7801
    const val DUSK_EXPLOSION_ANIMATION = 7802
    const val DUSK_DEATH_ANIMATION = 7803

    const val SPHERE_PROJECTILE = 1437
    const val SPHERE_MEDIUM_PROJECTILE = 1438
    const val SPHERE_HIGH_PROJECTILE = 1439
    const val RANGED_PROJECTILE = 1444
    const val STONE_PROJECTILE = 1445
    const val DEBRIS_PROJECTILE = 1435
    const val DEBRIS_IMPACT = 1436
    const val DEBRIS_SHADOW = 1446
    const val PRISON_FLAME = 1434
    val ANIMATIONS =
        intArrayOf(
            PLAYER_RING_ANIMATION,
            BELL_ANIMATION,
            DAWN_MELEE_ANIMATION,
            DAWN_RANGED_ANIMATION,
            DAWN_SPHERE_ANIMATION,
            DAWN_AOE_ANIMATION,
            DAWN_ASCEND_ANIMATION,
            DAWN_DESCEND_ANIMATION,
            DAWN_DEATH_ANIMATION,
            DUSK_MELEE_ANIMATION,
            DUSK_MELEE_SECOND_ANIMATION,
            DUSK_TRANSFORM_ANIMATION,
            DUSK_AOE_ANIMATION,
            DUSK_REVERT_ANIMATION,
            DUSK_PRISON_ANIMATION,
            DUSK_PHASE_FOUR_MELEE_ANIMATION,
            DUSK_RANGED_ANIMATION,
            DUSK_EXPLOSION_ANIMATION,
            DUSK_DEATH_ANIMATION,
        )
}
