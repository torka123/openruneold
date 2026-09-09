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
import org.rsmod.api.combat.commons.magic.HomeTeleportCooldown
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.config.refs.params
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.net.rsprot.handlers.If1ButtonHandler
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
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
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class ArceuusTeleportRuntimeTest {
    @Test
    fun `every Arceuus teleport executes its real cast and consumes required runes`() {
        for (teleport in ArceuusTeleport.entries) {
            val f = TeleportFixture()
            val spells =
                Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
            val spell =
                assertNotNull(
                    spells.getObjSpell(
                        assertNotNull(
                            ServerCacheManager.getItem(teleport.spellObj.asRSCM(RSCMType.OBJ))
                        )
                    )
                )
            val runes = runeManager()
            val realm = mock(Realm::class.java)
            val config = mock(RealmConfig::class.java)
            `when`(realm.config).thenReturn(config)
            `when`(config.respawnCoord).thenReturn(CoordGrid(3100, 3500, 0))
            val destination =
                if (teleport == ArceuusTeleport.Respawn) config.respawnCoord
                else
                    teleport.destination
                        ?: assertNotNull(spell.obj.paramOrNull(params.spell_telecoord))
            f.collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 3)
            spell.objReqs.forEachIndexed { index, req ->
                f.player.inv[index] = InvObj(req.obj, req.count)
            }
            val script = ArceuusSpellTeleportScript(spells, runes, f.validator, f.areas, realm)
            assertTrue(
                withProtectedAccess(f.player, f.context) { with(script) { cast(spell, teleport) } }
            )
            val home = teleport == ArceuusTeleport.Home
            assertEquals(if (home) 714 else 6575, f.player.pendingSequence.id, spell.name)
            assertEquals(
                if (home) 111 else 1296,
                EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id,
                spell.name,
            )
            f.tick(2)
            assertEquals(f.origin, f.player.coords, spell.name)
            f.tick()
            if (home) f.tick()
            assertEquals(destination, f.player.coords, spell.name)
            assertEquals(
                if (home) 715 else EntitySeq.ZERO.id,
                f.player.pendingSequence.id,
                spell.name,
            )
            assertEquals(65535, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id, spell.name)
            spell.objReqs.forEach {
                assertEquals(0, f.player.inv.count(it.obj.internalName), spell.name)
            }
            if (teleport == ArceuusTeleport.Home) {
                assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
                assertEquals(0, f.player.vars[HomeTeleport.TIMER])
                f.tick(3)
                f.player.coords = f.origin
                assertTrue(
                    withProtectedAccess(f.player, f.context) {
                        with(script) { cast(spell, teleport) }
                    }
                )
                f.tick(HomeTeleport.CAST_TICKS)
                assertEquals(destination, f.player.coords)
                assertEquals(0, f.player.vars[HomeTeleport.TIMER])
            }
        }
    }

    @Test
    fun `repeated home packets finish one standard cast for every supported book and button format`() {
        for (book in
            listOf(Spellbook.Standard, Spellbook.Ancients, Spellbook.Arceuus)) for (legacy in
            listOf(true, false)) {
            val f = TeleportFixture()
            val click = f.homeButton(book, legacy)
            click()
            val active = assertNotNull(f.player.activeCoroutine)
            val ticks = HomeTeleport.CAST_TICKS
            repeat(ticks) { tick ->
                val sequence = f.player.pendingSequence
                repeat(10) {
                    click()
                    assertSame(
                        active,
                        f.player.activeCoroutine,
                        "Repeated packet replaced the cast at tick $tick",
                    )
                    assertEquals(sequence, f.player.pendingSequence)
                }
                f.tick()
                if (tick < ticks - 1) assertEquals(f.origin, f.player.coords)
            }
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
            assertEquals(715, f.player.pendingSequence.id)
            assertEquals(65535, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id)
            assertEquals(Component.NULL, f.player.ui.ignoredOverlayButton)
            assertNull(f.player.activeCoroutine)
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
            f.player.coords = f.origin
            click()
            f.tick(HomeTeleport.CAST_TICKS)
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        }
    }

    @Test
    fun `cancelled home casts clear visuals and allow a fresh click`() {
        for (arceuus in listOf(true, false)) {
            val f = TeleportFixture()
            val click =
                f.homeButton(if (arceuus) Spellbook.Arceuus else Spellbook.Ancients, legacy = false)
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
            f.tick(HomeTeleport.CAST_TICKS)
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        }
    }

    @Test
    fun `home stops a previous walking route and reaches Edgeville at tick four`() {
        val f = TeleportFixture()
        val click = f.homeButton(Spellbook.Standard, legacy = false)
        f.player.routeDestination.add(f.origin.translate(6, 0))
        assertTrue(f.player.routeDestination.isNotEmpty())
        click()
        assertTrue(f.player.routeDestination.isEmpty())
        f.tick(3)
        assertEquals(f.origin, f.player.coords)
        f.tick()
        assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
    }

    @Test
    fun `home replaces a pending standard teleport without a second queued arrival`() {
        val f = TeleportFixture()
        val varrock =
            f.homeButton(
                Spellbook.Standard,
                legacy = false,
                spellObjOverride = "obj.25_varrock_teleport",
            )
        varrock()
        assertTrue(SpellTeleportScript.TeleportQueue in f.player.queueList)
        f.tick()
        val home = f.homeButton(Spellbook.Standard, legacy = false)
        home()
        assertFalse(SpellTeleportScript.TeleportQueue in f.player.queueList)
        f.tick(HomeTeleport.CAST_TICKS)
        assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        f.tick(8)
        assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
    }

    @Test
    fun `home buttons allow wilderness nineteen and reject twenty or higher`() {
        for (book in
            listOf(Spellbook.Standard, Spellbook.Ancients, Spellbook.Arceuus)) for (level in
            listOf(19, 20, 21)) {
            val f = TeleportFixture()
            val click = f.homeButton(book, legacy = false)
            val origin = CoordGrid(3100, 3520 + (level - 1) * 8, 0)
            f.player.coords = origin
            f.collision.allocateIfAbsent(origin.x, origin.z, origin.level)
            `when`(f.areas.inArea("area.wilderness", origin)).thenReturn(true)
            click()
            f.tick(HomeTeleport.CAST_TICKS)
            assertEquals(
                if (level < 20) CoordGrid(3087, 3495, 0) else origin,
                f.player.coords,
                "$book level $level",
            )
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
        }
    }

    @Test
    fun `home uses the native combat boundary without adding the resting lookback`() {
        for (book in
            listOf(Spellbook.Standard, Spellbook.Ancients, Spellbook.Arceuus)) for (combatVar in
            listOf("varp.lastcombat", "varp.lastcombat_pvp")) {
            val f = TeleportFixture()
            val click = f.homeButton(book, legacy = false)
            VarPlayerIntMapSetter.set(f.player, combatVar, f.player.currentMapClock - 8)
            click()
            assertNull(f.player.activeCoroutine)
            assertEquals(f.origin, f.player.coords)
            f.tick()
            // A leftover action delay is not a Home cooldown once native combat has expired.
            f.player.actionDelay = f.player.currentMapClock + 20
            click()
            f.tick(HomeTeleport.CAST_TICKS)
            assertEquals(CoordGrid(3087, 3495, 0), f.player.coords, "$book $combatVar")
        }
    }

    @Test
    fun `home buttons retain native teleport validation before and during departure`() {
        for (book in
            listOf(
                Spellbook.Standard,
                Spellbook.Ancients,
                Spellbook.Arceuus,
            )) for (denyImmediately in listOf(true, false)) {
            var denied = denyImmediately
            val hook = PlayerTeleportValidateHook { _, type, _ ->
                assertEquals(TeleportType.Standard, type)
                if (denied) "A native teleport restriction prevents this." else null
            }
            val f = TeleportFixture(setOf(hook))
            val click = f.homeButton(book, legacy = false)
            click()
            if (denyImmediately) assertNull(f.player.activeCoroutine)
            else {
                f.tick(3)
                denied = true
            }
            f.tick(HomeTeleport.CAST_TICKS)
            assertEquals(f.origin, f.player.coords)
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
        }
    }

    @Test
    fun `every book ignores saved home recharge and clears its client timer`() {
        val f = TeleportFixture()
        val click = f.homeButton(Spellbook.Arceuus, legacy = false)
        val savedMinute = (System.currentTimeMillis() / 60_000L).toInt()
        VarPlayerIntMapSetter.set(f.player, HomeTeleport.TIMER, savedMinute)
        f.messages.clear()
        HomeTeleportCooldown.syncClient(f.player)
        assertEquals(VarpSmall(892, 0), f.messages.last())
        click()
        f.tick(HomeTeleport.CAST_TICKS)
        assertEquals(CoordGrid(3087, 3495, 0), f.player.coords)
        assertEquals(savedMinute, f.player.vars[HomeTeleport.TIMER])
        assertFalse(f.messages.any { it == VarpLarge(892, savedMinute) })

        val manager = MagicSpellbookManager(mock(AutocastWeapons::class.java))
        for (book in Spellbook.entries) {
            manager.setSpellbook(f.player, book)
            assertEquals(VarpSmall(892, 0), f.messages.last())
        }
        assertEquals(savedMinute, f.player.vars[HomeTeleport.TIMER])
    }

    @Test
    fun `home uses standard visuals and four tick departure without a persistent cooldown`() {
        val f = TeleportFixture()
        f.home()
        assertEquals(714, f.player.pendingSequence.id)
        assertEquals(111, EntitySpotanim(f.player.pendingSpotanims.getLong(0)).id)
        f.tick(3)
        assertEquals(f.origin, f.player.coords)
        f.tick()
        assertEquals(f.destination, f.player.coords)
        assertEquals(715, f.player.pendingSequence.id)
        assertEquals(0, f.player.vars[HomeTeleport.TIMER])
        val loaded = Player()
        loaded.vars.backing.putAll(f.player.vars.backing)
        assertEquals(0, loaded.vars[HomeTeleport.TIMER])
        f.player.coords = f.origin
        f.home()
        f.tick(HomeTeleport.CAST_TICKS)
        assertEquals(f.destination, f.player.coords)
    }

    @Test
    fun `movement combat and cancellation interrupt home without consuming recharge`() {
        for (interruption in 0..2) {
            val f = TeleportFixture()
            f.home()
            f.tick()
            when (interruption) {
                0 -> f.player.coords = f.origin.translate(1, 0)
                1 ->
                    VarPlayerIntMapSetter.set(f.player, "varp.lastcombat", f.player.currentMapClock)
                2 -> f.player.activeCoroutine?.cancel()
            }
            f.tick(22)
            assertNotEquals(f.destination, f.player.coords)
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
        }
    }

    @Test
    fun `teleport denial at completion leaves home recharge available`() {
        val f = TeleportFixture()
        var allowed = true
        assertTrue(
            withProtectedAccess(f.player, f.context) {
                castHome(f.destination, { allowed }, f.areas)
            }
        )
        f.tick(3)
        allowed = false
        f.tick()
        assertEquals(f.origin, f.player.coords)
        assertEquals(0, f.player.vars[HomeTeleport.TIMER])
    }

    @Test
    fun `supported minigame destinations execute and persist their separate 20 minute recharge`() {
        for (destination in MinigameTeleport.DESTINATIONS) {
            val f = TeleportFixture()
            f.collision.allocateIfAbsent(
                destination.coords.x,
                destination.coords.z,
                destination.coords.level,
            )
            val spell = MinigameTeleport(f.validator, f.areas)
            assertTrue(
                withProtectedAccess(f.player, f.context) {
                    with(spell) { cast(destination) { 1_000_000 } }
                }
            )
            f.tick(21)
            assertEquals(f.origin, f.player.coords)
            assertFalse(f.player.isDelayed)
            f.tick()
            assertEquals(destination.coords, f.player.coords)
            assertEquals(20, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
            assertEquals(0, f.player.vars[HomeTeleport.TIMER])
            val loaded = Player().apply { vars.backing.putAll(f.player.vars.backing) }
            assertEquals(1, MinigameTeleport.remainingMinutes(loaded, 1_000_019))
            assertEquals(0, MinigameTeleport.remainingMinutes(loaded, 1_000_020))
            f.player.coords = f.origin
            assertTrue(
                withProtectedAccess(f.player, f.context) {
                    with(spell) { cast(destination) { 1_000_001 } }
                }
            )
            f.tick(22)
            assertEquals(f.origin, f.player.coords)
        }
    }

    @Test
    fun `minigame interruption and invalid destination do not consume recharge`() {
        for (interrupt in listOf(true, false)) {
            val f = TeleportFixture()
            val destination = MinigameTeleport.DESTINATIONS.first()
            val spell = MinigameTeleport(f.validator, f.areas)
            assertTrue(
                withProtectedAccess(f.player, f.context) {
                    with(spell) { cast(destination) { 1_000_000 } }
                }
            )
            f.tick(3)
            if (interrupt) f.player.coords = f.origin.translate(1, 0)
            f.tick(22)
            assertNotEquals(destination.coords, f.player.coords)
            assertEquals(0, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
        }
    }

    @Test
    fun `minigame chooser resumes a real selection and begins the interruptible cast`() {
        val f = TeleportFixture()
        val destination = MinigameTeleport.DESTINATIONS.last()
        f.collision.allocateIfAbsent(
            destination.coords.x,
            destination.coords.z,
            destination.coords.level,
        )
        val spell = MinigameTeleport(f.validator, f.areas)
        assertTrue(withProtectedAccess(f.player, f.context) { with(spell) { openMenu() } })
        assertNotNull(f.player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal"))
        assertNotNull(f.player.activeCoroutine)
            .resumeWith(ResumePauseButtonInput("component.menu:lj_layer1", 1))
        f.tick()
        f.tick(22)
        assertEquals(destination.coords, f.player.coords)
        assertNull(f.player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal"))
    }

    @Test
    fun `resting teleport requires ten seconds out of combat`() {
        for (combatVar in listOf("varp.lastcombat", "varp.lastcombat_pvp")) {
            val f = TeleportFixture()
            VarPlayerIntMapSetter.set(f.player, combatVar, 84)
            f.resting()
            assertNull(f.player.activeCoroutine)
            assertEquals(f.origin, f.player.coords)
            f.tick()
            f.resting()
            assertNotNull(f.player.activeCoroutine)
            f.tick(22)
            assertEquals(f.destination, f.player.coords)
        }
    }

    @Test
    fun `Pest Control teleport enforces its combat requirement before channeling`() {
        val f = TeleportFixture()
        val destination = MinigameTeleport.DESTINATIONS.first()
        f.collision.allocateIfAbsent(
            destination.coords.x,
            destination.coords.z,
            destination.coords.level,
        )
        val spell = MinigameTeleport(f.validator, f.areas)
        f.player.appearance.combatLevel = 39
        assertTrue(
            withProtectedAccess(f.player, f.context) {
                with(spell) { cast(destination) { 1_000_000 } }
            }
        )
        assertNull(f.player.activeCoroutine)
        assertEquals(0, MinigameTeleport.remainingMinutes(f.player, 1_000_000))
        f.player.appearance.combatLevel = 40
        assertTrue(
            withProtectedAccess(f.player, f.context) {
                with(spell) { cast(destination) { 1_000_000 } }
            }
        )
        f.tick(22)
        assertEquals(destination.coords, f.player.coords)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}

@OptIn(InternalApi::class)
private class TeleportFixture(hooks: Set<PlayerTeleportValidateHook> = emptySet()) {
    val messages = mutableListOf<Any>()
    val origin = CoordGrid(3200, 3200, 0)
    val destination = CoordGrid(1699, 3879, 0)
    val collision = CollisionFlagMap()
    val validator = PlayerTeleportValidator(hooks)
    val areas = mock(AreaChecker::class.java)
    val context = mock(ProtectedAccessContext::class.java)
    private var queues: org.rsmod.api.game.process.player.PlayerQueueProcessor? = null
    private val movement =
        org.rsmod.api.game.process.player.PlayerMovementProcessor(
            collision,
            org.rsmod.api.route.RouteFactory(collision),
            org.rsmod.api.route.StepFactory(collision),
            EventBus(),
        )
    val player =
        Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
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
        player.client =
            object : Client<Any, Any> {
                override fun write(message: Any) {
                    messages.add(message)
                }

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
        assertTrue(withProtectedAccess(player, context) { castHome(destination, { true }, areas) })
    }

    fun resting() {
        assertTrue(
            withProtectedAccess(player, context) {
                with(HomeTeleport) { castResting(destination, { true }) }
            }
        )
    }

    fun homeButton(book: Spellbook, legacy: Boolean, spellObjOverride: String? = null): () -> Unit {
        val spells =
            Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
        val obj =
            spellObjOverride
                ?: when (book) {
                    Spellbook.Standard -> "obj.48_home_teleport"
                    Spellbook.Arceuus -> "obj.deadman_level99_lamp"
                    else -> "obj.01_zaros_home_tele"
                }
        val spell =
            assertNotNull(
                spells.getObjSpell(
                    assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
                )
            )
        val events = EventBus()
        if (spellObjOverride != null) {
            spell.objReqs.forEachIndexed { slot, req ->
                player.inv[slot] = InvObj(req.obj, req.count)
            }
        }
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        if (book == Spellbook.Standard) {
            with(
                SpellTeleportScript(
                    spells,
                    runeManager(),
                    validator,
                    areas,
                    mock(org.rsmod.content.skills.construction.ConstructionHouseService::class.java),
                )
            ) {
                scriptContext.startup()
            }
        } else if (book == Spellbook.Arceuus) {
            with(
                ArceuusSpellTeleportScript(
                    spells,
                    runeManager(),
                    validator,
                    areas,
                    mock(Realm::class.java),
                )
            ) {
                scriptContext.startup()
            }
        } else {
            with(AncientSpellTeleportScript(spells, runeManager(), validator, areas)) {
                scriptContext.startup()
            }
        }
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", book.varValue)
        collision.allocateIfAbsent(3087, 3495, 0)
        player.ui.topLevel = UserInterface(spell.component.packed ushr 16)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        val launcher = ProtectedAccessLauncher(factory)
        queues = org.rsmod.api.game.process.player.PlayerQueueProcessor(events, launcher)
        `when`(context.eventBus).thenReturn(events)
        val if1 = If1ButtonHandler(events, launcher)
        val if3 = If3ButtonHandler(events, launcher)
        val combined = CombinedId(spell.component.packed)
        return {
            if (legacy) if1.handle(player, If1Button(combined))
            else if3.handle(player, If3Button(combined, -1, -1, 1))
        }
    }

    fun tick(count: Int = 1) =
        repeat(count) {
            // Mirror PlayerPostTickProcess: pending visuals are per-cycle output, not active anims.
            player.pendingSequence = EntitySeq.NULL
            player.pendingSpotanims.clear()
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.advanceActiveCoroutine()
            queues?.process(player)
            movement.process(player)
        }
}

private fun runeManager(): MagicRuneManager {
    val fakes = FakeRuneRepository()
    val combos = ComboRuneRepository()
    val compact = CompactRuneRepository()
    val unlimited = UnlimitedRuneRepository()
    val staves = StaffSubstituteRepository()
    val subs = RuneSubstituteRepository()
    val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
    listOf(
            FakeRuneScript(fakes),
            ComboRuneScript(combos),
            CompactRuneScript(compact),
            UnlimitedRuneScript(unlimited),
            StaffSubstituteScript(staves),
            RuneSubstituteScript(subs),
        )
        .forEach { with(it) { context.startup() } }
    return MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
}
