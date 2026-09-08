package org.rsmod.content.minigames.clanhall

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfCloseSub
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ClanHallWorld
@Inject
constructor(
    private val halls: ClanHallService,
    private val nativeUi: ClanHallNativeUiService,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        val portal = requireLoc(ClanHallAssets.HUB_PORTAL)
        val exit = requireLoc(ClanHallAssets.MEDIEVAL_EXIT)
        val coffer = requireLoc(ClanHallAssets.COFFER)
        val chestClosed = requireLoc(ClanHallAssets.PARTY_CHEST_CLOSED)
        val chestOpen = requireLoc(ClanHallAssets.PARTY_CHEST_OPEN)
        val lever = requireLoc(ClanHallAssets.PARTY_CHEST_LEVER)

        onOpLoc1(portal) {
            arriveDelay()
            halls.enterPreferred(player)
        }
        onOpLoc2(portal) {
            arriveDelay()
            halls.enter(player, ClanHallRole.Member)
        }
        onOpLoc3(portal) {
            arriveDelay()
            halls.enter(player, ClanHallRole.Guest)
        }
        onOpLoc1(exit) {
            if (!nativeUi.closePartyChest(player)) return@onOpLoc1
            arriveDelay()
            if (!halls.leave(player)) player.mes("You are not inside a Clan Hall.")
        }
        onOpLoc1(coffer) {
            arriveDelay()
            nativeUi.openCoffer(this)
        }
        onOpLoc1(chestClosed) {
            arriveDelay()
            nativeUi.openPartyChest(this)
        }
        onOpLoc2(chestOpen) {
            arriveDelay()
            nativeUi.openPartyChest(this)
        }
        onOpLoc4(chestClosed) { halls.checkPartyChestPermissions(this) }
        onOpLoc4(chestOpen) { halls.checkPartyChestPermissions(this) }
        onOpLoc1(lever) {
            arriveDelay()
            halls.pullPartyChestLever(this)
        }
        ClanHallAssets.PARTY_BALLOONS.forEach { id ->
            onOpLoc1(requireLoc(id)) {
                arriveDelay()
                halls.burstPartyBalloon(this, it.loc)
            }
        }
        ClanHallNativeUi.CofferWithdrawButtons.forEach { (child, preset) ->
            onProtectedEvent<IfModalButton>(
                ClanHallNativeUi.packed(ClanHallNativeUi.COFFER_MAIN, child)
            ) {
                nativeUi.withdrawCoffer(this, preset)
            }
        }
        ClanHallNativeUi.CofferDepositButtons.forEach { (child, preset) ->
            onProtectedEvent<IfModalButton>(
                ClanHallNativeUi.packed(ClanHallNativeUi.COFFER_SIDE, child)
            ) {
                nativeUi.depositCoffer(this, preset)
            }
        }
        ClanHallNativeUi.PartyQuantityButtons.forEach { (child, mode) ->
            onProtectedEvent<IfModalButton>(
                ClanHallNativeUi.packed(ClanHallNativeUi.PARTY_MAIN, child)
            ) {
                nativeUi.selectPartyQuantity(this, mode)
            }
        }
        onProtectedEvent<IfModalButton>(
            ClanHallNativeUi.packed(ClanHallNativeUi.PARTY_SIDE, ClanHallNativeUi.PARTY_SIDE_ITEMS)
        ) { event ->
            nativeUi.depositPartyItem(this, event.comsub)
        }
        onProtectedEvent<IfModalButton>(
            ClanHallNativeUi.packed(ClanHallNativeUi.PARTY_MAIN, ClanHallNativeUi.PARTY_OFFER_ITEMS)
        ) { event ->
            nativeUi.returnPartyItem(this, event.comsub)
        }
        onProtectedEvent<IfModalButton>(
            ClanHallNativeUi.packed(ClanHallNativeUi.PARTY_MAIN, ClanHallNativeUi.PARTY_CONFIRM)
        ) {
            nativeUi.confirmPartyOffer(this)
        }
        onEvent<IfCloseSub>(ClanHallNativeUi.PARTY_MAIN) { nativeUi.closePartyChest(player) }

        onEvent<InstancePlayerLeaveEvent>(instanceEventId(CLAN_HALL_INSTANCE_KEY)) {
            nativeUi.closePartyChest(player)
            protectedAccess.launchLenient(player) { halls.membershipEnded(player, instanceId) }
        }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == CLAN_HALL_INSTANCE_KEY) {
                nativeUi.closePartyChest(player)
                protectedAccess.launchLenient(player) { halls.membershipEnded(player, instanceId) }
            }
        }
        onEvent<InstanceEndedEvent>(instanceEventId(CLAN_HALL_INSTANCE_KEY)) {
            halls.instanceEnded(instanceId)
        }
    }

    private fun requireLoc(id: Int): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing revision-240 Clan Hall location definition: $id"
        }
}
