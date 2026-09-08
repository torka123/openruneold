package org.rsmod.content.minigames.clanhall

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invCompress
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.net.social.ClanDomainService
import org.rsmod.api.net.social.ClanManagementResult
import org.rsmod.api.net.social.ClanPartyChestEntry
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanSnapshot
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj

/**
 * Server-authoritative bindings for the stock revision-240 Clan Hall interfaces.
 *
 * Coffer balance is transmitted by [ClanChatService] as VarClan 3. All mutations still pass through
 * [ClanDomainService], so the interface is only a view/controller over canonical state.
 */
@Singleton
@OptIn(UncheckedType::class)
internal class ClanHallNativeUiService
@Inject
constructor(
    private val instances: InstanceManager,
    private val chat: ClanChatService,
    private val domain: ClanDomainService,
) {
    private val partyViews = mutableMapOf<Long, PartyChestView>()

    suspend fun openCoffer(access: ProtectedAccess) {
        val clan = memberClan(access.player) ?: return
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot use a clan coffer.")
            return
        }
        val coffer = resultOrMessage(access, domain.coffer(access.player)) ?: return
        access.invTransmit(access.inv)
        access.ifOpenMainSidePair(ClanHallNativeUi.COFFER_MAIN, ClanHallNativeUi.COFFER_SIDE)
        enableCofferControls(access.player)
        access.mes("The clan coffer contains ${coffer.balance} coins.")
        check(clan.member(access.player.characterId) != null)
    }

    suspend fun depositCoffer(access: ProtectedAccess, preset: CofferAmountPreset) {
        memberClan(access.player) ?: return
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot use a clan coffer.")
            return
        }
        val available = access.player.inv.count("obj.coins")
        val amount =
            resolveCofferAmount(access, preset, available.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong())
        if (amount <= 0L) return
        if (access.player.invDel(access.player.inv, "obj.coins", amount.toInt()).failure) {
            access.mes("You do not have that many coins.")
            return
        }
        when (val result = domain.depositCoffer(access.player, amount)) {
            is ClanManagementResult.Success ->
                access.mes("You deposit $amount coins. New balance: ${result.value.balance}.")
            is ClanManagementResult.Denied -> {
                check(
                    access.player.invAdd(access.player.inv, "obj.coins", amount.toInt()).success
                ) {
                    "Clan coffer deposit rollback failed for ${access.player.characterId}."
                }
                access.mes(result.message)
            }
        }
    }

    suspend fun withdrawCoffer(access: ProtectedAccess, preset: CofferAmountPreset) {
        memberClan(access.player) ?: return
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot use a clan coffer.")
            return
        }
        val snapshot = resultOrMessage(access, domain.coffer(access.player)) ?: return
        val amount =
            resolveCofferAmount(access, preset, snapshot.balance)
                .coerceAtMost(Int.MAX_VALUE.toLong())
        if (amount <= 0L) return
        val currentCoins = access.player.inv.count("obj.coins")
        if (currentCoins == 0 && access.player.inv.freeSpace() == 0) {
            access.mes("You do not have enough inventory space for those coins.")
            return
        }
        if (currentCoins.toLong() + amount > Int.MAX_VALUE) {
            access.mes("That would create too many coins in one stack.")
            return
        }
        val requestId = UUID.randomUUID()
        when (val result = domain.withdrawCoffer(access.player, amount, requestId)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                check(
                    access.player.invAdd(access.player.inv, "obj.coins", amount.toInt()).success
                ) {
                    "Preflighted clan coffer withdrawal could not be delivered."
                }
                access.mes("You withdraw $amount coins. New balance: ${result.value.balance}.")
                chat.publishMandatorySystemNotice(
                    actor = access.player,
                    eventToken = "coffer-withdraw:$requestId",
                    message = "withdrew $amount coins from the clan coffer.",
                )
            }
        }
    }

    suspend fun openPartyChest(access: ProtectedAccess) {
        val clan = memberClan(access.player) ?: return
        if (!closePartyChest(access.player)) return
        val offer = access.tempInv
        if (offer.isNotEmpty()) {
            access.mes("Finish your other item transfer before opening the party chest.")
            return
        }
        val entries = resultOrMessage(access, domain.partyChest(access.player)) ?: return
        val chest = partyChestInventory(entries)
        val view = PartyChestView(access.player, clan.clanId, chest, offer)
        partyViews[access.player.characterId.toLong()] = view

        access.player.startInvTransmit(access.inv)
        access.player.startInvTransmit(offer)
        access.player.startInvTransmit(chest)
        access.ifOpenMainSidePair(ClanHallNativeUi.PARTY_MAIN, ClanHallNativeUi.PARTY_SIDE)
        enablePartyControls(access.player)
        UpdateInventory.updateInvFull(access.player, chest)
        UpdateInventory.updateInvFull(access.player, offer)
        UpdateInventory.updateInvFull(access.player, access.inv)
    }

    suspend fun selectPartyQuantity(access: ProtectedAccess, mode: PartyQuantityMode) {
        val view = activeView(access.player) ?: return
        view.quantityMode = mode
        if (mode == PartyQuantityMode.Custom) {
            val selected = access.numberDialog("How many items would you like to move?")
            if (selected <= 0) return
            view.customQuantity = selected
        }
    }

    fun closePartyChest(player: Player): Boolean {
        val playerKey = player.characterId.toLong()
        val view = partyViews.remove(playerKey) ?: return true
        val restored = player.invMoveAll(from = view.offer, into = player.inv)
        if (!restored.success) {
            partyViews[playerKey] = view
            player.mes("Make inventory space before closing the party chest.")
            return false
        }
        player.stopInvTransmit(view.chest)
        player.stopInvTransmit(view.offer)
        return true
    }

    suspend fun depositPartyItem(access: ProtectedAccess, slot: Int) {
        val view = activeView(access.player) ?: return
        val clan = memberClan(access.player) ?: return
        val denial = permissionDenial(access.player, clan, ClanPermissionKey.HallPartyChestDeposit)
        if (denial != null) {
            access.mes(denial)
            return
        }
        if (access.player.isAnyIronman) {
            access.mes("Iron players cannot place items in the clan party chest.")
            return
        }
        val item = access.inv[slot] ?: return
        if (!getInvObj(item).tradeable) {
            access.mes("Only tradeable items can be placed in the clan party chest.")
            return
        }
        val count = view.resolveQuantity(item.count)
        val result = access.invMoveFromSlot(access.inv, view.offer, slot, count)
        if (result.noneCompleted()) access.mes("There is not enough room in your offer.")
    }

    fun returnPartyItem(access: ProtectedAccess, slot: Int) {
        val view = activeView(access.player) ?: return
        val item = view.offer[slot] ?: return
        val result =
            access.invMoveFromSlot(view.offer, access.inv, slot, view.resolveQuantity(item.count))
        if (result.noneCompleted()) {
            access.mes("You do not have enough inventory space.")
            return
        }
        access.player.invCompress(view.offer)
    }

    suspend fun confirmPartyOffer(access: ProtectedAccess) {
        val view = activeView(access.player) ?: return
        val clan = memberClan(access.player) ?: return
        val denial = permissionDenial(access.player, clan, ClanPermissionKey.HallPartyChestDeposit)
        if (denial != null) {
            access.mes(denial)
            return
        }
        if (view.offer.isEmpty()) {
            access.mes("You have not offered any items.")
            return
        }
        var latest: List<ClanPartyChestEntry>? = null
        for (slot in view.offer.indices) {
            val item = view.offer[slot] ?: continue
            when (
                val result = domain.addPartyChestItem(access.player, item.id, item.count.toLong())
            ) {
                is ClanManagementResult.Success -> {
                    latest = result.value
                    view.offer[slot] = null
                }
                is ClanManagementResult.Denied -> {
                    access.mes(result.message)
                    break
                }
            }
        }
        access.player.invCompress(view.offer)
        latest?.let { refreshPartyViews(view.clanId, it) }
        if (view.offer.isEmpty()) access.mes("Your items are now in the clan party chest.")
    }

    private fun enableCofferControls(player: Player) {
        ClanHallNativeUi.CofferWithdrawButtons.keys.forEach { child ->
            enableButton(player, ClanHallNativeUi.COFFER_MAIN, child)
        }
        ClanHallNativeUi.CofferDepositButtons.keys.forEach { child ->
            enableButton(player, ClanHallNativeUi.COFFER_SIDE, child)
        }
    }

    private fun enablePartyControls(player: Player) {
        ClanHallNativeUi.PartyQuantityButtons.keys.forEach { child ->
            enableButton(player, ClanHallNativeUi.PARTY_MAIN, child)
        }
        enableButton(player, ClanHallNativeUi.PARTY_MAIN, ClanHallNativeUi.PARTY_CONFIRM)
        enableInventory(player, ClanHallNativeUi.PARTY_MAIN, ClanHallNativeUi.PARTY_OFFER_ITEMS, 8)
        enableInventory(player, ClanHallNativeUi.PARTY_SIDE, ClanHallNativeUi.PARTY_SIDE_ITEMS, 28)
    }

    private fun enableButton(player: Player, interfaceId: Int, child: Int) {
        player.ifSetEvents(component(interfaceId, child), 0..0, IfEvent.Op1)
    }

    private fun enableInventory(player: Player, interfaceId: Int, child: Int, size: Int) {
        player.ifSetEvents(component(interfaceId, child), 0 until size, IfEvent.Op1)
    }

    private fun component(interfaceId: Int, child: Int): ComponentType =
        requireNotNull(ServerCacheManager.getInterface(interfaceId)?.components?.get(child)) {
            "Missing revision-240 component $interfaceId:$child"
        }

    private suspend fun resolveCofferAmount(
        access: ProtectedAccess,
        preset: CofferAmountPreset,
        allAmount: Long,
    ): Long =
        when (preset) {
            CofferAmountPreset.One -> 1L
            CofferAmountPreset.Thousand -> 1_000L
            CofferAmountPreset.Million -> 1_000_000L
            CofferAmountPreset.TenMillion -> 10_000_000L
            CofferAmountPreset.Custom -> access.numberDialog("How many coins?").toLong()
            CofferAmountPreset.All -> allAmount
        }

    private fun activeView(player: Player): PartyChestView? {
        if (!insideHall(player)) {
            player.mes("You must be inside a Clan Hall to do that.")
            return null
        }
        return partyViews[player.characterId.toLong()]
            ?: run {
                player.mes("Open the party chest first.")
                null
            }
    }

    private fun memberClan(player: Player): ClanSnapshot? {
        if (!insideHall(player)) {
            player.mes("You must be inside a Clan Hall to do that.")
            return null
        }
        val clan = chat.ownClan(player)
        if (clan == null || clan.member(player.characterId) == null) {
            player.mes("Only ranked clan members can use this.")
            return null
        }
        return clan
    }

    private fun insideHall(player: Player): Boolean =
        instances.sessionForPlayer(player)?.key == CLAN_HALL_INSTANCE_KEY

    private fun permissionDenial(
        player: Player,
        clan: ClanSnapshot,
        permission: ClanPermissionKey,
    ): String? {
        val rank =
            clan.member(player.characterId)?.rank ?: return "You are not a ranked clan member."
        return ClanHallRules.permissionDenial(
            ClanHallRole.Member,
            rank,
            clan.permissionSet,
            permission,
        )
    }

    private fun partyChestInventory(entries: List<ClanPartyChestEntry>): Inventory {
        val type = requireNotNull(ServerCacheManager.getInventory(ClanHallNativeUi.PARTY_CHEST_INV))
        return Inventory(type, arrayOfNulls(type.size)).also { inventory ->
            entries.take(type.size).forEachIndexed { slot, entry ->
                inventory[slot] =
                    InvObj(
                        entry.itemId,
                        entry.quantity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
            }
            inventory.clearModifiedSlots()
        }
    }

    private fun refreshPartyViews(clanId: Long, entries: List<ClanPartyChestEntry>) {
        partyViews.values
            .filter { it.clanId == clanId }
            .forEach { view ->
                view.chest.fillNulls()
                entries.take(view.chest.size).forEachIndexed { slot, entry ->
                    view.chest[slot] =
                        InvObj(
                            entry.itemId,
                            entry.quantity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        )
                }
                UpdateInventory.updateInvFull(view.player, view.chest)
            }
    }

    private fun <T> resultOrMessage(access: ProtectedAccess, result: ClanManagementResult<T>): T? =
        when (result) {
            is ClanManagementResult.Success -> result.value
            is ClanManagementResult.Denied -> {
                access.mes(result.message)
                null
            }
        }

    private data class PartyChestView(
        val player: Player,
        val clanId: Long,
        val chest: Inventory,
        val offer: Inventory,
        var quantityMode: PartyQuantityMode = PartyQuantityMode.One,
        var customQuantity: Int = 1,
    ) {
        fun resolveQuantity(available: Int): Int =
            quantityMode.resolve(customQuantity).coerceIn(1, available)
    }
}

