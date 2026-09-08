package org.rsmod.content.minigames.toa

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.zone.ZoneKey

internal object ToaInstanceLayout {
    fun area(): InstanceArea.Template {
        val template =
            RegionTemplate.createLarge {
                for (room in TOA_ACTIVE_ROOMS) {
                    val segment = room.segment
                    val sourceRegionX = segment.sourceRegion ushr 8
                    val sourceRegionZ = segment.sourceRegion and 0xFF
                    for (level in 0..segment.sourceMaxLevel) {
                        for (x in 0 until 8) for (z in 0 until 8) {
                            this[
                                segment.destinationZoneX + x,
                                segment.destinationZoneZ + z,
                                level,
                            ] = ZoneKey(sourceRegionX * 8 + x, sourceRegionZ * 8 + z, level)
                        }
                    }
                }
            }
        return InstanceArea.template(template, local(ToaRoom.LOBBY), TOA_ENTRANCE)
    }

    fun local(room: ToaRoom, boss: Boolean = false): RegionLocal {
        val x = if (boss) room.bossLocalX else room.entryLocalX
        val z = if (boss) room.bossLocalZ else room.entryLocalZ
        return roomLocal(room, x, z)
    }

    fun fightLocal(room: ToaRoom): RegionLocal = roomLocal(room, room.fightLocalX, room.fightLocalZ)

    fun spiritLocal(room: ToaRoom): RegionLocal =
        roomLocal(room, room.spiritLocalX, room.spiritLocalZ)

    fun rewardEntryLocal(): RegionLocal =
        roomLocal(
            ToaRoom.TUMEKEN_WARDEN,
            WardenRewardTransition.LIGHT_LOCAL_X,
            WardenRewardTransition.LIGHT_LOCAL_Z,
        )

    fun roomLocal(room: ToaRoom, x: Int, z: Int): RegionLocal {
        require(x in 0 until 64 && z in 0 until 64) {
            "ToA room-local coordinate is outside its source map square: room=$room, local=($x,$z)"
        }
        val sourceRegionX = room.sourceRegion ushr 8
        val sourceRegionZ = room.sourceRegion and 0xFF
        return RegionLocal(
            level = room.entryLevel,
            regionZoneX = sourceRegionX,
            regionZoneZ = sourceRegionZ,
            localX = x,
            localZ = z,
        )
    }
}
