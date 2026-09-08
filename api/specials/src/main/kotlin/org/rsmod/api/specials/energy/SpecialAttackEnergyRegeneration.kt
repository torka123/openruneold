package org.rsmod.api.specials.energy

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.player.ring
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

/** Owns the recurring special-attack energy clock and its intentional pause state. */
public object SpecialAttackEnergyRegeneration {
    public const val TIMER: String = "timer.spec_regen"
    public const val NORMAL_INTERVAL_TICKS: Int = constants.spec_regen_interval
    public const val LIGHTBEARER_INTERVAL_TICKS: Int = constants.spec_regen_interval / 2

    /** Starts a fresh, full regeneration interval using the player's currently worn ring. */
    public fun restart(player: Player) {
        restart(player, player.ring.isType("obj.lightbearer"))
    }

    internal fun restart(player: Player, hasLightbearer: Boolean) {
        player.attr.remove(PAUSED)
        schedule(player, interval(hasLightbearer, intervalDivisor(player)))
    }

    /** Stops regeneration until [restart] is called explicitly. */
    public fun pause(player: Player) {
        player.attr[PAUSED] = true
        player.attr.remove(NEXT_REGEN_CYCLE)
        player.attr.remove(SCHEDULED_INTERVAL)
        player.clearSoftTimer(TIMER)
    }

    /** Re-evaluates the interval after an equipment change without overriding a content pause. */
    public fun refreshInterval(player: Player) {
        if (isPaused(player)) return
        val desired = interval(player)
        val scheduled = player.attr[SCHEDULED_INTERVAL]
        val remaining = player.attr[NEXT_REGEN_CYCLE]?.minus(player.currentMapClock)
        if (shouldResetAfterIntervalChange(scheduled, desired, remaining)) {
            schedule(player, desired)
        }
    }

    public fun isPaused(player: Player): Boolean = player.attr[PAUSED] == true

    public fun interval(player: Player): Int =
        interval(player.ring.isType("obj.lightbearer"), intervalDivisor(player))

    public fun setIntervalDivisor(player: Player, divisor: Int) {
        require(divisor > 0) { "Special-energy regeneration interval divisor must be positive." }
        if (divisor == DEFAULT_INTERVAL_DIVISOR) {
            clearIntervalDivisor(player)
            return
        }
        if (player.attr[INTERVAL_DIVISOR] == divisor) return
        player.attr[INTERVAL_DIVISOR] = divisor
        refreshInterval(player)
    }

    public fun clearIntervalDivisor(player: Player) {
        if (player.attr[INTERVAL_DIVISOR] == null) return
        player.attr.remove(INTERVAL_DIVISOR)
        refreshInterval(player)
    }

    public fun intervalDivisor(player: Player): Int =
        player.attr[INTERVAL_DIVISOR] ?: DEFAULT_INTERVAL_DIVISOR

    internal fun interval(hasLightbearer: Boolean): Int =
        interval(hasLightbearer, DEFAULT_INTERVAL_DIVISOR)

    internal fun interval(hasLightbearer: Boolean, intervalDivisor: Int): Int {
        require(intervalDivisor > 0) {
            "Special-energy regeneration interval divisor must be positive."
        }
        val base = if (hasLightbearer) LIGHTBEARER_INTERVAL_TICKS else NORMAL_INTERVAL_TICKS
        return ((base.toLong() + intervalDivisor - 1L) / intervalDivisor)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    internal fun shouldResetAfterRingChange(lightbearer: Boolean, remainingTicks: Int?): Boolean =
        !lightbearer ||
            remainingTicks == null ||
            remainingTicks <= 0 ||
            remainingTicks > LIGHTBEARER_INTERVAL_TICKS

    internal fun shouldResetAfterIntervalChange(
        scheduledInterval: Int?,
        desiredInterval: Int,
        remainingTicks: Int?,
    ): Boolean {
        require(desiredInterval > 0) { "Desired special-energy interval must be positive." }
        if (scheduledInterval == null || remainingTicks == null || remainingTicks <= 0) return true
        if (desiredInterval > scheduledInterval) return true
        return desiredInterval < scheduledInterval && remainingTicks > desiredInterval
    }

    private fun schedule(player: Player, interval: Int) {
        player.attr[NEXT_REGEN_CYCLE] = player.currentMapClock + interval
        player.attr[SCHEDULED_INTERVAL] = interval
        player.softTimer(TIMER, interval)
    }

    private const val DEFAULT_INTERVAL_DIVISOR: Int = 1
    private val PAUSED: AttributeKey<Boolean> = AttributeKey(temp = true)
    private val NEXT_REGEN_CYCLE: AttributeKey<Int> = AttributeKey(temp = true)
    private val SCHEDULED_INTERVAL: AttributeKey<Int> = AttributeKey(temp = true)
    private val INTERVAL_DIVISOR: AttributeKey<Int> = AttributeKey(resetOnDeath = true, temp = true)
}
