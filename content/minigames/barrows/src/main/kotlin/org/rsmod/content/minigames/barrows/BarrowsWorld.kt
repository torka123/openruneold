package org.rsmod.content.minigames.barrows

import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.InvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Real-world Barrows interactions. The surface mounds, crypts, maze, and chest all use the normal
 * world map; this minigame intentionally does not create an instance.
 */
internal class BarrowsWorld
@Inject
constructor(
    private val runs: BarrowsRunManager,
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        installChestRoomCollision()
        installChestRoomChest()
        onOpHeld1(SPADE) { digIntoCrypt() }

        BarrowsBrother.entries.forEach { brother ->
            onOpLoc1(brother.sarcophagus) { openSarcophagus(brother, it.loc) }
            onOpLoc1(brother.staircase) { returnToSurface(brother) }
        }

        BARROWS_CHESTS.forEach { chest ->
            onOpLoc1(chest) { openRewardChest(it.loc) }
            onOpLoc2(chest) { openRewardChest(it.loc) }
            onOpLoc3(chest) { openRewardChest(it.loc) }
            onOpLoc4(chest) { openRewardChest(it.loc) }
            onOpLoc5(chest) { openRewardChest(it.loc) }
        }
        TUNNEL_LADDERS.forEach { ladder ->
            onOpLoc1(ladder) { leaveTunnels() }
        }
    }

    private fun installChestRoomCollision() {
        for (x in 3548..3555) {
            for (z in 9692..9697) {
                collision.allocateIfAbsent(x, z, 0)
                if (
                    x == 3548 ||
                    x == 3555 ||
                    z == 9692 ||
                    z == 9697
                ) {
                    collision.add(x, z, 0, CollisionFlag.BLOCK_WALK)
                }
            }
        }
    }

    private fun installChestRoomChest() {
        locRepo.add(
            coords = CHEST_ROOM_CHEST,
            internal = BARROWS_CHESTS.first(),
            duration = Int.MAX_VALUE,
            angle = LocAngle.West,
            shape = LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.digIntoCrypt() {
        val brother = BarrowsBrother.entries.firstOrNull { it.containsSurfaceMound(player.coords) }
        if (brother == null) {
            mes("You need to stand on one of the Barrows burial mounds to dig here.")
            return
        }

        runs.beginRun(player)
        mes("You dig a hole and descend into " + brother.displayName + "'s crypt.")
        telejump(brother.cryptEntryCoords)
    }

    private suspend fun ProtectedAccess.openSarcophagus(
        brother: BarrowsBrother,
        sarcophagus: BoundLocInfo,
    ) {
        if (!runs.isRunActive(player)) {
            mes("Dig on one of the Barrows burial mounds with a spade to begin a run.")
            return
        }

        arriveDelay()
        if (runs.isBrotherKilled(player, brother)) {
            mes("The sarcophagus is empty.")
            return
        }

        if (runs.tunnelBrother(player) == brother) {
            if (!runs.areAllSurfaceBrothersKilled(player)) {
                mes("The hidden tunnel remains sealed. Defeat the other five brothers first.")
                return
            }
            runs.markTunnelEntered(player, brother)
            val ratCount = runs.spawnChestRoomCryptRats(player)
            mes("A hidden passage opens beneath the sarcophagus. You descend into the chest chamber.")
            telejump(CHEST_ROOM_ENTRY)
            if (ratCount > 0) {
                mes("$ratCount crypt rats are waiting in the chest chamber.")
            }
            return
        }

        if (!runs.spawnBrother(player, brother, brother.spawnCoords)) {
            mes(brother.displayName + " is already guarding this crypt.")
        }
    }

    private suspend fun ProtectedAccess.returnToSurface(brother: BarrowsBrother) {
        arriveDelay()
        telejump(brother.moundCenter)
    }

    private suspend fun ProtectedAccess.openRewardChest(chest: BoundLocInfo) {
        if (!runs.isRunActive(player)) {
            mes("You must begin a Barrows run at a burial mound before opening this chest.")
            return
        }
        if (!runs.isTunnelEntered(player)) {
            mes("You need to find the hidden tunnel passage before you can reach this chest.")
            return
        }

        val tunnelBrother = runs.tunnelBrother(player)
        if (tunnelBrother == null) {
            mes("Your Barrows run is incomplete. Return to a burial mound and start again.")
            return
        }
        if (!runs.isBrotherKilled(player, tunnelBrother)) {
            if (runs.spawnBrother(player, tunnelBrother, CHEST_GUARD_SPAWN)) {
                mes(tunnelBrother.displayName + " emerges to defend the chest!")
            } else {
                mes(tunnelBrother.displayName + " is already defending the chest.")
            }
            return
        }
        if (!runs.areAllBrothersKilled(player)) {
            mes("The chest remains sealed by the brothers you have not defeated.")
            return
        }
        if (!runs.canOpenChest(player)) {
            mes("This chest has already been claimed.")
            return
        }

        anim("seq.human_openchest")
        val rewards = runs.openChest(player, chest.coords)
        showRewardInterface(rewards)
    }

    private fun ProtectedAccess.showRewardInterface(rewards: List<BarrowsReward>) {
        val rewardInventory = Inventory.create(REWARD_DISPLAY_INVENTORY)
        check(rewardInventory.size >= rewards.size) {
            "Barrows reward display inventory has too few slots. (size=${rewardInventory.size}, rewards=${rewards.size})"
        }
        rewards.forEachIndexed { slot, reward ->
            rewardInventory[slot] = InvObj(reward.obj, reward.count)
        }

        invTransmit(rewardInventory)
        ifOpenMainModal(BARROWS_REWARD_INTERFACE)
        interfaceInvInit(
            inv = rewardInventory,
            target = BARROWS_REWARD_ITEMS,
            objRowCount = 2,
            objColCount = MAX_CHEST_REWARDS,
        )
    }

    private suspend fun ProtectedAccess.leaveTunnels() {
        arriveDelay()
        val exit = runs.tunnelBrother(player)?.moundCenter ?: SURFACE_EXIT
        mes("You climb out of the Barrows tunnels.")
        telejump(exit)
    }

    private companion object {
        private const val SPADE = "obj.spade"

        private val CHEST_ROOM_ENTRY = BARROWS_CHEST_ROOM_ENTRY
        private val CHEST_ROOM_CHEST = CoordGrid(3551, 9695)
        private val CHEST_GUARD_SPAWN = CoordGrid(3553, 9694)
        private val SURFACE_EXIT = CoordGrid(3565, 3288)

        private const val BARROWS_REWARD_INTERFACE = "interface.barrows_reward"
        private const val BARROWS_REWARD_ITEMS = "component.barrows_reward:items"
        private const val REWARD_DISPLAY_INVENTORY = "inv.deathkeep_items"
        private const val MAX_CHEST_REWARDS = 7

        private val BARROWS_CHESTS =
            listOf(
                "loc.barrows_stone_chest",
                "loc.barrows_stone_chest_closed",
                "loc.barrows_stone_chest_open",
            )

        private val TUNNEL_LADDERS =
            listOf(
                "loc.barrows_ladder",
                "loc.barrows_ladder_a",
                "loc.barrows_ladder_c",
                "loc.barrows_ladder_g",
            )
    }
}
