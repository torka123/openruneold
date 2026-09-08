package org.rsmod.content.slayer.core

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.slayer.SlayerMasterTaskRow
import org.rsmod.api.table.slayer.SlayerMastersRow
import org.rsmod.game.entity.Player

/**
 * Durable, server-authoritative task choices shared by every Slayer master.
 *
 * The client only renders the saved varbits. Every click is resolved back through the saved master
 * and that master's cache-backed task rows before an assignment is applied.
 */
object SlayerAssignmentChoices {

    const val CHOICE_COUNT = 3

    private const val VAR_CHOOSE_TASK_1 = "varbit.slayer_choose_task_1"
    private const val VAR_CHOOSE_TASK_2 = "varbit.slayer_choose_task_2"
    private const val VAR_CHOOSE_TASK_3 = "varbit.slayer_choose_task_3"
    private const val VAR_CHOOSE_MOD_ID_1 = "varbit.slayer_choose_task_1_modifier_id"
    private const val VAR_CHOOSE_MOD_ID_2 = "varbit.slayer_choose_task_2_modifier_id"
    private const val VAR_CHOOSE_MOD_ID_3 = "varbit.slayer_choose_task_3_modifier_id"
    private const val VAR_CHOOSE_MOD_VAL_1 = "varbit.slayer_choose_task_1_modifier_value"
    private const val VAR_CHOOSE_MOD_VAL_2 = "varbit.slayer_choose_task_2_modifier_value"
    private const val VAR_CHOOSE_MOD_VAL_3 = "varbit.slayer_choose_task_3_modifier_value"
    private const val VAR_CHOOSE_MOD_NEG_1 = "varbit.slayer_choose_task_1_modifier_negative"
    private const val VAR_CHOOSE_MOD_NEG_2 = "varbit.slayer_choose_task_2_modifier_negative"
    private const val VAR_CHOOSE_MOD_NEG_3 = "varbit.slayer_choose_task_3_modifier_negative"
    private const val VAR_CHOOSE_BOSS_1 = "varbit.slayer_choose_task_1_boss_id"
    private const val VAR_CHOOSE_BOSS_2 = "varbit.slayer_choose_task_2_boss_id"
    private const val VAR_CHOOSE_BOSS_3 = "varbit.slayer_choose_task_3_boss_id"
    private const val VAR_CHOOSE_AMOUNT_1 = "varbit.slayer_choose_amount_1"
    private const val VAR_CHOOSE_AMOUNT_2 = "varbit.slayer_choose_amount_2"
    private const val VAR_CHOOSE_AMOUNT_3 = "varbit.slayer_choose_amount_3"
    private const val VAR_CHOOSE_MASTER = "varbit.slayer_choose_rolled_master"

    data class Offer(
        val masterTask: SlayerMasterTaskRow,
        val categoryId: Int = 0,
        val amount: Int,
        val modifier: MortimerModifiers.RolledModifier? = null,
    )

    enum class AcceptStatus {
        Accepted,
        NoPendingChoice,
        ActiveTask,
        InvalidSession,
        InvalidSlot,
    }

    data class AcceptResult(
        val status: AcceptStatus,
        val master: SlayerMastersRow? = null,
        val offer: Offer? = null,
    )

    fun hasPendingChoice(player: Player): Boolean =
        player.vars[VAR_CHOOSE_MASTER] != 0 || player.vars[VAR_CHOOSE_TASK_1] != 0

    fun hasPendingChoice(access: ProtectedAccess): Boolean = hasPendingChoice(access.player)

    fun pendingMasterId(player: Player): Int = player.vars[VAR_CHOOSE_MASTER]

    fun pendingMaster(player: Player): SlayerMastersRow? =
        SlayerTaskManager.findMasterById(pendingMasterId(player))

    fun pendingMasterName(player: Player): String? {
        val master = pendingMaster(player) ?: return null
        val npcId = master.npcIds.firstOrNull()?.id ?: return null
        return ServerCacheManager.getNpc(npcId)?.name
    }

    fun rollOffers(access: ProtectedAccess, master: SlayerMastersRow): List<Offer>? {
        val eligible =
            SlayerTaskManager.rollCanonicalCategories(access, master, CanonicalSlayerRegistry.categories.size)
        val categories = selectStructuredCategories(eligible) ?: return null
        return categories.map { category ->
            val backing = SlayerTaskManager.canonicalBackingTask(category) ?: return null
            Offer(
                masterTask = backing,
                amount = CanonicalSlayerRegistry.rollAmount(category),
                categoryId = category.id,
            )
        }
    }

    fun storeOffers(access: ProtectedAccess, master: SlayerMastersRow, offers: List<Offer>): Boolean {
        if (!isStructurallyValid(master.masterId, offers)) return false
        clearOffers(access.player)
        VarPlayerIntMapSetter.set(access.player, VAR_CHOOSE_MASTER, master.masterId)
        offers.forEachIndexed { slot, offer -> writeOfferSlot(access.player, slot, offer) }
        return true
    }

