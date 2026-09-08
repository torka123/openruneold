package org.rsmod.content.bosses.royaltitans

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val ROYAL_TITANS_INSTANCE_KEY = "royal_titans"
internal const val ROYAL_TITANS_SOURCE_REGION = 11669
internal const val ROYAL_TITANS_MAX_HITPOINTS = 600
internal const val ROYAL_TITANS_NPC_LIFETIME = Int.MAX_VALUE

internal val ROYAL_TITANS_OUTSIDE = CoordGrid(2951, 9573, 0)
internal val ROYAL_TITANS_ENTRY = RegionLocal(0, 45, 149, 31, 37)
internal val ROYAL_TITANS_BRANDA_SPAWN = RegionLocal(0, 45, 149, 26, 30)
internal val ROYAL_TITANS_ELDRIC_SPAWN = RegionLocal(0, 45, 149, 34, 30)

internal val ROYAL_TITANS_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(ROYAL_TITANS_SOURCE_REGION),
        enterCoord = ROYAL_TITANS_ENTRY,
        exitCoord = ROYAL_TITANS_OUTSIDE,
    )

internal fun royalTitansSpec(
    branda: dev.openrune.types.NpcServerType,
    eldric: dev.openrune.types.NpcServerType,
) =
    InstanceSettings(
            maxPlayers = 2,
            destroyWhenEmpty = true,
            bossNpc = listOf(branda, eldric),
            bossName = "Royal Titans",
            recommendedCombat = 100..126,
            teamSize = 2,
            description = "Solo or duo Royal Titans encounter",
        )
        .withArea(ROYAL_TITANS_AREA, settingsRowId = 0)

internal object RoyalTitansAssets {
    const val BRANDA_ACTIVE = 12596
    const val ELDRIC_ACTIVE = 14147
    const val BRANDA_CORPSE = 14148
    const val ELDRIC_CORPSE = 14149
    const val FIRE_ELEMENTAL = 14150
    const val ICE_ELEMENTAL = 14151
    const val FIRE_WALL_HITBOX = 14152
    const val ICE_WALL_HITBOX = 14153
    const val AREA_TIMER = 14154
    const val AREA_TIMER_HEADBAR = "headbar.charge_30"

    const val ENTRANCE = 55986
    const val EXIT = 55987
    const val BRANDA_FLOOR_WEST = 55993
    const val BRANDA_FLOOR_EAST = 55994
    const val BRANDA_DEATH_FLOOR_WEST = 55995
    const val BRANDA_DEATH_FLOOR_EAST = 55996
    const val ELDRIC_FLOOR_EAST = 55997
    const val ELDRIC_FLOOR_WEST = 55998
    const val ELDRIC_DEATH_FLOOR_EAST = 55999
    const val ELDRIC_DEATH_FLOOR_WEST = 56000
    const val FIRE_WALL = 56001
    const val ICE_WALL = 56002
    const val SAFE_TILE = 56003
    const val SCOREBOARD = 56004

    const val BRANDA_WALK = 11972
    const val ELDRIC_WALK = 11971
    const val BRANDA_STOMP = 11974
    const val ELDRIC_STOMP = 11973
    const val BRANDA_WALL_CAST = 11976
    const val ELDRIC_WALL_CAST = 11975
    const val BRANDA_SUMMON = 11978
    const val ELDRIC_SUMMON = 11977
    const val BRANDA_AREA_CHARGE = 11981
    const val ELDRIC_AREA_CHARGE = 11979
    const val BRANDA_AREA_EXECUTE = 11982
    const val ELDRIC_AREA_EXECUTE = 11980
    const val BRANDA_TELEPORT = 11984
    const val ELDRIC_TELEPORT = 11983
    const val BRANDA_ARENA_SPAWN = 11966
    const val ELDRIC_ARENA_SPAWN = 11963
    const val BRANDA_DEATH = 11992
    const val ELDRIC_DEATH = 11987
    const val FIRE_ELEMENTAL_SPAWN = 11956
    const val FIRE_ELEMENTAL_DEATH = 11957
    const val FIRE_ELEMENTAL_EXPLODE = 11961
    const val ICE_ELEMENTAL_SPAWN = 11954
    const val ICE_ELEMENTAL_DEATH = 11951
    const val ICE_ELEMENTAL_EXPLODE = 11959

