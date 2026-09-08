package org.rsmod.content.bosses.wilderness

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.areas.wilderness.interactions.WildernessInteractionTestMode
import org.rsmod.game.MapClock
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Revision-240 travel links for all six Wilderness boss caves and their shared exits. */
public class WildernessBossAccess
@Inject
constructor(
    private val players: PlayerRegistry,
    random: GameRandom,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val escapeRoutes = WildernessEscapeRouteSelector(random::of)

    override fun ScriptContext.startup() {
        registerBlockedSurfaceEscapeOpenings()
        registerSimpleExits(WILDERNESS_ESCAPE_CAVE_EXIT_LINKS)
        registerEntrances(WILDERNESS_BOSS_ENTRANCE_LINKS)
        registerExits(WILDERNESS_BOSS_EXIT_LINKS, WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES)
    }

    private fun ScriptContext.registerEntrances(
        linksByLoc: Map<Int, Map<CoordGrid, CaveTravelLink>>
    ) {
        linksByLoc.forEach { (locId, links) ->
            validateLoc(locId, requiredOptions = 3)
            val room = WILDERNESS_BOSS_ROOMS_BY_ENTRANCE.getValue(locId)
            links.keys.forEach { source ->
                val key = LocCoordEvents.key(locId, source)
                onProtectedEvent<LocCoordEvents.Op1>(key) { enter(it.loc.coords, links) }
                onProtectedEvent<LocCoordEvents.Op2>(key) { peek(room) }
                onProtectedEvent<LocCoordEvents.Op3>(key) { checkFee() }
                val registered =
                    listOf(
                        LocCoordEvents.Op1::class.java,
                        LocCoordEvents.Op2::class.java,
                        LocCoordEvents.Op3::class.java,
                    )
                check(registered.all { eventBus.contains(it, key) }) {
                    "Wilderness boss entrance handlers were not registered: $locId at $source"
                }
            }
        }
    }

    private fun ScriptContext.registerBlockedSurfaceEscapeOpenings() {
        validateLoc(WILDERNESS_ESCAPE_CAVE_ENTRANCE, requiredOptions = 1)
        WILDERNESS_ESCAPE_CAVE_SURFACE_EXITS.forEach { source ->
            val key = LocCoordEvents.key(WILDERNESS_ESCAPE_CAVE_ENTRANCE, source)
            onProtectedEvent<LocCoordEvents.Op1>(key) {
                arriveDelay()
                mes("Nothing interesting happens.")
            }
            check(eventBus.contains(LocCoordEvents.Op1::class.java, key))
        }
    }

    private fun ScriptContext.registerSimpleExits(
        linksByLoc: Map<Int, Map<CoordGrid, CaveTravelLink>>
    ) {
        linksByLoc.forEach { (locId, links) ->
            validateLoc(locId, requiredOptions = 1)
            links.keys.forEach { source ->
                val key = LocCoordEvents.key(locId, source)
                onProtectedEvent<LocCoordEvents.Op1>(key) { leaveEscapeCave(it.loc.coords, links) }
                check(eventBus.contains(LocCoordEvents.Op1::class.java, key)) {
                    "Escape Cave exit handler was not registered: $locId at $source"
                }
            }
        }
    }

    private fun ScriptContext.registerExits(
        linksByLoc: Map<Int, Map<CoordGrid, CaveTravelLink>>,
        escapeSourcesByLoc: Map<Int, Set<CoordGrid>>,
    ) {
        WILDERNESS_BOSS_EXIT_LOC_IDS.forEach { locId ->
            val links = linksByLoc[locId].orEmpty()
            val escapeSources = escapeSourcesByLoc[locId].orEmpty()
            validateLoc(locId, requiredOptions = 1)
            (links.keys + escapeSources).forEach { source ->
                val key = LocCoordEvents.key(locId, source)
                onProtectedEvent<LocCoordEvents.Op1>(key) {
                    traverseExit(it.loc.coords, links, escapeSources)
                }
                check(eventBus.contains(LocCoordEvents.Op1::class.java, key)) {
                    "Wilderness boss exit handler was not registered: $locId at $source"
                }
            }
        }
    }

    private fun validateLoc(locId: Int, requiredOptions: Int) {
        val type =
            requireNotNull(ServerCacheManager.getObject(locId)) {
                "Missing revision-240 Wilderness boss cave object: $locId"
            }
        for (op in 0 until requiredOptions) {
            require(!type.actions.getOpOrNull(op).isNullOrBlank()) {
                "Revision-240 Wilderness boss cave object has no option ${op + 1}: " +
                    "$locId (actions=${type.actions})"
            }
        }
    }

    private suspend fun ProtectedAccess.enter(
        source: CoordGrid,
        links: Map<CoordGrid, CaveTravelLink>,
    ) {
        arriveDelay()
        val link =
            links[source]
                ?: run {
                    mes("That cave route is unavailable.")
                    return
                }
        if (!payEntranceFee()) return
        travel(link)
    }

    private suspend fun ProtectedAccess.leaveEscapeCave(
        source: CoordGrid,
        links: Map<CoordGrid, CaveTravelLink>,
    ) {
        arriveDelay()
        val link =
            links[source]
                ?: run {
                    mes("That cave route is unavailable.")
                    return
                }
        travel(link)
    }

    private suspend fun ProtectedAccess.peek(room: WildernessBossRoom) {
        arriveDelay()
        var count = 0
        players.forEachOnline { other ->
            if (other.isSlotAssigned && room.contains(other.coords)) count++
        }
        val noun = if (count == 1) "player" else "players"
        mes("There ${if (count == 1) "is" else "are"} $count $noun inside ${room.name}.")
    }

    private suspend fun ProtectedAccess.checkFee() {
        arriveDelay()
        val status = if (hasPaidEntranceFee()) " You have already paid it." else ""
        mes("The fee to enter this cave is ${WILDERNESS_BOSS_ENTRANCE_FEE.formatted()}.$status")
    }

    private fun ProtectedAccess.payEntranceFee(): Boolean {
        if (WildernessInteractionTestMode.isEnabled(player)) return true
        val fee = WILDERNESS_BOSS_ENTRANCE_FEE
        if (fee.amount <= 0) return true
        if (hasPaidEntranceFee()) {
            VarPlayerIntMapSetter.set(player, WILDERNESS_BOSS_FEE_AT_RISK_VARP, 1)
            return true
        }
        val inventoryCount = inv.count(fee.currency)
        val bankCount = bank.count(fee.currency)
        if (inventoryCount.toLong() + bankCount.toLong() < fee.amount.toLong()) {
            mes("You need ${fee.formatted()} to enter this cave.")
            return false
        }
        val fromInventory = minOf(inventoryCount, fee.amount)
        val fromBank = fee.amount - fromInventory
        if (fromInventory > 0 && invDel(inv, fee.currency, fromInventory).failure) {
            mes("You need ${fee.formatted()} to enter this cave.")
            return false
        }
        if (fromBank > 0 && invDel(bank, fee.currency, fromBank).failure) {
            if (fromInventory > 0) check(invAdd(inv, fee.currency, fromInventory).success)
            mes("The entrance fee could not be collected. No coins were taken.")
            return false
        }
        VarPlayerIntMapSetter.set(player, WILDERNESS_BOSS_FEE_PAID_VARP, 1)
        VarPlayerIntMapSetter.set(player, WILDERNESS_BOSS_FEE_AT_RISK_VARP, 1)
        mes("You pay ${fee.formatted()} to enter the cave.")
        return true
    }

    private fun ProtectedAccess.hasPaidEntranceFee(): Boolean =
        player.vars[WILDERNESS_BOSS_FEE_PAID_VARP] != 0

    private suspend fun ProtectedAccess.traverseExit(
        source: CoordGrid,
        links: Map<CoordGrid, CaveTravelLink>,
        escapeSources: Set<CoordGrid>,
    ) {
        arriveDelay()
        val link =
            links[source]
                ?: if (source in escapeSources) {
                    CaveTravelLink(
                        destination =
                            escapeRoutes.select(source, this@WildernessBossAccess.mapClock.cycle),
                        message = "You enter the Escape Caves.",
                        facing = Direction.North,
                    )
                } else {
                    null
                }
                ?: run {
                    mes("That cave route is unavailable.")
                    return
                }
        travel(link)
    }

    private fun ProtectedAccess.travel(link: CaveTravelLink) {
        // Walking through a cave is physical traversal, so Wilderness spell-teleport and
        // teleblock restrictions must not reject it.
        val destination = safeDestination(link.destination)
        if (destination == null) {
            mes("That cave route has no safe arrival tile.")
            return
        }
        telejump(destination, WILDERNESS_BOSS_CAVE_TRAVERSAL)
        if (coords == destination) {
            link.facing?.let(::faceDirection)
            mes(link.message)
        }
    }

    private fun safeDestination(preferred: CoordGrid): CoordGrid? {
        val candidates = buildList {
            add(preferred)
            for (radius in 1..2) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dz)) == radius) {
                            add(preferred.translate(dx, dz))
                        }
                    }
                }
            }
        }
        return candidates.firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
    }
}

