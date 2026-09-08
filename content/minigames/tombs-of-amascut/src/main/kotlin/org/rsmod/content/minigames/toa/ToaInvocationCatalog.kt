package org.rsmod.content.minigames.toa

internal enum class ToaInvocationExclusiveGroup(val key: String) {
    ATTEMPTS("attempts"),
    TIME_LIMIT("time_limit"),
    HELPFUL_SPIRIT("helpful_spirit"),
    PATH_LEVEL("path_level"),
}

internal enum class ToaInvocationMechanic {
    ATTEMPT_LIMIT,
    RAID_TIMER,
    HELPFUL_SPIRIT_SUPPLIES,
    WALK_THE_PATH_LEVEL_UP,
    INITIAL_PATH_LEVEL,
    PROTECTION_PRAYER_EFFECTIVENESS,
    DAMAGE_PRAYER_DRAIN,
    FOOD_RESTRICTION,
    HEALTH_POTION_RESTRICTION,
    SPECIAL_ATTACK_COST,
    KEPHRI_EGGS,
    KEPHRI_OVERLORDS,
    KEPHRI_DUNG_TARGETS,
    KEPHRI_SWARM_HEALING,
    KEPHRI_BOMB_RADIUS,
    ZEBAK_BLOOD_MAGIC,
    ZEBAK_BLOOD_RADIUS,
    ZEBAK_BLOOD_CLOUD_COUNT,
    ZEBAK_ACID_AND_JUG_RADIUS,
    AKKHA_DOUBLE_SPECIAL,
    AKKHA_ADJACENT_MELEE,
    AKKHA_STYLE_CADENCE,
    AKKHA_SPECIAL_DIFFICULTY,
    BABA_PIT_KNOCKBACK,
    BABA_SARCOPHAGUS_DAMAGE,
    BABA_BANANA_PEELS,
    BABA_SLAM_RADIUS,
    BABA_BOULDER_CADENCE,
    WARDENS_PHASE_ONE_CHARGE,
    WARDENS_PHASE_TWO_CADENCE,
    WARDENS_PHASE_TWO_OBELISK,
    WARDENS_PHASE_THREE_SLAM,
    WARDENS_PHASE_THREE_INSANITY,
}

internal data class ToaInvocationUiMapping(
    val row: Int,
    val structId: Int,
    val clientInvocationId: Int,
) {
    val rootSubcomponent: Int
        get() = row * 3

    val bitmapIndex: Int
        get() =
            when {
                clientInvocationId > 61 -> 2
                clientInvocationId > 30 -> 1
                else -> 0
            }

    val bitmapMask: Int
        get() = 1 shl (clientInvocationId % 31)
}

internal data class ToaInvocationDefinition(
    val invocation: ToaInvocation,
    val ui: ToaInvocationUiMapping,
    val prerequisites: Set<ToaInvocation> = emptySet(),
    val exclusiveGroup: ToaInvocationExclusiveGroup? = null,
    val affectedMechanics: Set<ToaInvocationMechanic>,
) {
    val key: String
        get() = invocation.key

    val displayName: String
        get() = invocation.displayName

    val raidLevel: Int
        get() = invocation.raidLevel

    val category: ToaInvocationCategory
        get() = invocation.category
}

internal sealed interface ToaInvocationViolation {
    val message: String

    data class MissingPrerequisite(val invocation: ToaInvocation, val prerequisite: ToaInvocation) :
        ToaInvocationViolation {
        override val message = "${invocation.displayName} requires ${prerequisite.displayName}."
    }

    data class MutuallyExclusive(
        val group: ToaInvocationExclusiveGroup,
        val invocations: Set<ToaInvocation>,
    ) : ToaInvocationViolation {
        override val message = "Only one ${group.key.replace('_', ' ')} invocation may be active."
    }
}

internal sealed interface ToaInvocationMutation {
    data class Changed(
        val enabled: Boolean,
        val automaticallyDisabled: Set<ToaInvocation> = emptySet(),
    ) : ToaInvocationMutation

    data class Rejected(val violations: List<ToaInvocationViolation>) : ToaInvocationMutation
}

internal sealed interface ToaInvocationLoadResult {
    data object Loaded : ToaInvocationLoadResult

    data class Rejected(val violations: List<ToaInvocationViolation>) : ToaInvocationLoadResult
}

