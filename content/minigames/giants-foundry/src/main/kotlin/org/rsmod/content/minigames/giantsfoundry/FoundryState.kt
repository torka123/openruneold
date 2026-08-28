package org.rsmod.content.minigames.giantsfoundry

/**
 * Pure per-player state reducer. Inventory, player attributes, interfaces, and scheduled actions
 * are adapters around this state; none may calculate their own Foundry outcome.
 */
internal data class FoundryState(
    val reputation: Int = 0,
    val completedSwords: Int = 0,
    val bestQuality: Int = 0,
    val commission: FoundryCommission? = null,
    val selectedMoulds: Map<MouldSlot, String> = emptyMap(),
    val unlockedMoulds: Set<String> = emptySet(),
    val crucible: FoundryCrucible = FoundryCrucible(),
    val preform: FoundryPreform? = null,
    val revision: Int = 0,
)

internal sealed interface FoundryTransition {
    data class Accepted(val state: FoundryState) : FoundryTransition

    data class Rejected(val reason: String) : FoundryTransition
}

internal object FoundryReducer {
    fun assign(state: FoundryState, commission: FoundryCommission): FoundryTransition {
        if (state.commission != null) return FoundryTransition.Rejected("A commission is already active.")
        if (!FoundryRules.isValidCommission(commission.first, commission.second)) {
            return FoundryTransition.Rejected("Invalid commission descriptors.")
        }
        return accept(state.copy(commission = commission))
    }

    fun selectMould(
        state: FoundryState,
        slot: MouldSlot,
        mouldKey: String,
    ): FoundryTransition {
        if (state.commission == null || state.preform != null) {
            return FoundryTransition.Rejected("Moulds can only be changed for an unpoured commission.")
        }
        val mould = FoundryMoulds.byKey[mouldKey]
            ?: return FoundryTransition.Rejected("Unknown mould.")
        if (mould.slot != slot) return FoundryTransition.Rejected("Mould does not fit this slot.")
        if (!mould.default && mould.key !in state.unlockedMoulds) {
            return FoundryTransition.Rejected("Mould is not unlocked.")
        }
        return accept(state.copy(selectedMoulds = state.selectedMoulds + (slot to mould.key)))
    }

    fun unlockMould(state: FoundryState, mouldKey: String): FoundryTransition {
        val mould = FoundryMoulds.byKey[mouldKey]
            ?: return FoundryTransition.Rejected("Unknown mould.")
        if (mould.default || mould.key in state.unlockedMoulds) {
            return FoundryTransition.Rejected("Mould is already available.")
        }
        if (state.reputation < mould.cost) return FoundryTransition.Rejected("Insufficient Foundry Reputation.")
        return accept(
            state.copy(
                reputation = state.reputation - mould.cost,
                unlockedMoulds = state.unlockedMoulds + mould.key,
            ),
        )
    }

    fun addMetal(state: FoundryState, metal: FoundryMetal, count: Int): FoundryTransition {
        val crucible = state.crucible.add(metal, count)
            ?: return FoundryTransition.Rejected("Crucible capacity would be exceeded.")
        return accept(state.copy(crucible = crucible))
    }

    fun emptyCrucible(state: FoundryState): FoundryTransition = accept(state.copy(crucible = state.crucible.empty()))

    fun pour(
        state: FoundryState,
        smithingLevel: Int,
        selectNextStation: ((List<FoundryTool>) -> FoundryTool)? = null,
    ): FoundryTransition {
        val commission = state.commission ?: return FoundryTransition.Rejected("No active commission.")
        if (state.preform != null) return FoundryTransition.Rejected("A preform is already active.")
        if (state.crucible.total != FoundryRules.CRUCIBLE_CAPACITY) {
            return FoundryTransition.Rejected("The crucible must contain exactly 28 bar-equivalents.")
        }
        val selected =
            MouldSlot.entries.map { slot ->
                val key = state.selectedMoulds[slot]
                    ?: return FoundryTransition.Rejected("A $slot mould has not been selected.")
                FoundryMoulds.byKey.getValue(key)
            }
        if (selected.any { !FoundryRules.canUseMould(it, state.unlockedMoulds, smithingLevel) }) {
            return FoundryTransition.Rejected("Smithing level or mould unlock requirement is not met.")
        }
        val metalScore = FoundryRules.metalScore(state.crucible)
        val mouldScore = FoundryRules.mouldScore(commission, selected)
        val quality = metalScore + mouldScore
        val preform =
            FoundryPreform(
                commissionId = commission.id,
                metalScore = metalScore,
                mouldScore = mouldScore,
                maxQuality = quality,
                quality = quality,
sections = FoundryRules.sectionCount(metalScore),
                stages = FoundryRules.stagePlan(commission.id, FoundryRules.sectionCount(metalScore), selectNextStation),
            )
        return accept(state.copy(crucible = FoundryCrucible(), preform = preform))
    }

    fun pickUpPreform(state: FoundryState): FoundryTransition {
        val preform = state.preform ?: return FoundryTransition.Rejected("There is no poured preform to pick up.")
        if (preform.lifecycle != PreformLifecycle.Poured) {
            return FoundryTransition.Rejected("The current preform is already being worked.")
        }
        return accept(state.copy(preform = preform.copy(lifecycle = PreformLifecycle.Processing)))
    }

