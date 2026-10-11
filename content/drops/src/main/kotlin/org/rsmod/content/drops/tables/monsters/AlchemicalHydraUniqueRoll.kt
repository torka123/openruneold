package org.rsmod.content.drops.tables.monsters

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.RollableHooks
import dtx.rs.RSTable
import kotlin.random.Random
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.game.entity.Player

internal fun interface HydraRewardRandom {
    fun nextInt(bound: Int): Int
}

internal object AlchemicalHydraUniqueEngine {
    val ringParts = listOf("obj.hydra_eye", "obj.hydra_fang", "obj.hydra_heart")

    fun roll(quantities: Map<String, Int>, random: HydraRewardRandom): DropRollItem? {
        if (random.checked(2_000) == 0) {
            return DropRollItem("obj.dragon_thrownaxe", 500 + random.checked(501))
        }
        if (random.checked(2_000) == 0) {
            return DropRollItem("obj.dragon_knife", 500 + random.checked(501))
        }
        if (random.checked(1_000) == 0) return DropRollItem("obj.hydra_claw", 1)
        if (random.checked(512) == 0) return DropRollItem("obj.hydra_tail", 1)
        if (random.checked(512) == 0) return DropRollItem("obj.hydra_leather", 1)
        if (random.checked(180) == 0) {
            return DropRollItem(ringParts.minBy { quantities[it] ?: 0 }, 1)
        }
        return null
    }

    private fun HydraRewardRandom.checked(bound: Int): Int {
        require(bound > 0)
        return nextInt(bound).also { require(it in 0 until bound) }
    }
}

internal object AlchemicalHydraUniqueRoll :
    RSTable<Player, DropRollItem>,
    RollableHooks<Player, DropRollItem> by RollableHooks.Default() {
    override val tableIdentifier: String = "Alchemical Hydra sequential unique pre-roll"
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> = emptyList()

    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> {
        if (otherArgs[KillRollContext.npc] == null) return RollResult.Nothing()
        val quantities =
            AlchemicalHydraUniqueEngine.ringParts.associateWith { obj ->
                val objId = obj.asRSCM(RSCMType.OBJ)
                target.collectionTransmit.objs.firstOrNull { it?.id == objId }?.count ?: 0
            }
        val drop =
            AlchemicalHydraUniqueEngine.roll(
                quantities = quantities,
                random = HydraRewardRandom(Random::nextInt),
            ) ?: return RollResult.Nothing()
        return RollResult.Single(drop)
    }
}
