package org.rsmod.api.instances

import dev.openrune.types.NpcServerType
import org.rsmod.api.instances.region.regionIdsGrid
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.map.CoordGrid

public const val INSTANCE_TICKS_PER_MINUTE: Int = 100

public const val EMPTY_INSTANCE_RECLAIM_MINUTES: Int = 20

public const val DEFAULT_EMPTY_INSTANCE_RECLAIM_TICKS: Int =
    EMPTY_INSTANCE_RECLAIM_MINUTES * INSTANCE_TICKS_PER_MINUTE

public const val INSTANCE_GRACE_MINUTES: Int = 10

public const val DEFAULT_INSTANCE_GRACE_TICKS: Int =
    INSTANCE_GRACE_MINUTES * INSTANCE_TICKS_PER_MINUTE

public const val INSTANCE_KILL_TIMER_MAX_PLAYERS: Int = 5

public const val INSTANCE_KILL_TIMER_MAX_TICKS: Int = 60 * INSTANCE_TICKS_PER_MINUTE

public const val INSTANCE_ARENA_EXPIRED_MESSAGE: String =
    "This arena has now expired and no further bosses will spawn."

public data class RegionLocal(
    val level: Int,
    val regionZoneX: Int,
    val regionZoneZ: Int,
    val localX: Int,
    val localZ: Int,
)

public data class InstanceNpc(val npcType: String, val coord: RegionLocal) {
    public constructor(
        npcType: String,
        coord: CoordGrid,
    ) : this(
        npcType,
        RegionLocal(
            level = coord.level,
            regionZoneX = coord.mx,
            regionZoneZ = coord.mz,
            localX = coord.lx,
            localZ = coord.lz,
        ),
    )
}

internal fun RegionLocal.worldCoord(): CoordGrid =
    CoordGrid(level, regionZoneX, regionZoneZ, localX, localZ)

@Deprecated("Use InstanceArea.Template via InstanceArea.template")
public data class InstanceTemplate(
    val template: RegionStaticTemplate,
    val enterCoord: RegionLocal,
    val exitCoord: CoordGrid,
    val npcSpawns: List<InstanceNpc>,
) {
    public fun toArea(): InstanceArea.Template =
        InstanceArea.Template(
            template = template,
            enterCoord = enterCoord,
            exitCoord = exitCoord,
            npcSpawns = npcSpawns,
        )
}

public sealed class InstanceArea {
    public abstract val npcSpawns: List<InstanceNpc>

    /** A server-owned session on canonical map tiles. The north-east corner is exclusive. */
    public data class Fixed(
        val southWest: CoordGrid,
        val northEast: CoordGrid,
        val enterCoord: RegionLocal,
        val exitCoord: CoordGrid,
        override val npcSpawns: List<InstanceNpc> = emptyList(),
    ) : InstanceArea() {
        init {
            require(southWest.level == northEast.level)
            require(southWest.x < northEast.x && southWest.z < northEast.z)
            require(!RegionRegistry.inWorkingArea(northEast.translate(-1, -1)))
            require(contains(enterCoord.worldCoord())) { "Fixed entry must be inside its area" }
            require(npcSpawns.all { contains(it.coord.worldCoord()) }) {
                "Fixed NPC spawn anchors must be inside their area"
            }
        }

        public fun contains(coords: CoordGrid): Boolean =
            coords.level == southWest.level && isWithinInstanceBounds(coords, southWest, northEast)

        internal fun overlaps(other: Fixed): Boolean =
            southWest.level == other.southWest.level &&
                southWest.x < other.northEast.x &&
                other.southWest.x < northEast.x &&
                southWest.z < other.northEast.z &&
                other.southWest.z < northEast.z
    }

    public data class Template(
        val template: RegionStaticTemplate,
        val enterCoord: RegionLocal,
        val exitCoord: CoordGrid,
        override val npcSpawns: List<InstanceNpc> = emptyList(),
    ) : InstanceArea()

    public data class CopyRegions(
        val regionIds: List<Int>,
        val level: Int = 0,
        val rotation: InstanceRegionRotation = InstanceRegionRotation.NONE,
        val enterCoord: RegionLocal,
        val exitCoord: CoordGrid,
        override val npcSpawns: List<InstanceNpc> = emptyList(),
        val copyAllLevels: Boolean = false,
    ) : InstanceArea()

