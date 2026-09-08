package org.rsmod.api.collectionlog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.inject.Inject
import jakarta.inject.Singleton

public data class CollectionLogWikiRevision(
    public val title: String,
    public val revisionId: Long,
    public val timestamp: String,
    public val sha1: String,
)

public data class CollectionLogCatalogMetadata(
    public val cacheRevision: Int,
    public val generatedAt: String,
    public val generatorVersion: String,
    public val wikiPages: List<CollectionLogWikiRevision>,
)

public data class CollectionLogLinkedCounter(public val kind: String, public val cacheId: Int)

public data class CollectionLogSlot(
    public val slotIdentity: String,
    public val order: Int,
    public val displayItemId: Int,
    public val canonicalItemId: Int,
    public val name: String,
    public val quantityCap: Int,
    public val displayRule: String,
)

public data class CollectionLogSection(
    public val name: String,
    public val order: Int,
    public val structId: Int,
    public val itemEnumId: Int,
    public val linkedCounter: CollectionLogLinkedCounter?,
    public val slots: List<CollectionLogSlot>,
)

public data class CollectionLogCategory(
    public val name: String,
    public val order: Int,
    public val structId: Int,
    public val sectionEnumId: Int,
    public val clientParams: Map<Int, Int>,
    public val sections: List<CollectionLogSection>,
)

public data class CollectionLogCatalog(
    public val schemaVersion: Int,
    public val metadata: CollectionLogCatalogMetadata,
    public val totalSections: Int,
    public val totalSlots: Int,
    public val uniqueCanonicalEntries: Int,
    public val categories: List<CollectionLogCategory>,
)

public object CollectionLogCatalogLoader {
    private const val RESOURCE = "/collection-log/catalog-r240.json"

    public fun load(): CollectionLogCatalog {
        val stream =
            requireNotNull(CollectionLogCatalogLoader::class.java.getResourceAsStream(RESOURCE)) {
                "Missing checked-in Collection Log catalog $RESOURCE"
            }
        return stream.use { jacksonObjectMapper().readValue(it) }
    }
}

@Singleton
public class CollectionLogCatalogRepository @Inject constructor() {
    public val catalog: CollectionLogCatalog = CollectionLogCatalogLoader.load()
    public val categories: List<CollectionLogCategory> = catalog.categories.sortedBy { it.order }
    public val sections: List<CollectionLogSection> =
        categories.flatMap { category -> category.sections.sortedBy { it.order } }
    public val slots: List<CollectionLogSlot> =
        sections.flatMap { section -> section.slots.sortedBy { it.order } }

    private val slotsByCanonical: Map<Int, List<CollectionLogSlot>> =
        slots.groupBy(CollectionLogSlot::canonicalItemId)
    public val canonicalItemIds: Set<Int> = slotsByCanonical.keys
    private val canonicalIdsByCategoryOrder: Map<Int, Set<Int>> = buildMap {
        val assigned = hashSetOf<Int>()
        categories.forEach { category ->
            val firstOccurrenceIds =
                category.sections
                    .asSequence()
                    .flatMap { section -> section.slots.asSequence() }
                    .map(CollectionLogSlot::canonicalItemId)
                    .filter(assigned::add)
                    .toCollection(linkedSetOf())
            put(category.order, firstOccurrenceIds)
        }
    }
    private val acceptedItemToCanonical: Map<Int, Int> = buildMap {
        slots.forEach { slot ->
            put(slot.displayItemId, slot.canonicalItemId)
            put(slot.canonicalItemId, slot.canonicalItemId)
        }
    }
    private val capByCanonical: Map<Int, Int> =
        slotsByCanonical.mapValues { (_, matches) -> matches.maxOf(CollectionLogSlot::quantityCap) }

    init {
        require(catalog.schemaVersion == 1) {
            "Unsupported Collection Log catalog schema ${catalog.schemaVersion}"
        }
        require(catalog.metadata.cacheRevision == 240) {
            "Collection Log catalog cache revision is ${catalog.metadata.cacheRevision}, expected 240"
        }
        require(categories.map(CollectionLogCategory::order) == categories.indices.toList()) {
            "Collection Log category order is not contiguous"
        }
        categories.forEach { category ->
            require(
                category.sections.map(CollectionLogSection::order).sorted() ==
                    category.sections.indices.toList()
            ) {
                "Collection Log section order is not contiguous for ${category.name}"
            }
            category.sections.forEach { section ->
                require(
                    section.slots.map(CollectionLogSlot::order).sorted() ==
                        section.slots.indices.toList()
                ) {
                    "Collection Log slot order is not contiguous for ${section.name}"
                }
            }
        }
        require(slots.size == catalog.totalSlots)
        require(sections.size == catalog.totalSections)
        require(slotsByCanonical.size == catalog.uniqueCanonicalEntries)
        require(
            canonicalIdsByCategoryOrder.values.sumOf(Set<Int>::size) ==
                catalog.uniqueCanonicalEntries
        ) {
            "Collection Log category allocation did not account for every canonical entry exactly once"
        }
    }

    public fun canonicalItemId(itemId: Int): Int? = acceptedItemToCanonical[itemId]

    public fun canonicalItemIds(category: CollectionLogCategory): Set<Int> =
        canonicalIdsByCategoryOrder[category.order].orEmpty()

    public fun quantityCap(canonicalItemId: Int): Int? = capByCanonical[canonicalItemId]

    public fun slotsFor(canonicalItemId: Int): List<CollectionLogSlot> =
        slotsByCanonical[canonicalItemId].orEmpty()

    public fun category(index: Int): CollectionLogCategory? = categories.getOrNull(index)

    public fun section(categoryIndex: Int, sectionIndex: Int): CollectionLogSection? =
        category(categoryIndex)
            ?.sections
            ?.sortedBy(CollectionLogSection::order)
            ?.getOrNull(sectionIndex)
}
