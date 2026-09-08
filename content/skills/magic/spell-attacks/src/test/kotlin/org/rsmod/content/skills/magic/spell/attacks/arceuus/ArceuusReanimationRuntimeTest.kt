package org.rsmod.content.skills.magic.spell.attacks.arceuus

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.events.interact.ObjTargetEvent
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.ObjTargetData
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.EnsouledHeadProvenance
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Uses actual registered spell events, real rune transactions and NPC spawning with a manual clock. */
@OptIn(InternalApi::class)
class ArceuusReanimationRuntimeTest {
    @Test fun `four live reanimation spells retain current levels XP and exact rune costs`() {
        val f = ReanimationFixture(ALTAR)
        val expected = listOf(
            Triple(16, 32.0, mapOf(559 to 4, 561 to 2)),
            Triple(41, 80.0, mapOf(559 to 4, 561 to 3, 566 to 1)),
            Triple(72, 138.0, mapOf(565 to 1, 561 to 3, 566 to 2)),
            Triple(90, 170.0, mapOf(565 to 2, 561 to 4, 566 to 4)),
        )
        ArceuusReanimationTier.entries.forEachIndexed { index, tier ->
            val spell = f.spell(tier)
            assertEquals(expected[index].first, spell.levelReq)
            assertEquals(expected[index].second, spell.castXp)
            assertEquals(expected[index].third, spell.objReqs.associate { it.obj.id to it.count })
        }
    }

