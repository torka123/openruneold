package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import java.time.Instant
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.interact.LocCategoryEvents
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.collision.CollisionFlagMap

public class WildernessInteractionScanner
@Inject
constructor(
    private val locZones: LocZoneStorage,
    private val locRepository: LocRepository,
    private val areaChecker: AreaChecker,
    private val eventBus: EventBus,
    private val collision: CollisionFlagMap,
) {
    public fun scan(): WildernessInteractionAudit {
        val raw = mutableListOf<WildernessInteractionRecord>()
        val scopedRegions = linkedSetOf<Int>()
        val scopedPlanes = linkedSetOf<Int>()
        val connectedAreas = linkedSetOf<String>()
        val candidateObjects = linkedSetOf<String>()
        var conflicts = 0

        val zones = locZones.mapLocs.zoneKeys().sortedBy { it.packed }
        for (zone in zones) {
            for (loc in locRepository.findAll(zone)) {
                val area = areaName(loc.coords) ?: continue
                val region = MapSquareKey.from(loc.coords).id
                scopedRegions += region
                scopedPlanes += loc.coords.level
                if (area != SURFACE_AREA && area != FER0X_AREA) {
                    connectedAreas += area
                }

                val type = ServerCacheManager.getObject(loc.id)
                if (type == null) {
                    if (looksLikeTraversalId(loc.id)) {
                        raw += missingDefinitionRecord(loc, area, region)
                    }
                    continue
                }

                val candidates = candidateActions(type)
                if (candidates.isEmpty() && !hasTraversalIdentity(type)) {
                    continue
                }
                candidateObjects += locIdentity(loc)
                val actions = candidates.ifEmpty { listOf(ActionCandidate(0, "<missing>")) }
                for (candidate in actions) {
                    val handlers =
                        if (candidate.option in 1..5) {
                            handlerRegistrations(type, candidate.option, loc.coords)
                        } else {
                            emptyList()
                        }
                    if (handlers.size > 1) {
                        conflicts++
                    }
                    raw += record(loc, type, area, region, candidate, handlers)
                }
            }
        }

        val records =
            raw.sortedWith(
                    compareBy<WildernessInteractionRecord>(
                        { it.sourcePlane },
                        { it.sourceRegionId },
                        { it.sourceX },
                        { it.sourceY },
                        { it.sourceObjectId },
                        { it.sourceAction },
                    )
                )
                .mapIndexed { index, record -> record.copy(auditIndex = index + 1) }
        val summary =
            WildernessInteractionAuditSummary(
                activeRevision = ACTIVE_REVISION,
                regionsScanned = scopedRegions.size,
                connectedAreasScanned = connectedAreas.size,
                planesScanned = scopedPlanes,
                candidateObjects = candidateObjects.size,
                routeRecords = records.size,
                registeredRoutes = records.count { it.existingHandlerFile.isNotBlank() },
                passingRoutes = records.count { it.auditStatus == "PASS" },
                missingHandlers = records.count { it.auditStatus == "NO_HANDLER" },
                wrongDestinations = records.count { it.auditStatus == "WRONG_DESTINATION" },
                missingReverseRoutes = records.count { it.auditStatus == "MISSING_REVERSE_ROUTE" },
                intentionalOneWayRoutes =
                    records.count { it.routeDirectionality == "INTENTIONAL_ONE_WAY" },
                invalidDestinations =
                    records.count {
                        it.auditStatus == "DESTINATION_BLOCKED" ||
                            it.auditStatus == "DESTINATION_OUTSIDE_LOADED_MAP"
                    },
                missingCacheContent =
                    records.count {
                        it.auditStatus == "MISSING_OBJECT_DEFINITION" ||
                            it.auditStatus == "BLOCKED_BY_MISSING_CACHE_CONTENT"
                    },
                conflictingRegistrations = conflicts,
            )
        return WildernessInteractionAudit(
            generatedAt = Instant.now().toString(),
            scopeDescription = SCOPE_DESCRIPTION,
            summary = summary,
            records = records,
        )
    }

    private fun record(
        loc: LocInfo,
        type: ObjectServerType,
        area: String,
        region: Int,
        candidate: ActionCandidate,
        handlers: List<String>,
    ): WildernessInteractionRecord {
        val internalName = runCatching { type.internalName }.getOrDefault("loc.${type.id}")
        val objectName = displayName(type, internalName)
        val transformIds = type.multiLoc.filter { it >= 0 }
        val sourceLoaded = collision.isZoneValid(loc.coords)
        val sourceBlocked = sourceLoaded && collision.isWalkBlocked(loc.coords)
        val verticalRoute =
            WildernessVerticalRouteTable.route(type.id, candidate.option, loc.coords)
        val verticalChoice =
            if (candidate.option == 1) {
                WildernessVerticalRouteTable.choice(type.id, loc.coords)
            } else {
                null
            }
        val managedVerticalSource =
            WildernessVerticalRouteTable.routes.any {
                it.locId == type.id && it.source == loc.coords
            } ||
                WildernessVerticalRouteTable.choices.any {
                    it.locId == type.id && it.source == loc.coords
                }
        val verifiedVertical = verticalRoute != null || verticalChoice != null
        val cacheBlockedInteraction =
            type.id in CACHE_BLOCKED_INTERACTION_IDS && candidate.option !in 1..5
        val intentionalExceptionReason =
            WILDERNESS_UNSUPPORTED_TRANSPORT[type.id to loc.coords]
                ?: if (
                    type.id == CORPOREAL_BEAST_PRIVATE_PORTAL &&
                        loc.coords in CORPOREAL_BEAST_PRIVATE_PORTAL_SOURCES
                ) {
                    "Private Corporeal Beast instances are not available on this world yet."
                } else null
        val status =
            when {
                cacheBlockedInteraction -> "BLOCKED_BY_MISSING_CACHE_CONTENT"
                intentionalExceptionReason != null -> "INTENTIONAL_OPENRUNE_CUSTOM_ROUTE"
                candidate.option !in 1..5 -> "WRONG_OPTION"
                managedVerticalSource && !verifiedVertical -> "BLOCKED_BY_MISSING_CACHE_CONTENT"
                verifiedVertical -> "PASS"
                handlers.isEmpty() -> "NO_HANDLER"
                else -> "PASS"
            }
        val handlerDescription =
            handlers.joinToString(" + ").ifBlank {
                if (verifiedVertical) "verified Wilderness vertical route table" else ""
            }
        val reverseAction =
            verticalRoute
                ?.reverseLocId
                ?.let { reverseId ->
                    val reverseOption = verticalRoute.reverseOption ?: return@let ""
                    ServerCacheManager.getObject(reverseId)
                        ?.actions
                        ?.getOpOrNull(reverseOption - 1)
                        .orEmpty()
                }
                .orEmpty()
        val notes = buildString {
            append(
                "Discovery classification from the revision-240 decoded map; internal object name=$internalName. "
            )
            append(
                "Source collision zone loaded=$sourceLoaded; source tile walk-blocked=$sourceBlocked. "
            )
            if (transformIds.isNotEmpty()) {
                append("Transform object IDs=${transformIds.joinToString()}. ")
            }
            if (handlers.size > 1) {
                append(
                    "Multiple resolution layers are registered; runtime precedence is direct, content, category. "
                )
            }
            when {
                verticalRoute != null ->
                    append(
                        "Destination and reverse anchor are covered by the automated vertical route table tests."
                    )
                verticalChoice != null ->
                    append(
                        "Both dialogue choices are covered by the automated vertical route table tests."
                    )
                managedVerticalSource ->
                    append(
                        "This action is disabled because the paired revision-240 object has no usable cache action; allowing it would trap the player."
                    )
                cacheBlockedInteraction ->
                    append(
                        "Revision 240 supplies the obelisk pillar model and map placement but no clickable action on the source or any transform; client/cache content is required before a server handler can receive an interaction."
                    )
                intentionalExceptionReason != null ->
                    append("Intentional OpenRune exception: $intentionalExceptionReason")
                else ->
                    append(
                        "Destination, reverse route, timing, and requirements require route-level verification."
                    )
            }
        }
        return WildernessInteractionRecord(
            auditIndex = 0,
            areaName = area,
            routeName = "$area: $objectName - ${candidate.action}",
            sourceObjectName = objectName,
            sourceObjectId = type.id,
            sourceObjectType = loc.shape.name,
            sourceObjectOrientation = loc.angle.id,
            sourceAction = candidate.action,
            sourceX = loc.coords.x,
            sourceY = loc.coords.z,
            sourcePlane = loc.coords.level,
            sourceRegionId = region,
            destinationArea = verticalRoute?.destinationAnchor?.let(::areaName).orEmpty(),
            destinationX = verticalRoute?.destinationAnchor?.x,
            destinationY = verticalRoute?.destinationAnchor?.z,
            destinationPlane = verticalRoute?.destinationAnchor?.level,
            reverseObjectId = verticalRoute?.reverseLocId,
            reverseAction = reverseAction,
            reverseSourceTile = verticalRoute?.destinationAnchor?.toString().orEmpty(),
            routeDirectionality =
                verticalRoute?.directionality
                    ?: if (verticalChoice != null) "BIDIRECTIONAL" else "UNVERIFIED",
            delayInGameTicks = verticalRoute?.let { if (it.animate) 1 else 0 },
            movementRoute =
                verticalRoute?.movement
                    ?: verticalChoice
                        ?.let {
                            val down =
                                it.down?.destinationAnchor?.toString()
                                    ?: "disabled (missing reverse cache action)"
                            "dialogue choice: down=$down; up=${it.up.destinationAnchor}"
                        }
                        .orEmpty(),
            existingHandlerFile = handlerDescription,
            finalHandlerFile = if (verifiedVertical) VERTICAL_HANDLER_FILE else "",
            auditStatus = status,
            automatedTestStatus =
                when {
                    verifiedVertical -> "ROUTE_TABLE_VALIDATED"
                    cacheBlockedInteraction -> "BLOCKED_BY_CACHE"
                    intentionalExceptionReason != null -> "INTENTIONAL_EXCEPTION"
                    status == "PASS" -> "DISCOVERY_HANDLER_ONLY"
                    else -> "FAIL"
                },
            manualTestStatus =
                when {
                    cacheBlockedInteraction -> "BLOCKED"
                    intentionalExceptionReason != null -> "NOT_APPLICABLE"
                    else -> "PENDING"
                },
            notes = notes,
        )
    }

    private fun missingDefinitionRecord(
        loc: LocInfo,
        area: String,
        region: Int,
    ): WildernessInteractionRecord =
        WildernessInteractionRecord(
            auditIndex = 0,
            areaName = area,
            routeName = "$area: missing object definition ${loc.id}",
            sourceObjectName = "<missing definition>",
            sourceObjectId = loc.id,
            sourceObjectType = loc.shape.name,
            sourceObjectOrientation = loc.angle.id,
            sourceAction = "<unknown>",
            sourceX = loc.coords.x,
            sourceY = loc.coords.z,
            sourcePlane = loc.coords.level,
            sourceRegionId = region,
            auditStatus = "MISSING_OBJECT_DEFINITION",
            automatedTestStatus = "FAIL",
            manualTestStatus = "BLOCKED",
            notes =
                "Object instance exists in the decoded map but its revision-240 definition is unavailable.",
        )

    private fun candidateActions(type: ObjectServerType): List<ActionCandidate> {
        val identity = traversalIdentity(type)
        val result = mutableListOf<ActionCandidate>()
        for (option in 1..5) {
            val action = type.actions.getOpOrNull(option - 1)?.trim().orEmpty()
            if (action.isEmpty()) {
                continue
            }
            val normalized = action.lowercase()
            val strong = STRONG_ACTIONS.any { normalized == it || normalized.startsWith("$it-") }
            val weak = WEAK_ACTIONS.any { normalized == it || normalized.startsWith("$it-") }
            if (strong || (weak && identity)) {
                result += ActionCandidate(option, action)
            }
        }
        if (result.isNotEmpty()) {
            return result
        }
        for (transform in type.multiLoc) {
            if (transform < 0) continue
            val transformed = ServerCacheManager.getObject(transform) ?: continue
            for (option in 1..5) {
                val action = transformed.actions.getOpOrNull(option - 1)?.trim().orEmpty()
                if (action.isEmpty()) continue
                val normalized = action.lowercase()
                val strong =
                    STRONG_ACTIONS.any { normalized == it || normalized.startsWith("$it-") }
                val weak = WEAK_ACTIONS.any { normalized == it || normalized.startsWith("$it-") }
                if (strong || (weak && (identity || traversalIdentity(transformed)))) {
                    result += ActionCandidate(option, action)
                }
            }
        }
        return result.distinctBy { it.option to it.action.lowercase() }
    }

    private fun hasTraversalIdentity(type: ObjectServerType): Boolean = traversalIdentity(type)

    private fun traversalIdentity(type: ObjectServerType): Boolean {
        val internalName = runCatching { type.internalName }.getOrDefault("")
        val visibleName =
            type.name.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        if (visibleName != null) {
            val normalized = visibleName.lowercase()
            if (TRAVERSAL_NOUNS.any { normalized.contains(it) }) {
                return true
            }
        }
        val tokens =
            internalName.lowercase().split('.', '_', '-', ' ').filter { it.isNotBlank() }.toSet()
        return INTERNAL_TRAVERSAL_TOKENS.any { it in tokens }
    }

    private fun displayName(type: ObjectServerType, internalName: String): String =
        type.name.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
            ?: internalName

    private fun handlerRegistrations(
        type: ObjectServerType,
        option: Int,
        coords: CoordGrid,
    ): List<String> {
        val registrations = mutableListOf<String>()
        if (containsCoordinate(option, LocCoordEvents.key(type.id, coords))) {
            registrations += "exact-coordinate"
        }
        if (containsDirect(option, type.id)) registrations += "direct-object-id"
        if (type.contentGroup >= 0 && containsContent(option, type.contentGroup)) {
            registrations += "content-group:${type.contentGroup}"
        }
        if (type.category >= 0 && containsCategory(option, type.category)) {
            registrations += "category:${type.category}"
        }
        return registrations
    }

    private fun containsCoordinate(option: Int, key: Long): Boolean =
        when (option) {
            1 -> eventBus.contains(LocCoordEvents.Op1::class.java, key)
            2 -> eventBus.contains(LocCoordEvents.Op2::class.java, key)
            3 -> eventBus.contains(LocCoordEvents.Op3::class.java, key)
            4 -> eventBus.contains(LocCoordEvents.Op4::class.java, key)
            5 -> eventBus.contains(LocCoordEvents.Op5::class.java, key)
            else -> false
        }

    private fun containsDirect(option: Int, key: Int): Boolean =
        when (option) {
            1 -> eventBus.contains(LocEvents.Op1::class.java, key)
            2 -> eventBus.contains(LocEvents.Op2::class.java, key)
            3 -> eventBus.contains(LocEvents.Op3::class.java, key)
            4 -> eventBus.contains(LocEvents.Op4::class.java, key)
            5 -> eventBus.contains(LocEvents.Op5::class.java, key)
            else -> false
        }

    private fun containsContent(option: Int, key: Int): Boolean =
        when (option) {
            1 -> eventBus.contains(LocContentEvents.Op1::class.java, key)
            2 -> eventBus.contains(LocContentEvents.Op2::class.java, key)
            3 -> eventBus.contains(LocContentEvents.Op3::class.java, key)
            4 -> eventBus.contains(LocContentEvents.Op4::class.java, key)
            5 -> eventBus.contains(LocContentEvents.Op5::class.java, key)
            else -> false
        }

    private fun containsCategory(option: Int, key: Int): Boolean =
        when (option) {
            1 -> eventBus.contains(LocCategoryEvents.Op1::class.java, key)
            2 -> eventBus.contains(LocCategoryEvents.Op2::class.java, key)
            3 -> eventBus.contains(LocCategoryEvents.Op3::class.java, key)
            4 -> eventBus.contains(LocCategoryEvents.Op4::class.java, key)
            5 -> eventBus.contains(LocCategoryEvents.Op5::class.java, key)
            else -> false
        }

    private fun areaName(coords: CoordGrid): String? {
        if (safeInArea("area.ferox_enclave", coords)) return FER0X_AREA
        if (coords.mx to coords.mz in WILDERNESS_BOSS_CAVE_MAP_SQUARES) {
            return "Wilderness Boss Caves"
        }
        if (safeInArea("area.wilderness", coords)) {
            return when {
                coords.x in 1600..1910 && coords.z in 11524..11584 -> "Wilderness Boss Caves"
                coords.z in 9920..10879 -> "Wilderness Dungeons"
                else -> SURFACE_AREA
            }
        }
        return CONNECTED_MAP_SQUARES[coords.mx to coords.mz]
    }

    private fun safeInArea(area: String, coords: CoordGrid): Boolean =
        runCatching { areaChecker.inArea(area, coords) }.getOrDefault(false)

    private fun locIdentity(loc: LocInfo): String =
        "${loc.id}:${loc.coords.packed}:${loc.layer}:${loc.shapeId}:${loc.angleId}"

    private fun looksLikeTraversalId(id: Int): Boolean = id >= 0

    private data class ActionCandidate(val option: Int, val action: String)

    private companion object {
        private const val ACTIVE_REVISION = 240
        private const val SURFACE_AREA = "Wilderness Surface"
        private const val FER0X_AREA = "Ferox Enclave"
        private const val VERTICAL_HANDLER_FILE =
            "content/areas/wilderness/.../interactions/WildernessVerticalRoutes.kt"
        private const val SCOPE_DESCRIPTION =
            "All decoded loc instances on planes 0-3 in area.wilderness, Ferox Enclave, " +
                "Wilderness dungeon/boss-area children, and cache-backed KBD, Mage Arena/Bank, " +
                "and Corporeal Beast connected map squares."

        private val CONNECTED_MAP_SQUARES =
            mapOf(
                (35 to 73) to "King Black Dragon Lair",
                (39 to 73) to "Mage Arena and Mage Bank",
                (46 to 66) to "Corporeal Beast Entrance",
                (46 to 68) to "Corporeal Beast Lair",
            )
        private val WILDERNESS_BOSS_CAVE_MAP_SQUARES = setOf(25 to 180, 27 to 180, 29 to 180)

        private val STRONG_ACTIONS =
            setOf(
                "climb",
                "climb-up",
                "climb-down",
                "climb-over",
                "enter",
                "exit",
                "pass",
                "pass-through",
                "go-through",
                "squeeze-through",
                "crawl-through",
                "jump",
                "jump-over",
                "cross",
                "walk-across",
                "swing-on",
                "quick-enter",
                "quick-exit",
                "teleport",
            )

        private val WEAK_ACTIONS =
            setOf(
                "use",
                "open",
                "close",
                "unlock",
                "pick-lock",
                "slash",
                "cut",
                "pull",
                "operate",
                "pay",
            )

        private val TRAVERSAL_NOUNS =
            setOf(
                "ladder",
                "stairs",
                "staircase",
                "steps",
                "trapdoor",
                "cave",
                "entrance",
                "exit",
                "tunnel",
                "hole",
                "crevice",
                "gap",
                "rope",
                "portal",
                "lever",
                "gate",
                "door",
                "web",
                "rocks",
                "stepping stone",
                "pipe",
                "boulder",
                "shortcut",
                "obelisk",
                "canoe station",
                "wilderness ditch",
            )

        /*
         * Internal keys are tokenized so broad scenery prefixes such as "wildcave" do not turn
         * every wall and floor decoration into a traversal candidate.
         */
        private val INTERNAL_TRAVERSAL_TOKENS =
            setOf(
                "ladder",
                "stairs",
                "staircase",
                "trapdoor",
                "entrance",
                "exit",
                "tunnel",
                "crevice",
                "portal",
                "lever",
                "gate",
                "door",
                "web",
                "obelisk",
                "shortcut",
                "ditch",
            )

        private val CACHE_BLOCKED_INTERACTION_IDS = (14826..14831).toSet()
    }
}
