package org.rsmod.api.instances.region

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.instances.InstanceRegionRotation
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.zone.ZoneKey

class InstanceRegionTemplateTest {
    @Test
    fun `default region copy preserves the requested single-plane contract`() {
        val template = buildRegionTemplate(listOf(LEVIATHAN_REGION), level = 0)
        val copies = backingMap(template)

        assertEquals(ZoneKey(256, 792, 0), copyAt(copies, ZoneKey(0, 0, 0)).normalZone())
        assertEquals(RegionZoneCopy.NULL, copyAt(copies, ZoneKey(0, 0, 1)))
    }

    @Test
    fun `all-level region copy preserves bridge and render planes`() {
        val template =
            buildRegionTemplate(
                listOf(LEVIATHAN_REGION),
                level = 0,
                rotation = InstanceRegionRotation.NONE,
                copyAllLevels = true,
            )
        val copies = backingMap(template)

        for (level in 0..3) {
            assertEquals(
                ZoneKey(256, 792, level),
                copyAt(copies, ZoneKey(0, 0, level)).normalZone(),
            )
        }
        assertEquals(ZoneKey(258, 796, 1), copyAt(copies, ZoneKey(2, 4, 1)).normalZone())
        assertEquals(ZoneKey(259, 797, 1), copyAt(copies, ZoneKey(3, 5, 1)).normalZone())
        assertEquals(ZoneKey(259, 796, 1), copyAt(copies, ZoneKey(3, 4, 1)).normalZone())
    }

    private fun backingMap(template: RegionStaticTemplate): Any {
        val field = template.javaClass.getDeclaredField("backing")
        field.isAccessible = true
        return field.get(template)
    }

    private fun copyAt(copies: Any, zone: ZoneKey): RegionZoneCopy {
        val intType = Int::class.javaPrimitiveType
        val packed = copies.javaClass.getMethod("get", intType).invoke(copies, zone.packed) as Int
        val default = copies.javaClass.getMethod("defaultReturnValue").invoke(copies) as Int
        return if (packed == default) {
            RegionZoneCopy.NULL
        } else {
            RegionZoneCopy(packed)
        }
    }

    private companion object {
        const val LEVIATHAN_REGION = 8291
    }
}
