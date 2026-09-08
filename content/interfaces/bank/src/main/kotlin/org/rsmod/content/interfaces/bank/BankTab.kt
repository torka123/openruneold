package org.rsmod.content.interfaces.bank

import kotlin.math.max
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.vars.VarEnumDelegate
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

enum class BankTab(val index: Int, val sizeVarBit: String, override val varValue: Int) :
    VarEnumDelegate {
    Tab1(0, "varbit.bank_tab_1", varValue = 1),
    Tab2(1, "varbit.bank_tab_2", varValue = 2),
    Tab3(2, "varbit.bank_tab_3", varValue = 3),
    Tab4(3, "varbit.bank_tab_4", varValue = 4),
    Tab5(4, "varbit.bank_tab_5", varValue = 5),
    Tab6(5, "varbit.bank_tab_6", varValue = 6),
    Tab7(6, "varbit.bank_tab_7", varValue = 7),
    Tab8(7, "varbit.bank_tab_8", varValue = 8),
    Tab9(8, "varbit.bank_tab_9", varValue = 9),
    Main(9, "varbit.bank_tab_main", varValue = 0);

    val isMainTab: Boolean
        get() = this == Main

    fun firstSlot(access: ProtectedAccess): Int {
        val indexRange = 0 until index
        return indexRange.sumOf {
            val tab = entries[it]
            access.vars[tab.sizeVarBit]
        }
    }

    fun slotRange(access: ProtectedAccess): IntRange {
        val firstSlot = firstSlot(access)
        val occupiedSpace = occupiedSpace(access)
        return firstSlot until firstSlot + occupiedSpace
    }

    fun occupiedSpace(access: ProtectedAccess): Int = access.vars[sizeVarBit]

    fun isEmpty(access: ProtectedAccess): Boolean = occupiedSpace(access) == 0

    fun decreaseSize(access: ProtectedAccess, amount: Int = 1) {
        val size = access.vars[sizeVarBit]
        access.vars[sizeVarBit] = max(0, size - amount)
        assert(size >= amount) {
            "Decreased tab size with an amount higher than capacity: decrease=$amount, size=$size"
        }
    }

    fun increaseSize(access: ProtectedAccess, amount: Int = 1) {
        access.vars[sizeVarBit] += amount
    }

    companion object {
        init {
            val sorted = entries.sortedBy(BankTab::index)
            check(sorted == entries) { "Entries must be sorted by `index`." }
        }

        val tabs = entries - Main

        fun forIndex(index: Int): BankTab? = entries.getOrNull(index)

        fun forSlot(access: ProtectedAccess, slot: Int): BankTab? {
            var currSlot = 0
            for (tab in entries) {
                val size = access.vars[tab.sizeVarBit]
                if (slot in currSlot until currSlot + size) {
                    return tab
                }
                currSlot += size
            }
            return null
        }

        /**
         * Recovers bank tab bookkeeping when items were inserted directly into the bank inventory.
         *
         * Bank tabs own contiguous slot ranges. A direct inventory transaction can place an item
         * beyond those ranges without updating the Main tab's size, which makes later withdrawals
         * fail because the slot has no tab association. Existing tab ranges are preserved and only
         * the Main tab is extended to cover the unowned tail.
         */
        fun repairLayout(player: Player, bank: Inventory, minimumSlots: Int = 0): Boolean {
            val requiredSlots = max(bank.lastOccupiedSlot(), minimumSlots)
            val allocatedSlots = entries.sumOf { player.vars[it.sizeVarBit] }
            if (requiredSlots <= allocatedSlots) {
                return false
            }

            val missingSlots = requiredSlots - allocatedSlots
            val mainSize = player.vars[Main.sizeVarBit]
            VarPlayerIntMapSetter.set(player, Main.sizeVarBit, mainSize + missingSlots)
            return true
        }
    }
}
