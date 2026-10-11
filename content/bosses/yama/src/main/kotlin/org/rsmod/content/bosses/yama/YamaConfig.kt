package org.rsmod.content.bosses.yama

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val YAMA_INSTANCE_KEY = "yama_domain"
internal const val YAMA_REGION = 6045
internal const val YAMA_NPC_LIFETIME = 18_000
internal const val YAMA_DROP_DURATION = 18_000
internal val YAMA_VOICE_SPAWN = CoordGrid(1439, 10079, 0)
internal val YAMA_OUTSIDE = CoordGrid(1439, 10074, 0)
internal val YAMA_RECORDS_IMP_SPAWN = CoordGrid(1438, 10077, 0)
internal val YAMA_RECORDS_IMP_PRESENTATION = CoordGrid(1438, 10075, 0)

internal object YamaAssets {
    const val THRONE_PARENT = 123
    const val THRONE_OCCUPIED = 124
    const val THRONE_EMPTY = 14157
    const val VOICE_PARENT = 7668
    const val VOICE_TALK = 14183
    const val VOICE_TRAVEL = 14184
    const val VOICE_JOIN = 14185
    const val BOSS = 14176
    const val VOID_FLARE = 14179
    const val JUDGE = 14180
    const val IMP = 14181
    const val METEOR = 14182
    const val FIRE_SURGE = 13507

    const val VOID_FLARE_CHARGE_HEADBAR = "headbar.charge_30"

    const val STEPPING_STONE = 56246
    const val FIREWALL = 56264
    const val THRONE_LOC = 56265
    const val GLYPH_SHADOW = 56335
    const val GLYPH_FIRE = 56336
    const val GLYPH_INACTIVE = 56337
    const val GLYPH_SHADOW_DEACTIVATE = 56338
    const val GLYPH_FIRE_DEACTIVATE = 56339

    fun activeGlyph(type: YamaGlyph): Int = if (type == YamaGlyph.FIRE) GLYPH_FIRE else GLYPH_SHADOW

    fun deactivatingGlyph(type: YamaGlyph): Int =
        if (type == YamaGlyph.FIRE) GLYPH_FIRE_DEACTIVATE else GLYPH_SHADOW_DEACTIVATE

    const val ANIM_IDLE = 12140
    const val ANIM_WALK = 12141
    const val ANIM_STANDARD = 12144
    const val ANIM_SPECIAL = 12145
    const val ANIM_MELEE = 12146
    const val ANIM_PORTAL = 12147
    const val ANIM_STOMP = 12148
    const val ANIM_PLAYER_PORTAL = 12149
    const val ANIM_PLAYER_PORTAL_FACE = 12152
    const val ANIM_THRONE_SUMMON = 12154
    const val ANIM_SUMMON = 12156
    const val ANIM_DESPAWN = 12161
    const val ANIM_THRONE_RESPAWN = 12163
    const val ANIM_VOID_FLARE_IDLE = 12133
    const val ANIM_VOID_FLARE_DEATH = 12134
    const val ANIM_VOID_FLARE_SPAWN = 12135
    const val ANIM_VOID_FLARE_EXPLODE = 12136
    const val ANIM_JUDGE_DEATH = 9042
    const val ANIM_SHORT_JUMP = 12196