internal data class CaveTravelLink(
    val destination: CoordGrid,
    val message: String,
    val facing: Direction? = null,
)

internal class WildernessEscapeRouteSelector(private val randomIndex: (Int) -> Int) {
    private val recentRoutes = mutableMapOf<CoordGrid, RecentEscapeRoute>()

    fun select(source: CoordGrid, currentTick: Int): CoordGrid {
        val recent = recentRoutes[source]
        if (recent != null && currentTick - recent.selectedTick <= ESCAPE_ROUTE_FOLLOW_TICKS) {
            return recent.destination
        }
        val destination =
            WILDERNESS_BOSS_ESCAPE_ARRIVALS[randomIndex(WILDERNESS_BOSS_ESCAPE_ARRIVALS.size)]
        recentRoutes[source] = RecentEscapeRoute(destination, currentTick)
        return destination
    }
}

private data class RecentEscapeRoute(val destination: CoordGrid, val selectedTick: Int)

internal data class WildernessBossRoom(
    val name: String,
    val bossName: String,
    val npcInternalName: String,
    val mapSquareX: Int,
    val mapSquareZ: Int,
    val level: Int,
) {
    fun contains(coords: CoordGrid): Boolean =
        coords.x shr 6 == mapSquareX && coords.z shr 6 == mapSquareZ && coords.level == level
}

