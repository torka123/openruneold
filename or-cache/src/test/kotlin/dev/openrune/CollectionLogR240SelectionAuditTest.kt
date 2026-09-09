package dev.openrune

import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.definition.codec.EnumCodec
import dev.openrune.definition.codec.ItemCodec
import dev.openrune.definition.codec.StructCodec
import dev.openrune.filesystem.Cache
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Read-only audits of the exact JS5 data used to render every collection, including item variants.
 */
class CollectionLogR240SelectionAuditTest {
    @Test
    fun `all collection sections and item models match the served revision 240 client cache`() {
        val catalog =
            ObjectMapper()
                .readTree(
                    File("api/collection-log/src/main/resources/collection-log/catalog-r240.json")
                )
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val structs = StructCodec()
            val enums = EnumCodec()
            val items = ItemCodec(240)
            val pending = ArrayDeque<Int>()
            var sectionsChecked = 0
            var slotsChecked = 0
            catalog["categories"].forEach { category ->
                val structId = category["structId"].asInt()
                val params =
                    requireNotNull(
                        structs
                            .loadData(structId, requireNotNull(live.data(2, 34, structId)))
                            .params
                    )
                assertEquals(category["sectionEnumId"].asInt(), params[683])
                (685..688).forEach {
                    assertEquals(category["clientParams"][it.toString()].asInt(), params[it])
                }
                val sectionEnumId = category["sectionEnumId"].asInt()
                val sectionEnum =
                    enums.loadData(sectionEnumId, requireNotNull(live.data(2, 8, sectionEnumId)))
                assertEquals(
                    category["sections"].map { it["structId"].asInt() },
                    sectionEnum.values.toSortedMap().values.toList(),
                )
                category["sections"].forEach { section ->
                    val name = section["name"].asText()
                    val sectionId = section["structId"].asInt()
                    val sectionParams =
                        requireNotNull(
                            structs
                                .loadData(sectionId, requireNotNull(live.data(2, 34, sectionId)))
                                .params
                        )
                    val itemEnumId = section["itemEnumId"].asInt()
                    assertEquals(itemEnumId, sectionParams[690], name)
                    val itemEnum =
                        enums.loadData(itemEnumId, requireNotNull(live.data(2, 8, itemEnumId)))
                    val displayIds = section["slots"].map { it["displayItemId"].asInt() }
                    assertEquals(displayIds, itemEnum.values.toSortedMap().values.toList(), name)
                    pending.addAll(displayIds)
                    slotsChecked += displayIds.size
                    sectionsChecked++
                    println(
                        "COLLECTION_LIVE_SECTION $name struct=$sectionId enum=$itemEnumId slots=${displayIds.size} PASS"
                    )
                }
            }
            val visited = mutableSetOf<Int>()
            val models = mutableSetOf<Int>()
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!visited.add(id)) continue
                val item =
                    items.loadData(
                        id,
                        requireNotNull(live.data(2, 10, id)) { "Missing client item $id" },
                    )
                if (item.inventoryModel > 0) {
                    val model = item.inventoryModel
                    if (models.add(model))
                        assertTrue(
                            requireNotNull(live.data(7, model)) { "Missing item $id model $model" }
                                .isNotEmpty()
                        )
                }
                pending.addAll(
                    listOf(
                            item.noteLinkId,
                            item.noteTemplateId,
                            item.unnotedId,
                            item.notedId,
                            item.placeholderLink,
                            item.placeholderTemplate,
                        )
                        .filter { it >= 0 }
                )
                item.countObj
                    .orEmpty()
                    .zip(item.countCo.orEmpty())
                    .filter { it.first > 0 && it.second > 0 }
                    .forEach { pending.add(it.first) }
            }
            assertEquals(124, sectionsChecked)
            assertEquals(1921, slotsChecked)
            println(
                "COLLECTION_LIVE_TOTAL sections=$sectionsChecked slots=$slotsChecked itemDefinitions=${visited.size} inventoryModels=${models.size}"
            )
        } finally {
            live.close()
        }
    }

    @Test
    fun `collection draw and search bytecode accepts the exact emitted argument signatures`() {
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val signatures = mapOf(7797 to 7, 7798 to 7, 2731 to 8, 2732 to 3, 4100 to 4, 2158 to 0)
            for ((id, args) in signatures) {
                val data = requireNotNull(live.data(12, id))
                val tailSize = ByteBuffer.wrap(data, data.size - 2, 2).short.toInt() and 65535
                val footerOffset = data.size - 2 - tailSize - 16
                val footer = ByteBuffer.wrap(data).apply { position(footerOffset) }
                val count = footer.int
                repeat(3) { footer.short }
                assertEquals(args, footer.short.toInt() and 65535, "script $id int args")
                assertEquals(0, footer.short.toInt() and 65535, "script $id string args")
                assertEquals(0, footer.short.toInt() and 65535, "script $id long args")
                val body = ByteBuffer.wrap(data)
                readString(body)
                var index = 0
                while (body.position() < footerOffset) {
                    val opcode = body.short.toInt() and 65535
                    val operand: Any =
                        when {
                            opcode == 3 -> readString(body)
                            opcode >= 100 || opcode in setOf(21, 38, 39) ->
                                body.get().toInt() and 255
                            else -> body.int
                        }
                    // Opcode9 is an integer comparison with a four-byte jump, not a null literal.
                    if (opcode in setOf(6, 7, 8, 9, 10, 31, 32)) {
                        assertTrue(
                            index + 1 + (operand as Int) in 0 until count,
                            "script $id branch $index",
                        )
                    }
                    println(
                        "COLLECTION_SCRIPT id=$id instruction=$index opcode=$opcode operand=$operand"
                    )
                    index++
                }
                assertEquals(footerOffset, body.position(), "script $id footer alignment")
                assertEquals(count, index, "script $id instruction count")
            }
        } finally {
            live.close()
        }
    }

    private fun readString(buffer: ByteBuffer): String = buildString {
        while (true) {
            val next = buffer.get().toInt() and 255
            if (next == 0) break
            append(next.toChar())
        }
    }
}
