package org.rsmod.content.slayer.dialogue.masters

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.slayer.slayerTuraelIntroComplete
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.GenericDialogue.slayerTip
import org.rsmod.content.slayer.dialogue.StandardSlayerDialogue.openMain
import org.rsmod.content.slayer.dialogue.SlayerAssignmentDialogue.assignNewTask

object TuradelDialogue {

    val turaelNpcId = org.rsmod.content.slayer.dialogue.SlayerMasters.Npc.turael

    suspend fun Dialogue.start() {
        if (
            SlayerTaskManager.getCurrentSlayerTask(access) == null &&
                !access.player.slayerTuraelIntroComplete
        ) {
            firstTime()
        } else {
            openMain(turaelNpcId)
        }
    }

    private suspend fun Dialogue.firstTime() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(neutral, "I'm one of the elite Slayer Masters.")
        when (choice2("What's a slayer?", 1, "Never heard of you...", 2)) {
            1 -> {
                chatPlayer(quiz, "What's a slayer?")
                chatNpc(neutral, "Oh dear, what do they teach you in school?")
                chatPlayer(confused, "Well... er...")
                chatNpc(
                    neutral,
                    "I suppose I'll have to educate you then. A slayer is someone who is trained to fight specific creatures. They know these creatures' every weakness and strength. As you can guess it makes killing them a lot easier.",
                )
                startLearning()
                access.player.slayerTuraelIntroComplete = true
            }
            2 -> startLearning()
        }
    }

    private suspend fun Dialogue.startLearning() {
        chatPlayer(neutral, "Never heard of you...")
        chatNpc(neutral, "That's because my foe never lives to tell of me. We slayers are a dangerous bunch.")
        when (choice2("Wow, can you teach me?", 1, "Sounds useless to me.", 2)) {
            1 -> {
                chatPlayer(happy, "Wow, can you teach me?")
                chatNpc(neutral, "Hmmm well I'm not so sure...")
                chatPlayer(sad, "Pleeeaasssse!")
                chatNpc(
                    neutral,
                    "Oh okay then, you twisted my arm. You'll have to train against specific groups of creatures.",
                )
                chatPlayer(quiz, "Okay, what's first?")
                chatNpc(
                    neutral,
                    "Choose one of the three assignments I offer you. Once you pick one, those creatures will become your Slayer task.",
                )
                chatNpc(
                    neutral,
                    "You'll also need this enchanted gem. It lets Slayer Masters update you on your progress. If you lose it, you can buy another from any Slayer Master.",
                )
                val gemAdd = access.invAdd(access.inv, "obj.slayer_gem", 1)
                if (gemAdd.failure) {
                    chatNpc(
                        neutral,
                        "I tried to give you an enchanted gem, but you had no room. Free a space or buy one from any Slayer Master.",
                    )
                }
                assignNewTask(npc!!.visType.internalName)
            }
            2 -> {
                chatPlayer(neutral, "Sounds useless to me.")
                chatNpc(neutral, "Suit yourself.")
            }
        }
    }
}