    const val FIRE_AREA_PROJECTILE = 3209
    const val ICE_AREA_PROJECTILE = 3210
    const val STOMP_TILE_GFX = 3211
    const val WALL_CAST_GFX = 3212
    const val ELEMENTAL_SUMMON_GFX = 3213
    const val AREA_CHARGE_GFX = 3214
    const val AREA_EXECUTE_GFX = 3215
    const val TELEPORT_GFX = 3216
    const val BRANDA_SPAWN_GFX = 3217
    const val FIRE_WALL_START_GFX = 3218
    const val FIRE_WALL_ACTIVE_GFX = 3219
    const val FIRE_WALL_END_GFX = 3220
    const val ICE_WALL_START_GFX = 3221
    const val ICE_WALL_ACTIVE_GFX = 3222
    const val ICE_WALL_END_GFX = 3223
    const val BRANDA_DEATH_GFX = 3224
    const val FIRE_ELEMENTAL_SPAWN_GFX = 3204
    const val FIRE_ELEMENTAL_DEATH_GFX = 3205
    const val ICE_ELEMENTAL_SPAWN_GFX = 3202
    const val ICE_ELEMENTAL_DEATH_GFX = 3203
    const val ICE_ELEMENTAL_EXPLOSION_GFX = 3206
    const val FIRE_ELEMENTAL_EXPLOSION_GFX = 3207

    const val FIRE_WALL_FORM_SOUND = 10147
    const val FIRE_WALL_ACTIVE_SOUND = 10148
    const val FIRE_WALL_BREAK_SOUND = 10146
    const val ICE_WALL_FORM_SOUND = 10116
    const val ICE_WALL_ACTIVE_SOUND = 10118
    const val ICE_WALL_BREAK_SOUND = 10119
    const val FIRE_ELEMENTAL_SPAWN_SOUND = 10172
    const val ICE_ELEMENTAL_SPAWN_SOUND = 10190
    const val AREA_PULSE_SOUND = 10155
    const val BRANDA_STOMP_SOUND = 10157
    const val BRANDA_STOMP_IMPACT_SOUND = 10158
    const val ELDRIC_STOMP_SOUND = 10114
    const val ELDRIC_STOMP_IMPACT_SOUND = 10127

    val npcIds =
        listOf(
            BRANDA_ACTIVE,
            ELDRIC_ACTIVE,
            BRANDA_CORPSE,
            ELDRIC_CORPSE,
            FIRE_ELEMENTAL,
            ICE_ELEMENTAL,
            FIRE_WALL_HITBOX,
            ICE_WALL_HITBOX,
            AREA_TIMER,
        )

    val locIds =
        listOf(
            ENTRANCE,
            EXIT,
            BRANDA_FLOOR_WEST,
            BRANDA_FLOOR_EAST,
            BRANDA_DEATH_FLOOR_WEST,
            BRANDA_DEATH_FLOOR_EAST,
            ELDRIC_FLOOR_EAST,
            ELDRIC_FLOOR_WEST,
            ELDRIC_DEATH_FLOOR_EAST,
            ELDRIC_DEATH_FLOOR_WEST,
            FIRE_WALL,
            ICE_WALL,
            SAFE_TILE,
            SCOREBOARD,
        )

    val sequenceIds =
        listOf(
            BRANDA_WALK,
            ELDRIC_WALK,
            BRANDA_STOMP,
            ELDRIC_STOMP,
            BRANDA_WALL_CAST,
            ELDRIC_WALL_CAST,
            BRANDA_SUMMON,
            ELDRIC_SUMMON,
            BRANDA_AREA_CHARGE,
            ELDRIC_AREA_CHARGE,
            BRANDA_AREA_EXECUTE,
            ELDRIC_AREA_EXECUTE,
            BRANDA_TELEPORT,
            ELDRIC_TELEPORT,
            BRANDA_ARENA_SPAWN,
            ELDRIC_ARENA_SPAWN,
            BRANDA_DEATH,
            ELDRIC_DEATH,
            FIRE_ELEMENTAL_SPAWN,
            FIRE_ELEMENTAL_DEATH,
            FIRE_ELEMENTAL_EXPLODE,
            ICE_ELEMENTAL_SPAWN,
            ICE_ELEMENTAL_DEATH,
            ICE_ELEMENTAL_EXPLODE,
        )

    val spotanimIds =
        listOf(
            FIRE_AREA_PROJECTILE,
            ICE_AREA_PROJECTILE,
            STOMP_TILE_GFX,
            WALL_CAST_GFX,
            ELEMENTAL_SUMMON_GFX,
            AREA_CHARGE_GFX,
            AREA_EXECUTE_GFX,
            TELEPORT_GFX,
            BRANDA_SPAWN_GFX,
            FIRE_WALL_START_GFX,
            FIRE_WALL_ACTIVE_GFX,
            FIRE_WALL_END_GFX,
            ICE_WALL_START_GFX,
            ICE_WALL_ACTIVE_GFX,
            ICE_WALL_END_GFX,
            BRANDA_DEATH_GFX,
            FIRE_ELEMENTAL_SPAWN_GFX,
            FIRE_ELEMENTAL_DEATH_GFX,
            ICE_ELEMENTAL_SPAWN_GFX,
            ICE_ELEMENTAL_DEATH_GFX,
            ICE_ELEMENTAL_EXPLOSION_GFX,
            FIRE_ELEMENTAL_EXPLOSION_GFX,
        )
}
