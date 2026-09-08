package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.table.sailing.SailingBoatRow

data class SailingBoatDefinition(
    val runtimeType: BoatType,
    val rowId: Int,
    val cacheTypeId: Int,
    val displayName: String,
    val levelRequired: Int,
    val playerCapacity: Int,
    val crewCapacity: Int,
    val boatSize: Int,
    val sizeDescription: String,
    val facilityAmount: Int,
    val retrievalCost: Int,
)

/**
 * Revision-240 boat definitions decoded from the runtime cache.
 *
 * The explicit [BoatType.cacheTypeId] and [BoatType.cacheRow] mappings are intentional: DB type ids
 * are 0/1/2 while the imported world-entity protocol ids are 1/2/3. Enum order is never used to
 * bridge those namespaces.
 */
@Singleton
class SailingBoatDefinitionRegistry @Inject constructor() {
    val all: List<SailingBoatDefinition> = BoatTypes.all.map(::decode)

    private val byRuntimeType = all.associateBy(SailingBoatDefinition::runtimeType)
    private val byCacheTypeId = all.associateBy(SailingBoatDefinition::cacheTypeId)
    private val byRowId = all.associateBy(SailingBoatDefinition::rowId)
    private val byCacheRow = all.associateBy { it.runtimeType.cacheRow }

    init {
        check(all.size == BoatTypes.all.size) { "Missing revision-240 Sailing boat definitions." }
        check(byCacheTypeId.size == all.size) { "Duplicate Sailing boat cache type id." }
        check(byRowId.size == all.size) { "Duplicate Sailing boat DB row id." }
        check(byCacheRow.size == all.size) { "Duplicate Sailing boat DB row symbol." }
    }

    operator fun get(type: BoatType): SailingBoatDefinition =
        checkNotNull(byRuntimeType[type]) { "Unregistered Sailing boat type `${type.key}`." }

    fun byCacheTypeId(typeId: Int): SailingBoatDefinition? = byCacheTypeId[typeId]

    fun byRowId(rowId: Int): SailingBoatDefinition? = byRowId[rowId]

    fun byCacheRow(cacheRow: String): SailingBoatDefinition? = byCacheRow[cacheRow]

    private fun decode(type: BoatType): SailingBoatDefinition {
        val row = SailingBoatRow.getRow(type.cacheRow)
        check(row.typeId == type.cacheTypeId) {
            "Sailing boat `${type.key}` expected cache type ${type.cacheTypeId}, found ${row.typeId}."
        }
        check(row.name.equals(type.key, ignoreCase = true)) {
            "Sailing boat row ${row.rowId} is `${row.name}`, expected `${type.key}`."
        }
        check(row.levelRequired >= 1) { "Invalid Sailing level in boat row ${row.rowId}." }
        check(row.playerCapacity >= 1) { "Invalid player capacity in boat row ${row.rowId}." }
        val crewCapacity =
            checkNotNull(row.crewCapacity) { "Missing crew capacity in boat row ${row.rowId}." }
        check(crewCapacity >= 0) { "Invalid crew capacity in boat row ${row.rowId}." }
        val facilityAmount =
            checkNotNull(row.facilityAmount) { "Missing facility amount in boat row ${row.rowId}." }
        check(facilityAmount >= 1) { "Invalid facility amount in boat row ${row.rowId}." }
        check(row.retrievalCost >= 0) { "Invalid retrieval cost in boat row ${row.rowId}." }
        return SailingBoatDefinition(
            runtimeType = type,
            rowId = row.rowId,
            cacheTypeId = row.typeId,
            displayName = row.name,
            levelRequired = row.levelRequired,
            playerCapacity = row.playerCapacity,
            crewCapacity = crewCapacity,
            boatSize = row.boatSize,
            sizeDescription = row.sizeDesc,
            facilityAmount = facilityAmount,
            retrievalCost = row.retrievalCost,
        )
    }
}
