package org.rsmod.content.other.pkkits

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.instances.currentInstanceId
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Player

@Singleton
public class KitRestrictionService @Inject constructor(private val areaChecker: AreaChecker) {
    public fun validate(player: Player): List<String> =
        evaluateKitRestrictions(
            KitRestrictionContext(
                mutableOnline =
                    player.isSlotAssigned &&
                        !player.loggingOut &&
                        !player.pendingLogout &&
                        !player.pendingShutdown,
                alive = player.hitpoints > 0,
                inCombat = player.isInCombat(),
                inWilderness = player.coords.isInWilderness(areaChecker),
                inInstance = player.currentInstanceId() != null,
                busy = player.isDelayed || player.isBusy2,
                ultimateIronman = player.isUltimateIronman,
                permittedArea =
                    AllowAnyNonWildernessArea ||
                        AllowedSafeAreas.any { areaChecker.inArea(it, player.coords) },
            )
        )

    public fun evaluate(context: KitRestrictionContext): List<String> =
        evaluateKitRestrictions(context)

    public companion object {
        /** Set false when the live game has explicit home/safe-zone area gamevals configured. */
        public const val AllowAnyNonWildernessArea: Boolean = true

        /** Area gamevals used when [AllowAnyNonWildernessArea] is disabled. */
        public val AllowedSafeAreas: Set<String> = emptySet()
    }
}
