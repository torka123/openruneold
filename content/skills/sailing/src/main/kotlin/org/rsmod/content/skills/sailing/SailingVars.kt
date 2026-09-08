package org.rsmod.content.skills.sailing

import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.aboardPlayerBoat by intVarBit("varbit.sailing_player_is_on_player_boat")

internal var Player.helmLockedIn by intVarBit("varbit.sailing_boat_facility_lockedin")

internal var Player.sailingSidePanelVisible by intVarBit("varbit.sailing_sidepanel_visible")

internal var Player.sailingSidePanelTab by intVarBit("varbit.sailing_sidepanel_tabs")

internal var Player.sailingSailButtonToggled by
    intVarBit("varbit.sailing_sidepanel_sail_button_toggled")

internal var Player.sailingBoatMoveMode by intVarBit("varbit.sailing_sidepanel_boat_move_mode")

internal var Player.sailingHelmStatus by intVarBit("varbit.sailing_sidepanel_helm_status")

internal var Player.sailingPlayerAtHelm by intVarBit("varbit.sailing_sidepanel_player_at_helm")

internal var Player.sailingAmmoNeedsUpdate by
    intVarBit("varbit.sailing_sidepanel_ammo_needs_update")

internal var Player.sailingBoatStatsNeedsUpdate by
    intVarBit("varbit.sailing_sidepanel_boat_stats_needs_update")

internal var Player.sailingSidePanelBoatType by intVarp("varp.sailing_sidepanel_boat_type")

internal var Player.keepSailingBoatWhenLeavingHelm by
    intVarBit("varbit.keep_sailing_boat_when_leaving_helm")

internal var Player.startBoatWhenSettingHeading by
    intVarBit("varbit.sailing_start_boat_when_setting_heading")
