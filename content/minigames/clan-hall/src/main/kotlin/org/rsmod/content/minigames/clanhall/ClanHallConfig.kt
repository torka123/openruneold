package org.rsmod.content.minigames.clanhall

import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal const val CLAN_HALL_INSTANCE_KEY = "clan_hall"

/** Revision-240 placements verified from map square (27, 85), region 6997. */
internal object ClanHallAssets {
    const val SOURCE_MAP_X = 27
    const val SOURCE_MAP_Z = 85
    const val SOURCE_REGION = (SOURCE_MAP_X shl 8) or SOURCE_MAP_Z

    const val HUB_PORTAL = 41724
    const val MEDIEVAL_EXIT = 41617
    const val COFFER = 41626

    /** Revision-240 active balloons transform to the corresponding popped loc at id + 8. */
    val PARTY_BALLOONS = 115..122
    val PARTY_BALLOONS_POPPED = 123..130
    const val PARTY_CHEST_CLOSED = 46248
    const val PARTY_CHEST_OPEN = 46249
    const val PARTY_CHEST_LEVER = 46250

    val HUB_PORTAL_COORDS = CoordGrid(3186, 3471, 0)

    /* Cache placement 41617 is (1759,5488,0); the unobstructed approach is one tile south. */
    val HALL_ENTRY = RegionLocal(0, SOURCE_MAP_X, SOURCE_MAP_Z, 31, 47)

    /* Verified source placements: chest (35,22), lever (36,23). */
    val PARTY_CHEST_LOCAL = RegionLocal(0, SOURCE_MAP_X, SOURCE_MAP_Z, 35, 22)
    val PARTY_LEVER_LOCAL = RegionLocal(0, SOURCE_MAP_X, SOURCE_MAP_Z, 36, 23)
    /* Return to the unobstructed approach immediately south of the hub portal. */
    val HUB_RETURN = CoordGrid(3186, 3470, 0)
}

/**
 * The stock hall has usable content on levels 0 and 1 (the coffer is on level 1), so copying only
 * level 0 silently removes core hall functionality. Keep the authentic map on all four cache
 * levels.
 */
internal val CLAN_HALL_TEMPLATE =
    RegionTemplate.create {
        copyAllLevels(ClanHallAssets.SOURCE_MAP_X shl 3, ClanHallAssets.SOURCE_MAP_Z shl 3) {
            zoneWidth = 8
            zoneLength = 8
        }
    }

internal val CLAN_HALL_AREA =
    InstanceArea.template(
        template = CLAN_HALL_TEMPLATE,
        enterCoord = ClanHallAssets.HALL_ENTRY,
        exitCoord = ClanHallAssets.HUB_RETURN,
    )

internal fun clanHallSpec(capacity: Int) =
    InstanceSettings(
            maxPlayers = capacity,
            destroyWhenEmpty = false,
            teamSize = capacity,
            description = "Clan Hall",
        )
        .withArea(CLAN_HALL_AREA, settingsRowId = 0)
