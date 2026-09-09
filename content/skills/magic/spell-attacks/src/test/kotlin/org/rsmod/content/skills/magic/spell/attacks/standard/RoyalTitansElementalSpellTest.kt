package org.rsmod.content.skills.magic.spell.attacks.standard

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.*
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class RoyalTitansElementalSpellTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `exact native accuracy boundaries include floor Void and overflow protection`() {
        for ((bonus, void, expected) in
            listOf(
                Triple(-100, false, 3000),
                Triple(0, false, 3000),
                Triple(69, false, 9900),
                Triple(70, false, 10000),
                Triple(-100, true, 4350),
                Triple(38, true, 9860),
                Triple(39, true, 10000),
                Triple(Int.MAX_VALUE, true, 10000),
                Triple(Int.MIN_VALUE, false, 3000),
            )) {
            val chance =
                assertNotNull(
                    RoyalTitansElementalAccuracy.hitChance(14150, true, false, bonus, void)
                )
            assertEquals(expected, chance)
            val rng = mock(GameRandom::class.java)
            `when`(rng.of(10000)).thenReturn(chance - 1)
            assertTrue(AccuracyFormulae.isSuccessfulHit(chance, rng))
            if (chance < 10000) {
                `when`(rng.of(10000)).thenReturn(chance)
                assertFalse(AccuracyFormulae.isSuccessfulHit(chance, rng))
            }
        }
    }

    @Test
    fun `guaranteed opposing hits use the registered handler for manual and autocast`() {
        for (autocast in listOf(false, true)) for ((npc, spell) in
            listOf(14150 to "obj.47_water_blast", 14151 to "obj.59_fire_blast")) {
            val f = ElementalCastFixture(npc, spell, 70, 9999, autocast)
            f.cast()
            assertEquals(0, f.ordinaryAccuracyRolls)
            assertEquals(16, f.nativeBaseMaxHit, "Water Blast must scale at 99 Magic too")
            assertEquals(37, f.queuedDamage, "Preserve the native damage pipeline's result")
            assertEquals(0, f.splashHits)
            assertEquals(1, f.projectiles)
            f.spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName)) }
        }
    }

    @Test
    fun `full Void reaches guarantee at 39 but missing gloves does not`() {
        for (gloves in listOf(true, false)) {
            val f = ElementalCastFixture(14150, "obj.47_water_blast", 39, 9999)
            f.player.hat = InvObj("obj.game_pest_mage_helm", 1)
            f.player.torso = InvObj("obj.pest_void_knight_top", 1)
            f.player.legs = InvObj("obj.pest_void_knight_robes", 1)
            if (gloves) f.player.hands = InvObj("obj.pest_void_knight_gloves", 1)
            f.cast()
            assertEquals(if (gloves) 37 else null, f.queuedDamage)
            assertEquals(if (gloves) 0 else 1, f.splashHits)
        }
    }

    @Test
    fun `failed special accuracy takes the native splash path without calculating damage`() {
        val f = ElementalCastFixture(14150, "obj.47_water_blast", -64, 3000)
        f.cast()
        assertEquals(1, f.splashHits)
        assertNull(f.nativeBaseMaxHit)
        assertNull(f.queuedDamage)
        assertEquals(0, f.ordinaryAccuracyRolls)
    }

    @Test
    fun `other NPCs bosses and wrong element keep normal spell accuracy`() {
        for ((npc, spell) in
            listOf(
                82 to "obj.47_water_blast",
                12596 to "obj.47_water_blast",
                14150 to "obj.59_fire_blast",
                14151 to "obj.47_water_blast",
            )) {
            val f = ElementalCastFixture(npc, spell, 70, 0)
            f.cast()
            assertEquals(1, f.ordinaryAccuracyRolls)
            assertEquals(1, f.splashHits)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}

/** Real registered spell + rune manager; record delegation to the combat manager separately. */
@OptIn(InternalApi::class)
private class ElementalCastFixture(
    npcId: Int,
    internal: String,
    bonus: Int,
    roll: Int,
    autocast: Boolean = false,
) {
    val player =
        Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            for (stat in listOf("magic", "hitpoints", "prayer")) {
                statMap.setXP("stat.$stat", PlayerSkillXPTable.getXPFromLevel(99))
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    private val spells =
        Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    val spell =
        assertNotNull(
            spells.getObjSpell(
                assertNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ)))
            )
        )
    private val target = Npc(assertNotNull(ServerCacheManager.getNpc(npcId)), CoordGrid(3200, 3203))
    private val context = mock(ProtectedAccessContext::class.java)
    private val handler: SpellAttack
    var queuedDamage: Int? = null
    var nativeBaseMaxHit: Int? = null
    var ordinaryAccuracyRolls = 0
    var splashHits = 0
    var projectiles = 0

    init {
        // This fixture checks spell dispatch, not routing; don't allocate a world collision map.
        `when`(context.collision).thenReturn(mock(CollisionFlagMap::class.java))
        `when`(context.eventBus).thenReturn(EventBus())
        VarPlayerIntMapSetter.set(player, "varbit.autocast_set", if (autocast) 1 else 0)
        val fakes = FakeRuneRepository()
        val combos = ComboRuneRepository()
        val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository()
        val staves = StaffSubstituteRepository()
        val subs = RuneSubstituteRepository()
        val scripts = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
        listOf(
                FakeRuneScript(fakes),
                ComboRuneScript(combos),
                CompactRuneScript(compact),
                UnlimitedRuneScript(unlimited),
                StaffSubstituteScript(staves),
                RuneSubstituteScript(subs),
            )
            .forEach { with(it) { scripts.startup() } }
        val runes = MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
        val attacks =
            mock(PlayerAttackManager::class.java) { call ->
                when (call.method.name) {
                    "rollSpellAccuracy" -> {
                        ordinaryAccuracyRolls++
                        false
                    }
                    "rollSpellMaxHit" -> {
                        nativeBaseMaxHit = call.arguments[4] as Int
                        37
                    }
                    "spawnProjectile" -> {
                        projectiles++
                        ProjAnim.fromBoundsToNpc(
                            player.bounds(),
                            target,
                            (call.arguments[2] as String).asRSCM(RSCMType.SPOTANIM),
                            "projanim.magic_spell",
                        )
                    }
                    "queueMagicHit" -> {
                        queuedDamage = call.arguments[3] as Int
                        mock(Hit::class.java)
                    }
                    "queueSplashHit" -> {
                        splashHits++
                        mock(Hit::class.java)
                    }
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            }
        val rng = mock(GameRandom::class.java)
        `when`(rng.of(10000)).thenReturn(roll)
        val bonuses = mock(WornBonuses::class.java)
        `when`(bonuses.offensiveMagicBonus(player)).thenReturn(bonus)
        val manager = SpellAttackManager(attacks, runes, DefaultGameRandom(42L))
        val registry = SpellAttackRegistry()
        with(
            ElementalSpells(
                spells,
                rng,
                bonuses,
                RoyalTitansWallSpells(
                    runes,
                    mock(WorldRepository::class.java),
                    StandardNpcHitModifier(EventBus()),
                ),
            )
        ) {
            SpellAttackRepository(registry).register(manager)
        }
        assertEquals(20, registry.spellIds().size)
        handler = assertNotNull(registry[internal])
        spell.objReqs.forEachIndexed { index, req ->
            player.inv[index] = InvObj(req.obj, req.count)
        }
    }

    fun cast() =
        assertTrue(
            withProtectedAccess(player, context) {
                handler.attack(this, target, CombatAttack.Spell(null, spell, false))
            }
        )
}
