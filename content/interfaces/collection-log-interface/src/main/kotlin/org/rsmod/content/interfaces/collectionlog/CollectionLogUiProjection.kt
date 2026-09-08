package org.rsmod.content.interfaces.collectionlog

import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogCategory
import org.rsmod.api.collectionlog.CollectionLogSection
import org.rsmod.api.collectionlog.CollectionLogState

public data class CollectionLogCategoryProgress(
    public val name: String,
    public val obtained: Int,
    public val total: Int,
)

public data class CollectionLogSectionView(
    public val categoryIndex: Int,
    public val sectionIndex: Int,
    public val category: CollectionLogCategory,
    public val section: CollectionLogSection,
    public val obtained: Int,
    public val quantities: List<Int>,
)

public data class CollectionLogSectionLocation(
    public val categoryIndex: Int,
    public val sectionIndex: Int,
)

public class CollectionLogUiProjection(public val catalog: CollectionLogCatalogRepository) {
    private val searchLocations: List<CollectionLogSectionLocation> =
        catalog.categories.flatMapIndexed { categoryIndex, category ->
            category.sections.indices.map { sectionIndex ->
                CollectionLogSectionLocation(categoryIndex, sectionIndex)
            }
        }

    public fun globalProgress(state: CollectionLogState): Pair<Int, Int> =
        catalog.canonicalItemIds.count { (state.quantities[it] ?: 0) > 0 } to
            catalog.catalog.uniqueCanonicalEntries

    public fun categoryProgress(state: CollectionLogState): List<CollectionLogCategoryProgress> =
        catalog.categories.map { category ->
            val canonicalIds = catalog.canonicalItemIds(category)
            CollectionLogCategoryProgress(
                name = category.name,
                obtained = canonicalIds.count { (state.quantities[it] ?: 0) > 0 },
                total = canonicalIds.size,
            )
        }

    public fun searchResult(globalSectionIndex: Int): CollectionLogSectionLocation? =
        searchLocations.getOrNull(globalSectionIndex)

    public fun section(
        state: CollectionLogState,
        categoryIndex: Int,
        sectionIndex: Int,
    ): CollectionLogSectionView? {
        val category = catalog.category(categoryIndex) ?: return null
        val section = catalog.section(categoryIndex, sectionIndex) ?: return null
        val quantities = section.slots.map { slot -> state.quantities[slot.canonicalItemId] ?: 0 }
        return CollectionLogSectionView(
            categoryIndex = categoryIndex,
            sectionIndex = sectionIndex,
            category = category,
            section = section,
            obtained = quantities.count { it > 0 },
            quantities = quantities,
        )
    }
}
