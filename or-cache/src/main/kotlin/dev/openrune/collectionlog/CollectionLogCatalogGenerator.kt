package dev.openrune.collectionlog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import dev.openrune.ServerCacheManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val CACHE_REVISION = 240
private const val CATALOG_SCHEMA_VERSION = 1
private const val GENERATOR_VERSION = "1.0.0"
private const val PARAM_CATEGORY_NAME = 682
private const val PARAM_CATEGORY_SECTIONS = 683
private const val PARAM_CATEGORY_ORDER = 684
private const val PARAM_SECTION_NAME = 689
private const val PARAM_SECTION_ITEMS = 690
private const val PARAM_SECTION_COUNTER = 2519
private const val DEFAULT_QUANTITY_CAP = 250
private const val STACKABLE_QUANTITY_CAP = 65_535

/**
 * Cache display IDs whose Wiki data row uses a canonical placeholder ID. These are verified by
 * identical item names and one-for-one section positions in revision 240.
 */
private val cacheToWikiItem =
    mapOf(
        13273 to 25624, // Unsired
        30803 to 30805, // Dossier
        24882 to 25629, // Plank sack
        29990 to 29992, // Alchemist's amulet
        12854 to 25630, // Flamtaer bag
        29472 to 12013, // Prospector helmet (Volcanic Mine display variant)
        29474 to 12014, // Prospector jacket (Volcanic Mine display variant)
        29476 to 12015, // Prospector legs (Volcanic Mine display variant)
        29478 to 12016, // Prospector boots (Volcanic Mine display variant)
        10859 to 25617, // Tea flask
        10877 to 25618, // Plain satchel
        10878 to 25619, // Green satchel
        10879 to 25620, // Red satchel
        10880 to 25621, // Black satchel
        10881 to 25622, // Gold satchel
        10882 to 25623, // Rune satchel
        12019 to 25627, // Coal bag
        12020 to 25628, // Gem bag
    )

private val sharedCanonicalItem =
    mapOf(29472 to 12013, 29474 to 12014, 29476 to 12015, 29478 to 12016)

private val wikiTitles =
    listOf(
        "Module:Collection log/data.json",
        "Module:Collection log/completion.json",
        "Collection log",
        "Collection log/Table",
    )

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: CollectionLogCatalogGenerator <runtime-resource-dir> <evidence-dir>"
    }
    val outputDir = Path.of(args[0]).toAbsolutePath().normalize()
    val evidenceDir = Path.of(args[1]).toAbsolutePath().normalize()
    Files.createDirectories(outputDir)
    Files.createDirectories(evidenceDir)

    ServerCacheManager.init(CACHE_REVISION)
    val mapper =
        ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    val wiki = fetchWiki(mapper)
    val wikiDataPage = wiki.getValue("Module:Collection log/data.json")
    val wikiCompletionPage = wiki.getValue("Module:Collection log/completion.json")
    val wikiItems = parseWikiItems(mapper, wikiDataPage.content)
    val generated = buildCatalog(wiki, wikiItems)

    mapper.writeValue(outputDir.resolve("catalog-r240.json").toFile(), generated.catalog)
    mapper.writeValue(
        evidenceDir.resolve("wiki-data.json").toFile(),
        mapper.readTree(wikiDataPage.content),
    )
    mapper.writeValue(
        evidenceDir.resolve("wiki-completion.json").toFile(),
        mapper.readTree(wikiCompletionPage.content),
    )
    mapper.writeValue(evidenceDir.resolve("mismatch-report.json").toFile(), generated.report)

    println(
        "Collection Log catalog generated: slots=${generated.catalog.totalSlots}, " +
            "unique=${generated.catalog.uniqueCanonicalEntries}, " +
            "sections=${generated.catalog.totalSections}, mismatches=${generated.report.mismatches.size}"
    )
    generated.report.mismatches.forEach { mismatch ->
        println("${mismatch.kind}: ${mismatch.message}")
    }
}

