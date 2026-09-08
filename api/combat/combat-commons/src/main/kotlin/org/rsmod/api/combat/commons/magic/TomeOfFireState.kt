package org.rsmod.api.combat.commons.magic

import dev.openrune.util.Wearpos
import kotlin.math.min
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/**
 * Persistent, per-item state for the Tome of fire.
 *
 * The normal inventory serializer already saves [InvObj.vars], so charges follow the exact tome
 * through inventory, equipment, bank, logout and normal death-item movement. Bits 0..14 hold the
 * remaining casts and bit 15 records whether those casts came from searing pages.
 */
public object TomeOfFireState {
    public const val CHARGED_ID: Int = 20714
    public const val EMPTY_ID: Int = 20716
    public const val BURNT_PAGE_ID: Int = 20718
    public const val BATTLE_ROYALE_ID: Int = 27358
    public const val SEARING_PAGE_ID: Int = 28931

    public const val CHARGES_PER_PAGE: Int = 20
    public const val MAX_PAGES: Int = 1_000
    public const val MAX_CHARGES: Int = MAX_PAGES * CHARGES_PER_PAGE

    private const val CHARGE_MASK: Int = 0x7fff
    private const val SEARING_MASK: Int = 1 shl 15

    public enum class PageType(public val itemId: Int, public val displayName: String) {
        Burnt(BURNT_PAGE_ID, "burnt pages"),
        Searing(SEARING_PAGE_ID, "searing pages");

        public companion object {
            public fun fromItemId(itemId: Int): PageType? =
                entries.firstOrNull { it.itemId == itemId }
        }
    }

    public sealed interface AddPagesResult {
        public data class Success(
            public val pagesAdded: Int,
            public val chargesAdded: Int,
            public val chargesTotal: Int,
        ) : AddPagesResult

        public data object InvalidTome : AddPagesResult

        public data object Full : AddPagesResult

        public data class MixedPageType(public val current: PageType) : AddPagesResult
    }

    public data class RemovedPages(
        public val pageType: PageType,
        public val pagesReturned: Int,
        public val partialPageChargesLost: Int,
    )

    /**
     * A cache-spawned charged tome from before charge-state support has no packed vars. Since a
     * legitimately depleted tome is always transformed to [EMPTY_ID], a zero-var [CHARGED_ID]
     * safely identifies legacy state and is migrated as a full burnt-page tome on first mutation.
     */
    public fun charges(tome: InvObj?): Int =
        when (tome?.id) {
            BATTLE_ROYALE_ID -> MAX_CHARGES
            CHARGED_ID -> rawCharges(tome).takeIf { it > 0 } ?: MAX_CHARGES
            else -> 0
        }

    public fun pageType(tome: InvObj?): PageType? =
        when {
            !isActive(tome) -> null
            tome?.id == BATTLE_ROYALE_ID -> PageType.Burnt
            checkNotNull(tome).vars and SEARING_MASK != 0 -> PageType.Searing
            else -> PageType.Burnt
        }

    public fun isActive(tome: InvObj?): Boolean =
        tome?.id == BATTLE_ROYALE_ID || (tome?.id == CHARGED_ID && charges(tome) > 0)

    public fun isSearing(tome: InvObj?): Boolean =
        tome?.id == CHARGED_ID && charges(tome) > 0 && pageType(tome) == PageType.Searing

    public fun loadedPages(tome: InvObj?): Int =
        (charges(tome) + CHARGES_PER_PAGE - 1) / CHARGES_PER_PAGE

    public fun availablePageCapacity(tome: InvObj?): Int =
        (MAX_PAGES - loadedPages(tome)).coerceAtLeast(0)

    public fun addPages(
        inventory: Inventory,
        tomeSlot: Int,
        pageType: PageType,
        offeredPages: Int,
    ): AddPagesResult {
        val tome = inventory[tomeSlot] ?: return AddPagesResult.InvalidTome
        if (tome.id != CHARGED_ID && tome.id != EMPTY_ID) {
            return AddPagesResult.InvalidTome
        }
        val currentCharges = charges(tome)
        val currentType = this.pageType(tome)
        if (currentCharges > 0 && currentType != pageType) {
            return AddPagesResult.MixedPageType(checkNotNull(currentType))
        }
        val pagesAdded = min(offeredPages.coerceAtLeast(0), availablePageCapacity(tome))
        if (pagesAdded == 0) {
            return AddPagesResult.Full
        }
        val chargesAdded = pagesAdded * CHARGES_PER_PAGE
        val total = currentCharges + chargesAdded
        inventory[tomeSlot] = InvObj("obj.tome_of_fire", vars = pack(total, pageType))
        return AddPagesResult.Success(pagesAdded, chargesAdded, total)
    }

    /** Consumes exactly one eligible combat-spell charge from the equipped normal tome. */
    public fun consumeEligibleCast(player: Player): Boolean {
        val slot = Wearpos.LeftHand.slot
        val tome = player.worn[slot] ?: return false
        if (tome.id == BATTLE_ROYALE_ID) {
            return true
        }
        if (tome.id != CHARGED_ID) {
            return false
        }
        val remaining = charges(tome) - 1
        player.worn[slot] =
            if (remaining <= 0) {
                InvObj("obj.tome_of_fire_uncharged")
            } else {
                tome.copy(vars = pack(remaining, checkNotNull(pageType(tome))))
            }
        return true
    }

    public fun removeAllPages(inventory: Inventory, tomeSlot: Int): RemovedPages? {
        val tome = inventory[tomeSlot] ?: return null
        if (tome.id != CHARGED_ID) {
            return null
        }
        val currentCharges = charges(tome)
        val type = pageType(tome) ?: return null
        val returned = currentCharges / CHARGES_PER_PAGE
        val lost = currentCharges % CHARGES_PER_PAGE
        inventory[tomeSlot] = InvObj("obj.tome_of_fire_uncharged")
        return RemovedPages(type, returned, lost)
    }

    public fun packed(charges: Int, pageType: PageType): Int = pack(charges, pageType)

    private fun rawCharges(tome: InvObj): Int = tome.vars and CHARGE_MASK

    private fun pack(charges: Int, pageType: PageType): Int {
        require(charges in 0..MAX_CHARGES) { "Invalid Tome of fire charges: $charges" }
        val pageBits = if (pageType == PageType.Searing) SEARING_MASK else 0
        return (charges and CHARGE_MASK) or pageBits
    }
}