    @Test fun `all 46 inventory head variants cast correct tier consume runes and award current spell XP`() {
        for (definition in ArceuusReanimations.definitions) for (headId in definition.headIds) {
            val f = ReanimationFixture(ALTAR)
            val spell = f.spell(definition.tier)
            f.supply(spell)
            f.player.inv[20] = f.item(headId)
            val before = f.magicXP()
            f.castInventory(spell, headId)
            f.tick(10)
            assertEquals(definition.npcId, f.npcs.single().id, "head=$headId")
            assertNull(f.player.inv[20], "head=$headId")
            assertEquals((spell.castXp * 10).toInt(), f.magicXP() - before, "head=$headId")
            spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName)) }
            val creature = f.npcs.single()
            assertIs<NpcAttackValidateResult.Pass>(f.state.validateReanimatedAttack(f.player, creature))
            assertIs<NpcAttackValidateResult.Deny>(f.state.validateReanimatedAttack(Player(), creature))
            val prayerBefore = f.player.statMap.getFineXP("stat.prayer")
            creature.hitpoints = 0
            f.state.onNpcDamage(f.player, creature, 1)
            f.tick()
            f.state.onNpcDamage(f.player, creature, 1)
            assertEquals((definition.prayerXp * 10).toInt(),
                f.player.statMap.getFineXP("stat.prayer") - prayerBefore, "head=$headId")
            assertFalse(f.state.hasActiveReanimation(f.player))
            f.state.clear(f.player)
        }
    }

    @Test fun `all 23 freshly dropped heads cast directly on ground away from altar`() {
        for (definition in ArceuusReanimations.definitions) {
            val f = ReanimationFixture(ORIGIN)
            val spell = f.spell(definition.tier)
            f.supply(spell)
            val head = EnsouledHeadProvenance.fromMonsterDrop(f.item(definition.headIds.max()), ORIGIN)
            val ground = f.drop(head, ORIGIN.translate(1, 0))
            f.castGround(spell, ground)
            f.tick(10)
            assertEquals(definition.npcId, f.npcs.single().id)
            assertEquals(ground.coords, f.npcs.single().coords)
            assertTrue(f.objects.findAll(ground.coords).none { it === ground })
            f.state.clear(f.player)
        }
    }

    @Test fun `fresh inventory origin survives copying and includes exactly 31 square tiles on original floor`() {
        for ((delta, level, allowed) in listOf(
            Triple(15, 0, true), Triple(-15, 0, true), Triple(16, 0, false),
            Triple(-16, 0, false), Triple(0, 1, false))) {
            val f = ReanimationFixture(ORIGIN.translate(delta, delta, level))
            val definition = ArceuusReanimations.definitions.first()
            val spell = f.spell(definition.tier)
            f.supply(spell)
            val head = EnsouledHeadProvenance.fromMonsterDrop(f.item(definition.headIds.max()), ORIGIN)
            f.player.inv[20] = InvObj(head)
            f.castInventory(spell, head.id)
            f.tick(10)
            assertEquals(allowed, f.npcs.any(), "delta=$delta level=$level")
            assertEquals(!allowed, f.player.inv[20] != null)
            f.state.clear(f.player)
        }
    }

    @Test fun `banked tradeable and originless heads outside altar are rejected without resource loss`() {
        for (kind in 0..2) {
            val f = ReanimationFixture(ORIGIN)
            val definition = ArceuusReanimations.definitions.first()
            val spell = f.spell(definition.tier)
            f.supply(spell)
            val fresh = EnsouledHeadProvenance.fromMonsterDrop(f.item(definition.headIds.max()), ORIGIN)
            val item = when (kind) {
                0 -> EnsouledHeadProvenance.normalizeStored(fresh)
                1 -> f.item(definition.headIds.max())
                else -> fresh.copy(vars = 0)
            }
            f.player.inv[20] = item
            val before = f.magicXP()
            f.castInventory(spell, item.id)
            f.tick(10)
            assertFalse(f.npcs.any())
            assertEquals(item, f.player.inv[20])
            assertEquals(before, f.magicXP())
            spell.objReqs.forEach { assertEquals(it.count, f.player.inv.count(it.obj.internalName)) }
        }
    }

    @Test fun `noted heads wrong tiers insufficient magic runes and wrong spellbook cannot consume a head`() {
        for (invalid in 0..4) {
            val f = ReanimationFixture(ALTAR)
            val dog = ArceuusReanimations.byHead.getValue(13469)
            val spell = f.spell(if (invalid == 1) ArceuusReanimationTier.Basic else dog.tier)
            f.supply(spell)
            val item = f.item(if (invalid == 0) 13470 else 13469)
            f.player.inv[20] = item
            when (invalid) {
                2 -> f.player.statMap.setCurrentLevel("stat.magic", 1)
                3 -> f.player.inv[0] = null
                4 -> VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 0)
            }
            val before = f.magicXP()
            f.castInventory(spell, item.id)
            f.tick(10)
            assertFalse(f.npcs.any(), "case=$invalid")
            assertEquals(item, f.player.inv[20])
            assertEquals(before, f.magicXP())
        }
    }

    @Test fun `active reanimation blocks second cast and departed ground item cannot consume runes`() {
        val f = ReanimationFixture(ALTAR)
        val definition = ArceuusReanimations.definitions.first()
        val spell = f.spell(definition.tier)
        f.supply(spell)
        f.player.inv[20] = f.item(definition.headIds.max())
        f.castInventory(spell, definition.headIds.max())
        f.tick(10)
        f.supply(spell)
        f.player.inv[20] = f.item(definition.headIds.max())
        f.castInventory(spell, definition.headIds.max())
        assertEquals(1, f.npcs.count())
        assertNotNull(f.player.inv[20])
        spell.objReqs.forEach { assertEquals(it.count, f.player.inv.count(it.obj.internalName)) }
        f.state.clear(f.player)
        val ground = f.drop(f.item(definition.headIds.max()), ALTAR.translate(1, 0))
        assertTrue(f.objects.del(ground))
        f.castGround(spell, ground)
        f.tick(10)
        assertFalse(f.npcs.any())
        spell.objReqs.forEach { assertEquals(it.count, f.player.inv.count(it.obj.internalName)) }
    }

    @Test fun `ground splitting preserves every heads original owner and origin vars`() {
        val f = ReanimationFixture(ORIGIN)
        val head = EnsouledHeadProvenance.fromMonsterDrop(f.item(26997).copy(count = 3), ORIGIN)
        f.drop(head, ORIGIN)
        val drops = f.objects.findAll(ORIGIN).toList()
        assertEquals(3, drops.size)
        drops.forEach {
            assertEquals(head.vars, it.vars)
            assertEquals(f.player.observerUUID, it.nullableOwnerId)
            assertTrue(EnsouledHeadProvenance.canReanimateAt(it.type, it.vars, ORIGIN))
        }
    }

    @Test fun `reanimation retains attributed kill hooks while suppressing remains and full reward suppression still wins`() {
        val f = ReanimationFixture(ALTAR)
        f.state.spawnReanimated(f.player, ArceuusReanimations.definitions.first())
        val creature = f.npcs.single()
        assertTrue(creature.suppressDrops)
        assertFalse(creature.suppressRewards)
        creature.recordDamage(f.player, 10)
        var remainsHooks = 0
        var attributedKills = 0
        val death = NpcDeath(mock(NpcRepository::class.java), f.players, f.objects,
            setOf(NpcDeathDropHook { remainsHooks++; true }),
            setOf(NpcDeathKillHook { context ->
                assertSame(f.player, context.hero)
                assertSame(creature, context.npc)
                attributedKills++
            }))
        val access = mock(StandardNpcAccess::class.java)
        `when`(access.npc).thenReturn(creature)
        death.spawnDrops(access, creature.coords)
        assertEquals(1, attributedKills)
        assertEquals(0, remainsHooks)
        assertFalse(f.objects.findAll(creature.coords).any())
        creature.suppressRewards = true
        death.spawnDrops(access, creature.coords)
        assertEquals(1, attributedKills)
        creature.suppressRewards = false
        creature.suppressDrops = false
        death.spawnDrops(access, creature.coords)
        assertEquals(2, attributedKills)
        assertEquals(1, remainsHooks)
        f.state.clear(f.player)
    }

    companion object {
        private val ALTAR = CoordGrid(1700, 3880)
        private val ORIGIN = CoordGrid(3200, 3200)
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}

