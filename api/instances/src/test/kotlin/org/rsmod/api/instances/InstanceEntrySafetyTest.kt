package org.rsmod.api.instances

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes InstanceScript + protected teleport validation; records manager commit boundaries. */
@OptIn(InternalApi::class)
class InstanceEntrySafetyTest {
    @Test
    fun `refused entry does not finalize and releases the newly created pending session`() {
        val f = Fixture()
        f.refused = true
        f.enter()
        assertEquals(f.exit, f.player.coords)
        assertEquals(0, f.finalized)
        assertEquals(1, f.cancelled)
    }

    @Test
    fun `entry only finalizes after movement and does not revive a destroyed session`() {
        val f = Fixture()
        f.enter()
        assertEquals(listOf(f.destination), f.finalizedCoords)
        f.player.coords = f.exit
        f.live = false
        f.enter()
        assertEquals(f.exit, f.player.coords)
        assertEquals(1, f.finalized)
    }

    @Test
    fun `refused exit retains membership and successful exit detaches after movement`() {
        val f = Fixture()
        f.enter()
        f.refused = true
        f.leave()
        assertEquals(f.destination, f.player.coords)
        assertTrue(f.member)
        assertTrue(f.leftCoords.isEmpty())
        f.refused = false
        f.leave()
        assertEquals(listOf(f.exit), f.leftCoords)
        assertFalse(f.member)
    }

    private class Fixture {
        val exit = CoordGrid(2280, 10016)
        val destination = CoordGrid(6440, 6438)
        val clock = MapClock()
        val collision = CollisionFlagMap()
        val player =
            Player().apply {
                slotId = 1
                uuid = 1
                assignUid()
                coords = exit
            }
        val spec =
            InstanceSettings(destroyWhenEmpty = true)
                .withArea(
                    InstanceArea.copyRegions(
                        listOf(9116),
                        enterCoord = RegionLocal(0, 35, 156, 40, 38),
                        exitCoord = exit,
                    ),
                    0,
                )
        val placement =
            (InstanceAreaResolver().resolve(spec.area) as InstanceAreaResolver.Result.Ready)
                .placement
        val session =
            InstanceSession(
                InstanceId(1),
                mutableSetOf(),
                1,
                "entry-test",
                spec,
                placement,
                InstanceAccess.Private,
            )
        var live = true
        var member = false
        var refused = false
        var finalized = 0
        var cancelled = 0
        val finalizedCoords = mutableListOf<CoordGrid>()
        val leftCoords = mutableListOf<CoordGrid>()
        val manager =
            mock(InstanceManager::class.java) { call ->
                when (call.method.name.substringBefore('-')) {
                    "sessionForId" -> if (live) session else null
                    "sessionForPlayer" -> if (member) session else null
                    "finalizeEntry" -> {
                        finalized++
                        member = true
                        finalizedCoords += player.coords
                        null
                    }
                    "cancelPendingEntry" -> {
                        cancelled++
                        live = false
                        null
                    }
                    "leave" -> {
                        member = false
                        leftCoords += player.coords
                        exit.packed
                    }
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            }
        private val script =
            object : InstanceScript(BossInstanceRegistry(), "entry-test", spec) {
                suspend fun ProtectedAccess.enter() =
                    completeInstanceEntry(InstanceManager.Result.Created(session, destination))

                suspend fun ProtectedAccess.leave() = defaultLeaveFlow()
            }
        val context =
            mock(ProtectedAccessContext::class.java) { call ->
                when (call.method.name) {
                    "getCollision" -> collision
                    "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                    "getTeleportValidator" ->
                        PlayerTeleportValidator(
                            setOf(
                                PlayerTeleportValidateHook { _, _, _ ->
                                    if (refused) "Test teleport refusal." else null
                                }
                            )
                        )
                    "getAreaChecker" -> mock(call.method.returnType)
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            }

        init {
            collision.allocateIfAbsent(exit.x, exit.z, 0)
            collision.allocateIfAbsent(destination.x, destination.z, 0)
            Guice.createInjector(
                    object : AbstractModule() {
                        override fun configure() {
                            bind(InstanceManager::class.java).toInstance(manager)
                            bind(MapClock::class.java).toInstance(clock)
                        }
                    }
                )
                .injectMembers(script)
            with(script) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun enter() {
            player.launch { with(script) { ProtectedAccess(player, this@launch, context).enter() } }
        }

        fun leave() {
            player.launch { with(script) { ProtectedAccess(player, this@launch, context).leave() } }
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
