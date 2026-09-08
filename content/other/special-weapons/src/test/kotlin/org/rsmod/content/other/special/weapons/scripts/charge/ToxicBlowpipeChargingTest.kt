package org.rsmod.content.other.special.weapons.scripts.charge

import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.ranged.BlowpipeAmmo
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("server-cache")
class ToxicBlowpipeChargingTest {
    @Test
    fun `every toxic variant registers scale and dart item interactions`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath(), Path::getParent)
                .first { Files.isDirectory(it.resolve(".data")) }
        GameValProvider.load("$root${File.separator}")
        val eventBus = EventBus()
        val context = ScriptContext(eventBus, CheatCommandMap(), EngineQueueCache())
        val script = ToxicBlowpipeCharging()
        with(script) { with(context) { startup() } }

        val scale = "obj.snakeboss_scale".asRSCM(RSCMType.OBJ)
        for (blowpipe in BlowpipeAmmo.toxicBlowpipeAliases()) {
            val blowpipeId = blowpipe.asRSCM(RSCMType.OBJ)
            val scaleKey = EventBus.composeLongKey(scale, blowpipeId)
            assertTrue(
                eventBus.contains(HeldUEvents.Type::class.java, scaleKey),
                "Missing scale interaction for $blowpipe",
            )

            for (dart in BlowpipeAmmo.toxicDartAliases()) {
                val dartKey = EventBus.composeLongKey(dart.asRSCM(RSCMType.OBJ), blowpipeId)
                assertTrue(
                    eventBus.contains(HeldUEvents.Type::class.java, dartKey),
                    "Missing $dart interaction for $blowpipe",
                )
            }
        }
    }
}
