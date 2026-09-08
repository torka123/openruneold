package org.rsmod.content.minigames.barrows

import org.rsmod.map.CoordGrid

internal val BARROWS_TUNNEL_ENTRY = CoordGrid(3568, 9695)
internal val BARROWS_CHEST_ROOM_ENTRY = CoordGrid(3551, 9694)

internal enum class BarrowsBrother(
    val displayName: String,
    val npc: String,
    val sarcophagus: String,
    val staircase: String,
    val moundSouthWest: CoordGrid,
    val moundNorthEast: CoordGrid,
    val moundCenter: CoordGrid,
    val cryptEntryCoords: CoordGrid,
    val sarcophagusCoords: CoordGrid,
    val spawnCoords: CoordGrid,
    val staircaseCoords: CoordGrid,
    val killedVarbit: String,
    val rewards: List<String>,
) {
    Ahrim(
        "Ahrim the Blighted",
        "npc.barrows_ahrim",
        "loc.barrow_ahrim_sarcophagus",
        "loc.barrows_stairs_ahrim",
        CoordGrid(3562, 3285),
        CoordGrid(3568, 3291),
        CoordGrid(3565, 3288),
        CoordGrid(3557, 9703, 3),
        CoordGrid(3555, 9698, 3),
        CoordGrid(3556, 9696, 3),
        CoordGrid(3558, 9703, 3),
        "varbit.barrows_killed_ahrim",
        listOf("obj.barrows_ahrim_head", "obj.barrows_ahrim_weapon", "obj.barrows_ahrim_body", "obj.barrows_ahrim_legs"),
    ),
    Dharok(
        "Dharok the Wretched",
        "npc.barrows_dharok",
        "loc.barrow_dharok_sarcophagus",
        "loc.barrows_stairs_dharok",
        CoordGrid(3573, 3295),
        CoordGrid(3578, 3300),
        CoordGrid(3575, 3298),
        CoordGrid(3556, 9718, 3),
        CoordGrid(3554, 9714, 3),
        CoordGrid(3552, 9715, 3),
        CoordGrid(3557, 9718, 3),
        "varbit.barrows_killed_dharok",
        listOf("obj.barrows_dharok_head", "obj.barrows_dharok_weapon", "obj.barrows_dharok_body", "obj.barrows_dharok_legs"),
    ),
    Guthan(
        "Guthan the Infested",
        "npc.barrows_guthan",
        "loc.barrow_guthan_sarcophagus",
        "loc.barrows_stairs_guthan",
        CoordGrid(3575, 3279),
        CoordGrid(3580, 3286),
        CoordGrid(3577, 3282),
        CoordGrid(3534, 9704, 3),
        CoordGrid(3538, 9703, 3),
        CoordGrid(3543, 9703, 3),
        CoordGrid(3534, 9705, 3),
        "varbit.barrows_killed_guthan",
        listOf("obj.barrows_guthan_head", "obj.barrows_guthan_weapon", "obj.barrows_guthan_body", "obj.barrows_guthan_legs"),
    ),
    Karil(
        "Karil the Tainted",
        "npc.barrows_karil",
        "loc.barrow_karil_sarcophagus",
        "loc.barrows_stairs_karil",
        CoordGrid(3563, 3273),
        CoordGrid(3568, 3278),
        CoordGrid(3566, 3275),
        CoordGrid(3546, 9684, 3),
        CoordGrid(3550, 9682, 3),
        CoordGrid(3554, 9682, 3),
        CoordGrid(3546, 9685, 3),
        "varbit.barrows_killed_karil",
        listOf("obj.barrows_karil_head", "obj.barrows_karil_weapon", "obj.barrows_karil_body", "obj.barrows_karil_legs"),
    ),
    Torag(
        "Torag the Corrupted",
        "npc.barrows_torag",
        "loc.barrow_torag_sarcophagus",
        "loc.barrows_stairs_torag",
        CoordGrid(3551, 3279),
        CoordGrid(3556, 3285),
        CoordGrid(3554, 3282),
        CoordGrid(3568, 9683, 3),
        CoordGrid(3569, 9685, 3),
        CoordGrid(3572, 9686, 3),
        CoordGrid(3565, 9683, 3),
        "varbit.barrows_killed_torag",
        listOf("obj.barrows_torag_head", "obj.barrows_torag_weapon", "obj.barrows_torag_body", "obj.barrows_torag_legs"),
    ),
    Verac(
        "Verac the Defiled",
        "npc.barrows_verac",
        "loc.barrow_verac_sarcophagus",
        "loc.barrows_stairs_verac",
        CoordGrid(3554, 3293),
        CoordGrid(3560, 3301),
        CoordGrid(3557, 3297),
        CoordGrid(3578, 9706, 3),
        CoordGrid(3573, 9705, 3),
        CoordGrid(3570, 9706, 3),
        CoordGrid(3578, 9703, 3),
        "varbit.barrows_killed_verac",
        listOf("obj.barrows_verac_head", "obj.barrows_verac_weapon", "obj.barrows_verac_body", "obj.barrows_verac_legs"),
    ),
}

internal val BarrowsBrother.combatAttackRange: Int
    get() =
        when (this) {
            BarrowsBrother.Ahrim -> 10
            BarrowsBrother.Karil -> 8
            else -> 1
        }

internal fun BarrowsBrother.containsSurfaceMound(coords: CoordGrid): Boolean =
    coords.level == 0 &&
        coords.x in moundSouthWest.x..moundNorthEast.x &&
        coords.z in moundSouthWest.z..moundNorthEast.z

internal data class BarrowsReward(
    val obj: String,
    val count: Int,
)
