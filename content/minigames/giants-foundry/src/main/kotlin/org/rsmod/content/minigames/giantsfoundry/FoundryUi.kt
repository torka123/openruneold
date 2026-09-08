package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** Bridges the Foundry reducer to the stock OSRS interfaces and per-player multi-loc transforms. */
@Singleton
internal class FoundryUi @Inject constructor(private val eventBus: EventBus) {
    fun validateAssets() {
        listOf(MOULD_JIG_INTERFACE, REWARD_SHOP_INTERFACE, FOUNDRY_HUD_INTERFACE).forEach { id ->
            requireNotNull(ServerCacheManager.getInterface(id)) { "Missing native Foundry interface $id" }
        }
        ALL_VARBITS.forEach { id ->
            requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing native Foundry varbit $id" }
        }
        requireNotNull(ServerCacheManager.fromComponent((MOULD_JIG_INTERFACE shl 16) or MOULD_LIST_COMPONENT))
        requireNotNull(ServerCacheManager.fromComponent(REWARD_BUY_COMPONENT))
    }

    fun openMouldJig(player: Player, state: FoundryState) {
        sync(player, state)
        player.ifOpenMainModal(MOULD_JIG_INTERFACE, eventBus)
        player.ifSetEvents(
            ServerCacheManager.fromComponent((MOULD_JIG_INTERFACE shl 16) or MOULD_LIST_COMPONENT),
            0..600,
            IfEvent.Op1,
        )
    }

    fun openHud(player: Player, state: FoundryState) {
        sync(player, state)
        player.ifOpenOverlay(FOUNDRY_HUD_INTERFACE, eventBus)
    }

    fun openRewardShop(player: Player, state: FoundryState) {
        sync(player, state)
        set(player, VARBIT_REWARD_FILTER, REWARD_FILTER_ALL)
        setVarp(player, VARP_REWARD_SELECTED_ITEM, NO_REWARD_SELECTED)
        player.ifOpenMainModal(REWARD_SHOP_INTERFACE, eventBus)
        player.runClientScript(REWARD_SHOP_SETUP_SCRIPT, SHOP_OPEN_INITIAL_REFRESH)
        player.ifSetEvents(
            ServerCacheManager.fromComponent(REWARD_BUY_COMPONENT),
            0..0,
            IfEvent.Op1,
        )
    }

    fun closeHud(player: Player) {
        player.ifCloseOverlay(FOUNDRY_HUD_INTERFACE, eventBus)
    }

    fun sync(player: Player, state: FoundryState) {
        set(player, VARBIT_COMMISSION_FIRST, state.commission?.first?.clientValue ?: 0)
        set(player, VARBIT_COMMISSION_SECOND, state.commission?.second?.clientValue ?: 0)
        MouldSlot.entries.forEach { slot ->
            val value = state.selectedMoulds[slot]?.let { key ->
                FoundryMoulds.all.filter { it.slot == slot }.indexOfFirst { it.key == key } + 1
            } ?: 0
            set(player, slot.varbit, value.coerceAtLeast(0))
        }
        FoundryMetal.entries.forEach { metal -> set(player, metal.varbit, state.crucible.amount(metal)) }
        set(player, VARBIT_CRUCIBLE, when (state.crucible.total) {
            0 -> 0
            FoundryRules.CRUCIBLE_CAPACITY -> 2
            else -> 1
        })
        val preform = state.preform
        set(player, VARBIT_GAME_STAGE, when {
            preform?.lifecycle == PreformLifecycle.Poured -> 2
            preform != null -> 0
            state.commission != null && state.selectedMoulds.size == MouldSlot.entries.size -> 1
            else -> 0
        })
        set(player, VARBIT_STORAGE, if (preform?.lifecycle == PreformLifecycle.Stored) 1 else 0)
        set(player, VARBIT_HEAT, preform?.heat ?: 0)
        set(player, VARBIT_PROGRESS, preform?.progress ?: 0)
        set(player, VARBIT_SECTION_SCORE, preform?.metalScore ?: 0)
        set(player, VARBIT_QUALITY, preform?.quality ?: 0)
        set(player, VARBIT_INITIAL_QUALITY, preform?.maxQuality ?: 0)
        val hudSeeds = preform?.stages?.let(FoundryRules::nativeHudSeeds).orEmpty()
        SECTION_PLAN_VARBITS.forEachIndexed { index, varbit ->
            set(player, varbit, hudSeeds.getOrNull(index)?.clientValue ?: 0)
        }
        setVarp(player, VARP_REPUTATION, state.reputation)
    }

