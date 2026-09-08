package org.rsmod.content.slayer

import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.content.slayer.core.MortimerAssignment
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry
import org.rsmod.content.slayer.core.MortimerModifiers
import org.rsmod.content.slayer.core.SlayerAssignmentChoices
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.KonarSlayerDialogueHelpers

object SlayerTaskChoiceInterface {

    const val INTERFACE = "interface.slayer_task_choice"
    const val CONTENT = "component.slayer_task_choice:content"
    const val CLOSE = "component.slayer_task_choice:close_button"
    const val UNIVERSE = "component.slayer_task_choice:universe"

    private const val CHILDREN_PER_CHOICE = 10
    private const val MAX_CHOICES = SlayerAssignmentChoices.CHOICE_COUNT

    fun open(access: ProtectedAccess) {
        val master = SlayerAssignmentChoices.pendingMaster(access.player)
        if (master == null || !SlayerAssignmentChoices.hasValidStoredOffers(access, master)) {
            SlayerAssignmentChoices.clearOffers(access)
            access.ifClose()
            return
        }
        access.ifOpenMainModal(INTERFACE)
        enableClickEvents(access)
    }

    fun onContentClick(access: ProtectedAccess, comsub: Int) {
        val master = SlayerAssignmentChoices.pendingMaster(access.player)
        if (master == null || !SlayerAssignmentChoices.hasValidStoredOffers(access, master)) {
            SlayerAssignmentChoices.clearOffers(access)
            access.ifClose()
            return
        }
        val offers = SlayerAssignmentChoices.readStoredOffers(access, master)
        val slot = resolveContentSlot(comsub, offers.size) ?: return
        val result = SlayerAssignmentChoices.acceptSlot(access, slot)
        when (result.status) {
            SlayerAssignmentChoices.AcceptStatus.Accepted -> {
                access.ifClose()
                val acceptedMaster = requireNotNull(result.master)
                val offer = requireNotNull(result.offer)
                access.mes(assignmentMessage(access, acceptedMaster.masterId, offer))
                if (MortimerAssignment.isMortimer(acceptedMaster) && offer.modifier != null) {
                    access.mes("Mortifier: ${MortimerModifiers.label(offer.modifier)}.")
                }
            }
            SlayerAssignmentChoices.AcceptStatus.InvalidSlot -> Unit
            SlayerAssignmentChoices.AcceptStatus.NoPendingChoice,
            SlayerAssignmentChoices.AcceptStatus.ActiveTask,
            SlayerAssignmentChoices.AcceptStatus.InvalidSession -> access.ifClose()
        }
    }

    fun onClose(access: ProtectedAccess) {
        // Closing intentionally preserves the saved offers so reopening cannot reroll them.
        access.ifClose()
    }

    private fun assignmentMessage(
        access: ProtectedAccess,
        masterId: Int,
        offer: SlayerAssignmentChoices.Offer,
    ): String {
        val taskName =
            CanonicalSlayerRegistry.get(offer.categoryId)?.displayName ?: offer.masterTask.task.nameUppercase
        val base = "Your new task is to kill ${offer.amount} $taskName"
        return when (masterId) {
            7 -> "$base in the Wilderness."
            8 -> {
                val area =
                    KonarSlayerDialogueHelpers.currentArea(access.player)
                        ?.let(KonarSlayerDialogueHelpers::areaShortName)
                if (area == null) "$base." else "$base in $area."
            }
            else -> "$base."
        }
    }

    private fun enableClickEvents(access: ProtectedAccess) {
        val allChoiceChildren = 0 until (CHILDREN_PER_CHOICE * MAX_CHOICES)
        access.player.ifSetEvents(CONTENT, allChoiceChildren, IfEvent.PauseButton, IfEvent.Op1)
    }

    internal fun resolveContentSlot(comsub: Int, offerCount: Int): Int? {
        val validChildren = 0 until (offerCount * CHILDREN_PER_CHOICE)
        if (comsub !in validChildren) return null
        return comsub / CHILDREN_PER_CHOICE
    }
}