    fun changeHeat(state: FoundryState, amount: Int, now: Long): FoundryTransition {
        val preform = usablePreform(state) ?: return FoundryTransition.Rejected("You need to pick up a preform first.")
        val cooled = applyPassiveCooling(preform, now)
        return accept(
            state.copy(
                preform = cooled.copy(
                    heat = (cooled.heat + amount).coerceIn(0, FoundryRules.MAX_HEAT),
                    lastHeatUpdateMillis = now,
                ),
            ),
        )
    }

    fun useTool(state: FoundryState, tool: FoundryTool, now: Long): FoundryTransition {
        val preform = usablePreform(state) ?: return FoundryTransition.Rejected("You need an active preform before using the ${tool.displayName}.")
        val cooled = applyPassiveCooling(preform, now)
        if (cooled.lifecycle == PreformLifecycle.Ruined) {
            return FoundryTransition.Rejected("This preform is ruined. Hand it in to Kovac.")
        }
        val stageIndex = FoundryRules.hudStageIndex(cooled)
        val expected = stageIndex?.let(cooled.stages::getOrNull)
            ?: return FoundryTransition.Rejected("This preform is ready to hand in.")
        if (expected != tool || !FoundryRules.inRequiredHeat(cooled, tool)) {
            val quality = (cooled.quality - FoundryRules.QUALITY_LOSS_PER_MISTAKE).coerceAtLeast(0)
            val lifecycle = if (quality == 0) PreformLifecycle.Ruined else PreformLifecycle.Processing
            return accept(
                state.copy(
                    preform = cooled.copy(
                        quality = quality,
                        lifecycle = lifecycle,
                        mistakes = cooled.mistakes + 1,
                        lastHeatUpdateMillis = now,
                    ),
                ),
            )
        }
        val nextProgress = (cooled.progress + tool.progressPerAction).coerceAtMost(FoundryRules.MAX_PROGRESS)
        val nextStage = FoundryRules.hudStageIndex(cooled.copy(progress = nextProgress)) ?: cooled.stages.size
        val complete = nextProgress >= FoundryRules.MAX_PROGRESS
        return accept(
            state.copy(
                preform = cooled.copy(
                    heat = (cooled.heat + tool.heatChangePerAction).coerceIn(0, FoundryRules.MAX_HEAT),
                    progress = nextProgress,
                    activeStage = nextStage,
                    stageProgress = nextProgress - ((nextStage * FoundryRules.MAX_PROGRESS) / cooled.stages.size),
                    lifecycle = if (complete) PreformLifecycle.Complete else PreformLifecycle.Processing,
                    lastHeatUpdateMillis = now,
                ),
            ),
        )
    }

    fun storePreform(state: FoundryState): FoundryTransition {
        val preform = usablePreform(state) ?: return FoundryTransition.Rejected("There is no active preform to store.")
        return accept(state.copy(preform = preform.copy(lifecycle = PreformLifecycle.Stored)))
    }

    fun takePreform(state: FoundryState): FoundryTransition {
        val preform = state.preform ?: return FoundryTransition.Rejected("There is no preform in storage.")
        if (preform.lifecycle != PreformLifecycle.Stored) return FoundryTransition.Rejected("There is no preform in storage.")
        return accept(state.copy(preform = preform.copy(lifecycle = PreformLifecycle.Processing)))
    }

    fun settle(state: FoundryState): Pair<FoundryTransition, FoundryReward?> {
        val commission = state.commission ?: return FoundryTransition.Rejected("No active commission.") to null
        val preform = state.preform ?: return FoundryTransition.Rejected("No preform to hand in.") to null
        if (preform.commissionId != commission.id) return FoundryTransition.Rejected("Preform belongs to another commission.") to null
        if (preform.lifecycle !in setOf(PreformLifecycle.Complete, PreformLifecycle.Ruined)) {
            return FoundryTransition.Rejected("Preform is not ready to hand in.") to null
        }
        val reward = FoundryRules.reward(preform.quality)
        val next =
            state.copy(
                reputation = state.reputation + reward.reputation,
                completedSwords = state.completedSwords + 1,
                bestQuality = maxOf(state.bestQuality, preform.quality),
                commission = null,
                selectedMoulds = emptyMap(),
                preform = null,
            )
        return accept(next) to reward
    }

    private fun accept(state: FoundryState): FoundryTransition.Accepted =
        FoundryTransition.Accepted(state.copy(revision = Math.incrementExact(state.revision)))

    private fun usablePreform(state: FoundryState): FoundryPreform? =
        state.preform?.takeIf { it.lifecycle == PreformLifecycle.Processing }

    private fun applyPassiveCooling(preform: FoundryPreform, now: Long): FoundryPreform {
        val elapsedTicks = ((now - preform.lastHeatUpdateMillis).coerceAtLeast(0) / FoundryRules.GAME_TICK_MILLIS).toInt()
        if (elapsedTicks == 0) return preform
        return preform.copy(
            heat = (preform.heat - elapsedTicks * FoundryRules.PASSIVE_COOL_PER_TICK).coerceAtLeast(0),
            lastHeatUpdateMillis = preform.lastHeatUpdateMillis + elapsedTicks * FoundryRules.GAME_TICK_MILLIS,
        )
    }
}
