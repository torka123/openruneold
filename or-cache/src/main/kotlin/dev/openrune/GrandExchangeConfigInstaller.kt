package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.cache.DBROW
import dev.openrune.cache.DBTABLEINDEX
import dev.openrune.cache.MAPS
import dev.openrune.cache.tools.tasks.impl.DBTableIndexPacker
import dev.openrune.codec.osrs.NpcDecoder
import dev.openrune.codec.osrs.impl.NpcServerCodec
import dev.openrune.definition.codec.DBRowCodec
import dev.openrune.definition.dbtables.DBRow
import dev.openrune.definition.type.DBRowType
import dev.openrune.impl.Music
import dev.openrune.map.area.MapAreaDecoder
import dev.openrune.map.area.MapAreaDefinition
import dev.openrune.map.area.MapAreaEncoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.toml.decode
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.toml.util.InternalAPI
import dev.openrune.types.NpcServerType
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.reflect.typeOf
import org.rsmod.game.area.polygon.PolygonMapSquareBuilder
import org.rsmod.game.area.util.PolygonMapSquareClipper
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Explicit maintenance only: preserve the existing cache and update just the imported GE assets.
 */
@OptIn(InternalAPI::class)
fun main() {
    val pending = linkedMapOf<GeCacheFile, ByteArray>()
    val cache = ServerCacheManager.init(240)
    try {
        val template = Music.musicModern()
        val geRow = template.rows.single { it.rscmName == "music_modern_grand_exchange" }
        val existing = ServerCacheManager.getRows()[geRow.rowId]
        check(existing == null || existing.tableId == template.tableId) { "GE music row collision" }
        val row =
            DBRowType(geRow.rowId, geRow.rscmName).apply {
                tableId = template.tableId
                ensureColumnStorage(template.columns.keys.max() + 1)
                for ((column, values) in geRow.columns) {
                    field5306!![column] =
                        template.columns.getValue(column).types.map { it.id }.toIntArray()
                    columnTypes!![column] = Array(values.size) { values[it] }
                }
            }
        pending[GeCacheFile(CONFIGS, DBROW, geRow.rowId)] = geEncode {
            with(DBRowCodec()) { encode(row) }
        }
        // Preserve every existing music-area row in this table's master and column indexes.
        val rows =
            ServerCacheManager.getRowsForTable(template.tableId)
                .filter { it.id != geRow.rowId }
                .map { old ->
                    DBRow(
                        old.id,
                        old.rscmName,
                        old.definedColumns().mapValues { requireNotNull(it.value.values) },
                    )
                }
        val indexed = template.copy(rows = rows + geRow)
        pending[GeCacheFile(DBTABLEINDEX, template.tableId, 0)] =
            DBTableIndexPacker.encodeMasterRowIndex(indexed)
        for (column in template.columns.keys) {
            pending[GeCacheFile(DBTABLEINDEX, template.tableId, column + 1)] =
                DBTableIndexPacker.encodeColumnIndex(indexed, column)
                    ?: DBTableIndexPacker.emptyIndexPayload()
        }

        val areaFile =
            tomlMapper {}
                .decode<GeAreaFile>(Path.of(".data/raw-cache/map/area/grand_exchange.toml"))
        val area = areaFile.area.single()
        check(area.area_id == "area.grand_exchange")
        val builders = mutableMapOf<MapSquareKey, PolygonMapSquareBuilder>()
        for (polygon in area.polygons) {
            val vertices = polygon.vertices.map { CoordGrid(it[0], it[1]) }
            for ((square, clipped) in PolygonMapSquareClipper.closeAndClip(vertices)) {
                builders
                    .getOrPut(square) { PolygonMapSquareBuilder() }
                    .polygon(area.area_id.asRSCM().toShort(), area.levels.toSet()) {
                        for (point in clipped) vertex(point.x % 64, point.z % 64)
                    }
            }
        }
        for ((square, builder) in builders) {
            val key = GeCacheFile(MAPS, (square.x shl 8) or square.z, 7)
            val ge = MapAreaDefinition.from(builder.build(), emptyMap())
            val old = cache.data(key.index, key.archive, key.file)
            val before = old?.let { MapAreaDecoder.decode(InlineByteBuf(it)) }
            val combined = if (before == null) ge else MapAreaDefinition.merge(ge, before)
            MapAreaEncoder.validateAreaLimits(square, combined)
            // Fastutil iteration order can differ after a merge; equivalent areas need no write.
            if (combined == before) continue
            pending[key] = geEncode { MapAreaEncoder.encode(combined, this) }
        }

        val names =
            setOf(
                "npc.ge_clerk_1",
                "npc.ge_clerk_2",
                "npc.ge_clerk_3",
                "npc.ge_clerk_4",
                "npc.ge_boss",
                "npc.ge_expert_ores",
                "npc.ge_expert_herbs",
                "npc.ge_expert_runes",
                "npc.ge_expert_logs",
                "npc.ge_expert_combat",
                "npc.banker1_west",
                "npc.banker1_east",
                "npc.banker2_east",
                "npc.deadman_banker_grey_west",
                "npc.bond_james_visible",
                "npc.clan_clerk",
            )
        val ids = names.map { it.asRSCM() }.toSet()
        val mapper = tomlMapper { rsconfig { enableConstantProvider() } }
        val overlays =
            mapper
                .decodeRuneScapeBlocks(Path.of(".data/raw-cache/server/npcs.toml"))
                .filter { it.name == "npc" }
                .map {
                    mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                        as NpcServerType
                }
                .filter { it.id in ids }
        check(overlays.map { it.id }.toSet() == ids && overlays.size == ids.size)
        val codec = NpcServerCodec(240)
        for (overlay in overlays) {
            val npc = requireNotNull(ServerCacheManager.getNpc(overlay.id))
            npc.moveRestrict = overlay.moveRestrict
            npc.wanderRange = overlay.wanderRange
            pending[GeCacheFile(CONFIGS, NpcDecoder(240).getArchive(0), npc.id)] =
                codec.encodeToBuffer(npc)
        }

        val backup = Path.of("logs/ge-config-backups")
        Files.createDirectories(backup)
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (key, bytes) = iterator.next()
            val old = cache.data(key.index, key.archive, key.file)
            if (old != null && old.contentEquals(bytes)) {
                iterator.remove()
                continue
            }
            val hash =
                if (old == null) "absent"
                else
                    MessageDigest.getInstance("SHA-256").digest(old).joinToString("") {
                        "%02x".format(it)
                    }
            val original = backup.resolve("${key.index}-${key.archive}-${key.file}-$hash.bin")
            if (!Files.exists(original)) Files.write(original, old ?: byteArrayOf())
        }
    } finally {
        cache.close()
    }
    if (pending.isEmpty()) {
        println("GE configuration already installed; SERVER unchanged.")
        return
    }
    val writable = CacheLibrary(".data/cache/SERVER")
    try {
        for ((key, bytes) in pending) writable.put(key.index, key.archive, key.file, bytes)
        for (index in pending.keys.map { it.index }.distinct()) check(
            writable.index(index).update()
        )
    } finally {
        writable.close()
    }
    println(
        "Installed ${pending.size} GE definition/map/index files in SERVER only; revision 240 and LIVE unchanged."
    )
}

private data class GeCacheFile(val index: Int, val archive: Int, val file: Int)

private data class GeAreaFile(val area: List<GeArea>)

private data class GeArea(
    val name: String,
    val area_id: String,
    val levels: List<Int>,
    val polygons: List<GePolygon>,
)

private data class GePolygon(val vertices: List<List<Int>>)

private fun geEncode(block: ByteBuf.() -> Unit): ByteArray {
    val buffer = Unpooled.buffer()
    try {
        buffer.block()
        return ByteArray(buffer.writerIndex()).also { buffer.getBytes(0, it) }
    } finally {
        buffer.release()
    }
}
