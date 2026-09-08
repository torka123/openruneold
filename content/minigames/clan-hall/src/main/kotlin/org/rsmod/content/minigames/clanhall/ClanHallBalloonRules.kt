package org.rsmod.content.minigames.clanhall

import org.rsmod.api.net.social.ClanPartyChestEntry

internal data class ClanHallBalloonPrize(val itemId: Int, val count: Int)

internal class ClanHallBalloonPrizeQueue
private constructor(private val pending: ArrayDeque<PendingPrize>) {
    val remainingBalloonCount: Long
        get() = pending.sumOf { prize -> if (prize.stackable) 1L else prize.quantity }

    val isEmpty: Boolean
        get() = pending.isEmpty()

    fun takeNext(): ClanHallBalloonPrize? {
        val prize = pending.firstOrNull() ?: return null
        return if (prize.stackable) {
            pending.removeFirst()
            ClanHallBalloonPrize(prize.itemId, prize.quantity.toInt())
        } else {
            prize.quantity--
            if (prize.quantity == 0L) pending.removeFirst()
            ClanHallBalloonPrize(prize.itemId, 1)
        }
    }

    companion object {
        fun from(
            entries: List<ClanPartyChestEntry>,
            isStackable: (Int) -> Boolean,
        ): ClanHallBalloonPrizeQueue {
            val pending = ArrayDeque<PendingPrize>()
            entries
                .asSequence()
                .filter { it.itemId >= 0 && it.quantity in 1..Int.MAX_VALUE.toLong() }
                .sortedBy(ClanPartyChestEntry::itemId)
                .forEach { pending += PendingPrize(it.itemId, it.quantity, isStackable(it.itemId)) }
            return ClanHallBalloonPrizeQueue(pending)
        }
    }

    private data class PendingPrize(val itemId: Int, var quantity: Long, val stackable: Boolean)
}

internal object ClanHallBalloonRules {
    const val LEVER_COST = 1_000L
    const val MAX_ACTIVE_BALLOONS = 32
    const val POPPED_DURATION_TICKS = 2
    const val PRIZE_GROUND_DURATION_TICKS = 500

    fun activeId(sequence: Int): Int =
        ClanHallAssets.PARTY_BALLOONS.first +
            Math.floorMod(sequence, ClanHallAssets.PARTY_BALLOONS.count())

    fun poppedId(activeId: Int): Int {
        require(activeId in ClanHallAssets.PARTY_BALLOONS)
        return activeId + ClanHallAssets.PARTY_BALLOONS.count()
    }

    fun <T> safeTiles(
        candidates: Iterable<T>,
        limit: Int = MAX_ACTIVE_BALLOONS,
        isSafe: (T) -> Boolean,
    ): List<T> {
        if (limit <= 0) return emptyList()
        return candidates.asSequence().filter(isSafe).distinct().take(limit).toList()
    }
}
