package org.rsmod.content.bosses.doom

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val DOOM_INSTANCE_KEY = "doom_of_mokhaiotl"
// A finite lifetime is intentional: NpcRepository treats Int.MAX_VALUE as a respawning spawn.
internal const val DOOM_NPC_LIFETIME = 18_000
internal const val DOOM_DROP_DURATION = 18_000
internal val DOOM_LOBBY = CoordGrid(1311, 9553, 0)
internal val DOOM_OUTSIDE = CoordGrid(1311, 9540, 0)

internal object DoomAssets {
    const val LARVA_HEADICON_GROUP = 440
    const val CHARGE_HEADBAR = "headbar.charge_100"
    const val SHIELD_HEADBAR = "headbar.shield_120"
    const val BOSS = 14707
    const val SHIELDED = 14708
    const val BURROWED = 14709
    const val LARVA_NEUTRAL = 14710
    const val LARVA_RANGED = 14711
    const val LARVA_MAGIC = 14712
    const val LARVA_MELEE = 14713
    const val VOLATILE_EARTH = 14714
    const val EARTH_SHIELD = 14715
    const val GIANT_RANGED = 14788
    const val GIANT_MAGIC = 14789
    const val ACID_ONE = 57283
    const val BURROW_HOLE = 57285
    const val UNIQUE_BURROW_HOLE = 50940
    const val ROCK = 57286
    const val SCOREBOARD = 57288
    const val GAP = 57289
    const val GAP_ALT = 57290
    const val LOOT_CHEST = 50938
    const val ANIM_IDLE = 12405
    const val ANIM_STANDARD = 12406
    const val ANIM_ROCK_THROW = 12407
    const val ANIM_BEAM_CHARGE = 12408
    const val ANIM_BEAM_CHARGE_LOOP = 12409
    const val ANIM_BEAM_CANCEL = 12410
    const val ANIM_BEAM_FIRE = 12411
    const val ANIM_SHOCKWAVE_PREPARE = 12412
    const val ANIM_SHOCKWAVE_CHARGE = 12413
    const val ANIM_SHOCKWAVE_SLAM = 12414
    const val ANIM_SHOCKWAVE_RECOVER = 12415
    const val ANIM_TONGUE = 12416
    const val ANIM_BURROW_MOVEMENT = 12417
    const val ANIM_BURROW_EMERGE = 12418
    const val ANIM_BURROW_EXPLOSION = 12419
    const val ANIM_BURROW = 12420
    const val ANIM_BURROW_IDLE = 12421
    const val ANIM_DESPAWN = 12422
    const val ANIM_VOLATILE_SPAWN = 12432
    const val ANIM_VOLATILE_UNUSED = 12433
    const val ANIM_VOLATILE_SELECTED = 12434
    const val ANIM_EARTH_SHIELD_SPAWN = 12436
    const val ANIM_LARVA_SPAWN = 12458
    const val ANIM_LARVA_DEATH = 12459
    const val ANIM_HOLE_SPAWN = 12477
    const val GFX_BURROW_MOVEMENT = 3371
    const val GFX_BURROW_EMERGE = 3372
    const val GFX_BURROW_EXPLOSION = 3373
    const val GFX_BURROW_EXPLOSION_AOE = 3374
    const val GFX_BURROW = 3375
    const val GFX_DESPAWN = 3377
    const val GFX_MELEE_PROJECTILE = 3378
    const val GFX_MAGIC_PROJECTILE = 3379
    const val GFX_RANGED_PROJECTILE = 3380
    const val GFX_RANGED_ROCK = 3384
    const val GFX_MAGIC_ROCK = 3385
    const val GFX_RANGED_ROCK_IMPACT = 3386
    const val GFX_MAGIC_ROCK_IMPACT = 3387
    const val GFX_DEBRIS_SHADOW = 2380
    const val GFX_DEBRIS_START = 3388
    // Every supplied rev-234/235 capture uses the eight debris variants 3388..3395.
    // 3404 is the separate delayed impact animation.
    const val GFX_DEBRIS_END = 3395
    const val GFX_DEBRIS_IMPACT = 3404
    const val SOUND_ROCK_BREAK = 10331
    const val SOUND_DEBRIS_IMPACT = 10297
    const val GFX_SHOCKWAVE_START = 3405
    const val GFX_SHOCKWAVE_MIDDLE = 3406
    const val GFX_SHOCKWAVE_END = 3407
    const val GFX_BEAM_HEAD = 3409
    const val GFX_BEAM_MIDDLE = 3410
    const val GFX_BEAM_END = 3411
    const val GFX_BEAM_CHARGE = 3412
    const val GFX_BEAM_IMPACT = 3413
    const val GFX_BURROW_CHARGE = 3414
    const val GFX_BURROW_TARGET = 3415
    const val GFX_BURROW_TARGET_SPAWN = 3416
    const val GFX_LARVA_SPAWN = 3417
    const val GFX_LARVA_PLAYER_IMPACT = 3426
    const val GFX_ACID_SPLAT_START = 3429
    const val GFX_ACID_SPLAT_END = 3432
    const val GFX_ACID_PROJECTILE = 3445
    val npcIds =
        listOf(
            BOSS,
            SHIELDED,
            BURROWED,
            LARVA_NEUTRAL,
            LARVA_RANGED,
            LARVA_MAGIC,
            LARVA_MELEE,
            VOLATILE_EARTH,
            EARTH_SHIELD,
            GIANT_RANGED,
            GIANT_MAGIC,
        )
    val locIds =
        listOf(
            ACID_ONE,
            BURROW_HOLE,
            UNIQUE_BURROW_HOLE,
            ROCK,
            SCOREBOARD,
            GAP,
            GAP_ALT,
            LOOT_CHEST,
        )
    val sequences =
        listOf(
            ANIM_IDLE,
            ANIM_STANDARD,
            ANIM_ROCK_THROW,
            ANIM_BEAM_CHARGE,
            ANIM_BEAM_CHARGE_LOOP,
            ANIM_BEAM_CANCEL,
            ANIM_BEAM_FIRE,
            ANIM_SHOCKWAVE_PREPARE,
            ANIM_SHOCKWAVE_CHARGE,
            ANIM_SHOCKWAVE_SLAM,
            ANIM_SHOCKWAVE_RECOVER,
            ANIM_TONGUE,
            ANIM_BURROW_MOVEMENT,
            ANIM_BURROW_EMERGE,
            ANIM_BURROW_EXPLOSION,
            ANIM_BURROW,
            ANIM_BURROW_IDLE,
            ANIM_DESPAWN,
            ANIM_VOLATILE_SPAWN,
            ANIM_VOLATILE_UNUSED,
            ANIM_VOLATILE_SELECTED,
            ANIM_EARTH_SHIELD_SPAWN,
            ANIM_LARVA_SPAWN,
            ANIM_LARVA_DEATH,
            ANIM_HOLE_SPAWN,
        )
    val spotanims =
        listOf(
            GFX_MELEE_PROJECTILE,
            GFX_MAGIC_PROJECTILE,
            GFX_RANGED_PROJECTILE,
            GFX_RANGED_ROCK,
            GFX_MAGIC_ROCK,
            GFX_RANGED_ROCK_IMPACT,
            GFX_MAGIC_ROCK_IMPACT,
            GFX_DEBRIS_SHADOW,
            GFX_DEBRIS_START,
            GFX_DEBRIS_END,
            GFX_DEBRIS_IMPACT,
            GFX_BURROW_MOVEMENT,
            GFX_BURROW_EMERGE,
            GFX_BURROW_EXPLOSION,
            GFX_BURROW_EXPLOSION_AOE,
            GFX_BURROW,
            GFX_DESPAWN,
            GFX_SHOCKWAVE_START,
            GFX_SHOCKWAVE_MIDDLE,
            GFX_SHOCKWAVE_END,
            GFX_BEAM_HEAD,
            GFX_BEAM_MIDDLE,
            GFX_BEAM_END,
            GFX_BEAM_CHARGE,
            GFX_BEAM_IMPACT,
            GFX_BURROW_CHARGE,
            GFX_BURROW_TARGET,
            GFX_BURROW_TARGET_SPAWN,
            GFX_LARVA_SPAWN,
            GFX_LARVA_PLAYER_IMPACT,
            GFX_ACID_SPLAT_START,
            GFX_ACID_SPLAT_END,
            GFX_ACID_PROJECTILE,
        )
}

