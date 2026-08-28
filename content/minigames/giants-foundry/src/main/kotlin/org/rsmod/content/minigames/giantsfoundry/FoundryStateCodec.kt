package org.rsmod.content.minigames.giantsfoundry

/**
 * Versioned primitive-only persistence codec. The character attribute store normalizes persisted
 * JSON numbers and maps, so this codec accepts Number/Map<*, *> on input and emits only those
 * shapes on output. A malformed non-empty value is rejected rather than converted to a fresh
 * state, preventing a damaged save from silently losing a commission or duplicating a reward.
 */
internal object FoundryStateCodec {
    private const val SCHEMA_VERSION = 3

    fun encode(state: FoundryState): MutableMap<String, Any> =
        mutableMapOf<String, Any>(
            "schema" to SCHEMA_VERSION,
            "revision" to state.revision,
            "reputation" to state.reputation,
            "completed" to state.completedSwords,
            "bestQuality" to state.bestQuality,
            "selected" to state.selectedMoulds.mapKeys { it.key.name }.toMutableMap(),
            "unlocked" to state.unlockedMoulds.sorted().toMutableList(),
            "crucible" to state.crucible.amounts.mapKeys { it.key.name }.toMutableMap(),
        ).apply {
            state.commission?.let {
                put(
                    "commission",
                    mutableMapOf<String, Any>(
                        "id" to it.id,
                        "first" to it.first.name,
                        "second" to it.second.name,
                        "assignedAt" to it.assignedAtMillis,
                    ),
                )
            }
            state.preform?.let {
                put(
                    "preform",
                    mutableMapOf<String, Any>(
                        "commissionId" to it.commissionId,
                        "metalScore" to it.metalScore,
                        "mouldScore" to it.mouldScore,
                        "maxQuality" to it.maxQuality,
                        "quality" to it.quality,
                        "sections" to it.sections,
"lifecycle" to it.lifecycle.name,
                        "heat" to it.heat,
                        "progress" to it.progress,
                        "stages" to it.stages.map(FoundryTool::name).toMutableList(),
                        "activeStage" to it.activeStage,
                        "stageProgress" to it.stageProgress,
                        "mistakes" to it.mistakes,
                        "lastHeatUpdate" to it.lastHeatUpdateMillis,
                    ),
                )
            }
        }

    fun decode(raw: Map<*, *>?): FoundryState? =
        runCatching {
                require(raw != null)
val schema = int(raw["schema"])
                require(schema in 1..SCHEMA_VERSION)
                val commission = decodeCommission(raw["commission"])
                val selected = decodeSelections(raw["selected"])
                val unlocked = decodeUnlocks(raw["unlocked"])
                val crucible = decodeCrucible(raw["crucible"])
                val preform = decodePreform(raw["preform"], commission, schema)
                FoundryState(
                    reputation = int(raw["reputation"]).coerceAtLeast(0),
                    completedSwords = int(raw["completed"]).coerceAtLeast(0),
                    bestQuality = int(raw["bestQuality"]).coerceIn(0, 199),
                    commission = commission,
                    selectedMoulds = selected,
                    unlockedMoulds = unlocked,
                    crucible = crucible,
                    preform = preform,
                    revision = int(raw["revision"]).coerceAtLeast(0),
                )
            }
            .getOrNull()

    private fun decodeCommission(value: Any?): FoundryCommission? {
        if (value == null) return null
        val map = value as? Map<*, *> ?: error("commission is not a map")
        val first = enum<FoundryDescriptor>(map["first"])
        val second = enum<FoundryDescriptor>(map["second"])
        require(FoundryRules.isValidCommission(first, second))
        return FoundryCommission(
            id = long(map["id"]),
            first = first,
            second = second,
            assignedAtMillis = long(map["assignedAt"]),
        )
    }

    private fun decodeSelections(value: Any?): Map<MouldSlot, String> {
        if (value == null) return emptyMap()
        val map = value as? Map<*, *> ?: error("selected is not a map")
        return buildMap {
            for ((slotName, keyValue) in map) {
                val slot = enum<FoundryMouldSlotProxy>(slotName).slot
                val key = keyValue as? String ?: error("invalid selected mould")
                val mould = FoundryMoulds.byKey[key] ?: error("unknown selected mould")
                require(mould.slot == slot)
                put(slot, key)
            }
        }
    }

