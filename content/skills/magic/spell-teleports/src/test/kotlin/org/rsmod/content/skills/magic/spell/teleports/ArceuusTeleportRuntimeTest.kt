package org.rsmod.content.skills.magic.spell.teleports

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import net.rsprot.protocol.game.incoming.buttons.If1Button
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.commons.magic.HomeTeleportCooldown
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.params
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.net.rsprot.handlers.If1ButtonHandler
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.autocast.AutocastWeapons
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.content.skills.magic.spell.teleports.ArceuusSpellTeleportScript.ArceuusTeleport
import org.rsmod.content.skills.magic.spell.teleports.HomeTeleport.castHome
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class ArceuusTeleportRuntimeTest {
    @Test fun `every Arceuus teleport executes its real cast and consumes required runes`() {
        for (teleport in ArceuusTeleport.entries) {
            val f = TeleportFixture()
            val spells = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
            val spell = assertNotNull(spells.getObjSpell(assertNotNull(ServerCacheManager.getItem(teleport.spellObj.asRSCM(RSCMType.OBJ)))))
            val runes = runeManager()
            val realm = mock(Realm::class.java)
            val config = mock(RealmConfig::class.java)
            `when`(realm.config).thenReturn(config)
            `when`(config.respawnCoord).thenReturn(CoordGrid(3100, 3500, 0))
            val destination = if (teleport == ArceuusTeleport.Respawn) config.respawnCoord
                else teleport.destination ?: assertNotNull(spell.obj.paramOrNull(params.spell_telecoord))
            f.collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 3)
            spell.objReqs.forEachIndexed { index, req -> f.player.inv[index] = InvObj(req.obj, req.count) }
            val script = ArceuusSpellTeleportScript(spells, runes, f.validator, f.areas, realm)
            assertTrue(withProtectedAccess(f.player, f.context) { with(script) { cast(spell, teleport) } })
            assertEquals(6575, f.player.pendingSequence.id, spell.name)
            assertEquals(1296, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id, spell.name)
            f.tick(2)
            assertEquals(f.origin, f.player.coords, spell.name)
            f.tick()
            assertEquals(destination, f.player.coords, spell.name)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence, spell.name)
            assertEquals(65535, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id, spell.name)
            spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName), spell.name) }
            if (teleport == ArceuusTeleport.Home) {
                assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
                assertEquals(0, f.player.vars[HomeTeleport.TIMER])
                f.tick(3)
                f.player.coords = f.origin
                assertTrue(withProtectedAccess(f.player, f.context) { with(script) { cast(spell, teleport) } })
                f.tick(3)
                assertEquals(destination, f.player.coords)
                assertEquals(0, f.player.vars[HomeTeleport.TIMER])
            }
        }
    }

    @Test fun `repeated home packets finish one full cast for Arceuus and Ancients in both button formats`() {
        for (arceuus in listOf(true, false)) for (legacy in listOf(true, false)) {
            val f = TeleportFixture()
            val click = f.homeButton(arceuus, legacy)
            click()
            val active = assertNotNull(f.player.activeCoroutine)
            val ticks = if (arceuus) 3 else HomeTeleport.CAST_TICKS
            repeat(ticks) { tick ->
                val sequence = f.player.pendingSequence
                repeat(10) {
                    click()
                    assertSame(active, f.player.activeCoroutine, "Repeated packet replaced the cast at tick $tick")
                    assertEquals(sequence, f.player.pendingSequence)
                }
                f.tick()
                if (tick < ticks - 1) assertEquals(f.origin, f.player.coords)
            }
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(65535, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id)
            assertEquals(Component.NULL, f.player.ui.ignoredOverlayButton)
            assertNull(f.player.activeCoroutine)
            if (arceuus) {
                assertEquals(0, f.player.vars[HomeTeleport.TIMER])
                f.tick(2)
                f.player.coords = f.origin
                click()
                f.tick(3)
                assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
            } else {
                assertTrue(f.player.vars[HomeTeleport.TIMER] > 0)
            }
        }
    }

    @Test fun `cancelled home casts clear visuals and allow a fresh click`() {
        for (arceuus in listOf(true, false)) {
            val f = TeleportFixture()
            val click = f.homeButton(arceuus, legacy = false)
            click()
            f.tick()
            f.player.cancelActiveCoroutine()
            assertEquals(Component.NULL, f.player.ui.ignoredOverlayButton)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(65535, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id)
            assertEquals(f.origin, f.player.coords)
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
            f.tick(5)
            click()
            f.tick(if (arceuus) 3 else HomeTeleport.CAST_TICKS)
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        }
    }

    @Test fun `Arceuus ignores a saved recharge and clears its client timer while other books retain it`() {
        val f = TeleportFixture()
        val click = f.homeButton(arceuus = true, legacy = false)
        val savedMinute = (System.currentTimeMillis() / 60_000L).toInt()
        VarPlayerIntMapSetter.set(f.player, HomeTeleport.TIMER, savedMinute)
        f.messages.clear()
        HomeTeleportCooldown.syncClient(f.player)
        assertEquals(VarpSmall(892, 0), f.messages.last())
        click()
        f.tick(3)
        assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        assertEquals(savedMinute, f.player.vars[HomeTeleport.TIMER])
        assertFalse(f.messages.any { it == VarpLarge(892, savedMinute) })

        val manager = MagicSpellbookManager(mock(AutocastWeapons::class.java))
        manager.setSpellbook(f.player, Spellbook.Ancients)
        assertEquals(VarpLarge(892, savedMinute), f.messages.last())
        manager.setSpellbook(f.player, Spellbook.Arceuus)
        assertEquals(VarpSmall(892, 0), f.messages.last())
        assertEquals(savedMinute, f.player.vars[HomeTeleport.TIMER])
    }

    @Test fun `home waits 22 ticks and shares its persistent recharge with every destination`() {
        val f = TeleportFixture()
        f.home()
        f.tick(21)
        assertEquals(f.origin, f.player.coords)
        assertFalse(f.player.isDelayed)
        f.tick()
        assertEquals(f.destination, f.player.coords)
        assertEquals(30, HomeTeleport.remainingMinutes(f.player, 1_000_000))
        assertEquals(1, HomeTeleport.remainingMinutes(f.player, 1_000_029))
        assertEquals(0, HomeTeleport.remainingMinutes(f.player, 1_000_030))
        val loaded = Player()
        loaded.vars.backing.putAll(f.player.vars.backing)
        assertEquals(30, HomeTeleport.remainingMinutes(loaded, 1_000_000))
        f.player.coords = f.origin
        f.home(CoordGrid(3087, 3495, 0))
        f.tick(22)
        assertEquals(f.origin, f.player.coords)
    }

    @Test fun `movement combat and cancellation interrupt home without consuming recharge`() {
        for (interruption in 0..2) {
            val f = TeleportFixture()
            f.home()
            f.tick(5)
            when (interruption) {
                0 -> f.player.coords = f.origin.translate(1, 0)
                1 -> VarPlayerIntMapSetter.set(f.player, "varp.lastcombat", f.player.currentMapClock)
                2 -> f.player.activeCoroutine?.cancel()
            }
            f.tick(22)
            assertNotEquals(f.destination, f.player.coords)
            assertEquals(0, HomeTeleport.remainingMinutes(f.player, 1_000_000))
        }
    }

    @Test fun `teleport denial at completion leaves home recharge available`() {
        val f = TeleportFixture()
        var allowed = true
        assertTrue(withProtectedAccess(f.player, f.context) {
            castHome(f.destination, { allowed }, { 1_000_000 })
        })
        f.tick(21)
        allowed = false
        f.tick()
        assertEquals(f.origin, f.player.coords)
        assertEquals(0, HomeTeleport.remainingMinutes(f.player, 1_000_000))
    }

    @Test fun `supported minigame destinations execute and persist their separate 20 minute recharge`() {
        for (destination in MinigameTeleport.DESTINATIONS) {
            val f = TeleportFixture()
            f.collision.allocateIfAbsent(destination.coords.x, destination.coords.z, destination.coords.level)
            val spell = MinigameTeleport(f.validator, f.areas)
            assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { cast(destination) { 1_000_000 } } })
            f.tick(21)
            assertEquals(f.origin, f.player.coords)
            assertFalse(f.player.isDelayed)
            f.tick()
            assertEquals(destination.coords, f.player.coords)
            assertEquals(20, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
            assertEquals(0, HomeTeleport.remainingMinutes(f.player, 1_000_000))
            val loaded = Player().apply { vars.backing.putAll(f.player.vars.backing) }
            assertEquals(1, MinigameTeleport.remainingMinutes(loaded, 1_000_019))
            assertEquals(0, MinigameTeleport.remainingMinutes(loaded, 1_000_020))
            f.player.coords = f.origin
            assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { cast(destination) { 1_000_001 } } })
            f.tick(22)
            assertEquals(f.origin, f.player.coords)
        }
    }

    @Test fun `minigame interruption and invalid destination do not consume recharge`() {
        for (interrupt in listOf(true, false)) {
            val f = TeleportFixture()
            val destination = MinigameTeleport.DESTINATIONS.first()
            val spell = MinigameTeleport(f.validator, f.areas)
            assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { cast(destination) { 1_000_000 } } })
            f.tick(3)
            if (interrupt) f.player.coords = f.origin.translate(1, 0)
            f.tick(22)
            assertNotEquals(destination.coords, f.player.coords)
            assertEquals(0, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
        }
    }

    @Test fun `minigame chooser resumes a real selection and begins the interruptible cast`() {
        val f = TeleportFixture()
        val destination = MinigameTeleport.DESTINATIONS.last()
        f.collision.allocateIfAbsent(destination.coords.x, destination.coords.z, destination.coords.level)
        val spell = MinigameTeleport(f.validator, f.areas)
        assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { openMenu() } })
        assertNotNull(f.player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal"))
        assertNotNull(f.player.activeCoroutine).resumeWith(ResumePauseButtonInput("component.menu:lj_layer1", 1))
        f.tick()
        f.tick(22)
        assertEquals(destination.coords, f.player.coords)
        assertNull(f.player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal"))
    }

    @Test fun `resting teleport requires ten seconds out of combat`() {
        for (combatVar in listOf("varp.lastcombat", "varp.lastcombat_pvp")) {
            val f = TeleportFixture()
            VarPlayerIntMapSetter.set(f.player, combatVar, 84)
            f.home()
            assertNull(f.player.activeCoroutine)
            assertEquals(f.origin, f.player.coords)
            f.tick()
            f.home()
            assertNotNull(f.player.activeCoroutine)
            f.tick(22)
            assertEquals(f.destination, f.player.coords)
        }
    }

    @Test fun `Pest Control teleport enforces its combat requirement before channeling`() {
        val f = TeleportFixture()
        val destination = MinigameTeleport.DESTINATIONS.first()
        f.collision.allocateIfAbsent(destination.coords.x, destination.coords.z, destination.coords.level)
        val spell = MinigameTeleport(f.validator, f.areas)
        f.player.appearance.combatLevel = 39
        assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { cast(destination) { 1_000_000 } } })
        assertNull(f.player.activeCoroutine)
        assertEquals(0, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
        f.player.appearance.combatLevel = 40
        assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { cast(destination) { 1_000_000 } } })
        f.tick(22)
        assertEquals(destination.coords, f.player.coords)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}

