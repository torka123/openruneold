package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.codec.osrs.ItemDecoder
import dev.openrune.codec.osrs.impl.ItemServerCodec
import dev.openrune.definition.codec.ItemCodec
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path

/** OpenRune allows Arceuus spells without quests; level, rune and equipment checks still apply. */
fun main() {
    GameValProvider.load()
    val root = Path.of(System.getProperty("user.dir"))
    installArceuusSpellAccess(root.resolve(".data/cache/LIVE"), root.resolve(".data/cache/SERVER"))
}

/** Also called by the normal cache builder so quest locks cannot return on a cache rebuild. */
fun installArceuusSpellAccess(live: Path, server: Path) {
    val bookParam = "param.spell_spellbook".asRSCM()
    val questParam = "param.spell_questreq_struct".asRSCM()
    for ((path, client) in listOf(live to true, server to false)) {
        val archive = if (client) 10 else ItemDecoder(240).getArchive(0)
        val edits = linkedMapOf<Int, ByteArray>()
        val cache = Cache.load(path)
        try {
            for (id in cache.files(CONFIGS, archive)) {
                val bytes = requireNotNull(cache.data(CONFIGS, archive, id))
                if (client) {
                    val type = ItemCodec(240).loadData(id, bytes)
                    if (type.params?.get(bookParam) != 3) continue
                    val unlocked = removeClientQuestRequirement(bytes, questParam)
                    if (unlocked.contentEquals(bytes)) continue
                    val params = type.params.orEmpty() + (questParam to -1)
                    edits[id] = unlocked
                    check(ItemCodec(240).loadData(id, edits.getValue(id)).params == params)
                } else {
                    val codec = ItemServerCodec(240)
                    val type = codec.loadData(id, bytes)
                    if (type.paramsRaw?.get(bookParam) != 3 || (type.paramsRaw?.get(questParam) as? Int ?: -1) == -1) continue
                    type.paramsRaw = (type.paramsRaw.orEmpty() + (questParam to -1)).toMutableMap()
                    edits[id] = codec.encodeToBuffer(type)
                    check(codec.loadData(id, edits.getValue(id)).paramsRaw == type.paramsRaw)
                }
                val backup = path.parent.parent.resolve("audit-backups/arceuus-access/${path.fileName}/item-$id.bin")
                Files.createDirectories(backup.parent)
                if (!Files.exists(backup)) Files.write(backup, bytes)
            }
        } finally { cache.close() }
        if (edits.isNotEmpty()) {
            val writable = CacheLibrary(path.toString())
            try {
                edits.forEach { (id, bytes) -> writable.put(CONFIGS, archive, id, bytes) }
                check(writable.index(CONFIGS).update()) { "Could not save Arceuus spell access in $path" }
            } finally { writable.close() }
        }
        println("Arceuus quest access: updated ${edits.size} spell definitions in $path")
    }
}

/**
 * Replace every occurrence in place. Appending another opcode 249 leaves the original quest value
 * in the client's parameter table even though our map decoder reports only the last value.
 * Using the item decoder to advance other opcodes preserves their original bytes exactly.
 */
internal fun removeClientQuestRequirement(bytes: ByteArray, questParam: Int): ByteArray {
    val result = bytes.copyOf()
    val buffer = Unpooled.wrappedBuffer(result)
    val codec = ItemCodec(240)
    val item = codec.createDefinition()
    return try {
        while (true) {
            when (val opcode = buffer.readUnsignedByte().toInt()) {
                0 -> {
                    check(!buffer.isReadable) { "Trailing data after client item terminator" }
                    break
                }
                249 -> repeat(buffer.readUnsignedByte().toInt()) {
                    val string = buffer.readUnsignedByte().toInt() == 1
                    val key = buffer.readUnsignedMedium()
                    if (string) {
                        check(key != questParam) { "Quest requirement must be an integer" }
                        while (buffer.readByte() != 0.toByte()) { }
                    } else {
                        if (key == questParam) buffer.setInt(buffer.readerIndex(), -1)
                        buffer.skipBytes(Int.SIZE_BYTES)
                    }
                }
                else -> with(codec) { item.read(opcode, buffer) }
            }
        }
        result
    } finally { buffer.release() }
}
