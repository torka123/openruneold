package org.rsmod.api.player.worn

import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isAnyType
import org.rsmod.game.inv.isType

public object EquipmentChecks {
    public const val TWINFLAME_SPELL_ATTACK_RATE: Int = 6
    public const val HARMONISED_NIGHTMARE_SPELL_ATTACK_RATE: Int = 4

    public fun isTwinflameStaff(obj: InvObj?): Boolean = obj.isType("obj.twinflame_staff")

    public fun isHarmonisedNightmareStaff(obj: InvObj?): Boolean =
        obj.isType("obj.nightmare_staff_harmonised")

    public fun spellAttackRateOverride(obj: InvObj?): Int? =
        when {
            isTwinflameStaff(obj) -> TWINFLAME_SPELL_ATTACK_RATE
            isHarmonisedNightmareStaff(obj) -> HARMONISED_NIGHTMARE_SPELL_ATTACK_RATE
            else -> null
        }

    public fun isSmokeStaff(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.smoke_battlestaff",
            "obj.mystic_smoke_battlestaff",
            "obj.twinflame_staff",
        )

    public fun isSoulreaperAxe(obj: InvObj?): Boolean =
        obj.isAnyType("obj.soulreaper", "obj.soulreaper_axe_orn")

    public fun isSerpentineHelm(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.serpentine_helm_charged",
            "obj.serpentine_helm_charged_cyan",
            "obj.serpentine_helm_charged_red",
        )

    public fun isTumekensShadow(obj: InvObj?): Boolean = obj.isType("obj.tumekens_shadow")

    public fun isTwistedBow(obj: InvObj?): Boolean = obj.isType("obj.twisted_bow")

    public fun isDragonHunterCrossbow(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.dragonhunter_xbow",
            "obj.dragonhunter_xbow_vorkath",
            "obj.dragonhunter_xbow_kbd",
        )

    public fun isCrystalBow(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.crystal_bow",
            "obj.bow_of_faerdhinen",
            "obj.bow_of_faerdhinen_infinite",
            "obj.bow_of_faerdhinen_infinite_ithell",
            "obj.bow_of_faerdhinen_infinite_iorwerth",
            "obj.bow_of_faerdhinen_infinite_trahaearn",
            "obj.bow_of_faerdhinen_infinite_cadarn",
            "obj.bow_of_faerdhinen_infinite_crwys",
            "obj.bow_of_faerdhinen_infinite_meilyr",
            "obj.bow_of_faerdhinen_infinite_amlodd",
        )

    public fun isCrystalHelm(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.crystal_helmet_hefin",
            "obj.crystal_helmet_ithell",
            "obj.crystal_helmet_iorwerth",
            "obj.crystal_helmet_trahaearn",
            "obj.crystal_helmet_cadarn",
            "obj.crystal_helmet_crwys",
            "obj.crystal_helmet",
            "obj.crystal_helmet_amlodd",
        )

    public fun isCrystalBody(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.crystal_chestplate_hefin",
            "obj.crystal_chestplate_ithell",
            "obj.crystal_chestplate_iorwerth",
            "obj.crystal_chestplate_trahaearn",
            "obj.crystal_chestplate_cadarn",
            "obj.crystal_chestplate_crwys",
            "obj.crystal_chestplate",
            "obj.crystal_chestplate_amlodd",
        )

    public fun isCrystalLegs(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.crystal_platelegs_hefin",
            "obj.crystal_platelegs_ithell",
            "obj.crystal_platelegs_iorwerth",
            "obj.crystal_platelegs_trahaearn",
            "obj.crystal_platelegs_cadarn",
            "obj.crystal_platelegs_crwys",
            "obj.crystal_platelegs",
            "obj.crystal_platelegs_amlodd",
        )

    public fun isObsidianSet(helm: InvObj?, top: InvObj?, legs: InvObj?): Boolean =
        helm.isType("obj.obsidian_helmet") &&
            top.isType("obj.obsidian_platebody") &&
            legs.isType("obj.obsidian_platelegs")

    public fun isVirtusMask(obj: InvObj?): Boolean =
        obj.isAnyType("obj.virtus_mask", "obj.virtus_mask_ornament")

    public fun isVirtusRobeTop(obj: InvObj?): Boolean =
        obj.isAnyType("obj.virtus_top", "obj.virtus_top_ornament")

    public fun isVirtusRobeBottom(obj: InvObj?): Boolean =
        obj.isAnyType("obj.virtus_legs", "obj.virtus_legs_ornament")

    public fun isVoidMeleeHelm(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.game_pest_melee_helm",
            "obj.game_pest_melee_helm_trouver",
            "obj.league_3_void_melee_helm",
            "obj.league_3_void_melee_helm_trouver",
        )

    public fun isVoidRangerHelm(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.game_pest_archer_helm",
            "obj.game_pest_archer_helm_trouver",
            "obj.league_3_void_range_helm",
            "obj.league_3_void_range_helm_trouver",
        )

    public fun isVoidMageHelm(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.game_pest_mage_helm",
            "obj.game_pest_mage_helm_trouver",
            "obj.league_3_void_mage_helm",
            "obj.league_3_void_mage_helm_trouver",
        )

    public fun isVoidTop(obj: InvObj?): Boolean = isRegularVoidTop(obj) || isEliteVoidTop(obj)

    public fun isRegularVoidTop(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.pest_void_knight_top",
            "obj.pest_void_knight_top_trouver",
            "obj.league_3_void_knight_top",
            "obj.league_3_void_knight_top_trouver",
        )

