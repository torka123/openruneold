package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.table.sailing.SailingDockRow

enum class SailingDockInteractionKind {
    Gangplank,
    Mooring,
}

data class SailingDockDefinition(
    val dockId: Int,
    val rowId: Int,
    val tableId: Int,
    val displayName: String,
    val inlineName: String?,
    val levelRequired: Int,
    val questRequirementRowId: Int?,
    val smallSpriteId: Int,
    val rowKey: String,
    val interactionKind: SailingDockInteractionKind,
    val parentLocId: Int,
    val parentLocKey: String,
    val buoyLocId: Int,
    val buoyLocKey: String,
)

/**
 * Revision-240 dock metadata decoded from `dbtable.sailing_dock` (table 194).
 *
 * This registry intentionally contains no root-world positions. The decoded table proves names,
 * level/quest requirements, and sprites, but it does not populate the native destination, anchor,
 * in-zone, or rotation fields. Those coordinates remain a separate capture-backed concern.
 */
@Singleton
class SailingDockDefinitionRegistry @Inject constructor() {
    val all: List<SailingDockDefinition> =
        SailingDockRow.all().map(::decode).sortedBy(SailingDockDefinition::dockId)

    private val byDockId = all.associateBy(SailingDockDefinition::dockId)
    private val byRowId = all.associateBy(SailingDockDefinition::rowId)
    private val byRowKey = all.associateBy(SailingDockDefinition::rowKey)
    private val byParentLocId = all.associateBy(SailingDockDefinition::parentLocId)
    private val byBuoyLocId = all.associateBy(SailingDockDefinition::buoyLocId)
    private val byNormalizedName = all.groupBy { normalizeName(it.displayName) }

    init {
        check(all.size == EXPECTED_DOCK_COUNT) {
            "Expected $EXPECTED_DOCK_COUNT revision-240 Sailing docks, found ${all.size}."
        }
        check(all.map(SailingDockDefinition::dockId) == EXPECTED_DOCK_IDS.toList()) {
            "Revision-240 Sailing dock ids must be exactly ${EXPECTED_DOCK_IDS.first}..${EXPECTED_DOCK_IDS.last}."
        }
        check(byDockId.size == all.size) { "Duplicate Sailing dock id." }
        check(byRowId.size == all.size) { "Duplicate Sailing dock DB row id." }
        check(byRowKey.size == all.size) { "Duplicate Sailing dock DB row key." }
        check(byParentLocId.size == all.size) { "Duplicate Sailing dock parent loc id." }
        check(byBuoyLocId.size == all.size) { "Duplicate Sailing dock buoy loc id." }
    }

    fun byDockId(dockId: Int): SailingDockDefinition? = byDockId[dockId]

    fun byRowId(rowId: Int): SailingDockDefinition? = byRowId[rowId]

    fun byRowKey(rowKey: String): SailingDockDefinition? = byRowKey[rowKey]

    fun byParentLocId(parentLocId: Int): SailingDockDefinition? = byParentLocId[parentLocId]

    fun byBuoyLocId(buoyLocId: Int): SailingDockDefinition? = byBuoyLocId[buoyLocId]

    fun named(name: String): List<SailingDockDefinition> =
        byNormalizedName[normalizeName(name)].orEmpty()

