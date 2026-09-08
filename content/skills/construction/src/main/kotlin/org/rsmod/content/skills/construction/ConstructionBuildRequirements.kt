package org.rsmod.content.skills.construction

import org.rsmod.content.skills.construction.data.DonorBuildable
import org.rsmod.content.skills.construction.data.DonorRequirement

internal sealed interface ConstructionBuildProblem {
    data class Level(val required: Int, val available: Int) : ConstructionBuildProblem

    data object Hammer : ConstructionBuildProblem

    data object Saw : ConstructionBuildProblem

    data class Material(val itemId: Int, val required: Int, val available: Int) :
        ConstructionBuildProblem

    data class Upgrade(val requiredBuildable: String) : ConstructionBuildProblem
}

internal object ConstructionBuildRequirements {
    private val toolFreeAnimations = setOf("GROUND_PLANT", "MAKE_TABLET", "READ_LECTERN", "-1")

    fun materialTotals(buildable: DonorBuildable): Map<Int, Int> =
        buildable.requirements
            .filterIsInstance<DonorRequirement.ITEM>()
            .groupBy(DonorRequirement.ITEM::itemId)
            .mapValues { (_, entries) -> entries.sumOf(DonorRequirement.ITEM::amount) }

    fun firstProblem(
        buildable: DonorBuildable,
        constructionLevel: Int,
        hasHammer: Boolean,
        hasSaw: Boolean,
        hasCrystalSaw: Boolean,
        currentBuildable: String?,
        itemCount: (Int) -> Int,
    ): ConstructionBuildProblem? {
        val requiresTools = buildable.animation !in toolFreeAnimations
        val effectiveLevel = constructionLevel + if (requiresTools && hasCrystalSaw) 3 else 0
        if (effectiveLevel < buildable.level) {
            return ConstructionBuildProblem.Level(buildable.level, effectiveLevel)
        }
        if (requiresTools && !hasHammer) return ConstructionBuildProblem.Hammer
        if (requiresTools && !hasSaw) return ConstructionBuildProblem.Saw
        materialTotals(buildable).forEach { (itemId, required) ->
            val available = itemCount(itemId)
            if (available < required) {
                return ConstructionBuildProblem.Material(itemId, required, available)
            }
        }
        buildable.requirements.filterIsInstance<DonorRequirement.UPGRADE>().forEach { upgrade ->
            if (currentBuildable != upgrade.buildable) {
                return ConstructionBuildProblem.Upgrade(upgrade.buildable)
            }
        }
        return null
    }
}