internal object ToaInvocationRules {
    fun validate(invocations: Set<ToaInvocation>): List<ToaInvocationViolation> {
        val violations = mutableListOf<ToaInvocationViolation>()
        invocations.forEach { invocation ->
            val definition = ToaInvocationCatalog.definition(invocation)
            (definition.prerequisites - invocations).forEach { prerequisite ->
                violations += ToaInvocationViolation.MissingPrerequisite(invocation, prerequisite)
            }
        }
        ToaInvocationExclusiveGroup.entries.forEach { group ->
            val selected =
                invocations.filterTo(linkedSetOf()) {
                    ToaInvocationCatalog.definition(it).exclusiveGroup == group
                }
            if (selected.size > 1) {
                violations += ToaInvocationViolation.MutuallyExclusive(group, selected)
            }
        }
        return violations
    }

    fun dependentClosure(
        prerequisite: ToaInvocation,
        selected: Set<ToaInvocation>,
    ): Set<ToaInvocation> {
        val dependents = linkedSetOf<ToaInvocation>()
        var changed: Boolean
        do {
            changed = false
            selected.forEach { candidate ->
                if (candidate == prerequisite || candidate in dependents) return@forEach
                val required = ToaInvocationCatalog.definition(candidate).prerequisites
                if (prerequisite in required || required.any(dependents::contains)) {
                    changed = dependents.add(candidate) || changed
                }
            }
        } while (changed)
        return dependents
    }
}

internal object ToaInvocationCatalog {
    private fun definition(
        invocation: ToaInvocation,
        row: Int,
        structId: Int,
        mechanic: ToaInvocationMechanic,
        exclusiveGroup: ToaInvocationExclusiveGroup? = null,
        prerequisites: Set<ToaInvocation> = emptySet(),
        additionalMechanics: Set<ToaInvocationMechanic> = emptySet(),
    ) =
        ToaInvocationDefinition(
            invocation = invocation,
            ui = ToaInvocationUiMapping(row, structId, row + 1),
            prerequisites = prerequisites,
            exclusiveGroup = exclusiveGroup,
            affectedMechanics = setOf(mechanic) + additionalMechanics,
        )

