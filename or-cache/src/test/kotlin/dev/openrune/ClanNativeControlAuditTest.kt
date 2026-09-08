package dev.openrune

import org.junit.jupiter.api.Test

/** Diagnostic index for the stock revision-240 clan editors and recruitment screens. */
class ClanNativeControlAuditTest {
    @Test
    fun `locate revision 240 clan member controls`() {
        ServerCacheManager.init(240)
        for (interfaceId in 0..1000) {
            val type = ServerCacheManager.getInterface(interfaceId) ?: continue
            val names = type.components.values.mapNotNull { it.internalName }.toSet()
            if ("scrollcontents" !in names || "minrank" !in names || "maxrank" !in names) continue
            type.components.toSortedMap().forEach { (child, component) ->
                println(
                    "CLAN_MEMBER_CONTROL interface=$interfaceId child=$child " +
                        "packed=${component.packed} name=${component.internalName}",
                )
            }
        }
    }

    @Test
    fun `dump actionable revision 240 clan controls`() {
        ServerCacheManager.init(240)
        listOf(691, 692, 694, 695, 699, 700, 703, 704, 706, 783).forEach { interfaceId ->
            val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
            type.components.toSortedMap().forEach { (child, component) ->
                val values =
                    component.javaClass.declaredFields.associate { field ->
                        field.isAccessible = true
                        field.name to printable(field.get(component))
                    }
                val actionable =
                    values.keys.any { it in ACTION_FIELDS && values[it] != null && values[it] != "null" }
                val named = !component.internalName.isNullOrBlank()
                if (actionable || named) {
                    println(
                        "CLAN_CONTROL interface=$interfaceId child=$child " +
                            "packed=${component.packed} name=${component.internalName} " +
                            ACTION_FIELDS.joinToString(" ") { "$it=${values[it]}" },
                    )
                }
            }
        }
    }

    private fun printable(value: Any?): String? =
        when (value) {
            null -> null
            is Array<*> -> value.contentDeepToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            else -> value.toString()
        }

    private companion object {
        val ACTION_FIELDS =
            listOf(
                "text",
                "opBase",
                "op",
                "onLoad",
                "onOp",
                "onClick",
                "onHold",
                "onMouseOver",
                "onMouseLeave",
                "onVarTransmit",
                "onVarcTransmit",
                "onVarcStrTransmit",
            )
    }
}
