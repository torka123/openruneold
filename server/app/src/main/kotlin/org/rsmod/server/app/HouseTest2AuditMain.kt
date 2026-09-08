package org.rsmod.server.app

import java.nio.file.Path
import org.rsmod.content.skills.construction.HouseTest2RegistryAuditWriter

/** Loads the production cache, map, and plugin graph before auditing POH interaction routes. */
public fun main() {
    val server = GameServer(skipTypeVerificationOverride = false)
    val injector = server.createInjector()
    server.prepareGame(injector)
    val output = Path.of("data", "reports", "housetest2-audit.txt")
    val written = injector.getInstance(HouseTest2RegistryAuditWriter::class.java).write(output)
    println("HouseTest2 production interaction audit: ${written.toAbsolutePath()}")
}