    fun readStoredOffers(access: ProtectedAccess, master: SlayerMastersRow): List<Offer> {
        if (pendingMasterId(access.player) != master.masterId) return emptyList()
        return buildList {
            for (slot in 0 until CHOICE_COUNT) {
                val taskId = access.vars[chooseTaskVar(slot)]
                val categoryId = access.vars[chooseBossVar(slot)]
                val category = CanonicalSlayerRegistry.get(categoryId) ?: continue
                if (taskId != category.baseTaskId) continue
                val masterTask = SlayerTaskManager.canonicalBackingTask(category) ?: continue
                add(
                    Offer(
                        masterTask = masterTask,
                        amount = access.vars[chooseAmountVar(slot)],
                        modifier = null,
                        categoryId = categoryId,
                    ),
                )
            }
        }
    }

    fun hasValidStoredOffers(access: ProtectedAccess, master: SlayerMastersRow): Boolean {
        if (pendingMasterId(access.player) != master.masterId) return false
        val offers = readStoredOffers(access, master)
        if (!isStructurallyValid(master.masterId, offers)) return false
        if (!rawSlotsAreValid(access.player, master)) return false
        return offers.all { offer ->
            val category = CanonicalSlayerRegistry.get(offer.categoryId) ?: return@all false
            SlayerTaskManager.canAcceptCanonicalCategory(access, master, category)
        }
    }

    fun acceptSlot(access: ProtectedAccess, slot: Int): AcceptResult {
        if (!hasPendingChoice(access)) return AcceptResult(AcceptStatus.NoPendingChoice)
        if (SlayerTaskManager.getCurrentSlayerTask(access) != null) {
            clearOffers(access.player)
            return AcceptResult(AcceptStatus.ActiveTask)
        }

        val master = pendingMaster(access.player)
        if (master == null || !hasValidStoredOffers(access, master)) {
            clearOffers(access.player)
            return AcceptResult(AcceptStatus.InvalidSession)
        }
        val offers = readStoredOffers(access, master)
        val offer = offers.getOrNull(slot) ?: return AcceptResult(AcceptStatus.InvalidSlot, master)

        val category = CanonicalSlayerRegistry.get(offer.categoryId)
        if (category == null || !SlayerTaskManager.applyCanonicalAssignment(access, master, category, offer.amount)) {
            clearOffers(access.player)
            return AcceptResult(AcceptStatus.InvalidSession)
        }
        MortimerAssignment.writeActiveModifier(access, null)
        clearOffers(access.player)
        return AcceptResult(AcceptStatus.Accepted, master, offer)
    }

    fun clearOffers(access: ProtectedAccess) = clearOffers(access.player)

    fun clearOffers(player: Player) {
        for (slot in 0 until CHOICE_COUNT) {
            VarPlayerIntMapSetter.set(player, chooseTaskVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseModIdVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseModValueVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseModNegVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseBossVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseAmountVar(slot), 0)
        }
        VarPlayerIntMapSetter.set(player, VAR_CHOOSE_MASTER, 0)
    }

    internal fun isStructurallyValid(masterId: Int, offers: List<Offer>): Boolean =
        masterId > 0 &&
            offers.size == CHOICE_COUNT &&
            offers.map { if (it.categoryId > 0) it.categoryId else it.masterTask.task.id }.distinct().size == CHOICE_COUNT &&
            offers.all { offer ->
                val category = CanonicalSlayerRegistry.get(offer.categoryId)
                category != null &&
                    offer.masterTask.task.id == category.baseTaskId &&
                    offer.amount in category.minAmount..category.maxAmount
            } &&
            isStructuredSelection(offers.mapNotNull { CanonicalSlayerRegistry.get(it.categoryId) })

    internal fun isSnapshotStructurallyValid(
        pendingMasterId: Int,
        taskMasterIds: List<Int>,
        taskIds: List<Int>,
        categoryIds: List<Int>,
        amounts: List<Int>,
    ): Boolean {
        if (!CanonicalSlayerRegistry.isEnabledMaster(pendingMasterId)) return false
        if (taskMasterIds.size != CHOICE_COUNT) return false
        if (taskIds.size != CHOICE_COUNT) return false
        if (categoryIds.size != CHOICE_COUNT) return false
        if (amounts.size != CHOICE_COUNT) return false
        if (taskMasterIds.any { it != pendingMasterId }) return false
        if (categoryIds.distinct().size != CHOICE_COUNT) return false
        val categories = categoryIds.map { CanonicalSlayerRegistry.get(it) ?: return false }
        return isStructuredSelection(categories) &&
            categoryIds.indices.all { index ->
                val category = categories[index]
                taskIds[index] == category.baseTaskId &&
                    amounts[index] in category.minAmount..category.maxAmount
            }
    }

