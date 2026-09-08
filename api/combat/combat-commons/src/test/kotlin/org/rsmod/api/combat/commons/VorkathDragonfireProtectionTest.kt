package org.rsmod.api.combat.commons

import kotlin.test.Test
import kotlin.test.assertEquals

class VorkathDragonfireProtectionTest {
    private fun cap(
        shield: Boolean = false,
        protect: Boolean = false,
        antifire: Boolean = false,
        superAntifire: Boolean = false,
    ) = DragonfireProtection.resolveVorkathMaxHit(shield, protect, antifire, superAntifire)

    @Test fun noProtectionCapsAt80() = assertEquals(80, cap())

    @Test fun shieldCapsAt20() = assertEquals(20, cap(shield = true))

    @Test fun protectMagicCapsAt30() = assertEquals(30, cap(protect = true))

    @Test fun shieldAndPrayerCapsAt20() = assertEquals(20, cap(shield = true, protect = true))

    @Test fun antifireAloneCapsAt70() = assertEquals(70, cap(antifire = true))

    @Test fun antifireAndShieldCapsAt10() = assertEquals(10, cap(shield = true, antifire = true))

    @Test fun antifireAndPrayerCapsAt20() = assertEquals(20, cap(protect = true, antifire = true))

    @Test fun antifireShieldPrayerCapsAt10() = assertEquals(10, cap(true, true, true, false))

    @Test fun superAntifireAloneCapsAt60() = assertEquals(60, cap(superAntifire = true))

    @Test
    fun resistedUnprotectedCapsAt50() =
        assertEquals(
            50,
            DragonfireProtection.resolveVorkathResistedMaxHit(false, false, false, false),
        )

    @Test
    fun resistedAntifireCapsAt40() =
        assertEquals(
            40,
            DragonfireProtection.resolveVorkathResistedMaxHit(false, false, true, false),
        )

    @Test
    fun resistedSuperAntifireCapsAt30() =
        assertEquals(
            30,
            DragonfireProtection.resolveVorkathResistedMaxHit(false, false, false, true),
        )

    @Test
    fun superAntifireAndShieldBlocks() = assertEquals(0, cap(shield = true, superAntifire = true))

    @Test
    fun superAntifireAndPrayerCapsAt10() =
        assertEquals(10, cap(protect = true, superAntifire = true))

    @Test fun superAntifireShieldPrayerBlocks() = assertEquals(0, cap(true, true, false, true))

    @Test
    fun superOverridesRegularAlone() = assertEquals(60, cap(antifire = true, superAntifire = true))

    @Test fun superAndShieldOverrideRegular() = assertEquals(0, cap(true, false, true, true))

    @Test fun superAndPrayerOverrideRegular() = assertEquals(10, cap(false, true, true, true))

    @Test fun maximumProtectionBlocks() = assertEquals(0, cap(true, true, true, true))
}
