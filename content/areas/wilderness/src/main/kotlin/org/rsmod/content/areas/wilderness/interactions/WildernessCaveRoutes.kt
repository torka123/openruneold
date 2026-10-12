package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

public data class WildernessCaveRoute(
    public val locId: Int,
    public val option: Int,
    public val source: CoordGrid,
    public val destination: CoordGrid,
    public val message: String,
    public val directionality: String,
    public val revenantFee: Boolean = false,
    public val facing: Direction? = null,
)

/**
 * Coordinate-scoped revision-240 links for Wilderness-connected caves outside the six boss lairs.
 * One-way arrivals deliberately do not reuse their exit's surface tile.
 */
public object WildernessCaveRouteTable {
    public val routes: List<WildernessCaveRoute> =
        listOf(
            WildernessCaveRoute(
                locId = WILDERNESS_GOD_WARS_CAVE,
                option = 1,
                source = CoordGrid(3016, 3738),
                destination = CoordGrid(3017, 10151),
                message = "You enter the Wilderness God Wars Dungeon.",
                directionality = "ONE_WAY_CACHE_ROUTE",
            ),
            WildernessCaveRoute(
                locId = REVENANT_CAVE_MIDDLE_ENTRANCE,
                option = 1,
                source = CoordGrid(3067, 3740),
                destination = CoordGrid(3240, 10234),
                message = "You jump down into the Revenant Caves.",
                directionality = "INTENTIONAL_ONE_WAY_ENTRANCE",
                revenantFee = true,
            ),
            WildernessCaveRoute(
                locId = WILDERNESS_SLAYER_CAVE_SOUTH_ENTRANCE,
                option = 1,
                source = CoordGrid(3259, 3664),
                destination = CoordGrid(3384, 10053),
                message = "You enter the Wilderness Slayer Cave.",
                directionality = "BIDIRECTIONAL_SLAYER_CAVE",
                facing = Direction.South,
            ),
            WildernessCaveRoute(
                locId = WILDERNESS_SLAYER_CAVE_NORTH_ENTRANCE,
                option = 1,
                source = CoordGrid(3293, 3746),
                destination = CoordGrid(3405, 10145),
                message = "You enter the Wilderness Slayer Cave.",
                directionality = "BIDIRECTIONAL_SLAYER_CAVE",
                facing = Direction.South,
            ),
            WildernessCaveRoute(
                locId = WILDERNESS_SLAYER_CAVE_SOUTH_EXIT,
                option = 1,
                source = CoordGrid(3384, 10050),
                destination = CoordGrid(3259, 3662),
                message = "You leave the Wilderness Slayer Cave.",
                directionality = "BIDIRECTIONAL_SLAYER_CAVE",
                facing = Direction.South,
            ),
            WildernessCaveRoute(
                locId = WILDERNESS_SLAYER_CAVE_NORTH_EXIT,
                option = 1,
                source = CoordGrid(3405, 10146),
                destination = CoordGrid(3293, 3749),
                message = "You leave the Wilderness Slayer Cave.",
                directionality = "BIDIRECTIONAL_SLAYER_CAVE",
                facing = Direction.North,
            ),
            WildernessCaveRoute(
                locId = REVENANT_CAVE_LOW_ENTRANCE,
                option = 1,
                source = CoordGrid(3073, 3654),
                destination = CoordGrid(3218, 10059),
                message = "You enter the Revenant Caves.",
                directionality = "INTENTIONAL_ONE_WAY_ENTRANCE",
                revenantFee = true,
            ),
            WildernessCaveRoute(
                locId = REVENANT_CAVE_LOW_EXIT,
                option = 1,
                source = CoordGrid(3218, 10058),
                destination = CoordGrid(3103, 3654),
                message = "You leave the Revenant Caves.",
                directionality = "INTENTIONAL_ONE_WAY_EXIT",
                facing = Direction.South,
            ),
            WildernessCaveRoute(
                locId = REVENANT_CAVE_HIGH_ENTRANCE,
                option = 1,
                source = CoordGrid(3124, 3831),
                destination = CoordGrid(3244, 10214),
                message = "You enter the Revenant Caves.",
                directionality = "INTENTIONAL_ONE_WAY_ENTRANCE",
                revenantFee = true,
            ),
            WildernessCaveRoute(
                locId = REVENANT_CAVE_HIGH_EXIT,
                option = 1,
                source = CoordGrid(3244, 10215),
                destination = CoordGrid(3122, 3806),
                message = "You leave the Revenant Caves.",
                directionality = "INTENTIONAL_ONE_WAY_EXIT",
                facing = Direction.North,
            ),
            WildernessCaveRoute(
                locId = CORPOREAL_BEAST_SURFACE_CAVE,
                option = 1,
                source = CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE,
                destination = CoordGrid(2964, 4254, 2),
                message = "You enter the Corporeal Beast's lair.",
                directionality = "BIDIRECTIONAL_PUBLIC_LAIR",
            ),
            WildernessCaveRoute(
                locId = CORPOREAL_BEAST_PASSAGE,
                option = 1,
                source = CoordGrid(2971, 4254, 2),
                destination = CoordGrid(2974, 4382, 2),
                message = "You go through the passage.",
                directionality = "BIDIRECTIONAL_PUBLIC_PASSAGE",
                facing = Direction.East,
            ),
            WildernessCaveRoute(
                locId = CORPOREAL_BEAST_PASSAGE,
                option = 1,
                source = CoordGrid(2971, 4382, 2),
                destination = CoordGrid(2970, 4254, 2),
                message = "You go through the passage.",
                directionality = "BIDIRECTIONAL_PUBLIC_PASSAGE",
                facing = Direction.West,
            ),
            WildernessCaveRoute(
                locId = CORPOREAL_BEAST_CAVE_EXIT,
                option = 1,
                source = CoordGrid(2963, 4382, 2),
                destination = CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE,
                message = "You leave the Corporeal Beast's lair.",
                directionality = "BIDIRECTIONAL_PUBLIC_LAIR",
            ),
            WildernessCaveRoute(
                locId = CORPOREAL_BEAST_CAVE_EXIT,
                option = 1,
                source = CoordGrid(2963, 4254, 2),
                destination = CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE,
                message = "You leave the Corporeal Beast's lair.",
                directionality = "SECONDARY_ROOM_EXIT",
            ),
        )

