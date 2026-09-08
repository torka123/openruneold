@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.corruptedgauntlet

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.attr.ItemSandboxAttributes
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

internal val CORRUPTED_GAUNTLET_ITEM_SNAPSHOT: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_item_snapshot_v1")

/**
 * Durable ownership of the player's carried items while Corrupted Gauntlet is active.
 *
 * The snapshot is written before either carried inventory is cleared. Restore always clears the
 * temporary Gauntlet items before putting the saved items back, which makes reconnect recovery and
 * repeated restore attempts idempotent. Bank access remains blocked by [ItemSandboxAttributes]
 * until the complete restore succeeds.
 */
@Singleton
internal class CorruptedGauntletSnapshotService
@Inject
constructor(private val accountSaving: AccountSavingService) {
    private val logger = InlineLogger()

    fun hasUnresolved(player: Player): Boolean = player.attr.has(CORRUPTED_GAUNTLET_ITEM_SNAPSHOT)

    fun blockedByAnotherSandbox(player: Player): Boolean =
        player.attr[ItemSandboxAttributes.active] == true && !hasUnresolved(player)

    fun runId(player: Player): UUID? = snapshot(player)?.runId

    fun returnCoord(player: Player): CoordGrid? = snapshot(player)?.returnCoord

    fun ownsStoredItem(player: Player, objId: Int): Boolean {
        val stored = snapshot(player) ?: return false
        return stored.inventory.any { it?.id == objId } || stored.equipment.any { it?.id == objId }
    }

    /** Durably commits the marker and empty carried inventories as one account state. */
    fun capture(player: Player, runId: UUID): Boolean {
        if (hasUnresolved(player) || player.attr[ItemSandboxAttributes.active] == true) return false
        val encoded = CorruptedGauntletSnapshotCodec.encode(player, runId)
        val rollback = rollbackState(player)
        return runCatching {
                accountSaving.commitPlayerMutationBlocking(
                    player,
                    mutation = {
                        player.attr[CORRUPTED_GAUNTLET_ITEM_SNAPSHOT] = encoded
                        player.attr[ItemSandboxAttributes.active] = true
                        clear(player.inv)
                        clear(player.worn)
                        true
                    },
                    rollback = { restoreRollback(player, rollback) },
                )
            }
            .getOrElse { error ->
                logger.error(error) { "Corrupted Gauntlet snapshot capture failed for $player." }
                false
            }
    }

    /** Durably restores carried items and removes the marker as one account state. */
    fun restore(player: Player): Boolean {
        val packed = player.attr[CORRUPTED_GAUNTLET_ITEM_SNAPSHOT] ?: return false
        val stored = CorruptedGauntletSnapshotCodec.decode(packed)
        if (stored == null) {
            logger.error { "Corrupted Gauntlet snapshot is corrupt for $player; marker retained." }
            return false
        }
        if (player.inv.size != stored.inventory.size || player.worn.size != stored.equipment.size) {
            logger.error {
                "Corrupted Gauntlet snapshot size changed for $player; marker retained."
            }
            return false
        }
        val rollback = rollbackState(player)
        return runCatching {
                accountSaving.commitPlayerMutationBlocking(
                    player,
                    mutation = {
                        restore(player.inv, stored.inventory)
                        restore(player.worn, stored.equipment)
                        player.attr.remove(CORRUPTED_GAUNTLET_ITEM_SNAPSHOT)
                        player.attr.remove(ItemSandboxAttributes.active)
                        true
                    },
                    rollback = { restoreRollback(player, rollback) },
                )
            }
            .getOrElse { error ->
                logger.error(error) {
                    "Corrupted Gauntlet snapshot restore failed for $player; marker retained."
                }
                false
            }
    }

    private fun snapshot(player: Player): CorruptedGauntletItemSnapshot? =
        CorruptedGauntletSnapshotCodec.decode(player.attr[CORRUPTED_GAUNTLET_ITEM_SNAPSHOT])

    private fun clear(inventory: Inventory) {
        for (slot in 0 until inventory.size) inventory[slot] = null
    }

    private fun rollbackState(player: Player) =
        SnapshotMutationRollback(
            snapshot = player.attr[CORRUPTED_GAUNTLET_ITEM_SNAPSHOT]?.toMutableList(),
            sandboxActive = player.attr[ItemSandboxAttributes.active],
            inventory = copy(player.inv),
            equipment = copy(player.worn),
        )

    private fun restoreRollback(player: Player, rollback: SnapshotMutationRollback) {
        restore(player.inv, rollback.inventory)
        restore(player.worn, rollback.equipment)
        restoreAttribute(player, CORRUPTED_GAUNTLET_ITEM_SNAPSHOT, rollback.snapshot)
        restoreAttribute(player, ItemSandboxAttributes.active, rollback.sandboxActive)
    }

    private fun copy(inventory: Inventory): Array<InvObj?> =
        Array(inventory.size) { slot -> inventory[slot]?.let(::InvObj) }

    private fun restore(inventory: Inventory, saved: Array<InvObj?>) {
        require(inventory.size == saved.size)
        for (slot in 0 until inventory.size) inventory[slot] = saved[slot]?.let(::InvObj)
    }

    private fun <T> restoreAttribute(player: Player, key: AttributeKey<T>, value: T?) {
        if (value == null) player.attr.remove(key) else player.attr[key] = value
    }

    private data class SnapshotMutationRollback(
        val snapshot: MutableList<Int>?,
        val sandboxActive: Boolean?,
        val inventory: Array<InvObj?>,
        val equipment: Array<InvObj?>,
    )
}

