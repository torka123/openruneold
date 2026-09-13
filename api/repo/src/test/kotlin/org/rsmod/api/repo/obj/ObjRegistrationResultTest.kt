package org.rsmod.api.repo.obj

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.obj.ObjRegistryResult
import org.rsmod.api.registry.obj.isSuccess
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.map.CoordGrid

/**
 * Native ground-registration-result mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. These tests
 * characterize the existing repository/registry factories, merging, splitting and timer behavior.
 * They make no OSRS timing, durable-reference, entitlement or pickup guarantee. The dummy input is
 * the actual revision240 definition backed by raw-cache/server/items.toml's existing dummyitem=1.
 */
class ObjRegistrationResultTest {
    @Test
    fun `Stack registers the exact private input with native default reveal and expiry`() {
        val f = Fixture()
        val input = f.privateObj(995, 17, 31)
        val duration = ObjRepository.DEFAULT_REVEAL_DELAY + 2
        assertSame(ObjRegistryResult.Add.Stack, f.repository.addWithResult(input, duration))
        assertSame(input, f.objects().single())
        assertTrue(input.isPrivate)
        assertEquals(11L, input.receiverId)
        assertEquals(11L, input.ownerId)
        assertEquals(100, input.creationCycle)
        assertEquals(31, input.vars)
        f.updates.clear()

        f.tick(ObjRepository.DEFAULT_REVEAL_DELAY - 1)
        assertSame(input, f.objects().single())
        assertTrue(input.isPrivate)
        assertEquals(0, f.updateCount())
        f.tick()
        assertTrue(input.isPublic)
        assertEquals(11L, input.ownerId)
        assertEquals(31, input.vars)
        assertEquals(1, f.updateCount())
        f.updates.clear()
        f.tick()
        assertSame(input, f.objects().single())
        assertEquals(0, f.updateCount())
        f.tick()
        assertTrue(f.objects().isEmpty())
        assertEquals(1, f.updateCount())
    }

    @Test
    fun `Merge returns the existing object and replaces its current finite deadlines`() {
        val f = Fixture()
        val existing = f.privateObj(995, 7, 123)
        f.repository.addWithResult(existing, duration = 6, reveal = 4)
        f.tick(3)
        val input = f.privateObj(995, 5, 456)
        val result =
            assertIs<ObjRegistryResult.Add.Merge>(
                f.repository.addWithResult(input, duration = 8, reveal = 2)
            )
        assertSame(existing, result.merged)
        assertNotSame(input, result.merged)
        assertSame(existing, f.objects().single())
        assertEquals(12, existing.count)
        assertEquals(5, input.count)
        assertEquals(123, existing.vars, "Native merge retains the registered object's vars")
        assertEquals(100, existing.creationCycle)
        assertEquals(103, input.creationCycle)
        assertEquals(11L, existing.ownerId)
        f.tick()
        assertTrue(existing.isPrivate, "The old reveal deadline was replaced")
        f.tick()
        assertTrue(existing.isPublic)
        f.tick(5)
        assertSame(existing, f.objects().single(), "The old deletion deadline must not survive")
        f.tick()
        assertTrue(f.objects().isEmpty())
    }

    @Test
    fun `Split returns each actual single object preserving private owner vars creation and timers`() {
        val f = Fixture()
        val sword = assertNotNull(ServerCacheManager.getItem(1277))
        assertFalse(sword.isStackable)
        assertFalse(sword.isDummyItem)
        val input = Obj.fromOwner(f.owner, f.coords, InvObj(sword, 3, 0x12345))
        val result =
            assertIs<ObjRegistryResult.Add.Split>(
                f.repository.addWithResult(input, duration = 5, reveal = 2)
            )
        assertEquals(3, result.split.size)
        assertEquals(3, result.split.toSet().size)
        assertTrue(f.objects().none { it === input })
        for (single in result.split) {
            assertTrue(f.objects().any { it === single })
            assertEquals(1, single.count)
            assertEquals(1277, single.type)
            assertEquals(0x12345, single.vars)
            assertEquals(input.coords, single.coords)
            assertEquals(input.creationCycle, single.creationCycle)
            assertEquals(input.ownerId, single.ownerId)
            assertEquals(input.receiverId, single.receiverId)
            assertTrue(single.isPrivate)
        }
        f.tick()
        assertTrue(result.split.all { it.isPrivate })
        f.tick()
        assertTrue(result.split.all { it.isPublic && it.ownerId == 11L })
        f.tick(2)
        assertEquals(result.split.toSet(), f.objects().toSet())
        f.tick()
        assertTrue(f.objects().isEmpty())
    }

