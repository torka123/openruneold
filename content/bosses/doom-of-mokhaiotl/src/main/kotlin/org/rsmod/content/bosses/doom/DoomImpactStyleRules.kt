package org.rsmod.content.bosses.doom

/** Keeps a normal projectile prayer-compatible with any Rock Throw hit landing on the same tick. */
internal object DoomImpactStyleRules {
    fun candidates(
        enabledStyles: List<DoomAttackStyle>,
        rockStylesByImpact: Collection<Pair<Int, DoomAttackStyle>>,
        normalImpactCycle: Int,
    ): List<DoomAttackStyle> {
        require(enabledStyles.isNotEmpty())
        val coincidentStyle =
            rockStylesByImpact.firstOrNull { (cycle, style) ->
                cycle == normalImpactCycle && style in enabledStyles
            }?.second
        return coincidentStyle?.let(::listOf) ?: enabledStyles
    }
}
