package dev.openrune.audit.tob

import com.google.gson.GsonBuilder
import dev.openrune.OsrsCacheProvider
import dev.openrune.definition.Definition
import dev.openrune.filesystem.Cache
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name

private const val SOURCE_REVISION = 229
private const val DESTINATION_REVISION = 240
private const val MAP_INDEX = 5
private const val MODEL_INDEX = 7
private const val LEGACY_FRAME_INDEX = 0
private const val SKELETAL_ANIMATION_INDEX = 22

public fun main(args: Array<String>) {
    require(args.size == 5) {
        "Usage: <reason-cache> <stock-229-cache> <destination-live-cache> <donor-xeric-source> <output-directory>"
    }
    val sourcePath = Path.of(args[0]).toAbsolutePath().normalize()
    val stockPath = Path.of(args[1]).toAbsolutePath().normalize()
    val destinationPath = Path.of(args[2]).toAbsolutePath().normalize()
    val donorSourcePath = Path.of(args[3]).toAbsolutePath().normalize()
    val outputPath = Path.of(args[4]).toAbsolutePath().normalize()
    listOf(sourcePath, stockPath, destinationPath, donorSourcePath).forEach {
        require(Files.isDirectory(it)) { "Missing audit input directory: $it" }
    }
    Files.createDirectories(outputPath)

    val sourceReferences = SourceReferenceScanner.scan(donorSourcePath)
    val source = CacheSnapshot.load(sourcePath, SOURCE_REVISION)
    val stock = CacheSnapshot.load(stockPath, SOURCE_REVISION)
    val destination = CacheSnapshot.load(destinationPath, DESTINATION_REVISION)
    try {
        val entries =
            buildMappings(sourceReferences, source, stock, destination)
                .sortedWith(compareBy<IdMapping>({ it.type }, { it.sourceId }))
        val unresolved =
            entries
                .filter {
                    it.classification == Classification.UNRESOLVED.name ||
                        it.missingDependencies.isNotEmpty()
                }
                .map {
                    UnresolvedItem(
                        key = "${it.type.lowercase()}-${it.sourceId}",
                        status = "UNRESOLVED",
                        detail =
                            buildString {
                                append(it.name)
                                if (it.classification == Classification.UNRESOLVED.name) {
                                    append(" has no verified revision-240 mapping")
                                }
                                if (it.missingDependencies.isNotEmpty()) {
                                    append("; missing dependencies: ")
                                    append(it.missingDependencies.joinToString())
                                }
                            },
                    )
                }

        val manifest =
            AuditManifest(
                schemaVersion = 1,
                generatedAt = Instant.now().toString(),
                status = if (unresolved.isEmpty()) "COMPLETE" else "AUDIT_WITH_UNRESOLVED",
                source = source.summary(),
                stock229 = stock.summary(),
                destination = destination.summary(),
                sourceCode =
                    SourceCodeSummary(sourceReferences.files, sourceReferences.references.size),
                mappingCount = entries.size,
                classificationCounts =
                    entries.groupingBy(IdMapping::classification).eachCount().toSortedMap(),
                cacheArchivesChanged = emptyList(),
            )
        val graph = buildDependencyGraph(entries)

        writeJson(outputPath.resolve("reason231-tob-manifest.json"), manifest)
        writeJson(
            outputPath.resolve("reason231-to-openrune240-tob-id-map.json"),
            IdMapDocument(
                schemaVersion = 1,
                sourceRevisionLabel = "Reason build 229.1 (user described as 231)",
                destinationRevision = DESTINATION_REVISION,
                classifications = Classification.entries.map(Enum<*>::name),
                entries = entries,
            ),
        )
        writeJson(outputPath.resolve("reason231-tob-dependency-graph.json"), graph)
        writeJson(
            outputPath.resolve("reason231-tob-cache-change-allowlist.json"),
            CacheAllowlist(
                schemaVersion = 1,
                policy =
                    "No cache archive may be changed unless the exact raid-only asset and every recursive dependency are listed here first.",
                allowedChanges = emptyList(),
                decision = "NO_CACHE_CHANGES_APPROVED_NATIVE_240_ASSETS_PREFERRED",
            ),
        )
        writeJson(
            outputPath.resolve("reason231-tob-unresolved.json"),
            UnresolvedDocument(
                schemaVersion = 1,
                items =
                    listOf(
                        UnresolvedItem(
                            key = "donor-revision-label",
                            status = "RESOLVED_WITH_CORRECTION",
                            detail =
                                "The donor source declares build 229.1 and OpenRS2 cache 2048; the supplied revision-231 label is not supported by the local evidence.",
                        ),
                        UnresolvedItem(
                            key = "authoritative-reason-cache-root",
                            status = "RESOLVED_WITH_DISTRIBUTION_EVIDENCE",
                            detail =
                                "The server distribution cache at server reasonps/data/cache is used. The sibling data reasonps cache is client data and packed-cache is a later packing output; server.properties was absent from the archive.",
                        ),
                    ) + unresolved,
            ),
        )
        Files.writeString(
            outputPath.resolve("reason231-tob-audit.md"),
            markdown(manifest, entries, unresolved),
        )

        println(
            "ToB cache audit complete: mappings=${entries.size}, unresolved=${unresolved.size}, output=${outputPath.absolutePathString()}"
        )
    } finally {
        source.close()
        stock.close()
        destination.close()
    }
}

