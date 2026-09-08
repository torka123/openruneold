package org.rsmod.content.bosses.zulrah

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class ZulrahRulesTest {
    @Test
    fun `damage cap rerolls each oversized hitsplat independently`() {
        val rolls = ArrayDeque(listOf(45, 50))
        assertEquals(45, ZulrahRules.cappedDamage(73) { rolls.removeFirst() })
        assertEquals(50, ZulrahRules.cappedDamage(51) { rolls.removeFirst() })
        assertEquals(50, ZulrahRules.cappedDamage(50) { error("must not reroll") })
    }

    @Test
    fun `blue style uses two magic rolls for every ranged roll`() {
        assertEquals(
            listOf(ZulrahAttackStyle.MAGIC, ZulrahAttackStyle.MAGIC, ZulrahAttackStyle.RANGED),
            (0..2).map(ZulrahRules::blueStyle),
        )
    }

    @Test
    fun `jad attacks alternate from the selected opening style`() {
        assertEquals(
            listOf(
                ZulrahAttackStyle.RANGED,
                ZulrahAttackStyle.MAGIC,
                ZulrahAttackStyle.RANGED,
                ZulrahAttackStyle.MAGIC,
            ),
            ZulrahRules.jadStyles(4, ZulrahAttackStyle.RANGED),
        )
    }

    @Test
    fun `cloud projectile lands on the southwest origin of its three by three object`() {
        assertEquals(ShrineTile(2268, 3068), ZulrahRules.cloudOrigin(ShrineTile(2269, 3069)))
        assertEquals(ShrineTile(2271, 3068), ZulrahRules.cloudOrigin(ShrineTile(2272, 3069)))
    }

    @Test
    fun `cloud and egg launches use the captured Zulrah attack animation`() {
        assertEquals(ZulrahAssets.ATTACK, ZulrahAssets.SUMMON)
    }

    @Test
    fun `tail pillar safespots remain the four donor and wiki backed shrine tiles`() {
        assertEquals(
            setOf(
                ShrineTile(2263, 3072),
                ShrineTile(2264, 3072),
                ShrineTile(2272, 3072),
                ShrineTile(2273, 3072),
            ),
            ZULRAH_TAIL_SAFESPOTS,
        )
    }

    @Test
    fun `tail swipe is area targeted and rolls 20 through 30`() {
        assertEquals(0, ZulrahRules.tailDamage(false, 10))
        assertEquals(20, ZulrahRules.tailDamage(true, 0))
        assertEquals(30, ZulrahRules.tailDamage(true, 10))
        assertTrue(ZulrahRules.inTailTarget(10, 10, 11, 11))
        assertEquals(false, ZulrahRules.inTailTarget(10, 10, 12, 10))
    }

    @Test
    fun `a new official rotation may repeat the previous family`() {
        assertEquals(1, ZulrahRules.nextRotation(2, 0))
        assertEquals(2, ZulrahRules.nextRotation(2, 1))
        assertEquals(4, ZulrahRules.nextRotation(2, 3))
    }

    @Test
    fun `capture timing constants stay on the verified game tick schedule`() {
        assertEquals(3, ZULRAH_ATTACK_RATE)
        assertEquals(4, ZULRAH_PHASE_END_TICKS)
        assertEquals(11, ZULRAH_INITIAL_EMERGE_TICKS)
        assertEquals(3, ZULRAH_EMERGE_TICKS)
        assertEquals(3, ZULRAH_DIVE_TICKS)
        assertEquals(7, ZULRAH_TAIL_WINDUP_TICKS + ZULRAH_TAIL_RECOVERY_TICKS)
        assertEquals(30, ZULRAH_CLOUD_LIFETIME)
        assertEquals(67, ZULRAH_SNAKELING_LIFETIME)
        assertEquals(4, ZULRAH_SNAKELING_ATTACK_RATE)
    }

    @Test
    fun `rewards always use the player tile rather than the boss tile`() {
        val player = CoordGrid(2268, 3068, 0)
        val boss = CoordGrid(2266, 3073, 0)
        assertEquals(player, ZulrahRules.rewardTile(player, boss))
    }

    @Test
    fun `private map copies only the playable shrine chunks`() {
        listOf(
                ShrineTile(2256, 3058),
                ShrineTile(2268, 3068),
                ShrineTile(2266, 3073),
                ShrineTile(2276, 3071),
                ShrineTile(2279, 3079),
            )
            .forEach { assertTrue(ZulrahRules.isInShrineTemplate(it), "missing shrine tile $it") }
        assertEquals(false, ZulrahRules.isInShrineTemplate(ShrineTile(2301, 3100)))
    }

    @Test
    fun `ten second respawn uses the nearest authoritative game tick`() {
        assertEquals(17, ZULRAH_RESPAWN_TICKS)
    }
}