    private fun decode(row: SailingDockRow): SailingDockDefinition {
        check(row.tableId == TABLE_ID) {
            "Sailing dock row ${row.rowId} belongs to table ${row.tableId}, expected $TABLE_ID."
        }
        check(row.dockId in EXPECTED_DOCK_IDS) {
            "Invalid Sailing dock id ${row.dockId} in row ${row.rowId}."
        }
        check(row.niceName.isNotBlank()) { "Missing name in Sailing dock row ${row.rowId}." }
        val inlineName = row.inlineName
        check(inlineName == null || inlineName.isNotBlank()) {
            "Blank inline name in Sailing dock row ${row.rowId}."
        }
        check(row.levelRequired in MIN_LEVEL..MAX_LEVEL) {
            "Invalid Sailing level ${row.levelRequired} in dock row ${row.rowId}."
        }
        check(row.dockSpriteSmall >= 0) {
            "Invalid small sprite ${row.dockSpriteSmall} in dock row ${row.rowId}."
        }
        val rowKey = RSCM.getReverseMapping(RSCMType.DBROW, row.rowId)
        check(rowKey.startsWith(ROW_KEY_PREFIX)) {
            "Unexpected Sailing dock row key `$rowKey` for row ${row.rowId}."
        }
        val suffix = rowKey.removePrefix(ROW_KEY_PREFIX)
        check(suffix.isNotBlank()) { "Missing Sailing dock row suffix for row ${row.rowId}." }
        val interactionKind =
            if (row.dockId in MOORING_DOCK_IDS) {
                SailingDockInteractionKind.Mooring
            } else {
                SailingDockInteractionKind.Gangplank
            }
        val parentPrefix =
            when (interactionKind) {
                SailingDockInteractionKind.Gangplank -> GANGPLANK_KEY_PREFIX
                SailingDockInteractionKind.Mooring -> MOORING_KEY_PREFIX
            }
        val parentLocKey = "$parentPrefix$suffix"
        val parentLocId = parentLocKey.asRSCM(RSCMType.LOC)
        val parent =
            requireNotNull(ServerCacheManager.getObject(parentLocId)) {
                "Missing Sailing dock parent `$parentLocKey` for dock ${row.dockId}."
            }
        validateParent(
            row.dockId,
            parentLocKey,
            interactionKind,
            parent.width,
            parent.length,
            parent.multiVarBit,
            parent.multiLoc.toList(),
        )
        val buoyLocKey = "$BUOY_KEY_PREFIX$suffix"
        val buoyLocId = buoyLocKey.asRSCM(RSCMType.LOC)
        requireNotNull(ServerCacheManager.getObject(buoyLocId)) {
            "Missing Sailing dock buoy `$buoyLocKey` for dock ${row.dockId}."
        }
        return SailingDockDefinition(
            dockId = row.dockId,
            rowId = row.rowId,
            tableId = row.tableId,
            displayName = row.niceName,
            inlineName = inlineName,
            levelRequired = row.levelRequired,
            questRequirementRowId = row.questRequired?.rowId,
            smallSpriteId = row.dockSpriteSmall,
            rowKey = rowKey,
            interactionKind = interactionKind,
            parentLocId = parentLocId,
            parentLocKey = parentLocKey,
            buoyLocId = buoyLocId,
            buoyLocKey = buoyLocKey,
        )
    }

    private fun validateParent(
        dockId: Int,
        parentLocKey: String,
        interactionKind: SailingDockInteractionKind,
        width: Int,
        length: Int,
        multiVarBit: Int,
        transforms: List<Int>,
    ) {
        val (expectedSize, embarkKey, disembarkKey) =
            when (interactionKind) {
                SailingDockInteractionKind.Gangplank ->
                    Triple(1, GANGPLANK_EMBARK_KEY, GANGPLANK_DISEMBARK_KEY)
                SailingDockInteractionKind.Mooring ->
                    Triple(3, MOORING_EMBARK_KEY, MOORING_DISEMBARK_KEY)
            }
        val embark = embarkKey.asRSCM(RSCMType.LOC)
        val disembark = disembarkKey.asRSCM(RSCMType.LOC)
        check(width == expectedSize && length == expectedSize) {
            "Sailing dock $dockId parent `$parentLocKey` is ${width}x$length, expected ${expectedSize}x$expectedSize."
        }
        check(multiVarBit == ABOARD_PLAYER_BOAT_VARBIT) {
            "Sailing dock $dockId parent `$parentLocKey` uses varbit $multiVarBit, expected $ABOARD_PLAYER_BOAT_VARBIT."
        }
        check(transforms == listOf(embark, disembark, embark)) {
            "Sailing dock $dockId parent `$parentLocKey` has transforms $transforms, expected ${listOf(embark, disembark, embark)}."
        }
    }

    private fun normalizeName(name: String): String = name.trim().lowercase()

    companion object {
        const val TABLE_ID: Int = 194
        const val EXPECTED_DOCK_COUNT: Int = 61
        val EXPECTED_DOCK_IDS: IntRange = 0 until EXPECTED_DOCK_COUNT

        private const val ROW_KEY_PREFIX = "dbrow.sailing_dock_"
        private const val GANGPLANK_KEY_PREFIX = "loc.sailing_gangplank_"
        private const val MOORING_KEY_PREFIX = "loc.sailing_mooring_"
        private const val BUOY_KEY_PREFIX = "loc.sailing_docking_buoy_"
        private const val GANGPLANK_EMBARK_KEY = "loc.sailing_gangplank_embark"
        private const val GANGPLANK_DISEMBARK_KEY = "loc.sailing_gangplank_disembark"
        private const val MOORING_EMBARK_KEY = "loc.sailing_mooring_embark"
        private const val MOORING_DISEMBARK_KEY = "loc.sailing_mooring_disembark"
        private const val ABOARD_PLAYER_BOAT_VARBIT = 19104
        private val MOORING_DOCK_IDS = 32..58

        private const val MIN_LEVEL = 1
        private const val MAX_LEVEL = 99
    }
}
