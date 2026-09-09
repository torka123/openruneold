package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal class VorkathWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
    private val encounters: VorkathEncounterManager,
    private val storage: VorkathDeathStorage,
    private val access: VorkathAccessPolicy,
) :
    InstanceScript(
        registry,
        VORKATH_INSTANCE_KEY,
        vorkathSpec(requireNotNull(ServerCacheManager.getNpc(8061))),
    ) {
    override fun ScriptContext.configure() {
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(VORKATH_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveVorkath(32) }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }
        encounters.validateAssets()
        onEvent<NpcStateEvents.Create> {
            if (
                npc.type.internalName == VorkathAssets.SLEEPING ||
                    npc.type.internalName == VorkathAssets.SLEEPING_NOOP
            ) {
                npc.mode = null
                npc.movementLocked = true
            }
        }
        VORKATH_CRATER_ENTRANCE_LOC_IDS.forEach { id ->
            onOpLoc1(requireNotNull(ServerCacheManager.getObject(id))) {
                arriveDelay()
                val onPublicApproach =
                    VorkathRules.isPublicCraterApproach(
                        worldX = player.coords.x,
                        worldZ = player.coords.z,
                        level = player.coords.level,
                    )
                if (encounters.isActive(player) || onPublicApproach) {
                    val crossingX =
                        player.coords.lx.coerceIn(
                            VORKATH_WALL_MIN_LOCAL_X,
                            VORKATH_WALL_MAX_LOCAL_X,
                        )
                    crossIceWall(crossingX)
                } else {
                    player.mes("Nothing interesting happens.")
                }
            }
        }
        onOpNpc1(VorkathAssets.SLEEPING) {
            arriveDelay()
            encounters.poke(player, it.npc)
        }

        val ungaelTorfinn = listOf(VorkathAssets.TORFINN, VorkathAssets.TORFINN_COLLECT)
        val rellekkaTorfinn =
            listOf(VorkathAssets.TORFINN_RELLEKKA, VorkathAssets.TORFINN_COLLECT_RELLEKKA)
        ungaelTorfinn.forEach { torfinn ->
            onOpNpc1(torfinn) { talkToTorfinn(it.npc) }
            onOpNpc3(torfinn) {
                arriveDelay()
                travelVorkath(VORKATH_RELLEKKA)
            }
            onOpNpc4(torfinn) { reclaimFromTorfinn(it.npc) }
        }
        rellekkaTorfinn.forEach { torfinn ->
            onOpNpc1(torfinn) { talkToTorfinn(it.npc) }
            onOpNpc3(torfinn) {
                arriveDelay()
                travelVorkath(VORKATH_OUTSIDE)
            }
            onOpNpc4(torfinn) { reclaimFromTorfinn(it.npc) }
        }

        onCommand("vorkath") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport outside the real Ungael Vorkath arena"
            invalidArgs = "Use as ::vorkath"
            cheat {
                if (args.isEmpty())
                    protectedAccess.launch(player) { travelVorkath(VORKATH_OUTSIDE) }
            }
        }
        onCommand("vorkathdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the current private Vorkath encounter state"
            invalidArgs = "Use as ::vorkathdebug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
        onCommand("vorkathdev") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Force a no-reward Vorkath mechanic for live testing"
            invalidArgs = "Use ::vorkathdev status|wake|special acid|spawn|attack <type>|reset|kill"
            cheat {
                val usage = {
                    player.mes(
                        "::vorkathdev status|wake|special acid|spawn|attack <type>|reset|kill"
                    )
                }
                when (args.firstOrNull()?.lowercase()) {
                    "status" -> player.mes(encounters.debugStatus(player))
                    "wake" -> encounters.debugWake(player)
                    "special" ->
                        when (args.getOrNull(1)?.lowercase()) {
                            "acid" -> encounters.debugSpecial(player, VorkathSpecial.ACID)
                            "spawn",
                            "zombified" ->
                                encounters.debugSpecial(player, VorkathSpecial.ZOMBIFIED_SPAWN)
                            else -> usage()
                        }
                    "attack" -> {
                        val attack =
                            when (args.getOrNull(1)?.lowercase()) {
                                "melee" -> VorkathStandardAttack.MELEE
                                "ranged",
                                "range" -> VorkathStandardAttack.RANGED
                                "magic",
                                "mage" -> VorkathStandardAttack.MAGIC
                                "dragonfire",
                                "fire" -> VorkathStandardAttack.DRAGONFIRE
                                "venom" -> VorkathStandardAttack.VENOM_DRAGONFIRE
                                "prayer",
                                "prayer-disable" -> VorkathStandardAttack.PRAYER_DRAGONFIRE
                                "fireball",
                                "bomb" -> VorkathStandardAttack.FIREBALL
                                else -> null
                            }
                        if (attack == null) usage() else encounters.debugAttack(player, attack)
                    }
                    "reset" -> encounters.debugReset(player)
                    "kill" -> encounters.debugKill(player)
                    else -> usage()
                }
            }
        }
    }

    private suspend fun ProtectedAccess.crossIceWall(localX: Int) {
        if (encounters.isActive(player)) {
            val destination = encounters.wallTile(player, localX, inside = false)
            if (destination == null) {
                player.mes("The way over the ice chunks is unavailable.")
                return
            }
            anim(VorkathAssets.ICE_WALL_JUMP_ANIM)
            exactMove(
                player.coords,
                destination,
                delay1 = 0,
                delay2 = VORKATH_WALL_CROSS_CLIENT_CYCLES,
                dir = constants.em_face_south,
                teleportType = TeleportType.Exempt,
            )
            delay(2)
            leaveVorkath(localX)
            return
        }
        if (!access.canAccess(player)) {
            player.mes("You must complete Dragon Slayer II before fighting Vorkath.")
            return
        }
        enterVorkath(localX)
        if (!encounters.isActive(player)) return
        val destination = encounters.wallTile(player, localX, inside = true)
        if (destination == null) {
            defaultLeaveFlow()
            return
        }
        anim(VorkathAssets.ICE_WALL_JUMP_ANIM)
        exactMove(
            player.coords,
            destination,
            delay1 = 0,
            delay2 = VORKATH_WALL_CROSS_CLIENT_CYCLES,
            dir = constants.em_face_north,
            teleportType = TeleportType.Exempt,
        )
        delay(2)
    }

    internal suspend fun ProtectedAccess.enterVorkath(localX: Int) {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering Vorkath's arena.")
            return
        }
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return
        }
        val result =
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        val entry =
            if (result is InstanceManager.Result.Created) {
                result.copy(
                    enter =
                        encounters.wallTile(result.session, localX, inside = false) ?: result.enter
                )
            } else result
        completeInstanceEntry(entry)
    }

    internal suspend fun ProtectedAccess.leaveVorkath(localX: Int) {
        if (manager.sessionForPlayer(player)?.key != key) return
        // Keep the clicked wall's X coordinate. The native teleport hook retires membership.
        val exit =
            CoordGrid(
                VORKATH_SOURCE_REGION_X * 64 + localX,
                VORKATH_SOURCE_REGION_Z * 64 + VORKATH_WALL_OUTSIDE_LOCAL_Z,
            )
        telejump(exit)
        if (coords != exit) return
        manager.sessionForPlayer(player)?.let { manager.leave(player, it, worldClock.cycle) }
        mes("You leave Vorkath's arena.")
    }

    private suspend fun ProtectedAccess.travelVorkath(destination: CoordGrid) {
        val current = manager.sessionForPlayer(player)
        if (current != null && current.key != key) {
            mes("Leave your current instance before travelling to Ungael.")
            return
        }
        telejump(destination)
        if (coords != destination) return
        if (current != null && manager.sessionForPlayer(player) === current)
            manager.leave(player, current, worldClock.cycle)
        mes(
            if (destination == VORKATH_OUTSIDE)
                "You arrive outside Vorkath's arena. Climb over the ice chunks to enter."
            else "Torfinn sails you back to Rellekka."
        )
    }

    private suspend fun ProtectedAccess.talkToTorfinn(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) {
            if (!storage.hasItems(player)) {
                chatNpc(neutral, "I am not holding any of your belongings.")
            } else {
                reclaimDialogue()
            }
        }

    private suspend fun ProtectedAccess.reclaimFromTorfinn(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) { reclaimDialogue() }

    private suspend fun Dialogue.reclaimDialogue() {
        if (!storage.hasItems(player)) {
            chatNpc(neutral, "I am not holding any of your belongings.")
            return
        }
        chatNpc(neutral, "I recovered your belongings from Ungael. My fee is 100,000 coins.")
        val choice = choice2("Pay 100,000 coins.", 1, "Not now.", 2)
        if (choice == 1) storage.reclaim(player)
    }
}
