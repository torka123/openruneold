package org.rsmod.content.slayer.dialogue

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.slayer.SlayerTaskChoiceInterface
import org.rsmod.content.slayer.core.SlayerAssignmentChoices
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.GenericDialogue.slayerTip

object SlayerAssignmentDialogue {

    private const val VARP_LAST_MASTER = "varp.slayer_last_master"
    private const val VARP_LAST_AREA = "varp.slayer_last_area"
    private const val KONAR_MASTER_ID = 8

    private fun ProtectedAccess.slayerCount(): Int = vars["varp.slayer_count"]

    suspend fun Dialogue.assignNewTask(masterNpcId: String) {
        assignNewTask(masterNpcId) { taskName, count ->
            defaultAssignedDialogue(taskName, count)
        }
    }

    suspend fun Dialogue.assignNewTask(
        masterNpcId: String,
        onAssigned: suspend Dialogue.(taskName: String, count: Int) -> Unit,
    ) {
        if (reopenPendingChoices(masterNpcId)) return
        val capeOffer = SlayerTaskManager.rollCapePerkOffer(access, masterNpcId)
        if (capeOffer != null) {
            offerCapePerk(masterNpcId, capeOffer.taskName, onAssigned)
            return
        }
        assignRandomTask(masterNpcId, onAssigned)
    }

    private suspend fun Dialogue.offerCapePerk(
        masterNpcId: String,
        previousTaskName: String,
        onAssigned: suspend Dialogue.(taskName: String, count: Int) -> Unit,
    ) {
        val locationSuffix =
            if (access.vars[VARP_LAST_MASTER] == KONAR_MASTER_ID && access.vars[VARP_LAST_AREA] != 0) {
                " at the same location"
            } else {
                ""
            }
        val capeMessage =
            if (masterNpcId == "npc.slayer_master_8") {
                val area =
                    if (access.vars[VARP_LAST_AREA] != 0) {
                        KonarSlayerDialogueHelpers.findArea(access.vars[VARP_LAST_AREA])
                            ?.let { " in ${KonarSlayerDialogueHelpers.areaShortName(it)}" }
                            .orEmpty()
                    } else {
                        ""
                    }
                "You're wearing a Slayer cape. Would you like me to assign you the same task as last time ($previousTaskName)$area?"
            } else {
                "You're wearing a Slayer cape. Would you like me to assign you the same task as last time ($previousTaskName)$locationSuffix?"
            }
        chatNpc(neutral, capeMessage)
        when (
            choice2(
                "Yes please.",
                1,
                "No thanks, I'd like a new task.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Yes please.")
                when (SlayerTaskManager.assignPreviousTask(access, masterNpcId)) {
                    SlayerTaskManager.AssignPreviousResult.Success -> {
                        val task = SlayerTaskManager.getCurrentSlayerTask(access)
                        if (task != null) {
                            onAssigned(task.nameUppercase, access.slayerCount())
                        }
                    }
                    SlayerTaskManager.AssignPreviousResult.BlockedPrevious ->
                        assignRandomTask(masterNpcId, onAssigned)
                    SlayerTaskManager.AssignPreviousResult.Failed -> assignRandomTask(masterNpcId, onAssigned)
                }
            }
            2 -> {
                chatPlayer(neutral, "No thanks, I'd like a new task.")
                assignRandomTask(masterNpcId, onAssigned)
            }
        }
    }

    private suspend fun Dialogue.reopenPendingChoices(masterNpcId: String): Boolean {
        if (!SlayerAssignmentChoices.hasPendingChoice(access)) return false
        val requestedMaster = SlayerTaskManager.findMasterByNpc(masterNpcId)
        val pendingMaster = SlayerAssignmentChoices.pendingMaster(access.player)
        if (
            requestedMaster != null &&
                pendingMaster?.masterId == requestedMaster.masterId &&
                SlayerAssignmentChoices.hasValidStoredOffers(access, pendingMaster)
        ) {
            chatNpc(neutral, "Your assignment choices are still waiting. Take your pick.")
            SlayerTaskChoiceInterface.open(access)
            return true
        }

        val masterName = SlayerAssignmentChoices.pendingMasterName(access.player) ?: "another Slayer master"
        chatNpc(
            neutral,
            "You already have assignment choices waiting from $masterName. Pick one of those before asking me for another roll.",
        )
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun Dialogue.assignRandomTask(
        masterNpcId: String,
        onAssigned: suspend Dialogue.(taskName: String, count: Int) -> Unit,
    ) {
        val master =
            SlayerTaskManager.findMasterByNpc(masterNpcId) ?: run {
                chatNpc(neutral, "I can't assign you a task right now.")
                return
            }
        val offers = SlayerAssignmentChoices.rollOffers(access, master)
        if (offers == null || !SlayerAssignmentChoices.storeOffers(access, master, offers)) {
            chatNpc(neutral, SlayerTaskManager.assignmentUnavailableMessage(access, master))
            return
        }
        chatNpc(neutral, "Choose one of these assignments.")
        SlayerTaskChoiceInterface.open(access)
    }

    private suspend fun Dialogue.defaultAssignedDialogue(taskName: String, count: Int) {
        chatNpc(neutral, "Excellent, you're doing great. Your new task is to kill $count $taskName.")
        when (choice2("Got any tips for me?", 1, "Okay, great!", 2)) {
            1 -> {
                chatPlayer(quiz, "Got any tips for me?")
                slayerTip()
            }
            2 -> {
                chatPlayer(happy, "Okay, great!")
                chatNpc(neutral, "Good luck! Don't forget to come back when you need a new assignment.")
            }
        }
    }
}
