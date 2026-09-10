package org.rsmod.content.bosses.royaltitans

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.player.PvPAreaAttackManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.interact.NpcTEvents
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.*
import org.rsmod.api.spells.autocast.AutocastSpells
import org.rsmod.api.spells.autocast.AutocastWeapons
import org.rsmod.api.spells.autocast.scripts.AutocastScript
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.content.skills.magic.spell.attacks.ancient.AncientAreaTargetSelector
import org.rsmod.content.skills.magic.spell.attacks.ancient.AncientCombatSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

class RoyalTitansWallCastTest {
    @Test
    fun `manual water surge consumes runes once no XP and preserves delay until impact`() {
        for (remaining in listOf(-3, 0, 12)) withFixture { f ->
            val wall = f.fireWall()
            val spell = f.spell("obj.85_water_surge")
            f.supply(spell)
            f.player.actionDelay = f.game.clock.cycle + remaining
            val delay = f.player.actionDelay
            val xp = f.magicXp()
            f.manual(wall, spell)
            assertEquals(delay, f.player.actionDelay)
            assertEquals(xp, f.magicXp())
            spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName)) }
            assertEquals(5, f.game.walls(f.player, 14152).size)
            val projectile = assertNotNull(f.projectile)
            assertEquals(76, projectile.durations.clientDelay)
            assertEquals(3, projectile.durations.serverDelay)
            f.game.tick(2)
            assertEquals(5, f.game.walls(f.player, 14152).size)
            f.game.tick()
            assertEquals(2, f.game.walls(f.player, 14152).size)
            assertEquals(delay, f.player.actionDelay)
            assertEquals(xp, f.magicXp())
            f.assertNoCombatCalculation()
        }
    }

    @Test
    fun `autocast and manual interactions can clear opposite walls two ticks apart`() =
        withFixture { f ->
            val ice = f.iceWall()
            val fireSpell = f.spell("obj.95_fire_surge")
            val waterSpell = f.spell("obj.85_water_surge")
            f.supply(fireSpell, waterSpell)
            val delay = f.game.clock.cycle + 20
            f.player.actionDelay = delay
            f.autocast(ice, fireSpell, op = true)
            f.game.tick(2)
            f.manual(f.fireWall(), waterSpell)
            assertEquals(delay, f.player.actionDelay)
            f.game.tick(4)
            assertEquals(2, f.game.walls(f.player, 14152).size)
            assertEquals(2, f.game.walls(f.player, 14153).size)
            assertEquals(0, f.magicXp())
            f.assertNoCombatCalculation()
        }

    @Test
    fun `real Douse and Melt clicks bypass existing attack delay without adding a cooldown`() {
        for (id in listOf(14152, 14153)) for (remaining in listOf(0, 12)) withFixture { f ->
            val spell = f.spell(if (id == 14152) "obj.85_water_surge" else "obj.95_fire_surge")
            f.supply(spell, spell)
            val wall = if (id == 14152) f.fireWall() else f.iceWall()
            val delay = f.game.clock.cycle + remaining
            f.player.actionDelay = delay
            f.autocast(wall, spell, op = false)
            assertEquals(1, f.projectileCount)
            assertEquals(delay, f.player.actionDelay)
            f.game.tick()
            f.autocast(wall, spell, op = false)
            assertEquals(2, f.projectileCount, "No five-tick or invented two-tick cast gate")
            assertEquals(delay, f.player.actionDelay)
            assertEquals(0, f.magicXp())
        }
    }

    @Test
    fun `invalid element or tier sends exact message without a cast or rune loss`() {
        for ((wallId, spellName) in
            listOf(
                14152 to "obj.23_water_bolt",
                14152 to "obj.95_fire_surge",
                14153 to "obj.35_fire_bolt",
                14153 to "obj.85_water_surge",
            )) withFixture { f ->
            val wall = if (wallId == 14152) f.fireWall() else f.iceWall()
            val spell = f.spell(spellName)
            f.supply(spell)
            f.player.actionDelay = f.game.clock.cycle + 9
            val delay = f.player.actionDelay
            f.manual(wall, spell)
            assertNull(f.projectile)
            assertFalse("queue.hit" in wall.queueList)
            assertEquals(delay, f.player.actionDelay)
            assertEquals(0, f.magicXp())
            val rejection =
                if (wallId == 14152)
                    "You'll need to use a water or ice spell that is strong enough to put out the fire."
                else "You'll need to use a fire spell to melt the ice."
            assertTrue(f.messages.filterIsInstance<MessageGame>().any { it.message == rejection })
            spell.objReqs.forEach {
                assertEquals(it.count, f.player.inv.count(it.obj.internalName))
            }
            f.game.tick(3)
            assertEquals(5, f.game.walls(f.player, wallId).size)
        }
    }

    @Test
    fun `missing runes wrong book and low level do not form a wall cast`() {
        for (failure in 0..2) withFixture { f ->
            val spell = f.spell("obj.85_water_surge")
            if (failure != 0) f.supply(spell)
            if (failure == 1) VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 1)
            if (failure == 2) f.player.statMap.setCurrentLevel("stat.magic", 1)
            f.manual(f.fireWall(), spell)
            assertNull(f.projectile)
            assertEquals(0, f.magicXp())
            assertFalse("queue.hit" in f.fireWall().queueList)
            if (failure != 0)
                spell.objReqs.forEach {
                    assertEquals(it.count, f.player.inv.count(it.obj.internalName))
                }
        }
    }

    @Test
    fun `Ancient ice casts clear fire but never select area targets freeze or roll damage`() {
        for (name in
            listOf(
                "obj.58_ice_rush",
                "obj.70_ice_burst",
                "obj.82_ice_blitz",
                "obj.94_ice_barrage",
            )) withFixture { f ->
            val spell = f.spell(name)
            f.supply(spell)
            VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 1)
            val wall = f.fireWall()
            val baseDelay = f.player.actionDelay
            f.manual(wall, spell)
            assertEquals(5, f.game.walls(f.player, 14152).size)
            f.game.tick(assertNotNull(f.projectile).durations.serverDelay)
            assertEquals(2, f.game.walls(f.player, 14152).size)
            assertEquals(0, f.magicXp())
            assertEquals(baseDelay, f.player.actionDelay)
            verifyNoInteractions(f.areaTargets, f.poison)
            f.assertNoCombatCalculation()
        }
    }

    @Test
    fun `Twinflame wave autocast uses effective wall blast requirements without an echo`() =
        withFixture { f ->
            val selected = f.spell("obj.75_fire_wave")
            val effective = f.spell("obj.47_water_blast")
            f.player.righthand = InvObj("obj.twinflame_staff", 1)
            f.supply(selected, effective)
            val before =
                effective.objReqs.associate { it.obj.id to f.player.inv.count(it.obj.internalName) }
            val delay = f.game.clock.cycle + 8
            f.player.actionDelay = delay
            f.autocast(f.fireWall(), selected, op = false)
            effective.objReqs.forEach {
                // The native rune pipeline supplies water/fire from the equipped Twinflame staff.
                val consumed =
                    if (it.obj.id == "obj.waterrune".asRSCM(RSCMType.OBJ)) 0 else it.count
                assertEquals(
                    before.getValue(it.obj.id) - consumed,
                    f.player.inv.count(it.obj.internalName),
                )
            }
            assertEquals(1, f.projectileCount)
            assertEquals(0, f.magicXp())
            assertEquals(delay, f.player.actionDelay)
            f.game.tick(4)
            assertEquals(2, f.game.walls(f.player, 14152).size)
            assertEquals(1, f.projectileCount)
        }

    @Test
    fun `removed target and expired instance cannot receive a later wall impact`() =
        withFixture { f ->
            val wall = f.fireWall()
            val spell = f.spell("obj.85_water_surge")
            f.supply(spell)
            f.manual(wall, spell)
            f.game.escape(f.player)
            assertFalse(wall.isSlotAssigned)
            f.game.tick(5)
            assertFalse(wall.isSlotAssigned)
        }

    private fun withFixture(test: (WallCastFixture) -> Unit) {
        try {
            test(WallCastFixture())
        } finally {
            framework().clearInlineMocks()
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

@OptIn(InternalApi::class)
private class WallCastFixture {
    val game = RoyalTitansTestWorld()
    val player = game.enter()
    val messages = mutableListOf<Any>()
    val areaTargets = mock(AncientAreaTargetSelector::class.java)
    val poison = mock(NpcPoisonEffectService::class.java)
    val spells =
        Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    private val context = mock(ProtectedAccessContext::class.java)
    private val npcInteractions = org.rsmod.api.player.interact.NpcInteractions(game.events)
    private val input =
        org.rsmod.api.net.rsprot.handlers.OpNpcHandler(game.events, game.npcList, npcInteractions)
    private val interactionProcessor =
        org.rsmod.api.game.process.player.PlayerInteractionProcessor::class
            .java
            .constructors
            .single()
            .let { ctor ->
                val factory =
                    mock(org.rsmod.api.player.protect.ProtectedAccessContextFactory::class.java)
                `when`(factory.create()).thenReturn(context)
                ctor.newInstance(
                    *ctor.parameterTypes
                        .map { type ->
                            when (type) {
                                org.rsmod.events.EventBus::class.java -> game.events
                                org.rsmod.api.route.BoundValidator::class.java ->
                                    org.rsmod.api.route.BoundValidator(game.collision)
                                org.rsmod.api.route.RayCastValidator::class.java ->
                                    org.rsmod.api.route.RayCastValidator(game.collision)
                                org.rsmod.api.player.interact.NpcInteractions::class.java ->
                                    npcInteractions
                                org.rsmod.api.player.interact.NpcTInteractions::class.java ->
                                    org.rsmod.api.player.interact.NpcTInteractions(game.events)
                                org.rsmod.api.player.protect.ProtectedAccessLauncher::class.java ->
                                    org.rsmod.api.player.protect.ProtectedAccessLauncher(factory)
                                org.rsmod.api.game.process.player.PlayerMovementProcessor::class
                                    .java ->
                                    org.rsmod.api.game.process.player.PlayerMovementProcessor(
                                        game.collision,
                                        org.rsmod.api.route.RouteFactory(game.collision),
                                        org.rsmod.api.route.StepFactory(game.collision),
                                        game.events,
                                    )
                                Set::class.java -> emptySet<Any>()
                                else -> mock(type)
                            }
                        }
                        .toTypedArray()
                ) as org.rsmod.api.game.process.player.PlayerInteractionProcessor
            }
    private val calls = mutableListOf<String>()
    var projectile: ProjAnim? = null
    var projectileCount = 0

    init {
        val scripts = ScriptContext(game.events, CheatCommandMap(), EngineQueueCache())
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { scripts.startup() }
        @Suppress("UNCHECKED_CAST")
        val client =
            mock(Client::class.java) { call ->
                if (call.method.name == "write") messages += call.arguments[0]
                RETURNS_DEFAULTS.answer(call)
            }
                as Client<Any, Any>
        player.client = client
        player.righthand = InvObj(assertNotNull(ServerCacheManager.getItem(1379)), 1)
        `when`(context.collision).thenReturn(game.collision)
        `when`(context.eventBus).thenReturn(game.events)
        val fake = FakeRuneRepository()
        val combo = ComboRuneRepository()
        val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository()
        val staves = StaffSubstituteRepository()
        val subs = RuneSubstituteRepository()
        listOf(
                FakeRuneScript(fake),
                ComboRuneScript(combo),
                CompactRuneScript(compact),
                UnlimitedRuneScript(unlimited),
                StaffSubstituteScript(staves),
                RuneSubstituteScript(subs),
            )
            .forEach { with(it) { scripts.startup() } }
        val runes = MagicRuneManager(fake, combo, compact, unlimited, staves, subs)
        val attacks =
            mock(PlayerAttackManager::class.java) { call ->
                calls += call.method.name
                if (call.method.name == "spawnProjectile") {
                    val value =
                        ProjAnim.fromBoundsToNpc(
                            player.bounds(),
                            call.arguments[1] as Npc,
                            (call.arguments[2] as String).asRSCM(RSCMType.SPOTANIM),
                            "projanim.magic_spell",
                        )
                    game.world.projAnim(value)
                    projectile = value
                    projectileCount++
                    value
                } else RETURNS_DEFAULTS.answer(call)
            }
        val bonuses = mock(WornBonuses::class.java)
        `when`(bonuses.offensiveMagicBonus(player)).thenReturn(-200)
        val random = DefaultGameRandom(42L)
        val manager = SpellAttackManager(attacks, runes, random)
        val registry = SpellAttackRegistry()
        val repository = SpellAttackRepository(registry)
        val walls = RoyalTitansWallSpells(runes, game.world, game.modifier)
        with(ElementalSpells(spells, random, bonuses, walls)) { repository.register(manager) }
        with(
            AncientCombatSpells(areaTargets, poison, mock(PvPAreaAttackManager::class.java), walls)
        ) {
            repository.register(manager)
        }
        val autocastSpells = AutocastSpells()
        with(AutocastScript(autocastSpells)) { scripts.startup() }
        with(
            RoyalTitansWallInteractions(
                game.manager,
                spells,
                registry,
                runes,
                AutocastWeapons(autocastSpells),
            )
        ) {
            scripts.startup()
        }
        game.tick(15)
        messages.clear()
    }

    fun fireWall(): Npc = game.walls(player, 14152).sortedBy { it.coords.x }[2]

    fun iceWall(): Npc = game.walls(player, 14153).sortedBy { it.coords.x }[2]

    fun magicXp(): Int = player.statMap.getFineXP("stat.magic")

    fun spell(name: String): MagicSpell =
        assertNotNull(
            spells.getObjSpell(assertNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))))
        )

    fun supply(vararg spells: MagicSpell) {
        spells
            .flatMap { it.objReqs }
            .groupBy { it.obj.id }
            .values
            .forEachIndexed { slot, reqs ->
                player.inv[slot] = InvObj(reqs.first().obj, reqs.sumOf { it.count })
            }
    }

    fun manual(target: Npc, spell: MagicSpell) =
        assertTrue(
            withProtectedAccess(player, context) {
                game.events.publish(
                    this,
                    NpcTEvents.Ap(target, -1, null, target.visType, spell.component),
                )
            }
        )

    fun autocast(target: Npc, spell: MagicSpell, op: Boolean) {
        val id = spells.autocastSpells().entries.first { it.value.obj.id == spell.obj.id }.key
        VarPlayerIntMapSetter.set(player, "varbit.autocast_set", 1)
        VarPlayerIntMapSetter.set(player, "varbit.autocast_spell", id)
        player.coords = target.coords.translate(0, if (op) 1 else 3)
        assertTrue(
            npcInteractions.hasOp(target, player.vars, org.rsmod.game.interact.InteractionOp.Op1)
        )
        assertFalse(
            npcInteractions.hasOp(target, player.vars, org.rsmod.game.interact.InteractionOp.Op2)
        )
        input.handle(
            player,
            net.rsprot.protocol.game.incoming.npcs.OpNpcV2(target.slotId, false, 1, 0),
        )
        assertNotNull(player.interaction, "Native packet validation must accept Douse/Melt")
        interactionProcessor.process(player)
    }

    fun assertNoCombatCalculation() {
        assertFalse(
            calls.any {
                it in
                    setOf(
                        "rollSpellAccuracy",
                        "rollSpellMaxHit",
                        "giveCombatXp",
                        "queueMagicHit",
                        "queueSplashHit",
                    )
            },
            calls.toString(),
        )
    }
}
