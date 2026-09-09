package org.rsmod.content.interfaces.prayer.tab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.prayer.tab.scripts.PrayerDrainScript
import org.rsmod.content.interfaces.prayer.tab.scripts.PrayerTabScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class PrayerLifecycleTest {
    @Test
    fun `disable all restores both native intervals and cancels prayer work`() {
        val f = Fixture()
        f.player.apply {
            f.set("varbit.prayer_rapidheal", 1)
            f.set("varbit.prayer_preserve", 1)
            f.set("varbit.quickprayer_active", 1)
            f.set("varbit.prayer_drain_counter", 10)
            appearance.overheadIcon = 0
            softTimer("timer.health_regen", constants.health_regen_interval / 2)
            softTimer("timer.stat_boost_restore", constants.stat_boost_restore_interval * 3 / 2)
            softTimer("timer.rapidrestore_regen", 100)
            softTimer("timer.prayer_drain", 1)
            longQueueDiscard("queue.preserve_activation", 25)
            softTimer("timer.spec_regen", 23)
            val specialTimer = f.timer("timer.spec_regen")

            disablePrayers()

            assertEquals(0, vars["varbit.prayer_allactive"])
            assertEquals(0, vars["varbit.quickprayer_active"])
            assertEquals(0, vars["varbit.prayer_drain_counter"])
            assertNull(appearance.overheadIcon)
            assertEquals(0, queueList.count("queue.preserve_activation"))
            assertNull(f.timer("timer.prayer_drain"))
            assertNull(f.timer("timer.rapidrestore_regen"))
            f.assertNormalTimers()
            assertEquals(
                specialTimer,
                f.timer("timer.spec_regen"),
                "Unrelated timers must not move",
            )
        }
    }

    @Test
    fun `native prayer drain depletion restores intervals and clears active state`() {
        val f = Fixture()
        f.set("varbit.prayer_rapidheal", 1)
        f.set("varbit.prayer_preserve", 1)
        f.player.softTimer("timer.health_regen", 50)
        f.player.softTimer("timer.stat_boost_restore", 150)
        f.player.softTimer("timer.prayer_drain", 1)
        with(PrayerDrainScript(repo, mock(WornBonuses::class.java))) { f.scripts.startup() }

        assertTrue(
            f.events.publish(
                PlayerTimerEvent.Soft(f.player, "timer.prayer_drain".asRSCM(RSCMType.TIMER))
            )
        )

        assertEquals(0, f.player.vars["varbit.prayer_allactive"])
        assertNull(f.timer("timer.prayer_drain"))
        f.assertNormalTimers()
        assertEquals(
            listOf(MessageGame(0, DEPLETED_MESSAGE)),
            f.packets.filterIsInstance<MessageGame>(),
        )
    }

    @Test
    fun `every prayer refused at zero points retransmits its disabled var and message`() {
        assertTrue(repo.prayerList.isNotEmpty())
        for (prayer in repo.prayerList) {
            val f = Fixture()
            f.toggle(prayer)
            val baseVar =
                assertNotNull(ServerCacheManager.getVarbit(prayer.enabled.asRSCM(RSCMType.VARBIT)))
                    .baseVar
            assertContains(f.packets, VarpSmall(baseVar.id, 0), prayer.name)
            assertEquals(
                listOf(MessageGame(0, DEPLETED_MESSAGE)),
                f.packets.filterIsInstance<MessageGame>(),
                prayer.name,
            )
            assertEquals(0, f.player.vars[prayer.enabled], prayer.name)
            assertNull(f.timer("timer.prayer_drain"))
            assertFalse(f.player.softTimerMap.isNotEmpty)
        }
    }

    @Test
    fun `repeated empty prayer clicks each resync even when server value was already zero`() {
        val f = Fixture()
        val prayer = repo.prayerList.first { it.enabled == "varbit.prayer_rapidheal" }
        repeat(3) { f.toggle(prayer) }
        assertEquals(3, f.packets.filterIsInstance<VarpSmall>().size)
        assertEquals(3, f.packets.filterIsInstance<MessageGame>().size)
        assertEquals(0, f.player.vars[prayer.enabled])
    }

    @Test
    fun `rapid heal still enables with points and manual disable restores interval`() {
        val f = Fixture()
        f.player.statMap.setCurrentLevel("stat.prayer", 99)
        val prayer = repo.prayerList.first { it.enabled == "varbit.prayer_rapidheal" }
        f.toggle(prayer)
        assertEquals(1, f.player.vars[prayer.enabled])
        assertEquals(
            50,
            f.player.softTimerMap.extractInterval(assertNotNull(f.timer("timer.health_regen"))),
        )
        assertNotNull(f.timer("timer.prayer_drain"))
        assertTrue(f.packets.none { it is MessageGame })
        f.toggle(prayer)
        assertEquals(0, f.player.vars[prayer.enabled])
        assertEquals(
            100,
            f.player.softTimerMap.extractInterval(assertNotNull(f.timer("timer.health_regen"))),
        )
        assertNull(f.timer("timer.prayer_drain"))
    }

    @Test
    fun `already enabled prayer can still be switched off at zero points`() {
        val f = Fixture()
        val prayer = repo.prayerList.first { it.enabled == "varbit.prayer_rapidheal" }
        f.set(prayer.enabled, 1)
        f.player.softTimer("timer.health_regen", 50)
        f.toggle(prayer)
        assertEquals(0, f.player.vars[prayer.enabled])
        assertEquals(
            100,
            f.player.softTimerMap.extractInterval(assertNotNull(f.timer("timer.health_regen"))),
        )
        assertTrue(f.packets.none { it is MessageGame })
    }

    private class Fixture {
        val packets = mutableListOf<Any>()
        val events = EventBus()
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val player =
            Player().apply {
                currentMapClock = 1234
                processedMapClock = 1234
                statMap.setBaseLevel("stat.prayer", 99)
                statMap.setCurrentLevel("stat.prayer", 0)
                client =
                    object : Client<Any, Any> {
                        override fun write(message: Any) {
                            packets += message
                        }

                        override fun close() = error("Unexpected disconnect")

                        override fun read(player: Player) = Unit

                        override fun flush() = Unit

                        override fun flushHighPriority() = Unit

                        override fun unregister(service: Any, player: Player) = Unit
                    }
            }

        init {
            val constructor =
                PrayerTabScript::class.java.declaredConstructors.single().apply {
                    isAccessible = true
                }
            val script =
                constructor.newInstance(repo, events, mock(ProtectedAccessLauncher::class.java))
                    as PrayerTabScript
            with(script) { scripts.startup() }
        }

        fun toggle(prayer: Prayer) {
            // Exercise the registered queue handler used by queued prayer-button interactions.
            assertTrue(
                ProtectedAccessLauncher.withProtectedAccess(
                    player,
                    ProtectedAccessContextFactory.empty(),
                ) {
                    assertTrue(
                        events.publish(
                            this,
                            PlayerQueueEvents.Protected(
                                prayer,
                                "queue.prayer_toggle".asRSCM(RSCMType.QUEUE),
                            ),
                        )
                    )
                }
            )
            assertNull(player.activeCoroutine, "Prayer interaction must finish without suspending")
        }

        fun set(name: String, value: Int) = VarPlayerIntMapSetter.set(player, name, value)

        fun timer(name: String): Long? = player.softTimerMap[name.asRSCM(RSCMType.TIMER).toShort()]

        fun assertNormalTimers() {
            for ((name, expected) in
                listOf(
                    "timer.health_regen" to constants.health_regen_interval,
                    "timer.stat_boost_restore" to constants.stat_boost_restore_interval,
                )) {
                val packed = assertNotNull(timer(name))
                assertEquals(expected, player.softTimerMap.extractInterval(packed), name)
                assertEquals(
                    player.currentMapClock + expected,
                    player.softTimerMap.extractExpiry(packed),
                    name,
                )
            }
        }
    }

    companion object {
        private const val DEPLETED_MESSAGE =
            "You have run out of prayer points, you can recharge at an altar."
        private lateinit var repo: PrayerRepository

        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            repo = PrayerRepository().apply { load() }
        }
    }
}
