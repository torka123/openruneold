package org.rsmod.content.bosses.zulrah

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ZulrahRotationTest {
    @Test
    fun `all four rotations contain the official phase count`() {
        assertEquals(listOf(11, 11, 12, 13), ZulrahRotations.all.map { it.phases.size })
        ZulrahRotations.all.forEach { rotation ->
            assertEquals((1..rotation.phases.size).toList(), rotation.phases.map { it.number })
        }
    }

    @Test
    fun `all rotations begin with the exact captured four cloud barrages`() {
        val expected =
            listOf(
                "2269,3069>2272,3069",
                "2266,3069>2263,3070",
                "2273,3072>2273,3075",
                "2263,3073>2263,3076",
            )
        ZulrahRotations.all.forEach { rotation ->
            val first = rotation.phases.first()
            assertEquals(ZulrahForm.SERPENTINE, first.form)
            assertEquals(ZulrahPosition.MIDDLE, first.position)
            assertEquals(expected, first.actions.map(::cloudSignature))
            assertTrue(first.actions.none { it is ZulrahAction.StandardAttack })
        }
    }

    @Test
    fun `surface and fixed facing coordinates match the shrine`() {
        assertEquals(
            listOf(
                listOf(2266, 3073, 2268, 3065),
                listOf(2266, 3062, 2268, 3068),
                listOf(2257, 3071, 2268, 3073),
                listOf(2276, 3071, 2270, 3074),
            ),
            ZulrahPosition.entries.map { listOf(it.x, it.z, it.faceX, it.faceZ) },
        )
    }

    @Test
    fun `rotation recognition phase two is correct`() {
        assertEquals(ZulrahForm.MAGMA, ZulrahRotations.MAGMA_A.phases[1].form)
        assertEquals(ZulrahForm.MAGMA, ZulrahRotations.MAGMA_B.phases[1].form)
        assertEquals(ZulrahForm.SERPENTINE, ZulrahRotations.SERP.phases[1].form)
        assertEquals(ZulrahPosition.EAST, ZulrahRotations.SERP.phases[1].position)
        assertEquals(ZulrahForm.TANZANITE, ZulrahRotations.TANZ.phases[1].form)
        assertEquals(ZulrahPosition.EAST, ZulrahRotations.TANZ.phases[1].position)
    }

    @Test
    fun `magma rotations share three phases then diverge`() {
        assertEquals(ZulrahRotations.MAGMA_A.phases.take(3), ZulrahRotations.MAGMA_B.phases.take(3))
        assertNotEquals(ZulrahRotations.MAGMA_A.phases[3], ZulrahRotations.MAGMA_B.phases[3])
    }

    @Test
    fun `every phase preserves its ordered attack and hazard program`() {
        assertEquals(
            listOf(
                "CCCC",
                "T2",
                "S4",
                "S5NNCCNN",
                "T2",
                "S5",
                "CCCNNNN",
                "S5NCNCN",
                "J10RCCCC",
                "T2",
                "S5CCCC",
            ),
            phasePrograms(ZulrahRotations.MAGMA_A),
        )
        assertEquals(
            listOf(
                "CCCC",
                "T2",
                "S4",
                "CCCNNNN",
                "S5NNCCNN",
                "T2",
                "S5",
                "S5NCNCN",
                "J10RCCCC",
                "T2",
                "S5CCCC",
            ),
            phasePrograms(ZulrahRotations.MAGMA_B),
        )
        assertEquals(
            listOf(
                "CCCC",
                "S5NNN",
                "CNCNCNT2",
                "S5",
                "S5",
                "S5",
                "CCCNNN",
                "S5",
                "S5CCNNN",
                "J10M",
                "NNNN",
                "S5CCCC",
            ),
            phasePrograms(ZulrahRotations.SERP),
        )
        assertEquals(
            listOf(
                "CCCC",
                "NNNNS6",
                "S4CC",
                "NNNNS4",
                "T2CC",
                "S4",
                "NNNNNNCCC",
                "S5NNNN",
                "S4",
                "S4CCC",
                "J8M",
                "NNNN",
                "S5CCCC",
            ),
            phasePrograms(ZulrahRotations.TANZ),
        )
    }

    @Test
    fun `jad phases start with the verified style and exact count`() {
        val jad =
            ZulrahRotations.all.map { rotation ->
                rotation.phases
                    .flatMap { it.actions }
                    .filterIsInstance<ZulrahAction.JadAttack>()
                    .single()
            }
        assertEquals(listOf(10, 10, 10, 8), jad.map { it.count })
        assertEquals(
            listOf(
                ZulrahAttackStyle.RANGED,
                ZulrahAttackStyle.RANGED,
                ZulrahAttackStyle.MAGIC,
                ZulrahAttackStyle.MAGIC,
            ),
            jad.map { it.startsWith },
        )
        jad.forEach { action ->
            val generated = ZulrahRules.jadStyles(action.count, action.startsWith)
            generated.zipWithNext().forEach { (left, right) -> assertNotEquals(left, right) }
        }
    }

    @Test
    fun `every hazard volley has explicit shrine targets`() {
        ZulrahRotations.all
            .flatMap { it.phases }
            .flatMap { it.actions }
            .forEach { action ->
                when (action) {
                    is ZulrahAction.VenomCloudVolley -> {
                        assertTrue(action.primary.x > 0 && action.primary.z > 0)
                        assertTrue(action.secondary.x > 0 && action.secondary.z > 0)
                    }
                    is ZulrahAction.SnakelingVolley ->
                        assertTrue(action.target.x > 0 && action.target.z > 0)
                    else -> Unit
                }
            }
    }

    private fun phasePrograms(rotation: ZulrahRotation): List<String> =
        rotation.phases.map { phase ->
            phase.actions.joinToString(separator = "", transform = ::actionCode)
        }

    private fun actionCode(action: ZulrahAction): String =
        when (action) {
            is ZulrahAction.Delay -> "D${action.ticks}"
            is ZulrahAction.StandardAttack -> "S${action.count}"
            is ZulrahAction.JadAttack ->
                "J${action.count}${if (action.startsWith == ZulrahAttackStyle.MAGIC) "M" else "R"}"
            is ZulrahAction.TailSwipe -> "T${action.count}"
            is ZulrahAction.VenomCloudVolley -> "C"
            is ZulrahAction.SnakelingVolley -> "N"
        }

    private fun cloudSignature(action: ZulrahAction): String {
        action as ZulrahAction.VenomCloudVolley
        return "${action.primary.x},${action.primary.z}>${action.secondary.x},${action.secondary.z}"
    }
}