@OptIn(InternalApi::class)
private class TeleportFixture {
    val messages = mutableListOf<Any>()
    val origin = CoordGrid(3200, 3200, 0)
    val destination = CoordGrid(1699, 3879, 0)
    val collision = CollisionFlagMap()
    val validator = PlayerTeleportValidator(emptySet())
    val areas = mock(AreaChecker::class.java)
    val context = mock(ProtectedAccessContext::class.java)
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        coords = origin
        currentMapClock = 100
        processedMapClock = 100
        appearance.combatLevel = 126
        for (stat in listOf("magic", "hitpoints", "prayer")) {
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    init {
        player.client = object : Client<Any, Any> {
            override fun write(message: Any) { messages.add(message) }
            override fun close() = Unit
            override fun read(player: Player) = Unit
            override fun flush() = Unit
            override fun flushHighPriority() = Unit
            override fun unregister(service: Any, player: Player) = Unit
        }
        collision.allocateIfAbsent(origin.x, origin.z, origin.level)
        collision.allocateIfAbsent(destination.x, destination.z, destination.level)
        `when`(context.collision).thenReturn(collision)
        `when`(context.areaChecker).thenReturn(areas)
        `when`(context.teleportValidator).thenReturn(validator)
        `when`(context.teleportHooks).thenReturn(PlayerTeleportHooks.EMPTY)
        `when`(context.eventBus).thenReturn(EventBus())
    }
    fun home(destination: CoordGrid = this.destination) {
        assertTrue(withProtectedAccess(player, context) { castHome(destination, { true }, { 1_000_000 }) })
    }
    fun homeButton(arceuus: Boolean, legacy: Boolean): () -> Unit {
        val spells = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
        val obj = if (arceuus) "obj.deadman_level99_lamp" else "obj.01_zaros_home_tele"
        val spell = assertNotNull(spells.getObjSpell(assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))))
        val events = EventBus()
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        if (arceuus) {
            with(ArceuusSpellTeleportScript(spells, runeManager(), validator, areas, mock(Realm::class.java))) {
                scriptContext.startup()
            }
        } else {
            with(AncientSpellTeleportScript(spells, runeManager(), validator, areas)) { scriptContext.startup() }
        }
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", if (arceuus) 3 else 1)
        collision.allocateIfAbsent(3087, 3495, 0)
        player.ui.topLevel = UserInterface(spell.component.packed ushr 16)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        val launcher = ProtectedAccessLauncher(factory)
        val if1 = If1ButtonHandler(events, launcher)
        val if3 = If3ButtonHandler(events, launcher)
        val combined = CombinedId(spell.component.packed)
        return {
            if (legacy) if1.handle(player, If1Button(combined))
            else if3.handle(player, If3Button(combined, -1, -1, 1))
        }
    }
    fun tick(count: Int = 1) = repeat(count) {
        player.currentMapClock++
        player.processedMapClock = player.currentMapClock
        player.advanceActiveCoroutine()
    }
}

private fun runeManager(): MagicRuneManager {
    val fakes = FakeRuneRepository(); val combos = ComboRuneRepository(); val compact = CompactRuneRepository()
    val unlimited = UnlimitedRuneRepository(); val staves = StaffSubstituteRepository(); val subs = RuneSubstituteRepository()
    val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
    listOf(FakeRuneScript(fakes), ComboRuneScript(combos), CompactRuneScript(compact),
        UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves), RuneSubstituteScript(subs))
        .forEach { with(it) { context.startup() } }
    return MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
}