    public fun route(locId: Int, option: Int, source: CoordGrid): WildernessCaveRoute? =
        routes.firstOrNull { it.locId == locId && it.option == option && it.source == source }
}

public class WildernessCaveRoutes
@Inject
constructor(private val collision: CollisionFlagMap, private val locRepository: LocRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        WildernessCaveRouteTable.routes.forEach { route ->
            validateOption(route.locId, route.option)
            val key = LocCoordEvents.key(route.locId, route.source)
            when (route.option) {
                1 -> onProtectedEvent<LocCoordEvents.Op1>(key) { traverse(route) }
                2 -> onProtectedEvent<LocCoordEvents.Op2>(key) { traverse(route) }
                3 -> onProtectedEvent<LocCoordEvents.Op3>(key) { traverse(route) }
                else -> error("Unsupported Wilderness cave option: ${route.option}")
            }
        }
        removeCorpPrivatePortals()
    }

    private fun removeCorpPrivatePortals() {
        CORPOREAL_BEAST_PRIVATE_PORTAL_SOURCES.forEach { source ->
            locRepository
                .findAll(source)
                .filter { it.id == CORPOREAL_BEAST_PRIVATE_PORTAL }
                .toList()
                .forEach { locRepository.del(it, Int.MAX_VALUE) }
        }
    }

    private suspend fun ProtectedAccess.traverse(route: WildernessCaveRoute) {
        arriveDelay()
        if (route.revenantFee && !payRevenantFee()) return
        if (!isSafe(route.destination)) {
            mes("That cave route's exact arrival tile is blocked.")
            return
        }
        val destination = route.destination
        telejump(destination, TeleportType.Exempt)
        if (coords == destination) {
            route.facing?.let(::faceDirection)
            mes(route.message)
        }
    }

    private fun ProtectedAccess.payRevenantFee(): Boolean {
        if (WildernessInteractionTestMode.isEnabled(player)) return true
        if (player.vars[REVENANT_CAVE_FEE_PAID_VARP] != 0) {
            VarPlayerIntMapSetter.set(player, REVENANT_CAVE_FEE_AT_RISK_VARP, 1)
            return true
        }
        val inventoryCount = inv.count(REVENANT_CAVE_FEE_CURRENCY)
        val bankCount = bank.count(REVENANT_CAVE_FEE_CURRENCY)
        if (inventoryCount.toLong() + bankCount.toLong() < REVENANT_CAVE_FEE_AMOUNT.toLong()) {
            mes("You need 100,000 coins to enter the Revenant Caves.")
            return false
        }
        val fromInventory = minOf(inventoryCount, REVENANT_CAVE_FEE_AMOUNT)
        val fromBank = REVENANT_CAVE_FEE_AMOUNT - fromInventory
        if (fromInventory > 0 && invDel(inv, REVENANT_CAVE_FEE_CURRENCY, fromInventory).failure) {
            mes("You need 100,000 coins to enter the Revenant Caves.")
            return false
        }
        if (fromBank > 0 && invDel(bank, REVENANT_CAVE_FEE_CURRENCY, fromBank).failure) {
            if (fromInventory > 0)
                check(invAdd(inv, REVENANT_CAVE_FEE_CURRENCY, fromInventory).success)
            mes("The entrance fee could not be collected. No coins were taken.")
            return false
        }
        VarPlayerIntMapSetter.set(player, REVENANT_CAVE_FEE_PAID_VARP, 1)
        VarPlayerIntMapSetter.set(player, REVENANT_CAVE_FEE_AT_RISK_VARP, 1)
        mes("You pay 100,000 coins to enter the Revenant Caves.")
        return true
    }

    private fun isSafe(destination: CoordGrid): Boolean =
        collision.isZoneValid(destination) && !collision.isWalkBlocked(destination)

    private fun validateOption(locId: Int, option: Int) {
        val type =
            requireNotNull(ServerCacheManager.getObject(locId)) {
                "Missing revision-240 Wilderness cave object: $locId"
            }
        require(!type.actions.getOpOrNull(option - 1).isNullOrBlank()) {
            "Revision-240 Wilderness cave object $locId has no option $option"
        }
    }
}

