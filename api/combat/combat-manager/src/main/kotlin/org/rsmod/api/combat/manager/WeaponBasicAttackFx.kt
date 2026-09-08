package org.rsmod.api.combat.manager

import org.rsmod.api.combat.commons.CombatStance

/**
 * Supplies revision-240 server-side melee FX omitted from cache item params.
 *
 * A decoded item animation parameter always takes priority in [PlayerAttackManager]. These entries
 * are deliberately limited to equipped weapon variants whose cache records otherwise fall through
 * to the unarmed punch/kick defaults.
 */
internal object WeaponBasicAttackFx {
    fun resolve(internalName: String?, stance: CombatStance?): BasicAttackFx? =
        when (internalName) {
            ARKAN_BLADE,
            THUNDER_KHOPESH,
            DEADMAN_THUNDER_KHOPESH -> sword(stance)
            NOXIOUS_HALBERD,
            BR_NOXIOUS_HALBERD -> halberd(stance)
            else -> null
        }

    private fun sword(stance: CombatStance?): BasicAttackFx =
        when (stance) {
            CombatStance.Stance3 -> BasicAttackFx(SWORD_STAB, SWORD_STAB_SOUND)
            else -> BasicAttackFx(SWORD_SLASH, SWORD_SLASH_SOUND)
        }

    private fun halberd(stance: CombatStance?): BasicAttackFx =
        when (stance) {
            CombatStance.Stance2 -> BasicAttackFx(HALBERD_SLASH, HALBERD_SLASH_SOUND)
            else -> BasicAttackFx(HALBERD_STAB, HALBERD_STAB_SOUND)
        }

    private const val ARKAN_BLADE = "obj.arkan_blade"
    private const val THUNDER_KHOPESH = "obj.thunder_khopesh"
    private const val DEADMAN_THUNDER_KHOPESH = "obj.deadman_thunder_khopesh"
    private const val NOXIOUS_HALBERD = "obj.noxious_halberd"
    private const val BR_NOXIOUS_HALBERD = "obj.br_noxious_halberd"

    private const val SWORD_SLASH = "seq.human_sword_slash"
    private const val SWORD_STAB = "seq.human_sword_stab"
    private const val HALBERD_STAB = "seq.human_spear_spike"
    private const val HALBERD_SLASH = "seq.human_scythe_sweep"

    private const val SWORD_SLASH_SOUND = 2500
    private const val SWORD_STAB_SOUND = 2501
    private const val HALBERD_STAB_SOUND = 2562
    private const val HALBERD_SLASH_SOUND = 2524
}

internal data class BasicAttackFx(val animation: String, val sound: Int)
