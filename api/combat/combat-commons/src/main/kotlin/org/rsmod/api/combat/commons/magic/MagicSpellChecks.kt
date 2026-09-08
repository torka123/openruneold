package org.rsmod.api.combat.commons.magic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType

public object MagicSpellChecks {
    public fun isBoltSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.17_wind_bolt",
            "obj.23_water_bolt",
            "obj.29_earth_bolt",
            "obj.35_fire_bolt",
        )

    public fun isGodSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.60_claws_of_guthix",
            "obj.60_flames_of_zamorak",
            "obj.60_saradomin_strike",
        )

    public fun isDemonbaneSpell(spell: ItemServerType): Boolean =
        spell.isAnyType("obj.br_mithril_scimitar", "obj.br_willow_bow", "obj.br_adamant_scimitar")

    public fun isBindSpell(spell: ItemServerType): Boolean =
        spell.isAnyType("obj.20_bind", "obj.50_snare", "obj.79_entangle")

    public fun isIceSpell(spell: ItemServerType): Boolean = iceBindTicks(spell) != null

    /** Exact revision-240 Ancient Ice spell mapping; display names are not protocol identity. */
    public fun iceBindTicks(spell: ItemServerType): Int? =
        when (spell.id) {
            "obj.58_ice_rush".asRSCM(RSCMType.OBJ) -> 8
            "obj.70_ice_burst".asRSCM(RSCMType.OBJ) -> 16
            "obj.82_ice_blitz".asRSCM(RSCMType.OBJ) -> 24
            "obj.94_ice_barrage".asRSCM(RSCMType.OBJ) -> 32
            else -> null
        }

    public fun isGraspSpell(spell: ItemServerType): Boolean =
        spell.name.equals("Ghostly Grasp", ignoreCase = true) ||
            spell.name.equals("Skeletal Grasp", ignoreCase = true) ||
            spell.name.equals("Undead Grasp", ignoreCase = true)

    public fun isWindSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.01_wind_strike",
            "obj.17_wind_bolt",
            "obj.41_wind_blast",
            "obj.62_wind_wave",
            "obj.81_wind_surge",
        )

    public fun isWaterSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.05_water_strike",
            "obj.23_water_bolt",
            "obj.47_water_blast",
            "obj.65_water_wave",
            "obj.85_water_surge",
        )

    public fun isEarthSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.09_earth_strike",
            "obj.29_earth_bolt",
            "obj.53_earth_blast",
            "obj.70_earth_wave",
            "obj.90_earth_surge",
        )

    public fun isFireSpell(spell: ItemServerType): Boolean =
        spell.isAnyType(
            "obj.13_fire_strike",
            "obj.35_fire_bolt",
            "obj.59_fire_blast",
            "obj.75_fire_wave",
            "obj.95_fire_surge",
            "obj.fang_of_the_hound_fire",
        )
}
