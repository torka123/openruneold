package org.rsmod.api.player.hit.processor

import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.cheat.ADMIN_GOD_HITPOINTS_MAX
import org.rsmod.api.player.cheat.adminGodHitpoints
import org.rsmod.api.player.hit.modifier.BypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class StandardPlayerHitProcessorTest {
    @Test
    fun GameTestState.`administrator god hitpoints take npc damage without changing normal stat`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.modLevel = Rights.ADMINISTRATOR
            player.stats[stats.hitpoints] = 99
            player.adminGodHitpoints = ADMIN_GOD_HITPOINTS_MAX

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 37)
            advance(ticks = 1)

            assertEquals(ADMIN_GOD_HITPOINTS_MAX - 37, player.adminGodHitpoints)
            assertEquals(99, player.hitpoints)
        }

    /**
     * Melee-based hits undergo special validation during processing (when the hitsplat is
     * displayed) to ensure that the hit's source is still alive.
     *
     * Currently, we have only confirmed this behavior for hits originating from an
     * [org.rsmod.game.entity.Npc]. If future evidence shows that the same applies to players, a
     * separate test should be added.
     */
    @Test
    fun GameTestState.`invalidate melee hits when npc source is dead`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Ensure hits are actually going through for test validity.
            advance(ticks = 1)
            check(player.hitpoints == 98) { "Unexpected player hitpoints: ${player.hitpoints}" }

            // Reset state and queue a new hit.
            player.stats[stats.hitpoints] = 99
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Set npc to 0 hitpoints, mimicking its death.
            npc.hitpoints = 0

            // Hit should have been invalidated due to npc being "dead."
            advance(ticks = 1)
            assertEquals(99, player.hitpoints)
        }

    @Test
    fun GameTestState.`post impact handler receives damage actually applied to player`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99

            var impact: HitImpact? = null
            player.queueImpactHit(
                source = npc,
                delay = 1,
                type = HitType.Ranged,
                damage = 10,
                impactHandler = HitImpactHandler { impact = it },
            )

            // Lower health after queuing so final processing must cap the pending hit.
            player.stats[stats.hitpoints] = 2
            advance(ticks = 1)

            val finalImpact = checkNotNull(impact)
            check(player.hitpoints == 0)
            check(finalImpact.target === player)
            check(finalImpact.damage == 2)
        }

    @Test
    fun GameTestState.`protection prayer bypass modifier preserves player-source damage`() =
        runGameTest(PlayerHitScript::class) {
            player.stats[stats.hitpoints] = 99
            player.vars["varbit.prayer_protectfrommelee"] = 1

            player.queueHit(
                source = player,
                delay = 1,
                type = HitType.Melee,
                damage = 10,
                modifier = BypassProtectionPrayerPlayerHitModifier,
            )
            advance(ticks = 1)

            check(player.hitpoints == 89)
        }
}
