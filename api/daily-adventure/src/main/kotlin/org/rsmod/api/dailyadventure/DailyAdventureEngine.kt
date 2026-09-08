package org.rsmod.api.dailyadventure

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public typealias DailyAdventureEligibility = (DailyAdventureTaskDefinition) -> Boolean

public fun currentDailyAdventureDay(epochMillis: Long = System.currentTimeMillis()): Long =
    Math.floorDiv(epochMillis, 86_400_000L)

public class DailyAdventureEngine(
    private val privateSalt: String,
    private val catalogue: List<DailyAdventureTaskDefinition> = DailyAdventureCatalogue.tasks,
) {
    init {
        require(privateSalt.isNotBlank()) { "Daily Adventure private salt must not be blank" }
        require(catalogue.map { it.id }.distinct().size == catalogue.size) {
            "Daily Adventure task IDs must be unique"
        }
    }

    public fun ensureCurrentDay(
        state: DailyAdventureState,
        accountUuid: String,
        currentDay: Long,
        member: Boolean,
        eligibility: DailyAdventureEligibility,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DailyAdventureDayResult {
        if (state.day > currentDay) {
            return DailyAdventureDayResult(false, false, false, 0)
        }
        if (state.day == currentDay) {
            return ensureMembershipUpgrade(
                state,
                accountUuid,
                currentDay,
                member,
                eligibility,
                nowEpochMillis,
            )
        }

        val generated =
            if (member) {
                DailyAdventurePath.entries.associateWith {
                    generatePath(accountUuid, currentDay, it, eligibility)
                }
            } else {
                emptyMap()
            }
        val consumed = applyMissedDayRules(state, currentDay)
        state.day = currentDay
        state.memberDayEntitlement = member
        state.selectedFreePath = null
        state.activePaths.clear()
        state.assignments.clear()
        state.claimedPaths.clear()
        state.seenTaskIds.clear()
        state.rerollsUsedToday = 0
        state.rerollSequence = 0
        state.streakAdvancedDay = null
        state.memberBoxGrantedDay = null
        state.recentEventIds.clear()
        state.recentRerollResults.clear()
        generated.forEach { (path, assignments) -> activate(state, path, assignments) }
        state.updatedAtEpochMillis = nowEpochMillis
        return DailyAdventureDayResult(true, false, false, consumed)
    }

    public fun selectFreePath(
        state: DailyAdventureState,
        accountUuid: String,
        currentDay: Long,
        path: DailyAdventurePath,
        eligibility: DailyAdventureEligibility,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DailyAdventureSelectionStatus {
        if (state.day != currentDay) return DailyAdventureSelectionStatus.InvalidDay
        if (state.memberDayEntitlement) return DailyAdventureSelectionStatus.MembersAlreadyActive
        val selected = state.selectedFreePath
        if (selected != null) return DailyAdventureSelectionStatus.AlreadySelected
        val assignments = generatePath(accountUuid, currentDay, path, eligibility)
        state.selectedFreePath = path
        activate(state, path, assignments)
        state.updatedAtEpochMillis = nowEpochMillis
        return DailyAdventureSelectionStatus.Selected
    }

    public fun progress(
        state: DailyAdventureState,
        currentDay: Long,
        event: DailyAdventureProgressEvent,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DailyAdventureProgressResult {
        if (state.day != currentDay || event.id.isBlank() || event.amount <= 0L) {
            return DailyAdventureProgressResult()
        }
        if (event.id in state.recentEventIds) {
            return DailyAdventureProgressResult(duplicateEvent = true)
        }
        rememberEvent(state, event.id)
        var changed = 0
        var awarded = 0L
        val completed = mutableListOf<String>()
        state.activePaths.forEach { path ->
            state.assignments[path].orEmpty().forEach { assignment ->
                if (assignment.complete || assignment.generatedDay != currentDay) return@forEach
                val definition = catalogue.firstOrNull { it.id == assignment.taskId } ?: return@forEach
                var increment = definition.matcher(event).coerceAtLeast(0L)
                if (increment == 0L) return@forEach
                val dimension = event.dimension ?: event.skill
                if (needsDimension(definition) && dimension.isNullOrBlank()) return@forEach
                if (dimension != null) {
                    val previous = assignment.dimensions[dimension] ?: 0L
                    val cap = definition.perDimensionCap
                    if (cap != null) increment = increment.coerceAtMost((cap - previous).coerceAtLeast(0L))
                    if (increment == 0L) return@forEach
                    assignment.dimensions[dimension] = saturatingAdd(previous, increment)
                }
                assignment.progress = saturatingAdd(assignment.progress, increment).coerceAtMost(assignment.goal)
                changed++
                if (assignment.progress >= assignment.goal && dimensionsSatisfied(definition, assignment)) {
                    assignment.complete = true
                    assignment.completedAtEpochMillis = nowEpochMillis
                    if (!assignment.rewardCredited) {
                        assignment.rewardCredited = true
                        state.taskPoints = saturatingAdd(state.taskPoints, assignment.reward)
                        state.lifetime.tasksCompleted = saturatingAdd(state.lifetime.tasksCompleted, 1L)
                        state.lifetime.pointsEarned = saturatingAdd(state.lifetime.pointsEarned, assignment.reward)
                        awarded = saturatingAdd(awarded, assignment.reward)
                    }
                    completed += assignment.taskId
                }
            }
        }
        if (changed > 0) state.updatedAtEpochMillis = nowEpochMillis
        return DailyAdventureProgressResult(
            changedAssignments = changed,
            completedTaskIds = completed,
            pointsAwarded = awarded,
        )
    }

    public fun reroll(
        state: DailyAdventureState,
        accountUuid: String,
        currentDay: Long,
        path: DailyAdventurePath,
        slot: Int,
        requestId: String,
        eligibility: DailyAdventureEligibility,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DailyAdventureRerollResult {
        state.recentRerollResults[requestId]?.let { return it.copy(duplicateRequest = true) }
        if (state.day != currentDay || path !in state.activePaths) {
            return DailyAdventureRerollResult(DailyAdventureRerollStatus.InvalidPath)
        }
        val assignments = state.assignments[path]
            ?: return DailyAdventureRerollResult(DailyAdventureRerollStatus.InvalidPath)
        val assignment = assignments.firstOrNull { it.slot == slot }
            ?: return DailyAdventureRerollResult(DailyAdventureRerollStatus.InvalidSlot)
        if (assignment.complete || assignment.rewardCredited) {
            return DailyAdventureRerollResult(
                DailyAdventureRerollStatus.CompletedTask,
                previousTaskId = assignment.taskId,
            )
        }
        if (state.rerollsRemaining() <= 0) {
            return DailyAdventureRerollResult(
                DailyAdventureRerollStatus.NoRerollsRemaining,
                previousTaskId = assignment.taskId,
            )
        }
        val activeIds = assignments.mapTo(mutableSetOf()) { it.taskId }
        val candidates =
            catalogue.filter { definition ->
                definition.path == path &&
                    definition.difficulty == assignment.difficulty &&
                    definition.id != assignment.taskId &&
                    definition.id !in activeIds &&
                    eligibility(definition) &&
                    constraintsSatisfied(
                        path,
                        assignments.map { existing ->
                            if (existing.slot == slot) definition else catalogueById(existing.taskId)
                        },
                        complete = true,
                    )
            }
        val unseen = candidates.filter { it.id !in state.seenTaskIds[path].orEmpty() }
        val pool = if (unseen.isNotEmpty()) unseen else candidates
        if (pool.isEmpty()) {
            return DailyAdventureRerollResult(
                DailyAdventureRerollStatus.NoEligibleReplacement,
                previousTaskId = assignment.taskId,
            )
        }
        val replacement =
            pool.minBy { definition ->
                deterministicScore(
                    accountUuid,
                    currentDay.toString(),
                    path.name,
                    slot.toString(),
                    state.rerollSequence.toString(),
                    definition.id,
                )
            }
        val oldId = assignment.taskId
        assignment.taskId = replacement.id
        assignment.resolvedTarget = null
        assignment.goal = replacement.goal
        assignment.reward = replacement.reward
        assignment.progress = 0L
        assignment.complete = false
        assignment.rewardCredited = false
        assignment.completedAtEpochMillis = null
        assignment.rerollCount++
        assignment.dimensions.clear()
        state.rerollsUsedToday++
        state.rerollSequence++
        state.seenTaskIds.getOrPut(path) { mutableSetOf() } += replacement.id
        state.lifetime.rerollsUsed = saturatingAdd(state.lifetime.rerollsUsed, 1L)
        state.updatedAtEpochMillis = nowEpochMillis
        val result =
            DailyAdventureRerollResult(
                DailyAdventureRerollStatus.Rerolled,
                previousTaskId = oldId,
                replacementTaskId = replacement.id,
            )
        rememberReroll(state, requestId, result)
        return result
    }

    public fun claim(
        state: DailyAdventureState,
        currentDay: Long,
        path: DailyAdventurePath,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DailyAdventureClaimResult {
        if (state.day != currentDay || path !in state.activePaths) {
            return DailyAdventureClaimResult(DailyAdventureClaimStatus.InvalidPath, streak = state.streak)
        }
        if (path in state.claimedPaths) {
            return DailyAdventureClaimResult(DailyAdventureClaimStatus.AlreadyClaimed, streak = state.streak)
        }
        val complete = state.assignments[path].orEmpty().count { it.complete }
        if (complete < REQUIRED_COMPLETIONS) {
            return DailyAdventureClaimResult(DailyAdventureClaimStatus.NotEnoughTasks, streak = state.streak)
        }
        val advanced = state.streakAdvancedDay != currentDay
        if (advanced) {
            state.streak = (state.streak + 1).coerceAtLeast(1)
            state.bestStreak = maxOf(state.bestStreak, state.streak)
            state.lastClaimDay = currentDay
            state.streakAdvancedDay = currentDay
        }
        val bonus = DailyAdventureRewards.streakBonus(state.streak)
        state.taskPoints = saturatingAdd(state.taskPoints, bonus)
        state.lifetime.pointsEarned = saturatingAdd(state.lifetime.pointsEarned, bonus)
        state.lifetime.pathsClaimed = saturatingAdd(state.lifetime.pathsClaimed, 1L)
        state.claimedPaths += path
        if (state.memberDayEntitlement && state.memberBoxGrantedDay != currentDay) {
            state.memberBoxGrantedDay = currentDay
            state.pendingMemberBoxes++
        }
        state.updatedAtEpochMillis = nowEpochMillis
        return DailyAdventureClaimResult(
            DailyAdventureClaimStatus.Claimed,
            bonusPoints = bonus,
            streak = state.streak,
            streakAdvanced = advanced,
        )
    }

    public fun spendPoints(state: DailyAdventureState, amount: Long, nowEpochMillis: Long): Boolean {
        if (amount <= 0L || state.taskPoints < amount) return false
        state.taskPoints -= amount
        state.lifetime.pointsSpent = saturatingAdd(state.lifetime.pointsSpent, amount)
        state.updatedAtEpochMillis = nowEpochMillis
        return true
    }

    private fun ensureMembershipUpgrade(
        state: DailyAdventureState,
        accountUuid: String,
        currentDay: Long,
        member: Boolean,
        eligibility: DailyAdventureEligibility,
        nowEpochMillis: Long,
    ): DailyAdventureDayResult {
        if (!member || state.memberDayEntitlement) {
            return DailyAdventureDayResult(false, false, false, 0)
        }
        val missing = DailyAdventurePath.entries.filter { it !in state.activePaths }
        val generated = missing.associateWith { generatePath(accountUuid, currentDay, it, eligibility) }
        state.memberDayEntitlement = true
        generated.forEach { (path, assignments) -> activate(state, path, assignments) }
        var boxQueued = false
        if (state.claimedPaths.isNotEmpty() && state.memberBoxGrantedDay != currentDay) {
            state.memberBoxGrantedDay = currentDay
            state.pendingMemberBoxes++
            boxQueued = true
        }
        state.updatedAtEpochMillis = nowEpochMillis
        return DailyAdventureDayResult(false, true, boxQueued, 0)
    }

    private fun applyMissedDayRules(state: DailyAdventureState, currentDay: Long): Int {
        if (state.day == DailyAdventureState.UNINITIALIZED_DAY || state.streak <= 0) return 0
        var consumed = 0
        var day = state.day
        while (day < currentDay) {
            if (state.lastClaimDay != day) {
                if (state.streakProtectionCharges > 0) {
                    state.streakProtectionCharges--
                    consumed++
                    state.lifetime.protectionChargesConsumed =
                        saturatingAdd(state.lifetime.protectionChargesConsumed, 1L)
                } else {
                    state.streak = 0
                }
            }
            day++
        }
        return consumed
    }

    private fun generatePath(
        accountUuid: String,
        day: Long,
        path: DailyAdventurePath,
        eligibility: DailyAdventureEligibility,
    ): MutableList<DailyAdventureAssignment> {
        val difficulties =
            listOf(
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Easy,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Medium,
                DailyAdventureDifficulty.Hard,
                DailyAdventureDifficulty.Hard,
            )
        val selected = mutableListOf<DailyAdventureTaskDefinition>()
        fun search(slot: Int): Boolean {
            if (slot == difficulties.size) return constraintsSatisfied(path, selected, complete = true)
            val candidates =
                catalogue
                    .asSequence()
                    .filter { it.path == path && it.difficulty == difficulties[slot] && eligibility(it) }
                    .filter { candidate -> selected.none { it.id == candidate.id } }
                    .sortedBy { candidate ->
                        deterministicScore(accountUuid, day.toString(), path.name, slot.toString(), candidate.id)
                    }
                    .toList()
            for (candidate in candidates) {
                selected += candidate
                if (constraintsSatisfied(path, selected, complete = false) && search(slot + 1)) return true
                selected.removeAt(selected.lastIndex)
            }
            return false
        }
        check(search(0)) { "No valid Daily Adventure $path assignment set for day $day" }
        return selected.mapIndexedTo(mutableListOf()) { slot, definition ->
            DailyAdventureAssignment(
                slot = slot,
                taskId = definition.id,
                difficulty = definition.difficulty,
                generatedDay = day,
                goal = definition.goal,
                reward = definition.reward,
            )
        }
    }

    private fun constraintsSatisfied(
        path: DailyAdventurePath,
        tasks: List<DailyAdventureTaskDefinition>,
        complete: Boolean,
    ): Boolean =
        when (path) {
            DailyAdventurePath.Combat -> combatConstraints(tasks, complete)
            DailyAdventurePath.Skilling -> skillingConstraints(tasks, complete)
        }

    private fun combatConstraints(tasks: List<DailyAdventureTaskDefinition>, complete: Boolean): Boolean {
        if (tasks.countTagged(DailyAdventureTag.Pvp) > 2) return false
        if (tasks.countTagged(DailyAdventureTag.Boss) > 2) return false
        if (tasks.countTagged(DailyAdventureTag.RaidOrMinigame) > 1) return false
        if (tasks.countTagged(DailyAdventureTag.Wilderness) > 2) return false
        if (tasks.countTagged(DailyAdventureTag.GenericKill) > 1) return false
        if (tasks.countTagged(DailyAdventureTag.CombatStyle) > 1) return false
        if (tasks.count { DailyAdventureTag.Damage in it.tags && DailyAdventureTag.Boss !in it.tags && DailyAdventureTag.Pvp !in it.tags } > 1) return false
        val dependent = setOf(DailyAdventureTag.RequiresOtherPlayers, DailyAdventureTag.TimedOrSpecial, DailyAdventureTag.SpecialActivity)
        if (tasks.count { task -> task.tags.any { it in dependent } } > 2) return false
        return !complete || tasks.count { task -> task.tags.none { it in dependent } } >= 8
    }

    private fun skillingConstraints(tasks: List<DailyAdventureTaskDefinition>, complete: Boolean): Boolean {
        if (tasks.countTagged(DailyAdventureTag.MultiSkill) > 2) return false
        if (tasks.countTagged(DailyAdventureTag.Wilderness) > 1) return false
        if (tasks.countTagged(DailyAdventureTag.SpecialActivity) > 1) return false
        val directSkills = tasks.filter { it.skills.size == 1 }.map { it.skills.single() }
        if (directSkills.size != directSkills.distinct().size) return false
        if (!complete) return true
        val representedSkills = tasks.flatMapTo(mutableSetOf()) { it.skills }
        val permanent = tasks.count { DailyAdventureTag.Wilderness !in it.tags && DailyAdventureTag.SpecialActivity !in it.tags }
        return representedSkills.size >= 7 && permanent >= 8
    }

    private fun activate(
        state: DailyAdventureState,
        path: DailyAdventurePath,
        assignments: MutableList<DailyAdventureAssignment>,
    ) {
        check(assignments.size == TASKS_PER_PATH)
        state.activePaths += path
        state.assignments[path] = assignments
        state.seenTaskIds.getOrPut(path) { mutableSetOf() }.addAll(assignments.map { it.taskId })
    }

    private fun dimensionsSatisfied(
        definition: DailyAdventureTaskDefinition,
        assignment: DailyAdventureAssignment,
    ): Boolean {
        if (definition.requiredDimensions.any {
                (assignment.dimensions[it] ?: 0L) < definition.minimumPerDimension
            }
        ) {
            return false
        }
        if (definition.minimumDistinctDimensions > 0) {
            val distinct = assignment.dimensions.count { (_, amount) -> amount > 0L }
            if (distinct < definition.minimumDistinctDimensions) return false
        }
        return true
    }

    private fun needsDimension(definition: DailyAdventureTaskDefinition): Boolean =
        definition.requiredDimensions.isNotEmpty() ||
            definition.minimumDistinctDimensions > 0 ||
            definition.perDimensionCap != null

    private fun catalogueById(id: String): DailyAdventureTaskDefinition =
        checkNotNull(catalogue.firstOrNull { it.id == id }) { "Unknown Daily Adventure task $id" }

    private fun deterministicScore(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        (parts.toList() + privateSalt).forEach { part ->
            digest.update(part.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun rememberEvent(state: DailyAdventureState, eventId: String) {
        state.recentEventIds += eventId
        while (state.recentEventIds.size > DailyAdventureState.MAX_RECENT_EVENTS) {
            state.recentEventIds.removeAt(0)
        }
    }

    private fun rememberReroll(
        state: DailyAdventureState,
        requestId: String,
        result: DailyAdventureRerollResult,
    ) {
        state.recentRerollResults[requestId] = result
        while (state.recentRerollResults.size > DailyAdventureState.MAX_RECENT_REROLLS) {
            state.recentRerollResults.remove(state.recentRerollResults.keys.first())
        }
    }

    private fun List<DailyAdventureTaskDefinition>.countTagged(tag: DailyAdventureTag): Int =
        count { tag in it.tags }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    public companion object {
        public const val TASKS_PER_PATH: Int = 10
        public const val REQUIRED_COMPLETIONS: Int = 8
    }
}
