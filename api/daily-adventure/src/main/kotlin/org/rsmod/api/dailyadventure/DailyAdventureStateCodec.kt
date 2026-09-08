package org.rsmod.api.dailyadventure

public object DailyAdventureStateCodec {
    public fun encode(state: DailyAdventureState): MutableMap<String, Any> =
        mutableMapOf(
            "schemaVersion" to DailyAdventureState.CURRENT_SCHEMA_VERSION,
            "day" to state.day,
            "memberDayEntitlement" to state.memberDayEntitlement,
            "selectedFreePath" to (state.selectedFreePath?.name ?: ""),
            "activePaths" to state.activePaths.map { it.name }.sorted().toMutableList(),
            "assignments" to encodeAssignments(state.assignments),
            "claimedPaths" to state.claimedPaths.map { it.name }.sorted().toMutableList(),
            "seenTaskIds" to
                state.seenTaskIds
                    .mapKeys { (path, _) -> path.name }
                    .mapValues { (_, ids) -> ids.sorted().toMutableList() }
                    .toMutableMap(),
            "rerollsUsedToday" to state.rerollsUsedToday,
            "rerollSequence" to state.rerollSequence,
            "taskPoints" to state.taskPoints,
            "streak" to state.streak,
            "bestStreak" to state.bestStreak,
            "lastClaimDay" to (state.lastClaimDay ?: NULL_DAY),
            "streakAdvancedDay" to (state.streakAdvancedDay ?: NULL_DAY),
            "streakProtectionCharges" to state.streakProtectionCharges,
            "memberBoxGrantedDay" to (state.memberBoxGrantedDay ?: NULL_DAY),
            "pendingMemberBoxes" to state.pendingMemberBoxes,
            "updatedAtEpochMillis" to state.updatedAtEpochMillis,
            "recentEventIds" to
                state.recentEventIds
                    .takeLast(DailyAdventureState.MAX_RECENT_EVENTS)
                    .toMutableList(),
            "recentRerollResults" to encodeRerolls(state.recentRerollResults),
            "activeScrolls" to state.activeScrolls.toMutableMap(),
            "lifetime" to encodeLifetime(state.lifetime),
        )

    public fun decode(raw: Any?): DailyAdventureState? {
        val map = raw as? Map<*, *> ?: return null
        val schema = map.number("schemaVersion")?.toInt() ?: return null
        if (schema !in 1..DailyAdventureState.CURRENT_SCHEMA_VERSION) return null
        val day = map.number("day")?.toLong() ?: DailyAdventureState.UNINITIALIZED_DAY
        val assignments = decodeAssignments(map["assignments"], day)
        val active =
            map.stringList("activePaths")
                .mapNotNull(::parsePath)
                .filterTo(mutableSetOf()) { path -> assignments[path]?.size == DailyAdventureEngine.TASKS_PER_PATH }
        val selected = parsePath(map["selectedFreePath"]?.toString())
        val claimed =
            map.stringList("claimedPaths")
                .mapNotNull(::parsePath)
                .filterTo(mutableSetOf()) { it in active }
        val state =
            DailyAdventureState(
                schemaVersion = DailyAdventureState.CURRENT_SCHEMA_VERSION,
                day = day,
                memberDayEntitlement = map["memberDayEntitlement"] as? Boolean ?: false,
                selectedFreePath = selected,
                activePaths = active,
                assignments = assignments.filterKeys { it in active }.toMutableMap(),
                claimedPaths = claimed,
                seenTaskIds = decodeSeen(map["seenTaskIds"]),
                rerollsUsedToday = map.nonNegativeInt("rerollsUsedToday"),
                rerollSequence = map.nonNegativeInt("rerollSequence"),
                taskPoints = map.nonNegativeLong("taskPoints"),
                streak = map.nonNegativeInt("streak"),
                bestStreak = map.nonNegativeInt("bestStreak"),
                lastClaimDay = map.nullableDay("lastClaimDay"),
                streakAdvancedDay = map.nullableDay("streakAdvancedDay"),
                streakProtectionCharges =
                    map.nonNegativeInt("streakProtectionCharges")
                        .coerceAtMost(DailyAdventureState.MAX_PROTECTION_CHARGES),
                memberBoxGrantedDay = map.nullableDay("memberBoxGrantedDay"),
                pendingMemberBoxes = map.nonNegativeInt("pendingMemberBoxes"),
                updatedAtEpochMillis = map.nonNegativeLong("updatedAtEpochMillis"),
                recentEventIds =
                    map.stringList("recentEventIds")
                        .filter(String::isNotBlank)
                        .takeLast(DailyAdventureState.MAX_RECENT_EVENTS)
                        .toMutableList(),
                recentRerollResults = decodeRerolls(map["recentRerollResults"]),
                activeScrolls = decodeLongMap(map["activeScrolls"]),
                lifetime = decodeLifetime(map["lifetime"]),
            )
        state.activePaths.forEach { path ->
            state.seenTaskIds.getOrPut(path) { mutableSetOf() }.addAll(
                state.assignments[path].orEmpty().map { it.taskId }
            )
        }
        state.bestStreak = maxOf(state.bestStreak, state.streak)
        state.rerollsUsedToday = state.rerollsUsedToday.coerceAtMost(state.rerollCap())
        return state
    }

