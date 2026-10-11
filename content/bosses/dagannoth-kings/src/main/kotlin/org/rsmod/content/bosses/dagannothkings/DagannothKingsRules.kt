package org.rsmod.content.bosses.dagannothkings

import dev.openrune.types.ProjAnimType
import org.rsmod.map.CoordGrid

internal enum class DagannothKingStyle {
    Melee,
    Ranged,
    Magic,
}

internal enum class SpinolypStyle {
    RangedPoison,
    MagicPrayerDrain,
    SuspiciousWaterMelee,
}

internal data class DagannothKingsMovementProfile(
    val wanderRange: Int,
    val attackRange: Int,
    val huntRange: Int,
    val maxRange: Int,
    val huntMode: Int,
)

internal object DagannothKingsRules {
    const val HITPOINTS = 255
    const val ATTACK_RATE_TICKS = 4
    const val SPINOLYP_ATTACK_RATE_TICKS = 4
    const val SUSPICIOUS_WATER_ATTACK_RATE_TICKS = 2
    const val RESPAWN_TICKS = 150
    const val PRIME_MAX_HIT = 50
    const val SUPREME_MAX_HIT = 30
    const val REX_MAX_HIT = 26
    const val SPINOLYP_MAX_HIT = 10
    const val SPINOLYP_POISON_DAMAGE = 6

    // RSProx capture 3433: kings remain within five tiles of their map spawn. Spinolyps
    // traverse twenty-tile perimeter segments and launch spines from as far as fifteen tiles.
    const val KING_WANDER_RANGE = 5
    const val KING_ATTACK_RANGE = 7
    const val KING_HUNT_RANGE = 8
    const val SPINOLYP_WANDER_RANGE = 20
    const val SPINOLYP_ATTACK_RANGE = 15
    const val SPINOLYP_HUNT_RANGE = 15
    const val AGGRESSIVE_MELEE_HUNT_MODE = 6
    const val AGGRESSIVE_RANGED_HUNT_MODE = 8

    // RSProx capture 3433 records a 50-tick active cycle. Submerged cycles vary in ten-tick
    // increments, so suspicious water performs a one-in-five resurfacing roll at each check.
    // Initial timers are coordinate-staggered so map-spawned Spinolyps do not surface together.
    const val SPINOLYP_ACTIVE_TICKS = 50
    const val SPINOLYP_RESURFACE_CHECK_TICKS = 10
    const val SPINOLYP_RESURFACE_ROLL_BOUND = 5

    fun initialActiveTicks(x: Int, z: Int): Int =
        1 + Math.floorMod((x * 31) xor (z * 17), SPINOLYP_ACTIVE_TICKS)

    fun initialResurfaceCheckTicks(x: Int, z: Int): Int {
        val slots = SPINOLYP_RESURFACE_CHECK_TICKS / SUSPICIOUS_WATER_ATTACK_RATE_TICKS
        return (1 + Math.floorMod((x * 31) xor (z * 17), slots)) *
            SUSPICIOUS_WATER_ATTACK_RATE_TICKS
    }

    fun shouldResurface(roll: Int): Boolean =
        Math.floorMod(roll, SPINOLYP_RESURFACE_ROLL_BOUND) == 0

    fun kingStyle(npcInternal: String): DagannothKingStyle =
        when (npcInternal) {
            DagannothKingsAssets.PRIME -> DagannothKingStyle.Magic
            DagannothKingsAssets.SUPREME -> DagannothKingStyle.Ranged
            DagannothKingsAssets.REX -> DagannothKingStyle.Melee
            else -> error("Unsupported Dagannoth King: $npcInternal")
        }

