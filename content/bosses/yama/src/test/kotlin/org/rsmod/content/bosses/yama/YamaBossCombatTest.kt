package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Exercises the production Yama script through native AI dispatch and BossCombat. */
@OptIn(InternalApi::class)
class YamaBossCombatTest {
    @Test
    fun `native counted programs preserve every contract and phase action sequence`() {
        for (contract in YamaContract.entries) for (phase in 1..3) {
            try {
                val f = Fixture()
                f.activate(phase, contract)
                assertTrue(SpecValidator.validate(f.encounter.spec).isEmpty())
                val legacy = YamaRotation(contract).apply { resetForPhase(phase != 3) }
                val expected = mutableListOf<String>()
                repeat(24) {
                    val special = legacy.next(phase)
                    if (phase == 3 || special == null) {
                        expected += "normal"
                        if (phase == 3) expected += "followup"
                    }
                    special?.let { expected += it.name }
                    f.attack()
                    assertEquals(expected, f.actions, "$contract phase=$phase opportunity=$it")
                    f.advance(YamaRules.attackRate(phase, contract))
                }
                assertEquals(24, f.switches)
            } finally {
                // Inline mock answers retain the complete fixture, including its collision map.
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `duplicate and early native callbacks consume no action or target switch`() {
        val f = Fixture()
        f.activate(1, YamaContract.NONE)
        f.attack()
        repeat(7) {
            f.attack()
            assertEquals(listOf("normal"), f.actions)
            assertEquals(1, f.switches)
            f.advance(1)
        }
        f.attack()
        assertEquals(listOf("normal"), f.actions)
        f.advance(1)
        f.attack()
        assertEquals(listOf("normal", "GLYPH_ATTACK"), f.actions)
        assertEquals(2, f.switches)
    }

    @Test
    fun `native zero-rate duo launch is followed by exactly one eight or seven tick DSL lock`() {
        for (contract in listOf(YamaContract.NONE, YamaContract.FAMILIAR)) for (phase in
            listOf(1, 3)) {
            try {
                val f = Fixture(nativeCombat = true)
                f.activate(phase, contract)
                val rate = YamaRules.attackRate(phase, contract)
                f.attack()
                // Real NvPCombat must accept both ordered recipients before BossCombat arms
                // cadence.
                assertEquals(listOf(1, 2), f.nativeTargets)
                assertEquals(listOf(100, 100), f.nativeLaunchLocks)
                assertEquals(100 + rate, f.npc.actionDelay)
                assertEquals(listOf(3), f.queuedHitDelays(f.player))
                assertEquals(listOf(3), f.queuedHitDelays(f.partner))
                repeat(rate - 1) {
                    f.advance(1)
                    f.attack()
                    assertEquals(2, f.nativeTargets.size)
                    assertEquals(1, f.switches)
                }
                f.advance(1)
                f.attack()
                assertEquals(100 + rate * 2, f.npc.actionDelay)
                assertEquals(2, f.switches)
                // P1's second opportunity is its first special; P3 always includes a normal.
                assertEquals(if (phase == 3) 4 else 2, f.nativeTargets.size)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `inactive Judge phase suppresses actions and return resumes after native six tick lock`() {
        val f = Fixture()
        f.activate(1, YamaContract.NONE)
        f.attack()
        f.encounter.transitionTo(YAMA_INACTIVE_PHASE, f.clock.cycle)
        f.advance(30)
        f.attack()
        assertEquals(listOf("normal"), f.actions)
        f.activate(2, YamaContract.NONE, delay = YamaCombatTimeline.PHASE_RESUME_ATTACK_DELAY)
        repeat(6) {
            f.attack()
            assertEquals(listOf("normal"), f.actions)
            f.advance(1)
        }
        f.attack()
        assertEquals(listOf("normal", "normal"), f.actions)
        f.advance(8)
        f.attack()
        assertEquals(listOf("normal", "normal", "METEOR_STRIKE"), f.actions)
    }

    @Test
    fun `native deletion ends encounter and new actor starts a fresh action program`() {
        val f = Fixture()
        f.activate(1, YamaContract.NONE)
        f.attack()
        val previous = f.encounter
        f.events.publish(NpcStateEvents.Delete(f.npc))
        assertTrue(previous.isEnded)
        assertNull(f.registry.existing(f.npc))
        f.activate(1, YamaContract.NONE)
        assertNotSame(previous, f.encounter)
        f.advance(8)
        f.attack()
        assertEquals(listOf("normal", "normal"), f.actions)
    }

    private class Fixture(private val nativeCombat: Boolean = false) {
        val clock = MapClock().apply { cycle = 100 }
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(YamaAssets.BOSS)), CoordGrid(2906, 9566))
                .apply {
                    slotId = 1
                    assignUid()
                    hitpoints = 1000
                    currentMapClock = 100
                    processedMapClock = 100
                    infoProtocol = NoopNpcInfo
                    mode = NpcMode.ApPlayer2
                }
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                assignUid()
                coords = CoordGrid(2911, 9566)
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        val partner =
            Player().apply {
                slotId = 2
                uuid = 2L
                assignUid()
                coords = player.coords
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        private val players =
            PlayerList().apply {
                this[1] = player
                this[2] = partner
            }
        private val random = DefaultGameRandom(1L)
        val registry = EncounterRegistry(random)
        val events = EventBus()
        val actions = mutableListOf<String>()
        val nativeTargets = mutableListOf<Int>()
        val nativeLaunchLocks = mutableListOf<Int>()
        var switches = 0
        private val manager =
            mock(YamaEncounterManager::class.java) { call ->
                when (call.method.name) {
                    "canAttack" -> true
                    "standardAttack" -> {
                        actions += "normal"
                        if (nativeCombat) launchNativeDuo(call.arguments[0] as StandardNpcAccess)
                        null
                    }
                    "specialAttack" -> {
                        actions += (call.arguments[2] as YamaSpecial).name
                        null
                    }
                    "phaseThreeFollowup" -> {
                        actions += "followup"
                        null
                    }
                    "afterAttack" -> {
                        switches++
                        null
                    }
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            }
        private val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) {
                    nativeTargets += (call.arguments[1] as Player).slotId
                    true
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        private val world = mock(WorldRepository::class.java)
        private val native = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
        private val deps =
            BossDeps(
                random,
                accuracy,
                world,
                mock(NpcRepository::class.java),
                players,
                clock,
                WorldQueueList(),
                CollisionFlagMap(),
                registry,
                BossExtensionRegistry(),
                native,
            )

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(YamaCombat(manager, mock(NpcDeath::class.java), players, NpcList(), deps)) {
                context.startup()
            }
        }

        val encounter: BossEncounter
            get() = registry.of(npc)

        private fun launchNativeDuo(access: StandardNpcAccess) {
            for (target in listOf(player, partner)) {
                native.attack(
                    access,
                    target,
                    CombatAttack.NpcMelee(MeleeAttackType.Crush),
                    NpcAttackOverride(
                        attackAnimation =
                            requireNotNull(
                                RSCM.getReverseMapping(RSCMType.SEQ, YamaAssets.ANIM_MELEE)
                            ),
                        maxHit = 46,
                        impactDelay = YamaCombatTimeline.STANDARD_IMPACT_DELAY,
                        attackRate = 0,
                        applyModifierOnImpact = true,
                    ),
                )
                nativeLaunchLocks += npc.actionDelay
            }
        }

        fun queuedHitDelays(target: Player): List<Int> {
            val iterator = target.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val queue = iterator.next()
                        if (queue.args is DeferredPlayerHit) add(queue.remainingCycles)
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }

        fun activate(phase: Int, contract: YamaContract, delay: Int = 0) {
            encounter.transitionTo(yamaCombatPhase(phase, contract), clock.cycle)
            encounter.lastAbilityTick = clock.cycle - encounter.expectedAttackRate()
            npc.actionDelay = maxOf(npc.actionDelay, clock.cycle + delay)
        }

        fun advance(ticks: Int) {
            clock.cycle += ticks
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
        }

        fun attack() {
            npc.launch {
                val access =
                    StandardNpcAccess(
                        npc,
                        this,
                        StandardNpcAccessContext(
                            { random },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    )
                events.publish(access, AiPlayerEvents.Ap2(player, npc))
            }
        }
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
