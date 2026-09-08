package dev.openrune

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** Verifies the named revision-240 components used by the native clan founding UI. */
class ClanCreationRscmAuditTest {
    @Test
    fun `dump social channel tab hooks`() {
        ServerCacheManager.init(240)
        for (interfaceId in 0..1000) {
            val type = ServerCacheManager.getInterface(interfaceId) ?: continue
            val names = type.components.values.mapNotNull { it.internalName }.toSet()
            if (!names.containsAll(setOf("tab_0", "tab_1", "tab_2", "tab_3", "contents"))) continue
            type.components.toSortedMap().forEach { (child, component) ->
                if (component.internalName !in setOf("tab_0", "tab_1", "tab_2", "tab_3", "contents")) return@forEach
                val fields = component.javaClass.declaredFields.associate { field ->
                    field.isAccessible = true
                    val value = field.get(component)
                    field.name to when (value) {
                        is Array<*> -> value.contentDeepToString()
                        is IntArray -> value.contentToString()
                        else -> value?.toString()
                    }
                }
                println(
                    "SOCIAL_TAB interface=$interfaceId child=$child name=${component.internalName} " +
                        listOf("onLoad", "onOp", "onClick", "onMouseOver", "onMouseLeave", "op", "events")
                            .joinToString(" ") { "$it=${fields[it]}" }
                )
            }
        }
    }
    @Test
    fun `dump clan creation rscm names`() {
        ServerCacheManager.init(240)
        (0..12).forEach { child ->
            val packed = (705 shl 16) or child
            println(
                "CLAN_CREATION_RSCM child=$child name=${RSCM.getReverseMapping(RSCMType.COMPONENT, packed)}"
            )
        }
    }

    @Test
    fun `dump clan creation dialogue interface names`() {
        ServerCacheManager.init(240)
        listOf(219, 231).forEach { id ->
            println(
                "CLAN_CREATION_DIALOGUE_RSCM id=${id} name=${RSCM.getReverseMapping(RSCMType.INTERFACE, id)}"
            )
        }
    }

    @Test
    fun `owner creation client state varbit exists in revision 240`() {
        ServerCacheManager.init(240)
        assertNotNull(ServerCacheManager.getVarbit(13_058))
    }
}