@OptIn(InternalApi::class)
private class ReanimationFixture(coords: CoordGrid) {
    val clock = MapClock(100)
    val events = EventBus()
    val context = mock(ProtectedAccessContext::class.java)
    val npcs = NpcList()
    val players = PlayerList()
    val collision = CollisionFlagMap()
    val world = WorldRepository(ZoneUpdateMap())
    val objects = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
    val state = ArceuusUtilityState(clock, CombatCorruption(clock), SpecialAttackEnergy(),
        DefaultGameRandom(42L), NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs),
        world, NpcHitModifier {}, AiPlayerInteractions(events, players))
    val registry = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        this.coords = coords
        currentMapClock = clock.cycle; processedMapClock = clock.cycle
        xpRate = 1.0; globalXpRate = 1.0
        for (stat in listOf("magic", "hitpoints", "prayer")) {
            statMap.setXP("stat.$stat", PlayerSkillXPTable.getXPFromLevel(99))
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    init {
        for (dx in -8..8) for (dz in -8..8) collision.allocateIfAbsent(coords.x + dx, coords.z + dz, coords.level)
        players[1] = player
        `when`(context.collision).thenReturn(collision)
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        val fakes = FakeRuneRepository(); val combos = ComboRuneRepository(); val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository(); val staves = StaffSubstituteRepository(); val subs = RuneSubstituteRepository()
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(FakeRuneScript(fakes), ComboRuneScript(combos), CompactRuneScript(compact),
            UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves), RuneSubstituteScript(subs))
            .forEach { with(it) { scriptContext.startup() } }
        with(ArceuusReanimationSpellScript(registry,
            MagicRuneManager(fakes, combos, compact, unlimited, staves, subs), state, events,
            ProtectedAccessLauncher(factory), world, objects)) { scriptContext.startup() }
    }
    fun item(id: Int) = InvObj(assertNotNull(ServerCacheManager.getItem(id)), 1)
    fun spell(tier: ArceuusReanimationTier) = assertNotNull(registry.getObjSpell(
        assertNotNull(ServerCacheManager.getItem(tier.spellObj.asRSCM(RSCMType.OBJ)))))
    fun supply(spell: MagicSpell) = spell.objReqs.forEachIndexed { i, req -> player.inv[i] = InvObj(req.obj, req.count) }
    fun magicXP() = player.statMap.getFineXP("stat.magic")
    fun drop(item: InvObj, coords: CoordGrid): Obj = objects.add(item, coords, 1000, player)
    fun castInventory(spell: MagicSpell, id: Int) {
        assertTrue(events.publish(IfOverlayButtonT(player, -1, null, 20,
            assertNotNull(ServerCacheManager.getItem(id)), Component(spell.component.packed),
            Component("component.inventory:items".asRSCM(RSCMType.COMPONENT)))))
    }
    fun castGround(spell: MagicSpell, obj: Obj) {
        assertTrue(withProtectedAccess(player, context) {
            assertTrue(events.publish(this, ObjTargetEvent(obj, ObjTargetData(Component(spell.component.packed), -1, null))))
        })
    }
    fun tick(count: Int = 1) = repeat(count) {
        clock.tick()
        player.currentMapClock = clock.cycle; player.processedMapClock = clock.cycle
        player.advanceActiveCoroutine()
        state.tick(player)
    }
}
