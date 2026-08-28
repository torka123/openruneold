package org.rsmod.content.minigames.giantsfoundry

/**
 * Mechanics whose values are pinned in docs/giants_foundry/wiki-data.json. Runtime cache IDs and
 * heat/sweet-spot tuning intentionally live outside this model until their evidence is complete.
 */
internal enum class FoundryDescriptor {
    BROAD,
    NARROW,
    HEAVY,
    LIGHT,
    SPIKED,
    FLAT,
}

internal enum class MouldSlot {
    FORTE,
    BLADE,
    TIP,
}

internal enum class FoundryMetal(
    val smithingLevel: Int,
    val tier: Int,
) {
    BRONZE(15, 1),
    IRON(15, 2),
    STEEL(30, 3),
    MITHRIL(50, 4),
    ADAMANT(70, 5),
    RUNE(85, 6),
}

internal data class DescriptorScores(
    val broad: Int,
    val narrow: Int,
    val heavy: Int,
    val light: Int,
    val spiked: Int,
    val flat: Int,
) {
    operator fun get(descriptor: FoundryDescriptor): Int =
        when (descriptor) {
            FoundryDescriptor.BROAD -> broad
            FoundryDescriptor.NARROW -> narrow
            FoundryDescriptor.HEAVY -> heavy
            FoundryDescriptor.LIGHT -> light
            FoundryDescriptor.SPIKED -> spiked
            FoundryDescriptor.FLAT -> flat
        }
}

internal data class FoundryMould(
    val key: String,
    val displayName: String,
    val slot: MouldSlot,
    val smithingLevel: Int?,
    val cost: Int,
    val scores: DescriptorScores,
) {
    val default: Boolean
        get() = cost == 0
}

internal object FoundryMoulds {
    private fun scores(
        broad: Int,
        narrow: Int,
        heavy: Int,
        light: Int,
        spiked: Int,
        flat: Int,
    ) = DescriptorScores(broad, narrow, heavy, light, spiked, flat)

    val all: List<FoundryMould> =
        listOf(
            FoundryMould("chopper_forte", "Chopper Forte", MouldSlot.FORTE, null, 0, scores(4, 0, 0, 4, 0, 4)),
            FoundryMould("gladius_ricasso", "Gladius Ricasso", MouldSlot.FORTE, null, 0, scores(4, 0, 4, 0, 0, 4)),
            FoundryMould("disarming_forte", "Disarming Forte", MouldSlot.FORTE, null, 0, scores(0, 4, 0, 4, 4, 0)),
            FoundryMould("medusa_ricasso", "Medusa Ricasso", MouldSlot.FORTE, null, 0, scores(8, 0, 6, 0, 0, 8)),
            FoundryMould("serpent_ricasso", "Serpent Ricasso", MouldSlot.FORTE, null, 0, scores(0, 6, 0, 8, 0, 8)),
            FoundryMould("serrated_forte", "Serrated Forte", MouldSlot.FORTE, null, 0, scores(0, 8, 8, 0, 6, 0)),
            FoundryMould("saw_blade", "Saw Blade", MouldSlot.BLADE, null, 0, scores(4, 0, 0, 4, 4, 0)),
            FoundryMould("defenders_edge", "Defenders Edge", MouldSlot.BLADE, null, 0, scores(4, 0, 4, 0, 4, 0)),
            FoundryMould("fish_blade", "Fish Blade", MouldSlot.BLADE, null, 0, scores(0, 4, 0, 4, 0, 4)),
            FoundryMould("medusa_blade", "Medusa Blade", MouldSlot.BLADE, null, 0, scores(8, 0, 8, 0, 0, 6)),
            FoundryMould("stiletto_blade", "Stiletto Blade", MouldSlot.BLADE, null, 0, scores(0, 8, 0, 6, 0, 8)),
            FoundryMould("gladius_edge", "Gladius Edge", MouldSlot.BLADE, null, 0, scores(0, 6, 8, 0, 0, 8)),
            FoundryMould("people_poker_point", "People Poker Point", MouldSlot.TIP, null, 0, scores(0, 4, 4, 0, 0, 4)),
            FoundryMould("chopper_tip", "Chopper Tip", MouldSlot.TIP, null, 0, scores(4, 0, 0, 4, 4, 0)),
            FoundryMould("medusas_head", "Medusa's Head", MouldSlot.TIP, null, 0, scores(4, 0, 4, 0, 4, 0)),
            FoundryMould("serpents_fang", "Serpent's Fang", MouldSlot.TIP, null, 0, scores(0, 8, 0, 6, 8, 0)),
            FoundryMould("gladius_point", "Gladius Point", MouldSlot.TIP, null, 0, scores(0, 8, 8, 0, 0, 6)),
            FoundryMould("saw_tip", "Saw Tip", MouldSlot.TIP, null, 0, scores(6, 0, 8, 0, 8, 0)),
            FoundryMould("stiletto_forte", "Stiletto Forte", MouldSlot.FORTE, 50, 300, scores(0, 8, 0, 10, 0, 8)),
            FoundryMould("defender_base", "Defender Base", MouldSlot.FORTE, 51, 300, scores(8, 0, 10, 0, 0, 8)),
            FoundryMould("juggernaut_forte", "Juggernaut Forte", MouldSlot.FORTE, 61, 350, scores(4, 0, 4, 0, 16, 0)),
            FoundryMould("chopper_forte_1", "Chopper Forte +1", MouldSlot.FORTE, 70, 400, scores(3, 0, 0, 4, 0, 18)),
            FoundryMould("spiker", "Spiker!", MouldSlot.FORTE, 79, 450, scores(0, 1, 2, 0, 22, 0)),
            FoundryMould("flamberge_blade", "Flamberge Blade", MouldSlot.BLADE, 48, 300, scores(0, 8, 0, 8, 10, 0)),
            FoundryMould("serpent_blade", "Serpent Blade", MouldSlot.BLADE, 50, 300, scores(0, 10, 0, 8, 0, 8)),
            FoundryMould("claymore_blade", "Claymore Blade", MouldSlot.BLADE, 59, 350, scores(16, 0, 4, 0, 0, 4)),
            FoundryMould("fleur_de_blade", "Fleur de Blade", MouldSlot.BLADE, 71, 400, scores(4, 0, 18, 0, 1, 0)),
            FoundryMould("choppa", "Choppa!", MouldSlot.BLADE, 80, 450, scores(1, 0, 0, 22, 0, 2)),
            FoundryMould("corrupted_point", "Corrupted Point", MouldSlot.TIP, 49, 300, scores(0, 8, 0, 10, 8, 0)),
            FoundryMould("defenders_tip", "Defenders Tip", MouldSlot.TIP, 52, 300, scores(10, 0, 8, 0, 8, 0)),
            FoundryMould("serrated_tip", "Serrated Tip", MouldSlot.TIP, 60, 350, scores(0, 4, 0, 16, 4, 0)),
            FoundryMould("needle_point", "Needle Point", MouldSlot.TIP, 69, 400, scores(0, 18, 0, 3, 0, 4)),
            FoundryMould("the_point", "The Point!", MouldSlot.TIP, 81, 450, scores(2, 0, 0, 1, 0, 22)),
        )