    public companion object {

        private val EMPTY_ENTER = RegionLocal(0, 0, 0, 0, 0)

        public fun fixed(
            southWest: CoordGrid,
            northEast: CoordGrid,
            enterCoord: CoordGrid,
            exitCoord: CoordGrid,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): Fixed =
            Fixed(
                southWest,
                northEast,
                RegionLocal(
                    enterCoord.level,
                    enterCoord.mx,
                    enterCoord.mz,
                    enterCoord.lx,
                    enterCoord.lz,
                ),
                exitCoord,
                npcSpawns,
            )

        public fun template(
            template: RegionStaticTemplate,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): Template =
            Template(
                template = template,
                enterCoord = EMPTY_ENTER,
                exitCoord = CoordGrid.ZERO,
                npcSpawns = npcSpawns,
            )

        public fun template(
            template: RegionStaticTemplate,
            enterCoord: RegionLocal,
            exitCoord: CoordGrid,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): Template = Template(template, enterCoord, exitCoord, npcSpawns)

        public fun copyRegions(
            regionIds: List<Int>,
            level: Int = 0,
            rotation: InstanceRegionRotation = InstanceRegionRotation.NONE,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): CopyRegions =
            CopyRegions(regionIds, level, rotation, EMPTY_ENTER, CoordGrid.ZERO, npcSpawns)

        public fun copyRegions(
            regionIds: List<Int>,
            level: Int = 0,
            rotation: InstanceRegionRotation = InstanceRegionRotation.NONE,
            enterCoord: RegionLocal,
            exitCoord: CoordGrid,
            npcSpawns: List<InstanceNpc> = emptyList(),
            copyAllLevels: Boolean = false,
        ): CopyRegions =
            CopyRegions(regionIds, level, rotation, enterCoord, exitCoord, npcSpawns, copyAllLevels)

        public fun copyRegions(
            centerRegionId: Int,
            gridSize: Int = 1,
            level: Int = 0,
            rotation: InstanceRegionRotation = InstanceRegionRotation.NONE,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): CopyRegions =
            copyRegions(
                regionIds = regionIdsGrid(centerRegionId, gridSize),
                level = level,
                rotation = rotation,
                npcSpawns = npcSpawns,
            )

        public fun copyRegions(
            centerRegionId: Int,
            gridSize: Int,
            level: Int = 0,
            rotation: InstanceRegionRotation = InstanceRegionRotation.NONE,
            enterCoord: RegionLocal,
            exitCoord: CoordGrid,
            npcSpawns: List<InstanceNpc> = emptyList(),
        ): CopyRegions =
            copyRegions(
                regionIds = regionIdsGrid(centerRegionId, gridSize),
                level = level,
                rotation = rotation,
                enterCoord = enterCoord,
                exitCoord = exitCoord,
                npcSpawns = npcSpawns,
            )
    }
}

public data class InstanceSettings(
    val fee: Int = 0,
    val maxPlayers: Int = 1,
    val reclaimTicks: Int = DEFAULT_EMPTY_INSTANCE_RECLAIM_TICKS,
    val graceTicks: Int = DEFAULT_INSTANCE_GRACE_TICKS,
    val destroyWhenEmpty: Boolean = false,
    val bossNpc: List<NpcServerType>? = null,
    val bossName: String = "Boss",
    val recommendedCombat: IntRange? = null,
    val teamSize: Int = 1,
    val lootMultiplier: String = "x1.0",
    val description: String = "",
    val timeLimitTicks: Int? = null,
    val spawnOnFirstJoin: Boolean = false,
    /** Whether this instance may update account-backed kill timers and personal-best events. */
    val recordsEligible: Boolean = true,
) {
    init {
        require((recommendedCombat?.first ?: 0) <= (recommendedCombat?.last ?: 0)) {
            "recommendedCombat range must be ascending: $recommendedCombat"
        }
    }

    public fun withArea(area: InstanceArea, settingsRowId: Int): InstanceSpec =
        InstanceSpec(
            fee = fee,
            maxPlayers = maxPlayers,
            reclaimTicks = reclaimTicks,
            graceTicks = graceTicks,
            destroyWhenEmpty = destroyWhenEmpty,
            area = area,
            settingsRowId = settingsRowId,
            bossNpcs = bossNpc,
            bossName = bossName,
            recommendedCombat = recommendedCombat,
            teamSize = teamSize,
            lootMultiplier = lootMultiplier,
            description = description,
            timeLimitTicks = timeLimitTicks,
            spawnOnFirstJoin = spawnOnFirstJoin,
            recordsEligible = recordsEligible,
        )
}

public data class InstanceSpec(
    val fee: Int,
    val maxPlayers: Int,
    val reclaimTicks: Int,
    val graceTicks: Int,
    val destroyWhenEmpty: Boolean = false,
    val area: InstanceArea,
    val settingsRowId: Int,
    val bossNpcs: List<NpcServerType>? = null,
    val bossName: String = "Boss",
    val recommendedCombat: IntRange? = null,
    val teamSize: Int = 1,
    val lootMultiplier: String = "x1.0",
    val description: String = "",
    val timeLimitTicks: Int? = null,
    val spawnOnFirstJoin: Boolean = false,
    val recordsEligible: Boolean = true,
)

public sealed interface InstanceAccess {
    public data object Private : InstanceAccess

    public data object Friends : InstanceAccess

    public data class Code(val value: String) : InstanceAccess
}

public sealed interface SessionState {
    public data object Active : SessionState

    public data class Reclaim(val deadlineTick: Int) : SessionState

    public data class Grace(val deadlineTick: Int) : SessionState
}
