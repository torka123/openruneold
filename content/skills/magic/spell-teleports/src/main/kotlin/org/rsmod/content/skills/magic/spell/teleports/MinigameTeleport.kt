package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.magic.spell.teleports.HomeTeleport.castResting
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Used by the sole spell-button registration in ClanMayhemWorld. */
public class MinigameTeleport @Inject constructor(
    private val validator: PlayerTeleportValidator,
    private val areas: AreaChecker,
) {
    public suspend fun ProtectedAccess.openMenu() {
        val choice = menu("Minigame Teleport", hotkeys = true, choices = DESTINATIONS.map { it.name })
        ifClose()
        val destination = DESTINATIONS.getOrNull(choice) ?: return
        cast(destination)
    }

    internal suspend fun ProtectedAccess.cast(
        destination: Destination,
        nowMinute: () -> Int = { (System.currentTimeMillis() / 60_000L).toInt() },
    ) {
        if (player.combatLevel < destination.minimumCombat) {
            mes("You need a combat level of ${destination.minimumCombat} to teleport to ${destination.name}.")
            return
        }
        val remaining = remainingMinutes(player, nowMinute())
        if (remaining > 0) {
            mes("You need to wait $remaining more minutes before you can use your minigame teleport.")
            return
        }
        val canTeleport = {
            val denial = validator.validate(player, TeleportType.Minigame, areas)
            if (denial != null) mes(denial)
            denial == null
        }
        if (castResting(destination.coords, canTeleport, TeleportType.Minigame)) {
            VarPlayerIntMapSetter.set(player, timer, nowMinute())
        }
    }

    internal data class Destination(val name: String, val coords: CoordGrid, val minimumCombat: Int = 0)

    internal companion object {
        // Native LAST_MINIGAME_TELEPORT, in Unix minutes; separate from home teleport's varp 892.
        val timer: String get() = RSCM.getReverseMapping(RSCMType.VARP, 888)
        const val RECHARGE_MINUTES = 20

        // Existing supported content entrances: PestControlConfig.exit and FoundryWorld's
        // FOUNDRY_WORKSHOP. Other native destinations require their own supported routing.
        val DESTINATIONS = listOf(
            Destination("Pest Control", CoordGrid(2657, 2639, 0), minimumCombat = 40),
            Destination("Giants' Foundry", CoordGrid(3369, 11486, 0)),
        )

        fun remainingMinutes(player: Player, nowMinute: Int): Int {
            val lastCast = player.vars[timer]
            return if (lastCast <= 0) 0 else (lastCast + RECHARGE_MINUTES - nowMinute).coerceAtLeast(0)
        }
    }
}