    val byKey: Map<String, FoundryMould> = all.associateBy(FoundryMould::key)
}

internal data class FoundryCommission(
    val id: Long,
    val first: FoundryDescriptor,
    val second: FoundryDescriptor,
    val assignedAtMillis: Long,
)

internal data class FoundryCrucible(
    val amounts: Map<FoundryMetal, Int> = emptyMap(),
) {
    val total: Int
        get() = amounts.values.sum()

    fun amount(metal: FoundryMetal): Int = amounts[metal] ?: 0

    fun add(metal: FoundryMetal, count: Int): FoundryCrucible? {
        if (count <= 0 || total + count > FoundryRules.CRUCIBLE_CAPACITY) return null
        return copy(amounts = amounts + (metal to (amount(metal) + count)))
    }

    fun empty(): FoundryCrucible = FoundryCrucible()
}

internal enum class PreformLifecycle {
    Poured,
    Processing,
    Stored,
    Complete,
    Ruined,
}

/** The three workstations, their required heat zone, and their genuine per-tick movement. */
internal enum class FoundryTool(
    val displayName: String,
    val requiredHeat: FoundryHeatBand,
    val progressPerAction: Int,
    val heatChangePerAction: Int,
    val actionTicks: Int,
) {
    TRIP_HAMMER("trip hammer", FoundryHeatBand.HIGH, 20, -25, 5),
    GRINDSTONE("grindstone", FoundryHeatBand.MEDIUM, 10, 15, 2),
    POLISHING_WHEEL("polishing wheel", FoundryHeatBand.LOW, 10, -17, 2),
}

internal enum class FoundryHeatBand(val centre: Int) {
    LOW(167),
    MEDIUM(500),
    HIGH(833),
}

internal data class FoundryPreform(
    val commissionId: Long,
    val metalScore: Int,
    val mouldScore: Int,
    val maxQuality: Int,
    val quality: Int,
    val sections: Int,
    val lifecycle: PreformLifecycle = PreformLifecycle.Poured,
    val heat: Int = FoundryRules.MAX_HEAT,
    val progress: Int = 0,
    val stages: List<FoundryTool> = FoundryRules.stagePlan(commissionId, sections),
    val activeStage: Int = 0,
    val stageProgress: Int = 0,
    val mistakes: Int = 0,
    val lastHeatUpdateMillis: Long = System.currentTimeMillis(),
)

internal data class FoundryReward(
    val quality: Int,
    val smithingXp: Int,
    val coins: Int,
    val reputation: Int,
)
