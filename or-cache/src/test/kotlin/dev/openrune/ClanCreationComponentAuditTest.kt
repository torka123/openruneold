package dev.openrune

import org.junit.jupiter.api.Test

/** Concise revision-240 audit for the native clan founding side panel. */
class ClanCreationComponentAuditTest {
    @Test
    fun `dump clan creation controls`() {
        ServerCacheManager.init(240)
        val type = requireNotNull(ServerCacheManager.getInterface(CLAN_CREATION_INTERFACE))
        type.components.toSortedMap().forEach { (child, component) ->
            val interesting =
                component.javaClass.declaredFields.mapNotNull { field ->
                    if (
                        field.name !in
                            setOf(
                                "actions",
                                "onLoad",
                                "onOp",
                                "onTargetEnter",
                                "onTargetLeave",
                                "opbase",
                                "text",
                                "type",
                                "width",
                                "height",
                            )
                    ) {
                        return@mapNotNull null
                    }
                    runCatching {
                            field.isAccessible = true
                            val value = field.get(component)
                            val printable =
                                when (value) {
                                    is Array<*> -> value.contentDeepToString()
                                    is IntArray -> value.contentToString()
                                    else -> value
                                }
                            "${field.name}=$printable"
                        }
                        .getOrNull()
                }
            println(
                "CLAN_CREATION child=$child packed=${component.packed} ${interesting.joinToString(" | ")}",
            )
        }
    }

    private companion object {
        const val CLAN_CREATION_INTERFACE = 705
    }
}