    fun movementProfile(npcInternal: String): DagannothKingsMovementProfile =
        when (npcInternal) {
            DagannothKingsAssets.PRIME,
            DagannothKingsAssets.SUPREME ->
                DagannothKingsMovementProfile(
                    wanderRange = KING_WANDER_RANGE,
                    attackRange = KING_ATTACK_RANGE,
                    huntRange = KING_HUNT_RANGE,
                    maxRange = KING_HUNT_RANGE,
                    huntMode = AGGRESSIVE_RANGED_HUNT_MODE,
                )
            DagannothKingsAssets.REX ->
                DagannothKingsMovementProfile(
                    wanderRange = KING_WANDER_RANGE,
                    attackRange = 1,
                    huntRange = KING_HUNT_RANGE,
                    maxRange = KING_HUNT_RANGE,
                    huntMode = AGGRESSIVE_MELEE_HUNT_MODE,
                )
            in DagannothKingsAssets.activeSpinolyps,
            in DagannothKingsAssets.suspiciousWater ->
                DagannothKingsMovementProfile(
                    wanderRange = SPINOLYP_WANDER_RANGE,
                    attackRange = SPINOLYP_ATTACK_RANGE,
                    huntRange = SPINOLYP_HUNT_RANGE,
                    maxRange = SPINOLYP_HUNT_RANGE,
                    huntMode = AGGRESSIVE_RANGED_HUNT_MODE,
                )
            else -> error("Unsupported Dagannoth Kings NPC: $npcInternal")
        }

    fun spinolypStyle(roll: Int, suspiciousWaterAdjacent: Boolean = false): SpinolypStyle =
        if (suspiciousWaterAdjacent) {
            SpinolypStyle.SuspiciousWaterMelee
        } else if ((roll and 1) == 0) {
            SpinolypStyle.RangedPoison
        } else {
            SpinolypStyle.MagicPrayerDrain
        }

    /** Spectral randomly rounds the one-point drain up or down, averaging a 50% reduction. */
    fun prayerDrain(hasSpectral: Boolean, spectralRound: Int = 0): Int =
        if (!hasSpectral || (spectralRound and 1) != 0) 1 else 0

    fun isPrimeSplash(centerX: Int, centerZ: Int, tileX: Int, tileZ: Int): Boolean =
        kotlin.math.abs(centerX - tileX) <= 1 && kotlin.math.abs(centerZ - tileZ) <= 1

    fun isSlayerChamber(x: Int, z: Int): Boolean =
        x in SLAYER_CHAMBER_MIN_X..SLAYER_CHAMBER_MAX_X &&
            z in SLAYER_CHAMBER_MIN_Z..SLAYER_CHAMBER_MAX_Z

    private const val SLAYER_CHAMBER_MIN_X = 2880
    private const val SLAYER_CHAMBER_MAX_X = 2943
    private const val SLAYER_CHAMBER_MIN_Z = 4352
    private const val SLAYER_CHAMBER_MAX_Z = 4415
}

internal enum class DagannothKingsRoom {
    Standard,
    Slayer,
}

/** Revision-240 map identities and public travel routes for the two Kings chambers. */
internal object DagannothKingsTravelRules {
    const val DOWN_PARENT_ID = 3831
    const val DOWN_NORMAL_ID = 40417
    const val DOWN_PRIVATE_ID = 40418
    const val UP_ID = 10229

    const val DOWN_PARENT = "loc.dagexp_bossroomladder_down"
    const val DOWN_NORMAL = "loc.dagexp_bossroomladder_down_normal"
    const val DOWN_PRIVATE = "loc.dagexp_bossroomladder_down_private"
    const val UP = "loc.dagexp_bossroomladder_up"

    val EXTERNAL_LADDER = CoordGrid(1911, 4367, 0)
    val EXTERNAL_ARRIVAL = CoordGrid(1912, 4367, 0)
    val STANDARD_LADDER = CoordGrid(2899, 4449, 0)
    val STANDARD_ENTRY = CoordGrid(2900, 4449, 0)
    val SLAYER_LADDER = CoordGrid(2899, 4385, 0)
    val SLAYER_ENTRY = CoordGrid(2900, 4385, 0)

    val downVariants = listOf(DOWN_PARENT, DOWN_NORMAL, DOWN_PRIVATE)

    fun entry(room: DagannothKingsRoom): CoordGrid =
        when (room) {
            DagannothKingsRoom.Standard -> STANDARD_ENTRY
            DagannothKingsRoom.Slayer -> SLAYER_ENTRY
        }

