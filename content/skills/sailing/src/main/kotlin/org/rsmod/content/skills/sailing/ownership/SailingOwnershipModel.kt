package org.rsmod.content.skills.sailing.ownership

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.attr.AttributeKey
import org.rsmod.content.skills.sailing.SailingBoatDefinitionRegistry
import org.rsmod.game.entity.Player

data class SailingOwnedBoat(
    val slot: Int,
    val boatTypeKey: String,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
    }
}

class SailingOwnershipSnapshot private constructor(boats: List<SailingOwnedBoat>) {
    val boats: List<SailingOwnedBoat> =
        Collections.unmodifiableList(ArrayList(boats.sortedBy(SailingOwnedBoat::slot)))

    override fun equals(other: Any?): Boolean =
        other is SailingOwnershipSnapshot && boats == other.boats

    override fun hashCode(): Int = boats.hashCode()

    override fun toString(): String = "SailingOwnershipSnapshot(boats=$boats)"

    companion object {
        val EMPTY: SailingOwnershipSnapshot = SailingOwnershipSnapshot(emptyList())

        internal fun create(boats: List<SailingOwnedBoat>): SailingOwnershipSnapshot =
            if (boats.isEmpty()) EMPTY else SailingOwnershipSnapshot(boats)
    }
}

data class SailingOwnershipData(val snapshot: SailingOwnershipSnapshot) :
    CharacterDataStage.Segment

@Singleton
class SailingOwnershipValidator internal constructor(private val validBoatTypeKeys: Set<String>) {
    @Inject
    constructor(
        definitions: SailingBoatDefinitionRegistry
    ) : this(definitions.all.mapTo(linkedSetOf()) { it.runtimeType.cacheRow })

    fun isKnownBoatType(boatTypeKey: String): Boolean = boatTypeKey in validBoatTypeKeys

    fun validate(boats: List<SailingOwnedBoat>): SailingOwnershipSnapshot {
        require(boats.size <= MAX_OWNED_BOATS) {
            "A character cannot own more than $MAX_OWNED_BOATS Sailing boats."
        }
        val slots = HashSet<Int>(boats.size)
        for (boat in boats) {
            require(boat.slot in OWNED_BOAT_SLOTS) {
                "Invalid Sailing ownership slot ${boat.slot}."
            }
            require(slots.add(boat.slot)) { "Duplicate Sailing ownership slot ${boat.slot}." }
            require(boat.formatVersion == SailingOwnedBoat.CURRENT_FORMAT_VERSION) {
                "Unsupported Sailing boat format version ${boat.formatVersion}."
            }
            require(isKnownBoatType(boat.boatTypeKey)) {
                "Unknown Sailing boat type `${boat.boatTypeKey}`."
            }
        }
        return SailingOwnershipSnapshot.create(boats)
    }

    companion object {
        const val MAX_OWNED_BOATS: Int = 5
        val OWNED_BOAT_SLOTS: IntRange = 0 until MAX_OWNED_BOATS
    }
}

object SailingOwnershipState {
    private val state =
        AttributeKey<AtomicReference<SailingOwnershipSnapshot>>(persistenceKey = null)

    fun snapshot(player: Player): SailingOwnershipSnapshot =
        player.attr[state]?.get() ?: SailingOwnershipSnapshot.EMPTY

    fun reference(player: Player): AtomicReference<SailingOwnershipSnapshot> =
        player.attr[state]
            ?: AtomicReference(SailingOwnershipSnapshot.EMPTY).also { player.attr[state] = it }

    fun attach(player: Player, snapshot: SailingOwnershipSnapshot) {
        player.attr[state] = AtomicReference(snapshot)
    }
}
