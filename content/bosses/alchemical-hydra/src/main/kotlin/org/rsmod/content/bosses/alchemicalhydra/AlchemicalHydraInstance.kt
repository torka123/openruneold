package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal class AlchemicalHydraInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: HydraEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_alchemical_hydra"

    override fun area(): InstanceArea = HYDRA_AREA

    override fun destroyWhenEmpty(): Boolean = true

    override fun ScriptContext.configure() {
        validateAssets()

        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave { encounters.onLeave(player, instanceId) }
        onInstanceEnded { encounters.onEnded(instanceId) }

        HydraAssets.doorIds.forEach { id ->
            val door = requireNotNull(ServerCacheManager.getObject(id)) { "Missing Hydra door $id" }
            onOpLoc1(door) {
                arriveDelay()
                encounters.crossDoor(this)
            }
            onOpLoc2(door) {
                arriveDelay()
                encounters.crossDoor(this)
            }
        }

        onExitObject { defaultLeaveFlow() }

        onCommand("hydra") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Alchemical Hydra encounter"
            invalidArgs = "Use as ::hydra"
            cheat {
                if (args.isEmpty()) {
                    protectedAccess.launch(
                        player,
                        "Finish what you are doing before entering the Alchemical Hydra's lair.",
                    ) {
                        enterHydra()
                    }
                }
            }
        }
        onCommand("leavehydra") {
            desc = "Leave your private Alchemical Hydra encounter"
            invalidArgs = "Use as ::leavehydra"
            cheat {
                if (args.isEmpty()) protectedAccess.launch(player) { leaveHydra() }
            }
        }
        onCommand("hydrastatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the active Alchemical Hydra state"
            invalidArgs = "Use as ::hydrastatus"
            cheat { if (args.isEmpty()) player.mes(encounters.status(player)) }
        }
    }

    private fun validateAssets() {
        HydraAssets.formIds.forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-241 Hydra NPC $id" }
        }
        require(ServerCacheManager.getNpc(HydraAssets.GREEN)?.hitpoints == HydraRules.MAX_HITPOINTS) {
            "Revision-241 Hydra must have ${HydraRules.MAX_HITPOINTS} hitpoints."
        }
        HydraAssets.animationIds.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) { "Missing revision-241 Hydra animation $id" }
        }
        HydraAssets.spotanimIds.forEach { id ->
            RSCM.getReverseMapping(RSCMType.SPOTANIM, id)
        }
        (HydraAssets.doorIds + HydraAssets.ventIds).forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) { "Missing revision-241 Hydra location $id" }
        }
    }

    private suspend fun ProtectedAccess.enterHydra() {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering the Alchemical Hydra's lair.")
            return
        }
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return
        }
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        completeInstanceEntry(
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        )
        val session = manager.sessionForPlayer(player)?.takeIf { it.key == key } ?: return
        val destination = requireNotNull(manager.resolveCoord(session, OBSERVATION_DESTINATION))
        anim(RSCM.getReverseMapping(RSCMType.SEQ, HydraAssets.ENTRY_ANIM))
        exactMove(player.coords, destination, 0, ENTRY_CLIENT_CYCLES, ENTRY_ANGLE)
        mes("You climb over the rocks into the observation room.")
        mes("Cross the alchemical doors to begin the fight.")
    }

    private suspend fun ProtectedAccess.leaveHydra() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside an Alchemical Hydra encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key == key) return
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        mes("You leave the Alchemical Hydra's lair.")
    }

    private companion object {
        private const val SOURCE_ZONE_X = 165
        private const val SOURCE_ZONE_Z = 1278
        private const val SOURCE_MAP_X = 21
        private const val SOURCE_MAP_Z = 160
        private const val ENTRY_CLIENT_CYCLES = 60
        private const val ENTRY_ANGLE = 1024

        private val OBSERVATION_DESTINATION = CoordGrid(0, SOURCE_MAP_X, SOURCE_MAP_Z, 7, 12)

        private val HYDRA_AREA =
            InstanceArea.template(
                template =
                    RegionTemplate.create {
                        copyAllLevels(SOURCE_ZONE_X, SOURCE_ZONE_Z) {
                            zoneWidth = 9
                            zoneLength = 9
                        }
                    },
                enterCoord = RegionLocal(0, SOURCE_MAP_X, SOURCE_MAP_Z, 7, 10),
                exitCoord = CoordGrid(1352, 10250, 0),
                npcSpawns =
                    listOf(
                        InstanceNpc("npc.hydraboss", RegionLocal(0, SOURCE_MAP_X, SOURCE_MAP_Z, 20, 25)),
                    ),
            )
    }
}
