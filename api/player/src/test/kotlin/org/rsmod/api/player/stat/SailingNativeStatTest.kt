package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.output.UpdateStat
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerStatMap

@ResourceLock("server-cache")
class SailingNativeStatTest {
    @Test
    fun `stat 23 uses native xp boost packet and released-skill plumbing`() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), REVISION)
        try {
            val sailing = assertNotNull(ServerCacheManager.getStats(SAILING_STAT_ID))
            assertEquals(SAILING_STAT_ID, "stat.sailing".asRSCM(RSCMType.STAT))
            assertEquals(1, sailing.minLevel)
            assertEquals(99, sailing.maxLevel)
            assertTrue(sailing.unreleased)

            val player = Player()
            player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
            PlayerSkillXP.internalAddXP(player, "stat.sailing", LEVEL_30_XP.toDouble(), 1.0)

            assertEquals(LEVEL_30_XP, player.statMap.getXP("stat.sailing"))
            assertEquals(30, player.baseSailingLvl)
            assertEquals(30, player.sailingLvl)
            assertTrue(player.pendingStatUpdates.get(SAILING_STAT_ID))

            player.statMap.setBaseLevel("stat.sailing", 50)
            player.statMap.setCurrentLevel("stat.sailing", 50)
            player.statBoost("stat.sailing", constant = 5, percent = 0)
            assertEquals(50, player.baseSailingLvl)
            assertEquals(55, player.sailingLvl)

            player.statDrain("stat.sailing", constant = 10, percent = 0)
            assertEquals(50, player.baseSailingLvl)
            assertEquals(45, player.sailingLvl)

            player.statHeal("stat.sailing", constant = 100, percent = 0)
            assertEquals(50, player.sailingLvl)

            val client = RecordingClient()
            player.client = client
            UpdateStat.update(player, sailing, LEVEL_30_XP, 30, 33)
            val update = assertIs<UpdateStatV2>(client.messages.single())
            assertEquals(SAILING_STAT_ID, update.stat)
            assertEquals(LEVEL_30_XP, update.experience)
            assertEquals(30, update.currentLevel)
            assertEquals(33, update.invisibleBoostedLevel)

            val released = ReleasedSkillMastery.releasedStats()
            assertTrue(released.isNotEmpty())
            assertFalse(ReleasedSkillMastery.isReleased(sailing))
            assertFalse(released.any { it.id == SAILING_STAT_ID })
            assertFalse(ReleasedSkillMastery.allReleasedSkillsMastered(player))

            released.forEach { stat ->
                val internal = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
                player.statMap.setBaseLevel(internal, stat.maxLevel.toByte())
            }
            assertEquals(released.size, ReleasedSkillMastery.masteredReleasedSkillCount(player))
            assertTrue(ReleasedSkillMastery.allReleasedSkillsMastered(player))
            assertFalse(ReleasedSkillMastery.isMastered(player, sailing))

            val first = released.first()
            val firstInternal = RSCM.getReverseMapping(RSCMType.STAT, first.id)
            player.statMap.setBaseLevel(firstInternal, (first.maxLevel - 1).toByte())
            assertEquals(released.size - 1, ReleasedSkillMastery.masteredReleasedSkillCount(player))
            assertFalse(ReleasedSkillMastery.allReleasedSkillsMastered(player))

            PlayerSkillXP.internalAddXP(player, "stat.sailing", 500_000_000.0, 1.0)
            assertEquals(PlayerStatMap.MAX_XP, player.statMap.getXP("stat.sailing"))
            assertEquals(99, player.baseSailingLvl)
        } finally {
            cache.close()
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()

        override fun close() {}

        override fun write(message: Any) {
            messages += message
        }

        override fun read(player: Player) {}

        override fun flush() {}

        override fun flushHighPriority() {}

        override fun unregister(service: Any, player: Player) {}
    }

    private companion object {
        private const val REVISION = 240
        private const val SAILING_STAT_ID = 23
        private const val LEVEL_30_XP = 13_363
    }
}
