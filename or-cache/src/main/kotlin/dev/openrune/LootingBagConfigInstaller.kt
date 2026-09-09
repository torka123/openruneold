package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.codec.osrs.InventoryDecoder
import dev.openrune.codec.osrs.impl.InventoryServerCodec
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InvScope
import dev.openrune.types.InventoryServerType
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Explicit, idempotent maintenance; never invoked by startup or a normal build. */
fun main() {
    GameValProvider.load()
    val path = Path.of(".data/cache/SERVER")
    val id = "inv.looting_bag".asRSCM()
    val archive = InventoryDecoder().getArchive(0)
    val codec = InventoryServerCodec()
    val cache = Cache.load(path)
    val original =
        try {
            requireNotNull(cache.data(CONFIGS, archive, id))
        } finally {
            cache.close()
        }
    val before = codec.loadData(id, original)
    require(before.size == 28 && before.scope == InvScope.Perm)
    if (!before.protect) {
        println("Looting bag $id already permits native modal transactions; SERVER unchanged.")
        return
    }
    val hash =
        MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
    val backup = Path.of("logs/pouches-cache-backups/inv-$id-$hash.bin")
    Files.createDirectories(backup.parent)
    if (!Files.exists(backup)) Files.write(backup, original)
    val after = before.copy(flags = before.flags and InventoryServerType.PROTECT_FLAG.inv())
    val encoded = codec.encodeToBuffer(after)
    check(codec.loadData(id, encoded) == after)
    val writable = CacheLibrary(path.toString())
    try {
        writable.put(CONFIGS, archive, id, encoded)
        check(writable.index(CONFIGS).update())
    } finally {
        writable.close()
    }
    println(
        "Updated only SERVER inventory $id protect=true -> false; backup=$backup; revision/path unchanged."
    )
}
