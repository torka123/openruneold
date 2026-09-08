package org.rsmod.content.minigames.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/** Raid-only gathering and shared storage backed by native revision-240 CoX items. */
@Singleton
internal class CoxSupplies @Inject constructor(private val random: GameRandom) {
    private val storage = mutableMapOf<InstanceId, MutableMap<Int, Int>>()

    fun interact(player: Player, raid: CoxRaidSession, locId: Int): Boolean =
        when (locId) {
            CoxLocs.GOURD_TREE ->
                gather(player, EMPTY_GOURD, 1, 2_280, "You pick a gourd and tear off its top.")
            CoxLocs.ENERGY_WELL -> fillGourd(player)
            CoxLocs.WEEDS ->
                gather(
                    player,
                    SEEDS[random.of(SEEDS.indices)],
                    1,
                    2_273,
                    "You rake the weeds and find a raid herb seed.",
                )
            CoxLocs.HERB_PATCH ->
                gather(
                    player,
                    GRIMY_HERBS[random.of(GRIMY_HERBS.indices)],
                    1,
                    2_282,
                    "You harvest a raid herb.",
                )
            CoxLocs.SPIRIT_TENDRILS ->
                gather(player, CAVE_WORMS, 3, 911, "You gather cave worms for bait.")
            CoxLocs.FISHING_SPOT -> fish(player)
            CoxLocs.STORAGE_UNIT -> useStorage(player, raid)
            else -> false
        }

    fun cleanupInventory(player: Player) {
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            if (obj.id in RAID_ONLY_ITEMS) {
                player.invDel(player.inv, obj.id, obj.count, slot = slot)
            }
        }
    }

    fun cleanup(raid: CoxRaidSession) {
        storage.remove(raid.instance.id)
    }

    fun verifyDefinitions(itemLookup: (Int) -> Boolean) {
        RAID_ONLY_ITEMS.forEach { require(itemLookup(it)) { "Missing CoX supply item $it" } }
    }

    private fun fillGourd(player: Player): Boolean {
        if (!player.inv.any { it?.id == EMPTY_GOURD }) {
            player.mes("You need an empty gourd vial to collect water.")
            return false
        }
        val removed = player.invDel(player.inv, EMPTY_GOURD, 1)
        if (!removed.success) return false
        check(player.invAdd(player.inv, WATER_GOURD, 1).success)
        player.anim(832)
        player.mes("You fill the gourd vial with water.")
        return true
    }

    private fun fish(player: Player): Boolean {
        if (!player.inv.any { it?.id == CAVE_WORMS }) {
            player.mes("You need cave worms to fish here.")
            return false
        }
        val fish = RAW_FISH[random.of(RAW_FISH.indices)]
        if (!player.invAdd(player.inv, fish, 1).success) {
            player.mes("Your inventory is too full to hold a fish.")
            return false
        }
        player.invDel(player.inv, CAVE_WORMS, 1)
        player.anim(623)
        player.mes("You catch a raid fish.")
        return true
    }

    /**
     * The stock storage interface is client-driven and has no server container in this project.
     * This preserves OSRS shared-party semantics without inventing a persistent global bank:
     * clicking with supplies deposits them; clicking empty-handed withdraws what fits.
     */
    private fun useStorage(player: Player, raid: CoxRaidSession): Boolean {
        val shared = storage.getOrPut(raid.instance.id) { linkedMapOf() }
        val carried = player.inv.mapNotNull { it }.filter { it.id in RAID_ONLY_ITEMS }
        if (carried.isNotEmpty()) {
            carried.forEach { obj ->
                val count = obj.count
                if (player.invDel(player.inv, obj.id, count).success) {
                    shared[obj.id] = (shared[obj.id] ?: 0) + count
                }
            }
            player.mes("You deposit your raid supplies into the shared storage unit.")
            return true
        }
        if (shared.isEmpty()) {
            player.mes("The shared raid storage unit is empty.")
            return false
        }
        var withdrawn = 0
        for ((id, count) in shared.toMap()) {
            var remaining = count
            while (remaining > 0 && player.invAdd(player.inv, id, 1).success) {
                remaining--
                withdrawn++
            }
            if (remaining == 0) shared.remove(id) else shared[id] = remaining
        }
        player.mes(
            if (withdrawn > 0) "You withdraw $withdrawn raid supplies."
            else "Your inventory is too full."
        )
        return withdrawn > 0
    }

    private fun gather(
        player: Player,
        id: Int,
        count: Int,
        animation: Int,
        message: String,
    ): Boolean {
        if (!player.invAdd(player.inv, id, count).success) {
            player.mes("Your inventory is too full to gather that.")
            return false
        }
        player.anim(animation)
        player.mes(message)
        return true
    }

    private companion object {
        const val EMPTY_GOURD = 20_800
        const val WATER_GOURD = 20_801
        const val CAVE_WORMS = 20_853
        val RAW_FISH = intArrayOf(20_855, 20_857, 20_859, 20_861, 20_863, 20_865, 20_867)
        val GRIMY_HERBS = intArrayOf(20_901, 20_904, 20_907)
        val SEEDS = intArrayOf(20_903, 20_906, 20_909)
        val SECONDARIES = intArrayOf(20_910, 20_911, 20_912)
        val POTIONS = (20_916..20_996 step 4).toList().toIntArray()
        val RAID_ONLY_ITEMS =
            setOf(EMPTY_GOURD, WATER_GOURD, CAVE_WORMS) +
                RAW_FISH.toSet() +
                GRIMY_HERBS.toSet() +
                SEEDS.toSet() +
                SECONDARIES.toSet() +
                POTIONS.toSet() +
                setOf(20_884, 20_885)
    }
}
