package org.rsmod.content.other.pkkits

public data class KitRestrictionContext(
    val mutableOnline: Boolean = true,
    val alive: Boolean = true,
    val inCombat: Boolean = false,
    val inWilderness: Boolean = false,
    val inInstance: Boolean = false,
    val busy: Boolean = false,
    val ultimateIronman: Boolean = false,
    val permittedArea: Boolean = true,
)