internal enum class DoomState {
    STARTING,
    ACTIVE,
    MELEE_CHARGE,
    SHIELD_TRANSITION,
    SHIELDED,
    SHOCKWAVE,
    BURROWING,
    DYING,
    DEFEATED,
    INVALIDATED,
}

internal enum class DoomAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal enum class DoomPrimaryAttack {
    STANDARD,
    TONGUE,
    ROCKS,
}

internal data class DoomArenaDefinition(val regionId: Int, val regionX: Int, val regionZ: Int)

internal fun arenaForDelve(delve: Int): DoomArenaDefinition =
    when {
        delve <= 1 -> DoomArenaDefinition(5269, 20, 149)
        delve <= 5 -> DoomArenaDefinition(13668, 53, 100)
        else -> DoomArenaDefinition(14180, 55, 100)
    }

internal fun doomEntryLocalZ(delve: Int): Int = if (delve <= 1) 23 else 30

internal fun doomArea(delve: Int): InstanceArea {
    val arena = arenaForDelve(delve)
    return InstanceArea.copyRegions(
        regionIds = listOf(arena.regionId),
        // RSProx: D1 lands at local (31,23); deeper-delves rebuild at local (31,30).
        enterCoord = RegionLocal(0, arena.regionX, arena.regionZ, 31, doomEntryLocalZ(delve)),
        exitCoord = DOOM_LOBBY,
    )
}

internal fun doomSpec(delve: Int, bossType: dev.openrune.types.NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(bossType),
            bossName = "Doom of Mokhaiotl",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private Doom of Mokhaiotl delve $delve",
        )
        .withArea(doomArea(delve), settingsRowId = 0)

internal val DOOM_PENDING_REWARDS =
    AttributeKey<String>(persistenceKey = "doom_of_mokhaiotl_pending_rewards")
internal val DOOM_BEST_DELVE = AttributeKey<Int>(persistenceKey = "doom_of_mokhaiotl_best_delve")
internal val DOOM_PERSONAL_STATS =
    AttributeKey<String>(persistenceKey = "doom_of_mokhaiotl_personal_stats_v1")
internal val DOOM_KILL_COUNT = AttributeKey<Int>(persistenceKey = "doom_of_mokhaiotl_kill_count")
