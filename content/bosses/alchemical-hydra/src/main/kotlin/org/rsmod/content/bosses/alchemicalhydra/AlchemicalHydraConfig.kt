package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.types.NpcServerType
import dev.openrune.types.ProjAnimType
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal const val HYDRA_INSTANCE_KEY = "alchemical_hydra"
internal const val HYDRA_MAX_HITPOINTS = 1_100
internal const val HYDRA_RESPAWN_TICKS = 43
internal const val HYDRA_ENTRY_CLIENT_CYCLES = 60
internal const val HYDRA_ENTRY_ANGLE = 1024
internal const val HYDRA_NPC_LIFETIME = Int.MAX_VALUE

internal const val HYDRA_SOURCE_ZONE_X = 165
internal const val HYDRA_SOURCE_ZONE_Z = 1278
internal const val HYDRA_TEMPLATE_WIDTH = 9
internal const val HYDRA_TEMPLATE_LENGTH = 9
internal const val HYDRA_SOURCE_MAP_X = 21
internal const val HYDRA_SOURCE_MAP_Z = 160

internal val HYDRA_PUBLIC_EXIT = CoordGrid(1352, 10250, 0)
internal val HYDRA_OBSERVATION_ENTRY = RegionLocal(0, 21, 160, 7, 10)
internal val HYDRA_OBSERVATION_DESTINATION = RegionLocal(0, 21, 160, 7, 12)
internal val HYDRA_CHAMBER_ENTRY = RegionLocal(0, 21, 160, 13, 18)
internal val HYDRA_BOSS_SPAWN = RegionLocal(0, 21, 160, 20, 25)

internal val HYDRA_TEMPLATE =
    RegionTemplate.create {
        copyAllLevels(HYDRA_SOURCE_ZONE_X, HYDRA_SOURCE_ZONE_Z) {
            zoneWidth = HYDRA_TEMPLATE_WIDTH
            zoneLength = HYDRA_TEMPLATE_LENGTH
        }
    }

internal val HYDRA_AREA =
    InstanceArea.template(
        template = HYDRA_TEMPLATE,
        enterCoord = HYDRA_OBSERVATION_ENTRY,
        exitCoord = HYDRA_PUBLIC_EXIT,
    )

internal fun hydraSpec(activeType: NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Alchemical Hydra",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private Alchemical Hydra encounter",
        )
        .withArea(HYDRA_AREA, settingsRowId = 0)

internal enum class HydraPhase {
    GREEN,
    BLUE,
    RED,
    FINAL,
}

internal enum class HydraState {
    OBSERVATION,
    ACTIVE,
    TRANSITION,
    DYING,
    RESPAWNING,
    INVALIDATED,
}

internal enum class HydraAttackStyle {
    MAGIC,
    RANGED;

    fun opposite(): HydraAttackStyle = if (this == MAGIC) RANGED else MAGIC
}

internal enum class HydraSpecial {
    POISON,
    LIGHTNING,
    FLAME,
}

internal enum class HydraVent(val locId: Int, val coord: RegionLocal) {
    RED(34568, RegionLocal(0, 21, 160, 27, 23)),
    GREEN(34569, RegionLocal(0, 21, 160, 27, 32)),
    BLUE(34570, RegionLocal(0, 21, 160, 18, 32)),
}

internal object AlchemicalHydraAssets {
    const val GREEN = 8615
    const val GREEN_TO_BLUE = 8616
    const val BLUE_TO_RED = 8617
    const val RED_TO_FINAL = 8618
    const val BLUE = 8619
    const val RED = 8620
    const val FINAL = 8621
    const val FINAL_DEATH = 8622

    const val GREEN_POISON_ANIM = 8234
    const val GREEN_RANGE_ANIM = 8235
    const val GREEN_MAGIC_ANIM = 8236
    const val GREEN_TRANSITION_ANIM = 8237
    const val BLUE_EMERGE_ANIM = 8238
    const val BLUE_LIGHTNING_ANIM = 8241
    const val BLUE_RANGE_ANIM = 8242
    const val BLUE_MAGIC_ANIM = 8243
    const val BLUE_TRANSITION_ANIM = 8244
    const val RED_EMERGE_ANIM = 8245
    const val RED_FLAME_ANIM = 8248
    const val RED_RANGE_ANIM = 8249
    const val RED_MAGIC_ANIM = 8250
    const val RED_TRANSITION_ANIM = 8251
    const val FINAL_EMERGE_ANIM = 8252
    const val FINAL_MAGIC_OR_POISON_ANIM = 8254
    const val FINAL_RANGE_ANIM = 8255
    const val DEATH_ANIM = 8257
    const val DEATH_END_ANIM = 8258
    const val ENTRY_ANIM = 839

    const val POISON_PROJECTILE = 1644
    const val POISON_SPLASH = 1645
    const val POISON_POOL = 1655
    const val MAGIC_PROJECTILE = 1662
    const val RANGED_PROJECTILE = 1663
    const val LIGHTNING_CENTER = 1664
    const val LIGHTNING_CURRENT_PROJECTILE = 1665
    const val LIGHTNING_CURRENT = 1666
    const val FLAME_PROJECTILE = 1667
    const val FLAME_TILE = 1668

    const val DOOR_SOUTH = 34553
    const val DOOR_NORTH = 34554
    const val DOOR_SOUTH_GRAPHIC = 34555
    const val DOOR_NORTH_GRAPHIC = 34556

    val activeForms = listOf(GREEN, BLUE, RED, FINAL)
    val transitionForms = listOf(GREEN_TO_BLUE, BLUE_TO_RED, RED_TO_FINAL)
    val allForms = activeForms + transitionForms + FINAL_DEATH
    val doors = listOf(DOOR_SOUTH, DOOR_NORTH, DOOR_SOUTH_GRAPHIC, DOOR_NORTH_GRAPHIC)
    val animations =
        listOf(
            GREEN_POISON_ANIM,
            GREEN_RANGE_ANIM,
            GREEN_MAGIC_ANIM,
            GREEN_TRANSITION_ANIM,
            BLUE_EMERGE_ANIM,
            BLUE_LIGHTNING_ANIM,
            BLUE_RANGE_ANIM,
            BLUE_MAGIC_ANIM,
            BLUE_TRANSITION_ANIM,
            RED_EMERGE_ANIM,
            RED_FLAME_ANIM,
            RED_RANGE_ANIM,
            RED_MAGIC_ANIM,
            RED_TRANSITION_ANIM,
            FINAL_EMERGE_ANIM,
            FINAL_MAGIC_OR_POISON_ANIM,
            FINAL_RANGE_ANIM,
            DEATH_ANIM,
            DEATH_END_ANIM,
            ENTRY_ANIM,
        )

    val BASIC_PROJECTILE =
        ProjAnimType(
            startHeight = 48,
            endHeight = 24,
            delay = 30,
            angle = 15,
            lengthAdjustment = 50,
            progress = 16,
            stepMultiplier = 5,
        )

    val SPECIAL_PROJECTILE =
        ProjAnimType(
            startHeight = 48,
            endHeight = 0,
            delay = 20,
            angle = 20,
            lengthAdjustment = 45,
            progress = 0,
            stepMultiplier = 5,
        )
}