internal const val WILDERNESS_GOD_WARS_CAVE: Int = 26766
internal const val WILDERNESS_SLAYER_CAVE_SOUTH_ENTRANCE: Int = 40388
internal const val WILDERNESS_SLAYER_CAVE_SOUTH_EXIT: Int = 40389
internal const val WILDERNESS_SLAYER_CAVE_NORTH_ENTRANCE: Int = 40390
internal const val WILDERNESS_SLAYER_CAVE_NORTH_EXIT: Int = 40391
internal const val REVENANT_CAVE_LOW_ENTRANCE: Int = 31555
internal const val REVENANT_CAVE_HIGH_ENTRANCE: Int = 31556
internal const val REVENANT_CAVE_LOW_EXIT: Int = 31558
internal const val REVENANT_CAVE_MIDDLE_ENTRANCE: Int = 40386
internal const val REVENANT_CAVE_HIGH_EXIT: Int = 43868
internal const val CORPOREAL_BEAST_SURFACE_CAVE: Int = 678
internal const val CORPOREAL_BEAST_CAVE_EXIT: Int = 679
internal const val CORPOREAL_BEAST_PASSAGE: Int = 677
internal const val CORPOREAL_BEAST_PRIVATE_PORTAL: Int = 9370
internal val CORPOREAL_BEAST_SURFACE_ENTRANCE_TILE: CoordGrid = CoordGrid(3201, 3679)
internal val CORPOREAL_BEAST_PRIVATE_PORTAL_SOURCES: Set<CoordGrid> =
    setOf(CoordGrid(2966, 4251, 2), CoordGrid(2966, 4379, 2))
internal const val REVENANT_CAVE_FEE_CURRENCY: String = "obj.coins"
internal const val REVENANT_CAVE_FEE_AMOUNT: Int = 100_000
internal const val REVENANT_CAVE_FEE_PAID_VARP: String = "varp.revenant_cave_fee_paid"
internal const val REVENANT_CAVE_FEE_AT_RISK_VARP: String = "varp.revenant_cave_fee_at_risk"
