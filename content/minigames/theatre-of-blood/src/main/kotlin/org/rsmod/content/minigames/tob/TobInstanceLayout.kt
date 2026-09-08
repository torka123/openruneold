package org.rsmod.content.minigames.tob

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.zone.ZoneKey

internal object TobInstanceLayout {
    fun area(): InstanceArea.Template {
        val template =
            RegionTemplate.createLarge {
                for (room in TobRoom.entries) {
                    for (segment in room.segments) {
                        val sourceRegionX = segment.sourceRegion ushr 8
                        val sourceRegionZ = segment.sourceRegion and 0xFF
                        for (sourceLevel in copiedSourceLevels(room, segment)) {
                            for (x in 0 until 8) for (z in 0 until 8) {
                                this[
                                    segment.destinationZoneX + x,
                                    segment.destinationZoneZ + z,
                                    sourceLevel,
                                ] =
                                    ZoneKey(
                                        sourceRegionX * 8 + x,
                                        sourceRegionZ * 8 + z,
                                        sourceLevel,
                                    )
                            }
                        }
                    }
                }
            }
        return InstanceArea.template(template, local(TobRoom.MAIDEN), TOB_LOBBY)
    }

    fun local(room: TobRoom, boss: Boolean = false): RegionLocal {
        val localX = if (boss) room.bossLocalX else room.entryLocalX
        val localZ = if (boss) room.bossLocalZ else room.entryLocalZ
        val sourceLevel = if (boss) room.gameplaySourceLevel else room.entrySourceLevel
        return local(room, localX, localZ, sourceLevel)
    }

    fun completion(room: TobRoom): RegionLocal =
        local(room, room.completionLocalX, room.completionLocalZ, room.gameplaySourceLevel)

    fun spectator(room: TobRoom): RegionLocal = local(room)

    fun local(room: TobRoom, localX: Int, localZ: Int): RegionLocal =
        local(room, localX, localZ, room.gameplaySourceLevel)

    fun local(room: TobRoom, localX: Int, localZ: Int, sourceLevel: Int): RegionLocal {
        val origin = room.segments.first()
        val destinationTileX = origin.destinationZoneX * 8 + localX
        val destinationTileZ = origin.destinationZoneZ * 8 + localZ
        val segment =
            room.segments.firstOrNull {
                destinationTileX in it.destinationZoneX * 8 until (it.destinationZoneX + 8) * 8 &&
                    destinationTileZ in it.destinationZoneZ * 8 until (it.destinationZoneZ + 8) * 8
            }
                ?: error(
                    "ToB local coordinate is outside room template: " +
                        "room=${room.key}, local=($localX,$localZ)"
                )
        val sourceMapSquareX = segment.sourceRegion ushr 8
        val sourceMapSquareZ = segment.sourceRegion and 0xFF
        val segmentLocalX = destinationTileX - segment.destinationZoneX * 8
        val segmentLocalZ = destinationTileZ - segment.destinationZoneZ * 8
        return RegionLocal(
            level = sourceLevel,
            regionZoneX = sourceMapSquareX,
            regionZoneZ = sourceMapSquareZ,
            localX = segmentLocalX,
            localZ = segmentLocalZ,
        )
    }

    fun copiedSourceLevels(room: TobRoom, segment: TobTemplateSegment): IntRange = 0..1
}
