package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.api.table.PohRoomRow
import org.rsmod.content.skills.construction.data.DonorConstructionData

class ConstructionCatalogAuditOutputTest {
    @Test
    fun `export exhaustive revision 240 POH menu comparison`() {
        val buildables = DonorConstructionData.buildables.associateBy { it.name }
        val hotspots = DonorConstructionData.hotspots.associateBy { it.name }
        val rooms = DonorConstructionData.rooms.associateBy { it.roomId }
        var total = 0
        var exact = 0
        var wrongOrder = 0
        var unresolved = 0
        println(
            "AUDIT_HEADER\troom_type\troom\thotspot_index\thotspot_row\tmenu_order\tfurniture_row\tname\tlevel\txp\tmaterials\ticon_item\tbuilt_objects\thotspot_objects\tinternal_key\tstatus"
        )
        val pohRoomTable = RSCM.getRSCM("dbtable.poh_room")
        val cacheRooms =
            ServerCacheManager.getRows()
                .values
                .asSequence()
                .filter { it.tableId == pohRoomTable }
                .map { PohRoomRow.getRow(it.id) }
                .sortedBy { it.roomType }
        cacheRooms
            .filter { it.roomType > 0 }
            .forEach { cacheRoom ->
                val localRoom = rooms[cacheRoom.roomType]
                val localCandidates =
                    localRoom?.hotspots.orEmpty().mapNotNull { key ->
                        val hotspot = hotspots[key] ?: return@mapNotNull null
                        val entries =
                            hotspot.buildables.mapNotNull { buildKey ->
                                val buildable = buildables[buildKey] ?: return@mapNotNull null
                                ConstructionFurnitureMenuCatalog.resolve(buildable)?.let {
                                    it.furnitureRowId to buildable
                                }
                            }
                        Triple(key, hotspot, entries)
                    }
                cacheRoom.hotspot.forEachIndexed { hotspotIndex, cacheHotspot ->
                    val visibleRows = cacheHotspot.builddata.filter { it.hiddenInBuildMenu == null }
                    val visibleIds = visibleRows.map { it.rowId }.toSet()
                    val bestCandidate =
                        localCandidates.maxByOrNull { (_, _, entries) ->
                            entries.count { (rowId, _) -> rowId in visibleIds }
                        }
                    val bestScore =
                        bestCandidate?.third?.count { (rowId, _) -> rowId in visibleIds } ?: 0
                    val fallbackKey = localRoom?.hotspots?.getOrNull(hotspotIndex)
                    val directCandidate = localCandidates.firstOrNull { it.first == fallbackKey }
                    val directScore =
                        directCandidate?.third?.count { (rowId, _) -> rowId in visibleIds } ?: 0
                    val selected =
                        if (directScore > 0) directCandidate
                        else if (bestScore > 0) bestCandidate else directCandidate
                    val localHotspot = selected?.second
                    val localEntries = selected?.third.orEmpty()
                    visibleRows.forEachIndexed { menuIndex, row ->
                        total++
                        val anyEntry = localEntries.firstOrNull { it.first == row.rowId }
                        val supportedPosition =
                            visibleRows.take(menuIndex + 1).count { candidate ->
                                localEntries.any { it.first == candidate.rowId }
                            } - 1
                        val orderedEntry =
                            localEntries.getOrNull(supportedPosition)?.takeIf {
                                it.first == row.rowId
                            }
                        val mapped = orderedEntry ?: anyEntry
                        val status =
                            when {
                                orderedEntry != null -> {
                                    exact++
                                    when (orderedEntry.second.name) {
                                        in newlyAdded -> "NEWLY_ADDED"
                                        in requirementCorrected -> "EXISTING_REQUIREMENTS_CORRECTED"
                                        in levelCorrected -> "EXISTING_LEVEL_CORRECTED"
                                        else -> "VERIFIED_EXISTING"
                                    }
                                }
                                anyEntry != null -> {
                                    wrongOrder++
                                    "EXISTING_MENU_ORDER_UNRESOLVED"
                                }
                                else -> {
                                    unresolved++
                                    "UNRESOLVED"
                                }
                            }
                        val buildable = mapped?.second
                        val level = row.levelRequirement.firstOrNull { it.t0.id == 22 }?.t1 ?: 0
                        val materials =
                            row.materialCost.joinToString(";") { "${it.t0.id}:${it.t1}" }
                        println(
                            listOf(
                                    "AUDIT",
                                    cacheRoom.roomType,
                                    clean(cacheRoom.name),
                                    hotspotIndex,
                                    cacheHotspot.rowId,
                                    menuIndex + 1,
                                    row.rowId,
                                    clean(row.name),
                                    level,
                                    buildable?.experience ?: "",
                                    materials,
                                    row.modelObj.id,
                                    buildable?.builtLocIds?.joinToString(";") ?: "",
                                    localHotspot?.locIds?.joinToString(";") ?: "",
                                    buildable?.name ?: "",
                                    status,
                                )
                                .joinToString("\t")
                        )
                    }
                }
            }
        println("AUDIT_TOTALS\t$total\t$exact\t$wrongOrder\t$unresolved")
        assertEquals(770, total, "Pinned revision-240 visible POH menu rows changed")
        assertEquals(769, exact, "Resolved POH menu rows changed")
        assertEquals(0, wrongOrder, "Local POH menu order differs from revision 240")
        assertEquals(1, unresolved, "Unresolved POH menu rows changed")
    }

    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ').trim()