    private fun decodeUnlocks(value: Any?): Set<String> {
        if (value == null) return emptySet()
        val values = value as? List<*> ?: error("unlocked is not a list")
        return values.mapTo(linkedSetOf()) { raw ->
            val key = raw as? String ?: error("invalid unlocked mould")
            val mould = FoundryMoulds.byKey[key] ?: error("unknown unlocked mould")
            require(!mould.default)
            key
        }
    }

    private fun decodeCrucible(value: Any?): FoundryCrucible {
        if (value == null) return FoundryCrucible()
        val map = value as? Map<*, *> ?: error("crucible is not a map")
        val amounts =
            buildMap {
                for ((metalName, countValue) in map) {
                    val metal = enum<FoundryMetal>(metalName)
                    val count = int(countValue)
                    require(count >= 0)
                    if (count > 0) put(metal, count)
                }
            }
        return FoundryCrucible(amounts).also { require(it.total <= FoundryRules.CRUCIBLE_CAPACITY) }
    }

    private fun decodePreform(value: Any?, commission: FoundryCommission?, schema: Int): FoundryPreform? {
        if (value == null) return null
        val map = value as? Map<*, *> ?: error("preform is not a map")
        requireNotNull(commission)
        val lifecycle = enum<PreformLifecycle>(map["lifecycle"])
        val sections = int(map["sections"])
        val stages =
            if (schema < 3) {
                FoundryRules.stagePlan(commission.id, sections)
            } else {
                val values = map["stages"] as? List<*> ?: error("stages is not a list")
                values.map { enum<FoundryTool>(it) }
            }
        val activeStage =
            if (schema < 3 && lifecycle == PreformLifecycle.Complete) stages.size
            else if (schema < 3) (intOr(map["activeStage"], 0) / FoundryTool.entries.size).coerceIn(0, stages.size)
            else intOr(map["activeStage"], 0)
        val progress =
            if (schema == 1 && lifecycle == PreformLifecycle.Complete) FoundryRules.MAX_PROGRESS else intOr(map["progress"], 0)
        val preform =
            FoundryPreform(
                commissionId = long(map["commissionId"]),
                metalScore = int(map["metalScore"]),
                mouldScore = int(map["mouldScore"]),
                maxQuality = int(map["maxQuality"]),
                quality = int(map["quality"]),
                sections = sections,
                lifecycle = lifecycle,
                heat = intOr(map["heat"], FoundryRules.MAX_HEAT),
                progress = progress,
                stages = stages,
                activeStage = activeStage,
                stageProgress = intOr(map["stageProgress"], 0),
                mistakes = intOr(map["mistakes"], 0),
                lastHeatUpdateMillis = longOr(map["lastHeatUpdate"], System.currentTimeMillis()),
            )
        require(preform.commissionId == commission.id)
        require(preform.metalScore in 10..130)
        require(preform.mouldScore in 0..FoundryRules.MAX_MOULD_SCORE)
        require(preform.maxQuality == preform.metalScore + preform.mouldScore)
        require(preform.quality in 0..preform.maxQuality)
require(preform.sections == FoundryRules.sectionCount(preform.metalScore))
        require(preform.heat in 0..FoundryRules.MAX_HEAT)
        require(preform.progress in 0..FoundryRules.MAX_PROGRESS)
        require(preform.stages.size == preform.sections)
        require(preform.activeStage in 0..preform.stages.size)
        require(preform.stageProgress >= 0)
        require(preform.mistakes >= 0)
        return preform
    }

    private fun int(value: Any?): Int =
        (value as? Number)?.toInt() ?: error("missing integer")

    private fun long(value: Any?): Long =
        (value as? Number)?.toLong() ?: error("missing long")

    private fun intOr(value: Any?, fallback: Int): Int =
        (value as? Number)?.toInt() ?: fallback

    private fun longOr(value: Any?, fallback: Long): Long =
        (value as? Number)?.toLong() ?: fallback

    private inline fun <reified T : Enum<T>> enum(value: Any?): T =
        enumValueOf(value as? String ?: error("missing enum"))

    /** Keeps the persisted slot strings independent from a Kotlin enum's ordinal. */
    private enum class FoundryMouldSlotProxy(val slot: MouldSlot) {
        FORTE(MouldSlot.FORTE),
        BLADE(MouldSlot.BLADE),
        TIP(MouldSlot.TIP),
    }
}
