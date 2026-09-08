package org.rsmod.content.bosses.doom

import dev.openrune.types.ItemServerType
import org.rsmod.game.hit.HitType

internal enum class DoomLarvaSpecialWeapon {
    NONE,
    EYE_OF_AYAK,
    HOLY_WATER,
}

/**
 * Target-side rules for attacks that have already been launched at the Doom encounter.
 *
 * These deliberately consume the immutable hit type instead of the player's current combat style. A
 * player can switch weapons or attack styles while a projectile is in flight without changing how
 * that projectile resolves.
 */
internal object DoomIncomingAttackRules {
    /** Weapons whose item identity alone qualifies as demonbane for Doom/larva handling. */
    val demonbaneWeaponTypes: Set<String> =
        setOf(
            "obj.silverlight",
            "obj.agrith_silverlight_dyed",
            "obj.darklight",
            "obj.arclight",
            "obj.emberlight",
            "obj.bone_claws",
            "obj.scorching_bow",
            "obj.purging_staff",
        )

    fun isDemonbaneWeapon(weapon: ItemServerType?): Boolean =
        weapon != null && demonbaneWeaponTypes.any(weapon::isType)

    /**
     * The normal charge is not the demonic shield. Any positive player melee hit interrupts it;
     * demonbane is only a requirement for damaging the separate Delve 3+ shield.
     */
    fun interruptsMeleeCharge(
        state: DoomState,
        hitType: HitType,
        damage: Int,
        isFromPlayer: Boolean,
    ): Boolean =
        state == DoomState.MELEE_CHARGE && isFromPlayer && hitType == HitType.Melee && damage > 0

    fun attackStyle(hitType: HitType): DoomAttackStyle? =
        when (hitType) {
            HitType.Melee -> DoomAttackStyle.MELEE
            HitType.Ranged -> DoomAttackStyle.RANGED
            HitType.Magic -> DoomAttackStyle.MAGIC
            HitType.Typeless -> null
        }

    /**
     * A coloured larva requires its unprotected style; a prayer larva accepts any style not in its
     * active protection set.
     */
    fun isCorrectLarvaStyle(
        hitType: HitType,
        requiredStyle: DoomAttackStyle?,
        protectedStyles: Set<DoomAttackStyle>,
    ): Boolean {
        if (requiredStyle == null && protectedStyles.isEmpty()) return true
        val incomingStyle = attackStyle(hitType) ?: return false
        return when {
            protectedStyles.isNotEmpty() -> incomingStyle !in protectedStyles
            else -> incomingStyle == requiredStyle
        }
    }

    /** Compatibility overload for the single-prayer larvae in delves 2-3. */
    fun isCorrectLarvaStyle(
        hitType: HitType,
        requiredStyle: DoomAttackStyle?,
        protectedStyle: DoomAttackStyle?,
    ): Boolean = isCorrectLarvaStyle(hitType, requiredStyle, setOfNotNull(protectedStyle))

    /**
     * Larvae use a target-specific damage cap: ordinary valid hits deal one, while demonbane and
     * the matching special tools kill the larva in one hit. The shared flat-armour modifier is
     * disabled on spawned larvae so this value is the final queued damage.
     */
    fun larvaDamage(
        incomingDamage: Int,
        currentHitpoints: Int,
        correctStyle: Boolean,
        lethalWeapon: Boolean,
    ): Int =
        when {
            !correctStyle -> 0
            lethalWeapon -> currentHitpoints.coerceAtLeast(1)
            incomingDamage > 0 -> 1
            else -> 0
        }

    fun specialWeaponCanOneShot(
        weapon: DoomLarvaSpecialWeapon,
        larvaStyle: DoomAttackStyle?,
    ): Boolean =
        when (weapon) {
            DoomLarvaSpecialWeapon.EYE_OF_AYAK -> larvaStyle == DoomAttackStyle.MAGIC
            DoomLarvaSpecialWeapon.HOLY_WATER -> larvaStyle == DoomAttackStyle.RANGED
            DoomLarvaSpecialWeapon.NONE -> false
        }

    fun specialWeaponRemovesAttackDelay(weapon: DoomLarvaSpecialWeapon): Boolean =
        weapon == DoomLarvaSpecialWeapon.EYE_OF_AYAK

    fun isRecurrentMeleePunish(
        state: DoomState,
        hitType: HitType,
        currentCycle: Int,
        punishUntilCycle: Int,
    ): Boolean =
        state == DoomState.ACTIVE && hitType == HitType.Melee && currentCycle <= punishUntilCycle
}
