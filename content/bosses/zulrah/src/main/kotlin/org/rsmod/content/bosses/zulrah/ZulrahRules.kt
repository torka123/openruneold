package org.rsmod.content.bosses.zulrah

import org.rsmod.map.CoordGrid

internal object ZulrahRules {
    const val BLUE_MAGIC_WEIGHT = 2
    const val BLUE_RANGED_WEIGHT = 1

    fun cappedDamage(damage: Int, reroll: (IntRange) -> Int): Int =
        if (damage > 50) reroll(45..50) else damage.coerceAtLeast(0)

    fun blueStyle(roll: Int): ZulrahAttackStyle =
        if (roll.mod(BLUE_MAGIC_WEIGHT + BLUE_RANGED_WEIGHT) < BLUE_MAGIC_WEIGHT)
            ZulrahAttackStyle.MAGIC
        else ZulrahAttackStyle.RANGED

    fun jadStyles(count: Int, startsWith: ZulrahAttackStyle): List<ZulrahAttackStyle> =
        List(count) { index ->
            if (index % 2 == 0) startsWith
            else if (startsWith == ZulrahAttackStyle.MAGIC) ZulrahAttackStyle.RANGED
            else ZulrahAttackStyle.MAGIC
        }

    fun nextRotation(previous: Int, roll: Int): Int {
        require(previous in 1..4)
        return roll.mod(4) + 1
    }

    fun cloudOrigin(center: ShrineTile): ShrineTile = ShrineTile(center.x - 1, center.z - 1)

    fun tailDamage(standingInTargetArea: Boolean, roll: Int): Int =
        if (standingInTargetArea) 20 + roll.mod(11) else 0

    fun inTailTarget(playerX: Int, playerZ: Int, targetX: Int, targetZ: Int): Boolean =
        kotlin.math.max(kotlin.math.abs(playerX - targetX), kotlin.math.abs(playerZ - targetZ)) <= 1

    fun rewardTile(playerTile: CoordGrid, bossTile: CoordGrid): CoordGrid {
        @Suppress("UNUSED_VARIABLE") val ignoredBossTile = bossTile
        return playerTile
    }

    fun isInShrineTemplate(tile: ShrineTile): Boolean {
        val zoneX = tile.x shr 3
        val zoneZ = tile.z shr 3
        return zoneX in
            ZULRAH_TEMPLATE_SOURCE_ZONE_X until
                ZULRAH_TEMPLATE_SOURCE_ZONE_X + ZULRAH_TEMPLATE_ZONE_WIDTH &&
            zoneZ in
                ZULRAH_TEMPLATE_SOURCE_ZONE_Z until
                    ZULRAH_TEMPLATE_SOURCE_ZONE_Z + ZULRAH_TEMPLATE_ZONE_LENGTH
    }

    fun formatTicks(ticks: Int): String {
        val tenths = ticks.coerceAtLeast(0) * 6
        return "%d:%02d.%d".format(tenths / 600, (tenths / 10) % 60, tenths % 10)
    }
}
