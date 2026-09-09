package org.rsmod.content.other.pouches

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import kotlin.test.*
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryData
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.CastResult
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.death.*
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.obj.plugin.ObjTakePlugin
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.interact.*
import org.rsmod.api.player.hook.*
import org.rsmod.api.player.protect.*
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.content.areas.wilderness.interactions.*
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.other.pouches.lootingbag.*
import org.rsmod.content.other.pouches.runepouch.*
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class PouchesIntegrationTest {
    @Test
    fun `opening each pouch enables stored rune slots before the client draws the modal`() {
        for (pouch in RunePouches.ALL) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(pouch)
            val slots = assertNotNull(f.player.runePouchSlots())
            listOf("airrune", "waterrune", "earthrune", "firerune").take(slots).forEachIndexed {
                index,
                rune ->
                f.store("obj.$rune", 101 + index)
            }
            val contents = f.player.readRunePouch(slots)
            assertFalse(RunePouchClientSlots.enabled(1) { 0 }, "Reproduces the hidden-slot bug")
            f.messages.clear()

            f.held(1)

            val capacity = VarpSmall(261, slots)
            assertContains(f.messages, capacity, pouch)
            val openIndex = f.messages.indexOfFirst { it is IfOpenSub }
            assertTrue(
                openIndex > f.messages.indexOf(capacity),
                "Capacity must precede modal: $pouch",
            )
            for (slot in 1..4) {
                assertEquals(
                    slot <= slots,
                    RunePouchClientSlots.enabled(slot) {
                        f.player.vars.backing.getOrDefault(it, 0)
                    },
                    "$pouch slot $slot",
                )
            }
            assertContentEquals(contents.types, f.player.readRunePouch(slots).types)
            assertContentEquals(contents.counts, f.player.readRunePouch(slots).counts)
        }
    }

    @Test
    fun `bankside configure initializes the same pouch drawing capacity`() {
        for (pouch in RunePouches.ALL) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(pouch)
            f.store("obj.deathrune", 400)
            f.openBank()
            f.messages.clear()

            f.click("component.bankside:items", 0, 1)

            val capacity = VarpSmall(261, assertNotNull(f.player.runePouchSlots()))
            assertContains(f.messages, capacity, pouch)
            assertTrue(f.messages.indexOf(capacity) < f.messages.indexOfFirst { it is IfOpenSub })
            assertEquals(400, f.player.readRunePouch(4).counts[0])
        }
    }

    @Test
    fun `reopening pouch resets temporary UI state without changing stored runes`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(RunePouches.REGULAR)
        f.store("obj.airrune", 100)
        for (previous in listOf(0, 4, 3)) {
            VarPlayerIntMapSetter.set(f.player, "varp.if1", previous)
            f.messages.clear()
            f.held(1)
            assertContains(f.messages, VarpSmall(261, 3))
            assertEquals(100, f.player.readRunePouch(3).counts[0])
        }
        f.player.inv[0] = InvObj(RunePouches.DIVINE)
        f.messages.clear()
        f.held(1)
        assertContains(f.messages, VarpSmall(261, 4))
        f.click("component.rune_pouch:pouch", 0, 6)
        assertEquals(100, f.player.inv.count("obj.airrune"))
        assertTrue(f.player.readRunePouch(4).isEmpty())
        assertEquals(4, f.player.vars["varp.if1"])
    }

    @Test
    fun `single bag handler coexists with wilderness agility and retains paid rewards`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(LootingBags.CLOSED)
        f.held(1)
        assertEquals(LootingBags.OPEN.asRSCM(RSCMType.OBJ), f.player.inv[0]?.id)
        f.player.coords = CoordGrid(3004, 3936)
        VarPlayerIntMapSetter.set(f.player, WILDERNESS_AGILITY_PAID_VARP, 1)
        VarPlayerIntMapSetter.set(f.player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 1)
        val type = requireNotNull(ServerCacheManager.getObject(53224))
        val loc = BoundLocInfo(LocInfo(2, CoordGrid(3005, 3936), LocEntity(53224, 10, 0)), type)
        f.dispatch(LocCoordEvents.Op1(loc, loc, type))
        assertNull(f.player.activeCoroutine)
        assertEquals(1, f.player.inv.count("obj.wildy_agility_token"))
        assertEquals(1, f.player.vars[WILDERNESS_AGILITY_STREAK_VARP])
        assertEquals(0, f.player.vars[WILDERNESS_AGILITY_UNCLAIMED_VARP])
        assertEquals(2, f.player.lootingBag.occupiedSpace())
        val before = f.player.lootingBag.toList()
        f.dispatch(LocCoordEvents.Op1(loc, loc, type))
        assertEquals(before, f.player.lootingBag.toList())
        f.held(2)
        assertTrue(f.player.ui.containsModal("interface.wilderness_lootingbag"))
        f.held(1)
        assertEquals(LootingBags.CLOSED.asRSCM(RSCMType.OBJ), f.player.inv[0]?.id)
        assertEquals(before, f.player.lootingBag.toList())
    }

    @Test
    fun `bag definition permits protected modal actions without disabling bank protection globally`() {
        val f = PouchFixture()
        assertFalse(f.player.lootingBag.type.protect)
        assertEquals(28, f.player.lootingBag.size)
        assertEquals(InvScope.Perm, f.player.lootingBag.type.scope)
        assertTrue(f.player.invMap.getOrPut("inv.tradeoffer").type.protect)
    }

    @Test
    fun `delayed pickup revalidates closed bag and full inventory without deleting ground item`() {
        for (change in listOf("closed", "full", "left")) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(LootingBags.OPEN)
            for (i in 1..27) f.player.inv[i] = InvObj("obj.rune_scimitar")
            f.wilderness(true)
            val ground =
                Obj.fromOwner(
                    f.player,
                    f.player.coords.copy(x = f.player.x + 1),
                    InvObj("obj.airrune", 10),
                )
            f.dispatch(ObjDefaultEvents.Op3(ground))
            assertNotNull(f.player.activeCoroutine)
            when (change) {
                "closed" -> f.player.inv[0] = InvObj(LootingBags.CLOSED)
                "full" -> for (i in 0..27) f.player.lootingBag[i] = InvObj("obj.rune_scimitar")
                "left" -> f.wilderness(false)
            }
            f.player.currentMapClock++
            f.player.processedMapClock = f.player.currentMapClock
            f.player.advanceActiveCoroutine()
            assertNull(f.player.activeCoroutine)
            verify(f.repo, never()).del(ground)
            assertEquals(0, f.player.inv.count("obj.airrune"))
        }
    }

    @Test
    fun `quantity prompt cannot withdraw after pouch disappears`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(RunePouches.REGULAR)
        f.store("obj.airrune", 20)
        f.held(1)
        f.click("component.rune_pouch:pouch", 0, 5)
        assertNotNull(f.player.activeCoroutine)
        f.player.inv[0] = null
        f.player.resumeActiveCoroutine(org.rsmod.api.player.input.ResumePCountDialogInput(10))
        assertNull(f.player.activeCoroutine)
        assertEquals(20, f.player.readRunePouch(3).counts[0])
        assertEquals(0, f.player.inv.count("obj.airrune"))
    }

    @Test
    fun `all four pouches open and transfer through real revision 240 button packets`() {
        for (pouch in RunePouches.ALL) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(pouch)
            f.player.inv[1] = InvObj("obj.airrune", 17000)
            f.held(1)
            assertTrue(f.player.ui.containsModal("interface.rune_pouch"))
            f.click("component.rune_pouch:inventory", 1, 6)
            assertEquals(16000, f.player.readRunePouch(4).counts[0])
            assertEquals(1000, f.player.inv[1]?.count)
            f.click("component.rune_pouch:pouch", 0, 3)
            assertEquals(15995, f.player.readRunePouch(4).counts[0])
            assertEquals(1005, f.player.inv[1]?.count)
            f.click("component.rune_pouch:pouch", 0, 6)
            assertEquals(17000, f.player.inv[1]?.count)
            assertTrue(f.player.readRunePouch(4).isEmpty())
            assertNull(f.player.activeCoroutine)
        }
    }

    @Test
    fun `regular pouch refuses fourth type and divine accepts it through UI`() {
        for ((pouch, expected) in listOf(RunePouches.REGULAR to 3, RunePouches.DIVINE to 4)) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(pouch)
            f.held(1)
            listOf("airrune", "waterrune", "earthrune", "firerune").forEachIndexed { index, rune ->
                f.player.inv[index + 1] = InvObj("obj.$rune", 100)
                f.click("component.rune_pouch:inventory", index + 1, 6)
            }
            assertEquals(expected, f.player.readRunePouch(4).counts.count { it == 100 })
        }
    }

    @Test
    fun `full inventory cannot lose runes on withdraw or empty and missing pouch blocks stale clicks`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(RunePouches.REGULAR)
        for (i in 1..27) f.player.inv[i] = InvObj("obj.rune_scimitar")
        f.store("obj.airrune", 200)
        f.held(1)
        f.click("component.rune_pouch:pouch", 0, 6)
        f.held(4)
        assertEquals(200, f.player.readRunePouch(3).counts[0])
        f.held(1)
        f.player.inv[0] = null
        f.click("component.rune_pouch:pouch", 0, 6)
        assertEquals(200, f.player.readRunePouch(3).counts[0])
        assertFalse(f.player.ui.containsModal("interface.rune_pouch"))
    }

    @Test
    fun `bankside configure reuses current protected action and empty uses bank capacity`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(RunePouches.DIVINE)
        f.openBank()
        f.click("component.bankside:items", 0, 1)
        assertTrue(f.player.ui.containsModal("interface.rune_pouch"))
        f.store("obj.airrune", 600)
        f.openBank()
        f.click("component.bankside:items", 0, 9)
        assertEquals(600, f.player.invMap.getOrPut("inv.bank").count("obj.airrune"))
        assertTrue(f.player.readRunePouch(4).isEmpty())
        f.player.bankCapacity = 1
        f.store("obj.waterrune", 250)
        f.click("component.bankside:items", 0, 9)
        assertEquals(250, f.player.readRunePouch(4).counts[0])
        assertEquals(0, f.player.invMap.getOrPut("inv.bank").count("obj.waterrune"))
    }

    @Test
    fun `native rune manager consumes pouch-only mixed and combo rune requirements atomically`() {
        val f = PouchFixture()
        val runes = f.runeManager()
        val spell = mock(MagicSpell::class.java)
        `when`(spell.obj)
            .thenReturn(ServerCacheManager.getItem("obj.47_water_blast".asRSCM(RSCMType.OBJ)))
        fun requirements(vararg reqs: Pair<String, Int>) {
            `when`(spell.objReqs)
                .thenReturn(
                    reqs.map { (name, count) ->
                        MagicSpell.ObjRequirement(
                            requireNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))),
                            count,
                            null,
                        )
                    }
                )
        }
        f.player.inv[0] = InvObj(RunePouches.REGULAR)
        f.store("obj.airrune", 10)
        f.store("obj.mindrune", 10)
        requirements("obj.airrune" to 1, "obj.mindrune" to 1)
        assertIs<CastResult.Success>(runes.delReqs(f.player, spell))
        assertContentEquals(intArrayOf(9, 9, 0), f.player.readRunePouch(3).counts)
        f.player.inv[1] = InvObj("obj.airrune", 1)
        assertIs<CastResult.Success>(runes.delReqs(f.player, spell))
        assertNull(f.player.inv[1])
        assertContentEquals(intArrayOf(9, 8, 0), f.player.readRunePouch(3).counts)
        requirements("obj.airrune" to 1, "obj.deathrune" to 1)
        assertIs<CastResult.Failure>(runes.delReqs(f.player, spell))
        assertContentEquals(intArrayOf(9, 8, 0), f.player.readRunePouch(3).counts)
        f.player.writeRunePouch(RunePouchContents.empty(4))
        f.store("obj.mistrune", 5)
        requirements("obj.airrune" to 1, "obj.waterrune" to 1)
        assertIs<CastResult.Success>(runes.delReqs(f.player, spell))
        assertEquals(4, f.player.readRunePouch(3).counts[0])
        f.player.inv[0] = null
        assertIs<CastResult.Failure>(runes.delReqs(f.player, spell))
        assertEquals(4, f.player.readRunePouch(3).counts[0])
    }

    @Test
    fun `open looting bag receives full-inventory pickup once and survives persistence reload`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(LootingBags.OPEN)
        for (i in 1..27) f.player.inv[i] = InvObj("obj.rune_scimitar")
        f.wilderness(true)
        val ground = Obj.fromOwner(f.player, f.player.coords, InvObj("obj.deathrune", 80))
        `when`(f.repo.del(ground)).thenReturn(true, false)
        f.dispatch(ObjDefaultEvents.Op3(ground))
        f.dispatch(ObjDefaultEvents.Op3(ground))
        assertEquals(80, f.player.lootingBag[0]?.count)
        assertEquals(0, f.player.lootingBag[0]?.vars)
        val reloaded = PouchFixture()
        assertEquals(InvScope.Perm, f.player.lootingBag.type.scope)
        assertNull(f.player.attr[LOOTING_BAG_ATTR])
        CharacterInventoryApplier()
            .apply(
                reloaded.player,
                CharacterInventoryData(
                    listOf(
                        CharacterInventoryData.Inventory(
                            1,
                            LootingBags.INV,
                            LootingBags.INV,
                            mutableMapOf(0 to CharacterInventoryData.Obj("obj.deathrune", 80, 0)),
                        )
                    )
                ),
            )
        // An older mirror must never overwrite the permanent inventory loaded by the account
        // pipeline.
        reloaded.player.attr[LOOTING_BAG_ATTR] = mutableListOf(560, 9999)
        reloaded.player.restoreLootingBag()
        assertEquals(f.player.lootingBag[0], reloaded.player.lootingBag[0])
        assertNull(reloaded.player.attr[LOOTING_BAG_ATTR])
    }

    @Test
    fun `closed bag outside wilderness full bag and rejected items use normal inventory`() {
        for (mode in 0..3) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(if (mode == 0) LootingBags.CLOSED else LootingBags.OPEN)
            f.wilderness(mode != 1)
            if (mode == 2) for (i in 0..27) f.player.lootingBag[i] = InvObj("obj.rune_scimitar")
            val item = if (mode == 3) LootingBags.CLOSED else "obj.airrune"
            val ground = Obj.fromOwner(f.player, f.player.coords, InvObj(item))
            `when`(f.repo.del(ground)).thenReturn(true)
            f.dispatch(ObjDefaultEvents.Op3(ground))
            assertEquals(1, f.player.inv.count(item), "mode=$mode")
            assertEquals(0, f.player.lootingBag.count(item), "mode=$mode")
        }
    }

    @Test
    fun `both containers full leaves ground item registered`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(LootingBags.OPEN)
        for (i in 1..27) f.player.inv[i] = InvObj("obj.rune_scimitar")
        for (i in 0..27) f.player.lootingBag[i] = InvObj("obj.rune_scimitar")
        f.wilderness(true)
        val ground = Obj.fromOwner(f.player, f.player.coords, InvObj("obj.airrune", 50))
        f.dispatch(ObjDefaultEvents.Op3(ground))
        verify(f.repo, never()).del(ground)
        assertEquals(0, f.player.inv.count("obj.airrune"))
        assertEquals(0, f.player.lootingBag.count("obj.airrune"))
    }

    @Test
    fun `looting bag deposit and bank panel move through native transactions`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(LootingBags.CLOSED)
        f.player.inv[1] = InvObj("obj.deathrune", 400)
        f.held(3)
        assertFalse(f.player.ui.containsModal("interface.wilderness_lootingbag"))
        f.wilderness(true)
        f.held(3)
        f.click("component.wilderness_lootingbag:items", 1, 3)
        assertNull(f.player.inv[1])
        assertEquals(400, f.player.lootingBag.count("obj.deathrune"))
        f.openBank()
        f.click("component.bankside:items", 0, 9)
        f.click("component.bankside:lootingbag_bankall")
        assertTrue(f.player.lootingBag.isEmpty())
        assertEquals(400, f.player.invMap.getOrPut("inv.bank").count("obj.deathrune"))
        assertNull(f.player.attr[LOOTING_BAG_ATTR])
    }

    @Test
    fun `pouch varbits survive native persistent varp reload`() {
        val f = PouchFixture()
        f.player.inv[0] = InvObj(RunePouches.DIVINE)
        f.store("obj.airrune", 16000)
        f.store("obj.deathrune", 500)
        val saved = f.player.vars.backing.toMap()
        val restored = PouchFixture()
        restored.player.vars.backing.putAll(saved)
        assertContentEquals(
            f.player.readRunePouch(4).counts,
            restored.player.readRunePouch(4).counts,
        )
        assertContentEquals(f.player.readRunePouch(4).types, restored.player.readRunePouch(4).types)
    }

    @Test
    fun `death spills bag outside instance retains PVM runes and clears PVP runes once`() {
        for (pvp in listOf(false, true)) {
            val f = PouchFixture()
            f.player.inv[0] = InvObj(RunePouches.REGULAR)
            f.player.inv[1] = InvObj(LootingBags.CLOSED)
            f.player.lootingBag[0] = InvObj("obj.airrune", 900)
            f.store("obj.deathrune", 100)
            val outside = CoordGrid(3000, 3000)
            val emitted = mutableListOf<Obj>()
            val repo =
                mock(ObjRepository::class.java) { call ->
                    if (call.method.name == "add" && call.arguments.firstOrNull() is Obj) {
                        emitted += call.arguments[0] as Obj
                        true
                    } else RETURNS_DEFAULTS.answer(call)
                }
            val hook =
                PouchDeathHook(
                    repo,
                    MapClock(),
                    GroundItemDropResolver(emptySet()),
                    setOf(PlayerDeathDropLocationHook { outside }),
                )
            val context =
                PlayerDeathContext(
                    f.player,
                    f.player.coords,
                    false,
                    0,
                    false,
                    true,
                    false,
                    false,
                    pvp,
                    0,
                    null,
                )
            val handling =
                PlayerDeathHandling(3, f.player, 100, 50, false, UntradeableHandling.DROP)
            hook.beforeDrops(context, handling)
            hook.beforeDrops(context, handling)
            assertTrue(f.player.lootingBag.isEmpty())
            assertEquals(if (pvp) 2 else 1, emitted.size)
            assertTrue(emitted.all { it.coords == outside && it.isOriginalOwner(f.player) })
            assertEquals(if (pvp) 0 else 100, f.player.readRunePouch(3).counts[0])
            assertEquals(!pvp, f.player.hasLootingBag())
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}

private class PouchFixture {
    val messages = mutableListOf<Any>()
    val events = EventBus()
    val repo = mock(ObjRepository::class.java)
    val player =
        Player().apply {
            uuid = 400L
            observerUUID = 400L
            currentMapClock = 100
            processedMapClock = 100
            coords = CoordGrid(3100, 3600)
            ui.topLevel = UserInterface(161)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            bankCapacity = 800
        }
    val context = mock(ProtectedAccessContext::class.java)
    private val launcher: ProtectedAccessLauncher
    private val input: If3ButtonHandler

    init {
        player.client =
            object : Client<Any, Any> {
                override fun write(message: Any) {
                    messages += message
                }

                override fun close() = error("Player disconnected")

                override fun read(player: Player) = Unit

                override fun flush() = Unit

                override fun flushHighPriority() = Unit

                override fun unregister(service: Any, player: Player) = Unit
            }
        `when`(context.eventBus).thenReturn(events)
        `when`(context.random).thenReturn(mock(GameRandom::class.java))
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        launcher = ProtectedAccessLauncher(factory)
        input = If3ButtonHandler(events, launcher)
        val bank =
            BankInvScript(
                events,
                mock(WornBonuses::class.java),
                mock(SpecialAttackRegistry::class.java),
                mock(WeaponSpeeds::class.java),
            )
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(
                WildernessAgilityCourse(mock(GameRandom::class.java), repo),
                bank,
                RunePouchScript(repo, bank),
                LootingBagScript(repo, bank, mock(MarketPrices::class.java), events),
                ObjTakePlugin(
                    repo,
                    PlayerObjTakeValidator(emptySet()),
                    PlayerObjTakeRedirector(setOf(LootingBagTakeHook())),
                ),
            )
            .forEach { with(it) { scripts.startup() } }
    }

    fun dispatch(event: SuspendEvent<ProtectedAccess>) {
        assertTrue(launcher.launch(player) { events.publish(this, event) })
    }

    fun held(op: Int) {
        player.ifClose(events)
        val obj = requireNotNull(player.inv[0])
        val type = requireNotNull(ServerCacheManager.getItem(obj.id))
        dispatch(
            when (op) {
                1 -> HeldObjEvents.Op1(0, obj, type, player.inv)
                2 -> HeldObjEvents.Op2(0, obj, type, player.inv)
                3 -> HeldObjEvents.Op3(0, obj, type, player.inv)
                4 -> HeldObjEvents.Op4(0, obj, type, player.inv)
                else -> error("Unsupported op")
            }
        )
    }

    fun click(component: String, slot: Int = -1, op: Int = 1) {
        input.handle(
            player,
            If3Button(CombinedId(component.asRSCM(RSCMType.COMPONENT)), slot, -1, op),
        )
    }

    fun wilderness(enabled: Boolean) =
        VarPlayerIntMapSetter.set(player, "varbit.inside_wilderness", if (enabled) 1 else 0)

    fun store(name: String, count: Int) {
        val contents = player.readRunePouch(player.runePouchSlots() ?: 4)
        contents.add(
            requireNotNull(RunePouchRunes.byObj(name.asRSCM(RSCMType.OBJ))).compactId,
            count,
        )
        player.writeRunePouch(contents)
    }

    fun openBank() {
        player.ifClose(events)
        launcher.launch(player) { ifOpenMainSidePair("interface.bankmain", "interface.bankside") }
        player.ifSetEvents("component.bankside:items", player.inv.indices, IfEvent.Op1, IfEvent.Op9)
    }

    fun runeManager(): MagicRuneManager {
        val fakes = FakeRuneRepository()
        val combos = ComboRuneRepository()
        val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository()
        val staves = StaffSubstituteRepository()
        val subs = RuneSubstituteRepository()
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(
                FakeRuneScript(fakes),
                ComboRuneScript(combos),
                CompactRuneScript(compact),
                UnlimitedRuneScript(unlimited),
                StaffSubstituteScript(staves),
                RuneSubstituteScript(subs),
            )
            .forEach { with(it) { scripts.startup() } }
        return MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
    }
}