private enum class Classification {
    NATIVE_240_SAME_ID,
    NATIVE_240_DIFFERENT_ID,
    NATIVE_240_REPLACEMENT,
    STOCK_231_NOT_NEEDED,
    REASON_CUSTOM_REQUIRED,
    REASON_MODIFIED_REQUIRED,
    UNRESOLVED,
    UNRELATED,
}

private data class SourceReference(val type: String, val id: Int, val components: Set<String>)

private data class SourceScan(val files: Int, val references: List<SourceReference>)

private object SourceReferenceScanner {
    private val constant =
        Regex(
            "(?:static\\s+final\\s+int|private\\s+final\\s+int)\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*(\\d+)"
        )
    private val patterns =
        linkedMapOf(
            "NPC" to listOf(Regex("(?:spawnNPC|new\\s+NPC)\\s*\\(\\s*([A-Z][A-Z0-9_]*|\\d+)")),
            "LOC" to
                listOf(
                    Regex(
                        "(?:ObjectAction\\.register|spawnObject|getObject|Tile\\.getObject)\\s*\\(\\s*([A-Z][A-Z0-9_]*|\\d+)"
                    ),
                    Regex("ItemObjectAction\\.register\\s*\\([^,]+,\\s*([A-Z][A-Z0-9_]*|\\d+)"),
                ),
            "OBJ" to
                listOf(
                    Regex("new\\s+Item\\s*\\(\\s*([A-Z][A-Z0-9_]*|\\d+)"),
                    Regex("(?:contains|hasId)\\s*\\(\\s*([A-Z][A-Z0-9_]*|\\d+)"),
                ),
            "SEQUENCE" to
                listOf(
                    Regex("\\.animate\\s*\\(\\s*(\\d+)"),
                    Regex("(?:^|[^A-Za-z])animate\\s*\\([^,]+,\\s*(\\d+)"),
                    Regex("delayedAnimation\\s*\\([^,]+,\\s*(\\d+)"),
                    Regex("new\\s+Animation\\s*\\(\\s*(\\d+)"),
                ),
            "SPOTANIM" to
                listOf(
                    Regex("new\\s+Projectile\\s*\\(\\s*(\\d+)"),
                    Regex("sendGraphics\\s*\\(\\s*(\\d+)"),
                ),
        )

