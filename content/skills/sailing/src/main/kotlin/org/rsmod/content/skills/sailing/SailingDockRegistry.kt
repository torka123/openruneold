package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.map.CoordGrid

data class SailingDockPlacement(
    val coords: CoordGrid,
    val locId: Int,
    val locKey: String,
    val shape: Int,
    val angle: Int,
    val layer: Int,
)

data class SailingDockEntry(
    val definition: SailingDockDefinition,
    val interactionPlacements: List<SailingDockPlacement>,
    val buoyPlacements: List<SailingDockPlacement>,
    val operational: SailingDockOperationalGeometry,
)

/**
 * Immutable revision-240 Sailing dock snapshot.
 *
 * DB metadata and symbolic loc identity come from [SailingDockDefinitionRegistry]. Placements are
 * decoded from the authoritative static root-world map. Travel coordinates deliberately remain a
 * separate capture-backed field; no parent or buoy tile is promoted into vessel geometry.
 */
@Singleton
class SailingDockRegistry
@Inject
internal constructor(definitions: SailingDockDefinitionRegistry, locZones: LocZoneStorage) {
    val all: List<SailingDockEntry> = buildEntries(definitions.all, locZones)

    private val byDockId = all.associateBy { it.definition.dockId }
    private val byRowId = all.associateBy { it.definition.rowId }
    private val byRowKey = all.associateBy { it.definition.rowKey }
    private val byParentLocId = all.associateBy { it.definition.parentLocId }
    private val byBuoyLocId = all.associateBy { it.definition.buoyLocId }
    private val byInteractionCoord = indexPlacements(all, SailingDockEntry::interactionPlacements)
    private val byBuoyCoord = indexPlacements(all, SailingDockEntry::buoyPlacements)

    init {
        check(all.size == SailingDockDefinitionRegistry.EXPECTED_DOCK_COUNT) {
            "Expected ${SailingDockDefinitionRegistry.EXPECTED_DOCK_COUNT} Sailing dock entries, found ${all.size}."
        }
        check(byDockId.size == all.size) { "Duplicate Sailing dock id in placement registry." }
        check(byRowId.size == all.size) { "Duplicate Sailing dock row id in placement registry." }
        check(byRowKey.size == all.size) { "Duplicate Sailing dock row key in placement registry." }
        check(byParentLocId.size == all.size) {
            "Duplicate Sailing dock parent loc id in placement registry."
        }
        check(byBuoyLocId.size == all.size) {
            "Duplicate Sailing dock buoy loc id in placement registry."
        }
        check(all.sumOf { it.interactionPlacements.size } == EXPECTED_PARENT_PLACEMENTS) {
            "Expected $EXPECTED_PARENT_PLACEMENTS Sailing parent placements."
        }
        check(all.sumOf { it.buoyPlacements.size } == EXPECTED_BUOY_PLACEMENTS) {
            "Expected $EXPECTED_BUOY_PLACEMENTS Sailing buoy placements."
        }
    }

    fun byDockId(dockId: Int): SailingDockEntry? = byDockId[dockId]

    fun byRowId(rowId: Int): SailingDockEntry? = byRowId[rowId]

    fun byRowKey(rowKey: String): SailingDockEntry? = byRowKey[rowKey]

    fun byParentLocId(parentLocId: Int): SailingDockEntry? = byParentLocId[parentLocId]

    fun byBuoyLocId(buoyLocId: Int): SailingDockEntry? = byBuoyLocId[buoyLocId]

    fun byInteractionCoord(coords: CoordGrid): SailingDockEntry? = byInteractionCoord[coords]

    fun byBuoyCoord(coords: CoordGrid): SailingDockEntry? = byBuoyCoord[coords]

    private fun buildEntries(
        definitions: List<SailingDockDefinition>,
        locZones: LocZoneStorage,
    ): List<SailingDockEntry> {
        val targetIds = definitions.flatMapTo(hashSetOf()) { listOf(it.parentLocId, it.buoyLocId) }
        val placements = scanStaticPlacements(locZones, targetIds).groupBy(StaticPlacement::locId)
        return definitions.sortedBy(SailingDockDefinition::dockId).map { definition ->
            val parent =
                placements[definition.parentLocId]
                    .orEmpty()
                    .map { it.toPlacement(definition.parentLocKey) }
                    .sortedWith(PLACEMENT_ORDER)
            val buoys =
                placements[definition.buoyLocId]
                    .orEmpty()
                    .map { it.toPlacement(definition.buoyLocKey) }
                    .sortedWith(PLACEMENT_ORDER)
            check(parent.isNotEmpty()) {
                "No static parent placement for Sailing dock ${definition.dockId} `${definition.parentLocKey}`."
            }
            check(buoys.isNotEmpty()) {
                "No static buoy placement for Sailing dock ${definition.dockId} `${definition.buoyLocKey}`."
            }
            SailingDockEntry(
                definition = definition,
                interactionPlacements = parent,
                buoyPlacements = buoys,
                operational = SailingDockOperationalGeometry.UNCERTIFIED,
            )
        }
    }

    private fun scanStaticPlacements(
        locZones: LocZoneStorage,
        targetIds: Set<Int>,
    ): List<StaticPlacement> {
        val found = mutableListOf<StaticPlacement>()
        for (zone in locZones.mapLocs.zoneKeys().sortedBy { it.packed }) {
            val zoneLocs = locZones.mapLocs[zone] ?: continue
            val zoneCoords = zone.toCoords()
            for (entry in zoneLocs.byte2IntEntrySet()) {
                val entity = LocEntity(entry.intValue)
                if (entity.id !in targetIds) {
                    continue
                }
                val key = LocZoneKey(entry.byteKey)
                found +=
                    StaticPlacement(
                        coords = zoneCoords.translate(key.x, key.z),
                        locId = entity.id,
                        shape = entity.shape,
                        angle = entity.angle,
                        layer = key.layer,
                    )
            }
        }
        return found
    }

    private fun indexPlacements(
        entries: List<SailingDockEntry>,
        selector: (SailingDockEntry) -> List<SailingDockPlacement>,
    ): Map<CoordGrid, SailingDockEntry> {
        val index = linkedMapOf<CoordGrid, SailingDockEntry>()
        for (entry in entries) {
            for (placement in selector(entry)) {
                val previous = index.put(placement.coords, entry)
                check(previous == null) {
                    "Ambiguous Sailing dock placement at ${placement.coords}: docks ${previous?.definition?.dockId} and ${entry.definition.dockId}."
                }
            }
        }
        return index
    }

    private data class StaticPlacement(
        val coords: CoordGrid,
        val locId: Int,
        val shape: Int,
        val angle: Int,
        val layer: Int,
    ) {
        fun toPlacement(locKey: String): SailingDockPlacement =
            SailingDockPlacement(coords, locId, locKey, shape, angle, layer)
    }

    companion object {
        const val EXPECTED_PARENT_PLACEMENTS = 64
        const val EXPECTED_BUOY_PLACEMENTS = 62

        private val PLACEMENT_ORDER =
            compareBy<SailingDockPlacement>(
                { it.coords.level },
                { it.coords.x },
                { it.coords.z },
                SailingDockPlacement::layer,
                SailingDockPlacement::shape,
                SailingDockPlacement::angle,
            )
    }
}