    const val GFX_SHADOW_CAST = 3243
    const val GFX_SHADOW_IMPACT = 3244
    const val GFX_FIRE_CAST = 3246
    const val GFX_FIRE_IMPACT = 3247
    const val GFX_FLARE_EXPLODE = 3238
    const val GFX_FLARE_PLAYER = 3242
    const val GFX_FIREBALL_CAST = 3253
    const val GFX_FIREBALL_PROJECTILE = 3254
    const val GFX_FIREBALL_IMPACT = 3255
    const val GFX_JUDGE_FIREBALL_SHADOW = 1446
    const val GFX_JUDGE_FIREBALL_PROJECTILE = 2855
    const val GFX_JUDGE_FIREBALL_IMPACT = 2856
    const val GFX_SHADOW_SPIKE_CAST = 3256
    const val GFX_SHADOW_SPIKE_PROJECTILE = 3257
    const val GFX_SHADOW_SPIKE_IMPACT = 3258
    const val GFX_SHADOW_ROOT_CAST = 3259
    const val GFX_SHADOW_ROOT_PROJECTILE = 3260
    const val GFX_SHADOW_ROOT_IMPACT = 3261
    const val GFX_SHADOW_CRASH_IMPACT = 3252
    const val GFX_FALLING_ROCK_PLAYER = 3262
    const val GFX_SHADOW_WALL_MIDDLE = 3265
    const val GFX_SHADOW_WALL_END = 3266
    const val GFX_FIRE_WALL = 3267
    const val GFX_METEOR_CAST = 3270
    const val GFX_METEOR_PROJECTILE = 3272
    const val GFX_PORTAL = 3274
    const val GFX_PORTAL_FALL = 3275
    const val GFX_PORTAL_SHADOW = 3276
    const val GFX_THRONE_SUMMON = 3278
    const val GFX_SUMMON = 3279
    const val GFX_FIRE_IMMUNITY = 3280
    const val GFX_SHADOW_IMMUNITY = 3281
    const val GFX_FIRE_SURGE_IMPACT = 1466
    const val GFX_DESPAWN = 3284
    const val GFX_THRONE_RESPAWN = 3286

    const val VARBIT_THRONE_STATE = 16286
    const val VARBIT_VOICE_PROGRESS = 16284
    const val VARBIT_TRANSITION_DAMAGE = 16549
    const val VARBIT_TRANSITION_PHASE = 16550
    const val THRONE_STATE_OCCUPIED = 0
    const val THRONE_STATE_EMPTY = 1

    const val DOSSIER = 30803
    const val CERT_PINEAPPLE_PIZZA = 2302
    const val OATHPLATE_HELM = 30750
    const val OATHPLATE_CHEST = 30753
    const val OATHPLATE_LEGS = 30756
    const val SOULFLAME_HORN = 30759
    const val OATHPLATE_SHARDS = 30765
    const val SIGIL_LEFT = 30783
    const val SIGIL_RIGHT = 30785
    const val SIGIL_MIDDLE = 30787
    const val SIGIL_TOP = 30789
    const val SIGIL_BOTTOM = 30791
    const val YAMI = 30888
    val npcIds =
        listOf(
            THRONE_PARENT,
            THRONE_OCCUPIED,
            THRONE_EMPTY,
            VOICE_PARENT,
            VOICE_TALK,
            VOICE_TRAVEL,
            VOICE_JOIN,
            BOSS,
            VOID_FLARE,
            JUDGE,
            IMP,
            METEOR,
            FIRE_SURGE,
        )
    val locIds =
        listOf(
            STEPPING_STONE,
            FIREWALL,
            THRONE_LOC,
            GLYPH_FIRE,
            GLYPH_SHADOW,
            GLYPH_INACTIVE,
            GLYPH_FIRE_DEACTIVATE,
            GLYPH_SHADOW_DEACTIVATE,
        )
    val sequences =
        listOf(
            ANIM_IDLE,
            ANIM_WALK,
            ANIM_STANDARD,
            ANIM_SPECIAL,
            ANIM_MELEE,
            ANIM_PORTAL,
            ANIM_STOMP,
            ANIM_PLAYER_PORTAL,
            ANIM_PLAYER_PORTAL_FACE,
            ANIM_THRONE_SUMMON,
            ANIM_SUMMON,
            ANIM_DESPAWN,
            ANIM_THRONE_RESPAWN,
            ANIM_VOID_FLARE_IDLE,
            ANIM_VOID_FLARE_DEATH,
            ANIM_VOID_FLARE_SPAWN,
            ANIM_VOID_FLARE_EXPLODE,
            ANIM_JUDGE_DEATH,
            ANIM_SHORT_JUMP,
        )
    val spotanims =
        listOf(
            GFX_SHADOW_CAST,
            GFX_SHADOW_IMPACT,
            GFX_FIRE_CAST,
            GFX_FIRE_IMPACT,
            GFX_FLARE_EXPLODE,
            GFX_FLARE_PLAYER,
            GFX_FIREBALL_CAST,
            GFX_FIREBALL_PROJECTILE,
            GFX_FIREBALL_IMPACT,
            GFX_JUDGE_FIREBALL_SHADOW,
            GFX_JUDGE_FIREBALL_PROJECTILE,
            GFX_JUDGE_FIREBALL_IMPACT,
            GFX_SHADOW_SPIKE_CAST,
            GFX_SHADOW_SPIKE_PROJECTILE,
            GFX_SHADOW_SPIKE_IMPACT,
            GFX_SHADOW_ROOT_CAST,
            GFX_SHADOW_ROOT_PROJECTILE,
            GFX_SHADOW_ROOT_IMPACT,
            GFX_SHADOW_CRASH_IMPACT,
            GFX_FALLING_ROCK_PLAYER,
            GFX_SHADOW_WALL_MIDDLE,
            GFX_SHADOW_WALL_END,
            GFX_FIRE_WALL,
            GFX_METEOR_CAST,
            GFX_METEOR_PROJECTILE,
            GFX_PORTAL,
            GFX_PORTAL_FALL,
            GFX_PORTAL_SHADOW,
            GFX_THRONE_SUMMON,
            GFX_SUMMON,
            GFX_FIRE_IMMUNITY,
            GFX_SHADOW_IMMUNITY,
            GFX_FIRE_SURGE_IMPACT,
            GFX_DESPAWN,
            GFX_THRONE_RESPAWN,
        )
}