    fun scan(root: Path): SourceScan {
        val files =
            Files.walk(root).use {
                it.filter(Files::isRegularFile).filter { file -> file.extension == "java" }.toList()
            }
        val constants = HashMap<String, Int>()
        for (file in files) {
            val text = Files.readString(file)
            constant.findAll(text).forEach {
                constants[it.groupValues[1]] = it.groupValues[2].toInt()
            }
        }
        val found = linkedMapOf<Pair<String, Int>, MutableSet<String>>()
        for (file in files) {
            val relative = root.relativize(file).toString().replace('\\', '/')
            Files.readAllLines(file).forEachIndexed { index, line ->
                for ((type, regexes) in patterns) {
                    for (regex in regexes) {
                        regex.findAll(line).forEach matchLoop@{ match ->
                            val token = match.groupValues[1]
                            val id = token.toIntOrNull() ?: constants[token] ?: return@matchLoop
                            if (id >= 0) {
                                found
                                    .getOrPut(type to id, ::linkedSetOf)
                                    .add("$relative:${index + 1}")
                            }
                        }
                    }
                }
            }
        }

        Regex("new\\s+Chunk\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")
            .findAll(files.joinToString("\n") { Files.readString(it) })
            .forEach { match ->
                val chunkX = match.groupValues[1].toInt()
                val chunkZ = match.groupValues[2].toInt()
                for (dx in 0 until 4) {
                    for (dz in 0 until 4) {
                        val mapSquareX = (chunkX + dx) shr 3
                        val mapSquareZ = (chunkZ + dz) shr 3
                        val region = (mapSquareX shl 8) or mapSquareZ
                        found.getOrPut("REGION" to region, ::linkedSetOf).add("tob/dungeon/room")
                    }
                }
            }
        Regex("build(?:Sw|Se)?\\s*\\(\\s*(\\d+)")
            .findAll(files.joinToString("\n") { Files.readString(it) })
            .forEach { match ->
                val region = match.groupValues[1].toInt()
                found.getOrPut("REGION" to region, ::linkedSetOf).add("tob/dungeon/room")
            }
        return SourceScan(
            files = files.size,
            references =
                found.map { (key, components) ->
                    SourceReference(key.first, key.second, components)
                },
        )
    }
}