    private fun encodeAssignments(
        assignments: Map<DailyAdventurePath, List<DailyAdventureAssignment>>,
    ): MutableMap<String, Any> =
        assignments
            .mapKeys { (path, _) -> path.name }
            .mapValues { (_, values) ->
                values.sortedBy { it.slot }.map { assignment ->
                    mutableMapOf<String, Any>(
                        "slot" to assignment.slot,
                        "taskId" to assignment.taskId,
                        "difficulty" to assignment.difficulty.name,
                        "generatedDay" to assignment.generatedDay,
                        "goal" to assignment.goal,
                        "reward" to assignment.reward,
                        "progress" to assignment.progress,
                        "complete" to assignment.complete,
                        "rewardCredited" to assignment.rewardCredited,
                        "rerollCount" to assignment.rerollCount,
                        "dimensions" to assignment.dimensions.toMutableMap(),
                    ).also { encoded ->
                        assignment.resolvedTarget?.let { encoded["resolvedTarget"] = it }
                        assignment.completedAtEpochMillis?.let { encoded["completedAtEpochMillis"] = it }
                    }
                }.toMutableList()
            }
            .toMutableMap()

    private fun decodeAssignments(
        raw: Any?,
        fallbackDay: Long,
    ): MutableMap<DailyAdventurePath, MutableList<DailyAdventureAssignment>> {
        val outer = raw as? Map<*, *> ?: return mutableMapOf()
        return outer.entries.mapNotNull { (rawPath, rawAssignments) ->
            val path = parsePath(rawPath?.toString()) ?: return@mapNotNull null
            val list = rawAssignments as? List<*> ?: return@mapNotNull null
            val decoded =
                list.mapNotNull(::decodeAssignment)
                    .filter { it.generatedDay == fallbackDay }
                    .distinctBy { it.slot }
                    .sortedBy { it.slot }
                    .toMutableList()
            path to decoded
        }.toMap(mutableMapOf())
    }

    private fun decodeAssignment(raw: Any?): DailyAdventureAssignment? {
        val map = raw as? Map<*, *> ?: return null
        val slot = map.number("slot")?.toInt() ?: return null
        val id = map["taskId"] as? String ?: return null
        val difficulty = parseDifficulty(map["difficulty"]?.toString()) ?: return null
        val generatedDay = map.number("generatedDay")?.toLong() ?: return null
        val goal = map.number("goal")?.toLong()?.takeIf { it > 0L } ?: return null
        val reward = map.number("reward")?.toLong()?.takeIf { it > 0L } ?: return null
        if (slot !in 0 until DailyAdventureEngine.TASKS_PER_PATH || id.isBlank()) return null
        val progress = map.nonNegativeLong("progress").coerceAtMost(goal)
        val rewardCredited = map["rewardCredited"] as? Boolean ?: false
        val complete = (map["complete"] as? Boolean ?: false) && progress >= goal
        return DailyAdventureAssignment(
            slot = slot,
            taskId = id,
            difficulty = difficulty,
            generatedDay = generatedDay,
            resolvedTarget = (map["resolvedTarget"] as? String)?.takeIf(String::isNotBlank),
            goal = goal,
            reward = reward,
            progress = progress,
            complete = complete,
            rewardCredited = rewardCredited && complete,
            completedAtEpochMillis = map.number("completedAtEpochMillis")?.toLong()?.takeIf { it >= 0L },
            rerollCount = map.nonNegativeInt("rerollCount"),
            dimensions = decodeLongMap(map["dimensions"]),
        )
    }

