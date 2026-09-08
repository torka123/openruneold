@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.pkkits

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.inv.InvObj

/** Builds the complete post-load state without mutating live inventories. */
@Singleton
public class KitBankPlanner @Inject constructor(private val itemPolicy: KitItemPolicy) {
    public fun plan(
        definition: KitDefinition,
        currentInventory: Array<InvObj?>,
        currentEquipment: Array<InvObj?>,
        currentBank: Array<InvObj?>,
        bankCapacity: Int,
    ): KitPlanResult {
        if (bankCapacity !in 1..currentBank.size) {
            return KitPlanResult.Failure(listOf("Invalid bank capacity $bankCapacity."))
        }
        val bank = copy(currentBank)
        val reusableFreeNeeds = mutableMapOf<ObjKey, Int>()
        for (item in definition.inventory + definition.equipment) {
            if (itemPolicy.access(definition, item) == KitItemAccess.FREE) {
                val key = ObjKey(item.id, item.vars)
                reusableFreeNeeds[key] =
                    safeAdd(reusableFreeNeeds[key] ?: 0, item.count)
                        ?: return KitPlanResult.Failure(listOf("Free-item quantity overflow."))
            }
        }

        for (obj in currentInventory.filterNotNull() + currentEquipment.filterNotNull()) {
            val key = ObjKey(obj.id, obj.vars)
            val reused = minOf(obj.count, reusableFreeNeeds[key] ?: 0)
            reusableFreeNeeds[key] = (reusableFreeNeeds[key] ?: 0) - reused
            val toBank = obj.count - reused
            if (toBank == 0) continue
            val failure = addToBank(bank, bankCapacity, InvObj(obj.id, toBank, obj.vars))
            if (failure != null) return KitPlanResult.Failure(listOf(failure))
        }

        val missing = linkedMapOf<String, Int>()
        val forbidden = mutableListOf<String>()
        val finalInventory = arrayOfNulls<InvObj>(currentInventory.size)
        val finalEquipment = arrayOfNulls<InvObj>(currentEquipment.size)

        fun place(item: KitItem, target: Array<InvObj?>) {
            when (itemPolicy.access(definition, item)) {
                KitItemAccess.FORBIDDEN -> forbidden += item.internalName
                KitItemAccess.OWNED -> {
                    val removed = removeFromBank(bank, bankCapacity, item.id, item.vars, item.count)
                    if (removed != item.count) {
                        missing[item.internalName] =
                            (missing[item.internalName] ?: 0) + item.count - removed
                        if (removed > 0)
                            addToBank(bank, bankCapacity, InvObj(item.id, removed, item.vars))
                        return
                    }
                    target[item.slot] = item.obj
                }
                KitItemAccess.FREE -> {
                    // Matching held copies were reserved before banking. Existing bank stock is
                    // deliberately not consumed; only any remaining deficit is freely supplied.
                    target[item.slot] = item.obj
                }
            }
        }

        definition.inventory.forEach { place(it, finalInventory) }
        definition.equipment.forEach { place(it, finalEquipment) }

        if (forbidden.isNotEmpty()) {
            return KitPlanResult.Failure(
                forbidden.distinct().map { "$it is not permitted by this kit's spawn policy." }
            )
        }
        if (missing.isNotEmpty()) {
            return KitPlanResult.Failure(
                missing.map { (key, count) ->
                    "Missing $count x $key in inventory, equipment, or bank."
                }
            )
        }
        return KitPlanResult.Success(
            KitInventoryPlan(inventory = finalInventory, equipment = finalEquipment, bank = bank)
        )
    }

    private fun addToBank(bank: Array<InvObj?>, capacity: Int, obj: InvObj): String? {
        if (obj.count <= 0) return "Cannot bank a non-positive item quantity."
        val existing =
            (0 until capacity).firstOrNull { slot ->
                val other = bank[slot]
                other != null && other.id == obj.id && other.vars == obj.vars
            }
        if (existing != null) {
            val current = bank[existing]!!
            val combined =
                safeAdd(current.count, obj.count)
                    ?: return "Item quantity overflow while banking object ${obj.id}."
            bank[existing] = InvObj(current.id, combined, current.vars)
            return null
        }
        val empty =
            (0 until capacity).firstOrNull { bank[it] == null }
                ?: return "Your bank does not have enough free slots for the current setup."
        bank[empty] = InvObj(obj)
        return null
    }

    private fun removeFromBank(
        bank: Array<InvObj?>,
        capacity: Int,
        id: Int,
        vars: Int,
        requested: Int,
    ): Int {
        var remaining = requested
        for (slot in 0 until capacity) {
            val obj = bank[slot] ?: continue
            if (obj.id != id || obj.vars != vars) continue
            val take = minOf(remaining, obj.count)
            val next = obj.count - take
            bank[slot] = if (next == 0) null else InvObj(id, next, vars)
            remaining -= take
            if (remaining == 0) break
        }
        return requested - remaining
    }

    private fun safeAdd(a: Int, b: Int): Int? {
        val sum = a.toLong() + b.toLong()
        return sum.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
    }

    private fun copy(source: Array<InvObj?>): Array<InvObj?> =
        Array(source.size) { source[it]?.let(::InvObj) }

    private data class ObjKey(val id: Int, val vars: Int)
}

public data class KitInventoryPlan(
    val inventory: Array<InvObj?>,
    val equipment: Array<InvObj?>,
    val bank: Array<InvObj?>,
)

public sealed interface KitPlanResult {
    public data class Success(val plan: KitInventoryPlan) : KitPlanResult

    public data class Failure(val reasons: List<String>) : KitPlanResult
}