    companion object {
        private val newlyAdded =
            setOf(
                "GOLD_SINK",
                "SPICE_RACK",
                "ORNATE_UNDEAD_COMBAT_DUMMY",
                "TEAK_HEAD_DISPLAY",
                "MAHOGANY_HEAD_DISPLAY",
                "GILDED_HEAD_DISPLAY",
                "OAK_FISHING_DISPLAY",
                "TEAK_FISHING_DISPLAY",
                "MAHOGANY_FISHING_DISPLAY",
                "STASH_CHART",
                "MARBLE_PORTAL_NEXUS",
                "GILDED_PORTAL_NEXUS",
                "CRYSTALLINE_PORTAL_NEXUS",
                "MOUNTED_XERICS_TALISMAN",
                "MOUNTED_DIGSITE_PENDANT",
                "LEAGUE_TROPHY_PEDESTAL_1",
                "LEAGUE_ORNATE_TROPHY_PEDESTAL_1",
                "LEAGUE_TROPHY_PEDESTAL_2",
                "LEAGUE_ORNATE_TROPHY_PEDESTAL_2",
                "LEAGUE_TROPHY_PEDESTAL_3",
                "LEAGUE_ORNATE_TROPHY_PEDESTAL_3",
                "LEAGUE_RUG",
                "LEAGUE_OPULENT_RUG",
                "OAK_TROPHY_CASE",
                "MAHOGANY_TROPHY_CASE",
                "LEAGUE_BANNER_STAND",
                "ORNATE_LEAGUE_BANNER_STAND",
                "OAK_OUTFIT_STAND",
                "MAHOGANY_OUTFIT_STAND",
                "LEAGUE_STATUE",
                "ORNATE_LEAGUE_STATUE",
                "TRAILBLAZER_GLOBE",
                "LEAGUE_ACCOMPLISHMENTS_SCROLL",
                "QUEST_LIST",
                "DEADMAN_RUG",
                "RAGING_ECHOES_RUG",
                "HALL_DEADMAN_RUG",
                "HALL_RAGING_ECHOES_RUG",
                "RAGING_ECHOES_CURTAINS",
                "GNOME_CHILD_ICON",
                "GRID_MASTER_ICON",
                "MARBLE_LECTERN",
                "PUMPKIN_GARDEN_1",
                "PUMPKIN_GARDEN_2",
                "PUMPKIN_FORMAL_GARDEN",
                "BEEHIVE_STYLE_1_GARDEN_1",
                "BEEHIVE_STYLE_1_GARDEN_2",
                "BEEHIVE_STYLE_1_FORMAL_GARDEN",
                "BEEHIVE_STYLE_2_GARDEN_1",
                "BEEHIVE_STYLE_2_GARDEN_2",
                "BEEHIVE_STYLE_2_FORMAL_GARDEN",
                "GREENMAN_CARVING",
                "GREENMAN_STATUE_CENTREPIECE",
                "GREENMAN_STATUE_BIG_PLANT_1",
                "GREENMAN_STATUE_BIG_PLANT_2",
                "ANNIHILATION_EXIT_PORTAL",
            )
        private val requirementCorrected =
            setOf(
                "ANCIENT_ALTAR",
                "BASIC_JEWELLERY_BOX",
                "CAT_BLANKET",
                "COMBAT_RING",
                "CW_ARMOUR_1",
                "CW_ARMOUR_2",
                "CW_ARMOUR_3",
                "DAGANNOTH",
                "DARK_ALTAR",
                "DEMON",
                "DESECRATED_HOUSE",
                "FLAME_PIT",
                "GILDED_ALTAR",
                "GILDED_CLOCK",
                "GREATER_MAGICAL_BALANCE",
                "KALPHITE_SOLDIER",
                "LESSER_MAGICAL_BALANCE",
                "MAHOGANY_DEMON_LECTERN",
                "MAHOGANY_EAGLE_LECTERN",
                "MAHOGANY_FEEDER",
                "MEDIUM_MAGICAL_BALANCE",
                "MOUNTED_EMBLEM",
                "OBSIDIAN_BENCH",
                "OPULENT_DINING_TABLE",
                "ORNATE_REJUVENATION_POOL",
                "POLAR_HABITAT",
                "POND",
                "RESTORATION_POOL",
                "RUNE_CASE_1",
                "RUNE_CASE_2",
                "RUNE_CASE_3",
                "RUNE_DRAGON",
                "SCRYING_POOL",
                "STEEL_DRAGON",
                "TEAK_DRESSER",
                "TEAK_FEEDER",
                "TOK_XIL",
                "VOLCANIC_HABITAT",
                "VOLCANIC_THEME",
            )
        private val levelCorrected = setOf("MARBLE_CAPE_RACK", "MAGICAL_CAPE_RACK", "REDWOOD_FENCE")

        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$root${java.io.File.separator}")
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
