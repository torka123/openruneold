package dev.openrune.map.loc

import io.netty.buffer.ByteBuf

/** Encodes the revision-240 delta-compressed static location stream. */
public object MapLocListEncoder {
    public fun encode(definition: MapLocListDefinition, data: ByteBuf) {
        val locs =
            definition.spawns
                .toLongArray()
                .map(::MapLocDefinition)
                .sortedWith(compareBy(MapLocDefinition::id, MapLocDefinition::packedCoord))

        var previousId = -1
        for (idGroup in locs.groupBy(MapLocDefinition::id).toSortedMap()) {
            val id = idGroup.key
            data.writeIncrShortSmart(id - previousId)
            previousId = id

            var previousCoord = 0
            for (loc in idGroup.value.sortedBy(MapLocDefinition::packedCoord)) {
                val coord = loc.packedCoord()
                data.writeShortSmart(coord - previousCoord + 1)
                data.writeByte(loc.packedAttributes())
                previousCoord = coord
            }
            data.writeShortSmart(0)
        }
        data.writeIncrShortSmart(0)
    }

    private fun ByteBuf.writeIncrShortSmart(input: Int) {
        require(input >= 0) { "Incremental short-smart cannot encode a negative value: $input" }
        var value = input
        while (value >= SHORT_SMART_CHUNK) {
            writeShort(SHORT_SMART_TERMINATOR)
            value -= SHORT_SMART_CHUNK
        }
        writeShortSmart(value)
    }

    private fun ByteBuf.writeShortSmart(value: Int) {
        require(value in 0..SHORT_SMART_CHUNK) {
            "Short-smart value outside supported range: $value"
        }
        if (value < SINGLE_BYTE_LIMIT) {
            writeByte(value)
        } else {
            writeShort(value or SHORT_SMART_FLAG)
        }
    }

    private const val SINGLE_BYTE_LIMIT = 128
    private const val SHORT_SMART_FLAG = 0x8000
    private const val SHORT_SMART_CHUNK = 0x7FFF
    private const val SHORT_SMART_TERMINATOR = 0xFFFF
}
