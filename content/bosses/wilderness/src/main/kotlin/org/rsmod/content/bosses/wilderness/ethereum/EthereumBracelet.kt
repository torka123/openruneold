package org.rsmod.content.bosses.wilderness.ethereum

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt
import org.rsmod.api.config.constants
import org.rsmod.api.death.PlayerDeathDropTransformHook
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntValidateHook
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.random.CoreRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.bosses.wilderness.REVENANT_COMBAT_DEFINITIONS
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.inv.InvObj

internal const val CHARGED_BRACELET = "obj.wild_cave_bracelet_charged"
internal const val UNCHARGED_BRACELET = "obj.wild_cave_bracelet_uncharged"
internal const val REVENANT_ETHER = "obj.wild_cave_shard"
internal const val ETHER_CHARGES_VAROBJ = "varobj.charges_16383"
internal const val MAX_ETHER_CHARGES = 16_000
internal const val CHARGED_BRACELET_ID = 21_816
internal const val UNCHARGED_BRACELET_ID = 21_817
internal const val REVENANT_ETHER_ID = 21_820

internal var Player.ethereumAbsorptionEnabled by boolVarp("varp.ethereum_absorption_enabled")

internal object EthereumBraceletMechanics {
    fun reduceDamage(damage: Int): Int = damage.coerceAtLeast(0) / 4

    fun absorbableEther(currentCharges: Int, dropped: Int): Int =
        min((MAX_ETHER_CHARGES - currentCharges).coerceAtLeast(0), dropped.coerceAtLeast(0))

    fun maximumGuaranteedEther(combatLevel: Int): Int =
        ((sqrt(combatLevel.toDouble()) + 1.0).toInt()) * 2
}

@Singleton
internal class EthereumBraceletService @Inject constructor(private val charges: ObjChargeManager) {
    fun charges(obj: InvObj?): Int = charges.getCharges(obj, ETHER_CHARGES_VAROBJ)

    fun isWearingCharged(player: Player): Boolean {
        val bracelet = player.worn[Wearpos.Hands.slot] ?: return false
        return bracelet.id == CHARGED_BRACELET_ID && charges(bracelet) > 0
    }

    fun consumeProtectionCharge(player: Player): ObjChargeManager.Uncharge.Success? {
        if (!isWearingCharged(player)) return null
        return charges.reduceWornCharges(player, Wearpos.Hands, ETHER_CHARGES_VAROBJ, 1)
            as? ObjChargeManager.Uncharge.Success
    }

    fun absorbDrop(player: Player, dropped: Int): Int {
        if (!player.ethereumAbsorptionEnabled) return 0
        val slot = Wearpos.Hands.slot
        val bracelet = player.worn[slot] ?: return 0
        if (bracelet.id != CHARGED_BRACELET_ID) return 0
        val add = EthereumBraceletMechanics.absorbableEther(charges(bracelet), dropped)
        if (add == 0) return 0
        val result =
            charges.addCharges(player.worn, slot, add, ETHER_CHARGES_VAROBJ, MAX_ETHER_CHARGES)
        return (result as? ObjChargeManager.Charge.Success)?.added ?: 0
    }
}

@Singleton
internal class EthereumBraceletHitModifier
@Inject
constructor(private val bracelet: EthereumBraceletService) : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        StandardPlayerHitModifier.run { modify(target) }
        val consumed = bracelet.consumeProtectionCharge(target) ?: return
        damage = EthereumBraceletMechanics.reduceDamage(damage)
        if (consumed.fullyUncharged) {
            target.mes("Your bracelet of ethereum has run out of ether.")
        }
    }
}

@Singleton
internal class EthereumBraceletHuntValidateHook
@Inject
constructor(private val bracelet: EthereumBraceletService) : NpcPlayerHuntValidateHook {
    private val revenantIds: Set<Int> by lazy {
        REVENANT_COMBAT_DEFINITIONS.mapTo(mutableSetOf()) { definition ->
            requireNotNull(ServerCacheManager.getNpc(definition.npcInternal.asRSCM(RSCMType.NPC)))
                .id
        }
    }

    override fun canHunt(npc: Npc, player: Player): Boolean =
        npc.id !in revenantIds || !bracelet.isWearingCharged(player)
}

@Singleton
internal class EthereumBraceletDeathTransformHook
@Inject
constructor(private val bracelet: EthereumBraceletService) : PlayerDeathDropTransformHook {
    override fun transform(player: Player, item: InvObj): List<InvObj>? {
        if (item.id != CHARGED_BRACELET_ID) return null
        val storedEther = bracelet.charges(item)
        return buildList {
            add(InvObj(UNCHARGED_BRACELET))
            if (storedEther > 0) add(InvObj(REVENANT_ETHER, storedEther))
        }
    }
}

@Singleton
internal class RevenantEtherKillHook
@Inject
constructor(
    private val bracelet: EthereumBraceletService,
    private val objRepo: ObjRepository,
    @CoreRandom private val random: GameRandom,
) : org.rsmod.api.death.NpcDeathKillHook {
    private val etherMaxByNpcId: Map<Int, Int> by lazy {
        REVENANT_COMBAT_DEFINITIONS.associate { definition ->
            val npc =
                requireNotNull(
                    ServerCacheManager.getNpc(definition.npcInternal.asRSCM(RSCMType.NPC))
                )
            npc.id to EthereumBraceletMechanics.maximumGuaranteedEther(definition.combatLevel)
        }
    }

    override fun onKill(context: org.rsmod.api.death.NpcDeathKillContext) {
        val maximum = etherMaxByNpcId[context.npc.id] ?: return
        val count = random.of(1..(maximum / 2)) * 2
        val absorbed = bracelet.absorbDrop(context.hero, count)
        val remainder = count - absorbed
        val ether = requireNotNull(ServerCacheManager.getItem(REVENANT_ETHER_ID))
        if (remainder > 0) {
            val duration = context.hero.lootDropDuration ?: constants.lootdrop_duration
            objRepo.add(ether, context.npc.coords, duration, context.hero, remainder)
        }
        ClientScripts.lootTrackerAddLoot(
            context.hero,
            context.npc.id,
            context.lootTrackerEventId,
            ether.id,
            count,
        )
    }
}
