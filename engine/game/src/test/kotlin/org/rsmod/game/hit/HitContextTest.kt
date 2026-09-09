package org.rsmod.game.hit

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds

class HitContextTest {
    @Test
    fun `encounter reset and member departure retire composed tokens independently`() {
        val run = HitScope()
        val member = HitScope()
        val first = HitContext.of(HitAttribution(HitOrigin.Recoil, 17), run, member)
        assertTrue(first.isValid())
        member.invalidate()
        assertFalse(first.isValid())
        val next = HitContext.of(first.attribution, run, member)
        assertTrue(next.isValid())
        run.invalidate()
        assertFalse(first.isValid())
        assertFalse(next.isValid())
    }

    @Test
    fun `closed scopes cannot issue live tokens or reopen on generation advance`() {
        val scope = HitScope()
        scope.close()
        scope.invalidate()
        assertFalse(scope.token().isValid())
    }

    @Test
    fun `inherited burn or echo cannot renew an expired membership`() {
        val source =
            Player().apply {
                uuid = 17
                combatHitScope = HitScope()
            }
        val target = Player().apply { combatHitScope = HitScope() }
        val launch = HitContext.capture(source, target)
        assertEquals(17L, launch.attribution.ownerUuid)
        source.combatHitScope = HitScope()
        val echo = HitContext.capture(source, target, launch)
        assertFalse(echo.isValid())
        assertTrue(HitContext.capture(source, target).isValid())
        assertEquals(
            17L,
            echo
                .withAttribution(launch.attribution.copy(origin = HitOrigin.Burn))
                .attribution
                .ownerUuid,
        )
    }

    @Test
    fun `leaving a bounded scope and returning before impact does not revive it`() {
        for (exit in
            listOf<(Player) -> Unit>(
                { it.coords = CoordGrid(99, 100) },
                { it.x = 110 },
                { it.z = 99 },
                { it.level = 1 },
            )) {
            val player = Player().apply { coords = CoordGrid(100, 100) }
            player.combatHitScope = HitScope(Bounds(player.coords, 10, 10))
            val context = HitContext.capture(null, player)
            player.coords = CoordGrid(109, 109)
            assertTrue(context.isValid())
            exit(player)
            player.coords = CoordGrid(100, 100)
            assertFalse(context.isValid())
            assertFalse(HitContext.capture(null, player).isValid())
        }
    }

    @Test
    fun `denied manual logout is not immunity but accepted logout and disconnect close scopes`() {
        val player = Player().apply { combatHitScope = HitScope() }
        val manual = HitContext.capture(null, player)
        player.manualLogout = true
        assertTrue(manual.isValid())
        player.pendingLogout = true
        assertFalse(manual.isValid())
        player.pendingLogout = false
        assertFalse(HitContext.capture(null, player).isValid())
        player.combatHitScope = HitScope()
        val forced = HitContext.capture(null, player)
        player.forceDisconnect = true
        assertFalse(forced.isValid())
    }

    @Test
    fun `network signal cancels before a queued or instant impact without content callbacks`() {
        val disconnected = AtomicBoolean(false)
        val scope = HitScope(cancellationSignal = disconnected)
        val context = HitContext.of(HitAttribution(), scope)
        disconnected.set(true)
        assertFalse(context.isValid())
        disconnected.set(false)
        assertFalse(context.isValid())
        assertFalse(scope.token().isValid())
    }

    @Test
    fun `composition preserves all original snapshots and default calls remain unscoped`() {
        val first = HitScope()
        val second = HitScope()
        val context =
            HitContext.compose(
                HitContext.of(HitAttribution(HitOrigin.Thrall, 21), first),
                HitContext.of(HitAttribution(), second),
            )
        assertEquals(HitAttribution(HitOrigin.Thrall, 21), context.attribution)
        second.close()
        assertFalse(context.isValid())
        assertTrue(HitContext.EMPTY.isValid())
    }
}
