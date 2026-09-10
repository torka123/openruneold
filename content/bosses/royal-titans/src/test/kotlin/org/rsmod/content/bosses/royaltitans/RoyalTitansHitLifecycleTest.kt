package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.combat.commons.npc.canRetaliate
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.mechanics.toxins.BurnEffectService
import org.rsmod.api.mechanics.toxins.PlayerPoisonTimerScript
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.front
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.*
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Executes the shared queues/processors with real Royal Titans instances and revision-240 types.
 */
class RoyalTitansHitLifecycleTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `actual armour recoil producer credits the defender and cannot survive membership retirement`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        player.front = InvObj("obj.damned_amulet", 1)
        player.hat = InvObj("obj.barrows_dharok_head", 1)
        player.torso = InvObj("obj.barrows_dharok_body", 1)
        player.legs = InvObj("obj.barrows_dharok_legs", 1)
        player.righthand = InvObj("obj.barrows_dharok_weapon", 1)
        val fixed =
            object : org.rsmod.api.random.GameRandom {
                override fun of(maxExclusive: Int) = 0

                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive

                override fun randomDouble() = 0.0
            }
        val script =
            Class.forName("org.rsmod.api.combat.scripts.armour.ArmourRecoilScript")
                .getConstructor(
                    org.rsmod.api.random.GameRandom::class.java,
                    org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier::class.java,
                )
                .newInstance(fixed, f.modifier) as org.rsmod.plugin.scripts.PluginScript
        with(script) { ScriptContext(f.events, CheatCommandMap(), EngineQueueCache()).startup() }
        val processor = DamageOnlyPlayerHitProcessor(f.events, f.npcList, f.players)
        fun recoil(): Hit {
            val incoming =
                player.queueHit(
                    source = boss,
                    delay = 1,
                    type = HitType.Melee,
                    damage = 20,
                    modifier = NoopPlayerHitModifier,
                )
            player.queueList.clear()
            with(processor) { player.process(incoming) }
            val iterator = assertNotNull(boss.queueList.iterator())
            val hits = mutableListOf<Hit>()
            try {
                while (iterator.hasNext()) (iterator.next().args as? Hit)?.let(hits::add)
            } finally {
                iterator.cleanUp()
            }
            return hits.single()
        }
        val first = recoil()
        assertEquals(HitAttribution(HitOrigin.Recoil, player.uuid), first.context.attribution)
        assertEquals(3, first.damage)
        f.tick()
        assertEquals(597, boss.hitpoints)
        assertEquals(3, boss.damageContributions.damageBy(player, HitOrigin.Recoil))
        assertEquals(0, boss.damageContributions.damageBy(player, HitOrigin.Direct))
        val pending = recoil()
        player.combatHitScope?.invalidate()
        assertFalse(pending.isValidNow())
        f.tick()
        assertEquals(597, boss.hitpoints)
        assertEquals(3, f.manager.contribution(player))
        val lethal =
            player.queueHit(
                source = boss,
                delay = 1,
                type = HitType.Melee,
                damage = 99,
                modifier = NoopPlayerHitModifier,
            )
        player.queueList.clear()
        with(processor) { player.process(lethal) }
        assertEquals(0, player.hitpoints)
        assertTrue(
            boss.queueList.isEmpty,
            "Death retires membership before derived recoil is queued",
        )
    }

    @Test
    fun `rejected scoped toxins neither mutate status nor replace valid poison`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val elemental = f.summon(player).first()
        val poison =
            org.rsmod.api.mechanics.toxins.NpcPoisonEffectService(WorldQueueList(), f.modifier)
        assertTrue(poison.applyAfterInterval(player, elemental, 6))
        val retired = f.newPlayer(2)
        retired.combatHitScope = HitScope().also { it.close() }
        assertFalse(poison.applyVenom(retired, elemental))
        assertTrue(
            poison.isPoisoned(elemental),
            "Rejected venom must not cure another owner's poison",
        )
        assertFalse(poison.isEnvenomed(elemental))
        val messages = mutableListOf<Any>()
        retired.client =
            object : org.rsmod.game.client.Client<Any, Any> by org.rsmod.game.client.NoopClient {
                override fun write(message: Any) {
                    messages += message
                }
            }
        assertFalse(PlayerPoison.tryPoison(retired, initialDamage = 6))
        assertFalse(PlayerVenom.tryVenom(retired))
        assertFalse(PlayerPoison.incidentalPoisonHit(retired, 6))
        assertEquals(0, retired.vars["varp.poison_severity"])
        assertEquals(0, retired.vars["varp.venom_strikes"])
        assertTrue(retired.queueList.isEmpty)
        assertTrue(messages.isEmpty(), "No toxin message or status packet after membership closure")
        val burn = BurnEffectService(WorldQueueList(), f.modifier)
        assertFalse(burn.apply(retired, elemental))
        val active =
            BurnEffectService::class.java.getDeclaredField("active").apply { isAccessible = true }
        assertTrue((active.get(burn) as Map<*, *>).isEmpty(), "No retained empty target entry")
    }

    @Test
    fun `all legal boss damage sources retain origin and only resolved HP is credited`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        for (origin in
            listOf(HitOrigin.Direct, HitOrigin.Recoil, HitOrigin.Burn, HitOrigin.Thrall)) {
            f.hit(player, boss, 7, context = HitContext.of(HitAttribution(origin, player.uuid)))
            f.tick()
            assertEquals(7, boss.damageContributions.damageBy(player, origin))
        }
        boss.hitpoints = 5
        f.hit(
            player,
            boss,
            100,
            context = HitContext.of(HitAttribution(HitOrigin.Recoil, player.uuid)),
        )
        f.tick()
        assertEquals(12, boss.damageContributions.damageBy(player, HitOrigin.Recoil))
        assertEquals(33, boss.damageContributions.totalDamage())
        assertEquals(
            33,
            f.instances
                .contributionsFor(assertNotNull(f.instances.sessionForPlayer(player)).id)
                ?.totalDamage(),
        )
    }

    @Test
    fun `explicit owner and offline owner agree across native and instance ledgers`() {
        val f = RoyalTitansTestWorld()
        val physical = f.enter()
        val owner = f.newPlayer(2)
        assertTrue(f.join(owner, physical.displayName))
        f.tick(6)
        val boss = f.npc(physical, 12596)
        f.hit(
            physical,
            boss,
            5,
            context = HitContext.of(HitAttribution(HitOrigin.Thrall, owner.uuid)),
        )
        f.tick()
        assertEquals(owner, f.damageNotifications.last().first)
        assertEquals(0, boss.damageContributions.damageBy(physical))
        assertEquals(5, boss.damageContributions.damageBy(owner, HitOrigin.Thrall))
        boss.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = 3,
            modifier = f.modifier,
            context = HitContext.of(HitAttribution(HitOrigin.Burn, 999)),
        )
        f.tick()
        assertNull(f.damageNotifications.last().first)
        val shared =
            assertNotNull(
                f.instances.contributionsFor(
                    assertNotNull(f.instances.sessionForPlayer(physical)).id
                )
            )
        assertEquals(3, shared.damageBy(999, HitOrigin.Burn))
        assertEquals(3, boss.damageContributions.damageBy(999, HitOrigin.Burn))
    }

    @Test
    fun `departure and rejoin cannot resurrect a queued attack or burn`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, player.displayName))
        f.tick(6)
        val boss = f.npc(player, 12596)
        val queues = WorldQueueList()
        val burn = BurnEffectService(queues, f.modifier)
        assertTrue(burn.apply(player, boss))
        val hit = f.hit(player, boss, 30, delay = 3)
        assertTrue(f.escape(player))
        assertTrue(f.join(player, partner.displayName))
        repeat(4) { WorldQueueListProcess(queues).process() }
        f.tick(4)
        assertFalse(hit.isValidNow())
        assertEquals(600, boss.hitpoints)
        assertEquals(0, burn.stackCount(boss))
        assertEquals(0, f.manager.contribution(player))
    }

    @Test
    fun `external teleport invalidates hits synchronously before instance tick cleanup`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        val launched = f.hit(player, boss, 20)
        val inside = player.coords
        player.coords = ROYAL_TITANS_OUTSIDE
        player.coords = inside
        assertFalse(launched.isValidNow())
        f.tick()
        assertEquals(600, boss.hitpoints)
    }

    @Test
    fun `invalid strong hit preserves weak work and rejects deferred modifier before access`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        var modified = 0
        player.queueImpactHit(
            delay = 2,
            type = HitType.Typeless,
            damage = 10,
            modifier = PlayerHitModifier { _ -> modified++ },
        )
        player.weakQueue("queue.hit", 50, "unrelated work")
        player.combatHitScope?.close()
        val launcher = mock(ProtectedAccessLauncher::class.java)
        PlayerQueueProcessor(f.events, launcher).process(player)
        assertEquals(0, player.queueList.size)
        assertEquals(1, player.weakQueueList.size)
        assertEquals(0, modified)
        verifyNoInteractions(launcher)
    }

    @Test
    fun `legacy validity can recover before due tick and is not early pruned`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        var reachable = false
        val launched =
            boss.queueHit(
                source = player,
                delay = 3,
                type = HitType.Magic,
                damage = 9,
                modifier =
                    org.rsmod.api.npc.hit.modifier.NpcHitModifier {
                        validity = HitValidity { reachable }
                    },
            )
        f.tick()
        assertTrue("queue.hit" in boss.queueList)
        assertTrue(launched.isQueueArgumentValid())
        assertFalse(launched.isValidNow())
        reachable = true
        f.tick(2)
        assertEquals(591, boss.hitpoints)
    }

    @Test
    fun `lethal instant hit closes membership before next same tick impact or modifier`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val processor = DamageOnlyPlayerHitProcessor(f.events, f.npcList, f.players)
        player.takeInstantHit(HitType.Typeless, 99, processor)
        var modified = 0
        val rejected =
            player.takeInstantHit(
                HitType.Typeless,
                20,
                processor,
                modifier = PlayerHitModifier { _ -> modified++ },
            )
        assertEquals(0, player.hitpoints)
        assertEquals(0, modified)
        assertFalse(rejected.isValidNow())
    }

    @Test
    fun `burn emitted before reset cannot land afterward and ordinary burn still deals damage`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        val queues = WorldQueueList()
        val burn = BurnEffectService(queues, f.modifier)
        assertTrue(burn.apply(player, boss))
        repeat(4) { WorldQueueListProcess(queues).process() }
        f.tick()
        assertEquals(599, boss.hitpoints)
        assertEquals(1, boss.damageContributions.damageBy(player, HitOrigin.Burn))
        repeat(4) { WorldQueueListProcess(queues).process() }
        boss.combatHitScope?.invalidate()
        f.tick()
        assertEquals(599, boss.hitpoints)
    }

    @Test
    fun `registry deletion detaches transformed helpers without stale entries`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(2)
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val initial = f.instances.npcsForInstance(session.id).size
        repeat(20) {
            val helper =
                Npc(assertNotNull(dev.openrune.ServerCacheManager.getNpc(14154)), player.coords)
            f.npcs.add(helper, 10)
            f.instances.attachNpc(session.id, helper)
            f.instances.attachNpc(session.id, helper)
            assertEquals(initial + 1, f.instances.npcsForInstance(session.id).size)
            helper.transmog(
                assertNotNull(dev.openrune.ServerCacheManager.getNpc(14148)),
                Int.MAX_VALUE,
            )
            if (it % 2 == 0) f.npcs.del(helper, Int.MAX_VALUE) else f.npcRegistry.del(helper)
            assertNull(f.instances.instanceForNpc(helper))
            assertEquals(initial, f.instances.npcsForInstance(session.id).size)
        }
    }

    @Test
    fun `actual repository expiry detaches while hide and respawn retain ownership`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val boss = f.npc(player, 12596)
        f.npcRegistry.hide(boss)
        assertEquals(session.id, f.instances.instanceForNpc(boss))
        f.npcRegistry.respawn(boss)
        assertEquals(session.id, f.instances.instanceForNpc(boss))
        val helper =
            Npc(assertNotNull(dev.openrune.ServerCacheManager.getNpc(14154)), player.coords)
        f.npcs.add(helper, 2)
        f.instances.attachNpc(session.id, helper)
        f.clock.tick()
        f.clock.tick()
        val lifecycle =
            org.rsmod.api.repo.EntityLifecycleProcess(
                f.npcs,
                mock(org.rsmod.api.repo.loc.LocRepository::class.java),
                mock(org.rsmod.api.repo.obj.ObjRepository::class.java),
                mock(org.rsmod.api.repo.controller.ControllerRepository::class.java),
            )
        lifecycle.process()
        assertFalse(helper.isSlotAssigned)
        assertNull(f.instances.instanceForNpc(helper))
        assertEquals(listOf(boss), f.instances.npcsForInstance(session.id))
    }

    @Test
    fun `HUD closes for deleted identity in either listener order even after a never-HUD transform`() {
        for (hudFirst in listOf(true, false)) {
            val f = RoyalTitansTestWorld(hudFirst)
            val player = f.enter()
            val boss = f.npc(player, 12596)
            // Content-specific mode is not needed: deletion follows the displayed identity even
            // when an inactive/corpse type no longer advertises an ON_ENTER/ON_ATTACK bar.
            f.hud.onOpen(player, boss)
            assertTrue(player.vars["varp.hpbar_hud_npc"] > 0)
            boss.transmog(
                assertNotNull(dev.openrune.ServerCacheManager.getNpc(14148)),
                Int.MAX_VALUE,
            )
            f.npcs.del(boss, Int.MAX_VALUE)
            repeat(3) {
                player.currentMapClock++
                player.processedMapClock = player.currentMapClock
                player.advanceActiveCoroutine()
            }
            assertEquals(0, player.vars["varp.hpbar_hud_npc"])
            assertNull(f.instances.instanceForNpc(boss))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `retaliation retirement preserves weak work and cannot resolve a departed latest aggressor`() {
        val f = RoyalTitansTestWorld()
        val first = f.enter()
        val second = f.newPlayer(2)
        assertTrue(f.join(second, first.displayName))
        val boss = f.npc(first, 12596)
        boss.queueCombatRetaliate(first, 3)
        boss.queueCombatRetaliate(second, 4)
        first.queueCombatRetaliate(boss, 3)
        first.weakQueue("queue.hit", 50, "unrelated")
        second.combatHitScope?.close()
        assertFalse(boss.canRetaliate(), "An older queue must not pick a newer retired aggressor")
        first.combatHitScope?.close()
        val launcher = mock(ProtectedAccessLauncher::class.java)
        PlayerQueueProcessor(f.events, launcher).process(first)
        assertEquals(0, first.queueList.size)
        assertEquals(1, first.weakQueueList.size)
        verifyNoInteractions(launcher)
    }

    @Test
    fun `scoped toxins cannot reload as unscoped damage but ordinary poison persists`() {
        for (venom in listOf(false, true)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            if (venom) assertTrue(PlayerVenom.tryVenom(player))
            else assertTrue(PlayerPoison.tryPoisonAfterInterval(player, initialDamage = 4))
            val savedAttrs = player.attr.toPersistentMap()
            val severity = player.vars[if (venom) "varp.venom_strikes" else "varp.poison_severity"]
            assertTrue(savedAttrs.values.none { it is HitContext || it is HitScope })
            val reloaded = f.newPlayer(3)
            reloaded.attr.putAllFromPersistence(savedAttrs)
            VarPlayerIntMapSetter.set(
                reloaded,
                if (venom) "varp.venom_strikes" else "varp.poison_severity",
                severity,
            )
            val scripts = ScriptContext(f.events, CheatCommandMap(), EngineQueueCache())
            with(PlayerPoisonTimerScript(f.clock)) { scripts.startup() }
            f.events.publish(SessionStateEvent.Login(reloaded))
            assertFalse(PlayerPoison.isPoisoned(reloaded))
            assertFalse(PlayerVenom.isEnvenomed(reloaded))
            val ordinary = f.newPlayer(4)
            assertTrue(PlayerPoison.tryPoisonAfterInterval(ordinary, initialDamage = 4))
            f.events.publish(SessionStateEvent.Logout(ordinary))
            f.events.publish(SessionStateEvent.Login(ordinary))
            assertTrue(PlayerPoison.isPoisoned(ordinary))
            f.events.publish(SessionStateEvent.Logout(player))
            assertFalse(PlayerPoison.isPoisoned(player))
            assertFalse(PlayerVenom.isEnvenomed(player))
            framework().clearInlineMocks()
        }
    }

    companion object {
        @BeforeAll @JvmStatic fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }

    @Test
    fun `external instance termination retires all damage and detaches every helper`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.summon(player)
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val boss = f.npc(player, 12596)
        val pending =
            player.queueHit(
                source = boss,
                delay = 3,
                type = HitType.Typeless,
                damage = 10,
                modifier = NoopPlayerHitModifier,
            )
        assertTrue(f.instances.terminate(session))
        assertFalse(pending.isValidNow())
        assertFalse(f.manager.isActive(player))
        assertEquals(0, f.instances.npcsForInstance(session.id).size)
        assertFalse(f.manager.isEncounterNpc(boss))
        assertFalse(f.instances.terminate(session))
    }

    @Test
    fun `retired burn cannot be consumed by atlatl or occupy new stack limits`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val boss = f.npc(player, 12596)
        val burn = BurnEffectService(WorldQueueList(), f.modifier)
        assertTrue(burn.apply(player, boss, maxStacks = 1))
        boss.combatHitScope?.invalidate()
        assertEquals(0, burn.consumeRemainingDamage(boss))
        assertTrue(burn.apply(player, boss, maxStacks = 1))
        boss.combatHitScope?.invalidate()
        assertEquals(0, burn.stackCount(boss))
        assertTrue(burn.apply(player, boss, maxStacks = 1))
    }

    @Test
    fun `full Titan freeze resistance uses the shared immune result`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(2)
        for (id in listOf(12596, 14147)) {
            val boss = f.npc(player, id)
            assertFalse(org.rsmod.api.combat.commons.CombatEffects.bind(boss, 32))
            assertFalse(boss.isBound)
        }
    }
}