private class CacheSnapshot
private constructor(
    val path: Path,
    val revision: Int,
    private val cache: Cache,
    private val provider: OsrsCacheProvider,
    private val spotanims: Map<Int, Any>,
) : AutoCloseable {
    val definitions: Map<String, Map<Int, Any>> =
        linkedMapOf(
            "NPC" to provider.npcs,
            "LOC" to provider.objects,
            "OBJ" to provider.items,
            "SEQUENCE" to provider.anims,
            "SPOTANIM" to spotanims,
            "VARBIT" to provider.varbits,
            "VARP" to provider.varps,
            "ENUM" to provider.enums,
            "STRUCT" to provider.structs,
            "DBROW" to provider.dbrows,
            "DBTABLE" to provider.dbtables,
        )

    fun definition(type: String, id: Int): DefinitionView? {
        if (type == "REGION") {
            return if (regionExists(id)) {
                DefinitionView(id, "region_$id", "region=$id", emptyList())
            } else {
                null
            }
        }
        val value = definitions[type]?.get(id) ?: return null
        return DefinitionView(
            id,
            definitionName(value, type, id),
            canonical(value),
            dependencies(type, value),
        )
    }

    private fun regionExists(id: Int): Boolean {
        if (id in cache.archives(MAP_INDEX).toSet() || namedMapRegionExists(cache, id)) return true
        if (revision != DESTINATION_REVISION || path.fileName.toString() != "LIVE") return false
        val serverPath = path.resolveSibling("SERVER")
        if (!Files.isDirectory(serverPath)) return false
        val serverCache = Cache.load(serverPath)
        return try {
            id in serverCache.archives(MAP_INDEX).toSet() || namedMapRegionExists(serverCache, id)
        } finally {
            serverCache.close()
        }
    }

    private fun namedMapRegionExists(target: Cache, regionId: Int): Boolean {
        val mapSquareX = regionId ushr 8
        val mapSquareZ = regionId and 0xFF
        return target.archiveId(MAP_INDEX, "m${mapSquareX}_${mapSquareZ}") >= 0 &&
            target.archiveId(MAP_INDEX, "l${mapSquareX}_${mapSquareZ}") >= 0
    }

    fun findByName(type: String, name: String): List<DefinitionView> {
        val normalized = normalize(name)
        return definitions[type].orEmpty().mapNotNull { (id, value) ->
            val candidate = definitionName(value, type, id)
            if (normalize(candidate) == normalized) {
                DefinitionView(id, candidate, canonical(value), dependencies(type, value))
            } else {
                null
            }
        }
    }

    fun findContainingName(type: String, token: String): List<DefinitionView> {
        val normalized = normalize(token)
        return definitions[type].orEmpty().mapNotNull { (id, value) ->
            val candidate = definitionName(value, type, id)
            if (normalize(candidate).contains(normalized)) {
                DefinitionView(id, candidate, canonical(value), dependencies(type, value))
            } else {
                null
            }
        }
    }

    fun namedTobDefinitions(): List<SourceReference> {
        val result = linkedMapOf<Pair<String, Int>, SourceReference>()
        for ((type, values) in definitions) {
            if (type !in setOf("NPC", "LOC", "OBJ")) continue
            for ((id, value) in values) {
                val name = definitionName(value, type, id)
                if (isTobName(name)) {
                    result[type to id] = SourceReference(type, id, setOf("decoded-name:$name"))
                }
            }
        }
        return result.values.toList()
    }

    fun dependencyExists(dependency: AssetDependency): Boolean =
        when (dependency.type) {
            "MODEL" -> dependency.id in cache.archives(MODEL_INDEX).toSet()
            "SEQUENCE" -> dependency.id in provider.anims
            "SPOTANIM" -> dependency.id in spotanims
            "VARBIT" -> dependency.id in provider.varbits
            "VARP" -> dependency.id in provider.varps
            "FRAME" -> dependency.id in cache.archives(LEGACY_FRAME_INDEX).toSet()
            "SKELETAL_ANIMATION" ->
                dependency.id in cache.archives(SKELETAL_ANIMATION_INDEX).toSet()
            else -> true
        }

    fun summary(): CacheSummary =
        CacheSummary(
            path = path.absolutePathString(),
            revision = revision,
            cacheManifestSha256 = cacheManifestSha256(path),
            definitionCounts = definitions.mapValues { it.value.size },
            maximumIds = definitions.mapValues { (_, values) -> values.keys.maxOrNull() ?: -1 },
        )

    override fun close() = cache.close()

    companion object {
        fun load(path: Path, revision: Int): CacheSnapshot {
            val cache = Cache.load(path)
            return try {
                val provider = OsrsCacheProvider(cache, revision)
                provider.init()
                val spotanims = mutableMapOf<Int, dev.openrune.definition.type.SpotAnimType>()
                OsrsCacheProvider.SpotAnimDecoder(revision).load(cache, spotanims)
                @Suppress("UNCHECKED_CAST")
                CacheSnapshot(path, revision, cache, provider, spotanims as Map<Int, Any>)
            } catch (error: Throwable) {
                cache.close()
                throw error
            }
        }
    }
}

private data class DefinitionView(
    val id: Int,
    val name: String,
    val canonical: String,
    val dependencies: List<AssetDependency>,
)

