package dev.openrune

import dev.openrune.filesystem.Cache
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Read-only evidence: native mappings, client models, current animation families and buff scripts. */
class ArceuusNativeAssetsAuditTest {
    @Test
    fun `native Arceuus assets match the current client families`() {
        val native = GameValProvider().apply {
            load(File(".data/gamevals-binary/gamevals.dat"), File(".data/gamevals-binary/gamevals_columns.dat"))
        }.mappings
        ServerCacheManager.init(240).close()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val matches = native.values.flatMap { it.entries }.filter {
                val key = it.key
                key.contains("thrall") || key.contains("thrawl") || key.contains("resurrect_") ||
                    key.contains("mark_of_darkness") || key.contains("ward_of_arceuus") ||
                    key.contains("corruption") || key.contains("shadow_veil") ||
                    key.contains("arceuus_death_charge") || key.contains("arceuus_resurrection") ||
                    key.contains("vile_vigour")
            }.sortedBy { it.key }
            for ((key, id) in matches) {
                println("ARCEUUS_NATIVE_MAPPING $key=$id")
                if (key.startsWith("seq.")) {
                    val seq = requireNotNull(ServerCacheManager.getAnim(id)) { key }
                    println("ARCEUUS_NATIVE_SEQUENCE $key=$id ticks=${seq.tickDuration} frames=${seq.totalDelay}")
                    assertTrue(requireNotNull(live.data(2, 12, id)).isNotEmpty(), key)
                }
                if (key.startsWith("clientscript.")) {
                    val bytes = requireNotNull(live.data(12, id)) { key }
                    println("ARCEUUS_NATIVE_SCRIPT $key=$id instructions=${decode(bytes)}")
                }
                if (key.startsWith("varbit.")) {
                    println("ARCEUUS_NATIVE_VARBIT $key=$id type=${ServerCacheManager.getVarbit(id)}")
                }
            }
            for (id in 10878..10886) {
                val npc = requireNotNull(ServerCacheManager.getNpc(id))
                val codec = NPCCodec(240)
                val pending = ArrayDeque<Int>().apply { add(id) }
                val seen = mutableSetOf<Int>()
                var renderedVariants = 0
                while (pending.isNotEmpty()) {
                    val variantId = pending.removeFirst()
                    if (variantId < 0 || !seen.add(variantId)) continue
                    val raw = requireNotNull(live.data(2, 9, variantId))
                    val client = codec.loadData(variantId, raw)
                    val models = client.models.orEmpty()
                    val variants = (client.transforms.orEmpty() + client.multiDefault).filter { it >= 0 }.distinct()
                    if (models.isNotEmpty()) {
                        renderedVariants++
                        for (model in models) assertTrue(requireNotNull(live.data(7, model)).isNotEmpty(), "NPC $id variant $variantId model $model")
                    } else {
                        assertTrue(variants.isNotEmpty(), "NPC $id variant $variantId has neither models nor transforms")
                    }
                    println("ARCEUUS_NATIVE_NPC id=$id variant=$variantId name=${client.name} stand=${client.standAnim} walk=${client.walkAnim} models=$models varbit=${client.multiVarBit} varp=${client.multiVarp} default=${client.multiDefault} variants=$variants serverStand=${npc.standAnim} serverWalk=${npc.walkAnim}")
                    pending.addAll(variants)
                }
                assertTrue(renderedVariants > 0, "NPC $id resolves to at least one modeled variant")
            }
            for (id in live.archives(12)) {
                val data = live.data(12, id) ?: continue
                val instructions = runCatching { decode(data) }.getOrNull() ?: continue
                if (instructions.any { it in setOf("43:516", "43:521", "43:522", "43:982") }) {
                    println("ARCEUUS_NATIVE_BUFF_WRITER script=$id instructions=$instructions")
                }
            }
            println("ARCEUUS_NATIVE_HITMARKS ${native["hitmark"]}")
        } finally { live.close() }
    }

    private fun decode(data: ByteArray): List<String> {
        val tail = ByteBuffer.wrap(data, data.size - 2, 2).short.toInt() and 65535
        val end = data.size - 2 - tail - 16
        val buffer = ByteBuffer.wrap(data)
        while (buffer.get() != 0.toByte()) { }
        return buildList {
            while (buffer.position() < end) {
                val op = buffer.short.toInt() and 65535
                val arg = if (op == 3) {
                    buildString { while (true) { val c = buffer.get().toInt() and 255; if (c == 0) break; append(c.toChar()) } }
                } else if (op in setOf(21, 38, 39) || op >= 100) {
                    (buffer.get().toInt() and 255).toString()
                } else buffer.int.toString()
                add("$op:$arg")
            }
        }
    }
}
