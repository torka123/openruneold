package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.codec.osrs.NpcDecoder
import dev.openrune.codec.osrs.impl.NpcServerCodec
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.toml.util.InternalAPI
import dev.openrune.types.NpcServerType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.typeOf

/** Applies the canonical Cerberus parameter overlays without regenerating source or other NPCs. */
@OptIn(InternalAPI::class)
fun main() {
    GameValProvider.load()
    val ids = setOf("npc.cerberus_attacking", "npc.cerberus_sitting", "npc.cerberus_resetting")
        .map { it.asRSCM() }.toSet()
    val mapper = tomlMapper { rsconfig { enableConstantProvider() } }
    val overlays = mapper.decodeRuneScapeBlocks(Path.of(".data/raw-cache/server/npcs.toml"))
        .filter { it.name == "npc" }
        .map { mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties) as NpcServerType }
        .filter { it.id in ids }
    check(overlays.map { it.id }.toSet() == ids && overlays.size == ids.size)
    val cachePath = Path.of(".data/cache/SERVER")
    val cache = Cache.load(cachePath)
    val encoded = mutableMapOf<Int, ByteArray>()
    val archive = NpcDecoder(240).getArchive(0)
    try {
        val decoder = NpcDecoder(240)
        val npcs = mutableMapOf<Int, NpcServerType>()
        decoder.load(cache, npcs)
        val codec = NpcServerCodec(240)
        val backup = Path.of("logs/cerberus-audit-20260907/cache-before")
        Files.createDirectories(backup)
        for (overlay in overlays) {
            val current = requireNotNull(npcs[overlay.id])
            val original = requireNotNull(cache.data(CONFIGS, archive, overlay.id))
            val originalPath = backup.resolve("npc-${overlay.id}.bin")
            if (!Files.exists(originalPath)) Files.write(originalPath, original)
            current.paramsRaw = current.paramsRaw.orEmpty().toMutableMap().apply {
                putAll(requireNotNull(overlay.paramsRaw))
            }
            encoded[current.id] = codec.encodeToBuffer(current)
        }
    } finally {
        cache.close()
    }
    val writable = CacheLibrary(cachePath.toString())
    try {
        encoded.forEach { (id, bytes) -> writable.put(CONFIGS, archive, id, bytes) }
        check(writable.index(CONFIGS).update()) { "Cerberus SERVER cache update failed" }
    } finally {
        writable.close()
    }
    println("Applied canonical Cerberus parameters to NPCs ${encoded.keys.sorted()} in $cachePath")
}
