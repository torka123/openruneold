package org.rsmod.content.bosses.zulrah

internal enum class ZulrahForm {
    SERPENTINE,
    MAGMA,
    TANZANITE,
}

internal enum class ZulrahPosition(val x: Int, val z: Int, val faceX: Int, val faceZ: Int) {
    MIDDLE(2266, 3073, 2268, 3065),
    SOUTH(2266, 3062, 2268, 3068),
    WEST(2257, 3071, 2268, 3073),
    EAST(2276, 3071, 2270, 3074),
}

internal enum class ZulrahAttackStyle {
    MAGIC,
    RANGED,
}

internal data class ShrineTile(val x: Int, val z: Int)

internal sealed interface ZulrahAction {
    data class Delay(val ticks: Int) : ZulrahAction

    data class StandardAttack(val count: Int) : ZulrahAction

    data class JadAttack(val count: Int, val startsWith: ZulrahAttackStyle) : ZulrahAction

    data class TailSwipe(val count: Int) : ZulrahAction

    /** One OSRS barrage: a primary and a secondary venom-cloud projectile. */
    data class VenomCloudVolley(val primary: ShrineTile, val secondary: ShrineTile) : ZulrahAction

    /** One OSRS snakeling orb and therefore one eventual snakeling spawn. */
    data class SnakelingVolley(val target: ShrineTile) : ZulrahAction
}

internal data class ZulrahPhase(
    val number: Int,
    val form: ZulrahForm,
    val position: ZulrahPosition,
    val actions: List<ZulrahAction>,
)

internal data class ZulrahRotation(
    val number: Int,
    val name: String,
    val phases: List<ZulrahPhase>,
)

private fun tile(x: Int, z: Int) = ShrineTile(x, z)

private fun cloud(ax: Int, az: Int, bx: Int, bz: Int) =
    ZulrahAction.VenomCloudVolley(tile(ax, az), tile(bx, bz))

private fun snake(x: Int, z: Int) = ZulrahAction.SnakelingVolley(tile(x, z))

private fun phase(
    number: Int,
    form: ZulrahForm,
    position: ZulrahPosition,
    vararg actions: ZulrahAction,
): ZulrahPhase = ZulrahPhase(number, form, position, actions.toList())

private val openingClouds =
    arrayOf(
        cloud(2269, 3069, 2272, 3069),
        cloud(2266, 3069, 2263, 3070),
        cloud(2273, 3072, 2273, 3075),
        cloud(2263, 3073, 2263, 3076),
    )

private fun initial() = phase(1, ZulrahForm.SERPENTINE, ZulrahPosition.MIDDLE, *openingClouds)

private fun finalGreen(number: Int) =
    phase(
        number,
        ZulrahForm.SERPENTINE,
        ZulrahPosition.MIDDLE,
        ZulrahAction.StandardAttack(5),
        *openingClouds,
    )

private val southBlueAlternating =
    arrayOf(
        snake(2263, 3070),
        cloud(2266, 3069, 2269, 3069),
        snake(2263, 3076),
        cloud(2272, 3069, 2273, 3072),
        snake(2263, 3073),
    )

private val westJadClouds =
    arrayOf(
        cloud(2263, 3070, 2266, 3069),
        cloud(2269, 3069, 2272, 3069),
        cloud(2263, 3073, 2263, 3076),
        cloud(2273, 3072, 2273, 3075),
    )

