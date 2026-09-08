@file:OptIn(org.rsmod.annotations.InternalApi::class, dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.clanmayhem

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.attr.ItemSandboxAttributes
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal val CLAN_MAYHEM_SNAPSHOT: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_snapshot_v1")

/**
 * Durable, commit-before-clear player state. The packed list deliberately contains only primitive
 * values supported by the normal character attribute persistence pipeline.
 */
@Singleton
internal class ClanMayhemSnapshotService
@Inject
constructor(private val collision: CollisionFlagMap) {
    private val log = InlineLogger()

    fun hasUnresolved(player: Player): Boolean =
        player.attr.has(CLAN_MAYHEM_SNAPSHOT) || player.attr[ItemSandboxAttributes.active] == true

    fun capture(player: Player, matchId: UUID): Boolean {
        if (hasUnresolved(player)) return false
        if (
            !ClanMayhemEntryRules.canEnter(
                inventoryEmpty = player.inv.isEmpty(),
                equipmentEmpty = player.worn.isEmpty(),
            )
        )
            return false
        val encoded = ClanMayhemSnapshotCodec.encode(player, matchId)
        player.attr[CLAN_MAYHEM_SNAPSHOT] = encoded
        player.attr[ItemSandboxAttributes.active] = true
        return true
    }

    fun matchId(player: Player): UUID? =
        ClanMayhemSnapshotCodec.decode(player.attr[CLAN_MAYHEM_SNAPSHOT])?.matchId

    /** Removes the persistent marker only after every restore operation succeeds. */
    fun restore(player: Player): Boolean {
        val packed =
            player.attr[CLAN_MAYHEM_SNAPSHOT]
                ?: run {
                    player.attr.remove(ItemSandboxAttributes.active)
                    return true
                }
        val snapshot = ClanMayhemSnapshotCodec.decode(packed)
        if (snapshot == null) {
            log.error {
                "[ClanMayhem] Refusing to discard corrupt snapshot for account=${player.accountId}"
            }
            return false
        }
        return try {
            restoreInventory(player.inv, snapshot.inventory)
            restoreInventory(player.worn, snapshot.equipment)
            player.vars.backing.clear()
            player.vars.backing.putAll(snapshot.vars)
            for ((internal, stat) in STAT_INTERNALS.zip(snapshot.stats)) {
                player.statMap.setXP(internal, stat.xp)
                player.statMap.setBaseLevel(internal, stat.base.toByte())
                player.statMap.setCurrentLevel(internal, stat.current.toByte())
                player.markStatUpdate(internal)
            }
            player.runEnergy = snapshot.runEnergy
            UpdateRun.energy(player, snapshot.runEnergy)
            player.appearance.combatLevel = snapshot.combatLevel
            player.skullIcon = snapshot.skullIcon.takeUnless { it == NONE }
            player.overheadIcon = snapshot.overheadIcon.takeUnless { it == NONE }
            player.transmog =
                snapshot.transmogNpcId.takeUnless { it == NONE }?.let(ServerCacheManager::getNpc)
            player.rebuildAppearance()
            PlayerInterfaceUpdates.updateCombatLevel(player)
            PlayerInterfaceUpdates.updateCombatTab(player)
            PathingEntityCommon.telejump(player, collision, snapshot.coord)
            player.attr.remove(CLAN_MAYHEM_SNAPSHOT)
            player.attr.remove(ItemSandboxAttributes.active)
            true
        } catch (error: Throwable) {
            log.error(error) {
                "[ClanMayhem] Snapshot restore failed; marker retained account=${player.accountId} match=${snapshot.matchId}"
            }
            false
        }
    }

    private fun restoreInventory(inventory: Inventory, values: Array<InvObj?>) {
        require(inventory.size == values.size) { "Inventory capacity changed during Clan Mayhem." }
        for (slot in values.indices) inventory[slot] = values[slot]?.let(::InvObj)
    }
}

internal data class ClanMayhemStatSnapshot(val xp: Int, val base: Int, val current: Int)

internal data class ClanMayhemPlayerSnapshot(
    val matchId: UUID,
    val coord: CoordGrid,
    val inventory: Array<InvObj?>,
    val equipment: Array<InvObj?>,
    val vars: Map<Int, Int>,
    val stats: List<ClanMayhemStatSnapshot>,
    val runEnergy: Int,
    val combatLevel: Int,
    val skullIcon: Int,
    val overheadIcon: Int,
    val transmogNpcId: Int,
)

internal object ClanMayhemSnapshotCodec {
    private const val MAGIC = 0x434D_0001

    fun encode(player: Player, matchId: UUID): MutableList<Int> =
        buildList {
                add(MAGIC)
                addLong(matchId.mostSignificantBits)
                addLong(matchId.leastSignificantBits)
                add(player.coords.packed)
                add(player.runEnergy)
                add(player.combatLevel)
                add(player.skullIcon ?: NONE)
                add(player.overheadIcon ?: NONE)
                add(player.transmog?.id ?: NONE)
                addInventory(player.inv)
                addInventory(player.worn)
                val vars = player.vars.backing.entries.sortedBy { it.key }
                add(vars.size)
                for ((key, value) in vars) {
                    add(key)
                    add(value)
                }
                add(STAT_INTERNALS.size)
                for (internal in STAT_INTERNALS) {
                    add(player.statMap.getXP(internal))
                    add(player.statMap.getBaseLevel(internal).toInt() and 0xff)
                    add(player.statMap.getCurrentLevel(internal).toInt() and 0xff)
                }
            }
            .toMutableList()

    fun decode(values: List<Int>?): ClanMayhemPlayerSnapshot? {
        if (values.isNullOrEmpty()) return null
        return runCatching {
                val reader = IntReader(values)
                require(reader.next() == MAGIC)
                val matchId = UUID(reader.nextLong(), reader.nextLong())
                val coord = CoordGrid(reader.next())
                val runEnergy = reader.next()
                val combatLevel = reader.next()
                val skullIcon = reader.next()
                val overheadIcon = reader.next()
                val transmogNpcId = reader.next()
                val inventory = reader.inventory()
                val equipment = reader.inventory()
                val varCount = reader.next().also { require(it in 0..MAX_VARS) }
                val vars = LinkedHashMap<Int, Int>(varCount)
                repeat(varCount) { vars[reader.next()] = reader.next() }
                require(reader.next() == STAT_INTERNALS.size)
                val stats =
                    List(STAT_INTERNALS.size) {
                        ClanMayhemStatSnapshot(reader.next(), reader.next(), reader.next())
                    }
                require(reader.finished())
                ClanMayhemPlayerSnapshot(
                    matchId,
                    coord,
                    inventory,
                    equipment,
                    vars,
                    stats,
                    runEnergy,
                    combatLevel,
                    skullIcon,
                    overheadIcon,
                    transmogNpcId,
                )
            }
            .getOrNull()
    }

    private fun MutableList<Int>.addLong(value: Long) {
        add((value ushr 32).toInt())
        add(value.toInt())
    }

    private fun MutableList<Int>.addInventory(inventory: Inventory) {
        add(inventory.size)
        for (slot in 0 until inventory.size) {
            val obj = inventory[slot]
            add(obj?.id ?: NONE)
            add(obj?.count ?: 0)
            add(obj?.vars ?: 0)
        }
    }

    private class IntReader(private val values: List<Int>) {
        private var index = 0

        fun next(): Int = values[index++]

        fun nextLong(): Long = (next().toLong() shl 32) or (next().toLong() and 0xffff_ffffL)

        fun inventory(): Array<InvObj?> {
            val size = next().also { require(it in 0..MAX_INVENTORY_SIZE) }
            return Array(size) {
                val id = next()
                val count = next()
                val vars = next()
                if (id == NONE) null else InvObj(id, count, vars)
            }
        }

        fun finished(): Boolean = index == values.size
    }
}

private const val NONE = -1
private const val MAX_VARS = 20_000
private const val MAX_INVENTORY_SIZE = 2_000
private val STAT_INTERNALS =
    listOf(
        "stat.attack",
        "stat.strength",
        "stat.defence",
        "stat.hitpoints",
        "stat.ranged",
        "stat.magic",
        "stat.prayer",
    )
