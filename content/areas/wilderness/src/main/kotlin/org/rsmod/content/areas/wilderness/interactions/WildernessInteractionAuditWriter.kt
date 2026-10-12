package org.rsmod.content.areas.wilderness.interactions

import jakarta.inject.Inject
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class WildernessInteractionAuditWriter @Inject constructor() {
    public fun write(
        audit: WildernessInteractionAudit,
        outputDirectory: Path = Path.of("docs", "wilderness"),
    ): WildernessInteractionAuditPaths {
        Files.createDirectories(outputDirectory)
        val paths =
            WildernessInteractionAuditPaths(
                markdown = outputDirectory.resolve("wilderness-interaction-audit.md"),
                json = outputDirectory.resolve("wilderness-interactions.json"),
                csv = outputDirectory.resolve("wilderness-interactions.csv"),
            )
        writeAtomic(paths.markdown, markdown(audit))
        writeAtomic(paths.json, json(audit))
        writeAtomic(paths.csv, csv(audit))
        return paths
    }

    private fun markdown(audit: WildernessInteractionAudit): String = buildString {
        val summary = audit.summary
        appendLine("# Wilderness Interaction Audit")
        appendLine()
        appendLine("Generated: ${audit.generatedAt}")
        appendLine()
        appendLine("Scope: ${audit.scopeDescription}")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        appendLine("| Active cache revision | ${summary.activeRevision} |")
        appendLine("| Regions scanned | ${summary.regionsScanned} |")
        appendLine("| Connected areas scanned | ${summary.connectedAreasScanned} |")
        appendLine("| Planes scanned | ${summary.planesScanned.sorted().joinToString()} |")
        appendLine("| Candidate objects | ${summary.candidateObjects} |")
        appendLine("| Route records | ${summary.routeRecords} |")
        appendLine("| Registered routes | ${summary.registeredRoutes} |")
        appendLine("| Discovery-handler passes | ${summary.passingRoutes} |")
        appendLine("| Missing handlers | ${summary.missingHandlers} |")
        appendLine("| Wrong destinations | ${summary.wrongDestinations} |")
        appendLine("| Missing reverse routes | ${summary.missingReverseRoutes} |")
        appendLine("| Intentional one-way routes | ${summary.intentionalOneWayRoutes} |")
        appendLine("| Invalid destinations | ${summary.invalidDestinations} |")
        appendLine("| Missing cache content | ${summary.missingCacheContent} |")
        appendLine("| Multiple handler layers | ${summary.conflictingRegistrations} |")
        appendLine()
        appendLine("## Route records")
        appendLine()
        appendLine(
            "PASS means the active revision-240 object action resolves to a direct, content-group, category, or exact-coordinate handler. Route-table validation and manual-client status are reported separately per row."
        )
        appendLine()
        appendLine(
            "| # | Area | Object | ID | Action | Source | Shape | Handler | Status | Automated | Manual |"
        )
        appendLine("|---:|---|---|---:|---|---|---|---|---|---|---|")
        for (record in audit.records) {
            append("| ${record.auditIndex} ")
            append("| ${record.areaName.md()} ")
            append("| ${record.sourceObjectName.md()} ")
            append("| ${record.sourceObjectId} ")
            append("| ${record.sourceAction.md()} ")
            append("| ${record.sourceX},${record.sourceY},${record.sourcePlane} ")
            append("| ${record.sourceObjectType}/${record.sourceObjectOrientation} ")
            append("| ${record.existingHandlerFile.md()} ")
            append("| ${record.auditStatus} ")
            append("| ${record.automatedTestStatus} ")
            appendLine("| ${record.manualTestStatus} |")
        }
        appendLine()
        appendLine(
            "The JSON and CSV beside this file contain every required route field, including requirements, restrictions, failure behavior, reverse-route data, source evidence, and notes."
        )
    }

    private fun json(audit: WildernessInteractionAudit): String = buildString {
        appendLine("{")
        append("  \"generatedAt\": ").appendJson(audit.generatedAt).appendLine(",")
        append("  \"scopeDescription\": ").appendJson(audit.scopeDescription).appendLine(",")
        appendLine("  \"summary\": {")
        val summaryFields =
            listOf(
                "activeRevision" to audit.summary.activeRevision,
                "regionsScanned" to audit.summary.regionsScanned,
                "connectedAreasScanned" to audit.summary.connectedAreasScanned,
                "planesScanned" to audit.summary.planesScanned.sorted().joinToString(","),
                "candidateObjects" to audit.summary.candidateObjects,
                "routeRecords" to audit.summary.routeRecords,
                "registeredRoutes" to audit.summary.registeredRoutes,
                "passingRoutes" to audit.summary.passingRoutes,
                "missingHandlers" to audit.summary.missingHandlers,
                "wrongDestinations" to audit.summary.wrongDestinations,
                "missingReverseRoutes" to audit.summary.missingReverseRoutes,
                "intentionalOneWayRoutes" to audit.summary.intentionalOneWayRoutes,
                "invalidDestinations" to audit.summary.invalidDestinations,
                "missingCacheContent" to audit.summary.missingCacheContent,
                "conflictingRegistrations" to audit.summary.conflictingRegistrations,
            )
        summaryFields.forEachIndexed { index, (name, value) ->
            append("    ").appendJson(name).append(": ").appendJsonValue(value)
            appendLine(if (index == summaryFields.lastIndex) "" else ",")
        }
        appendLine("  },")
        appendLine("  \"records\": [")
        audit.records.forEachIndexed { recordIndex, record ->
            appendLine("    {")
            val fields = record.fields()
            fields.forEachIndexed { fieldIndex, (name, value) ->
                append("      ").appendJson(name).append(": ").appendJsonValue(value)
                appendLine(if (fieldIndex == fields.lastIndex) "" else ",")
            }
            append("    }")
            appendLine(if (recordIndex == audit.records.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun csv(audit: WildernessInteractionAudit): String = buildString {
        val headers = audit.records.firstOrNull()?.fields()?.map { it.first } ?: emptyList()
        appendLine(headers.joinToString(",") { it.csv() })
        for (record in audit.records) {
            appendLine(
                record.fields().joinToString(",") { (_, value) -> (value?.toString() ?: "").csv() }
            )
        }
    }

    private fun writeAtomic(path: Path, contents: String) {
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, contents)
        try {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun String.csv(): String = "\"${replace("\"", "\"\"")}\""

    private fun String.md(): String = replace("|", "\\|").replace("\n", " ")

    private fun StringBuilder.appendJsonValue(value: Any?): StringBuilder =
        when (value) {
            null -> append("null")
            is Number,
            is Boolean -> append(value)
            else -> appendJson(value.toString())
        }

    private fun StringBuilder.appendJson(value: String): StringBuilder {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else ->
                    if (character.code < 0x20) append("\\u%04x".format(character.code))
                    else append(character)
            }
        }
        return append('"')
    }
}