private fun buildCatalog(
    wiki: Map<String, WikiPage>,
    wikiItems: Map<Int, WikiItem>,
): GeneratedCatalog {
    val categories = mutableListOf<CatalogCategory>()
    val mismatches = mutableListOf<Mismatch>()
    val cacheSectionNames = mutableSetOf<String>()
    val cacheSlotPairs = mutableSetOf<Pair<Int, String>>()

    val categoryStructs =
        (0..8_000)
            .mapNotNull { structId ->
                val params =
                    ServerCacheManager.getStruct(structId)?.params ?: return@mapNotNull null
                val name = params[PARAM_CATEGORY_NAME] as? String ?: return@mapNotNull null
                val sections = params[PARAM_CATEGORY_SECTIONS] as? Int ?: return@mapNotNull null
                val order = params[PARAM_CATEGORY_ORDER] as? Int ?: return@mapNotNull null
                CategoryStruct(structId, name, sections, order, params)
            }
            .sortedBy(CategoryStruct::order)

    require(
        categoryStructs.map(CategoryStruct::name) ==
            listOf("Bosses", "Raids", "Clues", "Minigames", "Other")
    ) {
        "Unexpected revision-$CACHE_REVISION Collection Log categories: ${categoryStructs.map(CategoryStruct::name)}"
    }

    categoryStructs.forEach { category ->
        val sectionEnum =
            requireNotNull(ServerCacheManager.getEnum(category.sectionEnumId)) {
                "Missing section enum ${category.sectionEnumId} for ${category.name}"
            }
        val sections =
            sectionEnum.values.entries
                .sortedBy { (key, _) -> key as Int }
                .map { (sectionOrderAny, sectionStructAny) ->
                    val sectionOrder = sectionOrderAny as Int
                    val sectionStructId = sectionStructAny as Int
                    val params =
                        requireNotNull(ServerCacheManager.getStruct(sectionStructId)?.params) {
                            "Missing section struct $sectionStructId"
                        }
                    val sectionName =
                        params[PARAM_SECTION_NAME] as? String
                            ?: error("Section struct $sectionStructId has no name")
                    val itemEnumId =
                        params[PARAM_SECTION_ITEMS] as? Int
                            ?: error("Section struct $sectionStructId has no item enum")
                    val itemEnum =
                        requireNotNull(ServerCacheManager.getEnum(itemEnumId)) {
                            "Missing item enum $itemEnumId for $sectionName"
                        }
                    cacheSectionNames += normalize(sectionName)
                    val slots =
                        itemEnum.values.entries
                            .sortedBy { (key, _) -> key as Int }
                            .map { (slotOrderAny, itemIdAny) ->
                                val slotOrder = slotOrderAny as Int
                                val displayItemId = itemIdAny as Int
                                val definition = ServerCacheManager.getItem(displayItemId)
                                if (definition == null) {
                                    mismatches +=
                                        Mismatch(
                                            "UNKNOWN_CACHE_ITEM",
                                            "$sectionName slot $slotOrder references missing item $displayItemId",
                                        )
                                }
                                val wikiItemId = cacheToWikiItem[displayItemId] ?: displayItemId
                                val wikiItem = wikiItems[wikiItemId]
                                if (wikiItem == null) {
                                    mismatches +=
                                        Mismatch(
                                            "ITEM_MISSING_FROM_WIKI",
                                            "$sectionName slot $slotOrder item $displayItemId is absent from Wiki data",
                                        )
                                } else if (
                                    wikiItem.tabs.none { normalize(it) == normalize(sectionName) }
                                ) {
                                    mismatches +=
                                        Mismatch(
                                            "WIKI_SECTION_MISMATCH",
                                            "Item $displayItemId (${wikiItem.name}) is in cache section " +
                                                "$sectionName but Wiki tabs are ${wikiItem.tabs}",
                                        )
                                }
                                cacheSlotPairs += wikiItemId to normalize(sectionName)
                                CatalogSlot(
                                    slotIdentity = "$sectionStructId:$slotOrder",
                                    order = slotOrder,
                                    displayItemId = displayItemId,
                                    canonicalItemId =
                                        sharedCanonicalItem[displayItemId] ?: displayItemId,
                                    name =
                                        definition?.name ?: wikiItem?.name ?: "item-$displayItemId",
                                    quantityCap =
                                        if (definition != null && isStackable(definition)) {
                                            STACKABLE_QUANTITY_CAP
                                        } else {
                                            DEFAULT_QUANTITY_CAP
                                        },
                                    displayRule = "QUANTITY_WHEN_OBTAINED",
                                )
                            }
                    CatalogSection(
                        name = sectionName,
                        order = sectionOrder,
                        structId = sectionStructId,
                        itemEnumId = itemEnumId,
                        linkedCounter =
                            (params[PARAM_SECTION_COUNTER] as? Int)?.let { counterId ->
                                LinkedCounter("CACHE_ACTIVITY", counterId)
                            },
                        slots = slots,
                    )
                }
        categories +=
            CatalogCategory(
                name = category.name,
                order = category.order,
                structId = category.structId,
                sectionEnumId = category.sectionEnumId,
                clientParams =
                    (685..688)
                        .mapNotNull { param ->
                            (category.params[param] as? Int)?.let { param to it }
                        }
                        .toMap(),
                sections = sections,
            )
    }

    wikiItems.values.forEach { item ->
        item.tabs.forEach { tab ->
            val pair = item.id to normalize(tab)
            if (normalize(tab) !in cacheSectionNames) {
                mismatches +=
                    Mismatch(
                        "WIKI_SECTION_MISSING_FROM_CACHE",
                        "Wiki section '$tab' for item ${item.id} does not exist in revision-$CACHE_REVISION",
                    )
            } else if (pair !in cacheSlotPairs) {
                mismatches +=
                    Mismatch(
                        "WIKI_ITEM_MISSING_FROM_CACHE_SECTION",
                        "Wiki item ${item.id} (${item.name}) is absent from cache section '$tab'",
                    )
            }
        }
    }

    val allSlots = categories.flatMap(CatalogCategory::sections).flatMap(CatalogSection::slots)
    val metadata =
        CatalogMetadata(
            cacheRevision = CACHE_REVISION,
            generatedAt =
                wiki.values.maxByOrNull(WikiPage::timestamp)?.timestamp
                    ?: error("No Wiki revision timestamps"),
            generatorVersion = GENERATOR_VERSION,
            wikiPages =
                wiki.values.sortedBy(WikiPage::title).map {
                    WikiRevision(it.title, it.revisionId, it.timestamp, it.sha1)
                },
        )
    val catalog =
        Catalog(
            schemaVersion = CATALOG_SCHEMA_VERSION,
            metadata = metadata,
            totalSections = categories.sumOf { it.sections.size },
            totalSlots = allSlots.size,
            uniqueCanonicalEntries = allSlots.map(CatalogSlot::canonicalItemId).distinct().size,
            categories = categories,
        )
    val report =
        MismatchReport(
            metadata = metadata,
            cacheTotals =
                Totals(catalog.totalSections, catalog.totalSlots, catalog.uniqueCanonicalEntries),
            wikiUniqueItems = wikiItems.size,
            mismatches = mismatches.distinct(),
        )
    return GeneratedCatalog(catalog, report)
}