internal data class CorruptedGauntletItemSnapshot(
    val runId: UUID,
    val returnCoord: CoordGrid,
    val inventory: Array<InvObj?>,
    val equipment: Array<InvObj?>,
) {
    override fun equals(other: Any?): Boolean =
        other is CorruptedGauntletItemSnapshot &&
            runId == other.runId &&
            returnCoord == other.returnCoord &&
            inventory.contentEquals(other.inventory) &&
            equipment.contentEquals(other.equipment)

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + returnCoord.hashCode()
        result = 31 * result + inventory.contentHashCode()
        return 31 * result + equipment.contentHashCode()
    }
}

internal object CorruptedGauntletSnapshotCodec {
    private const val MAGIC = 0x4347_5301
    private const val NONE = -1
    private const val MAX_INVENTORY_SIZE = 2_000

    fun encode(player: Player, runId: UUID): MutableList<Int> =
        encode(
            CorruptedGauntletItemSnapshot(
                runId = runId,
                returnCoord = player.coords,
                inventory = copy(player.inv),
                equipment = copy(player.worn),
            )
        )

    fun encode(snapshot: CorruptedGauntletItemSnapshot): MutableList<Int> =
        buildList {
                add(MAGIC)
                addUuid(snapshot.runId)
                add(snapshot.returnCoord.packed)
                addInventory(snapshot.inventory)
                addInventory(snapshot.equipment)
            }
            .toMutableList()

    fun decode(values: List<Int>?): CorruptedGauntletItemSnapshot? {
        if (values.isNullOrEmpty()) return null
        return runCatching {
                val reader = IntReader(values)
                require(reader.next() == MAGIC)
                val runId = reader.uuid()
                val returnCoord = CoordGrid(reader.next())
                val inventory = reader.inventory()
                val equipment = reader.inventory()
                require(reader.finished())
                CorruptedGauntletItemSnapshot(runId, returnCoord, inventory, equipment)
            }
            .getOrNull()
    }

    private fun copy(inventory: Inventory): Array<InvObj?> =
        Array(inventory.size) { slot -> inventory[slot]?.let(::InvObj) }

    private fun MutableList<Int>.addUuid(value: UUID) {
        addLong(value.mostSignificantBits)
        addLong(value.leastSignificantBits)
    }

    private fun MutableList<Int>.addLong(value: Long) {
        add((value ushr 32).toInt())
        add(value.toInt())
    }

    private fun MutableList<Int>.addInventory(inventory: Array<InvObj?>) {
        add(inventory.size)
        inventory.forEach { obj ->
            add(obj?.id ?: NONE)
            add(obj?.count ?: 0)
            add(obj?.vars ?: 0)
        }
    }

    private class IntReader(private val values: List<Int>) {
        private var index = 0

        fun next(): Int = values[index++]

        fun long(): Long = (next().toLong() shl 32) or (next().toLong() and 0xffff_ffffL)

        fun uuid(): UUID = UUID(long(), long())

        fun inventory(): Array<InvObj?> {
            val size = next().also { require(it in 0..MAX_INVENTORY_SIZE) }
            return Array(size) {
                val id = next()
                val count = next()
                val vars = next()
                if (id == NONE) {
                    require(count == 0 && vars == 0)
                    null
                } else {
                    require(count > 0)
                    InvObj(id, count, vars)
                }
            }
        }

        fun finished(): Boolean = index == values.size
    }
}
