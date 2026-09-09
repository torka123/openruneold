package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.combat.commons.magic.HomeTeleportCooldown
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** OpenRune's fast, cooldown-free Home; the resting sequence remains for minigame teleports. */
internal object HomeTeleport {
    const val TIMER = HomeTeleportCooldown.TIMER
    const val CAST_TICKS = 4
    private const val RESTING_CAST_TICKS = 22
    private const val COMBAT_BLOCK_TICKS = 17

    suspend fun ProtectedAccess.castHome(
        destination: CoordGrid,
        canTeleport: () -> Boolean,
        areaChecker: AreaChecker,
    ) {
        HomeTeleportCooldown.syncClient(player)
        if (!homeAllowed(areaChecker, canTeleport)) return
        player.clearInteractionRoute()
        clearQueue(SpellTeleportScript.TeleportQueue)
        val origin = coords
        var arrived = false
        try {
            // Use the same native visuals/timing as ordinary Standard spellbook teleports.
            actionDelay = mapClock + SpellTeleportScript.TeleportActionDelay
            anim(SpellTeleportScript.TeleportStartAnim)
            spotanim(
                SpellTeleportScript.TeleportSpotanim,
                height = SpellTeleportScript.TeleportSpotanimHeight,
            )
            soundSynth(SpellTeleportScript.TeleportSound)
            delay(CAST_TICKS)
            if (coords != origin || !homeAllowed(areaChecker, canTeleport)) return
            telejump(destination, TeleportType.Standard)
            arrived = coords == destination
            if (arrived) {
                player.resetSpotanim()
                anim(SpellTeleportScript.TeleportEndAnim)
            }
        } finally {
            if (!arrived) {
                player.resetAnim()
                player.resetSpotanim()
            }
        }
    }

    private fun ProtectedAccess.homeAllowed(
        areaChecker: AreaChecker,
        canTeleport: () -> Boolean,
    ): Boolean {
        if (player.isInCombat()) {
            mes("You can't use this teleport while in combat.")
            return false
        }
        if (coords.wildernessLevel(areaChecker) >= 20) {
            mes("You can't use your home teleport at level 20 Wilderness or above.")
            return false
        }
        return canTeleport()
    }

    suspend fun ProtectedAccess.castResting(
        destination: CoordGrid,
        canTeleport: () -> Boolean,
        teleportType: TeleportType = TeleportType.Standard,
    ): Boolean {
        if (recentCombat(player)) {
            mes("You can't use this teleport while in combat.")
            return false
        }
        if (!canTeleport()) return false

        val origin = coords
        player.routeDestination.clear()
        try {
            for (tick in 0 until RESTING_CAST_TICKS) {
                when (tick) {
                    0 -> {
                        anim("seq.aide_drawing_chalk_circle")
                        spotanim("spotanim.aide_chalk_circle")
                    }
                    4 -> {
                        anim("seq.aide_sitting_down_crosslegged")
                        spotanim("spotanim.aide_chalk_circle_static")
                    }
                    7 -> {
                        anim("seq.aide_player_getting_book")
                        spotanim("spotanim.aide_player_book_get")
                    }
                    10 -> {
                        anim("seq.aide_reciting_incantation")
                        spotanim("spotanim.aide_player_book_portal")
                    }
                    17 -> {
                        anim("seq.aide_player_teleporting")
                        spotanim("spotanim.aide_player_teleport")
                    }
                }
                // Unlike delay(), this does not lock movement or new actions while channeling.
                val cycle = mapClock
                coroutine.pause { mapClock > cycle }
                if (
                    recentCombat(player) || coords != origin || player.routeDestination.isNotEmpty()
                )
                    return false
            }
            if (!canTeleport()) return false
            telejump(destination, teleportType)
            return coords == destination
        } finally {
            player.resetAnim()
            player.resetSpotanim()
        }
    }

    private fun recentCombat(player: Player): Boolean {
        val last = maxOf(player.vars["varp.lastcombat"], player.vars["varp.lastcombat_pvp"])
        return player.isInCombat() || last > 0 && player.currentMapClock - last < COMBAT_BLOCK_TICKS
    }
}
