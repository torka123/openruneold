package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid

@Singleton
internal class NexRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val objects: ObjRepository,
    private val players: PlayerList,
    private val collectionLog: CollectionLogService,
) {
    fun reward(
        contributions: Map<PlayerUid, NexContribution>,
        rewarded: MutableSet<PlayerUid>,
        dropTile: org.rsmod.map.CoordGrid,
    ): Int {
        val total = contributions.values.sumOf { it.damage }
        if (total <= 0) return 0
        val eligible =
            contributions.entries
                .filter { NexRules.isEligible(it.value.damage, total) }
                .sortedWith(
                    compareByDescending<Map.Entry<PlayerUid, NexContribution>> { it.value.damage }
                        .thenBy { it.value.firstCycle }
                        .thenBy { it.key.packed }
                )
        val mvpUid = eligible.firstOrNull()?.key
        var count = 0
        for ((uid, contribution) in eligible) {
            if (!rewarded.add(uid)) continue
            val player = uid.resolve(players) ?: continue
            val mvp = uid == mvpUid
            rewardPlayer(player, contribution.damage, total, mvp, dropTile)
            count++
        }
        return count
    }

    private fun rewardPlayer(
        player: Player,
        damage: Int,
        total: Int,
        mvp: Boolean,
        dropTile: org.rsmod.map.CoordGrid,
    ) {
        val killcount = player.vars["varp.total_nex_kills"] + 1
        val awards = mutableListOf<Pair<Int, Int>>()
        val duration = player.lootDropDuration ?: NEX_DROP_DURATION
        fun award(itemId: Int, count: Int) {
            if (count <= 0) return
            drop(player, itemId, count, dropTile, duration)
            awards += itemId to count
        }
        repeat(2) {
            val common = commonPool[random.of(commonPool.size)]
            val base = random.of(common.minimum..common.maximum)
            val quantity = NexRules.commonQuantity(base, damage, total, mvp)
            award(common.itemId, quantity)
            when (common.itemId) {
                SHARK ->
                    award(
                        PRAYER_POTION_4,
                        NexRules.commonQuantity(3, damage, total, mvp),
                    )
                SARADOMIN_BREW_4 ->
                    award(
                        SUPER_RESTORE_4,
                        NexRules.commonQuantity(random.of(1..5), damage, total, mvp),
                    )
            }
        }
        if (mvp) award(BIG_BONES, 1)

        val uniqueThreshold = NexRules.uniqueRollThreshold(damage, total, mvp)
        if (random.of(43_000_000) < uniqueThreshold) {
            val unique = uniqueFor(random.of(12))
            award(unique, 1)
            players
                .filter { it.isSlotAssigned }
                .forEach {
                    it.mes(
                        "<col=ff0000>${player.displayName} received a unique drop from Nex!</col>"
                    )
                }
        }
        if (random.of(82) == 0) award(BLOOD_ESSENCE, random.of(1..2))
        if (random.of(82) < 5) award(NIHIL_SHARD, random.of(80..85))
        if (random.of(209) < 8) award(NIHIL_SHARD, random.of(85..95))
        if (random.of(209) == 0) award(RUNE_SWORD, 1)
        if (random.of(48) == 0) award(ELITE_CLUE_SCROLL, 1)

        val petAwarded = random.of(500) == 0 && player.attr[NEXLING_UNLOCKED] != true
        if (petAwarded) {
            player.attr[NEXLING_UNLOCKED] = true
            player.mes("<col=ff0000>You have a funny feeling like you're being followed.</col>")
            player.mes(
                "Nexling has been permanently unlocked. The follower-call UI is not yet available on this server."
            )
        }

        collectionLog.recordBatch(
            player = player,
            awards = awards,
            source =
                CollectionLogAwardSource(
                    CollectionLogAwardKind.NpcDrop,
                    "nex",
                ),
            eventId = "${player.uid}:$killcount",
        )
        if (petAwarded) {
            collectionLog.record(
                player = player,
                itemId = NEXLING,
                amount = 1,
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.PetGrant,
                        "nexling",
                    ),
                eventId = "${player.uid}:$killcount",
            )
        }
        VarPlayerIntMapSetter.set(player, "varp.total_nex_kills", killcount)
        player.mes(
            "Your Nex kill count is: <col=ff0000>$killcount</col>. " +
                "Contribution: $damage/$total${if (mvp) " (MVP)" else ""}."
        )
    }

    private fun drop(
        player: Player,
        itemId: Int,
        count: Int,
        tile: org.rsmod.map.CoordGrid,
        duration: Int,
    ) {
        if (count <= 0) return
        val type =
            requireNotNull(ServerCacheManager.getItem(itemId)) {
                "Missing revision-240 Nex reward item $itemId"
            }
        objects.add(type, tile, duration, player, count)
    }

    private fun uniqueFor(weight: Int): Int =
        when (weight.coerceIn(0, 11)) {
            0 -> ANCIENT_HILT
            1,
            2 -> NIHIL_HORN
            3,
            4 -> BROKEN_TORVA_HELM
            5,
            6 -> BROKEN_TORVA_BODY
            7,
            8 -> BROKEN_TORVA_LEGS
            else -> ZARYTE_VAMBRACES
        }

    fun validateAssets() {
        (commonPool.map { it.itemId } +
                listOf(
                    BIG_BONES,
                    ANCIENT_HILT,
                    NIHIL_HORN,
                    BROKEN_TORVA_HELM,
                    BROKEN_TORVA_BODY,
                    BROKEN_TORVA_LEGS,
                    ZARYTE_VAMBRACES,
                    BLOOD_ESSENCE,
                    NIHIL_SHARD,
                    RUNE_SWORD,
                    ELITE_CLUE_SCROLL,
                    SHARK,
                    PRAYER_POTION_4,
                    SARADOMIN_BREW_4,
                    SUPER_RESTORE_4,
                    NEXLING,
                ))
            .distinct()
            .forEach { id ->
                requireNotNull(ServerCacheManager.getItem(id)) { "Missing Nex reward item $id" }
            }
    }

    private data class CommonDrop(val itemId: Int, val minimum: Int, val maximum: Int)

    private companion object {
        const val BIG_BONES = 532
        const val ANCIENT_HILT = 26370
        const val NIHIL_HORN = 26372
        const val BROKEN_TORVA_HELM = 26376
        const val BROKEN_TORVA_BODY = 26378
        const val BROKEN_TORVA_LEGS = 26380
        const val ZARYTE_VAMBRACES = 26235
        const val BLOOD_ESSENCE = 26390
        const val NIHIL_SHARD = 26231
        const val RUNE_SWORD = 1289
        const val ELITE_CLUE_SCROLL = 12073
        const val SHARK = 385
        const val PRAYER_POTION_4 = 2434
        const val SARADOMIN_BREW_4 = 6685
        const val SUPER_RESTORE_4 = 3024
        const val NEXLING = 26348

        val commonPool =
            listOf(
                CommonDrop(565, 84, 876),
                CommonDrop(560, 85, 986),
                CommonDrop(566, 86, 592),
                CommonDrop(556, 123, 10_000),
                CommonDrop(554, 123, 10_000),
                CommonDrop(555, 193, 10_000),
                CommonDrop(21930, 12, 283),
                CommonDrop(9245, 11, 29),
                CommonDrop(2, 42, 2_252),
                CommonDrop(573, 6, 100),
                CommonDrop(453, 23, 2_354),
                CommonDrop(451, 2, 30),
                CommonDrop(1603, 3, 80),
                CommonDrop(1601, 3, 64),
                CommonDrop(245, 4, 64),
                CommonDrop(SHARK, 10, 10),
                CommonDrop(SARADOMIN_BREW_4, 1, 10),
                CommonDrop(995, 8_539, 26_748),
                CommonDrop(26356, 6, 39),
            )
    }
}