internal data class WildernessBossEntranceFee(
    val currency: String,
    val amount: Int,
    val currencyName: String,
) {
    fun formatted(): String = "${"%,d".format(amount)} $currencyName"
}

/** Change these three values when the entrance currency or amount changes. */
internal val WILDERNESS_BOSS_ENTRANCE_FEE =
    WildernessBossEntranceFee(currency = "obj.coins", amount = 100_000, currencyName = "coins")

internal val CALVARION_ROOM =
    WildernessBossRoom("Skeletal Tomb", "Calvar'ion", "npc.vetion_single", 29, 180, 1)
internal val SPINDEL_ROOM =
    WildernessBossRoom("Web Chasm", "Spindel", "npc.venenatis_singles", 25, 180, 2)
internal val ARTIO_ROOM =
    WildernessBossRoom("Hunter's End", "Artio", "npc.callisto_singles", 27, 180, 0)

internal val VETION_ROOM = WildernessBossRoom("Vet'ion's Rest", "Vet'ion", "npc.vetion", 51, 159, 1)
internal val VENENATIS_ROOM =
    WildernessBossRoom("Silk Chasm", "Venenatis", "npc.venenatis", 53, 159, 2)
internal val CALLISTO_ROOM =
    WildernessBossRoom("Callisto's Den", "Callisto", "npc.callisto", 52, 161, 0)
internal const val WILDERNESS_BOSS_FEE_PAID_VARP: String = "varp.wilderness_boss_fee_paid"
internal const val WILDERNESS_BOSS_FEE_AT_RISK_VARP: String = "varp.wilderness_boss_fee_at_risk"

private val CALVARION_SURFACE =
    CaveTravelLink(CoordGrid(3180, 3682), "You leave the Skeletal Tomb.", Direction.South)
private val SPINDEL_SURFACE =
    CaveTravelLink(CoordGrid(3183, 3743), "You leave the Web Chasm.", Direction.South)
private val ARTIO_SURFACE =
    CaveTravelLink(CoordGrid(3115, 3675), "You leave Hunter's End.", Direction.South)

internal val WILDERNESS_BOSS_ROOMS_BY_ENTRANCE: Map<Int, WildernessBossRoom> =
    mapOf(
        CALVARION_ENTRANCE to CALVARION_ROOM,
        SPINDEL_ENTRANCE to SPINDEL_ROOM,
        ARTIO_ENTRANCE to ARTIO_ROOM,
        VETION_ENTRANCE to VETION_ROOM,
        VENENATIS_ENTRANCE to VENENATIS_ROOM,
        CALLISTO_ENTRANCE to CALLISTO_ROOM,
    )

internal val WILDERNESS_BOSS_ENTRANCE_LINKS: Map<Int, Map<CoordGrid, CaveTravelLink>> =
    mapOf(
        CALVARION_ENTRANCE to
            mapOf(
                CoordGrid(3180, 3683) to
                    CaveTravelLink(
                        CoordGrid(1886, 11539, 1),
                        "You enter the Skeletal Tomb.",
                        Direction.North,
                    )
            ),
        SPINDEL_ENTRANCE to
            mapOf(
                CoordGrid(3183, 3744) to
                    CaveTravelLink(
                        CoordGrid(1630, 11532, 2),
                        "You enter the Web Chasm.",
                        Direction.North,
                    )
            ),
        ARTIO_ENTRANCE to
            mapOf(
                CoordGrid(3115, 3676) to
                    CaveTravelLink(
                        CoordGrid(1758, 11536),
                        "You enter Hunter's End.",
                        Direction.North,
                    )
            ),
        VETION_ENTRANCE to
            mapOf(
                CoordGrid(3221, 3787) to
                    CaveTravelLink(
                        CoordGrid(3294, 10195, 1),
                        "You enter Vet'ion's Rest.",
                        Direction.North,
                    )
            ),
        VENENATIS_ENTRANCE to
            mapOf(
                CoordGrid(3321, 3794) to
                    CaveTravelLink(
                        CoordGrid(3422, 10184, 2),
                        "You enter the Silk Chasm.",
                        Direction.North,
                    )
            ),
        CALLISTO_ENTRANCE to
            mapOf(
                CoordGrid(3291, 3849) to
                    CaveTravelLink(
                        CoordGrid(3358, 10316),
                        "You enter Callisto's Den.",
                        Direction.North,
                    )
            ),
    )

