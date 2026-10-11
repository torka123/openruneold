package org.rsmod.content.bosses.corporealbeast

import kotlin.math.max

internal enum class CorporealBeastAttack {
    Melee,
    Magic,
    Drain,
    AreaMagic,
}

/** Frozen pre-migration selector oracle. Not packaged in production or registered as combat. */
internal object CorporealBeastLegacyCombat {
    fun attack(inMeleeReach: Boolean, primaryRoll: Int, magicRoll: Int): CorporealBeastAttack {
        if (inMeleeReach && primaryRoll in 0 until CorporealBeastRules.MELEE_CHANCE_PERCENT) {
            return CorporealBeastAttack.Melee
        }
        return when (magicRoll.mod(3)) {
            0 -> CorporealBeastAttack.Magic
            1 -> CorporealBeastAttack.Drain
            else -> CorporealBeastAttack.AreaMagic
        }
    }

    fun attackRate(shortRoll: Int): Int =
        if (shortRoll.mod(CorporealBeastRules.SHORT_ATTACK_ROLL_DENOMINATOR) == 0) {
            CorporealBeastRules.SHORT_ATTACK_RATE_TICKS
        } else {
            CorporealBeastRules.NORMAL_ATTACK_RATE_TICKS
        }

    fun distanceToFootprint(npcX: Int, npcZ: Int, npcSize: Int, playerX: Int, playerZ: Int): Int {
        val dx = maxOf(npcX - playerX, playerX - (npcX + npcSize - 1), 0)
        val dz = maxOf(npcZ - playerZ, playerZ - (npcZ + npcSize - 1), 0)
        return max(dx, dz)
    }
}
