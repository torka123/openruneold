package org.rsmod.content.bosses.yama

internal data class YamaRecordSnapshot(
    val killCount: Int,
    val personalBestTicks: Int?,
    val recentKillTimes: List<Int>,
    val fourKillAverageTicks: Int?,
)

internal object YamaRecords {
    const val RECENT_KILL_LIMIT = 4
    const val IMP_APPROACH_TICKS = 2
    const val IMP_DESTROY_TICKS = 2
    const val IMP_SAFETY_LIFETIME_TICKS = 200

    fun snapshot(
        killCount: Int?,
        personalBestTicks: Int?,
        recentKillTimes: List<Int>?,
    ): YamaRecordSnapshot {
        val recent = sanitizeRecent(recentKillTimes)
        return YamaRecordSnapshot(
            killCount = killCount?.coerceAtLeast(0) ?: 0,
            personalBestTicks = personalBestTicks?.takeIf { it > 0 },
            recentKillTimes = recent,
            fourKillAverageTicks = rollingFourKillAverage(recent),
        )
    }

    fun recordSuccess(
        killCount: Int?,
        personalBestTicks: Int?,
        recentKillTimes: List<Int>?,
        killTimeTicks: Int,
    ): YamaRecordSnapshot {
        require(killTimeTicks > 0) { "Yama kill time must be positive." }
        val current = snapshot(killCount, personalBestTicks, recentKillTimes)
        val recent = sanitizeRecent(current.recentKillTimes + killTimeTicks)
        return YamaRecordSnapshot(
            killCount =
                (current.killCount.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            personalBestTicks = minOf(current.personalBestTicks ?: killTimeTicks, killTimeTicks),
            recentKillTimes = recent,
            fourKillAverageTicks = rollingFourKillAverage(recent),
        )
    }

    fun rollingFourKillAverage(recentKillTimes: List<Int>): Int? {
        val recent = sanitizeRecent(recentKillTimes)
        if (recent.size < RECENT_KILL_LIMIT) return null
        return (recent.sumOf(Int::toLong) / RECENT_KILL_LIMIT)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun formatTime(ticks: Int?): String {
        if (ticks == null || ticks <= 0) return "--"
        val totalTenths = ticks.toLong() * 6L
        val minutes = totalTenths / 600L
        val seconds = (totalTenths % 600L) / 10L
        val tenths = totalTenths % 10L
        return "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
    }

    private fun sanitizeRecent(values: List<Int>?): List<Int> =
        values.orEmpty().filter { it > 0 }.takeLast(RECENT_KILL_LIMIT)
}
