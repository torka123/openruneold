package org.rsmod.content.minigames.giantsfoundry

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

@Singleton
internal class FoundryService @Inject constructor(private val random: GameRandom) {
    fun state(player: Player): FoundryServiceResult = load(player)

    fun assignCommission(player: Player): FoundryServiceResult {
        val state = ready(player) ?: return FoundryServiceResult.CorruptState
        val descriptors = FoundryRules.commissionForRoll(random.of(FoundryRules.commissionPairs.size))
        val commission = FoundryCommission(state.revision.toLong() + 1L, descriptors.first, descriptors.second, System.currentTimeMillis())
        return persist(player, FoundryReducer.assign(state, commission), commission)
    }

    fun setupBestAvailableMoulds(player: Player, smithingLevel: Int): FoundryServiceResult {
        val original = ready(player) ?: return FoundryServiceResult.CorruptState
        val commission = original.commission ?: return FoundryServiceResult.Rejected("Ask Kovac for a commission first.")
        if (original.preform != null) return FoundryServiceResult.Rejected("The mould jig cannot be changed after you pour the metal.")
        var state = original
        MouldSlot.entries.forEach { slot ->
            val mould = FoundryMoulds.all
                .asSequence()
                .filter { it.slot == slot && FoundryRules.canUseMould(it, state.unlockedMoulds, smithingLevel) }
                .maxByOrNull { it.scores[commission.first] + it.scores[commission.second] }
                ?: return FoundryServiceResult.Rejected("No usable $slot mould is available.")
            state = (FoundryReducer.selectMould(state, slot, mould.key) as FoundryTransition.Accepted).state
        }
        player.saveFoundryState(state)
        return FoundryServiceResult.Changed(state)
    }

    fun addMetal(player: Player, metal: FoundryMetal, count: Int): FoundryServiceResult =
        mutate(player) { FoundryReducer.addMetal(it, metal, count) }

    fun emptyCrucible(player: Player): FoundryServiceResult = mutate(player, FoundryReducer::emptyCrucible)

    fun pour(player: Player, smithingLevel: Int): FoundryServiceResult =
        mutate(player) { state ->
            FoundryReducer.pour(state, smithingLevel) { options -> options[random.of(options.size)] }
        }

    fun pickUpPreform(player: Player): FoundryServiceResult = mutate(player, FoundryReducer::pickUpPreform)

    fun changeHeat(player: Player, amount: Int): FoundryServiceResult =
        mutate(player) { FoundryReducer.changeHeat(it, amount, System.currentTimeMillis()) }

    fun useTool(player: Player, tool: FoundryTool): FoundryServiceResult =
        mutate(player) { FoundryReducer.useTool(it, tool, System.currentTimeMillis()) }

    fun storePreform(player: Player): FoundryServiceResult = mutate(player, FoundryReducer::storePreform)

    fun takePreform(player: Player): FoundryServiceResult = mutate(player, FoundryReducer::takePreform)

    fun settle(player: Player): FoundryServiceResult {
        val state = ready(player) ?: return FoundryServiceResult.CorruptState
        val (transition, reward) = FoundryReducer.settle(state)
        return when (transition) {
            is FoundryTransition.Accepted -> {
                player.saveFoundryState(transition.state)
                FoundryServiceResult.Settled(transition.state, requireNotNull(reward))
            }
            is FoundryTransition.Rejected -> FoundryServiceResult.Rejected(transition.reason)
        }
    }

    private fun load(player: Player): FoundryServiceResult =
        when (val loaded = player.loadFoundryState()) {
            is FoundryLoad.Ready -> FoundryServiceResult.Changed(loaded.state)
            FoundryLoad.Corrupt -> FoundryServiceResult.CorruptState
        }

    private fun ready(player: Player): FoundryState? = (player.loadFoundryState() as? FoundryLoad.Ready)?.state

    private fun mutate(player: Player, action: (FoundryState) -> FoundryTransition): FoundryServiceResult {
        val state = ready(player) ?: return FoundryServiceResult.CorruptState
        return persist(player, action(state))
    }

    private fun persist(
        player: Player,
        transition: FoundryTransition,
        commission: FoundryCommission? = null,
    ): FoundryServiceResult =
        when (transition) {
            is FoundryTransition.Accepted -> {
                player.saveFoundryState(transition.state)
                commission?.let { FoundryServiceResult.Assigned(transition.state, it) }
                    ?: FoundryServiceResult.Changed(transition.state)
            }
            is FoundryTransition.Rejected -> FoundryServiceResult.Rejected(transition.reason)
        }
}

internal sealed interface FoundryServiceResult {
    data class Assigned(val state: FoundryState, val commission: FoundryCommission) : FoundryServiceResult
    data class Changed(val state: FoundryState) : FoundryServiceResult
    data class Settled(val state: FoundryState, val reward: FoundryReward) : FoundryServiceResult
    data class Rejected(val reason: String) : FoundryServiceResult
    data object CorruptState : FoundryServiceResult
}