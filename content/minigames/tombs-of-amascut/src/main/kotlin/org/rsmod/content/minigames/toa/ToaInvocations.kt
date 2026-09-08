package org.rsmod.content.minigames.toa

internal enum class ToaInvocationCategory(val cacheParamValue: Int) {
    ATTEMPTS(3),
    TIME_LIMIT(4),
    HELPFUL_SPIRIT(5),
    PATH_LEVEL(6),
    PRAYER(7),
    RESTORATION(8),
    PATHS(9),
    AKKHA(10),
    KEPHRI(11),
    ZEBAK(12),
    BABA(13),
    WARDENS(14),
}

internal enum class ToaInvocation(
    val key: String,
    val displayName: String,
    val raidLevel: Int,
    val category: ToaInvocationCategory,
) {
    TRY_AGAIN("try_again", "Try Again", 5, ToaInvocationCategory.ATTEMPTS),
    PERSISTENCE("persistence", "Persistence", 10, ToaInvocationCategory.ATTEMPTS),
    SOFTCORE_RUN("softcore_run", "Softcore Run", 15, ToaInvocationCategory.ATTEMPTS),
    HARDCORE_RUN("hardcore_run", "Hardcore Run", 25, ToaInvocationCategory.ATTEMPTS),
    WALK_FOR_IT("walk_for_it", "Walk for It", 10, ToaInvocationCategory.TIME_LIMIT),
    JOG_FOR_IT("jog_for_it", "Jog for It", 15, ToaInvocationCategory.TIME_LIMIT),
    RUN_FOR_IT("run_for_it", "Run for It", 20, ToaInvocationCategory.TIME_LIMIT),
    SPRINT_FOR_IT("sprint_for_it", "Sprint for It", 25, ToaInvocationCategory.TIME_LIMIT),
    NEED_SOME_HELP("need_some_help", "Need Some Help?", 15, ToaInvocationCategory.HELPFUL_SPIRIT),
    NEED_LESS_HELP("need_less_help", "Need Less Help?", 25, ToaInvocationCategory.HELPFUL_SPIRIT),
    NO_HELP_NEEDED("no_help_needed", "No Help Needed", 40, ToaInvocationCategory.HELPFUL_SPIRIT),
    WALK_THE_PATH("walk_the_path", "Walk the Path", 50, ToaInvocationCategory.PATHS),
    PATHSEEKER("pathseeker", "Pathseeker", 15, ToaInvocationCategory.PATH_LEVEL),
    PATHFINDER("pathfinder", "Pathfinder", 40, ToaInvocationCategory.PATH_LEVEL),
    PATHMASTER("pathmaster", "Pathmaster", 50, ToaInvocationCategory.PATH_LEVEL),
    QUIET_PRAYERS("quiet_prayers", "Quiet Prayers", 20, ToaInvocationCategory.PRAYER),
    DEADLY_PRAYERS("deadly_prayers", "Deadly Prayers", 20, ToaInvocationCategory.PRAYER),
    ON_A_DIET("on_a_diet", "On a Diet", 15, ToaInvocationCategory.RESTORATION),
    DEHYDRATION("dehydration", "Dehydration", 30, ToaInvocationCategory.RESTORATION),
    OVERLY_DRAINING("overly_draining", "Overly Draining", 15, ToaInvocationCategory.RESTORATION),
    LIVELY_LARVAE("lively_larvae", "Lively Larvae", 5, ToaInvocationCategory.KEPHRI),
    MORE_OVERLORDS("more_overlords", "More Overlords", 15, ToaInvocationCategory.KEPHRI),
    BLOWING_MUD("blowing_mud", "Blowing Mud", 10, ToaInvocationCategory.KEPHRI),
    MEDIC("medic", "Medic!", 15, ToaInvocationCategory.KEPHRI),
    AERIAL_ASSAULT("aerial_assault", "Aerial Assault", 10, ToaInvocationCategory.KEPHRI),
    NOT_JUST_A_HEAD("not_just_a_head", "Not Just a Head", 15, ToaInvocationCategory.ZEBAK),
    ARTERIAL_SPRAY("arterial_spray", "Arterial Spray", 10, ToaInvocationCategory.ZEBAK),
    BLOOD_THINNERS("blood_thinners", "Blood Thinners", 5, ToaInvocationCategory.ZEBAK),
    UPSET_STOMACH("upset_stomach", "Upset Stomach", 15, ToaInvocationCategory.ZEBAK),
    DOUBLE_TROUBLE("double_trouble", "Double Trouble", 20, ToaInvocationCategory.AKKHA),
    KEEP_BACK("keep_back", "Keep Back", 10, ToaInvocationCategory.AKKHA),
    STAY_VIGILANT("stay_vigilant", "Stay Vigilant", 15, ToaInvocationCategory.AKKHA),
    FEELING_SPECIAL("feeling_special", "Feeling Special?", 20, ToaInvocationCategory.AKKHA),
    MIND_THE_GAP("mind_the_gap", "Mind the Gap!", 10, ToaInvocationCategory.BABA),
    GOTTA_HAVE_FAITH("gotta_have_faith", "Gotta Have Faith", 10, ToaInvocationCategory.BABA),
    JUNGLE_JAPES("jungle_japes", "Jungle Japes", 5, ToaInvocationCategory.BABA),
    SHAKING_THINGS_UP("shaking_things_up", "Shaking Things Up", 10, ToaInvocationCategory.BABA),
    BOULDERDASH("boulderdash", "Boulderdash", 10, ToaInvocationCategory.BABA),
    ANCIENT_HASTE("ancient_haste", "Ancient Haste", 10, ToaInvocationCategory.WARDENS),
    ACCELERATION("acceleration", "Acceleration", 10, ToaInvocationCategory.WARDENS),
    PENETRATION("penetration", "Penetration", 10, ToaInvocationCategory.WARDENS),
    OVERCLOCKED("overclocked", "Overclocked", 10, ToaInvocationCategory.WARDENS),
    OVERCLOCKED_2("overclocked_2", "Overclocked 2", 10, ToaInvocationCategory.WARDENS),
    INSANITY("insanity", "Insanity", 50, ToaInvocationCategory.WARDENS);

    val selectable: Boolean
        get() = true

    val exclusiveGroup: String?
        get() = ToaInvocationCatalog.definition(this).exclusiveGroup?.key

    companion object {
        fun fromInterfaceSubcomponent(subcomponent: Int): ToaInvocation? =
            ToaInvocationCatalog.fromInterfaceSubcomponent(subcomponent)

        val interfaceSubcomponents: Set<Int>
            get() = ToaInvocationCatalog.interfaceSubcomponents

        fun find(value: String): ToaInvocation? = ToaInvocationCatalog.find(value)
    }
}

