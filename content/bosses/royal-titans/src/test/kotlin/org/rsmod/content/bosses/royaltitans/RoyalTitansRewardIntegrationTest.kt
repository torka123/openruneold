package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito.framework
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player

/** Executes completion and claims against native instances, hit processing and ground registry. */
class RoyalTitansRewardIntegrationTest {
    init {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `either corpse delivers two standard rolls through native tracker and Collection Log`() {
        for (claimedId in listOf(12596, 14147)) {
            val random = RewardRandom()
            val f = RoyalTitansTestWorld(random = random)
            val player = f.enter()
            f.tick(6)
            val branda = f.npc(player, 12596)
            val eldric = f.npc(player, 14147)
            f.hit(player, branda, 600)
            f.hit(player, eldric, 600)
            f.tick(2)
            val corpse = if (claimedId == 12596) branda else eldric
            val packets = capture(player)
            random.arm(0.99)
            f.manager.claim(player, corpse, ClaimChoice.LOOT)
            val expectedItem =
                (if (claimedId == 12596) "obj.cert_mystic_fire_staff"
                    else "obj.cert_mystic_water_staff")
                    .asRSCM()
            assertEquals(
                listOf(
                    RunClientScript(7192, listOf(claimedId, 1, expectedItem, 4)),
                    RunClientScript(7192, listOf(claimedId, 1, expectedItem, 4)),
                ),
                packets.filterIsInstance<RunClientScript>(),
            )
            assertEquals(8, f.objRegistry.findAll(player.coords).sumOf { it.count })
            assertEquals(2, f.rewardLogEvents.size)
            assertEquals(
                CollectionLogAwardSource(CollectionLogAwardKind.NpcDrop, corpse.type.internalName),
                f.rewardLogEvents[0][3],
            )
            assertEquals(2, f.rewardLogEvents.map { it[4] }.toSet().size)
            val other = if (claimedId == 12596) eldric else branda
            f.manager.claim(player, other, ClaimChoice.PAGES)
            assertEquals(2, f.rewardLogEvents.size, "The other corpse cannot grant another choice")
        }
    }

    @Test
    fun `pages and sacrifice retain pet and both clue rolls without normal or unique loot`() {
        for (choice in listOf(ClaimChoice.PAGES, ClaimChoice.SACRIFICE)) {
            val random = RewardRandom()
            val f = RoyalTitansTestWorld(random = random)
            val player = f.enter()
            f.tick(6)
            val branda = f.npc(player, 12596)
            f.hit(player, branda, 600)
            f.hit(player, f.npc(player, 14147), 600)
            f.tick(2)
            random.arm(0.0)
            f.manager.claim(player, branda, choice)
            val items = f.rewardLogEvents.map { it[1] }
            assertEquals(if (choice == ClaimChoice.PAGES) 4 else 3, items.size)
            assertTrue(
                "obj.rtbrandapet".asRSCM() in items,
                "Native Bran item delivery is not proof of a pet unlock",
            )
            assertTrue(items.none { it == 30631 || it == 30627 || it == 30637 })
            assertEquals(3, random.chanceCalls, "No unique checks for these choices")
        }
    }

    @Test
    fun `missing table does not consume a claim and retry has one reward identity`() {
        val random = RewardRandom()
        val f = RoyalTitansTestWorld(random = random)
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        f.hit(player, branda, 600)
        f.hit(player, f.npc(player, 14147), 600)
        f.tick(2)
        random.arm(0.99)
        f.missingDropTable = true
        f.manager.claim(player, branda, ClaimChoice.LOOT)
        assertTrue(f.rewardLogEvents.isEmpty())
        assertEquals(0, f.objRegistry.count())
        f.missingDropTable = false
        f.manager.claim(player, branda, ClaimChoice.LOOT)
        f.manager.claim(player, branda, ClaimChoice.LOOT)
        assertEquals(2, f.rewardLogEvents.size)
    }

    @Test
    fun `duo claims share a corpse but not personal ownership or award identities`() {
        val random = RewardRandom()
        val f = RoyalTitansTestWorld(random = random)
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        f.tick(6)
        val branda = f.npc(owner, 12596)
        f.hit(owner, branda, 600)
        f.hit(partner, f.npc(owner, 14147), 600)
        f.tick(2)
        random.arm(0.99)
        f.manager.claim(owner, branda, ClaimChoice.LOOT)
        f.manager.claim(partner, branda, ClaimChoice.LOOT)
        assertEquals(4, f.rewardLogEvents.size)
        assertEquals(4, f.rewardLogEvents.map { it[4] }.toSet().size)
        assertEquals(setOf(owner, partner), f.rewardLogEvents.map { it[0] }.toSet())
        assertEquals(
            2,
            f.objRegistry.count(),
            "Two personal stacks must not merge across receivers",
        )
    }

    private fun capture(player: Player): MutableList<Any> {
        val packets = mutableListOf<Any>()
        player.client =
            object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) {
                    packets += message
                }
            }
        return packets
    }

    private class RewardRandom : GameRandom by DefaultGameRandom(42L) {
        private val combat = DefaultGameRandom(42L)
        private var chance: Double? = null
        var chanceCalls = 0

        fun arm(value: Double) {
            chance = value
            chanceCalls = 0
        }

        override fun of(maxExclusive: Int): Int = if (chance == null) combat.of(maxExclusive) else 0

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            if (chance == null) combat.of(minInclusive, maxInclusive) else minInclusive

        override fun randomDouble(): Double {
            val fixed = chance ?: return combat.randomDouble()
            chanceCalls++
            return fixed
        }
    }
}
