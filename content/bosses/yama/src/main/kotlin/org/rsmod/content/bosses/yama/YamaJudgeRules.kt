package org.rsmod.content.bosses.yama

import kotlin.math.max

internal data class YamaJudgeHitResolution(
    val damage: Int,
    val accepted: Boolean,
    val nextBlockedStyles: Set<YamaAttackStyle>,
)

/** Pure Judge rules shared by the combat hook and deterministic regression tests. */
internal object YamaJudgeRules {
    const val MELEE_AP_RANGE = 1
    const val JUDGE_SIZE = 3
    private const val MIRRORED_LOCAL_X_SUM = 58

    fun initialBlockedStyles(): Set<YamaAttackStyle> =
        setOf(YamaAttackStyle.MELEE, YamaAttackStyle.RANGED)

    fun resolvePlayerHit(
        style: YamaAttackStyle,
        rolledDamage: Int,
        currentMaxHit: Int,
        blockedStyles: Set<YamaAttackStyle>,
        alreadyAcceptedThisTick: Boolean,
        sharedHitpoints: Int,
    ): YamaJudgeHitResolution {
        val blocked = style == YamaAttackStyle.RANGED || style in blockedStyles
        if (blocked || alreadyAcceptedThisTick || sharedHitpoints <= 0) {
            return YamaJudgeHitResolution(
                damage = 0,
                accepted = false,
                nextBlockedStyles = blockedStyles.toSet(),
            )
        }
        val damage =
            YamaRules.judgeSuccessfulDamage(rolledDamage, currentMaxHit)
                .coerceAtMost(sharedHitpoints)
        return YamaJudgeHitResolution(
            damage = damage,
            accepted = damage > 0,
            nextBlockedStyles = setOf(YamaAttackStyle.RANGED, style),
        )
    }

    fun remainingHitpoints(current: Int, finalDamage: Int): Int =
        (current - finalDamage.coerceAtLeast(0)).coerceAtLeast(0)

    fun mirrorLocalX(westLocalX: Int): Int = MIRRORED_LOCAL_X_SUM - westLocalX

    fun mirrorSouthWestX(westSouthWestX: Int): Int = mirrorLocalX(westSouthWestX + JUDGE_SIZE - 1)

    fun occupiesTile(southWestX: Int, southWestZ: Int, size: Int, tileX: Int, tileZ: Int): Boolean {
        val occupiedSize = size.coerceAtLeast(1)
        return tileX in southWestX until southWestX + occupiedSize &&
            tileZ in southWestZ until southWestZ + occupiedSize
    }

    fun distanceToOccupiedRectangle(
        southWestX: Int,
        southWestZ: Int,
        size: Int,
        tileX: Int,
        tileZ: Int,
    ): Int {
        val occupiedSize = size.coerceAtLeast(1)
        val eastX = southWestX + occupiedSize - 1
        val northZ = southWestZ + occupiedSize - 1
        val dx =
            when {
                tileX < southWestX -> southWestX - tileX
                tileX > eastX -> tileX - eastX
                else -> 0
            }
        val dz =
            when {
                tileZ < southWestZ -> southWestZ - tileZ
                tileZ > northZ -> tileZ - northZ
                else -> 0
            }
        return max(dx, dz)
    }
}
