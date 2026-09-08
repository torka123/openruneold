package org.rsmod.content.bosses.obor

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.oborLairUnlocked: Int by intVarp("varp.obor_lair_unlocked")

public class OborInstance
@Inject
constructor(
    private val registry: BossInstanceRegistry,
    private val state: OborRunState,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_obor"

    override fun area(): InstanceArea = AREA

    override fun destroyWhenEmpty(): Boolean = true

    override fun ScriptContext.configure() {
        onOpLoc1("loc.hillgiant_boss_entrance_r") { enterLair() }
        onOpLoc1("loc.hillgiant_boss_entrance_l") { enterLair() }
        onOpLoc1("loc.hillgiant_boss_exit_r") { leaveLair() }
        onOpLoc1("loc.hillgiant_boss_exit_l") { leaveLair() }
        onOpLoc1("loc.hillgiant_boss_climbingrocks") { climbIntoArena() }
        onOpLoc1(OBOR_CHEST_CLOSED) { openRewardChest(it.loc) }

        onInstanceStarted {
            val session = manager.sessionForId(instanceId) ?: return@onInstanceStarted
            state.initialize(instanceId)
            val chest = manager.localCoord(session, CHEST_LOCAL) ?: return@onInstanceStarted
            locRepo.add(
                coords = chest,
                internal = OBOR_CHEST_CLOSED,
                duration = Int.MAX_VALUE,
                angle = LocAngle.North,
                shape = LocShape.CentrepieceStraight,
            )
        }
        onInstanceEnded { state.remove(instanceId) }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.internalName != OBOR_NPC) return@onEvent
            val instanceId = manager.instanceForNpc(npc) ?: return@onEvent
            state.markActive(instanceId)
        }
    }

    private suspend fun ProtectedAccess.enterLair() {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        if (player.oborLairUnlocked == 0 && invTotal(inv, OBOR_KEY) == 0) {
            mes("The gate is locked. You need a giant key to unlock it.")
            return
        }
        val spec = registry.get(key) ?: run {
            mes("Obor's lair is unavailable.")
            return
        }
        val result = manager.create(player, key, spec, InstanceAccess.Private, worldClock.cycle)
        if (result is InstanceManager.Result.Created && player.oborLairUnlocked == 0) {
            // The first key proves access permanently; it is only consumed when loot is claimed.
            player.oborLairUnlocked = 1
            mes("The giant key permanently unlocks the gate.")
        }
        completeInstanceEntry(result)
    }

    private suspend fun ProtectedAccess.leaveLair() {
        val session = manager.sessionForPlayer(player)
        if (session == null || session.key != key) {
            mes("You are not inside Obor's lair.")
            return
        }
        defaultLeaveFlow()
    }

    private fun ProtectedAccess.climbIntoArena() {
        val session = manager.sessionForPlayer(player)
        if (session == null || session.key != key) {
            mes("The rocks do not lead anywhere from here.")
            return
        }
        val destination = manager.localCoord(session, FIGHT_LOCAL) ?: return
        telejump(destination)
    }

    private suspend fun ProtectedAccess.openRewardChest(loc: BoundLocInfo) {
        arriveDelay()
        val session = manager.sessionForPlayer(player)
        if (session == null || session.key != key) {
            mes("This chest belongs to another encounter.")
            return
        }
        if (!state.beginClaim(session.id)) {
            mes("You must defeat Obor before opening the chest.")
            return
        }

        val obor = manager.npcsForInstance(session.id).firstOrNull { it.type.internalName == OBOR_NPC }
        if (invTotal(inv, OBOR_KEY) > 0) {
            invDel(inv, OBOR_KEY)
            anim("seq.human_openchest")
            locRepo.change(loc, OBOR_CHEST_OPEN, CHEST_OPEN_TICKS)
            rollRewards(loc)
            incrementKillCount(obor)
            mes("You find some treasure in the chest!")
        } else {
            mes("The chest is locked. You need another giant key to claim the reward.")
        }

        if (obor != null) {
            obor.lifecycleRespawnCycle = worldClock.cycle + obor.type.respawnRate.coerceAtLeast(1)
        }
    }

    private fun ProtectedAccess.rollRewards(loc: BoundLocInfo) {
        val table = dropRegistry.forLoc(OBOR_CHEST_CLOSED) ?: return
        val result = table.roll(player, ArgMap()).flatten()
        when (result) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> giveGroundReward(result.result, loc)
            is RollResult.ListOf -> result.results.forEach { giveGroundReward(it, loc) }
        }
    }

    private fun ProtectedAccess.giveGroundReward(drop: DropRollItem, loc: BoundLocInfo) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add(obj, loc.coords, duration, player, drop.rollCount(random))
    }

    private fun ProtectedAccess.incrementKillCount(obor: org.rsmod.game.entity.Npc?) {
        val npc = obor ?: return
        val varp = npc.paramOrNull(BaseParams.killcount_varp) ?: return
        val count = player.vars[varp] + 1
        VarPlayerIntMapSetter.set(player, varp, count)
        mes("Your Obor kill count is: <col=ff0000>$count</col>")
    }

    private companion object {
        private const val CHEST_OPEN_TICKS = 2
        private val ENTER_LOCAL = RegionLocal(0, 48, 153, 20, 22)
        private val FIGHT_LOCAL = RegionLocal(0, 48, 153, 20, 12)
        private val CHEST_LOCAL = RegionLocal(0, 48, 153, 13, 5)
        private val AREA =
            InstanceArea.copyRegions(
                regionIds = listOf(12441),
                enterCoord = ENTER_LOCAL,
                exitCoord = CoordGrid(3097, 9833),
                npcSpawns = listOf(InstanceNpc(OBOR_NPC, CoordGrid(3091, 9799))),
            )
    }
}
