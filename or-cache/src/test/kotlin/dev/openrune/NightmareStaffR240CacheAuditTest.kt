package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NightmareStaffR240CacheAuditTest {
    @Test
    fun `revision 240 resolves Nightmare staff special effects`() {
        ServerCacheManager.init(240)

        val sequences = mapOf("seq.nightmare_staff_special" to 8532)
        sequences.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.SEQ)
            assertEquals(expected, id, name)
            assertEquals(name, requireNotNull(ServerCacheManager.getAnim(id)).internalName)
        }

        val spotanims =
            mapOf(
                "spotanim.nightmare_staff_volatile_cast_spotanim" to 1760,
                "spotanim.nightmare_staff_volatile_hit_spotanim" to 1759,
                "spotanim.nightmare_staff_eldritch_cast_spotanim" to 1762,
                "spotanim.nightmare_staff_eldritch_hit_spotanim" to 1761,
            )
        spotanims.forEach { (name, expected) ->
            assertEquals(expected, name.asRSCM(RSCMType.SPOTANIM), name)
        }
    }
}
