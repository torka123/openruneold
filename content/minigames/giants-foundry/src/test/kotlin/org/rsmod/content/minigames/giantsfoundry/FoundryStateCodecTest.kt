package org.rsmod.content.minigames.giantsfoundry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoundryStateCodecTest {
    @Test
    fun stateCodecRoundTripsPrimitivePersistenceAndRejectsCorruptPreforms() {
        val commission = FoundryCommission(55, FoundryDescriptor.NARROW, FoundryDescriptor.LIGHT, 1234)
        val state =
            FoundryState(
                reputation = 900,
                completedSwords = 4,
                bestQuality = 133,
                commission = commission,
                selectedMoulds = mapOf(MouldSlot.FORTE to "stiletto_forte"),
                unlockedMoulds = setOf("stiletto_forte"),
                crucible = FoundryCrucible(mapOf(FoundryMetal.STEEL to 19, FoundryMetal.MITHRIL to 9)),
                preform = FoundryPreform(55, 59, 18, 77, 77, 4),
                revision = 8,
            )
        assertEquals(state, FoundryStateCodec.decode(FoundryStateCodec.encode(state)))

        val corrupt = FoundryStateCodec.encode(state)
        @Suppress("UNCHECKED_CAST")
        (corrupt.getValue("preform") as MutableMap<String, Any>)["commissionId"] = 99L
        assertEquals(null, FoundryStateCodec.decode(corrupt))
    }
}