    /**
     * Keeps the established three-slot UI contract: Wilderness boss, Edgeville Dungeon, then
     * Custom Slayer Cave. The candidates arrive in weighted random order, so the first eligible
     * category in each bucket preserves the registry's configured weighting.
     */
    internal fun selectStructuredCategories(
        candidates: List<CanonicalSlayerRegistry.Category>,
    ): List<CanonicalSlayerRegistry.Category>? {
        val wildernessBoss = candidates.firstOrNull(::isWildernessBoss)
        val edgevilleDungeon =
            candidates.firstOrNull { CanonicalSlayerRegistry.Location.EdgevilleDungeon in it.locations }
        val customSlayerCave =
            candidates.firstOrNull { CanonicalSlayerRegistry.Location.CustomSlayerCave in it.locations }
        val result = listOfNotNull(wildernessBoss, edgevilleDungeon, customSlayerCave)
        return result.takeIf(::isStructuredSelection)
    }

    private fun isStructuredSelection(categories: List<CanonicalSlayerRegistry.Category>): Boolean =
        categories.size == CHOICE_COUNT &&
            categories.map(CanonicalSlayerRegistry.Category::id).distinct().size == CHOICE_COUNT &&
            isWildernessBoss(categories[0]) &&
            CanonicalSlayerRegistry.Location.EdgevilleDungeon in categories[1].locations &&
            CanonicalSlayerRegistry.Location.CustomSlayerCave in categories[2].locations

    private fun isWildernessBoss(category: CanonicalSlayerRegistry.Category): Boolean =
        category.boss &&
            category.locations.any { location ->
                location == CanonicalSlayerRegistry.Location.Wilderness ||
                    location == CanonicalSlayerRegistry.Location.KingBlackDragon ||
                    location == CanonicalSlayerRegistry.Location.CorporealBeast
            }

    private fun rawSlotsAreValid(player: Player, master: SlayerMastersRow): Boolean {
        if (!CanonicalSlayerRegistry.isEnabledMaster(master.masterId)) return false
        for (slot in 0 until CHOICE_COUNT) {
            val categoryId = player.vars[chooseBossVar(slot)]
            val category = CanonicalSlayerRegistry.get(categoryId) ?: return false
            if (player.vars[chooseTaskVar(slot)] != category.baseTaskId) return false
            if (hasRawModifier(player, slot)) return false
        }
        return true
    }

    private fun hasRawModifier(player: Player, slot: Int): Boolean =
        player.vars[chooseModIdVar(slot)] != 0 ||
            player.vars[chooseModValueVar(slot)] != 0 ||
            player.vars[chooseModNegVar(slot)] != 0

    private fun writeOfferSlot(player: Player, slot: Int, offer: Offer) {
        VarPlayerIntMapSetter.set(player, chooseTaskVar(slot), offer.masterTask.task.id)
        VarPlayerIntMapSetter.set(player, chooseAmountVar(slot), offer.amount)
        VarPlayerIntMapSetter.set(player, chooseBossVar(slot), offer.categoryId)
        val modifier = offer.modifier
        if (modifier == null) {
            VarPlayerIntMapSetter.set(player, chooseModIdVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseModValueVar(slot), 0)
            VarPlayerIntMapSetter.set(player, chooseModNegVar(slot), 0)
            return
        }
        VarPlayerIntMapSetter.set(player, chooseModIdVar(slot), modifier.id)
        VarPlayerIntMapSetter.set(player, chooseModValueVar(slot), modifier.absoluteValue)
        VarPlayerIntMapSetter.set(player, chooseModNegVar(slot), if (modifier.negative) 1 else 0)
    }

    private fun readModifier(player: Player, slot: Int): MortimerModifiers.RolledModifier? {
        val id = player.vars[chooseModIdVar(slot)]
        if (id == 0) return null
        val value = player.vars[chooseModValueVar(slot)]
        val negative = player.vars[chooseModNegVar(slot)] != 0
        return MortimerModifiers.RolledModifier(id = id, value = if (negative) -value else value)
    }

    private fun chooseTaskVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_TASK_1
            1 -> VAR_CHOOSE_TASK_2
            else -> VAR_CHOOSE_TASK_3
        }

    private fun chooseModIdVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_MOD_ID_1
            1 -> VAR_CHOOSE_MOD_ID_2
            else -> VAR_CHOOSE_MOD_ID_3
        }

    private fun chooseModValueVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_MOD_VAL_1
            1 -> VAR_CHOOSE_MOD_VAL_2
            else -> VAR_CHOOSE_MOD_VAL_3
        }

    private fun chooseModNegVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_MOD_NEG_1
            1 -> VAR_CHOOSE_MOD_NEG_2
            else -> VAR_CHOOSE_MOD_NEG_3
        }

    private fun chooseBossVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_BOSS_1
            1 -> VAR_CHOOSE_BOSS_2
            else -> VAR_CHOOSE_BOSS_3
        }

    private fun chooseAmountVar(slot: Int): String =
        when (slot) {
            0 -> VAR_CHOOSE_AMOUNT_1
            1 -> VAR_CHOOSE_AMOUNT_2
            else -> VAR_CHOOSE_AMOUNT_3
        }

    private const val MAX_SAVED_AMOUNT = 1_023
}
