package org.rsmod.server.app

import dev.openrune.ServerCacheManager
import dev.openrune.audit.ArmourAudit
import dev.openrune.audit.ArmourAuditWriter
import java.nio.file.Files
import java.nio.file.Path

/** Generates a cache-first raw-ID armour audit without starting network services. */
public fun main(args: Array<String>) {
    val projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val activeRevision =
        Files.readAllLines(projectRoot.resolve("game.yml"))
            .first { it.trimStart().startsWith("revision:") }
            .substringAfter("revision:")
            .trim()
            .substringBefore('.')
            .toInt()
    val output =
        args.firstOrNull { !it.startsWith("--") }?.let(Path::of)
            ?: projectRoot.resolve(Path.of("docs", "audit", "armour", "current"))

    val serverCache = ServerCacheManager.init(activeRevision)
    try {
        val snapshot = ArmourAudit.scan(projectRoot, activeRevision)
        val paths = ArmourAuditWriter.write(snapshot, output)
        println(
            "Armour audit complete: revision=${snapshot.revision}, " +
                "rawIds=${snapshot.summary.scopedRawIds}, matches=${snapshot.summary.matchingRows}, " +
                "mismatches=${snapshot.summary.mismatchRows}, " +
                "manualModelReview=${snapshot.summary.manualModelReviewRows}.",
        )
        println("JSON: ${paths.json.toAbsolutePath()}")
        println("CSV: ${paths.csv.toAbsolutePath()}")
        println("Effects: ${paths.effects.toAbsolutePath()}")
        println("Models: ${paths.models.toAbsolutePath()}")
    } finally {
        serverCache.close()
    }
}
