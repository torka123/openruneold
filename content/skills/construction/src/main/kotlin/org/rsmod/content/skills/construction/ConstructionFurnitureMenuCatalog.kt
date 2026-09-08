package org.rsmod.content.skills.construction

import org.rsmod.api.table.FurnitureRow
import org.rsmod.content.skills.construction.data.DonorBuildable

/**
 * Revision-240 bridge between the server's build mechanics and Jagex's POH menu database.
 *
 * The furniture creation clientscript expects a dbrow id, not an item id. Supplying the inventory
 * icon id in that slot makes the script read unrelated db columns, which is what caused incorrect
 * icons and malformed hover targets. World-object ids remain exclusively in
 * [DonorBuildable.builtLocIds].
 */
internal object ConstructionFurnitureMenuCatalog {
    private val rows: List<FurnitureRow> by lazy(FurnitureRow::all)

    fun resolve(buildable: DonorBuildable): ConstructionFurnitureMenuEntry? {
        val explicit = buildable.cacheRowId?.let(FurnitureRow::getRow)
        val row = explicit ?: bestRevisionRow(buildable) ?: return null
        if (row.modelObj.id != buildable.itemId) return null
        return ConstructionFurnitureMenuEntry(
            buildable = buildable,
            furnitureRowId = row.rowId,
            iconItemId = row.modelObj.id,
            displayName = row.name.trim(),
        )
    }

    fun canonicalName(buildable: DonorBuildable): String =
        resolve(buildable)?.displayName ?: buildable.name.lowercase().replace('_', ' ')

    private fun bestRevisionRow(buildable: DonorBuildable): FurnitureRow? {
        val iconRows = rows.filter { it.modelObj.id == buildable.itemId }
        if (iconRows.isEmpty()) return null
        val exactLevel = iconRows.filter { it.constructionLevel() == buildable.level }
        val candidates = if (exactLevel.isNotEmpty()) exactLevel else iconRows
        if (candidates.size == 1) return candidates.single()
        val key = normalize(buildable.name)
        return candidates.maxWithOrNull(
            compareBy<FurnitureRow> { nameScore(key, normalize(it.name)) }
                .thenBy { if (it.hiddenInBuildMenu == null) 1 else 0 }
                .thenByDescending { -it.rowId }
        )
    }

    private fun FurnitureRow.constructionLevel(): Int? =
        levelRequirement.firstOrNull { it.t0.id == CONSTRUCTION_STAT_ID }?.t1

    private fun normalize(value: String): Set<String> =
        value
            .lowercase()
            .replace("staircase up", "staircase")
            .replace("staircase down", "staircase")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter(String::isNotBlank)
            .toSet()

    private fun nameScore(left: Set<String>, right: Set<String>): Int =
        (left intersect right).size * 10 - (left union right).size

    private const val CONSTRUCTION_STAT_ID = 22
}

internal data class ConstructionFurnitureMenuEntry(
    val buildable: DonorBuildable,
    val furnitureRowId: Int,
    val iconItemId: Int,
    val displayName: String,
) {
    val hoverText: String
        get() = "Build: $displayName"
}
