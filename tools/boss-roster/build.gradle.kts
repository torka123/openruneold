import groovy.json.JsonSlurper
import java.io.DataInputStream
import java.io.FileInputStream

plugins {
    base
}

private data class BossRow(
    val slug: String,
    val name: String,
    val status: String,
    val modulePath: String?,
    val npcForms: List<String>,
)

private fun mergedRows(root: Map<*, *>): List<BossRow> {
    val defaults = root["fieldDefaults"] as Map<*, *>
    val entries = root["bosses"] as List<*>
    return entries.map { raw ->
        val row = defaults + (raw as Map<*, *>)
        BossRow(
            slug = row["slug"] as String,
            name = row["name"] as String,
            status = row["status"] as String,
            modulePath = row["modulePath"] as String?,
            npcForms = (row["npcForms"] as List<*>).map { it as String },
        )
    }
}

private fun readNpcGamevals(file: File): Set<String> {
    DataInputStream(FileInputStream(file).buffered()).use { input ->
        val tableCount = input.readInt()
        val values = HashSet<String>()
        repeat(tableCount) {
            val table = input.readUTF()
            val count = input.readInt()
            repeat(count) {
                val value = input.readUTF()
                if (table == "npc") {
                    values += "npc.${value.substringBeforeLast('=')}"
                }
            }
        }
        return values
    }
}

tasks.register("validateBossRoster") {
    group = "verification"
    description = "Validates live OSRS boss-roster ownership and active-cache NPC references."

    val rootDir = rootProject.projectDir
    val rosterFile = rootDir.resolve("docs/bosses/boss-roster.json")
    val gamevalsFile = rootDir.resolve(".data/gamevals-binary/gamevals.dat")
    inputs.file(rosterFile)
    inputs.file(gamevalsFile)

    doLast {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(rosterFile) as Map<*, *>
        val rows = mergedRows(json)
        val errors = mutableListOf<String>()
        val completeStates = setOf("IMPLEMENTED_UNTESTED", "IMPLEMENTED_TESTED")
        val validStates = setOf(
            "MISSING", "PARTIAL", "INCORRECT", "IMPLEMENTED_UNTESTED",
            "IMPLEMENTED_TESTED", "CACHE_BLOCKED", "MAP_BLOCKED", "ENGINE_BLOCKED",
        )

        rows.groupBy { it.slug }.filterValues { it.size > 1 }.forEach { (slug, _) ->
            errors += "Duplicate stable slug: $slug"
        }
        rows.filter { it.status !in validStates }.forEach {
            errors += "${it.slug}: invalid status '${it.status}'"
        }

        for (row in rows) {
            val modulePath = row.modulePath
            if (modulePath == null) {
                errors += "${row.slug}: target roster entry has no owning module"
                continue
            }
            val module = rootDir.resolve(modulePath)
            if (!module.isDirectory) {
                errors += "${row.slug}: owning module does not exist: $modulePath"
                continue
            }
            if (!module.resolve("build.gradle.kts").isFile) {
                errors += "${row.slug}: owning module has no build.gradle.kts: $modulePath"
            }
            val sources = module.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            if (sources.isEmpty()) {
                errors += "${row.slug}: owning module has no loadable Kotlin source: $modulePath"
            }
            if (row.status in completeStates) {
                val placeholders = sources.filter { source ->
                    val text = source.readText()
                    Regex("\\b(TODO|FIXME|NotImplementedError)\\b").containsMatchIn(text)
                }
                if (placeholders.isNotEmpty()) {
                    errors += "${row.slug}: complete claim contains required placeholders in ${placeholders.joinToString { it.relativeTo(rootDir).path }}"
                }
            }
        }

        rows.filter { it.modulePath != null }
            .groupBy { it.modulePath }
            .filterValues { group -> group.map { it.slug }.distinct().size > 1 && group.map { it.name }.distinct().size == 1 }
            .forEach { (module, group) -> errors += "Duplicate primary boss ownership in $module: ${group.joinToString { it.slug }}" }

        val npcGamevals = readNpcGamevals(gamevalsFile)
        rows.flatMap { row -> row.npcForms.map { row.slug to it } }.forEach { (slug, form) ->
            if (form !in npcGamevals) {
                errors += "$slug: NPC form does not resolve in active gamevals: $form"
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "Boss roster validation failed (${errors.size} issue(s), ${rows.size} targets):\n" +
                    errors.joinToString("\n") { " - $it" }
            )
        }
        logger.lifecycle("Boss roster validation passed: ${rows.size} unique targets.")
    }
}
