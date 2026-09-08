package org.rsmod.content.bosses.doom

import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/**
 * Persistent per-item Eye of ayak charge state.
 *
 * Bits 0..15 hold charges (0..50,000); bits 16..17 identify empty, demon-tear or rune mode. The
 * inventory serializer already persists InvObj.vars, so this state follows the exact Eye item.
 */
internal object DoomEyeState {
    const val CHARGED_ID = 31113
    const val UNCHARGED_ID = 31115
    const val MAX_CHARGES = 50_000

    private const val CHARGE_MASK = 0xffff
    private const val MODE_SHIFT = 16
    private const val MODE_MASK = 0x3 shl MODE_SHIFT
    private const val MODE_EMPTY = 0
    private const val MODE_TEARS = 1
    private const val MODE_RUNES = 2

    fun charges(eye: InvObj?): Int = eye?.vars?.and(CHARGE_MASK) ?: 0

    fun isTearMode(eye: InvObj?): Boolean = eye?.id == CHARGED_ID && mode(eye) == MODE_TEARS

    fun isRuneMode(eye: InvObj?): Boolean = eye?.id == CHARGED_ID && mode(eye) == MODE_RUNES

    /**
     * Adds the passive floor reward to at most one inventory Eye. Rune-charged Eyes are ineligible.
     * Returns the number actually added; no message is emitted by design.
     */
    fun addPassiveCharges(inventory: Inventory, amount: Int): Int {
        require(amount >= 0)
        if (amount == 0) return 0
        val slot =
            inventory.indices.firstOrNull { index ->
                val eye = inventory[index]
                eye?.id == UNCHARGED_ID || isTearMode(eye)
            } ?: return 0
        val eye = checkNotNull(inventory[slot])
        val current = charges(eye)
        val total = (current + amount).coerceAtMost(MAX_CHARGES)
        val added = total - current
        if (added <= 0) return 0
        inventory[slot] = InvObj("obj.eye_of_ayak", vars = pack(total, MODE_TEARS))
        return added
    }

    internal fun packTearCharges(charges: Int): Int = pack(charges, MODE_TEARS)

    internal fun packRuneCharges(charges: Int): Int = pack(charges, MODE_RUNES)

    private fun mode(eye: InvObj): Int = (eye.vars and MODE_MASK) ushr MODE_SHIFT

    private fun pack(charges: Int, mode: Int): Int {
        require(charges in 0..MAX_CHARGES)
        require(mode in MODE_EMPTY..MODE_RUNES)
        return (charges and CHARGE_MASK) or (mode shl MODE_SHIFT)
    }
}
