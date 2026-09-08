package org.rsmod.content.minigames.toa

internal object ToaInvocationPresetCodec {
    const val SLOT_COUNT = 5

    fun emptyStorage(): MutableList<String> = MutableList(SLOT_COUNT) { "" }

    fun normalizeStorage(values: List<String>?): MutableList<String> =
        MutableList(SLOT_COUNT) { index -> values?.getOrNull(index).orEmpty() }

    fun encode(invocations: Set<ToaInvocation>): String =
        ToaInvocationCatalog.sortForClient(invocations)
            .joinToString(",", transform = ToaInvocation::key)

    fun decode(value: String): Set<ToaInvocation>? {
        if (value.isBlank()) return emptySet()
        val invocations = linkedSetOf<ToaInvocation>()
        value.split(',').forEach { key ->
            val invocation = ToaInvocation.find(key) ?: return null
            if (!invocations.add(invocation)) return null
        }
        if (ToaInvocationRules.validate(invocations).isNotEmpty()) return null
        return invocations
    }
}