internal enum class YamaState {
    LOBBY,
    SUMMONING,
    PHASE_ONE,
    JUDGE_ONE,
    PHASE_TWO,
    JUDGE_TWO,
    PHASE_THREE,
    DYING,
    DEFEATED,
    INVALIDATED,
}

internal enum class YamaAttackStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal enum class YamaSpecial {
    GLYPH_ATTACK,
    SHADOW_WAVES,
    FIRE_STREAKS,
    VOID_FLARES,
    METEOR_STRIKE,
    SHADOW_STOMP,
    SHADOW_CRASH,
}

internal enum class YamaGlyph {
    FIRE,
    SHADOW,
}

internal fun yamaArea(): InstanceArea =
    InstanceArea.copyRegions(
        regionIds = listOf(YAMA_REGION),
        enterCoord = RegionLocal(0, 23, 157, 31, 2),
        exitCoord = YAMA_OUTSIDE,
    )

internal fun yamaSpec() =
    InstanceSettings(
            maxPlayers = 2,
            reclaimTicks = 2_000,
            graceTicks = 10,
            destroyWhenEmpty = true,
            bossNpc = emptyList(),
            bossName = "Yama",
            recommendedCombat = 110..126,
            teamSize = 2,
            description = "Private Yama Domain",
        )
        .withArea(yamaArea(), settingsRowId = 0)

internal val YAMA_KILL_COUNT = AttributeKey<Int>(persistenceKey = "yama_kill_count")
internal val YAMA_BEST_TIME = AttributeKey<Int>(persistenceKey = "yama_best_time_ticks")
internal val YAMA_RECENT_KILL_TIMES =
    AttributeKey<MutableList<Int>>(persistenceKey = "yama_recent_kill_times_ticks_v1")
internal val YAMA_FOUR_KILL_AVERAGE =
    AttributeKey<Int>(persistenceKey = "yama_four_kill_average_ticks_v1")
internal val YAMA_DEATH_STORAGE =
    AttributeKey<MutableList<Int>>(persistenceKey = "yama_death_storage_v1")
internal val YAMA_DEATH_FEE = AttributeKey<Int>(persistenceKey = "yama_death_fee_v1")
internal val YAMA_STORAGE_REMINDER_SHOWN = AttributeKey<Boolean>()
