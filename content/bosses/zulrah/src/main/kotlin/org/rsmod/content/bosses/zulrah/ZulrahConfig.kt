package org.rsmod.content.bosses.zulrah

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal const val ZULRAH_INSTANCE_KEY = "zulrah"
internal const val ZULRAH_MAX_HITPOINTS = 500
internal const val ZULRAH_ATTACK_RATE = 3
internal const val ZULRAH_PHASE_END_TICKS = 4
internal const val ZULRAH_MAX_HIT = 41
internal const val ZULRAH_NPC_LIFETIME = Int.MAX_VALUE
internal const val ZULRAH_RESPAWN_TICKS = 17
internal const val ZULRAH_ENTRY_CUTSCENE_TICKS = 5
internal const val ZULRAH_DROP_DURATION = 18_000
internal const val ZULRAH_DEATH_FEE = 100_000
internal const val ZULRAH_FREE_RECLAIM_KILLS = 50
internal const val ZULRAH_CLOUD_LIFETIME = 30
internal const val ZULRAH_SNAKELING_LIFETIME = 67
internal const val ZULRAH_SNAKELING_ATTACK_RATE = 4
internal const val ZULRAH_SNAKELING_MAX_HIT = 15
internal const val ZULRAH_SNAKELING_SPAWN_LOCK_TICKS = 3
internal const val CLIENT_CYCLES_PER_GAME_TICK = 30
internal const val ZULRAH_RANGED_SEND_SOUND = "synth.waterwave_cast_and_fire"
internal const val ZULRAH_RANGED_IMPACT_SOUND = "synth.zulrah_ranged_impact"
internal const val ZULRAH_MAGIC_SEND_SOUND = "synth.firewave_cast_and_fire"
internal const val ZULRAH_MAGIC_IMPACT_SOUND = "synth.firewave_hit"
internal const val ZULRAH_CLOUD_SEND_SOUND = "synth.zulrah_cloud_send"
internal const val ZULRAH_SNAKELING_SEND_SOUND = "synth.zulrah_snakeling_send"
internal const val ZULRAH_HAZARD_SPAWN_SOUND = "synth.zulrah_hazard_spawn"
internal const val ZULRAH_CLOUD_DISSIPATE_SOUND_A = "synth.zulrah_cloud_dissipate_a"
internal const val ZULRAH_CLOUD_DISSIPATE_SOUND_B = "synth.zulrah_cloud_dissipate_b"
internal const val ZULRAH_SNAKELING_IMPACT_SOUND = "synth.zulrah_snakeling_impact"
internal const val ZULRAH_INITIAL_EMERGE_TICKS = 11
internal const val ZULRAH_EMERGE_TICKS = 3
internal const val ZULRAH_DIVE_TICKS = 3
internal const val ZULRAH_TAIL_WINDUP_TICKS = 4
internal const val ZULRAH_TAIL_RECOVERY_TICKS = 3

internal val ZULRAH_TAIL_SAFESPOTS =
    setOf(
        ShrineTile(2263, 3072),
        ShrineTile(2264, 3072),
        ShrineTile(2272, 3072),
        ShrineTile(2273, 3072),
    )

/** Enable only after a persisted Regicide quest state is available in OpenRune. */
internal const val ZULRAH_REQUIRE_REGICIDE = false

internal val ZULRAH_DOCK = CoordGrid(2204, 3056, 0)

internal const val ZULRAH_TEMPLATE_SOURCE_ZONE_X = 282
internal const val ZULRAH_TEMPLATE_SOURCE_ZONE_Z = 382
internal const val ZULRAH_TEMPLATE_ZONE_WIDTH = 3
internal const val ZULRAH_TEMPLATE_ZONE_LENGTH = 3
private const val ZULRAH_TEMPLATE_DESTINATION_ZONE_X = 6
private const val ZULRAH_TEMPLATE_DESTINATION_ZONE_Z = 6
private const val ZULRAH_SWAMP_SOURCE_ZONE_X = 284
private const val ZULRAH_SWAMP_SOURCE_ZONE_Z = 382
private const val ZULRAH_SWAMP_DESTINATION_ZONE_X = 3
private const val ZULRAH_SWAMP_DESTINATION_ZONE_Z = 4

/**
 * The 9x8 rotated swamp surround sent by OSRS for the Zulrah instance.
 *
 * Values are stored in x-major order. The central 3x3 is deliberately replaced below with the
 * existing playable shrine copy so all server-side normal-coordinate lookups remain stable.
 */