    fun contains(room: DagannothKingsRoom, tile: CoordGrid): Boolean {
        if (tile.level != 0 || tile.x !in ROOM_MIN_X..ROOM_MAX_X) return false
        return when (room) {
            DagannothKingsRoom.Standard -> tile.z in STANDARD_MIN_Z..STANDARD_MAX_Z
            DagannothKingsRoom.Slayer -> tile.z in SLAYER_MIN_Z..SLAYER_MAX_Z
        }
    }

    private const val ROOM_MIN_X = 2880
    private const val ROOM_MAX_X = 2943
    private const val STANDARD_MIN_Z = 4416
    private const val STANDARD_MAX_Z = 4479
    private const val SLAYER_MIN_Z = 4352
    private const val SLAYER_MAX_Z = 4415
}

internal object DagannothKingsAssets {
    const val PRIME_ATTACK_ANIMATION = "seq.dagannoth_meganoth_attack_mage"
    const val SUPREME_ATTACK_ANIMATION = "seq.dagannoth_meganoth_attack_range"
    const val REX_ATTACK_ANIMATION = "seq.dagannoth_meganoth_attack_melee"
    const val SPINOLYP_ATTACK_ANIMATION = "seq.dagannoth_water_creature_attack"
    const val KING_DEATH_ANIMATION = "seq.dagannoth_meganoth_death"
    const val SPINOLYP_DEATH_ANIMATION = "seq.dagannoth_water_creature_death"
    const val PRIME_PROJECTILE = "spotanim.waterwave_travel"
    const val SUPREME_PROJECTILE = "spotanim.dagannoth_spine_spotanim_travel"
    const val SPINOLYP_PROJECTILE = "spotanim.horror_spines_travel"
    const val SPINOLYP_SURFACE_SPOTANIM = "spotanim.watersplash"
    const val SUPREME = "npc.dagcave_ranged_boss"
    const val PRIME = "npc.dagcave_magic_boss"
    const val REX = "npc.dagcave_melee_boss"
    const val SPINOLYP_SMALL = "npc.dagannoth_water_creature_1x1"
    const val SPINOLYP_MEDIUM = "npc.dagannoth_water_creature_1x12"
    const val SPINOLYP_LARGE = "npc.dagannoth_water_creature_1x13"
    const val SUSPICIOUS_WATER_SMALL = "npc.dagannoth_water_creature_1x1_invisible"
    const val SUSPICIOUS_WATER_MEDIUM = "npc.dagannoth_water_creature_1x1_invisible2"
    const val SUSPICIOUS_WATER_LARGE = "npc.dagannoth_water_creature_1x1_invisible3"
    const val SPECTRAL_SHIELD = "obj.spectral"

    val primeProjectileType =
        ProjAnimType(
            startHeight = 220,
            endHeight = 100,
            delay = 10,
            angle = 20,
            lengthAdjustment = 36,
            progress = 50,
            stepMultiplier = 5,
        )

    val supremeProjectileType =
        ProjAnimType(
            startHeight = 210,
            endHeight = 100,
            delay = 10,
            angle = 40,
            lengthAdjustment = 46,
            progress = 50,
            stepMultiplier = 5,
        )

    val spinolypProjectileType =
        ProjAnimType(
            startHeight = 110,
            endHeight = 110,
            delay = 10,
            angle = 25,
            lengthAdjustment = 36,
            progress = 50,
            stepMultiplier = 5,
        )

    val kings = listOf(SUPREME, PRIME, REX)
    val activeSpinolyps = listOf(SPINOLYP_SMALL, SPINOLYP_MEDIUM, SPINOLYP_LARGE)
    val suspiciousWater =
        listOf(SUSPICIOUS_WATER_SMALL, SUSPICIOUS_WATER_MEDIUM, SUSPICIOUS_WATER_LARGE)
    val spinolypPairs = activeSpinolyps.zip(suspiciousWater)
    val allNpcs = kings + activeSpinolyps + suspiciousWater
}