    private fun set(player: Player, id: Int, value: Int) {
        VarPlayerIntMapSetter.set(player, requireNotNull(ServerCacheManager.getVarbit(id)), value)
    }

    private fun setVarp(player: Player, id: Int, value: Int) {
        VarPlayerIntMapSetter.set(player, requireNotNull(ServerCacheManager.getVarp(id)), value)
    }

    private val FoundryDescriptor.clientValue: Int
        get() = when (this) {
            FoundryDescriptor.NARROW -> 1
            FoundryDescriptor.LIGHT -> 2
            FoundryDescriptor.FLAT -> 3
            FoundryDescriptor.BROAD -> 4
            FoundryDescriptor.HEAVY -> 5
            FoundryDescriptor.SPIKED -> 6
        }

    private val MouldSlot.varbit: Int
        get() = when (this) {
            MouldSlot.FORTE -> VARBIT_FORTE
            MouldSlot.BLADE -> VARBIT_BLADE
            MouldSlot.TIP -> VARBIT_TIP
        }

    private val FoundryMetal.varbit: Int
        get() = when (this) {
            FoundryMetal.BRONZE -> 13_931
            FoundryMetal.IRON -> 13_932
            FoundryMetal.STEEL -> 13_933
            FoundryMetal.MITHRIL -> 13_934
            FoundryMetal.ADAMANT -> 13_935
            FoundryMetal.RUNE -> 13_936
        }

    private val FoundryTool.clientValue: Int
        get() = when (this) {
            FoundryTool.TRIP_HAMMER -> 0
            FoundryTool.GRINDSTONE -> 1
            FoundryTool.POLISHING_WHEEL -> 2
        }

    private companion object {
        const val MOULD_JIG_INTERFACE = 718
        const val REWARD_SHOP_INTERFACE = 753
        const val FOUNDRY_HUD_INTERFACE = 754
        const val MOULD_LIST_COMPONENT = 9
        const val REWARD_BUY_COMPONENT = (REWARD_SHOP_INTERFACE shl 16) or 15

        const val VARBIT_COMMISSION_FIRST = 13_907
        const val VARBIT_COMMISSION_SECOND = 13_908
        const val VARBIT_FORTE = 13_910
        const val VARBIT_BLADE = 13_911
        const val VARBIT_TIP = 13_912
        const val VARBIT_GAME_STAGE = 13_914
        const val VARBIT_CRUCIBLE = 13_937
        const val VARBIT_STORAGE = 13_947
        const val VARBIT_HEAT = 13_948
        const val VARBIT_PROGRESS = 13_949
        const val VARBIT_SECTION_SCORE = 13_938
        const val VARBIT_QUALITY = 13_939
        const val VARBIT_INITIAL_QUALITY = 13_950
        val SECTION_PLAN_VARBITS = listOf(13_940, 13_941, 13_942, 13_943, 13_944, 13_945)
        const val VARP_REPUTATION = 3_436
        const val VARP_REWARD_SELECTED_ITEM = 3_435
        const val VARBIT_REWARD_FILTER = 13_915
        const val REWARD_FILTER_ALL = 1
        const val NO_REWARD_SELECTED = -1
        const val REWARD_SHOP_SETUP_SCRIPT = 6_083
        const val SHOP_OPEN_INITIAL_REFRESH = 0

        val ALL_VARBITS =
            listOf(
                VARBIT_COMMISSION_FIRST,
                VARBIT_COMMISSION_SECOND,
                VARBIT_FORTE,
                VARBIT_BLADE,
                VARBIT_TIP,
                VARBIT_GAME_STAGE,
                VARBIT_CRUCIBLE,
                VARBIT_SECTION_SCORE,
                VARBIT_QUALITY,
                VARBIT_INITIAL_QUALITY,
                VARBIT_STORAGE,
                VARBIT_HEAT,
                VARBIT_PROGRESS,
                VARBIT_REWARD_FILTER,
                *SECTION_PLAN_VARBITS.toTypedArray(),
                13_931,
                13_932,
                13_933,
                13_934,
                13_935,
                13_936,
            )
    }
}