internal val ZULRAH_SWAMP_ROTATIONS =
    listOf(
        listOf(2, 2, 2, 1, 2, 1, 0, 0),
        listOf(1, 3, 2, 2, 3, 0, 2, 2),
        listOf(1, 1, 1, 1, 0, 2, 0, 2),
        listOf(3, 1, 2, 0, 0, 2, 1, 3),
        listOf(3, 1, 0, 0, 0, 0, 1, 3),
        listOf(2, 3, 1, 0, 0, 1, 3, 2),
        listOf(1, 1, 1, 1, 0, 2, 0, 2),
        listOf(1, 3, 2, 2, 3, 0, 2, 2),
        listOf(2, 2, 2, 1, 2, 1, 0, 0),
    )

internal val ZULRAH_TEMPLATE =
    RegionTemplate.create {
        for ((localX, column) in ZULRAH_SWAMP_ROTATIONS.withIndex()) {
            for ((localZ, rotation) in column.withIndex()) {
                val destinationX = ZULRAH_SWAMP_DESTINATION_ZONE_X + localX
                val destinationZ = ZULRAH_SWAMP_DESTINATION_ZONE_Z + localZ
                val isPlayableCore =
                    destinationX in
                        ZULRAH_TEMPLATE_DESTINATION_ZONE_X until
                            ZULRAH_TEMPLATE_DESTINATION_ZONE_X + ZULRAH_TEMPLATE_ZONE_WIDTH &&
                        destinationZ in
                            ZULRAH_TEMPLATE_DESTINATION_ZONE_Z until
                                ZULRAH_TEMPLATE_DESTINATION_ZONE_Z + ZULRAH_TEMPLATE_ZONE_LENGTH
                if (!isPlayableCore) {
                    for (level in 0..1) {
                        copy(ZULRAH_SWAMP_SOURCE_ZONE_X, ZULRAH_SWAMP_SOURCE_ZONE_Z, level) {
                            zoneWidth = 1
                            zoneLength = 1
                            regionZoneX = destinationX
                            regionZoneZ = destinationZ
                            this.rotation = rotation
                        }
                    }
                }
            }
        }
        copy(ZULRAH_TEMPLATE_SOURCE_ZONE_X, ZULRAH_TEMPLATE_SOURCE_ZONE_Z, 0) {
            zoneWidth = ZULRAH_TEMPLATE_ZONE_WIDTH
            zoneLength = ZULRAH_TEMPLATE_ZONE_LENGTH
            regionZoneX = ZULRAH_TEMPLATE_DESTINATION_ZONE_X
            regionZoneZ = ZULRAH_TEMPLATE_DESTINATION_ZONE_Z
        }
        // OSRS supplies the real fight chunks on level one too. This prevents the repeated swamp
        // backdrop from contributing stray upper-level terrain around the island.
        copy(ZULRAH_TEMPLATE_SOURCE_ZONE_X, ZULRAH_TEMPLATE_SOURCE_ZONE_Z + 1, 1) {
            zoneWidth = ZULRAH_TEMPLATE_ZONE_WIDTH
            zoneLength = ZULRAH_TEMPLATE_ZONE_LENGTH - 1
            regionZoneX = ZULRAH_TEMPLATE_DESTINATION_ZONE_X
            regionZoneZ = ZULRAH_TEMPLATE_DESTINATION_ZONE_Z + 1
        }
    }

internal val ZULRAH_AREA =
    InstanceArea.template(
        template = ZULRAH_TEMPLATE,
        enterCoord = RegionLocal(0, 35, 47, 28, 60),
        exitCoord = ZULRAH_DOCK,
        npcSpawns = listOf(InstanceNpc(ZulrahAssets.GREEN, CoordGrid(2266, 3073))),
    )

internal fun zulrahSpec(activeType: dev.openrune.types.NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Zulrah",
            recommendedCombat = 90..126,
            teamSize = 1,
            description = "Private Zulrah shrine encounter",
        )
        .withArea(ZULRAH_AREA, settingsRowId = 0)

internal object ZulrahAssets {
    const val GREEN = "npc.snakeboss_boss_ranged"
    const val RED = "npc.snakeboss_boss_melee"
    const val BLUE = "npc.snakeboss_boss_magic"
    const val SNAKELING_MELEE = "npc.snakeboss_minion_melee"
    const val SNAKELING_MAGIC = "npc.snakeboss_minion_magic"
    const val PRIESTESS = "npc.snakeboss_priest"
    const val HIGH_PRIESTESS = "npc.snakeboss_highpriest"
    const val PRIESTESS_ONE_OP = "npc.snakeboss_priest_1op"
    const val PRIESTESS_TWO_OPS = "npc.snakeboss_priest_2ops"

