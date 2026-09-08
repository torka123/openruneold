package org.rsmod.content.minigames.clanmayhem

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.spell.teleports.MinigameTeleport
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ClanMayhemWorld
@Inject
constructor(
    private val manager: ClanMayhemManager,
    private val protectedAccess: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val progression: ClanMayhemProgressionService,
    private val minigameTeleport: MinigameTeleport,
) : PluginScript() {
    override fun ScriptContext.startup() {
        // All four spellbooks share one registration and one out-of-match destination chooser.
        RETURN_TO_TEAM_SPELLS.forEach { internalName ->
            val spellObj = ServerCacheManager.getItem(internalName.asRSCM(RSCMType.OBJ))
            val spell = spellObj?.let(spells::getObjSpell)
            if (spell != null) {
                onIfOverlayButton(spell.component) {
                    if (manager.matchFor(player) != null) {
                        manager.returnToTeam(player)
                    } else {
                        protectedAccess.launch(player) { with(minigameTeleport) { openMenu() } }
                    }
                }
            }
        }

        ServerCacheManager.getObject(CRATE_OBJECT_ID)?.let { crate ->
            onOpLoc1(crate) {
                val crateCoords = it.loc.coords
                val start = player.coords
                player.mes("You begin opening the Clan Mayhem chest...")
                delay(CRATE_OPEN_CHANNEL_TICKS)
                if (player.coords != start) {
                    player.mes("You stop opening the chest.")
                    return@onOpLoc1
                }
                if (manager.claimCrate(player, crateCoords)) {
                    anim("seq.human_openchest")
                    return@onOpLoc1
                }
                val reward = manager.claimReward(player, crateCoords)
                if (reward == null) {
                    player.mes("That crate is not active.")
                } else {
                    anim("seq.human_openchest")
                    showRewardInterface(reward)
                }
            }
        }

        onCommand("mayhem") {
            desc = "Join Clan Mayhem registration or view its status"
            invalidArgs = "Use ::mayhem"
            cheat { if (args.isEmpty()) manager.join(player) }
        }
        onCommand("mayhemjoin") {
            desc = "Join the open Clan Mayhem registration"
            invalidArgs = "Use ::mayhemjoin"
            cheat { if (args.isEmpty()) manager.join(player) }
        }
        onCommand("mayhemready") {
            desc = "Toggle your Clan Mayhem ready state"
            invalidArgs = "Use ::mayhemready"
            cheat {
                if (args.isEmpty() && !manager.toggleReady(player)) {
                    player.mes("You are not registered for Clan Mayhem.")
                }
            }
        }
        onCommand("mayhemleave") {
            desc = "Leave Clan Mayhem registration"
            invalidArgs = "Use ::mayhemleave"
            cheat {
                if (args.isEmpty() && !manager.leaveRegistration(player)) {
                    player.mes("You cannot leave Clan Mayhem right now.")
                }
            }
        }
        onCommand("mayhemclaim") {
            desc = "Claim a committed Clan Mayhem winner reward"
            invalidArgs = "Use ::mayhemclaim"
            cheat {
                if (args.isEmpty()) {
                    val reward = manager.claimReward(player) ?: return@cheat
                    protectedAccess.launch(player) { showRewardInterface(reward) }
                }
            }
        }
        onCommand("mayhemstats") {
            desc = "Show your persistent Clan Mayhem statistics"
            invalidArgs = "Use ::mayhemstats"
            cheat { if (args.isEmpty()) player.mes(progression.summary(player)) }
        }

        onCommand("mayhemstart") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open a Clan Mayhem format for testing"
            invalidArgs = "Use ::mayhemstart trio|five welfare|mid|max"
            cheat {
                val format = parseFormat(args.getOrNull(0), args.getOrNull(1))
                if (format == null) {
                    player.mes("Use ::mayhemstart trio|five welfare|mid|max")
                } else if (!manager.openRegistration(format, debugOverride = true)) {
                    player.mes("Clan Mayhem already has an active registration or match.")
                } else {
                    manager.prepareAdminTestParticipant(player)
                }
            }
        }
        onCommand("mayhemforcestart") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Start the current Clan Mayhem registration immediately"
            invalidArgs = "Use ::mayhemforcestart"
            cheat {
                if (args.isEmpty()) {
                    val prepared = manager.prepareAdminTestParticipant(player)
                    if (!prepared || !manager.startNow(debugOverride = true)) {
                        player.mes("No safe Clan Mayhem test roster could be started.")
                    }
                }
            }
        }
        onCommand("mayhemcancel") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Cancel Clan Mayhem safely"
            invalidArgs = "Use ::mayhemcancel"
            cheat {
                if (args.isEmpty() && !manager.cancel()) player.mes("Clan Mayhem is not active.")
            }
        }
        onCommand("mayhemstate") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show Clan Mayhem state"
            invalidArgs = "Use ::mayhemstate"
            cheat { if (args.isEmpty()) player.mes(manager.status(player)) }
        }
        onCommand("mayhemtime") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Fast-forward a live Clan Mayhem match clock for testing"
            invalidArgs = "Use ::mayhemtime <minutes> or ::mayhemtime <minutes:seconds>"
            cheat {
                val target = args.singleOrNull()?.let(::parseMayhemElapsedTicks)
                if (target == null) {
                    player.mes("Use ::mayhemtime <minutes> or ::mayhemtime <minutes:seconds> (maximum 25:00).")
                    player.mes("Fog test points: 8:00, 13:00, 18:00, 22:00, and 24:00.")
                } else {
                    manager.fastForwardForTesting(player, target)
                }
            }
        }
        onCommand("mayhemclan") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Assign an isolated Clan Mayhem test clan identity"
            invalidArgs = "Use ::mayhemclan <name>"
            cheat {
                val name = args.joinToString(" ").trim()
                if (name.isEmpty()) {
                    player.mes("Use ::mayhemclan <name>")
                } else {
                    manager.assignTestClan(player, name)
                }
            }
        }
        onCommand("mayhemreturn") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Test the Clan Mayhem Return-to-Team action"
            invalidArgs = "Use ::mayhemreturn"
            cheat { if (args.isEmpty()) manager.returnToTeam(player) }
        }
    }

    private fun ProtectedAccess.showRewardInterface(rewards: List<InvObj>) {
        val rewardInventory = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(rewardInventory.size >= rewards.size) {
            "Clan Mayhem reward display has too few slots."
        }
        rewards.forEachIndexed { slot, reward -> rewardInventory[slot] = InvObj(reward) }
        invTransmit(rewardInventory)
        ifOpenMainModal(RSCM.getReverseMapping(RSCMType.INTERFACE, REWARD_INTERFACE))
        interfaceInvInit(
            inv = rewardInventory,
            target = RSCM.getReverseMapping(RSCMType.COMPONENT, REWARD_ITEMS_COMPONENT),
            objRowCount = 2,
            objColCount = REWARD_COLUMNS,
        )
    }

    private fun parseFormat(size: String?, tier: String?): ClanMayhemFormat? {
        val teamSize =
            when (size?.lowercase()) {
                "trio",
                "3",
                "3man" -> ClanMayhemTeamSize.TRIO
                "five",
                "5",
                "5man" -> ClanMayhemTeamSize.FIVE
                else -> return null
            }
        val kitTier =
            when (tier?.lowercase()) {
                "welfare" -> ClanMayhemKitTier.WELFARE
                "mid" -> ClanMayhemKitTier.MID
                "max" -> ClanMayhemKitTier.MAX
                else -> return null
            }
        return ClanMayhemFormat(teamSize, kitTier)
    }

    private companion object {
        const val CRATE_OPEN_CHANNEL_TICKS = 3
        // Verified against the active revision-240 cache; no generated game-value aliases exist.
        const val CRATE_OBJECT_ID = 33_016
        const val REWARD_INTERFACE = 23
        const val REWARD_ITEMS_COMPONENT = (REWARD_INTERFACE shl 16) or 12
        const val REWARD_DISPLAY_INVENTORY = "inv.deathkeep_items"
        const val REWARD_COLUMNS = 4
        val RETURN_TO_TEAM_SPELLS =
            listOf(
                "obj.minigame_teleport",
                "obj.ancient_minigame_teleport",
                "obj.lunar_minigame_teleport",
                "obj.arceuus_minigame_teleport",
            )
    }
}
