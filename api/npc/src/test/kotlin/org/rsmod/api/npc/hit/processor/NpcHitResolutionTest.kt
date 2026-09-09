package org.rsmod.api.npc.hit.processor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.player.cheat.adminOneHit
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.*
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class NpcHitResolutionTest {
    @Test
    fun `simultaneous arrivals use updated HP and credit only accepted damage`() {
        val f = Fixture()
        val observedHp = mutableListOf<Int>()
        f.resolve {
            observedHp += npc.hitpoints
            damage = damage.coerceAtMost((npc.hitpoints - 50).coerceAtLeast(0))
        }
        val first = f.hit(40)
        val second = f.hit(40)
        assertEquals(100, f.npc.hitpoints)
        f.land(first)
        f.land(second)
        assertEquals(listOf(100, 60), observedHp)
        assertEquals(50, f.npc.hitpoints)
        assertEquals(listOf(40, 10), f.impacts.map { it.damage })
        assertEquals(50, f.npc.damageContributions.totalDamage())
        assertEquals(listOf(40, 10), f.credits)
        assertEquals(40, second.damage, "Queued hit is immutable")
        assertSame(second.context, f.impacts.last().context)
        assertFalse("queue.death" in f.npc.queueList)
    }

    @Test
    fun `immunity beginning and ending in flight is sampled on arrival`() {
        val f = Fixture()
        var immune = false
        f.resolve { if (immune) damage = 0 }
        val beforeWindow = f.hit(40, privateHit = true)
        immune = true
        f.land(beforeWindow)
        assertEquals(100, f.npc.hitpoints)
        assertTrue(f.impacts.single().hitmark.isPrivate)
        assertEquals(hitmark_groups.zero_damage.lit.asRSCM(RSCMType.HITMARK), f.marks.single().self)
        assertTrue(f.credits.isEmpty())
        val duringWindow = f.hit(20)
        immune = false
        f.land(duringWindow)
        assertEquals(80, f.npc.hitpoints)
        assertEquals(listOf(0, 20), f.impacts.map { it.damage })
    }

    @Test
    fun `retired scope has no resolution HP contribution or visual side effects`() {
        val f = Fixture()
        var calls = 0
        f.resolve { calls++ }
        val scope = HitScope()
        val hit = f.hit(40).copy(context = HitContext.of(HitAttribution(), scope))
        scope.close()
        f.land(hit)
        assertEquals(0, calls)
        assertEquals(100, f.npc.hitpoints)
        assertTrue(f.impacts.isEmpty() && f.marks.isEmpty() && f.credits.isEmpty())
    }

    @Test
    fun `indirect damage keeps its owner and origin after final damage policy`() {
        for (origin in HitOrigin.entries) {
            val f = Fixture()
            f.resolve { damage = 7 }
            f.land(f.hit(20, origin = origin))
            assertEquals(93, f.npc.hitpoints)
            assertEquals(7, f.npc.damageContributions.damageBy(900L, origin))
            assertEquals(HitAttribution(origin, 900L), f.impacts.single().context.attribution)
        }
    }

    @Test
    fun `ordinary NPC retains overkill clamping and one queued death`() {
        val f = Fixture()
        f.land(f.hit(120))
        f.land(f.hit(30))
        assertEquals(0, f.npc.hitpoints)
        assertEquals(listOf(100, 0), f.impacts.map { it.damage })
        assertEquals(100, f.npc.damageContributions.totalDamage())
        assertEquals(1, f.npc.queueList.size)
        assertTrue("queue.death" in f.npc.queueList)
    }

    @Test
    fun `resolution cannot turn negative damage into healing or exceed remaining HP`() {
        val f = Fixture()
        var requested = -100
        f.resolve { damage = requested }
        f.land(f.hit(20))
        assertEquals(100, f.npc.hitpoints)
        requested = Int.MAX_VALUE
        f.land(f.hit(20))
        assertEquals(0, f.npc.hitpoints)
        assertEquals(listOf(0, 100), f.impacts.map { it.damage })
    }

    @Test
    fun `admin one hit is expanded before policy and cannot bypass phase floor`() {
        val f = Fixture()
        val player =
            Player().apply {
                uuid = 900L
                observerUUID = 900L
                slotId = 1
                assignUid()
                modLevel = Rights.ADMINISTRATOR
                adminOneHit = true
            }
        f.players[1] = player
        f.resolve {
            assertEquals(1, hit.damage)
            assertEquals(100, damage)
            damage = npc.hitpoints - 50
        }
        val hit =
            f.hit(1)
                .copy(
                    sourceUid = player.uid.packed,
                    hitmark = Hitmark.fromPlayerSource(16, 16, 16, 1, 0, 1),
                )
        f.land(hit)
        assertEquals(50, f.npc.hitpoints)
        assertEquals(50, f.impacts.single().damage)
        assertFalse("queue.death" in f.npc.queueList)
    }

    private class Fixture {
        val events = EventBus()
        val players = PlayerList()
        val marks = mutableListOf<Hitmark>()
        val impacts = mutableListOf<Hit>()
        val credits = mutableListOf<Int>()
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(11278)), CoordGrid(2924, 5202)).apply {
                hitpoints = 100
                baseHitpointsLvl = 100
                currentMapClock = 100
                infoProtocol =
                    object : NpcInfoProtocol by NoopNpcInfo {
                        override fun showHitmark(hitmark: Hitmark) {
                            marks += hitmark
                        }
                    }
            }
        val processor =
            StandardNpcHitProcessor(
                players,
                events,
                setOf(
                    object : NpcDamageContributor {
                        override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
                            credits += damage
                        }

                        override fun onPlayerDamageNpc(
                            npc: Npc,
                            source: Player?,
                            damage: Int,
                            attribution: HitAttribution,
                        ) {
                            credits += damage
                        }
                    }
                ),
            )
        private val access =
            StandardNpcAccess(
                npc,
                GameCoroutine(),
                StandardNpcAccessContext(
                    { DefaultGameRandom(42L) },
                    { StandardNpcHitModifier(events) },
                    { processor },
                ),
            )

        init {
            events.subscribeKeyed(NpcHitEvents.Impact::class.java, npc.visType.id.toLong()) {
                impacts += hit
            }
        }

        fun resolve(action: NpcHitEvents.Resolve.() -> Unit) =
            events.subscribeKeyed(NpcHitEvents.Resolve::class.java, npc.visType.id.toLong(), action)

        fun hit(damage: Int, privateHit: Boolean = false, origin: HitOrigin = HitOrigin.Direct) =
            Hit(
                HitType.Magic,
                Hitmark.fromNoSource(16, 16, if (privateHit) null else 16, damage, 0),
                null,
                null,
                null,
                context = HitContext.of(HitAttribution(origin, 900L)),
            )

        fun land(hit: Hit) = with(processor) { access.process(hit) }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
