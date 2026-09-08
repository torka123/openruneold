package org.rsmod.content.systems.breach

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class WildernessBreachModule : PluginModule() {
    override fun bind() {
        bind(BreachConfig::class.java).toInstance(BreachConfig.load())
    }
}

internal class BreachWorldScript
@Inject
constructor(private val breaches: BreachService, private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { breaches.tick() }
        breaches.bossTypes().forEach { type ->
            onNpcHit(type) {
                val source = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
                if (source != null) {
                    // Apply first-attacker HP scaling from the delivered hit event. This produces
                    // the same resulting current/max HP as the old pre-hit hook while allowing
                    // revision-240 encounter scripts to own the NPC-specific Modify event.
                    breaches.prepareHit(npc, source, hit.damage)
                    breaches.recordHit(npc, source, hit.damage)
                }
            }
        }
        breaches.breachTypes().forEach { type ->
            onOpLoc1(type) { breaches.inspect(player) }
            onOpLoc2(type) { breaches.inspect(player) }
            onOpLoc3(type) { breaches.inspect(player) }
            onOpLoc4(type) { breaches.inspect(player) }
            onOpLoc5(type) { breaches.inspect(player) }
        }
        onCommand("breach") {
            desc = "Teleport to the announced or active Wilderness Breach"
            invalidArgs = "Use ::breach"
            cheat { if (args.isEmpty()) player.mes(breaches.teleport(player)) }
        }
        staff("breachstart", "Start Breach: ::breachstart random OR <boss> [location]") {
            val boss = args.firstOrNull()
            val location = args.getOrNull(1)?.toIntOrNull()
            player.mes(breaches.forceStart(boss, location))
        }
        staff("breachstop", "Safely stop the active Breach") {
            if (args.isEmpty()) player.mes(breaches.forceStop())
        }
        staff("breachstatus", "Show detailed Breach state") {
            if (args.isEmpty()) breaches.status().forEach(player::mes)
        }
        staff("breachnext", "Show the next UTC Breach boundary") {
            if (args.isEmpty()) player.mes(breaches.nextText())
        }
        staff("breachgoto", "Teleport to a Breach test location: ::breachgoto <1-4>") {
            val index = args.singleOrNull()?.toIntOrNull()
            player.mes(
                if (index == null) "Use ::breachgoto <1-4>." else breaches.goto(player, index)
            )
        }
        staff("breachloot", "Simulate Roat contribution odds: ::breachloot <basisPoints>") {
            val basisPoints = args.singleOrNull()?.toIntOrNull()
            player.mes(
                if (basisPoints == null) "Use ::breachloot <1-10000>."
                else breaches.lootSimulation(basisPoints)
            )
        }
        staff("breachpoints", "Show your persisted Breach PK Points and stats") {
            if (args.isEmpty()) {
                player.mes(
                    "Breach PK Points: ${player.attr[BREACH_PK_POINTS] ?: 0}; kills=${player.attr[BREACH_KILLS] ?: 0}; MVPs=${player.attr[BREACH_MVPS] ?: 0}."
                )
            }
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
