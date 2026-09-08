package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.hit.HitType

class DoomIncomingAttackRulesTest {
    @Test
    fun `incoming hit type controls coloured and protected larvae`() {
        assertTrue(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Magic,
                requiredStyle = DoomAttackStyle.MAGIC,
                protectedStyle = null,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Ranged,
                requiredStyle = DoomAttackStyle.MAGIC,
                protectedStyle = null,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Melee,
                requiredStyle = null,
                protectedStyle = DoomAttackStyle.MELEE,
            )
        )
        assertTrue(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Magic,
                requiredStyle = null,
                protectedStyle = DoomAttackStyle.MELEE,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Typeless,
                requiredStyle = DoomAttackStyle.MAGIC,
                protectedStyle = null,
            )
        )
    }

    @Test
    fun `two protection prayers leave one correct larva style`() {
        val protectedStyles = setOf(DoomAttackStyle.MELEE, DoomAttackStyle.RANGED)
        assertTrue(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Magic,
                requiredStyle = DoomAttackStyle.MAGIC,
                protectedStyles = protectedStyles,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isCorrectLarvaStyle(
                HitType.Ranged,
                requiredStyle = DoomAttackStyle.MAGIC,
                protectedStyles = protectedStyles,
            )
        )
    }

    @Test
    fun `Eye and holy water only one-shot their matching coloured larva`() {
        assertTrue(
            DoomIncomingAttackRules.specialWeaponCanOneShot(
                DoomLarvaSpecialWeapon.EYE_OF_AYAK,
                DoomAttackStyle.MAGIC,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.specialWeaponCanOneShot(
                DoomLarvaSpecialWeapon.EYE_OF_AYAK,
                DoomAttackStyle.RANGED,
            )
        )
        assertTrue(
            DoomIncomingAttackRules.specialWeaponCanOneShot(
                DoomLarvaSpecialWeapon.HOLY_WATER,
                DoomAttackStyle.RANGED,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.specialWeaponCanOneShot(
                DoomLarvaSpecialWeapon.HOLY_WATER,
                DoomAttackStyle.MAGIC,
            )
        )
        assertTrue(
            DoomIncomingAttackRules.specialWeaponRemovesAttackDelay(
                DoomLarvaSpecialWeapon.EYE_OF_AYAK
            )
        )
        assertFalse(
            DoomIncomingAttackRules.specialWeaponRemovesAttackDelay(
                DoomLarvaSpecialWeapon.HOLY_WATER
            )
        )
    }

    @Test
    fun `demonbane classification includes every live weapon`() {
        assertEquals(
            setOf(
                "obj.silverlight",
                "obj.agrith_silverlight_dyed",
                "obj.darklight",
                "obj.arclight",
                "obj.emberlight",
                "obj.bone_claws",
                "obj.scorching_bow",
                "obj.purging_staff",
            ),
            DoomIncomingAttackRules.demonbaneWeaponTypes,
        )
    }

    @Test
    fun `valid demonbane hit kills a two-hitpoint larva while normal weapons deal one`() {
        assertEquals(
            2,
            DoomIncomingAttackRules.larvaDamage(
                incomingDamage = 1,
                currentHitpoints = 2,
                correctStyle = true,
                lethalWeapon = true,
            ),
        )
        assertEquals(
            1,
            DoomIncomingAttackRules.larvaDamage(
                incomingDamage = 40,
                currentHitpoints = 2,
                correctStyle = true,
                lethalWeapon = false,
            ),
        )
        assertEquals(0, DoomIncomingAttackRules.larvaDamage(40, 2, false, true))
        assertEquals(0, DoomIncomingAttackRules.larvaDamage(0, 2, true, false))
    }

    @Test
    fun `normal charge accepts any positive melee hit and never requires demonbane`() {
        assertTrue(
            DoomIncomingAttackRules.interruptsMeleeCharge(
                DoomState.MELEE_CHARGE,
                HitType.Melee,
                damage = 1,
                isFromPlayer = true,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.interruptsMeleeCharge(
                DoomState.MELEE_CHARGE,
                HitType.Ranged,
                damage = 99,
                isFromPlayer = true,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.interruptsMeleeCharge(
                DoomState.SHIELDED,
                HitType.Melee,
                damage = 99,
                isFromPlayer = true,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.interruptsMeleeCharge(
                DoomState.MELEE_CHARGE,
                HitType.Melee,
                damage = 0,
                isFromPlayer = true,
            )
        )
    }

    @Test
    fun `every melee hitsplat inside the punish window is recurrent`() {
        assertTrue(
            DoomIncomingAttackRules.isRecurrentMeleePunish(
                DoomState.ACTIVE,
                HitType.Melee,
                currentCycle = 102,
                punishUntilCycle = 102,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isRecurrentMeleePunish(
                DoomState.ACTIVE,
                HitType.Ranged,
                currentCycle = 101,
                punishUntilCycle = 102,
            )
        )
        assertFalse(
            DoomIncomingAttackRules.isRecurrentMeleePunish(
                DoomState.ACTIVE,
                HitType.Melee,
                currentCycle = 103,
                punishUntilCycle = 102,
            )
        )
    }
}