private fun fetchWiki(mapper: ObjectMapper): Map<String, WikiPage> {
    val encodedTitles = URLEncoder.encode(wikiTitles.joinToString("|"), StandardCharsets.UTF_8)
    val uri =
        URI.create(
            "https://oldschool.runescape.wiki/api.php?action=query&prop=revisions" +
                "&rvprop=ids%7Ctimestamp%7Csha1%7Ccontent&rvslots=main&format=json" +
                "&formatversion=2&titles=$encodedTitles"
        )
    val request =
        HttpRequest.newBuilder(uri)
            .header("User-Agent", "OpenRune-CollectionLog/$GENERATOR_VERSION (catalog validator)")
            .GET()
            .build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    require(response.statusCode() == 200) { "OSRS Wiki returned HTTP ${response.statusCode()}" }
    val root = mapper.readTree(response.body())
    return root.path("query").path("pages").associate { page ->
        val revision = page.path("revisions").first()
        val title = page.path("title").asText()
        title to
            WikiPage(
                title = title,
                revisionId = revision.path("revid").asLong(),
                timestamp = revision.path("timestamp").asText(),
                sha1 = revision.path("sha1").asText(),
                content = revision.path("slots").path("main").path("content").asText(),
            )
    }
}

private fun parseWikiItems(mapper: ObjectMapper, content: String): Map<Int, WikiItem> =
    mapper.readTree(content).associate { node ->
        val item =
            WikiItem(
                id = node.path("id").asInt(),
                name = node.path("name").asText(),
                tabs = node.path("tabs").map(JsonNode::asText),
            )
        item.id to item
    }

private fun isStackable(definition: Any): Boolean {
    val getter =
        definition.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && it.name in setOf("getStackable", "isStackable")
        }
    val value = runCatching { getter?.invoke(definition) }.getOrNull()
    return value == true || (value is Number && value.toInt() != 0)
}

private fun normalize(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

private data class CategoryStruct(
    val structId: Int,
    val name: String,
    val sectionEnumId: Int,
    val order: Int,
    val params: Map<Int, Any>,
)

private data class WikiPage(
    val title: String,
    val revisionId: Long,
    val timestamp: String,
    val sha1: String,
    val content: String,
)

private data class WikiItem(val id: Int, val name: String, val tabs: List<String>)

private data class CatalogMetadata(
    val cacheRevision: Int,
    val generatedAt: String,
    val generatorVersion: String,
    val wikiPages: List<WikiRevision>,
)

private data class WikiRevision(
    val title: String,
    val revisionId: Long,
    val timestamp: String,
    val sha1: String,
)

private data class Catalog(
    val schemaVersion: Int,
    val metadata: CatalogMetadata,
    val totalSections: Int,
    val totalSlots: Int,
    val uniqueCanonicalEntries: Int,
    val categories: List<CatalogCategory>,
)

private data class CatalogCategory(
    val name: String,
    val order: Int,
    val structId: Int,
    val sectionEnumId: Int,
    val clientParams: Map<Int, Int>,
    val sections: List<CatalogSection>,
)

private data class CatalogSection(
    val name: String,
    val order: Int,
    val structId: Int,
    val itemEnumId: Int,
    val linkedCounter: LinkedCounter?,
    val slots: List<CatalogSlot>,
)

private data class LinkedCounter(val kind: String, val cacheId: Int)

private data class CatalogSlot(
    val slotIdentity: String,
    val order: Int,
    val displayItemId: Int,
    val canonicalItemId: Int,
    val name: String,
    val quantityCap: Int,
    val displayRule: String,
)

private data class MismatchReport(
    val metadata: CatalogMetadata,
    val cacheTotals: Totals,
    val wikiUniqueItems: Int,
    val mismatches: List<Mismatch>,
)

private data class Totals(val sections: Int, val slots: Int, val uniqueCanonicalEntries: Int)

private data class Mismatch(val kind: String, val message: String)

private data class GeneratedCatalog(val catalog: Catalog, val report: MismatchReport)
