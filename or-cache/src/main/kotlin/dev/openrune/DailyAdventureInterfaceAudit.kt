package dev.openrune

import dev.openrune.cache.INTERFACES
import dev.openrune.filesystem.Cache
import java.nio.file.Files
import java.nio.file.Paths

/** Read-only collision audit for the reserved Daily Adventure interface ID. */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    val mapping = root.resolve(Paths.get(".data", "gamevals", "interface.rscm"))
    val ownedMapping =
        Files.exists(mapping) &&
            Files.readAllLines(mapping).any {
                it.trim() == "daily_adventure=${DailyAdventureInterfaceIds.Id}"
            }
    println("daily_adventure_mapping_owned=$ownedMapping")
    listOf("SERVER", "LIVE").forEach { name ->
        val path = root.resolve(Paths.get(".data", "cache", name))
        val cache = Cache.load(path)
        try {
            val present = DailyAdventureInterfaceIds.Id in cache.archives(INTERFACES)
            val files =
                if (present) cache.files(INTERFACES, DailyAdventureInterfaceIds.Id).sorted()
                else emptyList()
            println(
                "cache=$name interface=${DailyAdventureInterfaceIds.Id} present=$present files=$files"
            )
        } finally {
            cache.close()
        }
    }
}
