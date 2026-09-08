package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * Persistent, per-player Path of Het pickaxe storage. The cache's controlling varbit is backed by a
 * permanent varp, so the same value drives every statue transform after logout and rejoin.
 */
@Singleton
internal class ToaHetPickaxeStorage @Inject constructor() {
    private data class Candidate(val internalName: String, val source: Inventory, val slot: Int)

    private val storageVarbit by lazy {
        requireNotNull(ServerCacheManager.getVarbit(HetPickaxeStorageRules.STORAGE_VARBIT))
    }

    fun validateAssets() {
        val varbit = storageVarbit
        require(varbit.baseVar.id == HetPickaxeStorageRules.STORAGE_VARP) {
            "Het pickaxe storage varbit now uses varp ${varbit.baseVar.id}."
        }
        require(varbit.baseVar.scope == VarpLifetime.Perm) {
            "Het pickaxe storage varp must remain persistent."
        }
        ToaLocs.hetPickaxeStatues.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 Het pickaxe statue loc $id."
                }
            require(type.actions.getOpOrNull(0) == "Take-pickaxe") {
                "Revision-240 Het statue $id op1 is not Take-pickaxe."
            }
            require(type.actions.getOpOrNull(1) == "Deposit-pickaxe") {
                "Revision-240 Het statue $id op2 is not Deposit-pickaxe."
            }
        }
        HetPickaxeStorageRules.entries.forEach { entry ->
            val id = RSCM.getRSCM(entry.internalName)
            require(ServerCacheManager.getItem(id)?.internalName == entry.internalName) {
                "Missing revision-240 Het storage item ${entry.internalName}."
            }
        }
    }

    /** Re-sends the persisted value so every multivar statue has the correct per-player model. */
    fun sync(player: Player) {
        VarPlayerIntMapSetter.set(player, storageVarbit, player.vars[storageVarbit])
    }

    fun take(player: Player): Boolean {
        val value = player.vars[storageVarbit]
        val stored = HetPickaxeStorageRules.itemFor(value)
        if (stored == null) {
            player.mes("The statue is not holding a pickaxe.")
            return false
        }
        if (player.invAdd(player.inv, stored).failure) {
            player.mes("You need a free inventory slot to take the pickaxe.")
            return false
        }
        // OSRS always falls back to another free bronze pickaxe after the stored tool is taken.
        VarPlayerIntMapSetter.set(player, storageVarbit, HetPickaxeStorageRules.FREE_BRONZE_VALUE)
        player.mes("You take the pickaxe from Het's statue.")
        return true
    }

    fun deposit(player: Player): Boolean {
        val candidate = findBestCandidate(player)
        if (candidate == null) {
            player.mes("You do not have a pickaxe that can be stored here.")
            return false
        }
        val depositValue =
            requireNotNull(HetPickaxeStorageRules.storageValueFor(candidate.internalName))
        val storedValue = player.vars[storageVarbit]
        val returned = HetPickaxeStorageRules.itemForSwap(storedValue)

        if (
            player.invDel(candidate.source, candidate.internalName, slot = candidate.slot).failure
        ) {
            return false
        }
        if (returned != null && player.invAdd(player.inv, returned).failure) {
            check(
                player
                    .invAdd(candidate.source, candidate.internalName, slot = candidate.slot)
                    .success
            ) {
                "Unable to roll back a failed Het pickaxe deposit."
            }
            player.mes("You need a free inventory slot to swap the stored pickaxe.")
            return false
        }

        VarPlayerIntMapSetter.set(player, storageVarbit, depositValue)
        player.mes("You deposit your pickaxe in Het's statue.")
        return true
    }

    private fun findBestCandidate(player: Player): Candidate? =
        sequenceOf(player.worn, player.inv)
            .flatMap { inventory ->
                inventory.withIndex().mapNotNull { (slot, obj) ->
                    obj ?: return@mapNotNull null
                    val internal =
                        ServerCacheManager.getItem(obj.id)?.internalName ?: return@mapNotNull null
                    if (HetPickaxeStorageRules.storageValueFor(internal) == null) {
                        return@mapNotNull null
                    }
                    Candidate(internal, inventory, slot)
                }
            }
            .maxByOrNull { HetPickaxeStorageRules.rank(it.internalName) }
}

internal object HetPickaxeStorageRules {
    const val STORAGE_VARBIT = 14440
    const val STORAGE_VARP = 3635

    /** Value 23 deliberately resolves to the bronze statue and represents OSRS's free fallback. */
    const val FREE_BRONZE_VALUE = 23

    data class Entry(val value: Int, val internalName: String, val rank: Int)

    val entries =
        listOf(
            Entry(0, "obj.bronze_pickaxe", 0),
            Entry(1, "obj.iron_pickaxe", 1),
            Entry(2, "obj.steel_pickaxe", 2),
            Entry(3, "obj.black_pickaxe", 3),
            Entry(4, "obj.mithril_pickaxe", 4),
            Entry(5, "obj.adamant_pickaxe", 5),
            Entry(6, "obj.rune_pickaxe", 6),
            Entry(7, "obj.trail_gilded_pickaxe", 6),
            Entry(8, "obj.dragon_pickaxe", 7),
            Entry(9, "obj.dragon_pickaxe_pretty", 7),
            Entry(10, "obj.zalcano_pickaxe", 7),
            Entry(11, "obj.infernal_pickaxe", 8),
            Entry(12, "obj.infernal_pickaxe_empty", 8),
            Entry(13, "obj.3a_pickaxe", 9),
            Entry(14, "obj.crystal_pickaxe", 10),
            Entry(15, "obj.crystal_pickaxe_inactive", 10),
            Entry(16, "obj.league_trailblazer_pickaxe", 7),
            Entry(17, "obj.trailblazer_pickaxe", 8),
            Entry(18, "obj.trailblazer_pickaxe_empty", 8),
            Entry(19, "obj.trailblazer_pickaxe_no_infernal", 7),
            Entry(20, "obj.trailblazer_reloaded_pickaxe", 8),
            Entry(21, "obj.trailblazer_reloaded_pickaxe_empty", 8),
            Entry(22, "obj.trailblazer_reloaded_pickaxe_no_infernal", 7),
        )

    private val byValue = entries.associateBy(Entry::value)
    private val byInternal = entries.associateBy(Entry::internalName)

    fun itemFor(value: Int): String? =
        if (value == FREE_BRONZE_VALUE) "obj.bronze_pickaxe" else byValue[value]?.internalName

    /** The default/free bronze is replaced, not returned, when a better pickaxe is deposited. */
    fun itemForSwap(value: Int): String? =
        itemFor(value)?.takeUnless {
            value == FREE_BRONZE_VALUE || value == 0 || it == "obj.bronze_pickaxe"
        }

    fun storageValueFor(internalName: String): Int? = byInternal[internalName]?.value

    fun rank(internalName: String): Int = byInternal[internalName]?.rank ?: -1
}