    val standard: List<ToaInvocationDefinition> =
        listOf(
            definition(
                ToaInvocation.TRY_AGAIN,
                0,
                417,
                ToaInvocationMechanic.ATTEMPT_LIMIT,
                ToaInvocationExclusiveGroup.ATTEMPTS,
            ),
            definition(
                ToaInvocation.PERSISTENCE,
                1,
                418,
                ToaInvocationMechanic.ATTEMPT_LIMIT,
                ToaInvocationExclusiveGroup.ATTEMPTS,
            ),
            definition(
                ToaInvocation.SOFTCORE_RUN,
                2,
                419,
                ToaInvocationMechanic.ATTEMPT_LIMIT,
                ToaInvocationExclusiveGroup.ATTEMPTS,
            ),
            definition(
                ToaInvocation.HARDCORE_RUN,
                3,
                420,
                ToaInvocationMechanic.ATTEMPT_LIMIT,
                ToaInvocationExclusiveGroup.ATTEMPTS,
            ),
            definition(
                ToaInvocation.WALK_FOR_IT,
                4,
                421,
                ToaInvocationMechanic.RAID_TIMER,
                ToaInvocationExclusiveGroup.TIME_LIMIT,
            ),
            definition(
                ToaInvocation.JOG_FOR_IT,
                5,
                422,
                ToaInvocationMechanic.RAID_TIMER,
                ToaInvocationExclusiveGroup.TIME_LIMIT,
            ),
            definition(
                ToaInvocation.RUN_FOR_IT,
                6,
                423,
                ToaInvocationMechanic.RAID_TIMER,
                ToaInvocationExclusiveGroup.TIME_LIMIT,
            ),
            definition(
                ToaInvocation.SPRINT_FOR_IT,
                7,
                424,
                ToaInvocationMechanic.RAID_TIMER,
                ToaInvocationExclusiveGroup.TIME_LIMIT,
            ),
            definition(
                ToaInvocation.NEED_SOME_HELP,
                8,
                425,
                ToaInvocationMechanic.HELPFUL_SPIRIT_SUPPLIES,
                ToaInvocationExclusiveGroup.HELPFUL_SPIRIT,
            ),
            definition(
                ToaInvocation.NEED_LESS_HELP,
                9,
                426,
                ToaInvocationMechanic.HELPFUL_SPIRIT_SUPPLIES,
                ToaInvocationExclusiveGroup.HELPFUL_SPIRIT,
            ),
            definition(
                ToaInvocation.NO_HELP_NEEDED,
                10,
                427,
                ToaInvocationMechanic.HELPFUL_SPIRIT_SUPPLIES,
                ToaInvocationExclusiveGroup.HELPFUL_SPIRIT,
            ),
            definition(
                ToaInvocation.WALK_THE_PATH,
                11,
                428,
                ToaInvocationMechanic.WALK_THE_PATH_LEVEL_UP,
            ),
            definition(
                ToaInvocation.PATHSEEKER,
                12,
                429,
                ToaInvocationMechanic.INITIAL_PATH_LEVEL,
                ToaInvocationExclusiveGroup.PATH_LEVEL,
            ),
            definition(
                ToaInvocation.PATHFINDER,
                13,
                430,
                ToaInvocationMechanic.INITIAL_PATH_LEVEL,
                ToaInvocationExclusiveGroup.PATH_LEVEL,
            ),
            definition(
                ToaInvocation.PATHMASTER,
                14,
                431,
                ToaInvocationMechanic.INITIAL_PATH_LEVEL,
                ToaInvocationExclusiveGroup.PATH_LEVEL,
            ),
            definition(
                ToaInvocation.QUIET_PRAYERS,
                15,
                432,
                ToaInvocationMechanic.PROTECTION_PRAYER_EFFECTIVENESS,
            ),
            definition(
                ToaInvocation.DEADLY_PRAYERS,
                16,
                433,
                ToaInvocationMechanic.DAMAGE_PRAYER_DRAIN,
            ),
            definition(ToaInvocation.ON_A_DIET, 17, 434, ToaInvocationMechanic.FOOD_RESTRICTION),
            definition(
                ToaInvocation.DEHYDRATION,
                18,
                435,
                ToaInvocationMechanic.HEALTH_POTION_RESTRICTION,
            ),
            definition(
                ToaInvocation.OVERLY_DRAINING,
                19,
                436,
                ToaInvocationMechanic.SPECIAL_ATTACK_COST,
            ),
            definition(ToaInvocation.LIVELY_LARVAE, 20, 437, ToaInvocationMechanic.KEPHRI_EGGS),
            definition(
                ToaInvocation.MORE_OVERLORDS,
                21,
                438,
                ToaInvocationMechanic.KEPHRI_OVERLORDS,
            ),
            definition(
                ToaInvocation.BLOWING_MUD,
                22,
                439,
                ToaInvocationMechanic.KEPHRI_DUNG_TARGETS,
            ),
            definition(ToaInvocation.MEDIC, 23, 440, ToaInvocationMechanic.KEPHRI_SWARM_HEALING),
            definition(
                ToaInvocation.AERIAL_ASSAULT,
                24,
                441,
                ToaInvocationMechanic.KEPHRI_BOMB_RADIUS,
            ),
            definition(
                ToaInvocation.NOT_JUST_A_HEAD,
                25,
                442,
                ToaInvocationMechanic.ZEBAK_BLOOD_MAGIC,
            ),
            definition(
                ToaInvocation.ARTERIAL_SPRAY,
                26,
                444,
                ToaInvocationMechanic.ZEBAK_BLOOD_RADIUS,
                prerequisites = setOf(ToaInvocation.NOT_JUST_A_HEAD),
            ),
            definition(
                ToaInvocation.BLOOD_THINNERS,
                27,
                445,
                ToaInvocationMechanic.ZEBAK_BLOOD_CLOUD_COUNT,
                prerequisites = setOf(ToaInvocation.NOT_JUST_A_HEAD),
            ),
            definition(
                ToaInvocation.UPSET_STOMACH,
                28,
                447,
                ToaInvocationMechanic.ZEBAK_ACID_AND_JUG_RADIUS,
            ),
            definition(
                ToaInvocation.DOUBLE_TROUBLE,
                29,
                448,
                ToaInvocationMechanic.AKKHA_DOUBLE_SPECIAL,
            ),
            definition(
                ToaInvocation.KEEP_BACK,
                30,
                449,
                ToaInvocationMechanic.AKKHA_ADJACENT_MELEE,
            ),
            definition(
                ToaInvocation.STAY_VIGILANT,
                31,
                542,
                ToaInvocationMechanic.AKKHA_STYLE_CADENCE,
            ),
            definition(
                ToaInvocation.FEELING_SPECIAL,
                32,
                543,
                ToaInvocationMechanic.AKKHA_SPECIAL_DIFFICULTY,
            ),
            definition(
                ToaInvocation.MIND_THE_GAP,
                33,
                603,
                ToaInvocationMechanic.BABA_PIT_KNOCKBACK,
            ),
            definition(
                ToaInvocation.GOTTA_HAVE_FAITH,
                34,
                752,
                ToaInvocationMechanic.BABA_SARCOPHAGUS_DAMAGE,
            ),
            definition(
                ToaInvocation.JUNGLE_JAPES,
                35,
                949,
                ToaInvocationMechanic.BABA_BANANA_PEELS,
            ),
            definition(
                ToaInvocation.SHAKING_THINGS_UP,
                36,
                1275,
                ToaInvocationMechanic.BABA_SLAM_RADIUS,
            ),
            definition(
                ToaInvocation.BOULDERDASH,
                37,
                1276,
                ToaInvocationMechanic.BABA_BOULDER_CADENCE,
            ),
            definition(
                ToaInvocation.ANCIENT_HASTE,
                38,
                1278,
                ToaInvocationMechanic.WARDENS_PHASE_ONE_CHARGE,
            ),
            definition(
                ToaInvocation.ACCELERATION,
                39,
                1688,
                ToaInvocationMechanic.WARDENS_PHASE_TWO_CADENCE,
            ),
            definition(
                ToaInvocation.PENETRATION,
                40,
                2874,
                ToaInvocationMechanic.WARDENS_PHASE_TWO_OBELISK,
            ),
            definition(
                ToaInvocation.OVERCLOCKED,
                41,
                2933,
                ToaInvocationMechanic.WARDENS_PHASE_THREE_SLAM,
            ),
            definition(
                ToaInvocation.OVERCLOCKED_2,
                42,
                2934,
                ToaInvocationMechanic.WARDENS_PHASE_THREE_SLAM,
                prerequisites = setOf(ToaInvocation.OVERCLOCKED),
            ),
            definition(
                ToaInvocation.INSANITY,
                43,
                2971,
                ToaInvocationMechanic.WARDENS_PHASE_THREE_INSANITY,
                prerequisites = setOf(ToaInvocation.OVERCLOCKED_2),
                additionalMechanics = setOf(ToaInvocationMechanic.WARDENS_PHASE_THREE_SLAM),
            ),
        )

