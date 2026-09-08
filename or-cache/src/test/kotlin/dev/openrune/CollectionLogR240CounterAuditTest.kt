package dev.openrune

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CollectionLogR240CounterAuditTest {
    @Test
    fun `revision 240 collection activity links are stable`() {
        ServerCacheManager.init(240)
        assertNotNull(ServerCacheManager.getParam(2519))

        val expected =
            mapOf(
                539 to 2,
                1016 to 3,
                995 to 4,
                6413 to 6,
                5029 to 23,
                4652 to 24,
                909 to 25,
                605 to 17,
                489 to 28,
                541 to 29,
                1015 to 30,
                4655 to 36,
                1203 to 37,
                1189 to 38,
                3769 to 41,
                1263 to 42,
                4455 to 45,
                1743 to 46,
                6337 to 50,
                2867 to 52,
                4653 to 63,
                503 to 66,
                4654 to 67,
                4736 to 69,
                505 to 71,
            )

        assertEquals(
            expected,
            expected.keys.associateWith { structId ->
                requireNotNull(requireNotNull(ServerCacheManager.getStruct(structId)).params)[2519]
            },
        )
    }
}
