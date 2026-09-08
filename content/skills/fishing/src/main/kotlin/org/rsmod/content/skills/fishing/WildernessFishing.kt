package org.rsmod.content.skills.fishing

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Revision-240 Wilderness fishing spots, including the Resource Area dark-crab pools. */
public class WildernessFishing
@Inject
constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(SALTFISH_SPOT) { fish(it.npc, FishingMethod.SmallNet) }
        onOpNpc3(SALTFISH_SPOT) { fish(it.npc, FishingMethod.Bait) }
        onOpNpc1(LAVAFISH_SPOT) { fish(it.npc, FishingMethod.LavaEel) }
        onOpNpc1(RESOURCE_CRAB_SPOT) { fish(it.npc, FishingMethod.DarkCrab) }
        onOpNpc1(WILDERNESS_CRAB_SPOT) { fish(it.npc, FishingMethod.DarkCrab) }
        onPlayerQueueWithArgs<FishingTask>(FISHING_QUEUE) { processFishingTick(it.args) }
    }

    private suspend fun ProtectedAccess.fish(npc: Npc, method: FishingMethod) {
        arriveDelay()
        if (!canContinueFishing(npc, method)) return
        spam("You attempt to catch some fish.")
        anim(method.animation)
        weakQueue(FISHING_QUEUE, FISHING_INTERVAL_TICKS, FishingTask(npc, method))
    }

    private fun ProtectedAccess.processFishingTick(task: FishingTask) {
        val (npc, method) = task
        if (!canContinueFishing(npc, method)) return
        val catch = method.selectCatch(player.fishingLvl, random)
        if (catch != null && random.of(100) < catchChance(player.fishingLvl, catch.level)) {
            if (method.bait != null && invDel(inv, method.bait, 1).failure) {
                resetAnim()
                return
            }
            if (invAdd(inv, catch.item, 1).failure) {
                method.bait?.let { check(invAdd(inv, it, 1).success) }
                mes("Your inventory is too full to hold any more fish.")
                resetAnim()
                return
            }
            statAdvance("stat.fishing", catch.experience)
            spam("You catch ${catch.messageName}.")
        }
        if (!canContinueFishing(npc, method)) return
        anim(method.animation)
        weakQueue(FISHING_QUEUE, FISHING_INTERVAL_TICKS, task)
    }

    private fun ProtectedAccess.canContinueFishing(npc: Npc, method: FishingMethod): Boolean {
        if (player.coords.chebyshevDistance(npc.coords) > 2) {
            resetAnim()
            return false
        }
        if (canFish(method)) return true
        resetAnim()
        return false
    }

    private fun ProtectedAccess.canFish(method: FishingMethod): Boolean {
        if (player.fishingLvl < method.minimumLevel) {
            mes("You need a Fishing level of ${method.minimumLevel} to fish here.")
            return false
        }
        if (!inv.contains(method.tool)) {
            mes("You need ${method.toolDescription} to fish here.")
            return false
        }
        if (method.bait != null && !inv.contains(method.bait)) {
            mes("You need ${method.baitDescription} to fish here.")
            return false
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more fish.")
            return false
        }
        return true
    }

    public companion object {
        public const val SALTFISH_SPOT: String = "npc.0_47_57_saltfish"
        public const val LAVAFISH_SPOT: String = "npc.0_47_59_lavafish"
        public const val RESOURCE_CRAB_SPOT: String = "npc.49_61_crabs"
        public const val WILDERNESS_CRAB_SPOT: String = "npc.52_59_crabs"
        public const val FISHING_INTERVAL_TICKS: Int = 3
        public const val FISHING_QUEUE: String = "queue.generic_queue10"

        public fun catchChance(level: Int, requirement: Int): Int =
            (15 + ((level - requirement).coerceAtLeast(0) * 2)).coerceAtMost(90)
    }
}

private data class FishingTask(val npc: Npc, val method: FishingMethod)

public enum class FishingMethod(
    public val minimumLevel: Int,
    public val tool: String,
    public val toolDescription: String,
    public val bait: String?,
    public val baitDescription: String,
    public val animation: Int,
) {
    SmallNet(1, "obj.small_fishing_net", "a small fishing net", null, "", 621),
    Bait(5, "obj.fishing_rod", "a fishing rod", "obj.fishing_bait", "fishing bait", 622),
    LavaEel(53, "obj.oily_fishing_rod", "an oily fishing rod", "obj.fishing_bait", "fishing bait", 622),
    DarkCrab(85, "obj.lobster_pot", "a lobster pot", "obj.wilderness_fishing_bait", "dark fishing bait", 619),
    ;

    public fun selectCatch(level: Int, random: GameRandom): WildernessCatch? =
        when (this) {
            SmallNet ->
                if (level >= ANCHOVIES.level && random.of(2) == 0) {
                    ANCHOVIES
                } else {
                    SHRIMP
                }
            Bait ->
                when {
                    level < SARDINE.level -> null
                    level >= HERRING.level && random.of(2) == 0 -> HERRING
                    else -> SARDINE
                }
            LavaEel -> LAVA_EEL
            DarkCrab -> DARK_CRAB
        }

    private companion object {
        val SHRIMP = WildernessCatch("obj.raw_shrimp", 1, 10.0, "some raw shrimps")
        val ANCHOVIES = WildernessCatch("obj.raw_anchovies", 15, 40.0, "some raw anchovies")
        val SARDINE = WildernessCatch("obj.raw_sardine", 5, 20.0, "a raw sardine")
        val HERRING = WildernessCatch("obj.raw_herring", 10, 30.0, "a raw herring")
        val LAVA_EEL = WildernessCatch("obj.raw_lava_eel", 53, 60.0, "a raw lava eel")
        val DARK_CRAB = WildernessCatch("obj.raw_dark_crab", 85, 130.0, "a raw dark crab")
    }
}

public data class WildernessCatch(
    public val item: String,
    public val level: Int,
    public val experience: Double,
    public val messageName: String,
)