    private val byInvocation = standard.associateBy(ToaInvocationDefinition::invocation)
    private val bySubcomponent = standard.associateBy { it.ui.rootSubcomponent }
    private val byKey = standard.associateBy { it.key.lowercase() }

    val interfaceSubcomponents: Set<Int> = bySubcomponent.keys

    init {
        require(standard.size == 44)
        require(byInvocation.keys == ToaInvocation.entries.toSet())
        require(bySubcomponent.size == standard.size)
        require(standard.map { it.ui.structId }.toSet().size == standard.size)
        require(standard.map { it.ui.clientInvocationId }.toSet().size == standard.size)
        require(standard.all { it.affectedMechanics.isNotEmpty() })
        require(ToaInvocationRules.validate(emptySet()).isEmpty())
    }

    fun definition(invocation: ToaInvocation): ToaInvocationDefinition =
        requireNotNull(byInvocation[invocation])

    fun fromInterfaceSubcomponent(subcomponent: Int): ToaInvocation? =
        bySubcomponent[subcomponent]?.invocation

    fun find(value: String): ToaInvocation? {
        val normalized =
            value.trim().lowercase().replace(" ", "_").replace("?", "").replace("!", "")
        return byKey[normalized]?.invocation
            ?: standard
                .firstOrNull {
                    it.invocation.name.equals(value, true) ||
                        it.displayName
                            .replace(" ", "_")
                            .replace("?", "")
                            .replace("!", "")
                            .equals(value, true)
                }
                ?.invocation
    }

    fun sortForClient(invocations: Set<ToaInvocation>): List<ToaInvocation> =
        invocations.sortedBy { definition(it).ui.row }

    fun raidLevel(invocations: Set<ToaInvocation>): Int =
        invocations.sumOf { definition(it).raidLevel }

    fun clientBitmaps(invocations: Set<ToaInvocation>): IntArray {
        val result = IntArray(3)
        invocations.forEach { invocation ->
            val ui = definition(invocation).ui
            result[ui.bitmapIndex] = result[ui.bitmapIndex] or ui.bitmapMask
        }
        return result
    }

    fun legalMaximumRaidLevel(): Int {
        val grouped = standard.filter { it.exclusiveGroup != null }.groupBy { it.exclusiveGroup }
        val exclusiveMaximum =
            grouped.values.sumOf { group -> group.maxOf(ToaInvocationDefinition::raidLevel) }
        val ungrouped =
            standard.filter { it.exclusiveGroup == null }.sumOf(ToaInvocationDefinition::raidLevel)
        return exclusiveMaximum + ungrouped
    }
}
