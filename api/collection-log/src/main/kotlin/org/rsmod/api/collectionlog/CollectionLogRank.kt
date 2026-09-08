package org.rsmod.api.collectionlog

public data class CollectionLogRank(
    public val index: Int,
    public val key: String,
    public val displayName: String,
    public val structId: Int,
    public val fixedThreshold: Int?,
    public val bookItemId: Int,
    public val staffItemId: Int,
) {
    public val bookInternalName: String
        get() = "obj.collection_log_$key"

    public val staffInternalName: String
        get() = "obj.collection_staff_$key"

    public fun threshold(totalEntries: Int): Int =
        fixedThreshold ?: CollectionLogRanks.gildedThreshold(totalEntries)
}

public object CollectionLogRanks {
    public val all: List<CollectionLogRank> =
        listOf(
            CollectionLogRank(0, "bronze", "Bronze", 1714, 100, 30_579, 30_597),
            CollectionLogRank(1, "iron", "Iron", 1715, 300, 30_581, 30_599),
            CollectionLogRank(2, "steel", "Steel", 1716, 500, 30_583, 30_601),
            CollectionLogRank(3, "black", "Black", 1717, 700, 30_585, 30_603),
            CollectionLogRank(4, "mithril", "Mithril", 1718, 900, 30_587, 30_605),
            CollectionLogRank(5, "adamant", "Adamant", 1719, 1_000, 30_589, 30_607),
            CollectionLogRank(6, "rune", "Rune", 1740, 1_100, 30_591, 30_609),
            CollectionLogRank(7, "dragon", "Dragon", 1741, 1_200, 30_593, 30_611),
            CollectionLogRank(8, "gilded", "Gilded", 1742, null, 30_595, 30_613),
        )

    public fun gildedThreshold(totalEntries: Int): Int =
        (((totalEntries.coerceAtLeast(0).toLong() * 90L) / 100L) / 25L * 25L).toInt()

    public fun highestAchieved(completedEntries: Int, totalEntries: Int): CollectionLogRank? =
        all.lastOrNull { completedEntries >= it.threshold(totalEntries) }

    public fun byStaffItemId(itemId: Int): CollectionLogRank? =
        all.firstOrNull { it.staffItemId == itemId }

    public fun byBookItemId(itemId: Int): CollectionLogRank? =
        all.firstOrNull { it.bookItemId == itemId }
}