    @Test
    fun `bulk dummy and nonpositive counts reject without registration or timer output`() {
        val f = Fixture()
        val dummyId = "obj.cert_chompy_bird_obj".asRSCM(RSCMType.OBJ)
        val dummy = assertNotNull(ServerCacheManager.getItem(dummyId))
        assertTrue(
            dummy.isDummyItem,
            "Canonical packed $dummyId must retain dummyitem=${dummy.dummyitem}",
        )
        val bulk = f.privateObj(1277, ObjRegistry.MAX_NON_STACK_COUNT_DROP + 1)
        val rejected =
            assertIs<ObjRegistryResult.Add.BulkNonStackableLimitExceeded>(
                f.repository.addWithResult(bulk, 5, 2)
            )
        assertEquals(129, rejected.requestedCount)
        assertFalse(f.repository.add(f.privateObj(1277, 129), 5, 2))
        assertFailsWith<IllegalStateException> {
            f.repository.addWithResult(Obj.fromOwner(f.owner, f.coords, InvObj(dummy, 1)), 5, 2)
        }
        assertFailsWith<IllegalStateException> {
            f.repository.add(Obj.fromOwner(f.owner, f.coords, InvObj(dummy, 1)), 5, 2)
        }
        for (count in listOf(0, -1)) {
            // Factories reject nonpositive counts first; exercise the repository's own guard
            // using the existing packed-value constructor, without changing a cache definition.
            val invalid = f.privateObj(995, 1)
            val countBits = ObjEntity.COUNT_BIT_MASK shl ObjEntity.COUNT_BIT_OFFSET
            invalid.entity =
                ObjEntity(
                    (invalid.entity.packed and countBits.inv()) or
                        ((count.toLong() and ObjEntity.COUNT_BIT_MASK) shl
                            ObjEntity.COUNT_BIT_OFFSET)
                )
            assertEquals(count, invalid.count)
            assertFailsWith<IllegalArgumentException> { f.repository.addWithResult(invalid, 5, 2) }
            assertFailsWith<IllegalArgumentException> { f.repository.add(invalid, 5, 2) }
        }
        assertTrue(f.objects().isEmpty())
        assertEquals(0, f.registry.count())
        assertEquals(0, f.updateCount())
        f.tick(110)
        assertTrue(f.objects().isEmpty())
        assertEquals(0, f.updateCount(), "Rejected attempts must not leave reveal/delete work")
    }

    @Test
    fun `infinite duration preserves existing native timer rules for fresh objects and merges`() {
        val fresh = Fixture()
        val permanentPrivate = fresh.privateObj(995, 1)
        fresh.repository.addWithResult(permanentPrivate, Int.MAX_VALUE, reveal = 1)
        fresh.updates.clear()
        fresh.tick(110)
        assertSame(permanentPrivate, fresh.objects().single())
        assertTrue(permanentPrivate.isPrivate)
        assertEquals(0, fresh.updateCount())

        val finite = Fixture()
        val timed = finite.privateObj(995, 1)
        finite.repository.addWithResult(timed, duration = 5, reveal = 3)
        finite.tick()
        val retained =
            assertIs<ObjRegistryResult.Add.Merge>(
                finite.repository.addWithResult(
                    finite.privateObj(995, 2),
                    Int.MAX_VALUE,
                    reveal = 1,
                )
            )
        assertSame(timed, retained.merged)
        finite.tick()
        assertTrue(timed.isPrivate, "An infinite merge leaves the old reveal deadline")
        finite.tick()
        assertTrue(timed.isPublic)
        finite.tick(2)
        assertTrue(finite.objects().isEmpty(), "An infinite merge leaves the old finite expiry")

        val infinite = Fixture()
        val untimed = infinite.privateObj(995, 1)
        infinite.repository.addWithResult(untimed, Int.MAX_VALUE, reveal = 1)
        val stillUntimed =
            assertIs<ObjRegistryResult.Add.Merge>(
                infinite.repository.addWithResult(
                    infinite.privateObj(995, 2),
                    duration = 3,
                    reveal = 1,
                )
            )
        assertSame(untimed, stillUntimed.merged)
        infinite.updates.clear()
        infinite.tick(110)
        assertSame(untimed, infinite.objects().single())
        assertEquals(3, untimed.count)
        assertTrue(untimed.isPrivate, "A finite merge does not create a missing native timer")
        assertEquals(0, infinite.updateCount())
    }

    @Test
    fun `legacy Boolean registration retains the same objects updates and timed effects`() {
        fun trace(detailed: Boolean): List<Pair<List<Snapshot>, Int>> {
            val f = Fixture()
            val trace = mutableListOf<Pair<List<Snapshot>, Int>>()
            fun snapshot() {
                trace +=
                    f.objects().map {
                        Snapshot(
                            it.type,
                            it.count,
                            it.vars,
                            it.ownerId,
                            it.receiverId,
                            it.creationCycle,
                            it.isPrivate,
                        )
                    } to f.updateCount()
                f.updates.clear()
            }
            fun add(obj: Obj, duration: Int, reveal: Int) {
                if (detailed)
                    assertTrue(f.repository.addWithResult(obj, duration, reveal).isSuccess())
                else assertTrue(f.repository.add(obj, duration, reveal))
                snapshot()
            }
            add(f.privateObj(995, 3, 31), 5, 2)
            add(f.privateObj(995, 2, 99), 6, 3)
            add(f.privateObj(1277, 2, 45), 4, 1)
            f.tick()
            snapshot()
            f.tick(2)
            snapshot()
            f.tick()
            snapshot()
            f.tick(2)
            snapshot()
            return trace
        }
        val detailed = trace(true)
        assertEquals(listOf(1, 1, 3, 3, 3, 1, 0), detailed.map { it.first.size })
        assertEquals(detailed, trace(false))
    }

    private data class Snapshot(
        val type: Int,
        val count: Int,
        val vars: Int,
        val owner: Long,
        val receiver: Long,
        val creation: Int,
        val isPrivate: Boolean,
    )

    private class Fixture {
        init {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }

        val clock = MapClock(100)
        val updates = ZoneUpdateMap()
        val registry = ObjRegistry(updates)
        val repository = ObjRepository(clock, registry)
        val coords = CoordGrid(2913, 5215)
        val owner =
            Player().apply {
                observerUUID = 11L
                currentMapClock = clock.cycle
            }

        fun privateObj(id: Int, count: Int, vars: Int = 0): Obj =
            Obj.fromOwner(
                owner,
                coords,
                InvObj(assertNotNull(ServerCacheManager.getItem(id)), count, vars),
            )

        fun objects(): List<Obj> = repository.findAll(coords).toList()

        fun updateCount(): Int = updates.updatedZones.values.sumOf { it.size }

        fun tick(count: Int = 1) =
            repeat(count) {
                clock.tick()
                owner.currentMapClock = clock.cycle
                repository.processDurations()
            }
    }
}