private fun buildMappings(
    scan: SourceScan,
    source: CacheSnapshot,
    stock: CacheSnapshot,
    destination: CacheSnapshot,
): List<IdMapping> {
    val refs =
        (scan.references + source.namedTobDefinitions())
            .groupBy { it.type to it.id }
            .map { (key, values) ->
                SourceReference(
                    key.first,
                    key.second,
                    values.flatMapTo(linkedSetOf(), SourceReference::components),
                )
            }

    return refs.map { ref ->
        val sourceDef = source.definition(ref.type, ref.id)
        val stockDef = stock.definition(ref.type, ref.id)
        val sameId = destination.definition(ref.type, ref.id)
        val byName = sourceDef?.let { destination.findByName(ref.type, it.name).singleOrNull() }
        // The revision-240 decoder cannot decode the initial Verzik forms, while the donor's
        // second tornado path references a private-server-only placeholder. Use the adjacent
        // native combat forms and the donor's own verified tornado definition instead.
        val verifiedNativeSubstitution =
            when (ref.type to ref.id) {
                "NPC" to 8369 -> destination.definition("NPC", 8370)
                "NPC" to 10847 -> destination.definition("NPC", 10848)
                "NPC" to 20015 -> destination.definition("NPC", 8386)
                else -> null
            }
        val destinationDef =
            verifiedNativeSubstitution
                ?: sameId?.takeIf {
                    sourceDef != null && normalize(it.name) == normalize(sourceDef.name)
                }
                ?: byName
        val sourceModified =
            sourceDef != null && stockDef != null && sourceDef.canonical != stockDef.canonical
        val classification =
            when {
                verifiedNativeSubstitution != null -> Classification.NATIVE_240_REPLACEMENT
                sourceDef == null -> Classification.UNRESOLVED
                destinationDef?.id == ref.id && sourceModified ->
                    Classification.NATIVE_240_REPLACEMENT
                destinationDef?.id == ref.id -> Classification.NATIVE_240_SAME_ID
                destinationDef != null -> Classification.NATIVE_240_DIFFERENT_ID
                stockDef == null -> Classification.REASON_CUSTOM_REQUIRED
                else -> Classification.UNRESOLVED
            }
        val dependencies = sourceDef?.dependencies.orEmpty().distinct()
        val missing = dependencies.filterNot(destination::dependencyExists)
        IdMapping(
            sourceId = ref.id,
            name = sourceDef?.name ?: "unresolved_${ref.type.lowercase()}_${ref.id}",
            type = ref.type,
            referencingRaidComponents = ref.components.sorted(),
            nativeRevision240Match = destinationDef?.id,
            destinationId = destinationDef?.id,
            classification = classification.name,
            reasonModifiedFromStock229 = sourceModified,
            bytesMustBeCopied =
                classification in
                    setOf(
                        Classification.REASON_CUSTOM_REQUIRED,
                        Classification.REASON_MODIFIED_REQUIRED,
                    ),
            dependencies = dependencies,
            missingDependencies = missing,
            verificationSource =
                "Decoded Reason build-229 cache, clean OpenRS2 cache 2048, and active OpenRune revision-240 LIVE cache",
            verificationTest =
                if (destinationDef == null)
                    "Resolve a revision-240 definition before implementation"
                else
                    "Definition resolves; recursively referenced cache groups must exist in revision 240",
        )
    }
}

private fun dependencies(type: String, value: Any): List<AssetDependency> {
    val out = linkedSetOf<AssetDependency>()
    for ((name, raw) in reflectedFields(value)) {
        val lower = name.lowercase()
        val ids = integerValues(raw).filter { it >= 0 }
        val dependencyType =
            when {
                "model" in lower -> "MODEL"
                lower == "skeletalid" -> "SKELETAL_ANIMATION"
                "frame" in lower -> "FRAME"
                "spotanim" in lower || "graphic" in lower -> "SPOTANIM"
                "sequence" in lower || lower.endsWith("anim") -> "SEQUENCE"
                "varbit" in lower -> "VARBIT"
                "varp" in lower -> "VARP"
                "sprite" in lower -> "SPRITE"
                "font" in lower -> "FONT"
                "transform" in lower -> type
                else -> null
            }
        if (dependencyType == "FRAME") {
            ids.forEach { packed ->
                out += AssetDependency("FRAME", packed ushr 16, "$name:${packed and 0xFFFF}")
            }
        } else if (dependencyType != null) {
            ids.forEach { out += AssetDependency(dependencyType, it, name) }
        }
    }
    return out.toList()
}

private fun integerValues(value: Any?): List<Int> =
    when (value) {
        null -> emptyList()
        is Number -> listOf(value.toInt())
        is IntArray -> value.toList()
        is ShortArray -> value.map(Short::toInt)
        is LongArray -> value.map(Long::toInt)
        is Array<*> -> value.flatMap(::integerValues)
        is Iterable<*> -> value.flatMap(::integerValues)
        is Map<*, *> -> value.values.flatMap(::integerValues)
        is Definition -> listOf(value.id)
        else -> emptyList()
    }

