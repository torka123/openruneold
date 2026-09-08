package org.rsmod.content.systems.voting

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class VoteSystemModule : PluginModule() {
    override fun bind() {
        bind(VoteSystemConfig::class.java).toInstance(VoteSystemConfig.load())
        addSetBinding<NpcDeathKillHook>(VoteBossKillHook::class.java)
    }
}

internal class VoteBossKillHook @Inject constructor(private val bosses: VoteBossService) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) = bosses.onKill(context)
}

internal class VoteWorldScript
@Inject
constructor(private val votes: VoteClaimService, private val bosses: VoteBossService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> {
            votes.tick()
            bosses.tick()
        }
        onPlayerLogin { votes.onLogin(player) }

        onCommand("vote") {
            desc = "Open the server vote page"
            invalidArgs = "Use ::vote"
            cheat {
                if (args.isNotEmpty()) return@cheat
                player.mes("Vote here: ${votes.voteUrl(player)}")
                player.mes("After voting, return and use ::claimvote.")
            }
        }
        onCommand("claimvote") {
            desc = "Claim valid votes from the configured provider"
            invalidArgs = "Use ::claimvote"
            cheat { if (args.isEmpty()) votes.claim(player) }
        }
        onCommand("vb") {
            desc = "Teleport to the announced Vote Boss arena"
            invalidArgs = "Use ::vb"
            cheat { if (args.isEmpty()) player.mes(bosses.teleport(player)) }
        }
        staff("votetotal", "Show Vote Boss and raffle state") {
            if (args.isEmpty()) player.mes(bosses.status())
        }
        staff("addvotes", "Add test votes: ::addvotes <amount>") {
            val amount = args.singleOrNull()?.toIntOrNull()
            player.mes(
                if (amount == null) "Use ::addvotes <amount>."
                else bosses.addTestVotes(amount, player)
            )
        }
        staff("forcezuk", "Queue TzKal-Zuk") {
            if (args.isEmpty()) player.mes(bosses.force(VoteBossKind.TZKAL_ZUK, player))
        }
        staff("forcesol", "Queue Sol Heredit") {
            if (args.isEmpty()) player.mes(bosses.force(VoteBossKind.SOL_HEREDIT, player))
        }
        staff("votequeue", "Show persisted Vote Boss queue") {
            if (args.isEmpty()) bosses.queueStatus().forEach(player::mes)
        }
        staff("clearvoteevent", "Clear active Vote Boss; use all to clear queue too") {
            player.mes(
                bosses.clear(player, clearQueue = args.singleOrNull()?.equals("all", true) == true)
            )
        }
    }

    private fun ScriptContext.staff(
        command: String,
        description: String,
        action: Cheat.() -> Unit,
    ) {
        onCommand(command) {
            requiredRights = Rights.ADMINISTRATOR
            desc = description
            cheat(action)
        }
    }
}
