package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class ConstructionEstateAgentTest {
    @Test
    fun `new accounts need to purchase but legacy saved houses remain owned`() {
        assertFalse(constructionHouseOwned(null))
        assertFalse(constructionHouseOwned(emptyMap()))
        assertFalse(constructionHouseOwned(mapOf("owned" to false, "rooms" to emptyList<Any>())))
        assertTrue(constructionHouseOwned(mapOf("owned" to true)))
        assertTrue(constructionHouseOwned(mapOf("rooms" to emptyList<Any>())))
    }

    @Test
    fun `legacy house settings migrate to OSRS defaults`() {
        assertEquals(HouseStyle.BASIC_WOOD, HouseStyle.fromStorage(null))
        assertEquals(HouseStyle.BASIC_WOOD, HouseStyle.fromStorage("REMOVED_STYLE"))
        assertEquals(HouseLocation.RIMMINGTON, HouseLocation.fromStorage(null))
        assertEquals(HouseLocation.RIMMINGTON, HouseLocation.fromStorage("REMOVED_LOCATION"))
    }

    @Test
    fun `estate agent standard styles use donor source columns and levels`() {
        val styles = HouseStyle.entries.filter { it.estateAgentAvailable }
        assertEquals(
            listOf(
                HouseStyle.BASIC_WOOD,
                HouseStyle.BASIC_STONE,
                HouseStyle.WHITEWASHED_STONE,
                HouseStyle.FREMENNIK_WOOD,
                HouseStyle.TROPICAL_WOOD,
                HouseStyle.FANCY_STONE,
                HouseStyle.DEATHLY_MANSION,
                HouseStyle.TWISTED_THEME,
                HouseStyle.HOSIDIUS_HOUSE,
                HouseStyle.COSY_CABIN,
            ),
            styles,
        )
        assertEquals(
            listOf(
                0 to 0,
                0 to 1,
                0 to 2,
                0 to 3,
                8 to 0,
                8 to 1,
                8 to 2,
                8 to 3,
                16 to 0,
                16 to 1,
            ),
            styles.map { it.zoneXOffset to it.sourceLevel },
        )
        assertEquals(
            listOf(5_000, 5_000, 7_500, 10_000, 15_000, 25_000, 35_000, 5_000, 5_000, 35_000),
            styles.map { it.price },
        )
    }

    @Test
    fun `house styles use revision 240 architectural wall window and door ids`() {
        val expected =
            listOf(
                listOf(13098, 13099, 13100, 13101),
                listOf(1902, 13091, 13094, 13096),
                listOf(1415, 13005, 13006, 13007),
                listOf(13111, 13112, 13109, 13107),
                listOf(13011, 10816, 13016, 13015),
                listOf(13116, 13117, 13119, 13118),
                listOf(27082, 27083, 27084, 27085),
                listOf(37467, 37469, 37463, 37465),
                listOf(39883, 39884, 37455, 37457),
                listOf(40862, 40863, 40858, 40860),
            )
        assertEquals(
            expected,
            HouseStyle.entries.map {
                listOf(it.wallLocId, it.windowLocId, it.doorLocId1, it.doorLocId2)
            },
        )
        expected.flatten().forEach { id ->
            assertTrue(ServerCacheManager.getObject(id) != null, "Missing revision-240 POH loc $id")
        }
    }

    @Test
    fun `finished windows become exterior windows or shared interior walls`() {
        HouseStyle.entries.forEach { style ->
            assertEquals(style.windowLocId, style.architecturalWindowLocId(1, null))
            assertEquals(style.windowLocId, style.architecturalWindowLocId(1, true))
            assertEquals(style.wallLocId, style.architecturalWindowLocId(1, false))
            assertEquals(style.wallLocId, style.architecturalWindowLocId(0, null))
        }
    }

    @Test
    fun `redecorating selects source levels instead of shifting rooms north`() {
        val gardenDonorX = 232
        val gardenDonorZ = 713
        assertEquals(
            listOf(
                ZoneKey(232, 881, 0),
                ZoneKey(232, 881, 1),
                ZoneKey(232, 881, 2),
                ZoneKey(232, 881, 3),
                ZoneKey(240, 881, 0),
                ZoneKey(240, 881, 1),
                ZoneKey(240, 881, 2),
                ZoneKey(240, 881, 3),
                ZoneKey(248, 881, 0),
                ZoneKey(248, 881, 1),
            ),
            HouseStyle.entries
                .filter { it.estateAgentAvailable }
                .map { revision240HouseZone(gardenDonorX, gardenDonorZ, it) },
        )
    }

    @Test
    fun `all supported house locations have unique revision 240 portals`() {
        assertEquals(
            HouseLocation.entries.size,
            HouseLocation.entries.map { it.portalId }.toSet().size,
        )
        HouseLocation.entries.forEach {
            assertTrue(ServerCacheManager.getObject(it.portalId) != null)
        }
    }

    @Test
    fun `house option storage values match the revision 240 interface`() {
        assertEquals(listOf(0, 1, 2), HouseDoorsMode.entries.map { it.cacheValue })
        assertEquals(HouseDoorsMode.CLOSED, HouseDoorsMode.fromStorage(null))
        assertEquals(HouseDoorsMode.CLOSED, HouseDoorsMode.fromStorage(0))
        assertEquals(HouseDoorsMode.OPEN, HouseDoorsMode.fromStorage(1))
        assertEquals(HouseDoorsMode.NONE, HouseDoorsMode.fromStorage(2))
        assertEquals(HouseDoorsMode.CLOSED, HouseDoorsMode.fromStorage(999))
        assertEquals(HouseOptions(), HouseBlueprint.default().options)
    }

    @Test
    fun `all house teleport locations have unique exterior destinations`() {
        assertEquals(
            HouseLocation.entries.size,
            HouseLocation.entries.map { it.outside }.toSet().size,
        )
        HouseLocation.entries.forEach { location ->
            assertEquals(0, location.outside.level, location.displayName)
        }
    }

    @Test
    fun `all five servants use the verified OSRS definitions and revision 240 npcs`() {
        val expected =
            listOf(
                listOf("Rick", 20, 500, 6, 60, false, 315, 221),
                listOf("Maid", 25, 1_000, 10, 30, false, 2003, 223),
                listOf("Cook", 30, 3_000, 16, 17, true, 2301, 225),
                listOf("Butler", 40, 5_000, 20, 12, true, 1897, 227),
                listOf("Demon butler", 50, 10_000, 26, 7, true, 2011, 229),
            )
        val actual =
            ConstructionServant.entries.map { servant ->
                val npcId = servant.npcGameval.asRSCM(RSCMType.NPC)
                assertTrue(ServerCacheManager.getNpc(npcId) != null, servant.npcGameval)
                listOf(
                    servant.displayName,
                    servant.level,
                    servant.wage,
                    servant.capacity,
                    servant.tripSeconds,
                    servant.sawmill,
                    servant.foodItemId,
                    npcId,
                )
            }
        assertEquals(expected, actual)
    }

    @Test
    fun `servant trip durations round upward to complete game cycles`() {
        assertEquals(
            listOf(100, 50, 29, 20, 12),
            ConstructionServant.entries.map { servantTripTicks(it.tripSeconds) },
        )
    }

    @Test
    fun `revision 240 estate agent exposes all three management actions`() {
        val agent = requireNotNull(ServerCacheManager.getNpc(3097))
        assertEquals("Estate agent", agent.name)
        assertEquals("Talk-to", agent.actions.getOpOrNull(0), agent.actions.toString())
        assertEquals("Relocate", agent.actions.getOpOrNull(2), agent.actions.toString())
        assertEquals("Redecorate", agent.actions.getOpOrNull(3), agent.actions.toString())
    }

    @Test
    fun `revision 240 house portal exposes entry build and friend actions`() {
        val portal = requireNotNull(ServerCacheManager.getObject(EDGEVILLE_HOUSE_PORTAL_ID))
        assertEquals("Portal", portal.name)
        assertEquals("Enter", portal.actions.getOpOrNull(0), portal.actions.toString())
        assertEquals("Home", portal.actions.getOpOrNull(1), portal.actions.toString())
        assertEquals("Build mode", portal.actions.getOpOrNull(2), portal.actions.toString())
        assertEquals("Friend's house", portal.actions.getOpOrNull(3), portal.actions.toString())
        assertTrue(portal.width > 0)
        assertTrue(portal.length > 0)
        assertEquals(0, EDGEVILLE_HOUSE_PORTAL_COORDS.level)
    }

    @Test
    fun `every built house exit portal has a working enter option`() {
        val ids =
            DonorConstructionData.buildables
                .filter { it.name.endsWith("EXIT_PORTAL") }
                .flatMap { it.builtLocIds.asIterable() }
                .filter { it > 0 }
                .toSet()
        assertEquals(setOf(4525, 60789), ids)
        ids.forEach { id ->
            val portal = requireNotNull(ServerCacheManager.getObject(id))
            assertTrue(
                (0 until 5).any {
                    portal.actions.getOpOrNull(it).equals("Enter", ignoreCase = true)
                },
                "Inside-house portal $id actions were ${portal.actions}",
            )
        }
    }

    @Test
    fun edgevilleEstateAgentUsesBankSpawnTile() {
        assertEquals(CoordGrid(3092, 3492, 0), EDGEVILLE_ESTATE_AGENT_COORDS)
    }

    companion object {
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
