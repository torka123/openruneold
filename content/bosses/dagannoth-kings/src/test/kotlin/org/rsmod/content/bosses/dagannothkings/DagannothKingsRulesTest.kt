package org.rsmod.content.bosses.dagannothkings

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogRecordStatus
import org.rsmod.api.collectionlog.CollectionLogState
import org.rsmod.api.collectionlog.CollectionLogStateCodec
import org.rsmod.api.collectionlog.CollectionLogStateEngine
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class DagannothKingsRulesTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `king styles and timings are explicit`() {
        assertEquals(
            DagannothKingStyle.Magic,
            DagannothKingsRules.kingStyle(DagannothKingsAssets.PRIME),
        )
        assertEquals(
            DagannothKingStyle.Ranged,
            DagannothKingsRules.kingStyle(DagannothKingsAssets.SUPREME),
        )
        assertEquals(
            DagannothKingStyle.Melee,
            DagannothKingsRules.kingStyle(DagannothKingsAssets.REX),
        )
        assertEquals(4, DagannothKingsRules.ATTACK_RATE_TICKS)
        assertEquals(4, DagannothKingsRules.SPINOLYP_ATTACK_RATE_TICKS)
        assertEquals(150, DagannothKingsRules.RESPAWN_TICKS)
    }

    @Test
    fun `rsprox movement profiles keep kings local and spinolyps on the outer ring`() {
        val prime = DagannothKingsRules.movementProfile(DagannothKingsAssets.PRIME)
        assertEquals(5, prime.wanderRange)
        assertEquals(7, prime.attackRange)
        assertEquals(8, prime.huntRange)
        assertEquals(DagannothKingsRules.AGGRESSIVE_RANGED_HUNT_MODE, prime.huntMode)

        val supreme = DagannothKingsRules.movementProfile(DagannothKingsAssets.SUPREME)
        assertEquals(prime, supreme)

        val rex = DagannothKingsRules.movementProfile(DagannothKingsAssets.REX)
        assertEquals(5, rex.wanderRange)
        assertEquals(1, rex.attackRange)
        assertEquals(8, rex.huntRange)
        assertEquals(DagannothKingsRules.AGGRESSIVE_MELEE_HUNT_MODE, rex.huntMode)

        DagannothKingsAssets.activeSpinolyps.plus(DagannothKingsAssets.suspiciousWater).forEach {
            internal ->
            val spinolyp = DagannothKingsRules.movementProfile(internal)
            assertEquals(20, spinolyp.wanderRange, internal)
            assertEquals(15, spinolyp.attackRange, internal)
            assertEquals(15, spinolyp.huntRange, internal)
            assertEquals(
                DagannothKingsRules.AGGRESSIVE_RANGED_HUNT_MODE,
                spinolyp.huntMode,
                internal,
            )
        }
    }

    @Test
    fun `prime splash is exactly three by three`() {
        assertTrue(DagannothKingsRules.isPrimeSplash(10, 10, 9, 9))
        assertTrue(DagannothKingsRules.isPrimeSplash(10, 10, 11, 11))
        assertFalse(DagannothKingsRules.isPrimeSplash(10, 10, 12, 10))
    }

    @Test
    fun `spinolyp styles transforms and spectral rounding are explicit`() {
        assertEquals(SpinolypStyle.RangedPoison, DagannothKingsRules.spinolypStyle(0))
        assertEquals(SpinolypStyle.MagicPrayerDrain, DagannothKingsRules.spinolypStyle(1))
        assertEquals(
            SpinolypStyle.SuspiciousWaterMelee,
            DagannothKingsRules.spinolypStyle(0, suspiciousWaterAdjacent = true),
        )
        assertEquals(1, DagannothKingsRules.prayerDrain(hasSpectral = false, spectralRound = 0))
        assertEquals(1, DagannothKingsRules.prayerDrain(hasSpectral = false, spectralRound = 1))
        assertEquals(0, DagannothKingsRules.prayerDrain(hasSpectral = true, spectralRound = 0))
        assertEquals(1, DagannothKingsRules.prayerDrain(hasSpectral = true, spectralRound = 1))
        assertEquals(10, DagannothKingsRules.SPINOLYP_RESURFACE_CHECK_TICKS)
        assertEquals(5, DagannothKingsRules.SPINOLYP_RESURFACE_ROLL_BOUND)
        assertEquals(2, DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS)
        assertTrue(DagannothKingsRules.shouldResurface(0))
        assertFalse(DagannothKingsRules.shouldResurface(1))
        assertFalse(DagannothKingsRules.shouldResurface(4))
    }

    @Test
    fun `map spawned spinolyp cycles are staggered and bounded`() {
        val activeTicks =
            (0 until 25).map { index ->
                DagannothKingsRules.initialActiveTicks(2885 + index, 4440 + (index % 4))
            }
        assertTrue(activeTicks.all { it in 1..DagannothKingsRules.SPINOLYP_ACTIVE_TICKS })
        assertTrue(activeTicks.distinct().size > 1)

        val submergedTicks =
            (0 until 25).map { index ->
                DagannothKingsRules.initialResurfaceCheckTicks(2885 + index, 4440 + (index % 4))
            }
        assertTrue(
            submergedTicks.all {
                it in
                    DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS..DagannothKingsRules
                            .SPINOLYP_RESURFACE_CHECK_TICKS &&
                    it % DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS == 0
            }
        )
        assertTrue(submergedTicks.distinct().size > 1)
    }

    @Test
    fun `only the lower dedicated chamber is slayer gated`() {
        assertTrue(DagannothKingsRules.isSlayerChamber(2911, 4387))
        assertTrue(DagannothKingsRules.isSlayerChamber(2880, 4352))
        assertTrue(DagannothKingsRules.isSlayerChamber(2943, 4415))
        assertFalse(DagannothKingsRules.isSlayerChamber(2911, 4451))
        assertFalse(DagannothKingsRules.isSlayerChamber(2879, 4387))
    }

    @Test
    fun `revision 240 contains all active lair and suspicious-water definitions`() {
        val root = projectRoot()
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val expected =
            mapOf(
                DagannothKingsAssets.SUPREME to 2265,
                DagannothKingsAssets.PRIME to 2266,
                DagannothKingsAssets.REX to 2267,
                DagannothKingsAssets.SPINOLYP_SMALL to 5947,
                DagannothKingsAssets.SPINOLYP_MEDIUM to 5961,
                DagannothKingsAssets.SPINOLYP_LARGE to 5963,
                DagannothKingsAssets.SUSPICIOUS_WATER_SMALL to 5946,
                DagannothKingsAssets.SUSPICIOUS_WATER_MEDIUM to 5948,
                DagannothKingsAssets.SUSPICIOUS_WATER_LARGE to 5962,
            )
        expected.forEach { (internal, id) ->
            assertEquals(id, internal.asRSCM(RSCMType.NPC), internal)
            assertNotNull(ServerCacheManager.getNpc(id), internal)
        }
        val combatFx =
            mapOf(
                RSCMType.SEQ to
                    mapOf(
                        DagannothKingsAssets.PRIME_ATTACK_ANIMATION to 2854,
                        DagannothKingsAssets.SUPREME_ATTACK_ANIMATION to 2855,
                        DagannothKingsAssets.REX_ATTACK_ANIMATION to 2853,
                        DagannothKingsAssets.SPINOLYP_ATTACK_ANIMATION to 2868,
                        DagannothKingsAssets.KING_DEATH_ANIMATION to 2856,
                        DagannothKingsAssets.SPINOLYP_DEATH_ANIMATION to 2866,
                    ),
                RSCMType.SPOTANIM to
                    mapOf(
                        DagannothKingsAssets.PRIME_PROJECTILE to 162,
                        DagannothKingsAssets.SUPREME_PROJECTILE to 475,
                        DagannothKingsAssets.SPINOLYP_PROJECTILE to 294,
                        DagannothKingsAssets.SPINOLYP_SURFACE_SPOTANIM to 68,
                    ),
            )
        combatFx.forEach { (type, mappings) ->
            mappings.forEach { (internal, expectedId) ->
                assertEquals(expectedId, internal.asRSCM(type), internal)
            }
        }
        DagannothKingsAssets.kings.forEach { internal ->
            val type = assertNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)))
            assertEquals(255, type.hitpoints, internal)
            type.respawnRate = DagannothKingsRules.RESPAWN_TICKS
            assertEquals(150, type.respawnRate, internal)
        }
    }

    @Test
    fun `revision 240 kings ladders expose the routed room options`() {
        val root = projectRoot()
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        assertEquals(
            DagannothKingsTravelRules.DOWN_PARENT_ID,
            DagannothKingsTravelRules.DOWN_PARENT.asRSCM(RSCMType.LOC),
        )
        assertEquals(
            DagannothKingsTravelRules.DOWN_NORMAL_ID,
            DagannothKingsTravelRules.DOWN_NORMAL.asRSCM(RSCMType.LOC),
        )
        assertEquals(
            DagannothKingsTravelRules.DOWN_PRIVATE_ID,
            DagannothKingsTravelRules.DOWN_PRIVATE.asRSCM(RSCMType.LOC),
        )
        assertEquals(
            DagannothKingsTravelRules.UP_ID,
            DagannothKingsTravelRules.UP.asRSCM(RSCMType.LOC),
        )

        val parent =
            assertNotNull(ServerCacheManager.getObject(DagannothKingsTravelRules.DOWN_PARENT_ID))
        assertEquals(listOf(40417, 40418, 40417), parent.transforms?.toList())
        val normal =
            assertNotNull(ServerCacheManager.getObject(DagannothKingsTravelRules.DOWN_NORMAL_ID))
        assertEquals("Standard", normal.actions.getOpOrNull(0))
        assertEquals("Slayer", normal.actions.getOpOrNull(1))
        assertEquals("Peek", normal.actions.getOpOrNull(3))
        val private =
            assertNotNull(ServerCacheManager.getObject(DagannothKingsTravelRules.DOWN_PRIVATE_ID))
        assertEquals("Standard", private.actions.getOpOrNull(0))
        assertEquals("Slayer", private.actions.getOpOrNull(1))
        assertEquals("Private", private.actions.getOpOrNull(2))
        assertEquals("Peek", private.actions.getOpOrNull(3))
        val up = assertNotNull(ServerCacheManager.getObject(DagannothKingsTravelRules.UP_ID))
        assertEquals("Climb-up", up.actions.getOpOrNull(0))
    }

    @Test
    fun `kings ladder routes use the verified public map positions`() {
        assertEquals(CoordGrid(1911, 4367, 0), DagannothKingsTravelRules.EXTERNAL_LADDER)
        assertEquals(CoordGrid(1912, 4367, 0), DagannothKingsTravelRules.EXTERNAL_ARRIVAL)
        assertEquals(CoordGrid(2899, 4449, 0), DagannothKingsTravelRules.STANDARD_LADDER)
        assertEquals(CoordGrid(2900, 4449, 0), DagannothKingsTravelRules.STANDARD_ENTRY)
        assertEquals(CoordGrid(2899, 4385, 0), DagannothKingsTravelRules.SLAYER_LADDER)
        assertEquals(CoordGrid(2900, 4385, 0), DagannothKingsTravelRules.SLAYER_ENTRY)
        assertEquals(
            DagannothKingsTravelRules.STANDARD_ENTRY,
            DagannothKingsTravelRules.entry(DagannothKingsRoom.Standard),
        )
        assertEquals(
            DagannothKingsTravelRules.SLAYER_ENTRY,
            DagannothKingsTravelRules.entry(DagannothKingsRoom.Slayer),
        )
        assertTrue(
            DagannothKingsTravelRules.contains(
                DagannothKingsRoom.Standard,
                CoordGrid(2911, 4451, 0),
            )
        )
        assertTrue(
            DagannothKingsTravelRules.contains(DagannothKingsRoom.Slayer, CoordGrid(2911, 4387, 0))
        )
        assertFalse(
            DagannothKingsTravelRules.contains(
                DagannothKingsRoom.Standard,
                CoordGrid(2911, 4387, 0),
            )
        )
    }

    @Test
    fun `king drop tables bind to global npc drop and collection log routing`() {
        val root = projectRoot()
        val routes =
            mapOf(
                "dagannoth_prime.toml" to DagannothKingsAssets.PRIME,
                "dagannoth_rex.toml" to DagannothKingsAssets.REX,
                "dagannoth_supreme.toml" to DagannothKingsAssets.SUPREME,
            )
        val tableRoot = root.resolve("content/drops/src/main/resources/drops/tables/monsters")
        routes.forEach { (file, npc) ->
            val text = Files.readString(tableRoot.resolve(file))
            assertTrue(text.contains("npcs = [\"$npc\"]"), "$file must route deaths for $npc")
        }

        val catalog = CollectionLogCatalogRepository()
        val engine = CollectionLogStateEngine(catalog)
        val state = CollectionLogState(legacyMigrationComplete = true)
        val kingUniques = listOf(6731, 6733, 6737, 6739)
        kingUniques.forEachIndexed { index, itemId ->
            assertTrue(catalog.slotsFor(itemId).isNotEmpty(), "DK unique $itemId must be tracked")
            val event = "npc:dagannoth-kings:test-kill-$index"
            assertEquals(
                CollectionLogRecordStatus.Recorded,
                engine.record(state, itemId, 1, event).status,
            )
            assertEquals(
                CollectionLogRecordStatus.DuplicateEvent,
                engine.record(state, itemId, 1, event).status,
            )
        }
        val restored =
            assertNotNull(CollectionLogStateCodec.decode(CollectionLogStateCodec.encode(state)))
        kingUniques.forEach { itemId -> assertEquals(1, engine.quantity(restored, itemId)) }
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
}