    private fun encodeRerolls(
        results: Map<String, DailyAdventureRerollResult>,
    ): MutableMap<String, Any> =
        results.entries
            .toList()
            .takeLast(DailyAdventureState.MAX_RECENT_REROLLS)
            .associateTo(mutableMapOf()) { (requestId, result) ->
                requestId to
                    mutableMapOf<String, Any>(
                        "status" to result.status.name,
                    ).also { map ->
                        result.previousTaskId?.let { map["previousTaskId"] = it }
                        result.replacementTaskId?.let { map["replacementTaskId"] = it }
                    }
            }

    private fun decodeRerolls(raw: Any?): MutableMap<String, DailyAdventureRerollResult> {
        val map = raw as? Map<*, *> ?: return mutableMapOf()
        return map.entries.mapNotNull { (rawId, rawResult) ->
            val id = rawId as? String ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            val resultMap = rawResult as? Map<*, *> ?: return@mapNotNull null
            val status = parseRerollStatus(resultMap["status"]?.toString()) ?: return@mapNotNull null
            id to
                DailyAdventureRerollResult(
                    status = status,
                    previousTaskId = resultMap["previousTaskId"] as? String,
                    replacementTaskId = resultMap["replacementTaskId"] as? String,
                )
        }.takeLast(DailyAdventureState.MAX_RECENT_REROLLS).toMap(mutableMapOf())
    }

    private fun encodeLifetime(stats: DailyAdventureLifetimeStats): MutableMap<String, Any> =
        mutableMapOf(
            "tasksCompleted" to stats.tasksCompleted,
            "pathsClaimed" to stats.pathsClaimed,
            "rerollsUsed" to stats.rerollsUsed,
            "pointsEarned" to stats.pointsEarned,
            "pointsSpent" to stats.pointsSpent,
            "protectionChargesConsumed" to stats.protectionChargesConsumed,
        )

    private fun decodeLifetime(raw: Any?): DailyAdventureLifetimeStats {
        val map = raw as? Map<*, *> ?: return DailyAdventureLifetimeStats()
        return DailyAdventureLifetimeStats(
            tasksCompleted = map.nonNegativeLong("tasksCompleted"),
            pathsClaimed = map.nonNegativeLong("pathsClaimed"),
            rerollsUsed = map.nonNegativeLong("rerollsUsed"),
            pointsEarned = map.nonNegativeLong("pointsEarned"),
            pointsSpent = map.nonNegativeLong("pointsSpent"),
            protectionChargesConsumed = map.nonNegativeLong("protectionChargesConsumed"),
        )
    }

    private fun decodeSeen(raw: Any?): MutableMap<DailyAdventurePath, MutableSet<String>> {
        val map = raw as? Map<*, *> ?: return mutableMapOf()
        return map.entries.mapNotNull { (rawPath, rawIds) ->
            val path = parsePath(rawPath?.toString()) ?: return@mapNotNull null
            val ids = (rawIds as? List<*>).orEmpty().mapNotNull { it as? String }
                .filter(String::isNotBlank).toMutableSet()
            path to ids
        }.toMap(mutableMapOf())
    }

    private fun decodeLongMap(raw: Any?): MutableMap<String, Long> {
        val map = raw as? Map<*, *> ?: return mutableMapOf()
        return map.entries.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey as? String ?: return@mapNotNull null
            val value = (rawValue as? Number)?.toLong() ?: return@mapNotNull null
            if (key.isBlank() || value < 0L) null else key to value
        }.toMap(mutableMapOf())
    }

    private fun parsePath(value: String?): DailyAdventurePath? =
        DailyAdventurePath.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun parseDifficulty(value: String?): DailyAdventureDifficulty? =
        DailyAdventureDifficulty.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun parseRerollStatus(value: String?): DailyAdventureRerollStatus? =
        DailyAdventureRerollStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun Map<*, *>.number(key: String): Number? = this[key] as? Number

    private fun Map<*, *>.nonNegativeLong(key: String): Long =
        (number(key)?.toLong() ?: 0L).coerceAtLeast(0L)

    private fun Map<*, *>.nonNegativeInt(key: String): Int =
        (number(key)?.toLong() ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun Map<*, *>.nullableDay(key: String): Long? =
        number(key)?.toLong()?.takeUnless { it == NULL_DAY }

    private fun Map<*, *>.stringList(key: String): List<String> =
        (this[key] as? List<*>).orEmpty().mapNotNull { it as? String }

    private const val NULL_DAY: Long = Long.MIN_VALUE + 1L
}
