package org.rsmod.content.minigames.perilousmoons

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class PerilousMoonWorld
@Inject
constructor(
    private val encounters: PerilousMoonEncounterManager,
    private val rewards: PerilousMoonRewardService,
    private val locs: LocRepository,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.initializeWorld()
        rewards.verifyDefinitions()

        onOpLoc1(MoonAssets.BLOOD_ENTRY_STATUE) {
            arriveDelay()
            encounters.begin(player, PerilousMoon.Blood)
        }
        onOpLoc1(MoonAssets.BLUE_ENTRY_STATUE) {
            arriveDelay()
            encounters.begin(player, PerilousMoon.Blue)
        }
        onOpLoc1(MoonAssets.ECLIPSE_ENTRY_STATUE) {
            arriveDelay()
            encounters.begin(player, PerilousMoon.Eclipse)
        }
        onOpLoc1(MoonAssets.BLUE_BRAZIER_UNLIT) {
            arriveDelay()
            encounters.lightBrazier(player, left = it.loc.coords.x < PerilousMoon.Blue.center.x)
        }
        onOpLoc1(MoonAssets.LUNAR_CHEST) {
            arriveDelay()
            val claimed = rewards.claim(player, it.loc.coords)
            if (claimed != null) {
                anim("seq.human_openchest")
                locs.change(it.loc, MoonAssets.LUNAR_CHEST_OPEN, LUNAR_CHEST_OPEN_TICKS)
            }
        }
        listOf(MoonAssets.EXIT_STAIRS, MoonAssets.EXIT_STAIRS_TWO).forEach { stairs ->
            onOpLoc1(stairs) {
                arriveDelay()
                encounters.escape(player)
            }
            onOpLoc2(stairs) {
                arriveDelay()
                encounters.escape(player)
            }
        }

        registerTeleport("bloodmoon", PerilousMoon.Blood)
        registerTeleport("sunmoon", PerilousMoon.Blood)
        registerTeleport("bluemoon", PerilousMoon.Blue)
        registerTeleport("eclipsemoon", PerilousMoon.Eclipse)

        onCommand("moondebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect or force a personal Perilous Moons phase"
            invalidArgs =
                "Use ::moondebug status|reset or ::moondebug blood|blue|eclipse standard|rain|jaguar|freeze|storm|shield|clones"
            cheat {
                when {
                    args.singleOrNull()?.equals("status", ignoreCase = true) == true ->
                        player.mes(encounters.debugStatus(player))
                    args.singleOrNull()?.equals("reset", ignoreCase = true) == true -> {
                        encounters.abort(player, "staff debug reset", teleport = false)
                        player.mes("Your personal Moon encounter was reset without rewards.")
                    }
                    args.size == 2 -> forceDebug(args[0].lowercase(), args[1].lowercase())
                    else -> player.mes(MOON_DEBUG_USAGE)
                }
            }
        }
    }

    private fun ScriptContext.registerTeleport(command: String, moon: PerilousMoon) {
        onCommand(command) {
            desc = "Teleport next to the ${moon.displayName} entry statue"
            invalidArgs = "Use as ::$command"
            cheat {
                if (args.isNotEmpty()) {
                    player.mes("Use as ::$command")
                } else {
                    player.ifClose(eventBus)
                    encounters.teleportToStatue(player, moon)
                }
            }
        }
    }

    private fun Cheat.forceDebug(moonName: String, phase: String) {
        val moon =
            when (moonName) {
                "blood" -> PerilousMoon.Blood
                "blue" -> PerilousMoon.Blue
                "eclipse" -> PerilousMoon.Eclipse
                else -> {
                    player.mes("Unknown Moon '$moonName'.")
                    return
                }
            }
        if (phase == "standard") {
            player.ifClose(eventBus)
            encounters.begin(player, moon)
            return
        }
        val special =
            when (moon to phase) {
                PerilousMoon.Blood to "rain",
                PerilousMoon.Blue to "freeze",
                PerilousMoon.Eclipse to "shield" -> 1
                PerilousMoon.Blood to "jaguar",
                PerilousMoon.Blue to "storm",
                PerilousMoon.Eclipse to "clones" -> 2
                else -> {
                    player.mes("That phase does not belong to ${moon.displayName}.")
                    return
                }
            }
        if (!encounters.forceSpecial(player, moon, special)) {
            player.ifClose(eventBus)
            if (!encounters.begin(player, moon)) return
            if (!encounters.forceSpecial(player, moon, special)) {
                player.mes("The requested phase could not be started.")
            }
        }
    }
}

private const val LUNAR_CHEST_OPEN_TICKS = 2
private const val MOON_DEBUG_USAGE =
    "Use ::moondebug status|reset or ::moondebug blood|blue|eclipse " +
        "standard|rain|jaguar|freeze|storm|shield|clones"
