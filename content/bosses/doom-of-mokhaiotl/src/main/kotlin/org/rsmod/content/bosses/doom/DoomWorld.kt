package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class DoomWorld
@Inject
constructor(private val encounters: DoomEncounterManager, private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        for (id in listOf(DoomAssets.GAP, DoomAssets.GAP_ALT)) {
            val action = requireNotNull(DoomGapRouting.actionFor(id))
            onOpLoc1(requireLoc(id)) {
                encounters.advanceEntryPrompt(player)
                arriveDelay()
                when (action) {
                    DoomGapAction.ENTER -> enterFirstDelve()
                    DoomGapAction.QUICK_ESCAPE ->
                        encounters.abort(player, "quick escape", teleport = true)
                }
            }
        }
        for (id in listOf(DoomAssets.BURROW_HOLE, DoomAssets.UNIQUE_BURROW_HOLE)) {
            onOpLoc1(requireLoc(id)) {
                encounters.advanceEntryPrompt(player)
                arriveDelay()
                showRewardInterface(inRun = true)
            }
            onOpLoc2(requireLoc(id)) {
                encounters.advanceEntryPrompt(player)
                arriveDelay()
                descend()
            }
            onOpLoc3(requireLoc(id)) {
                encounters.advanceEntryPrompt(player)
                arriveDelay()
                claimEarly()
            }
        }
        onOpLoc1(requireLoc(DoomAssets.LOOT_CHEST)) {
            encounters.advanceEntryPrompt(player)
            arriveDelay()
            showRewardInterface(inRun = false)
        }
        onOpLoc1(requireLoc(DoomAssets.SCOREBOARD)) {
            encounters.advanceEntryPrompt(player)
            arriveDelay()
            DoomScoreboardUi.show(
                this,
                encounters.scoreboardSnapshot(player),
                DoomScoreboardUi.View.PERSONAL,
            )
        }
        onOpLoc2(requireLoc(DoomAssets.SCOREBOARD)) {
            encounters.advanceEntryPrompt(player)
            arriveDelay()
            DoomScoreboardUi.show(
                this,
                encounters.scoreboardSnapshot(player),
                DoomScoreboardUi.View.GENERAL,
            )
        }
        onOpLoc3(requireLoc(DoomAssets.SCOREBOARD)) {
            encounters.advanceEntryPrompt(player)
            arriveDelay()
            DoomScoreboardUi.show(
                this,
                encounters.scoreboardSnapshot(player),
                DoomScoreboardUi.View.DELVES,
            )
        }

        onProtectedEvent<IfModalButton>(componentPacked(REWARD_CLAIM_EARLY)) { claimEarly() }
        onProtectedEvent<IfModalPauseButton>(componentPacked(REWARD_CLAIM_EARLY)) { claimEarly() }
        onProtectedEvent<IfModalButton>(componentPacked(REWARD_TO_INVENTORY)) { claimToInventory() }
        onProtectedEvent<IfModalPauseButton>(componentPacked(REWARD_TO_INVENTORY)) {
            claimToInventory()
        }
        onProtectedEvent<IfModalButton>(componentPacked(REWARD_DESCEND)) { descend() }
        onProtectedEvent<IfModalPauseButton>(componentPacked(REWARD_DESCEND)) { descend() }
        onProtectedEvent<IfModalButton>(componentPacked(REWARD_TO_BANK)) { claimToBank() }
        onProtectedEvent<IfModalPauseButton>(componentPacked(REWARD_TO_BANK)) { claimToBank() }

        onCommand("dom") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport outside the Doom of Mokhaiotl delve"
            invalidArgs = "Use as ::dom"
            cheat { if (args.isEmpty()) encounters.teleportOutside(player) }
        }
        onCommand("domdelve") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start an isolated no-reward Doom delve for live mechanic testing"
            invalidArgs = "Use as ::domdelve [1-8]"
            cheat {
                val delve = args.singleOrNull()?.toIntOrNull()
                if (delve == null) player.mes("Use as ::domdelve [1-8]")
                else encounters.debugEnter(player, delve)
            }
        }
        onCommand("domdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show current Doom of Mokhaiotl encounter state"
            invalidArgs = "Use as ::domdebug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
        onCommand("leavedoom") {
            desc = "Leave your current Doom of Mokhaiotl instance"
            invalidArgs = "Use as ::leavedoom"
            cheat {
                player.ifClose(eventBus)
                if (args.isEmpty() && !encounters.leave(player)) {
                    player.mes("You are not inside a Doom of Mokhaiotl encounter.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.claimEarly() {
        if (!encounters.canDescend(player)) return
        ifClose()
        val choice =
            choice2("Yes, claim my loot and forfeit the run.", 1, "No, continue my run.", 2)
        ifClose()
        if (choice == 1) {
            encounters.claim(player)
            ifClose()
        }
    }

    private fun ProtectedAccess.claimToInventory() {
        if (encounters.canDescend(player)) return
        ifClose()
        encounters.claim(player)
        ifClose()
    }

    private suspend fun ProtectedAccess.enterFirstDelve() {
        runEntryTransition(DoomEntryTransition.FIRST_DELVE) {
            encounters.enter(player, awaitPrompt = true)
        }
    }

    private suspend fun ProtectedAccess.descend() {
        if (!encounters.canDescend(player)) return
        if (encounters.hasUniqueRewardRisk(player)) {
            val choice =
                choice2(
                    "Descend and risk every accumulated reward, including the unique.",
                    1,
                    "Stay and claim the rewards.",
                    2,
                )
            ifClose()
            if (choice != 1) return
        }
        ifClose()
        var descent: DoomEncounterManager.Descent? = null
        runEntryTransition(
            transition = DoomEntryTransition.DEEPER_DELVE,
            prepareRun = {
                encounters.beginDescend(player)?.let {
                    descent = it
                    true
                } ?: false
            },
            createRun = {
                descent?.let { encounters.finishDescend(player, it, awaitPrompt = true) } ?: false
            },
        )
    }

    /**
     * Replays the revision-235 delve-entry presentation without carrying a suspended dialogue
     * across the instance hand-off. The entry text is deliberately non-blocking: movement closes
     * the message box through the normal input path, while the transition coroutine owns only the
     * fade and encounter activation. This prevents the first movement click from closing the same
     * protected continuation twice and corrupting the player session.
     */
    private suspend fun ProtectedAccess.runEntryTransition(
        transition: DoomEntryTransition,
        prepareRun: (() -> Boolean)? = null,
        createRun: () -> Boolean,
    ) {
        var entered = false
        var prepared = prepareRun == null
        if (transition.createRunCycle == 0) {
            if (!createRun()) return
            entered = true
        }

        try {
            mesboxNp(transition.promptText)
            fadeOverlay(
                startColour = 0,
                startTransparency = 255,
                endColour = 0,
                endTransparency = 0,
                clientDuration = DoomEntryTransition.FADE_CLIENT_DURATION,
            )

            delay(1)
            minimapHideMap()
            if (!prepared) {
                if (prepareRun?.invoke() != true) {
                    ifClose()
                    return
                }
                prepared = true
            }
            // The old instance must be absent for a complete server tick before allocating the
            // next copied region, otherwise old NPC/UI state survives in the reused scene.
            delay(1)

            if (!entered) {
                if (!createRun()) {
                    ifClose()
                    return
                }
                entered = true
            }

            delay(1)
            minimapReset()
            val delve = encounters.currentDelve(player) ?: return
            player.mes("<col=ff3045>Delve level: ${if (delve > 8) "8+ ($delve)" else delve}</col>")
            fadeOverlay(
                startColour = 0,
                startTransparency = 0,
                endColour = 0,
                endTransparency = 255,
                clientDuration = DoomEntryTransition.FADE_CLIENT_DURATION,
            )

            ifClose()
        } finally {
            ifClose()
            minimapReset()
            closeFadeOverlay(cycles = 1)
            if (entered) {
                // Activation must survive a movement click that cancels the presentation early.
                encounters.advanceEntryPrompt(player)
            }
        }
    }

    private fun ProtectedAccess.claimToBank() {
        if (encounters.canDescend(player)) return
        ifClose()
        encounters.claimToBank(player, bank)
        ifClose()
    }

    private fun ProtectedAccess.showRewardInterface(inRun: Boolean) {
        if (inRun && !encounters.canDescend(player)) {
            player.mes("Defeat the Doom of Mokhaiotl before investigating the burrow.")
            return
        }
        val rewards = encounters.pendingRewards(player)
        val display = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(display.size >= rewards.size) {
            "Doom reward display inventory is too small (size=${display.size}, rewards=${rewards.size})."
        }
        rewards.forEachIndexed { slot, reward ->
            display[slot] = InvObj(RSCM.getReverseMapping(RSCMType.OBJ, reward.objId), reward.count)
        }

        runClientScript(
            2889,
            19857413,
            19857416,
            19857414,
            19857415,
            19857417,
            19857419,
            19857421,
            19857422,
            19857423,
            19857428,
            19857426,
            19857427,
            19857424,
            19857425,
            0,
        )
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, REWARD_INTERFACE))
        runClientScript(
            7927,
            if (inRun) ((encounters.currentDelve(player) ?: return) - 1).coerceAtLeast(0) else 0,
            if (inRun) 0 else 1,
            if (inRun) 0 else 1,
        )
        invTransmit(display)
        interfaceInvInit(
            inv = display,
            target = component(REWARD_ITEMS),
            objRowCount = 8,
            objColCount = 2,
            dragType = 1,
        )
        ifSetEvents(component(REWARD_ITEMS), 0..27, IfEvent.Op1)
        listOf(REWARD_CLAIM_EARLY, REWARD_TO_INVENTORY, REWARD_DESCEND, REWARD_TO_BANK).forEach {
            ifSetEvents(component(it), -1..-1, IfEvent.PauseButton, IfEvent.Op1)
        }
        ifSetText(
            component(REWARD_VALUE),
            "Value: ${"%,d".format(encounters.pendingRewardValue(player))} GP",
        )
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing Doom location definition $id" }

    private fun componentPacked(child: Int) = (REWARD_INTERFACE shl 16) or child

    private fun component(child: Int) =
        RSCM.getReverseMapping(RSCMType.COMPONENT, componentPacked(child))

    private companion object {
        private const val REWARD_INTERFACE = 919
        private const val REWARD_CLAIM_EARLY = 14
        private const val REWARD_TO_INVENTORY = 17
        private const val REWARD_ITEMS = 19
        private const val REWARD_VALUE = 20
        private const val REWARD_DESCEND = 23
        private const val REWARD_TO_BANK = 26
        private const val REWARD_DISPLAY_INVENTORY = "inv.deathkeep_items"
    }
}

internal enum class DoomGapAction {
    ENTER,
    QUICK_ESCAPE,
}

internal object DoomGapRouting {
    fun actionFor(locId: Int): DoomGapAction? =
        when (locId) {
            DoomAssets.GAP -> DoomGapAction.ENTER
            DoomAssets.GAP_ALT -> DoomGapAction.QUICK_ESCAPE
            else -> null
        }
}

internal enum class DoomEntryTransition(val promptText: String, val createRunCycle: Int) {
    FIRST_DELVE("You jump the gap...", 0),
    DEEPER_DELVE("You jump further into the burrow...", 2);

    companion object {
        const val INTERACTIVE_PROMPT_CYCLE = 3
        const val FADE_CLIENT_DURATION = 50
    }
}
