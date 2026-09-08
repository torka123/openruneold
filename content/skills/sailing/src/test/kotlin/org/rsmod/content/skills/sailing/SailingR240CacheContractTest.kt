package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.StatComponentsRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.ownership.SailingOwnedBoat
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipState
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipValidator
import org.rsmod.game.entity.Player

@ResourceLock("sailing-server-cache")
class SailingR240CacheContractTest {
    @Test
    fun `core Sailing contracts match revision 240`() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
        val liveCache = ServerCacheManager.init(root.resolve(".data/cache/LIVE"), REVISION)
        try {
            assertNativeStatMapping()
            assertNativeInterfaces()
            assertCoreVars()
            assertBoatDefinitions()
            assertBoatRegistry()
        } finally {
            liveCache.close()
        }

        val serverCache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), REVISION)
        try {
            assertRuntimeStatDefinition()
            assertBoatDefinitions()
            assertBoatRegistry()
            assertSailingVarpDefinition()
            assertTransientSanitizerPreservesOwnership()
        } finally {
            serverCache.close()
        }
    }

    private fun assertSailingVarpDefinition() {
        for (id in SAILING_TEMP_VARPS) {
            val varp = assertNotNull(ServerCacheManager.getVarp(id), "Sailing temp varp $id")
            assertEquals(VarpLifetime.Temp, varp.scope, "Sailing temp varp $id scope")
            assertEquals(
                VarpTransmitLevel.OnSetAlways,
                varp.transmit,
                "Sailing temp varp $id transmit",
            )
        }
    }

    private fun assertNativeStatMapping() {
        val statId = "stat.sailing".asRSCM(RSCMType.STAT)
        assertEquals(23, statId)
        assertEquals(
            (STATS_INTERFACE shl 16) or SAILING_STATS_CHILD,
            "component.stats:sailing".asRSCM(RSCMType.COMPONENT),
        )
    }

    private fun assertRuntimeStatDefinition() {
        val stat = assertNotNull(ServerCacheManager.getStats(23), "runtime stat.sailing")
        assertEquals(1, stat.minLevel)
        assertEquals(99, stat.maxLevel)

        val row = StatComponentsRow.getRow("dbrow.sailing_stat")
        assertEquals(23, row.stat.id)
        assertEquals((STATS_INTERFACE shl 16) or SAILING_STATS_CHILD, row.component.packed)
        assertEquals(SAILING_STATS_CHILD, row.bit)
    }

    private fun assertNativeInterfaces() {
        val interfaces =
            mapOf(
                "interface.sailing_boat_selection" to (934 to 34),
                "interface.sailing_log" to (935 to 51),
                "interface.sailing_spyglass" to (936 to 5),
                "interface.sailing_sidepanel" to (937 to 40),
                "interface.sailing_crew" to (938 to 17),
                "interface.sailing_customisation" to (939 to 42),
                "interface.sailing_boat_cargohold" to (943 to 25),
                "interface.sailing_boat_cargohold_side" to (944 to 9),
            )
        for ((symbol, expected) in interfaces) {
            val (id, componentCount) = expected
            assertEquals(id, symbol.asRSCM(RSCMType.INTERFACE), symbol)
            val type = assertNotNull(ServerCacheManager.getInterface(id), symbol)
            assertEquals(componentCount, type.components.size, symbol)
        }
    }

    private fun assertCoreVars() {
        assertVarbit(
            symbol = "varbit.sailing_player_is_on_player_boat",
            id = 19104,
            baseVar = 5056,
            bits = 0..0,
        )
        assertVarbit(
            symbol = "varbit.sailing_boat_facility_lockedin",
            id = 19105,
            baseVar = 5056,
            bits = 1..8,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_visible",
            id = 19151,
            baseVar = 5111,
            bits = 0..0,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_tabs",
            id = 19152,
            baseVar = 5111,
            bits = 1..2,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_sail_button_toggled",
            id = 19174,
            baseVar = 5119,
            bits = 0..0,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_boat_move_mode",
            id = 19175,
            baseVar = 5119,
            bits = 1..3,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_helm_status",
            id = 19176,
            baseVar = 5119,
            bits = 4..5,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_player_at_helm",
            id = 19205,
            baseVar = 5121,
            bits = 30..30,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_ammo_needs_update",
            id = 19236,
            baseVar = 5118,
            bits = 15..15,
        )
        assertVarbit(
            symbol = "varbit.sailing_sidepanel_boat_stats_needs_update",
            id = 19237,
            baseVar = 5118,
            bits = 16..16,
        )
        assertVarbit(
            symbol = "varbit.keep_sailing_boat_when_leaving_helm",
            id = 18135,
            baseVar = 4849,
            bits = 15..15,
        )
        assertVarbit(
            symbol = "varbit.sailing_start_boat_when_setting_heading",
            id = 18136,
            baseVar = 4849,
            bits = 16..17,
        )
    }

    private fun assertVarbit(symbol: String, id: Int, baseVar: Int, bits: IntRange) {
        assertEquals(id, symbol.asRSCM(RSCMType.VARBIT), symbol)
        val type = assertNotNull(ServerCacheManager.getVarbit(id), symbol)
        assertEquals(baseVar, type.varp, "$symbol base var")
        assertEquals(bits.first, type.startBit, "$symbol least-significant bit")
        assertEquals(bits.last, type.endBit, "$symbol most-significant bit")
    }

    private fun assertBoatDefinitions() {
        assertBoat(BoatTypes.RAFT, 8110, 1, 2, 0, "Tiny (1x3)", 250)
        assertBoat(BoatTypes.SKIFF, 8111, 15, 5, 2, "Small (2x5)", 3_750)
        assertBoat(BoatTypes.SLOOP, 8112, 50, 10, 5, "Medium (3x10)", 50_000)
    }

    private fun assertBoatRegistry() {
        val registry = SailingBoatDefinitionRegistry()
        assertEquals(3, registry.all.size)
        assertEquals(listOf(0, 2, 5), registry.all.map { it.crewCapacity })
        assertEquals(listOf(1, 2, 3), registry.all.map { it.boatSize })
        for (definition in registry.all) {
            assertEquals(definition, registry[definition.runtimeType])
            assertEquals(definition, registry.byCacheTypeId(definition.cacheTypeId))
            assertEquals(definition, registry.byRowId(definition.rowId))
            assertEquals(definition, registry.byCacheRow(definition.runtimeType.cacheRow))
        }
        assertEquals(null, registry.byCacheTypeId(-1))
        assertEquals(null, registry.byRowId(-1))
        assertNull(registry.byCacheRow("dbrow.sailing_boat_unknown"))
    }

    private fun assertTransientSanitizerPreservesOwnership() {
        val registry = SailingBoatDefinitionRegistry()
        val validator = SailingOwnershipValidator(registry)
        val snapshot = validator.validate(listOf(SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow)))
        val player = Player()
        SailingOwnershipState.attach(player, snapshot)
        player.aboardPlayerBoat = 1
        player.helmLockedIn = 9
        player.sailingSidePanelVisible = 1
        player.sailingSailButtonToggled = 1
        player.sailingBoatMoveMode = SailingMoveModes.FULL
        player.sailingPlayerAtHelm = 1
        player.sailingSidePanelBoatType = 8112

        clearTransientSailingState(player)

        assertEquals(0, player.aboardPlayerBoat)
        assertEquals(0, player.helmLockedIn)
        assertEquals(0, player.sailingSidePanelVisible)
        assertEquals(0, player.sailingSailButtonToggled)
        assertEquals(SailingMoveModes.STOPPED, player.sailingBoatMoveMode)
        assertEquals(0, player.sailingPlayerAtHelm)
        assertEquals(0, player.sailingSidePanelBoatType)
        assertEquals(snapshot, SailingOwnershipState.snapshot(player))
    }

    private fun assertBoat(
        runtime: BoatType,
        rowId: Int,
        level: Int,
        playerCapacity: Int,
        crewCapacity: Int,
        size: String,
        retrievalCost: Int,
    ) {
        val row = SailingBoatRow.getRow(runtime.cacheRow)
        assertEquals(runtime.cacheTypeId, row.typeId)
        assertEquals(runtime.key, row.name.lowercase())
        assertEquals(rowId, row.rowId)
        assertEquals(level, row.levelRequired)
        assertEquals(playerCapacity, row.playerCapacity)
        assertEquals(crewCapacity, row.crewCapacity)
        assertEquals(size, row.sizeDesc)
        assertEquals(retrievalCost, row.retrievalCost)
    }

    private companion object {
        private const val REVISION = 240
        private const val STATS_INTERFACE = 320
        private const val SAILING_STATS_CHILD = 24
        private val SAILING_TEMP_VARPS = listOf(5056, 5111, 5117, 5118, 5119, 5121)
    }
}
