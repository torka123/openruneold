package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc2
import org.rsmod.plugin.scripts.ScriptContext

internal class KrakenWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val whirlpools: KrakenWhirlpools,
    private val protectedAccess: ProtectedAccessLauncher,
) : InstanceScript(registry, KRAKEN_INSTANCE_KEY, krakenSpec(requireKrakenNpc(KrakenAssets.BOSS))) {
    override fun ScriptContext.configure() {
        whirlpools.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { whirlpools.onJoin(player, it) }
        }
        onOpNpc2(KrakenAssets.BOSS_WHIRLPOOL) {
            arriveDelay()
            whirlpools.disturb(player, it.npc)
        }
        onOpNpc2(KrakenAssets.TENTACLE_WHIRLPOOL) {
            arriveDelay()
            whirlpools.disturb(player, it.npc)
        }
        onOpLoc2(requireLoc(KrakenAssets.PUBLIC_CREVICE)) {
            arriveDelay()
            enterKraken()
        }
        onOpLoc1(requireLoc(KrakenAssets.INSIDE_CREVICE)) {
            arriveDelay()
            leaveKraken()
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(KRAKEN_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveKraken() }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }
        for (command in listOf("kraken", "krak")) {
            onCommand(command) {
                requiredRights = Rights.ADMINISTRATOR
                desc = "Create and enter a private Kraken encounter"
                invalidArgs = "Use as ::" + command
                cheat {
                    if (args.isEmpty())
                        protectedAccess.launch(
                            player,
                            "Finish what you are doing before entering the Kraken's cove.",
                        ) {
                            enterKraken()
                        }
                }
            }
        }
        onCommand("leavekraken") {
            desc = "Leave your current private Kraken encounter"
            invalidArgs = "Use as ::leavekraken"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { leaveKraken() } }
        }
    }

    internal suspend fun ProtectedAccess.enterKraken() {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering the Kraken's cove.")
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
    }

    internal suspend fun ProtectedAccess.leaveKraken() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside a Kraken encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key == key) return
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        mes("You leave the Kraken's private cove.")
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing Kraken location definition: $id"
        }
}
