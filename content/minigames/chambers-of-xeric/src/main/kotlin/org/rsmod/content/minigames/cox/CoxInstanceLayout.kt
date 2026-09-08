package org.rsmod.content.minigames.cox

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.zone.ZoneKey

/** Fixed map assembled by the Reason donor's `CustomXericRaid` rework. */
internal object CoxInstanceLayout {
    private val chunks = buildList {
        fun place(
            destinationX: Int,
            destinationZ: Int,
            sourceX: Int,
            sourceZ: Int,
            sourceLevel: Int = 0,
            rotation: Int = 0,
        ) {
            add(TemplateChunk(destinationX, destinationZ, sourceX, sourceZ, sourceLevel, rotation))
        }

        // Floor one, donor south-west region.
        for (x in 0..2) for (z in 0..7) place(x, z, 412 + x, 648 + z)
        for (x in 0..3) for (z in 0..3) {
            place(4 + x, 7 - z, 411 - x, 644 + z, rotation = 2)
        }

        // Floor one, donor north-west region (64 tiles north of floor-one SW).
        val f1North = CoxDonorArea.FLOOR_ONE_NW.baseZoneZ
        for (x in 0..3) for (z in 0..3) {
            place(x, f1North + z, 412 + x, 664 + z, sourceLevel = 1)
            place(x, f1North + 4 + z, 416 + x, 668 + z, sourceLevel = 1)
            place(4 + x, f1North + 7 - z, 408 + x, 655 - z, sourceLevel = 1)
            place(4 + x, f1North + 3 - z, 412 + x, 667 - z)
        }

        // Floor two, donor south-west region.
        val f2West = CoxDonorArea.FLOOR_TWO_SW.baseZoneX
        for (x in 0..2) for (z in 0..2) place(f2West + x, z, 412 + x, 713 + z)
        for (x in 0..3) for (z in 0..3) {
            place(f2West + x, 3 + z, 416 + x, 652 + z, sourceLevel = 1)
            place(f2West + 4 + z, 3 + x, 415 - x, 656 + z, sourceLevel = 1, rotation = 1)
        }
        for (x in 0..2) for (z in 0..3) {
            place(f2West + 4 + z, x, 413 + x, 647 - z, rotation = 3)
        }

        // Floor two, donor south-east region (64 tiles east of floor-two SW).
        val f2East = CoxDonorArea.FLOOR_TWO_SE.baseZoneX
        for (x in 0..3) for (z in 0..2) {
            place(f2East + x, z, 419 - x, 654 - z, rotation = 2)
        }
        for (x in 0..3) for (z in 0..3) {
            place(f2East + z, 3 + x, 419 - x, 656 + z, sourceLevel = 2, rotation = 1)
        }

        // Great Olm, donor map square 12889 (zone origin 400, 712).
        val olmX = CoxDonorArea.OLM.baseZoneX
        for (x in 0..7) for (z in 0..7) place(olmX + x, z, 400 + x, 712 + z)
    }

    fun area(olmOnly: Boolean = false): InstanceArea.Template {
        val template =
            RegionTemplate.createLarge {
                for (chunk in chunks) {
                    val source = ZoneKey(chunk.sourceX, chunk.sourceZ, chunk.sourceLevel)
                    when (chunk.rotation) {
                        0 -> this[chunk.destinationX, chunk.destinationZ, 0] = source
                        1 -> this[chunk.destinationX, chunk.destinationZ, 0] = source.rotate90()
                        2 -> this[chunk.destinationX, chunk.destinationZ, 0] = source.rotate180()
                        3 -> this[chunk.destinationX, chunk.destinationZ, 0] = source.rotate270()
                        else -> error("Invalid CoX donor chunk rotation: ${chunk.rotation}")
                    }
                }
            }
        val entry =
            if (olmOnly) local(CoxDonorArea.OLM, 33, 24) else local(CoxDonorArea.FLOOR_ONE_SW, 3, 4)
        return InstanceArea.template(template, entry, COX_LOBBY)
    }

    fun local(area: CoxDonorArea, x: Int, z: Int): RegionLocal {
        require(x in 0 until 64 && z in 0 until 64) {
            "CoX donor coordinate outside region: area=$area local=($x,$z)"
        }
        val destinationZoneX = area.baseZoneX + x / 8
        val destinationZoneZ = area.baseZoneZ + z / 8
        val chunk =
            requireNotNull(
                chunks.firstOrNull {
                    it.destinationX == destinationZoneX && it.destinationZ == destinationZoneZ
                }
            ) {
                "CoX donor coordinate is outside the assembled template: " +
                    "area=$area local=($x,$z) zone=($destinationZoneX,$destinationZoneZ)"
            }
        val destinationLocalX = x % 8
        val destinationLocalZ = z % 8
        val (sourceLocalX, sourceLocalZ) =
            when (chunk.rotation) {
                0 -> destinationLocalX to destinationLocalZ
                1 -> (7 - destinationLocalZ) to destinationLocalX
                2 -> (7 - destinationLocalX) to (7 - destinationLocalZ)
                3 -> destinationLocalZ to (7 - destinationLocalX)
                else -> error("Invalid CoX donor chunk rotation: ${chunk.rotation}")
            }
        val sourceTileX = chunk.sourceX * 8 + sourceLocalX
        val sourceTileZ = chunk.sourceZ * 8 + sourceLocalZ
        return RegionLocal(
            level = chunk.sourceLevel,
            regionZoneX = sourceTileX / 64,
            regionZoneZ = sourceTileZ / 64,
            localX = sourceTileX % 64,
            localZ = sourceTileZ % 64,
        )
    }

    /** Keeps the administrator room controls inside the fixed donor map. */
    fun local(room: CoxRoomPlacement): RegionLocal =
        when (room.room) {
            CoxRoom.START -> local(CoxDonorArea.FLOOR_ONE_SW, 3, 4)
            CoxRoom.MUTTADILES -> local(CoxDonorArea.FLOOR_ONE_NW, 13, 11)
            CoxRoom.TIGHTROPE -> local(CoxDonorArea.FLOOR_ONE_NW, 27, 47)
            CoxRoom.VANGUARDS -> local(CoxDonorArea.FLOOR_ONE_NW, 48, 22)
            CoxRoom.SCAVENGERS -> local(CoxDonorArea.FLOOR_ONE_NW, 39, 45)
            CoxRoom.GUARDIANS -> local(CoxDonorArea.FLOOR_TWO_SE, 22, 38)
            CoxRoom.OLM -> local(CoxDonorArea.OLM, 32, 25)
            CoxRoom.SKELETAL_MYSTICS,
            CoxRoom.SHAMANS,
            CoxRoom.VASA,
            CoxRoom.ICE_DEMON,
            CoxRoom.TEKTON,
            CoxRoom.VESPULA,
            CoxRoom.JEWELLED_CRABS,
            CoxRoom.THIEVING,
            CoxRoom.RESOURCE_FISHING,
            CoxRoom.RESOURCE_HUNTER -> local(CoxDonorArea.FLOOR_TWO_SW, 45, 33)
        }

    private data class TemplateChunk(
        val destinationX: Int,
        val destinationZ: Int,
        val sourceX: Int,
        val sourceZ: Int,
        val sourceLevel: Int,
        val rotation: Int,
    )
}

internal enum class CoxDonorArea(val baseZoneX: Int, val baseZoneZ: Int) {
    FLOOR_ONE_SW(0, 0),
    FLOOR_ONE_NW(0, 8),
    FLOOR_TWO_SW(8, 0),
    FLOOR_TWO_SE(16, 0),
    OLM(24, 0),
}
