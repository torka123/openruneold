package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.api.combat.commons.magic.HomeTeleportCooldown
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** The shared, interruptible home spell sequence and persistent recharge timer. */
internal object HomeTeleport {
    const val TIMER = HomeTeleportCooldown.TIMER
    const val RECHARGE_MINUTES = 30
    const val CAST_TICKS = 22
    private const val COMBAT_BLOCK_TICKS = 17

    fun remainingMinutes(player: Player, nowMinute: Int): Int {
        val lastCast = player.vars[TIMER]
        return if (lastCast <= 0) 0 else (lastCast + RECHARGE_MINUTES - nowMinute).coerceAtLeast(0)
    }

    suspend fun ProtectedAccess.castHome(
        destination: CoordGrid,
        canTeleport: () -> Boolean,
        nowMinute: () -> Int = { (System.currentTimeMillis() / 60_000L).toInt() },
        sequence: suspend ProtectedAccess.() -> Boolean = { castResting(destination, canTeleport) },
    ) {
        val recharge = HomeTeleportCooldown.requiresRecharge(player)
        HomeTeleportCooldown.syncClient(player)
        val remaining = if (recharge) remainingMinutes(player, nowMinute()) else 0
        if (remaining > 0) {
            mes("You need to wait $remaining more minutes before you can use your home teleport.")
            return
        }
        if (recentCombat(player)) {
            mes("You can't use this teleport while in combat.")
            return
        }
        if (!canTeleport()) return
        if (sequence() && recharge) {
            VarPlayerIntMapSetter.set(player, TIMER, nowMinute())
        }
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
            for (tick in 0 until CAST_TICKS) {
                when (tick) {
                    0 -> { anim("seq.aide_drawing_chalk_circle"); spotanim("spotanim.aide_chalk_circle") }
                    4 -> { anim("seq.aide_sitting_down_crosslegged"); spotanim("spotanim.aide_chalk_circle_static") }
                    7 -> { anim("seq.aide_player_getting_book"); spotanim("spotanim.aide_player_book_get") }
                    10 -> { anim("seq.aide_reciting_incantation"); spotanim("spotanim.aide_player_book_portal") }
                    17 -> { anim("seq.aide_player_teleporting"); spotanim("spotanim.aide_player_teleport") }
                }
                // Unlike delay(), this does not lock movement or new actions while channeling.
                val cycle = mapClock
                coroutine.pause { mapClock > cycle }
                if (recentCombat(player) || coords != origin || player.routeDestination.isNotEmpty()) return false
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
