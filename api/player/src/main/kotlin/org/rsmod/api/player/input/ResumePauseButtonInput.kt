package org.rsmod.api.player.input

public data class ResumePauseButtonInput(
    public val component: String,
    public val subcomponent: Int,
    /** Exact revision-specific component ID supplied by the client packet, when available. */
    public val componentPacked: Int? = null,
) {
    public fun isComponentType(type: String): Boolean = type == component

    public fun isSubcomponent(comsub: Int): Boolean = subcomponent == comsub
}