internal val WILDERNESS_BOSS_EXIT_LINKS: Map<Int, Map<CoordGrid, CaveTravelLink>> =
    mapOf(
        CALVARION_EXIT to mapOf(CoordGrid(1886, 11534, 1) to CALVARION_SURFACE),
        SPINDEL_EXIT to mapOf(CoordGrid(1630, 11527, 2) to SPINDEL_SURFACE),
        ARTIO_EXIT to mapOf(CoordGrid(1758, 11531) to ARTIO_SURFACE),
    )

/** The stronger Vet'ion, Venenatis, and Callisto exits feed the shared Escape Caves. */
internal val WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES: Map<Int, Set<CoordGrid>> =
    mapOf(
        CALVARION_EXIT to setOf(CoordGrid(3294, 10190, 1)),
        SPINDEL_EXIT to setOf(CoordGrid(3422, 10183, 2)),
        ARTIO_EXIT to setOf(CoordGrid(3358, 10315)),
    )

/** Safe arrival markers packed in the revision-240 Escape Caves map square. */
internal val WILDERNESS_BOSS_ESCAPE_ARRIVALS: List<CoordGrid> =
    listOf(CoordGrid(3342, 10252), CoordGrid(3361, 10295), CoordGrid(3375, 10254))

internal val WILDERNESS_BOSS_EXIT_LOC_IDS: Set<Int> =
    WILDERNESS_BOSS_EXIT_LINKS.keys + WILDERNESS_BOSS_ESCAPE_EXIT_SOURCES.keys

internal const val CALVARION_ENTRANCE: Int = 46996
internal const val CALVARION_EXIT: Int = 46925
internal const val SPINDEL_ENTRANCE: Int = 47078
internal const val SPINDEL_EXIT: Int = 47000
internal const val ARTIO_ENTRANCE: Int = 47141
internal const val ARTIO_EXIT: Int = 47122
internal const val ESCAPE_ROUTE_FOLLOW_TICKS: Int = 16
internal val WILDERNESS_BOSS_CAVE_TRAVERSAL: TeleportType = TeleportType.Exempt
internal const val VETION_ENTRANCE: Int = 46995
internal const val VENENATIS_ENTRANCE: Int = 47077
internal const val WILDERNESS_ESCAPE_CAVE_ENTRANCE: Int = 47175
internal const val WILDERNESS_ESCAPE_CAVE_SOUTH_EXIT: Int = 47147
internal const val WILDERNESS_ESCAPE_CAVE_SIDE_EXIT: Int = 47148
internal const val WILDERNESS_ESCAPE_CAVE_CENTRAL_EXIT: Int = 47149

internal val WILDERNESS_ESCAPE_CAVE_SURFACE_EXITS: Set<CoordGrid> =
    setOf(
        CoordGrid(3260, 3832),
        CoordGrid(3282, 3774),
        CoordGrid(3284, 3807),
        CoordGrid(3320, 3830),
    )

internal val WILDERNESS_ESCAPE_CAVE_EXIT_LINKS: Map<Int, Map<CoordGrid, CaveTravelLink>> =
    mapOf(
        WILDERNESS_ESCAPE_CAVE_SIDE_EXIT to
            mapOf(
                CoordGrid(3336, 10287) to
                    CaveTravelLink(
                        CoordGrid(3260, 3831),
                        "You leave the Escape Caves.",
                        Direction.South,
                    ),
                CoordGrid(3382, 10287) to
                    CaveTravelLink(
                        CoordGrid(3320, 3829),
                        "You leave the Escape Caves.",
                        Direction.South,
                    ),
            ),
        WILDERNESS_ESCAPE_CAVE_SOUTH_EXIT to
            mapOf(
                CoordGrid(3358, 10244) to
                    CaveTravelLink(
                        CoordGrid(3282, 3773),
                        "You leave the Escape Caves.",
                        Direction.South,
                    )
            ),
        WILDERNESS_ESCAPE_CAVE_CENTRAL_EXIT to
            mapOf(
                CoordGrid(3361, 10273) to
                    CaveTravelLink(
                        CoordGrid(3284, 3806),
                        "You leave the Escape Caves.",
                        Direction.South,
                    )
            ),
    )
internal const val CALLISTO_ENTRANCE: Int = 47140
