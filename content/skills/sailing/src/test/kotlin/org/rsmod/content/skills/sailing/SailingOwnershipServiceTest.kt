package org.rsmod.content.skills.sailing

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.rsmod.content.skills.sailing.ownership.SailingOwnedBoat
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipMutationResult
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipRejection
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipService
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipSnapshot
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipValidator

class SailingOwnershipServiceTest {
    private val validator =
        SailingOwnershipValidator(
            setOf(BoatTypes.RAFT.cacheRow, BoatTypes.SKIFF.cacheRow, BoatTypes.SLOOP.cacheRow)
        )
    private val service = SailingOwnershipService(validator)

    @Test
    fun `validator sorts records and permits duplicate boat classes in distinct slots`() {
        val snapshot =
            validator.validate(
                listOf(
                    SailingOwnedBoat(4, BoatTypes.RAFT.cacheRow),
                    SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow),
                    SailingOwnedBoat(2, BoatTypes.SKIFF.cacheRow),
                )
            )

        assertEquals(listOf(0, 2, 4), snapshot.boats.map(SailingOwnedBoat::slot))
        assertEquals(2, snapshot.boats.count { it.boatTypeKey == BoatTypes.RAFT.cacheRow })
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST") (snapshot.boats as MutableList<SailingOwnedBoat>).clear()
        }
    }

    @Test
    fun `validator rejects malformed or future ownership rows`() {
        assertFailsWith<IllegalArgumentException> {
            validator.validate(listOf(SailingOwnedBoat(-1, BoatTypes.RAFT.cacheRow)))
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate(listOf(SailingOwnedBoat(5, BoatTypes.RAFT.cacheRow)))
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate(
                listOf(
                    SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow),
                    SailingOwnedBoat(0, BoatTypes.SKIFF.cacheRow),
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate(listOf(SailingOwnedBoat(0, "dbrow.sailing_boat_unknown")))
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate(listOf(SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow, 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate((0..5).map { SailingOwnedBoat(it, BoatTypes.RAFT.cacheRow) })
        }
    }

    @Test
    fun `five adds succeed and rejected sixth slot emits no persistence hint`() {
        val reference = AtomicReference(SailingOwnershipSnapshot.EMPTY)
        var hints = 0
        for (slot in 0..4) {
            assertIs<SailingOwnershipMutationResult.Changed>(
                service.add(reference, slot, BoatTypes.RAFT.cacheRow) { hints++ }
            )
        }

        val rejected = service.add(reference, 5, BoatTypes.RAFT.cacheRow) { hints++ }

        assertEquals(
            SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.INVALID_SLOT),
            rejected,
        )
        assertEquals(5, reference.get().boats.size)
        assertEquals(5, hints)
    }

    @Test
    fun `invalid and no-op mutations leave the snapshot and hint count unchanged`() {
        val initial = validator.validate(listOf(SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow)))
        val reference = AtomicReference(initial)
        var hints = 0

        assertEquals(
            SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.SLOT_OCCUPIED),
            service.add(reference, 0, BoatTypes.SKIFF.cacheRow) { hints++ },
        )
        assertEquals(
            SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.UNKNOWN_BOAT_TYPE),
            service.replace(reference, 0, "dbrow.sailing_boat_unknown") { hints++ },
        )
        assertEquals(
            SailingOwnershipMutationResult.NoChange,
            service.replace(reference, 0, BoatTypes.RAFT.cacheRow) { hints++ },
        )
        assertEquals(
            SailingOwnershipMutationResult.NoChange,
            service.remove(reference, 1) { hints++ },
        )

        assertEquals(initial, reference.get())
        assertEquals(0, hints)
    }

    @Test
    fun `replace remove swap and clear publish one hint per material change`() {
        val reference = AtomicReference(SailingOwnershipSnapshot.EMPTY)
        var hints = 0
        service.add(reference, 0, BoatTypes.RAFT.cacheRow) { hints++ }
        service.add(reference, 2, BoatTypes.SKIFF.cacheRow) { hints++ }

        assertIs<SailingOwnershipMutationResult.Changed>(
            service.replace(reference, 0, BoatTypes.SLOOP.cacheRow) { hints++ }
        )
        assertIs<SailingOwnershipMutationResult.Changed>(service.swap(reference, 0, 1) { hints++ })
        assertEquals(listOf(1, 2), reference.get().boats.map(SailingOwnedBoat::slot))
        assertIs<SailingOwnershipMutationResult.Changed>(service.remove(reference, 2) { hints++ })
        assertEquals(listOf(1), reference.get().boats.map(SailingOwnedBoat::slot))
        assertIs<SailingOwnershipMutationResult.Changed>(service.clear(reference) { hints++ })
        assertEquals(SailingOwnershipSnapshot.EMPTY, reference.get())
        assertEquals(6, hints)
        assertEquals(SailingOwnershipMutationResult.NoChange, service.clear(reference) { hints++ })
        assertEquals(6, hints)
    }

    @Test
    fun `swap moves a boat into an empty slot and swaps two occupied slots`() {
        val initial =
            validator.validate(
                listOf(
                    SailingOwnedBoat(0, BoatTypes.RAFT.cacheRow),
                    SailingOwnedBoat(2, BoatTypes.SKIFF.cacheRow),
                )
            )
        val reference = AtomicReference(initial)

        service.swap(reference, 0, 1)
        assertEquals(
            listOf(1 to BoatTypes.RAFT.cacheRow, 2 to BoatTypes.SKIFF.cacheRow),
            reference.get().boats.map { it.slot to it.boatTypeKey },
        )
        service.swap(reference, 1, 2)
        assertEquals(
            listOf(1 to BoatTypes.SKIFF.cacheRow, 2 to BoatTypes.RAFT.cacheRow),
            reference.get().boats.map { it.slot to it.boatTypeKey },
        )
    }
}
