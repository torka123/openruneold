package org.rsmod.content.skills.magic.spell.attacks.standard

/**
 * Royal Titans opposing-element accuracy, in the shared formula's 0..10,000 units. Evidence: OSRS
 * Wiki Royal Titans/Strategies; NPC identities verified in revision 240. This is a rule in the
 * existing spell content, not an engine callback or another spell handler.
 */
internal object RoyalTitansElementalAccuracy {
    fun hitChance(
        targetId: Int?,
        waterSpell: Boolean,
        fireSpell: Boolean,
        magicBonus: Int,
        voidMage: Boolean,
    ): Int? {
        val opposing =
            when (targetId) {
                14150 -> waterSpell
                14151 -> fireSpell
                else -> false
            }
        if (!opposing) return null
        val basePercent = (magicBonus.toLong() + 30).coerceAtLeast(30)
        return (basePercent * if (voidMage) 145 else 100).coerceAtMost(10000).toInt()
    }
}
