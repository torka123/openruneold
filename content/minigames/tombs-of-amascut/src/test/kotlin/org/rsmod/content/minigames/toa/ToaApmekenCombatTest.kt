package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.hit.HitType

class ToaApmekenCombatTest {
    @Test
    fun `level zero combat baboons use current fixed max hits and four tick attacks`() {
        val base =
            listOf(ApmekenBaboon.BRAWLER, ApmekenBaboon.THROWER, ApmekenBaboon.MAGE).map {
                checkNotNull(ApmekenCombatRules.definition(it.npcId))
            }
        assertEquals(listOf(5, 5, 5), base.map { it.maxHit })
        assertEquals(listOf(4, 4, 4), base.map { it.attackRate })
        assertTrue(base.none { it.scripted })

        val stronger =
            listOf(
                    ApmekenBaboon.STRONGER_BRAWLER,
                    ApmekenBaboon.STRONGER_THROWER,
                    ApmekenBaboon.STRONGER_MAGE,
                )
                .map { checkNotNull(ApmekenCombatRules.definition(it.npcId)) }
        assertEquals(listOf(7, 7, 7), stronger.map { it.maxHit })
        assertEquals(listOf(4, 4, 4), stronger.map { it.attackRate })
    }

    @Test
    fun `combat and special baboon weaknesses guarantee only the documented style`() {
        val cases =
            mapOf(
                ApmekenBaboon.BRAWLER to HitType.Magic,
                ApmekenBaboon.STRONGER_BRAWLER to HitType.Magic,
                ApmekenBaboon.THROWER to HitType.Melee,
                ApmekenBaboon.STRONGER_THROWER to HitType.Melee,
                ApmekenBaboon.MAGE to HitType.Ranged,
                ApmekenBaboon.STRONGER_MAGE to HitType.Ranged,
                ApmekenBaboon.SHAMAN to HitType.Ranged,
                ApmekenBaboon.CURSED to HitType.Ranged,
            )
        cases.forEach { (baboon, weakness) ->
            assertTrue(ApmekenCombatRules.receivesGuaranteedMaximum(baboon.npcId, weakness))
            HitType.entries
                .filter { it != weakness }
                .forEach {
                    assertFalse(ApmekenCombatRules.receivesGuaranteedMaximum(baboon.npcId, it))
                }
        }
    }

    @Test
    fun `scripted baboons stay out of generic combat and use packet backed timing`() {
        val shaman = checkNotNull(ApmekenCombatRules.definition(ApmekenBaboon.SHAMAN.npcId))
        val cursed = checkNotNull(ApmekenCombatRules.definition(ApmekenBaboon.CURSED.npcId))
        val volatile = checkNotNull(ApmekenCombatRules.definition(ApmekenBaboon.VOLATILE.npcId))

        assertTrue(shaman.scripted)
        assertEquals(7, shaman.maxHit)
        assertEquals(ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL, shaman.attackRate)
        assertTrue(cursed.scripted)
        assertEquals(0, cursed.attackRate)
        assertTrue(volatile.scripted)
        assertEquals(0, volatile.attackRate)
    }
}