private fun canonical(value: Any): String = GsonHolder.gson.toJson(stableValue(value))

private fun stableValue(value: Any?, depth: Int = 0): Any? {
    if (value == null || depth > 5) return value?.toString()
    return when (value) {
        is String,
        is Number,
        is Boolean,
        is Char -> value
        is IntArray -> value.toList()
        is ShortArray -> value.toList()
        is LongArray -> value.toList()
        is ByteArray -> value.toList()
        is BooleanArray -> value.toList()
        is Array<*> -> value.map { stableValue(it, depth + 1) }
        is Iterable<*> -> value.map { stableValue(it, depth + 1) }
        is Map<*, *> ->
            value.entries
                .sortedBy { it.key.toString() }
                .associate { it.key.toString() to stableValue(it.value, depth + 1) }
        is Enum<*> -> value.name
        else -> reflectedFields(value).toSortedMap().mapValues { stableValue(it.value, depth + 1) }
    }
}

private fun reflectedFields(value: Any): Map<String, Any?> {
    val fields = linkedMapOf<String, Any?>()
    var type: Class<*>? = value.javaClass
    while (type != null && type != Any::class.java) {
        for (field in type.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
            val extracted =
                runCatching {
                        field.trySetAccessible()
                        field.get(value)
                    }
                    .getOrNull()
            fields.putIfAbsent(field.name, extracted)
        }
        type = type.superclass
    }
    return fields
}

private fun definitionName(value: Any, type: String, id: Int): String {
    val reflected =
        runCatching { value.javaClass.getMethod("getName").invoke(value) as? String }.getOrNull()
    return reflected?.takeIf(String::isNotBlank) ?: "${type.lowercase()}_$id"
}

private fun isTobName(name: String): Boolean {
    val value = normalize(name)
    return TOB_NAME_MARKERS.any(value::contains) || value in TOB_EXACT_NAMES
}

private fun normalize(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

private val TOB_NAME_MARKERS =
    listOf(
        "maiden_of_sugadinti",
        "pestilent_bloat",
        "nylocas",
        "sotetseg",
        "xarpus",
        "verzik",
        "theatre_of_blood",
        "theatre",
        "dawnbringer",
        "justiciar",
        "ghrazi_rapier",
        "sanguinesti",
        "avernic_defender_hilt",
        "scythe_of_vitur",
        "vial_of_blood",
        "lil_zik",
        "blood_spawn",
        "matomenos",
        "exhumed",
    )

private val TOB_EXACT_NAMES =
    setOf(
        "the_maiden_of_sugadinti",
        "pestilent_bloat",
        "nylocas_vasilias",
        "sotetseg",
        "xarpus",
        "verzik_vitur",
        "monumental_chest",
        "theatre_of_blood",
    )

private fun cacheManifestSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val rows =
        Files.list(path).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .filter { it.name.startsWith("main_file_cache.") }
                .sorted()
                .map { file -> "${file.name}:${Files.size(file)}:${sha256(file)}" }
                .toList()
        }
    return digest.digest(rows.joinToString("\n").toByteArray()).toHex()
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

private fun buildDependencyGraph(entries: List<IdMapping>): DependencyGraph {
    val nodes = linkedMapOf<String, GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    for (entry in entries) {
        val root = "${entry.type}:${entry.sourceId}"
        nodes[root] = GraphNode(root, entry.type, entry.sourceId, entry.name)
        for (dependency in entry.dependencies) {
            val child = "${dependency.type}:${dependency.id}"
            nodes.putIfAbsent(child, GraphNode(child, dependency.type, dependency.id, null))
            edges += GraphEdge(root, child, dependency.field)
        }
    }
    return DependencyGraph(1, nodes.values.toList(), edges.distinct())
}

