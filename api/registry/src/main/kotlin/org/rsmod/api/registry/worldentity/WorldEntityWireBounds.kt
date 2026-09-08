package org.rsmod.api.registry.worldentity

import org.rsmod.game.entity.WorldEntity

internal object WorldEntityWireBounds {
    fun invalidReason(entity: WorldEntity): String? =
        when {
            entity.id !in 0..MAX_ID -> "id=${entity.id}"
            entity.sizeX !in MIN_SIZE..MAX_SIZE -> "sizeX=${entity.sizeX}"
            entity.sizeZ !in MIN_SIZE..MAX_SIZE -> "sizeZ=${entity.sizeZ}"
            entity.southWestZoneX !in 0..MAX_ZONE ->
                "southWestZoneX=${entity.southWestZoneX}"
            entity.southWestZoneZ !in 0..MAX_ZONE ->
                "southWestZoneZ=${entity.southWestZoneZ}"
            entity.minLevel !in 0..WorldEntity.MAX_LEVEL -> "minLevel=${entity.minLevel}"
            entity.maxLevel !in 0..WorldEntity.MAX_LEVEL -> "maxLevel=${entity.maxLevel}"
            entity.minLevel > entity.maxLevel ->
                "levelRange=${entity.minLevel}..${entity.maxLevel}"
            entity.activeLevel !in 0..WorldEntity.MAX_LEVEL ->
                "activeLevel=${entity.activeLevel}"
            entity.projectedLevel !in 0..WorldEntity.MAX_LEVEL ->
                "projectedLevel=${entity.projectedLevel}"
            entity.fineX !in 0..MAX_FINE_COORD -> "fineX=${entity.fineX}"
            entity.fineZ !in 0..MAX_FINE_COORD -> "fineZ=${entity.fineZ}"
            entity.angle !in 0..WorldEntity.MAX_ANGLE -> "angle=${entity.angle}"
            else -> null
        }

    fun isValidClientSlot(slot: Int): Boolean = slot in MIN_CLIENT_SLOT..MAX_CLIENT_SLOT

    private const val MAX_ID = 65_535
    private const val MIN_SIZE = 1
    private const val MAX_SIZE = 12
    private const val MAX_ZONE = 2_047
    private const val MAX_FINE_COORD = 2_097_151
    private const val MIN_CLIENT_SLOT = 1
    private const val MAX_CLIENT_SLOT = 4_095
}
