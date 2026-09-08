package org.rsmod.content.skills.sailing.ownership

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicReference
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints

sealed interface SailingOwnershipMutationResult {
    data class Changed(val snapshot: SailingOwnershipSnapshot) : SailingOwnershipMutationResult

    data object NoChange : SailingOwnershipMutationResult

    data class Rejected(val reason: SailingOwnershipRejection) : SailingOwnershipMutationResult
}

enum class SailingOwnershipRejection {
    INVALID_SLOT,
    UNKNOWN_BOAT_TYPE,
    SLOT_OCCUPIED,
    SLOT_EMPTY,
}

@Singleton
class SailingOwnershipService
@Inject
constructor(private val validator: SailingOwnershipValidator) {
    fun add(player: Player, slot: Int, boatTypeKey: String): SailingOwnershipMutationResult =
        add(SailingOwnershipState.reference(player), slot, boatTypeKey) {
            PlayerPersistenceHints.notify(player)
        }

    internal fun add(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        slot: Int,
        boatTypeKey: String,
        onChanged: () -> Unit = {},
    ): SailingOwnershipMutationResult {
        if (slot !in SailingOwnershipValidator.OWNED_BOAT_SLOTS) {
            return SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.INVALID_SLOT)
        }
        if (!validator.isKnownBoatType(boatTypeKey)) {
            return SailingOwnershipMutationResult.Rejected(
                SailingOwnershipRejection.UNKNOWN_BOAT_TYPE
            )
        }
        return mutate(reference, onChanged) { current ->
            if (current.boats.any { it.slot == slot }) {
                return@mutate Edit.Rejected(SailingOwnershipRejection.SLOT_OCCUPIED)
            }
            Edit.Changed(validator.validate(current.boats + SailingOwnedBoat(slot, boatTypeKey)))
        }
    }

    fun replace(player: Player, slot: Int, boatTypeKey: String): SailingOwnershipMutationResult =
        replace(SailingOwnershipState.reference(player), slot, boatTypeKey) {
            PlayerPersistenceHints.notify(player)
        }

    internal fun replace(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        slot: Int,
        boatTypeKey: String,
        onChanged: () -> Unit = {},
    ): SailingOwnershipMutationResult {
        if (slot !in SailingOwnershipValidator.OWNED_BOAT_SLOTS) {
            return SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.INVALID_SLOT)
        }
        if (!validator.isKnownBoatType(boatTypeKey)) {
            return SailingOwnershipMutationResult.Rejected(
                SailingOwnershipRejection.UNKNOWN_BOAT_TYPE
            )
        }
        return mutate(reference, onChanged) { current ->
            val old =
                current.boats.firstOrNull { it.slot == slot }
                    ?: return@mutate Edit.Rejected(SailingOwnershipRejection.SLOT_EMPTY)
            if (old.boatTypeKey == boatTypeKey) {
                return@mutate Edit.NoChange
            }
            val next =
                current.boats.map {
                    if (it.slot == slot) SailingOwnedBoat(slot, boatTypeKey) else it
                }
            Edit.Changed(validator.validate(next))
        }
    }

    fun remove(player: Player, slot: Int): SailingOwnershipMutationResult =
        remove(SailingOwnershipState.reference(player), slot) {
            PlayerPersistenceHints.notify(player)
        }

    internal fun remove(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        slot: Int,
        onChanged: () -> Unit = {},
    ): SailingOwnershipMutationResult {
        if (slot !in SailingOwnershipValidator.OWNED_BOAT_SLOTS) {
            return SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.INVALID_SLOT)
        }
        return mutate(reference, onChanged) { current ->
            if (current.boats.none { it.slot == slot }) return@mutate Edit.NoChange
            Edit.Changed(validator.validate(current.boats.filterNot { it.slot == slot }))
        }
    }

    fun swap(player: Player, firstSlot: Int, secondSlot: Int): SailingOwnershipMutationResult =
        swap(SailingOwnershipState.reference(player), firstSlot, secondSlot) {
            PlayerPersistenceHints.notify(player)
        }

    internal fun swap(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        firstSlot: Int,
        secondSlot: Int,
        onChanged: () -> Unit = {},
    ): SailingOwnershipMutationResult {
        if (
            firstSlot !in SailingOwnershipValidator.OWNED_BOAT_SLOTS ||
                secondSlot !in SailingOwnershipValidator.OWNED_BOAT_SLOTS
        ) {
            return SailingOwnershipMutationResult.Rejected(SailingOwnershipRejection.INVALID_SLOT)
        }
        if (firstSlot == secondSlot) return SailingOwnershipMutationResult.NoChange
        return mutate(reference, onChanged) { current ->
            if (current.boats.none { it.slot == firstSlot || it.slot == secondSlot }) {
                return@mutate Edit.NoChange
            }
            val next =
                current.boats.map { boat ->
                    when (boat.slot) {
                        firstSlot -> boat.copy(slot = secondSlot)
                        secondSlot -> boat.copy(slot = firstSlot)
                        else -> boat
                    }
                }
            Edit.Changed(validator.validate(next))
        }
    }

    fun clear(player: Player): SailingOwnershipMutationResult =
        clear(SailingOwnershipState.reference(player)) { PlayerPersistenceHints.notify(player) }

    internal fun clear(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        onChanged: () -> Unit = {},
    ): SailingOwnershipMutationResult =
        mutate(reference, onChanged) { current ->
            if (current.boats.isEmpty()) Edit.NoChange
            else Edit.Changed(SailingOwnershipSnapshot.EMPTY)
        }

    private inline fun mutate(
        reference: AtomicReference<SailingOwnershipSnapshot>,
        onChanged: () -> Unit,
        edit: (SailingOwnershipSnapshot) -> Edit,
    ): SailingOwnershipMutationResult {
        while (true) {
            val current = reference.get()
            when (val decision = edit(current)) {
                Edit.NoChange -> return SailingOwnershipMutationResult.NoChange
                is Edit.Rejected -> return SailingOwnershipMutationResult.Rejected(decision.reason)
                is Edit.Changed ->
                    if (reference.compareAndSet(current, decision.snapshot)) {
                        onChanged()
                        return SailingOwnershipMutationResult.Changed(decision.snapshot)
                    }
            }
        }
    }

    private sealed interface Edit {
        data object NoChange : Edit

        data class Rejected(val reason: SailingOwnershipRejection) : Edit

        data class Changed(val snapshot: SailingOwnershipSnapshot) : Edit
    }
}
