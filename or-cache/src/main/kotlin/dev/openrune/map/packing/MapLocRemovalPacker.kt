package dev.openrune.map.packing

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.loc.MapLocListDefinition
import dev.openrune.map.loc.MapLocListEncoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.map.util.toReadableByteArray
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.toml.decode
import io.netty.buffer.PooledByteBufAllocator
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/** Applies exact, source-controlled removals to the static map-location archive. */
public object MapLocRemovalPacker : MapSpawnPackerBase<MapLocListDefinition>() {
    override val resourceDir: Path = Path.of("../.data/raw-cache/map/loc-removals")
    private val logger = InlineLogger()

    public fun apply(cache: Cache) {
        if (!Files.isDirectory(resourceDir)) return
        val removals = loadRemovals()
        if (removals.isEmpty()) return

        val buffer = PooledByteBufAllocator.DEFAULT.buffer()
        try {
            for ((square, definitions) in removals) {
                val group = (square.x shl 8) or (square.z and 0xFF)
                val bytes =
                    cache.data(MAPS, group, STATIC_LOC_FILE)
                        ?: error("Missing static loc archive for map-square $square (group=$group)")
                val decoded = MapLocListDecoder.decode(InlineByteBuf(bytes))
                val remaining = LongArrayList(decoded.spawns.size)
                val matched = IntArray(definitions.size)

                for (packed in decoded.spawns.longIterator()) {
                    val loc = MapLocDefinition(packed)
                    val index = definitions.indexOfFirst { it.matches(loc) }
                    if (index >= 0) {
                        matched[index]++
                    } else {
                        remaining.add(packed)
                    }
                }

                definitions.forEachIndexed { index, removal ->
                    check(matched[index] == 1) {
                        "Expected exactly one static loc for $removal, matched ${matched[index]}"
                    }
                }

                val output = buffer.clear()
                MapLocListEncoder.encode(MapLocListDefinition(remaining), output)
                cache.write(MAPS, group, STATIC_LOC_FILE, output.toReadableByteArray())
                logger.info {
                    "Removed ${definitions.size} source-controlled static loc(s) from $square"
                }
            }
        } finally {
            buffer.release()
        }
    }

    private fun loadRemovals(): Map<MapSquareKey, List<LocRemoval>> =
        listTomlFiles(resourceDir)
            .flatMap { path ->
                toml.decode<TomlLocRemovalFile>(path).remove.map { removal ->
                    val coords = parseCoordGrid(removal.coords)
                    MapSquareKey.from(coords) to
                        LocRemoval(
                            loc = removal.loc.asRSCM(),
                            coords = coords,
                            shape = removal.shape,
                            angle = removal.angle,
                        )
                }
            }
            .groupBy({ it.first }, { it.second })

    override fun decodeTomlSpawnFile(path: Path): List<Pair<MapSquareKey, MapLocListDefinition>> =
        error("MapLocRemovalPacker uses exact removals, not merged spawn definitions")

    override fun mergeDefinitions(
        old: MapLocListDefinition,
        new: MapLocListDefinition,
    ): MapLocListDefinition = error("Static location removals cannot be merged")

    private data class TomlLocRemovalFile(val remove: List<TomlLocRemoval>)

    private data class TomlLocRemoval(
        val loc: String,
        val coords: String,
        val shape: Int,
        val angle: Int,
    )

    private data class LocRemoval(
        val loc: Int,
        val coords: CoordGrid,
        val shape: Int,
        val angle: Int,
    ) {
        fun matches(candidate: MapLocDefinition): Boolean =
            candidate.id == loc &&
                candidate.level == coords.level &&
                candidate.localX == coords.x and 0x3F &&
                candidate.localZ == coords.z and 0x3F &&
                candidate.shape == shape &&
                candidate.angle == angle
    }

    private const val STATIC_LOC_FILE = 1
}
