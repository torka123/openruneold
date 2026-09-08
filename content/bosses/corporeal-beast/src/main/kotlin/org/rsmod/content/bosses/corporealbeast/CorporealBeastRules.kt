package org.rsmod.content.bosses.corporealbeast

import kotlin.math.max
import org.rsmod.map.CoordGrid

internal enum class CorporealBeastAttack {
    Melee,
    Magic,
    Drain,
    AreaMagic,
}

internal object CorporealBeastRules {
    const val HITPOINTS = 2_000
    const val NORMAL_ATTACK_RATE_TICKS = 4
    const val SHORT_ATTACK_RATE_TICKS = 3
    const val SHORT_ATTACK_ROLL_DENOMINATOR = 8
    const val MELEE_CHANCE_PERCENT = 40

    const val MELEE_MAX_HIT = 33
    const val MAGIC_MAX_HIT = 65
    const val AREA_MAIN_DIRECT_MAX_HIT = 40
    const val AREA_MAIN_ADJACENT_MAX_HIT = 30
    const val AREA_SPLIT_DIRECT_MAX_HIT = 30
    const val AREA_SPLIT_ADJACENT_MAX_HIT = 20

    const val STOMP_INTERVAL_TICKS = 7
    const val STOMP_MIN_HIT = 30
    const val STOMP_MAX_HIT = 51

    const val DARK_CORE_SPAWN_HIT_THRESHOLD = 32
    const val DARK_CORE_SPAWN_ROLL_DENOMINATOR = 8
    const val DARK_CORE_HITPOINTS = 25
    const val DARK_CORE_ATTACK_RATE_TICKS = 2
    const val DARK_CORE_MIN_HIT = 5
    const val DARK_CORE_MAX_HIT = 13

    const val EMPTY_ROOM_HEAL = 25
    const val EMPTY_ROOM_HEAL_INTERVAL_TICKS = 7
    const val STAT_RESTORE_INTERVAL_TICKS = 20
    const val NO_COMBAT_RESET_TICKS = 300
    const val ROOM_RADIUS = 48
    const val AREA_SPLIT_COUNT = 6

    fun attack(inMeleeReach: Boolean, primaryRoll: Int, magicRoll: Int): CorporealBeastAttack {
        if (inMeleeReach && primaryRoll in 0 until MELEE_CHANCE_PERCENT) {
            return CorporealBeastAttack.Melee
        }
        return when (magicRoll.mod(3)) {
            0 -> CorporealBeastAttack.Magic
            1 -> CorporealBeastAttack.Drain
            else -> CorporealBeastAttack.AreaMagic
        }
    }

    fun attackRate(shortRoll: Int): Int =
        if (shortRoll.mod(SHORT_ATTACK_ROLL_DENOMINATOR) == 0) {
            SHORT_ATTACK_RATE_TICKS
        } else {
            NORMAL_ATTACK_RATE_TICKS
        }

    fun distanceToFootprint(npcX: Int, npcZ: Int, npcSize: Int, playerX: Int, playerZ: Int): Int {
        val dx = maxOf(npcX - playerX, playerX - (npcX + npcSize - 1), 0)
        val dz = maxOf(npcZ - playerZ, playerZ - (npcZ + npcSize - 1), 0)
        return max(dx, dz)
    }

    fun isUnderFootprint(npcX: Int, npcZ: Int, npcSize: Int, playerX: Int, playerZ: Int): Boolean =
        playerX in npcX until (npcX + npcSize) && playerZ in npcZ until (npcZ + npcSize)

    fun areaMaximum(direct: Boolean, split: Boolean): Int =
        when {
            split && direct -> AREA_SPLIT_DIRECT_MAX_HIT
            split -> AREA_SPLIT_ADJACENT_MAX_HIT
            direct -> AREA_MAIN_DIRECT_MAX_HIT
            else -> AREA_MAIN_ADJACENT_MAX_HIT
        }

    fun coreSpawnFromHit(damage: Int, roll: Int, coreAlive: Boolean): Boolean =
        !coreAlive &&
            damage >= DARK_CORE_SPAWN_HIT_THRESHOLD &&
            roll.mod(DARK_CORE_SPAWN_ROLL_DENOMINATOR) == 0

    fun coreSpawnFromAttack(hitpoints: Int, roll: Int, coreAlive: Boolean): Boolean =
        !coreAlive && hitpoints < HITPOINTS / 2 && roll.mod(DARK_CORE_SPAWN_ROLL_DENOMINATOR) == 0

    fun roomMatches(spawn: CoordGrid, other: CoordGrid): Boolean =
        spawn.level == other.level && spawn.chebyshevDistance(other) <= ROOM_RADIUS

    fun splitCandidates(center: CoordGrid): List<CoordGrid> =
        listOf(
            center.translate(-4, -2),
            center.translate(-4, 2),
            center.translate(-2, -4),
            center.translate(-2, 4),
            center.translate(2, -4),
            center.translate(2, 4),
            center.translate(4, -2),
            center.translate(4, 2),
            center.translate(-3, 0),
            center.translate(3, 0),
            center.translate(0, -3),
            center.translate(0, 3),
        )

    fun shouldReset(currentTick: Int, lastCombatTick: Int): Boolean =
        currentTick - lastCombatTick >= NO_COMBAT_RESET_TICKS
}
