package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.rsmod.api.instances.events.InstancePlayerJoinEvent
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class InstanceScriptSourceTest {
    private val area =
        InstanceArea.copyRegions(
            listOf(9116),
            enterCoord = RegionLocal(0, 35, 156, 40, 38),
            exitCoord = CoordGrid(2280, 10016),
        )
    private val spec =
        InstanceSettings(maxPlayers = 2, destroyWhenEmpty = true).withArea(area, settingsRowId = 0)

    @Test
    fun `existing database constructor still registers settings and key from its row`() {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        val registry = BossInstanceRegistry()
        val script =
            object : InstanceScript(registry) {
                override fun settingsRow() = "dbrow.instance_scurrius"

                override fun area() = this@InstanceScriptSourceTest.area

                fun expected() = buildSpec()

                fun instanceKey() = key
            }
        with(script) { ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup() }
        assertEquals(script.expected(), registry.get(script.instanceKey()))
        assertTrue(assertNotNull(registry.get(script.instanceKey())).settingsRowId > 0)
    }

    @Test
    fun `source spec registers without database row and uses native keyed events`() {
        val registry = BossInstanceRegistry()
        val events = EventBus()
        val script = SourceScript(registry, spec)
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        assertEquals(spec, registry.get("source-test"))
        val player = Player()
        events.publish(InstancePlayerJoinEvent(player, "another-instance", InstanceId(1), 10))
        assertEquals(0, script.joins)
        events.publish(InstancePlayerJoinEvent(player, "source-test", InstanceId(1), 10))
        assertEquals(1, script.joins)
    }

    @Test
    fun `source overrides preserve every other supplied setting`() {
        val registry = BossInstanceRegistry()
        val script =
            object : InstanceScript(registry, "source-test", spec.copy(destroyWhenEmpty = false)) {
                override fun spawnOnFirstJoin() = true

                override fun destroyWhenEmpty() = true
            }
        with(script) { ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup() }
        assertEquals(spec.copy(spawnOnFirstJoin = true), registry.get("source-test"))
    }

    @Test
    fun `typed content state is session owned resettable and cannot revive destroyed session`() {
        val key = InstanceStateKey<MutableList<Int>>("mechanic")
        val first = session(1)
        val second = session(2)
        first.state(key) { mutableListOf() }.add(7)
        assertEquals<List<Int>?>(listOf(7), first.stateOrNull(key))
        assertNull(second.stateOrNull(key))
        assertNull(first.stateOrNull(InstanceStateKey<MutableList<Int>>("mechanic")))
        first.resetState()
        assertNull(first.stateOrNull(key))
        first.state(key) { mutableListOf(8) }
        first.markEnded()
        first.markEnded()
        assertTrue(first.isEnded)
        assertNull(first.stateOrNull(key))
        assertFailsWith<IllegalStateException> { first.state(key) { mutableListOf() } }
        assertFailsWith<IllegalStateException> { first.resetState() }
        assertFalse(second.isEnded)
    }

    private fun session(id: Long): InstanceSession {
        val placement =
            (InstanceAreaResolver().resolve(area) as InstanceAreaResolver.Result.Ready).placement
        return InstanceSession(
            InstanceId(id),
            mutableSetOf(),
            id,
            "source-test",
            spec,
            placement,
            InstanceAccess.Private,
        )
    }

    private class SourceScript(registry: BossInstanceRegistry, spec: InstanceSpec) :
        InstanceScript(registry, "source-test", spec) {
        var joins = 0

        override fun ScriptContext.configure() {
            onInstancePlayerJoin { joins++ }
        }
    }
}
