package org.rsmod.api.player.prayer

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Prevents the three protection prayers from being enabled for a short, temporary duration.
 *
 * Applying the lock immediately disables the current Protect from Melee, Protect from Missiles, and
 * Protect from Magic states. It intentionally leaves every other prayer untouched.
 */
public object ProtectionPrayerLock {
    public const val DURATION_CYCLES: Int = 8

    /**
     * Disables the three protection prayers on [target] and prevents them from being enabled for
     * [DURATION_CYCLES] game cycles.
     */
    public fun apply(target: Player): Unit {
        target.attr[LOCK_EXPIRY] = target.currentMapClock + DURATION_CYCLES

        for (prayer in PROTECTION_PRAYERS) {
            VarPlayerIntMapSetter.set(target, prayer, value = 0)
        }

        target.clearProtectionPrayerOverhead()
        target.stopPrayerDrainIfNoneRemain()
    }

    /**
     * Returns `true` when [prayerVarbit] is a protection prayer which is currently locked for
     * [target].
     */
    public fun blocks(target: Player, prayerVarbit: String): Boolean {
        return prayerVarbit in PROTECTION_PRAYERS && isActive(target)
    }

    /** Removes an encounter-owned protection lock without re-enabling any prayer. */
    public fun clear(target: Player): Unit {
        target.attr.remove(LOCK_EXPIRY)
    }

    public fun isActive(target: Player): Boolean {
        val expiry = target.attr[LOCK_EXPIRY] ?: return false
        if (target.currentMapClock < expiry) {
            return true
        }

        target.attr.remove(LOCK_EXPIRY)
        return false
    }

    private fun Player.clearProtectionPrayerOverhead() {
        if (overheadIcon in PROTECTION_OVERHEAD_ICONS) {
            overheadIcon = null
        }
    }

    private fun Player.stopPrayerDrainIfNoneRemain() {
        if (vars["varbit.prayer_allactive"] != 0) {
            return
        }

        VarPlayerIntMapSetter.set(this, "varbit.quickprayer_active", value = 0)
        VarPlayerIntMapSetter.set(this, "varbit.prayer_drain_counter", value = 0)
        clearSoftTimer("timer.prayer_drain")
    }

    private val LOCK_EXPIRY: AttributeKey<Int> = AttributeKey(resetOnDeath = true, temp = true)

    private val PROTECTION_PRAYERS: Set<String> =
        setOf(
            "varbit.prayer_protectfrommelee",
            "varbit.prayer_protectfrommissiles",
            "varbit.prayer_protectfrommagic",
        )

    private val PROTECTION_OVERHEAD_ICONS: Set<Int> = setOf(0, 1, 2, 6, 7, 8, 9)
}

/** Immediately disables and locks Protect from Melee, Missiles, and Magic for eight game cycles. */
public fun Player.disableProtectionPrayers(): Unit = ProtectionPrayerLock.apply(this)
