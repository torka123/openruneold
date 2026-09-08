package org.rsmod.content.skills.agility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class AgilityRouteCoverageTest {
    @Test
    fun `all twelve shared courses match every donor player sequence`() {
        val expected =
            mapOf(
                "gnome" to
                    listOf(
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(828),
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(828),
                        signature(828),
                        signature(749, 749),
                    ),
                "barbarian" to
                    listOf(
                        signature(751),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(828),
                        signature(753, renders = listOf(AgilityRenderStyle.Wall)),
                        signature(827),
                        signature(839),
                        signature(839),
                        signature(839),
                    ),
                "draynor" to
                    listOf(
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(753, 759, renders = listOf(AgilityRenderStyle.Wall)),
                        signature(2583, 2585),
                        signature(2586, 2588),
                        signature(2586, 2588, 2586, 2588),
                    ),
                "al_kharid" to
                    listOf(
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(1995, 751),
                        signature(2586, 1601, 1602),
                        signature(2583, 1122, 1124, 2588),
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2586),
                    ),
                "varrock" to
                    listOf(
                        signature(828, 2585),
                        signature(741, 741, 741),
                        signature(2586, 2588),
                        signature(
                            1995,
                            2583,
                            1122,
                            1122,
                            1122,
                            1122,
                            1122,
                            753,
                            741,
                            renders = listOf(AgilityRenderStyle.Wall),
                        ),
                        signature(2583, 2585),
                        signature(1995, 4789, 2583, 2585),
                        signature(2586, 2588),
                        signature(1603),
                        signature(741, 2586, 2588),
                    ),
                "canifis" to
                    listOf(
                        signature(1765),
                        signature(1995, 2586, 2588),
                        signature(2586, 2588),
                        signature(2583, 2585),
                        signature(2586, 2588),
                        signature(1995, 7132, 2588),
                        signature(2586, 2588),
                        signature(2586, 2588),
                    ),
                "falador" to
                    listOf(
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2583, 1118, 1118, 1118, 1118, 1120),
                        signature(741),
                        signature(741),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(7134),
                        signature(1603),
                        signature(1603),
                        signature(1603),
                        signature(1603),
                        signature(1603),
                        signature(1603, 2586),
                    ),
                "seers" to
                    listOf(
                        signature(737, 1118),
                        signature(2586, 2588, 2586, 2588),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2585),
                        signature(2586, 2588),
                        signature(2462, 2588),
                    ),
                "pollnivneach" to
                    listOf(
                        signature(828),
                        signature(2586, 2588, 2588),
                        signature(751),
                        signature(1995, 1603),
                        signature(2583, 1122, 1124, 2588),
                        signature(828),
                        signature(742, 743, renders = listOf(AgilityRenderStyle.MonkeyBars)),
                        signature(1603, 1603),
                        signature(2586, 2588, 2588),
                    ),
                "rellekka" to
                    listOf(
                        signature(828),
                        signature(1995, 1603),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(
                            752,
                            renders = listOf(AgilityRenderStyle.Jump, AgilityRenderStyle.Balance),
                        ),
                        signature(1995, 1603),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2586, 2588),
                    ),
                "ardougne" to
                    listOf(
                        signature(737, 737, 737, 2588),
                        signature(2586, 2588, 2586, 2588, 2583, 2588),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2586, 2588),
                        signature(7133, 2588),
                        signature(753, 759, renders = listOf(AgilityRenderStyle.Wall)),
                        signature(2586, 2588, 741, 741, 2586, 2588),
                    ),
                "prifddinas" to
                    listOf(
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(2583, 2585, 741),
                        signature(2586, 2588),
                        signature(827),
                        signature(828),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(renders = listOf(AgilityRenderStyle.Balance)),
                        signature(827),
                    ),
            )

        assertEquals(expected.keys, AgilityDefinitions.courses.map { it.key }.toSet())
        for (course in AgilityDefinitions.courses) {
            val donorStages = expected.getValue(course.key)
            assertEquals(course.finalStage, donorStages.size, course.key)
            for (stage in 1..course.finalStage) {
                assertEquals(
                    donorStages[stage - 1],
                    effectiveSignature(course, stage),
                    "${course.key}:$stage",
                )
            }
        }
    }

    @Test
    fun `audited routes end on their configured destination`() {
        for (course in AgilityDefinitions.courses) {
            for (obstacle in course.obstacles) {
                val route = AgilityRoutes.routeFor(course.key, obstacle.stage)
                val configured = obstacle.movement.destination ?: continue
                if (route.isNotEmpty()) {
                    assertEquals(
                        configured,
                        route.last().destination,
                        "${course.key}:${obstacle.stage}",
                    )
                }
            }
        }
    }

    @Test
    fun `course render families use their real movement sequences`() {
        assertEquals(763, AgilityRenderStyle.Balance.readyAnimation)
        assertEquals(762, AgilityRenderStyle.Balance.movementAnimation)
        assertEquals(757, AgilityRenderStyle.Wall.readyAnimation)
        assertEquals(756, AgilityRenderStyle.Wall.movementAnimation)
        assertEquals(755, AgilityRenderStyle.Jump.readyAnimation)
        assertEquals(754, AgilityRenderStyle.Jump.movementAnimation)
        assertEquals(744, AgilityRenderStyle.MonkeyBars.readyAnimation)
        assertEquals(744, AgilityRenderStyle.MonkeyBars.movementAnimation)

        val wallStages = listOf("barbarian" to 4, "draynor" to 4, "varrock" to 4, "ardougne" to 6)
        for ((course, stage) in wallStages) {
            assertTrue(
                AgilityRoutes.routeFor(course, stage).any {
                    it.renderStyle == AgilityRenderStyle.Wall
                },
                "$course:$stage",
            )
        }
        assertTrue(
            AgilityRoutes.routeFor("pollnivneach", 7).any {
                it.renderStyle == AgilityRenderStyle.MonkeyBars
            }
        )
    }

    @Test
    fun `donor one-shot sequences are not restarted during sustained movement`() {
        assertEquals(listOf(2586, 1601, 1602), animations("al_kharid", 4))
        assertEquals(listOf(753), animations("barbarian", 4))
        assertEquals(listOf(751), animations("pollnivneach", 3))
        assertEquals(listOf(1603, 2586), animations("falador", 13))
        assertTrue(AgilityRoutes.routeFor("falador", 13).none { it.landingAnimation == 2588 })
    }

    @Test
    fun `final Prifddinas tightrope uses balance renders before changing plane`() {
        val route = AgilityRoutes.routeFor("prifddinas", 11)
        assertEquals(3, route.size)
        assertEquals(AgilityRenderStyle.Balance, route[1].renderStyle)
        assertEquals(763, route[1].animation)
        assertEquals(false, route[1].teleport)
        assertEquals(true, route[2].teleport)
        assertEquals(null, route[2].animation)
    }

    @Test
    fun `audited rooftop routes retain donor approach tiles and intermediate paths`() {
        assertEquals(CoordGrid(3507, 3488, 0), AgilityRoutes.approachFor("canifis", 1))
        assertEquals(CoordGrid(3480, 3485, 2), AgilityRoutes.approachFor("canifis", 6))
        assertEquals(CoordGrid(3318, 3165, 1), AgilityRoutes.approachFor("al_kharid", 5))
        assertEquals(CoordGrid(3194, 3416, 1), AgilityRoutes.approachFor("varrock", 4))
        assertEquals(CoordGrid(3208, 3397, 3), AgilityRoutes.approachFor("varrock", 6))
        assertEquals(CoordGrid(3350, 2968, 1), AgilityRoutes.approachFor("pollnivneach", 2))
        assertEquals(CoordGrid(3355, 2976, 1), AgilityRoutes.approachFor("pollnivneach", 3))
        assertEquals(CoordGrid(3362, 2977, 1), AgilityRoutes.approachFor("pollnivneach", 4))
        assertEquals(CoordGrid(3367, 2976, 1), AgilityRoutes.approachFor("pollnivneach", 5))
        assertEquals(CoordGrid(3366, 2982, 1), AgilityRoutes.approachFor("pollnivneach", 6))
        assertEquals(CoordGrid(3365, 2983, 2), AgilityRoutes.approachFor("pollnivneach", 7))
        assertEquals(CoordGrid(3359, 2995, 2), AgilityRoutes.approachFor("pollnivneach", 8))
        assertEquals(CoordGrid(3362, 3002, 2), AgilityRoutes.approachFor("pollnivneach", 9))
        assertEquals(CoordGrid(2628, 3654, 3), AgilityRoutes.approachFor("rellekka", 4))

        val varrockGap = AgilityRoutes.routeFor("varrock", 6)
        assertEquals(CoordGrid(3208, 3397, 3), varrockGap[0].destination)
        assertEquals(0, varrockGap[0].durationTicks)
        assertEquals(CoordGrid(3215, 3399, 3), varrockGap[1].destination)
        assertEquals(2, varrockGap[1].durationTicks)

        assertEquals(
            CoordGrid(3258, 6105, 2),
            AgilityRoutes.routeFor("prifddinas", 2).first().destination,
        )
        assertEquals(
            CoordGrid(3273, 6107, 2),
            AgilityRoutes.routeFor("prifddinas", 3)[2].destination,
        )
        assertEquals(14, AgilityRoutes.routeFor("prifddinas", 2)[1].durationTicks)
        assertEquals(6, AgilityRoutes.routeFor("prifddinas", 12).single().durationTicks)

        val canifisVault = AgilityRoutes.routeFor("canifis", 6)
        assertEquals(CoordGrid(3478, 3487, 2), canifisVault.first().destination)
        assertEquals(CoordGrid(3489, 3476, 3), canifisVault.last().destination)
        assertTrue(canifisVault.dropLast(1).all { it.facing == AgilityRouteFacing.East })
    }

    private fun animations(course: String, stage: Int): List<Int> =
        AgilityRoutes.routeFor(course, stage).mapNotNull { it.animation }

    private data class DonorSignature(
        val animations: List<Int>,
        val renders: List<AgilityRenderStyle>,
    )

    private fun signature(
        vararg animations: Int,
        renders: List<AgilityRenderStyle> = emptyList(),
    ): DonorSignature = DonorSignature(animations.toList(), renders)

    private fun effectiveSignature(course: AgilityCourse, stage: Int): DonorSignature {
        val route = AgilityRoutes.routeFor(course.key, stage)
        if (route.isNotEmpty()) {
            val animations = buildList {
                for (step in route) {
                    val renderOwnsAnimation =
                        step.renderStyle != null &&
                            step.animation == step.renderStyle.readyAnimation
                    if (step.animation != null && !renderOwnsAnimation) add(step.animation)
                    if (step.landingAnimation != null) add(step.landingAnimation)
                }
            }
            return DonorSignature(animations, route.mapNotNull { it.renderStyle }.distinct())
        }

        val movement = course.obstacles.first { it.stage == stage }.movement
        return when (movement.style) {
            AgilityMovementStyle.Balance ->
                DonorSignature(emptyList(), listOf(AgilityRenderStyle.Balance))
            AgilityMovementStyle.Climb,
            AgilityMovementStyle.Jump ->
                DonorSignature(
                    listOfNotNull(movement.animation, movement.landingAnimation),
                    emptyList(),
                )
            AgilityMovementStyle.Pipe -> {
                val animations = buildList {
                    add(movement.animation)
                    if (
                        movement.travelAnimation != null &&
                            movement.travelAnimation != movement.animation
                    ) {
                        add(movement.travelAnimation)
                    }
                    if (movement.landingAnimation != null) add(movement.landingAnimation)
                }
                DonorSignature(animations, emptyList())
            }
        }
    }
}
