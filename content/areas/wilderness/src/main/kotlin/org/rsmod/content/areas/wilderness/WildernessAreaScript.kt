package org.rsmod.content.areas.wilderness

import jakarta.inject.Inject
import java.awt.Color
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInCorporealBeastLair
import org.rsmod.api.area.checker.isInEdgevilleDungeon
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessAreaScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val areaChecker: AreaChecker,
    private val realm: Realm,
) : PluginScript() {

    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")
    private var Player.specialSpecOrb by intVarBit("varbit.pvp_area_client")

    override fun ScriptContext.startup() {
        onPlayerLogin {
            player.syncWildernessState(forceUi = true)
            player.syncForinthrySurgeOnLogin(realm.config.id)
            if (player.hasForinthrySurge()) {
                player.refreshSkullIcon()
                return@onPlayerLogin
            }
            if (player.isSkullEquipLocked()) {
                player.refreshSkullIcon()
                return@onPlayerLogin
            }
            if (player.isSkulled()) {
                player.expireForinthrySurgeIfNeeded()
                val source = player.attr.getOrDefault(SKULL_SOURCE, SkullSource.COMBAT)
                if (source.timerTicks != Int.MAX_VALUE) {
                    player.softTimer("timer.skull_timer", source.timerTicks)
                }
                player.refreshSkullIcon()
            }
        }

        onPlayerSoftTimer("timer.skull_timer") {
            if (player.hasForinthrySurgeMarker()) {
                player.expireForinthrySurgeIfNeeded()
                val seconds = player.forinthrySurgeRemainingSeconds()
                if (seconds > 0) {
                    player.softTimer(
                        "timer.skull_timer",
                        ForinthrySurgeTime.secondsToTicks(seconds),
                    )
                }
                return@onPlayerSoftTimer
            }
            if (player.isSkullEquipLocked()) {
                return@onPlayerSoftTimer
            }
            if (player.isInPvpCombat()) {
                player.softTimer("timer.skull_timer", 10)
                return@onPlayerSoftTimer
            }
            player.expireForinthrySurgeIfNeeded()
            if (player.isSkulled()) {
                player.clearSkull()
            }
        }

        onArea("area.wilderness") { player.syncWildernessState(forceUi = true) }

        onAreaExit("area.wilderness") { player.syncWildernessState() }

        onPlayerCoordsChanged { player.syncWildernessState() }
    }

    private fun Player.syncWildernessState(forceUi: Boolean = false) {
        val inWilderness =
            coords.isInEdgevilleDungeon() ||
                coords.isInCorporealBeastLair() ||
                areaChecker.inArea("area.wilderness", coords)
        if (!inWilderness) {
            if (insideWilderness || forceUi) {
                insideWilderness = false
                setUnAttackable(this)
            }
            return
        }

        val needsUi = forceUi || !insideWilderness
        insideWilderness = true
        if (needsUi && !hasWildernessOptOut()) {
            setAttackable(this)
        }
        refreshWildernessLevelDisplay()
    }

    private fun setAttackable(player: Player) {
        player.setCanPvp(true)
        player.specialSpecOrb = 1
        player.ifOpenOverlay(
            "interface.pvp_icons",
            "component.toplevel_osrs_stretch:pvp_icons",
            eventBus,
        )
    }

    private fun Player.refreshWildernessLevelDisplay() {
        val level = coords.wildernessLevel(areaChecker)
        if (level <= 0) return
        val bracket = WildernessCombatLevelRules.bracket(combatLevel, level)
        val range = bracket.minimumCombatLevel..bracket.maximumCombatLevel
        if (usesServerWildernessLevelHud(coords)) {
            val wasActive = attr.getOrDefault(SERVER_WILDERNESS_LEVEL_HUD, false)
            val lastLevel = attr.getOrDefault(SERVER_WILDERNESS_LEVEL, -1)
            val lastMinimum = attr.getOrDefault(SERVER_WILDERNESS_MINIMUM, -1)
            val lastMaximum = attr.getOrDefault(SERVER_WILDERNESS_MAXIMUM, -1)
            if (
                !shouldRefreshServerWildernessLevelHud(
                    wasActive,
                    lastLevel,
                    lastMinimum,
                    lastMaximum,
                    level,
                    bracket.minimumCombatLevel,
                    bracket.maximumCombatLevel,
                )
            ) {
                return
            }
            attr[SERVER_WILDERNESS_LEVEL_HUD] = true
            attr[SERVER_WILDERNESS_LEVEL] = level
            attr[SERVER_WILDERNESS_MINIMUM] = bracket.minimumCombatLevel
            attr[SERVER_WILDERNESS_MAXIMUM] = bracket.maximumCombatLevel
            // The stock level and combat-range components are rewritten by client scripts. Move
            // both off-screen as well as hiding them so those scripts cannot flash stale text.
            ifSetPosition(
                PVP_ICONS_INTERFACE,
                PVP_COMBAT_RANGE_COMPONENT,
                PVP_CLIENT_COMPONENT_OFFSCREEN_X,
                0,
            )
            ifSetPosition(
                PVP_ICONS_INTERFACE,
                PVP_LEVEL_COMPONENT,
                PVP_CLIENT_COMPONENT_OFFSCREEN_X,
                0,
            )
            ifSetHide(PVP_ICONS_INTERFACE, PVP_COMBAT_RANGE_COMPONENT, true)
            ifSetHide(PVP_ICONS_INTERFACE, PVP_LEVEL_COMPONENT, true)
            // Child 52 is the stock quitter warning and intentionally flashes. Older revisions of
            // this workaround reused it for the level text, which caused the visible flicker.
            ifSetHide(PVP_ICONS_INTERFACE, PVP_LEGACY_QUITTER_WARNING_COMPONENT, true)
            ifSetText(PVP_ICONS_INTERFACE, PVP_LEGACY_QUITTER_WARNING_COMPONENT, "")
            ifSetHide(PVP_ICONS_INTERFACE, PVP_SERVER_LEVEL_COMPONENT, false)
            ifSetHide(PVP_ICONS_INTERFACE, PVP_SERVER_RANGE_COMPONENT, false)
            ifSetPosition(PVP_ICONS_INTERFACE, PVP_SERVER_LEVEL_COMPONENT, PVP_LEVEL_X, PVP_LEVEL_Y)
            ifSetPosition(PVP_ICONS_INTERFACE, PVP_SERVER_RANGE_COMPONENT, PVP_LEVEL_X, PVP_RANGE_Y)
            setColour(PVP_ICONS_INTERFACE, PVP_SERVER_LEVEL_COMPONENT, Color.YELLOW)
            setColour(PVP_ICONS_INTERFACE, PVP_SERVER_RANGE_COMPONENT, Color.YELLOW)
            ifSetText(
                PVP_ICONS_INTERFACE,
                PVP_SERVER_LEVEL_COMPONENT,
                serverWildernessLevelText(level),
            )
            ifSetText(
                PVP_ICONS_INTERFACE,
                PVP_SERVER_RANGE_COMPONENT,
                serverWildernessCombatRangeText(range),
            )
        } else {
            restoreClientWildernessLevelHud(reopenOverlay = true)
            ifSetText(PVP_ICONS_INTERFACE, PVP_LEVEL_COMPONENT, "Level: $level")
            ifSetText(
                PVP_ICONS_INTERFACE,
                PVP_COMBAT_RANGE_COMPONENT,
                "${range.first}-${range.last}",
            )
        }
    }

    private fun setUnAttackable(player: Player) {
        player.restoreClientWildernessLevelHud(reopenOverlay = false)
        player.setCanPvp(false)
        player.specialSpecOrb = 0
        player.ifCloseOverlay("interface.pvp_icons", eventBus)
    }

    private fun Player.restoreClientWildernessLevelHud(reopenOverlay: Boolean) {
        if (!attr.getOrDefault(SERVER_WILDERNESS_LEVEL_HUD, false)) return
        attr[SERVER_WILDERNESS_LEVEL_HUD] = false
        attr.remove(SERVER_WILDERNESS_LEVEL)
        attr.remove(SERVER_WILDERNESS_MINIMUM)
        attr.remove(SERVER_WILDERNESS_MAXIMUM)
        ifCloseOverlay("interface.pvp_icons", eventBus)
        if (reopenOverlay) {
            ifOpenOverlay(
                "interface.pvp_icons",
                "component.toplevel_osrs_stretch:pvp_icons",
                eventBus,
            )
        }
    }

    companion object {
        internal const val PVP_ICONS_INTERFACE: Int = 90
        internal const val PVP_COMBAT_RANGE_COMPONENT: Int = 49
        internal const val PVP_LEVEL_COMPONENT: Int = 50
        internal const val PVP_LEGACY_QUITTER_WARNING_COMPONENT: Int = 52
        // Added by WildernessHudInterfaceBuilder. These children clone the native fonts and layout
        // without client timers, flashing hooks, or stock layout ownership.
        internal const val PVP_SERVER_LEVEL_COMPONENT: Int = 55
        internal const val PVP_SERVER_RANGE_COMPONENT: Int = 56
        internal const val PVP_LEVEL_X: Int = 0
        internal const val PVP_LEVEL_Y: Int = 27
        internal const val PVP_RANGE_Y: Int = 44
        internal const val PVP_CLIENT_COMPONENT_OFFSCREEN_X: Int = -1000
        internal const val MIN_COMBAT_LEVEL: Int = MIN_WILDERNESS_COMBAT_LEVEL
        internal const val MAX_COMBAT_LEVEL: Int = MAX_WILDERNESS_COMBAT_LEVEL

        internal fun usesServerWildernessLevelHud(coords: CoordGrid): Boolean {
            return coords.isInEdgevilleDungeon() || coords.isInCorporealBeastLair()
        }

        internal fun wildernessCombatLevelRange(combatLevel: Int, wildernessLevel: Int): IntRange {
            val bracket = WildernessCombatLevelRules.bracket(combatLevel, wildernessLevel)
            return bracket.minimumCombatLevel..bracket.maximumCombatLevel
        }

        internal fun serverWildernessLevelText(level: Int): String = "Level: $level"

        internal fun serverWildernessCombatRangeText(range: IntRange): String =
            "Combat: ${range.first}-${range.last}"

        internal fun shouldRefreshServerWildernessLevelHud(
            wasActive: Boolean,
            lastLevel: Int,
            lastMinimum: Int,
            lastMaximum: Int,
            level: Int,
            minimum: Int,
            maximum: Int,
        ): Boolean {
            return !wasActive ||
                lastLevel != level ||
                lastMinimum != minimum ||
                lastMaximum != maximum
        }

        private val WILDERNESS_OPT_OUT =
            AttributeKey<Boolean>(persistenceKey = "wilderness_opt_out")
        private val SERVER_WILDERNESS_LEVEL_HUD = AttributeKey<Boolean>()
        private val SERVER_WILDERNESS_LEVEL = AttributeKey<Int>()
        private val SERVER_WILDERNESS_MINIMUM = AttributeKey<Int>()
        private val SERVER_WILDERNESS_MAXIMUM = AttributeKey<Int>()
        private val CAN_PVP = AttributeKey<Boolean>()
        private val DUEL_FLAG = AttributeKey<Boolean>()

        fun Player.hasWildernessOptOut(): Boolean = attr.getOrDefault(WILDERNESS_OPT_OUT, false)

        fun Player.setWildernessOptOut(optOut: Boolean) {
            if (hasWildernessOptOut() == optOut) return
            attr[WILDERNESS_OPT_OUT] = optOut
            refreshPlayerOptions()
        }

        fun Player.canPvp(): Boolean = attr.getOrDefault(CAN_PVP, false) && !hasWildernessOptOut()

        fun Player.isDueling(): Boolean = attr.getOrDefault(DUEL_FLAG, false)

        fun Player.setCanPvp(canPvp: Boolean, duel: Boolean = false) {
            val currentPvp = attr.getOrDefault(CAN_PVP, false)
            val currentDuel = isDueling()
            if (currentPvp == canPvp && currentDuel == duel) return
            attr[CAN_PVP] = canPvp
            attr[DUEL_FLAG] = duel
            refreshPlayerOptions()
        }

        private fun Player.refreshPlayerOptions() {
            when {
                canPvp() ->
                    MiscOutput.setPlayerOp(
                        this,
                        slot = 1,
                        op = if (isDueling()) "Fight" else "Attack",
                        priority = true,
                    )
                isDueling() ->
                    MiscOutput.setPlayerOp(this, slot = 1, op = "Challenge", priority = false)
                else -> {
                    MiscOutput.clearPlayerOp(this, 1, "Attack")
                    MiscOutput.clearPlayerOp(this, 1, "Fight")
                    MiscOutput.clearPlayerOp(this, 1, "Challenge")
                }
            }
        }
    }
}