internal object ZulrahRotations {
    val MAGMA_A =
        ZulrahRotation(
            1,
            "Magma A",
            listOf(
                initial(),
                phase(2, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                phase(
                    3,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.StandardAttack(4),
                ),
                phase(
                    4,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(5),
                    snake(2263, 3076),
                    snake(2263, 3073),
                    cloud(2263, 3070, 2266, 3069),
                    cloud(2272, 3069, 2273, 3072),
                    snake(2273, 3075),
                    snake(2273, 3077),
                ),
                phase(5, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                phase(6, ZulrahForm.TANZANITE, ZulrahPosition.WEST, ZulrahAction.StandardAttack(5)),
                phase(
                    7,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.SOUTH,
                    cloud(2269, 3069, 2272, 3069),
                    cloud(2263, 3070, 2266, 3069),
                    cloud(2263, 3073, 2263, 3076),
                    snake(2272, 3071),
                    snake(2273, 3075),
                    snake(2273, 3077),
                    snake(2273, 3072),
                ),
                phase(
                    8,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(5),
                    *southBlueAlternating,
                ),
                phase(
                    9,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.WEST,
                    ZulrahAction.JadAttack(10, ZulrahAttackStyle.RANGED),
                    *westJadClouds,
                ),
                phase(10, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                finalGreen(11),
            ),
        )

    val MAGMA_B =
        ZulrahRotation(
            2,
            "Magma B",
            listOf(
                initial(),
                phase(2, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                phase(
                    3,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.StandardAttack(4),
                ),
                phase(
                    4,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.WEST,
                    cloud(2273, 3072, 2272, 3069),
                    cloud(2273, 3075, 2273, 3078),
                    cloud(2269, 3069, 2266, 3069),
                    snake(2266, 3069),
                    snake(2263, 3070),
                    snake(2263, 3073),
                    snake(2263, 3076),
                ),
                phase(
                    5,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(5),
                    snake(2263, 3076),
                    snake(2263, 3073),
                    cloud(2263, 3070, 2266, 3069),
                    cloud(2272, 3069, 2273, 3072),
                    snake(2273, 3075),
                    snake(2273, 3077),
                ),
                phase(6, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                phase(
                    7,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.EAST,
                    ZulrahAction.StandardAttack(5),
                ),
                phase(
                    8,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(5),
                    *southBlueAlternating,
                ),
                phase(
                    9,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.WEST,
                    ZulrahAction.JadAttack(10, ZulrahAttackStyle.RANGED),
                    *westJadClouds,
                ),
                phase(10, ZulrahForm.MAGMA, ZulrahPosition.MIDDLE, ZulrahAction.TailSwipe(2)),
                finalGreen(11),
            ),
        )

    val SERP =
        ZulrahRotation(
            3,
            "Serp",
            listOf(
                initial(),
                phase(
                    2,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.EAST,
                    ZulrahAction.StandardAttack(5),
                    snake(2273, 3078),
                    snake(2273, 3075),
                    snake(2273, 3072),
                ),
                phase(
                    3,
                    ZulrahForm.MAGMA,
                    ZulrahPosition.MIDDLE,
                    cloud(2273, 3078, 2273, 3075),
                    snake(2273, 3072),
                    cloud(2272, 3070, 2269, 3069),
                    snake(2266, 3069),
                    cloud(2263, 3070, 2263, 3073),
                    snake(2263, 3076),
                    ZulrahAction.TailSwipe(2),
                ),
                phase(4, ZulrahForm.TANZANITE, ZulrahPosition.WEST, ZulrahAction.StandardAttack(5)),
                phase(
                    5,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(5),
                ),
                phase(6, ZulrahForm.TANZANITE, ZulrahPosition.EAST, ZulrahAction.StandardAttack(5)),
                phase(
                    7,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.MIDDLE,
                    cloud(2273, 3078, 2273, 3075),
                    cloud(2273, 3072, 2272, 3069),
                    cloud(2269, 3069, 2266, 3069),
                    snake(2263, 3070),
                    snake(2263, 3076),
                    snake(2263, 3073),
                ),
                phase(
                    8,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.WEST,
                    ZulrahAction.StandardAttack(5),
                ),
                phase(
                    9,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.StandardAttack(5),
                    cloud(2263, 3076, 2263, 3073),
                    cloud(2263, 3070, 2266, 3069),
                    snake(2269, 3069),
                    snake(2272, 3069),
                    snake(2273, 3072),
                ),
                phase(
                    10,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.EAST,
                    ZulrahAction.JadAttack(10, ZulrahAttackStyle.MAGIC),
                ),
                phase(
                    11,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    snake(2263, 3076),
                    snake(2263, 3070),
                    snake(2273, 3072),
                    snake(2273, 3078),
                ),
                finalGreen(12),
            ),
        )

    val TANZ =
        ZulrahRotation(
            4,
            "Tanz",
            listOf(
                initial(),
                phase(
                    2,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.EAST,
                    snake(2272, 3069),
                    snake(2273, 3078),
                    snake(2273, 3075),
                    snake(2273, 3072),
                    ZulrahAction.StandardAttack(6),
                ),
                phase(
                    3,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.SOUTH,
                    ZulrahAction.StandardAttack(4),
                    cloud(2263, 3070, 2269, 3069),
                    cloud(2266, 3069, 2272, 3069),
                ),
                phase(
                    4,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.WEST,
                    snake(2263, 3076),
                    snake(2263, 3073),
                    snake(2266, 3069),
                    snake(2269, 3069),
                    ZulrahAction.StandardAttack(4),
                ),
                phase(
                    5,
                    ZulrahForm.MAGMA,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.TailSwipe(2),
                    cloud(2263, 3070, 2269, 3069),
                    cloud(2266, 3069, 2272, 3069),
                ),
                phase(
                    6,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.EAST,
                    ZulrahAction.StandardAttack(4),
                ),
                phase(
                    7,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.SOUTH,
                    snake(2263, 3076),
                    snake(2263, 3073),
                    snake(2263, 3070),
                    snake(2273, 3072),
                    snake(2273, 3075),
                    snake(2273, 3078),
                    cloud(2273, 3075, 2273, 3078),
                    cloud(2272, 3069, 2273, 3072),
                    cloud(2269, 3069, 2266, 3069),
                ),
                phase(
                    8,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.WEST,
                    ZulrahAction.StandardAttack(5),
                    snake(2263, 3076),
                    snake(2263, 3073),
                    snake(2263, 3070),
                    snake(2266, 3069),
                ),
                phase(
                    9,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.StandardAttack(4),
                ),
                phase(
                    10,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    ZulrahAction.StandardAttack(4),
                    cloud(2263, 3073, 2266, 3069),
                    cloud(2263, 3070, 2263, 3076),
                    cloud(2269, 3069, 2272, 3069),
                ),
                phase(
                    11,
                    ZulrahForm.SERPENTINE,
                    ZulrahPosition.EAST,
                    ZulrahAction.JadAttack(8, ZulrahAttackStyle.MAGIC),
                ),
                phase(
                    12,
                    ZulrahForm.TANZANITE,
                    ZulrahPosition.MIDDLE,
                    snake(2263, 3076),
                    snake(2263, 3073),
                    snake(2273, 3075),
                    snake(2273, 3078),
                ),
                finalGreen(13),
            ),
        )

    val all = listOf(MAGMA_A, MAGMA_B, SERP, TANZ)

    fun byNumber(number: Int): ZulrahRotation = requireNotNull(all.find { it.number == number })
}