internal class ToaInvocationSet {
    private val enabled = linkedSetOf<ToaInvocation>()

    val raidLevel: Int
        get() = ToaInvocationCatalog.raidLevel(enabled)

    fun all(): Set<ToaInvocation> = enabled.toSet()

    operator fun contains(invocation: ToaInvocation): Boolean = invocation in enabled

    fun toggle(invocation: ToaInvocation): Boolean =
        toggleValidated(invocation) is ToaInvocationMutation.Changed

    fun toggleValidated(invocation: ToaInvocation): ToaInvocationMutation {
        if (invocation in enabled) {
            val removed = ToaInvocationRules.dependentClosure(invocation, enabled) + invocation
            enabled.removeAll(removed)
            return ToaInvocationMutation.Changed(
                enabled = false,
                automaticallyDisabled = removed - invocation,
            )
        }

        val definition = ToaInvocationCatalog.definition(invocation)
        val missing = definition.prerequisites - enabled
        if (missing.isNotEmpty()) {
            return ToaInvocationMutation.Rejected(
                missing.map { ToaInvocationViolation.MissingPrerequisite(invocation, it) }
            )
        }

        val automaticallyDisabled = linkedSetOf<ToaInvocation>()
        definition.exclusiveGroup?.let { group ->
            val sibling =
                enabled.firstOrNull { ToaInvocationCatalog.definition(it).exclusiveGroup == group }
            if (sibling != null) {
                automaticallyDisabled += sibling
                automaticallyDisabled += ToaInvocationRules.dependentClosure(sibling, enabled)
                enabled.removeAll(automaticallyDisabled)
            }
        }
        enabled += invocation
        return ToaInvocationMutation.Changed(
            enabled = true,
            automaticallyDisabled = automaticallyDisabled,
        )
    }

    fun replace(invocations: Set<ToaInvocation>): ToaInvocationLoadResult {
        val violations = ToaInvocationRules.validate(invocations)
        if (violations.isNotEmpty()) return ToaInvocationLoadResult.Rejected(violations)
        enabled.clear()
        enabled.addAll(ToaInvocationCatalog.sortForClient(invocations))
        return ToaInvocationLoadResult.Loaded
    }

    fun clientBitmaps(): IntArray = ToaInvocationCatalog.clientBitmaps(enabled)

    fun clear() = enabled.clear()
}
