package org.rsmod.content.skills.agility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgilityRouteCoverageTest {
    @Test
    fun `non-rooftop courses match every donor player sequence`() {
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

        val wallStages = listOf("barbarian" to 4)
        for ((course, stage) in wallStages) {
            assertTrue(
                AgilityRoutes.routeFor(course, stage).any {
                    it.renderStyle == AgilityRenderStyle.Wall
                },
                "$course:$stage",
            )
        }
    }

    @Test
    fun `donor one-shot sequences are not restarted during sustained movement`() {
        assertEquals(listOf(753), animations("barbarian", 4))
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
