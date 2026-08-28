package org.rsmod.content.minigames.giantsfoundry

internal object FoundryRules {
    const val CRUCIBLE_CAPACITY = 28
const val MAX_MOULD_SCORE = 69
    const val MAX_HEAT = 1_000
    const val MAX_PROGRESS = 1_000
    const val QUALITY_LOSS_PER_MISTAKE = 5
    const val SLOW_HEAT_CHANGE = 50
    const val FAST_HEAT_CHANGE = 160
    const val PASSIVE_COOL_PER_TICK = 1
    const val GAME_TICK_MILLIS = 600L

    val commissionPairs: List<Pair<FoundryDescriptor, FoundryDescriptor>> =
        listOf(
            FoundryDescriptor.BROAD to FoundryDescriptor.HEAVY,
            FoundryDescriptor.BROAD to FoundryDescriptor.LIGHT,
            FoundryDescriptor.BROAD to FoundryDescriptor.SPIKED,
            FoundryDescriptor.BROAD to FoundryDescriptor.FLAT,
            FoundryDescriptor.NARROW to FoundryDescriptor.HEAVY,
            FoundryDescriptor.NARROW to FoundryDescriptor.LIGHT,
            FoundryDescriptor.NARROW to FoundryDescriptor.SPIKED,
            FoundryDescriptor.NARROW to FoundryDescriptor.FLAT,
            FoundryDescriptor.HEAVY to FoundryDescriptor.SPIKED,
            FoundryDescriptor.HEAVY to FoundryDescriptor.FLAT,
            FoundryDescriptor.LIGHT to FoundryDescriptor.SPIKED,
            FoundryDescriptor.LIGHT to FoundryDescriptor.FLAT,
        )

    fun commissionForRoll(roll: Int): Pair<FoundryDescriptor, FoundryDescriptor> =
        commissionPairs[Math.floorMod(roll, commissionPairs.size)]

    fun isValidCommission(first: FoundryDescriptor, second: FoundryDescriptor): Boolean =
        (first to second) in commissionPairs

    fun mouldScore(commission: FoundryCommission, selected: Collection<FoundryMould>): Int {
        require(selected.size == MouldSlot.entries.size)
        require(selected.map(FoundryMould::slot).toSet().size == MouldSlot.entries.size)
        return selected.sumOf { it.scores[commission.first] + it.scores[commission.second] }
    }

    fun canUseMould(
        mould: FoundryMould,
        unlockedMoulds: Set<String>,
        smithingLevel: Int,
    ): Boolean =
        (mould.default || mould.key in unlockedMoulds) &&
            (mould.smithingLevel == null || smithingLevel >= mould.smithingLevel)

    /** Implements the documented top-two amounts rule, including higher-tier tie-breaking. */
    fun metalScore(crucible: FoundryCrucible): Int {
        require(crucible.total == CRUCIBLE_CAPACITY)
        val components =
            crucible.amounts
                .filterValues { it > 0 }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<FoundryMetal, Int>> { it.value }
                        .thenByDescending { it.key.tier },
                )
                .take(2)
        val first = components.getOrNull(0)
        val second = components.getOrNull(1)
        return Math.floorDiv(
            componentTenths(first) + componentTenths(second) + productTenths(first, second),
            10,
        )
    }

    fun sectionCount(metalScore: Int): Int =
        when (metalScore) {
            in 10..19 -> 3
            in 20..59 -> 4
            in 60..89 -> 5
            in 90..119 -> 6
            in 120..130 -> 7
            else -> error("Unsupported Giants' Foundry metal score: $metalScore")
        }

    fun stagePlan(
        commissionId: Long,
        sections: Int,
        selectNext: ((List<FoundryTool>) -> FoundryTool)? = null,
    ): List<FoundryTool> {
        require(sections in 3..7)
        var seed = commissionId xor (commissionId ushr 33) xor 0x6A09E667F3BCC909L
        fun deterministicChoice(options: List<FoundryTool>): FoundryTool {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return options[Math.floorMod(seed, options.size.toLong()).toInt()]
        }
        val plan = mutableListOf(FoundryTool.TRIP_HAMMER)
        while (plan.size < sections) {
            val options = FoundryTool.entries.filter { it != plan.last() }
            val next = selectNext?.invoke(options) ?: deterministicChoice(options)
            require(next in options) { "Foundry workflow repeated the previous station." }
            plan += next
        }
        return plan
    }

    fun stageTarget(preform: FoundryPreform): Int =
        Math.ceil(MAX_PROGRESS.toDouble() / preform.stages.size.coerceAtLeast(1)).toInt()

    /**
     * The stock Foundry HUD (and Easy Giants' Foundry) selects the highlighted
     * workstation from total refinement progress, rather than a separate
     * server-side section counter. Keep validation on that same projection so
     * a restored preform can never disagree with the overlay.
     */
    fun hudStageIndex(preform: FoundryPreform): Int? {
        if (preform.progress !in 0 until MAX_PROGRESS || preform.stages.isEmpty()) return null
        return ((preform.progress.toLong() * preform.stages.size) / MAX_PROGRESS)
            .toInt()
            .coerceIn(0, preform.stages.lastIndex)
    }

    /**
     * Interface 754 renders its opening trip-hammer section without a seed.
     * The six section varbits are therefore sections two through seven.
     */
    fun nativeHudSeeds(stages: List<FoundryTool>): List<FoundryTool> = stages.drop(1).take(6)

    /** Native client-script 6121: each heat window narrows around its own centre. */
    fun heatRange(preform: FoundryPreform, band: FoundryHeatBand): IntRange {
        val width = 333 - (preform.metalScore.coerceIn(0, 130) * 167 / 130)
        val start = when (band) {
            FoundryHeatBand.LOW -> 166 - width / 2
            FoundryHeatBand.MEDIUM -> 499 - width / 2
            FoundryHeatBand.HIGH -> 832 - width / 2
        }
        return start..(start + width)
    }

    fun inRequiredHeat(preform: FoundryPreform, tool: FoundryTool): Boolean =
        preform.heat in heatRange(preform, tool.requiredHeat)

    fun reward(quality: Int): FoundryReward {
        require(quality >= 0)
        val xp = (Math.floorDiv(quality * quality, 73) + Math.floorDiv(3 * quality, 2) + 1) * 30
        return FoundryReward(quality, xp, xp * 2, quality)
    }

    private fun componentTenths(component: Map.Entry<FoundryMetal, Int>?): Int {
        if (component == null) return 0
        return Math.floorDiv(100 * component.key.tier * component.value, CRUCIBLE_CAPACITY)
    }

    private fun productTenths(
        first: Map.Entry<FoundryMetal, Int>?,
        second: Map.Entry<FoundryMetal, Int>?,
    ): Int {
        if (first == null || second == null) return 0
        return Math.floorDiv(
            100 * first.key.tier * first.value * second.key.tier * second.value,
            CRUCIBLE_CAPACITY * CRUCIBLE_CAPACITY,
        )
    }
}
