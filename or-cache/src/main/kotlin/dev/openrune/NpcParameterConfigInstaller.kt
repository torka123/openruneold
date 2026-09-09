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

/**
 * Explicit selected-NPC parameter/occupancy maintenance, never a startup or source-generation task.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Supply the canonical npc.* names to update" }
    require(args.all { it.startsWith("npc.") }) { "Expected named NPC identifiers" }
    applyNpcParameterOverlays(args.toSet(), Path.of("logs/npc-parameter-backups"))
}

/**
 * Shared by scoped installers; tracked TOML supplies parameters and explicitly set occupancy only.
 */
@OptIn(InternalAPI::class)
internal fun applyNpcParameterOverlays(names: Set<String>, backup: Path) {
    require(names.isNotEmpty())
    GameValProvider.load()
    val ids = names.map { it.asRSCM() }.toSet()
    val mapper = tomlMapper { rsconfig { enableConstantProvider() } }
    val blocks =
        mapper.decodeRuneScapeBlocks(Path.of(".data/raw-cache/server/npcs.toml")).filter {
            it.name == "npc"
        }
    val explicitBlockWalk =
        blocks
            .filter { "blockWalk" in it.map.properties }
            .map {
                (mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                        as NpcServerType)
                    .id
            }
            .toSet()
    val explicitMaxRange =
        blocks
            .filter { "maxRange" in it.map.properties }
            .map {
                (mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                        as NpcServerType)
                    .id
            }
            .toSet()
    val overlays =
        blocks
            .map {
                mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                    as NpcServerType
            }
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
        Files.createDirectories(backup)
        for (overlay in overlays) {
            val current = requireNotNull(npcs[overlay.id])
            val original = requireNotNull(cache.data(CONFIGS, archive, overlay.id))
            val hash =
                java.security.MessageDigest.getInstance("SHA-256").digest(original).joinToString(
                    ""
                ) {
                    "%02x".format(it)
                }
            val originalPath = backup.resolve("npc-${overlay.id}-$hash.bin")
            if (!Files.exists(originalPath)) Files.write(originalPath, original)
            current.paramsRaw =
                current.paramsRaw.orEmpty().toMutableMap().apply {
                    putAll(requireNotNull(overlay.paramsRaw))
                }
            // Apply only explicitly supplied movement occupancy; never copy decoder defaults
            // over other inherited fields. Royal elementals share tiles but still obey scenery.
            if (current.id in explicitBlockWalk) current.blockWalk = overlay.blockWalk
            if (current.id in explicitMaxRange) current.maxRange = overlay.maxRange
            encoded[current.id] = codec.encodeToBuffer(current)
        }
    } finally {
        cache.close()
    }
    val writable = CacheLibrary(cachePath.toString())
    try {
        encoded.forEach { (id, bytes) -> writable.put(CONFIGS, archive, id, bytes) }
        check(writable.index(CONFIGS).update()) { "Selected NPC SERVER cache update failed" }
    } finally {
        writable.close()
    }
    println(
        "Applied canonical parameters/explicit occupancy to NPCs ${encoded.keys.sorted()} in $cachePath (revision 240)"
    )
}
