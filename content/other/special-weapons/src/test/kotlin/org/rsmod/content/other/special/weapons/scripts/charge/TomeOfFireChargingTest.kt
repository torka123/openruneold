package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.TomeOfFireState
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("server-cache")
class TomeOfFireChargingTest {
    @Test
    fun `burnt and searing pages register against both tome variants`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath(), Path::getParent).first {
                Files.isDirectory(it.resolve(".data"))
            }
        GameValProvider.load("$root${File.separator}")
        val eventBus = EventBus()
        val context = ScriptContext(eventBus, CheatCommandMap(), EngineQueueCache())
        val script = TomeOfFireCharging()
        with(script) { with(context) { startup() } }

        for (page in TomeOfFireState.PageType.entries) {
            for (tome in listOf(TomeOfFireState.EMPTY_ID, TomeOfFireState.CHARGED_ID)) {
                val key = EventBus.composeLongKey(page.itemId, tome)
                assertTrue(
                    eventBus.contains(HeldUEvents.Type::class.java, key),
                    "Missing ${page.displayName} interaction for tome $tome",
                )
            }
        }
    }
}
