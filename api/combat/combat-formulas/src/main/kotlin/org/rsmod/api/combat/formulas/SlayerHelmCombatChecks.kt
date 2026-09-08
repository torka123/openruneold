package org.rsmod.api.combat.formulas

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getOrNull

/**
 * Slayer helmet / black mask task boosts are applied at combat time when the player is on a slayer
 * task and the target is a valid task monster, not only through cache params on [obj.slayer_helm].
 */
internal object SlayerHelmCombatChecks {
    private val BLACK_MASK_KEYS: Set<String> =
        setOf("nzone_black_mask") + (1..10).map { "nzone_black_mask_$it" }.toSet()

    fun InvObj?.providesMeleeTaskBoost(): Boolean {
        val type = getOrNull(this) ?: return false
        val key = type.itemKey()
        if (type.param(BaseParams.blackmask) != 0 || type.param(BaseParams.blackmask_imbued) != 0) {
            return true
        }
        return itemKeyProvidesMeleeTaskBoost(key)
    }

    fun InvObj?.providesImbuedRangedMagicTaskBoost(): Boolean {
        val type = getOrNull(this) ?: return false
        val key = type.itemKey()
        if (
            type.param(BaseParams.blackmask_imbued) != 0 || key?.isImbuedBlackMaskItemKey() == true
        ) {
            return true
        }
        return itemKeyProvidesImbuedRangedMagicTaskBoost(key)
    }

    fun itemKeyProvidesMeleeTaskBoost(itemKey: String?): Boolean =
        itemKey?.let {
            it.isBlackMaskItemKey() || it.isImbuedBlackMaskItemKey() || it.isSlayerHelmItem()
        } == true

    fun itemKeyProvidesImbuedRangedMagicTaskBoost(itemKey: String?): Boolean =
        itemKey?.let { it.isImbuedBlackMaskItemKey() || it.isImbuedSlayerHelmItem() } == true

    private fun ItemServerType.itemKey(): String? = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private fun String.normalizedCombatItemKey(): String =
        removePrefix("obj.").removePrefix("sw_").removePrefix("pvpa_")

    private fun String.isBlackMaskItemKey(): Boolean = normalizedCombatItemKey() in BLACK_MASK_KEYS

    private fun String.isImbuedBlackMaskItemKey(): Boolean =
        normalizedCombatItemKey() == "nzone_black_mask_i"

    private fun String.isSlayerHelmItem(): Boolean =
        normalizedCombatItemKey().startsWith("slayer_helm")

    private fun String.isImbuedSlayerHelmItem(): Boolean =
        normalizedCombatItemKey().let { it == "slayer_helm_i" || it.startsWith("slayer_helm_i_") }
}