    public fun isEliteVoidTop(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.elite_void_knight_top",
            "obj.elite_void_knight_top_trouver",
            "obj.league_3_void_knight_top_elite",
            "obj.league_3_void_knight_top_elite_trouver",
        )

    public fun isVoidRobe(obj: InvObj?): Boolean = isRegularVoidRobe(obj) || isEliteVoidRobe(obj)

    public fun isRegularVoidRobe(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.pest_void_knight_robes",
            "obj.pest_void_knight_robes_trouver",
            "obj.league_3_void_knight_robes",
            "obj.league_3_void_knight_robes_trouver",
        )

    public fun isEliteVoidRobe(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.elite_void_knight_robes",
            "obj.elite_void_knight_robes_trouver",
            "obj.league_3_void_knight_robes_elite",
            "obj.league_3_void_knight_robes_elite_trouver",
        )

    public fun isVoidGloves(obj: InvObj?): Boolean =
        obj.isAnyType(
            "obj.pest_void_knight_gloves",
            "obj.pest_void_knight_gloves_trouver",
            "obj.league_3_void_knight_gloves",
            "obj.league_3_void_knight_gloves_trouver",
        )

    /**
     * Returns whether [obj] is an active Barrows piece for [brother] and [piece].
     *
     * The unnumbered, fully repaired item is active immediately. The 100/75/50/25 combat states
     * retain identical stats and set effects. Broken/0 variants are intentionally excluded.
     */
    private fun isBarrowsPiece(obj: InvObj?, brother: String, piece: String): Boolean =
        obj.isAnyType(
            "obj.barrows_${brother}_${piece}",
            "obj.barrows_${brother}_${piece}_100",
            "obj.barrows_${brother}_${piece}_75",
            "obj.barrows_${brother}_${piece}_50",
            "obj.barrows_${brother}_${piece}_25",
        )

    private fun isBarrowsSet(
        brother: String,
        helm: InvObj?,
        top: InvObj?,
        legs: InvObj?,
        weapon: InvObj?,
    ): Boolean =
        isBarrowsPiece(helm, brother, "head") &&
            isBarrowsPiece(top, brother, "body") &&
            isBarrowsPiece(legs, brother, "legs") &&
            isBarrowsPiece(weapon, brother, "weapon")

    /** The amulet keeps its Barrows enhancement until its remaining durability reaches zero. */
    public fun isAmuletOfTheDamned(obj: InvObj?): Boolean =
        obj.isAnyType("obj.damned_amulet", "obj.damned_amulet_degraded")

    public fun isDharokSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("dharok", helm, top, legs, weapon)

    public fun isGuthanSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("guthan", helm, top, legs, weapon)

    public fun isKarilSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("karil", helm, top, legs, weapon)

    public fun isToragSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("torag", helm, top, legs, weapon)

    public fun isVeracSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("verac", helm, top, legs, weapon)

    public fun isAhrimSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        isBarrowsSet("ahrim", helm, top, legs, weapon)

    public fun isBloodMoonSet(
        helm: InvObj?,
        top: InvObj?,
        legs: InvObj?,
        weapon: InvObj?,
    ): Boolean =
        helm.isAnyType(
            "obj.blood_moon_helm",
            "obj.blood_moon_helm_degraded",
            "obj.br_blood_moon_helm",
        ) &&
            top.isAnyType(
                "obj.blood_moon_chestplate",
                "obj.blood_moon_chestplate_degraded",
                "obj.br_blood_moon_chestplate",
            ) &&
            legs.isAnyType(
                "obj.blood_moon_tassets",
                "obj.blood_moon_tassets_degraded",
                "obj.br_blood_moon_tassets",
            ) &&
            weapon.isAnyType("obj.dual_macuahuitl", "obj.br_dual_macuahuitl")

    public fun isBlueMoonSet(helm: InvObj?, top: InvObj?, legs: InvObj?, weapon: InvObj?): Boolean =
        helm.isAnyType(
            "obj.frost_moon_helm",
            "obj.frost_moon_helm_degraded",
            "obj.br_frost_moon_helm",
        ) &&
            top.isAnyType(
                "obj.frost_moon_chestplate",
                "obj.frost_moon_chestplate_degraded",
                "obj.br_frost_moon_chestplate",
            ) &&
            legs.isAnyType(
                "obj.frost_moon_tassets",
                "obj.frost_moon_tassets_degraded",
                "obj.br_frost_moon_tassets",
            ) &&
            weapon.isAnyType("obj.frostmoon_spear", "obj.br_frostmoon_spear")

    public fun isEclipseMoonSet(
        helm: InvObj?,
        top: InvObj?,
        legs: InvObj?,
        weapon: InvObj?,
    ): Boolean =
        helm.isAnyType(
            "obj.eclipse_moon_helm",
            "obj.eclipse_moon_helm_degraded",
            "obj.br_eclipse_moon_helm",
        ) &&
            top.isAnyType(
                "obj.eclipse_moon_chestplate",
                "obj.eclipse_moon_chestplate_degraded",
                "obj.br_eclipse_moon_chestplate",
            ) &&
            legs.isAnyType(
                "obj.eclipse_moon_tassets",
                "obj.eclipse_moon_tassets_degraded",
                "obj.br_eclipse_moon_tassets",
            ) &&
            weapon.isAnyType("obj.eclipse_atlatl", "obj.br_eclipse_atlatl")

    public fun isJusticiarSet(helm: InvObj?, top: InvObj?, legs: InvObj?): Boolean =
        helm.isType("obj.justiciar_faceguard") &&
            top.isType("obj.justiciar_chestguard") &&
            legs.isType("obj.justiciar_leg_guards")
}