    const val ATTACK = "seq.snakeboss_attack_acidx1"
    const val SUMMON = ATTACK
    const val SPAWN = "seq.snakeboss_spawn"
    const val EMERGE = "seq.snakeboss_emergefast"
    const val DIVE = "seq.snakeboss_sinkfast"
    const val DEATH = "seq.snakeboss_death"
    const val DEFEND = "seq.snakeboss_defend"
    const val TAIL_LEFT = "seq.snakeboss_attack_tail_left"
    const val TAIL_RIGHT = "seq.snakeboss_attack_tail_right"
    const val SNAKELING_ATTACK = "seq.snakeboss_pet_attack"
    const val SNAKELING_DEATH = "seq.snakeboss_pet_death"

    const val RANGED_PROJECTILE = "spotanim.snakeboss_orb"
    const val MAGIC_PROJECTILE = "spotanim.snakeboss_fireball"
    const val CLOUD_PROJECTILE = "spotanim.snakeboss_double_orb"
    const val SNAKELING_PROJECTILE = "spotanim.snakeboss_egg"
    const val SNAKELING_MAGIC_PROJECTILE = "spotanim.snakeboss_minion_spell"

    const val ATTACK_PROJECTILE_CONTROLLER = "projanim.zulrah_attack"
    const val CLOUD_PRIMARY_PROJECTILE_CONTROLLER = "projanim.zulrah_cloud_primary"
    const val CLOUD_SECONDARY_PROJECTILE_CONTROLLER = "projanim.zulrah_cloud_secondary"
    const val SNAKELING_EGG_PROJECTILE_CONTROLLER = "projanim.zulrah_snakeling_egg"
    const val SNAKELING_MAGIC_PROJECTILE_CONTROLLER = "projanim.magic_spell"
    const val CLOUD_LOC = "loc.snakeboss_poisoncloud"
    const val EXIT = "loc.snakeboss_exit"
    const val BOAT = "loc.snakeboss_boat"
    const val BOAT_ONE_OP = "loc.snakeboss_boat_1op"
    const val BOAT_TWO_OPS = "loc.snakeboss_boat_2ops"

    val forms = listOf(GREEN, RED, BLUE)
    val snakelings = listOf(SNAKELING_MELEE, SNAKELING_MAGIC)
    val priestesses = listOf(PRIESTESS, HIGH_PRIESTESS, PRIESTESS_ONE_OP, PRIESTESS_TWO_OPS)
    val sequences =
        listOf(
            ATTACK,
            SUMMON,
            SPAWN,
            EMERGE,
            DIVE,
            DEATH,
            DEFEND,
            TAIL_LEFT,
            TAIL_RIGHT,
            SNAKELING_ATTACK,
            SNAKELING_DEATH,
        )
    val spotanims =
        listOf(
            RANGED_PROJECTILE,
            MAGIC_PROJECTILE,
            CLOUD_PROJECTILE,
            SNAKELING_PROJECTILE,
            SNAKELING_MAGIC_PROJECTILE,
        )
    val projectileControllers =
        listOf(
            ATTACK_PROJECTILE_CONTROLLER,
            CLOUD_PRIMARY_PROJECTILE_CONTROLLER,
            CLOUD_SECONDARY_PROJECTILE_CONTROLLER,
            SNAKELING_EGG_PROJECTILE_CONTROLLER,
            SNAKELING_MAGIC_PROJECTILE_CONTROLLER,
        )
    val sounds =
        listOf(
            ZULRAH_RANGED_SEND_SOUND,
            ZULRAH_RANGED_IMPACT_SOUND,
            ZULRAH_MAGIC_SEND_SOUND,
            ZULRAH_MAGIC_IMPACT_SOUND,
            ZULRAH_CLOUD_SEND_SOUND,
            ZULRAH_SNAKELING_SEND_SOUND,
            ZULRAH_HAZARD_SPAWN_SOUND,
            ZULRAH_CLOUD_DISSIPATE_SOUND_A,
            ZULRAH_CLOUD_DISSIPATE_SOUND_B,
            ZULRAH_SNAKELING_IMPACT_SOUND,
        )
    val boats = listOf(BOAT, BOAT_ONE_OP, BOAT_TWO_OPS)
}

internal val ZULRAH_PERSONAL_BEST_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "zulrah_personal_best_ticks")
internal val ZULRAH_STORAGE_REMINDER_SHOWN: AttributeKey<Boolean> = AttributeKey()
