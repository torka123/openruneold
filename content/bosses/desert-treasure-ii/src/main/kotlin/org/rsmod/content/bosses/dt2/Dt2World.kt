package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class Dt2World
@Inject
constructor(
    private val encounters: Dt2EncounterManager,
    private val leviathanRecords: LeviathanRecordService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        onCommand("dt2debug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the current private DT2 encounter state"
            invalidArgs = "Use as ::dt2debug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
        onCommand("dt2reset") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset the current private DT2 encounter"
            invalidArgs = "Use as ::dt2reset"
            cheat { if (args.isEmpty()) encounters.reset(player) }
        }
        onCommand("dt2phase") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Force the next DT2 special for deterministic smoke testing"
            invalidArgs = "Use as ::dt2phase [special]"
            cheat { encounters.forcePhase(player, args.firstOrNull()) }
        }
        onCommand("dt2solve") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Solve the active deterministic DT2 puzzle mechanic"
            invalidArgs = "Use as ::dt2solve"
            cheat { encounters.solve(player) }
        }

        for (component in 6..13) {
            onProtectedEvent<IfOverlayButton>((VARDORVIS_QTE_INTERFACE shl 16) or component) {
                if (!encounters.destroyVardorvisSpore(player, component)) {
                    player.mes("That tendril is not active.")
                }
            }
        }

        val leviathanScoreboard =
            requireNotNull(ServerCacheManager.getObject(Dt2Assets.LEVIATHAN_SCOREBOARD)) {
                "Missing revision-240 Leviathan scoreboard: ${Dt2Assets.LEVIATHAN_SCOREBOARD}"
            }
        onOpLoc1(leviathanScoreboard) {
            arriveDelay()
            val leviathan =
                requireNotNull(ServerCacheManager.getNpc(Dt2Boss.LEVIATHAN.normalNpcId)) {
                    "Missing revision-240 Leviathan npc: ${Dt2Boss.LEVIATHAN.normalNpcId}"
                }
            val killCountVarp =
                requireNotNull(leviathan.paramOrNull(BaseParams.killcount_varp)) {
                    "The revision-240 Leviathan definition has no kill-count varp."
                }
            val text =
                LeviathanScoreboard.texts(
                    killCount = player.vars[killCountVarp],
                    records = leviathanRecords.snapshot(player),
                    globals = leviathanRecords.globalSnapshot(player),
                )
            player.ifOpenMainModal(LeviathanScoreboard.INTERFACE, eventBus)
            text.forEach { (component, value) ->
                player.ifSetText(LeviathanScoreboard.INTERFACE, component, value)
            }
        }

        ServerCacheManager.getObject(Dt2Assets.LEVIATHAN_START_HANDHOLDS)?.let { handholds ->
            onOpLoc1(handholds) {
                arriveDelay()
                if (!encounters.activateLeviathan(player, it.loc.id, it.loc.coords)) {
                    player.mes("The handholds do not lead into an active Leviathan encounter.")
                }
            }
        }

        ServerCacheManager.getNpc(Dt2Assets.WHISPERER_SPAWN_ID)?.let { whisperer ->
            onOpNpc1(whisperer) { encounters.activateWhisperer(player, it.npc) }
        }
        ServerCacheManager.getItem(Dt2Assets.BLACKSTONE_FRAGMENT)?.let { fragment ->
            onOpHeld1(fragment) { encounters.toggleWhispererShadowRealm(player) }
        }
        ServerCacheManager.getNpc(12167)?.let { duke ->
            onOpNpcU(duke) { encounters.poisonDuke(player, it.npc, it.objType) }
            onOpNpc1(duke) { encounters.feedDuke(player, it.npc) }
        }
        registerDukePreparation()
    }

    private fun ScriptContext.registerDukePreparation() {
        Dt2Assets.dukePrepLocs.forEach { id ->
            ServerCacheManager.getObject(id)?.let { type ->
                onOpLoc1(type) {
                    arriveDelay()
                    encounters.useDukePreparation(this, id, it.loc.coords)
                }
            }
        }
        ServerCacheManager.getObject(47536)?.let { vat ->
            onOpLoc3(vat) {
                arriveDelay()
                encounters.inspectDukeVat(player, it.loc.coords, false)
            }
            onOpLoc4(vat) {
                arriveDelay()
                encounters.inspectDukeVat(player, it.loc.coords, true)
            }
        }
        val pestle = ServerCacheManager.getItem(Dt2Assets.PESTLE_AND_MORTAR)
        val musca = ServerCacheManager.getItem(Dt2Assets.MUSCA_MUSHROOM)
        val arder = ServerCacheManager.getItem(Dt2Assets.ARDER_MUSHROOM)
        if (pestle != null && musca != null) {
            onOpHeldU(pestle, musca) { encounters.grindDukeIngredient(this, true) }
        }
        if (pestle != null && arder != null) {
            onOpHeldU(pestle, arder) { encounters.grindDukeIngredient(this, false) }
        }
    }
}