private fun markdown(
    manifest: AuditManifest,
    entries: List<IdMapping>,
    unresolved: List<UnresolvedItem>,
): String = buildString {
    appendLine("# Theatre of Blood donor audit")
    appendLine()
    appendLine("Generated: ${manifest.generatedAt}")
    appendLine()
    appendLine("## Revision evidence")
    appendLine()
    appendLine(
        "The donor server declares build `229.1` and OpenRS2 cache `2048`. OpenRS2 identifies cache 2048 as OSRS live build 229 (2025-02-18). The user-facing revision-231 label is therefore not used as technical evidence."
    )
    appendLine()
    appendLine("## Cache paths")
    appendLine()
    appendLine("- Reason runtime cache: `${manifest.source.path}`")
    appendLine("- Clean stock build 229: `${manifest.stock229.path}`")
    appendLine("- OpenRune revision 240 LIVE: `${manifest.destination.path}`")
    appendLine("- OpenRune server destination remains `.data/cache/SERVER`.")
    appendLine()
    appendLine("## Source code findings")
    appendLine()
    appendLine(
        "The donor has 47 Theatre source files with party, dynamic-room, boss, hazard, death, spectator, supply, and treasure logic. It also contains shared static completion fields and production debug output, so it is used as behavioral evidence and never copied directly into OpenRune."
    )
    appendLine()
    appendLine("## Mapping summary")
    appendLine()
    manifest.classificationCounts.forEach { (classification, count) ->
        appendLine("- $classification: $count")
    }
    appendLine()
    appendLine(
        "No cache archive is approved for modification. Revision-240 native assets are used wherever a verified mapping exists."
    )
    appendLine()
    appendLine("## Unresolved")
    appendLine()
    if (unresolved.isEmpty()) appendLine("No unresolved decoded dependencies.")
    unresolved.take(100).forEach { appendLine("- ${it.key}: ${it.detail}") }
    appendLine()
    appendLine(
        "The complete machine-readable mapping (${entries.size} entries) is in `reason231-to-openrune240-tob-id-map.json`."
    )
}

private fun writeJson(path: Path, value: Any) {
    Files.writeString(path, GsonHolder.gson.toJson(value) + "\n")
}

private object GsonHolder {
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
}

private data class CacheSummary(
    val path: String,
    val revision: Int,
    val cacheManifestSha256: String,
    val definitionCounts: Map<String, Int>,
    val maximumIds: Map<String, Int>,
)

private data class SourceCodeSummary(val javaFiles: Int, val rawReferences: Int)

private data class AuditManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val status: String,
    val source: CacheSummary,
    val stock229: CacheSummary,
    val destination: CacheSummary,
    val sourceCode: SourceCodeSummary,
    val mappingCount: Int,
    val classificationCounts: Map<String, Int>,
    val cacheArchivesChanged: List<String>,
)

private data class AssetDependency(val type: String, val id: Int, val field: String)

private data class IdMapping(
    val sourceId: Int,
    val name: String,
    val type: String,
    val referencingRaidComponents: List<String>,
    val nativeRevision240Match: Int?,
    val destinationId: Int?,
    val classification: String,
    val reasonModifiedFromStock229: Boolean,
    val bytesMustBeCopied: Boolean,
    val dependencies: List<AssetDependency>,
    val missingDependencies: List<AssetDependency>,
    val verificationSource: String,
    val verificationTest: String,
)

private data class IdMapDocument(
    val schemaVersion: Int,
    val sourceRevisionLabel: String,
    val destinationRevision: Int,
    val classifications: List<String>,
    val entries: List<IdMapping>,
)

private data class GraphNode(val key: String, val type: String, val id: Int, val name: String?)

private data class GraphEdge(val from: String, val to: String, val field: String)

private data class DependencyGraph(
    val schemaVersion: Int,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

private data class CacheAllowlist(
    val schemaVersion: Int,
    val policy: String,
    val allowedChanges: List<Any>,
    val decision: String,
)

private data class UnresolvedItem(val key: String, val status: String, val detail: String)

private data class UnresolvedDocument(val schemaVersion: Int, val items: List<UnresolvedItem>)