internal enum class CofferAmountPreset {
    One,
    Thousand,
    Million,
    TenMillion,
    Custom,
    All,
}

internal enum class PartyQuantityMode {
    One,
    Five,
    Custom,
    All;

    fun resolve(custom: Int): Int =
        when (this) {
            One -> 1
            Five -> 5
            Custom -> custom.coerceAtLeast(1)
            All -> Int.MAX_VALUE
        }
}

internal object ClanHallNativeUi {
    const val COFFER_MAIN = 696
    const val COFFER_SIDE = 697
    const val PARTY_MAIN = 783
    const val PARTY_SIDE = 266

    const val PARTY_CHEST_INV = 916
    const val PARTY_OFFER_INV = 92
    const val PLAYER_INV = 93

    const val PARTY_SIDE_ITEMS = 0
    const val PARTY_CHEST_ITEMS = 11
    const val PARTY_OFFER_ITEMS = 16
    const val PARTY_CONFIRM = 17

    val CofferWithdrawButtons =
        mapOf(
            7 to CofferAmountPreset.One,
            8 to CofferAmountPreset.Thousand,
            9 to CofferAmountPreset.Million,
            10 to CofferAmountPreset.TenMillion,
            11 to CofferAmountPreset.Custom,
            12 to CofferAmountPreset.All,
        )
    val CofferDepositButtons =
        mapOf(
            5 to CofferAmountPreset.One,
            6 to CofferAmountPreset.Thousand,
            7 to CofferAmountPreset.Million,
            8 to CofferAmountPreset.TenMillion,
            9 to CofferAmountPreset.Custom,
            10 to CofferAmountPreset.All,
        )
    val PartyQuantityButtons =
        mapOf(
            7 to PartyQuantityMode.One,
            8 to PartyQuantityMode.Five,
            9 to PartyQuantityMode.Custom,
            10 to PartyQuantityMode.All,
        )

    fun packed(interfaceId: Int, child: Int): Int = (interfaceId shl 16) or child
}
