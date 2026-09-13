package org.rsmod.api.npc.hit.processor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
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
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.*
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class NpcHitResolutionTest {
    @Test
    fun `retirement inside Resolve prevents all subsequent impact side effects`() {
        val f = Fixture()
        val scope = HitScope()
        var resolved = 0
        var callbacks = 0
        f.resolve {
            resolved++
            scope.close()
        }
        f.land(
            f.hit(120)
                .copy(
                    context = HitContext.of(HitAttribution(ownerUuid = 900L), scope),
                    impactHandler = HitImpactHandler { callbacks++ },
                )
        )
        assertEquals(1, resolved)
        assertEquals(100, f.npc.hitpoints)
        assertTrue(f.npc.damageContributions.isEmpty)
        assertTrue(f.credits.isEmpty() && f.marks.isEmpty() && f.bars.isEmpty())
        assertTrue(f.observations.isEmpty())
        assertFalse("queue.death" in f.npc.queueList)
        assertEquals(0, callbacks)
    }

    @Test
    fun `Impact retirement prevents a silent heal from refreshing the old headbar`() {
        val f = Fixture()
        val scope = HitScope()
        f.npc.slotId = 10
        f.npc.assignUid()
        f.npc.hitpoints = 50
        f.resolve { damage = 0 }
        f.afterImpact = {
            it.npc.hitpoints += 20
            scope.close()
        }
        f.land(f.hit(20).copy(context = HitContext.of(HitAttribution(), scope)))
        assertEquals(70, f.npc.hitpoints)
        assertEquals(1, f.observations.size)
        assertEquals(1, f.bars.size)
    }

    @Test
    fun `silent absorption refreshes native healthbar without a healing mark`() {
        val f = Fixture()
        f.npc.slotId = 10
        f.npc.assignUid()
        f.npc.hitpoints = 50
        f.resolve { damage = 0 }
        f.afterImpact = { it.npc.hitpoints += it.originalHit.damage }
        f.land(
            f.hit(20)
                .copy(
                    sourceUid = PlayerUid(1, 900L).packed,
                    hitmark = Hitmark.fromPlayerSource(16, 16, null, 20, 10, 1),
                )
        )
        assertEquals(70, f.npc.hitpoints)
        assertEquals(
            listOf(50, 70).map { it * f.npc.visHeadbar(params.headbar).segments / 100 },
            f.bars.map { it.endFill },
        )
        assertEquals(1, f.marks.size)
        assertEquals(0, f.marks.single().damage)
        assertTrue(f.credits.isEmpty())
        assertEquals(1, f.observations.size)
        assertTrue(f.bars.all { it.isPrivate && it.playerSlot == 1 && it.startTime == 10 })
    }

    @Test
    fun `post-impact healing cannot reintroduce a suppressed hidden or detached headbar`() {
        for (retire in
            listOf<(Npc) -> Unit>(
                { it.suppressHitpointHeadbar = true },
                { it.hidden = true },
                { it.clearUid() },
                { it.slotId = -1 },
                { it.delay = 999 },
            )) {
            val f = Fixture()
            f.npc.slotId = 10
            f.npc.assignUid()
            f.npc.hitpoints = 50
            f.resolve { damage = 0 }
            f.afterImpact = {
                it.npc.hitpoints += 20
                retire(it.npc)
            }
            f.land(f.hit(20))
            assertEquals(1, f.bars.size)
        }
    }

    @Test
    fun `impact observes immutable input separately from overkill and zero private damage`() {
        val f = Fixture()
        val first = f.hit(120)
        val second = f.hit(30, privateHit = true)
        f.land(first)
        f.land(second)
        assertEquals(listOf(120, 30), f.observations.map { it.originalHit.damage })
        assertEquals(listOf(120, 30), f.observations.map { it.inputDamage })
        assertEquals(listOf(100, 0), f.observations.map { it.hit.damage })
        assertSame(first, f.observations.first().originalHit)
        assertSame(second, f.observations.last().originalHit)
        assertTrue(f.observations.last().hit.hitmark.isPrivate)
    }

    @Test
    fun `impact remains on original resolution identity after transformation`() {
        val f = Fixture()
        val key = f.npc.visType.id.toLong()
        f.resolve { npc.transmog(assertNotNull(ServerCacheManager.getNpc(11279)), Int.MAX_VALUE) }
        f.land(f.hit(20))
        assertEquals(80, f.npc.hitpoints)
        assertEquals(key, f.observations.single().id)
        assertEquals(20, f.observations.single().inputDamage)
    }

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
        assertTrue(f.observations.isEmpty())
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
        assertEquals(1, f.observations.single().originalHit.damage)
        assertEquals(100, f.observations.single().inputDamage)
        assertFalse("queue.death" in f.npc.queueList)
    }

    private class Fixture {
        val events = EventBus()
        val players = PlayerList()
        val marks = mutableListOf<Hitmark>()
        val bars = mutableListOf<Headbar>()
        var afterImpact: (NpcHitEvents.Impact) -> Unit = {}
        val impacts = mutableListOf<Hit>()
        val observations = mutableListOf<NpcHitEvents.Impact>()
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

                        override fun showHeadbar(headbar: Headbar) {
                            bars += headbar
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
                observations += this
                afterImpact(this)
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
