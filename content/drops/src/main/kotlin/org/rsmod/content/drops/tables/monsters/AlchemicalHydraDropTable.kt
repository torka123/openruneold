package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.RollableHooks
import dtx.rs.RSDropTable
import dtx.rs.RSTable
import dtx.rs.npcs
import kotlin.random.Random
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.CollectionLogStateCodec
import org.rsmod.api.config.constants
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.dropRollable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

internal fun interface HydraRewardRandom {
    fun nextInt(bound: Int): Int
}

internal object AlchemicalHydraUniqueEngine {
    private const val EYE_ID = 22973
    private const val FANG_ID = 22971
    private const val HEART_ID = 22969

    fun roll(quantities: Map<Int, Int>, random: HydraRewardRandom): DropRollItem? {
        if (random.checked(2000) == 0) {
            return DropRollItem("obj.dragon_thrownaxe", 500 + random.checked(501))
        }
        if (random.checked(2000) == 0) {
            return DropRollItem("obj.dragon_knife", 500 + random.checked(501))
        }
        if (random.checked(1000) == 0) return DropRollItem("obj.hydra_claw", 1)
        if (random.checked(512) == 0) return DropRollItem("obj.hydra_tail", 1)
        if (random.checked(512) == 0) return DropRollItem("obj.hydra_leather", 1)
        if (random.checked(180) == 0) {
            val part =
                listOf(
                        RingPart(EYE_ID, "obj.hydra_eye"),
                        RingPart(FANG_ID, "obj.hydra_fang"),
                        RingPart(HEART_ID, "obj.hydra_heart"),
                    )
                    .minBy { quantities[it.id] ?: 0 }
            return DropRollItem(part.obj, 1)
        }
        return null
    }

    private fun HydraRewardRandom.checked(bound: Int): Int {
        require(bound > 0)
        return nextInt(bound).also { require(it in 0 until bound) }
    }

    private data class RingPart(val id: Int, val obj: String)
}

internal object AlchemicalHydraUniqueRoll :
    RSTable<Player, DropRollItem>, RollableHooks<Player, DropRollItem> by RollableHooks.Default() {
    override val tableIdentifier: String = "Alchemical Hydra sequential unique pre-roll"
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> = emptyList()

    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> {
        if (otherArgs[KillRollContext.npc] == null) return RollResult.Nothing()
        val quantities =
            CollectionLogStateCodec.decode(target.attr[CollectionLogService.STATE])
                ?.quantities
                .orEmpty()
        val drop =
            AlchemicalHydraUniqueEngine.roll(
                quantities = quantities,
                random = HydraRewardRandom(Random::nextInt),
            ) ?: return RollResult.Nothing()
        return RollResult.Single(drop)
    }
}

@field:RegisterDropTable
@JvmField
public val alchemicalHydraDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Alchemical Hydra Drops",
        npcs = npcs("npc.hydraboss_4"),
        guaranteed = rsPlayerGuaranteedTable { "obj.hydra_bones" count 2 },
        separateRolls = AlchemicalHydraUniqueRoll,
        mainRolls = 2,
        mainRollsFor = { _, unique -> if (unique is RollResult.Nothing) 2 else 1 },
        mainTable =
            rsPlayerWeightedTable(total = 110) {
                name("Alchemical Hydra Drops")
                8 weight
                    dropRollable(
                        DropRollItem(
                            "obj.mystic_fire_staff",
                            1,
                            bonusDrops = listOf(DropRollItem("obj.mystic_water_staff", 1)),
                        )
                    )
                8 weight "obj.mystic_water_staff" count 1
                5 weight "obj.cert_battlestaff" count 8..12
                5 weight "obj.black_dragonhide_body" count 1
                3 weight "obj.dragon_longsword" count 1
                3 weight "obj.dragon_med_helm" count 1
                3 weight
                    dropRollable(
                        DropRollItem(
                            "obj.rune_platebody",
                            1,
                            bonusDrops =
                                listOf(
                                    DropRollItem(
                                        "obj.rune_platelegs",
                                        1,
                                        condition = { player ->
                                            player.appearance.bodyType == constants.bodytype_a
                                        },
                                    ),
                                    DropRollItem(
                                        "obj.rune_plateskirt",
                                        1,
                                        condition = { player ->
                                            player.appearance.bodyType == constants.bodytype_b
                                        },
                                    ),
                                ),
                        )
                    )
                2 weight "obj.dragon_battleaxe" count 1
                2 weight "obj.rune_platelegs" count 1
                2 weight "obj.rune_plateskirt" count 1
                1 weight
                    dropRollable(
                        DropRollItem(
                            "obj.mystic_robe_top_light",
                            1,
                            bonusDrops = listOf(DropRollItem("obj.mystic_robe_bottom_light", 1)),
                        )
                    )
                1 weight "obj.mystic_robe_bottom_light" count 1
                6 weight "obj.chaosrune" count 150..300
                6 weight "obj.deathrune" count 150..300
                6 weight "obj.bloodrune" count 150..300
                6 weight "obj.astralrune" count 150..300
                2 weight
                    "obj.xbows_crossbow_bolts_runite_tipped_dragonstone_enchanted" count
                    100..120
                1 weight "obj.xbows_crossbow_bolts_runite_tipped_onyx_enchanted" count 35..50
                1 weight "obj.coins" count 5550..25550
                10 weight "obj.coins" count 40000..60000
                7 weight "obj.shark" count 2..4
                7 weight
                    dropRollable(
                        DropRollItem(
                            "obj.3doserangerspotion",
                            1,
                            bonusDrops = listOf(DropRollItem("obj.3dose2restore", 2)),
                        )
                    )
                7 weight "obj.3dose2restore" count 2
                6 weight "obj.cert_dragon_bones" count 30
                1 weight "obj.crystal_key" count 1

                1 weight SharedDropTables.rareDrop
            },
        tertiaries =
            rsPlayerTertiaryTable {
                onBuilder { brimstoneKeyRoll(konarTaskBonus = true) }
                1 outOf 256 weight "obj.poh_alchemical_hydra_head" count 1
                1 outOf 2000 weight "obj.jar_of_chemicals" count 1
                1 outOf 3000 weight "obj.hydrapet" count 1
                1 outOf
                    95 weight
                    "obj.trail_clue_hard_map001" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_clue_hard_map001")
                    }
                1 outOf
                    243 weight
                    "obj.trail_elite_emote_exp1" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                    }
            },
    )

// Unknown wiki drop rates (text rarity — need data collection):
//   - Hydra's eye [main/1/{{#expr:180/(1999/2000*1999/2000*999/1000*511/512*511/512) round 1}}]
//   - Hydra's fang [main/1/{{#expr:180/(1999/2000*1999/2000*999/1000*511/512*511/512) round 1}}]
//   - Hydra's heart [main/1/{{#expr:180/(1999/2000*1999/2000*999/1000*511/512*511/512) round 1}}]
//   - Hydra tail [main/1/{{#expr:512/(1999/2000*1999/2000*999/1000) round 1}}]
//   - Hydra leather [main/1/{{#expr:512/(1999/2000*1999/2000*999/1000*511/512) round 1}}]
//   - Hydra's claw [main/1/{{#expr:1000/(1999/2000*1999/2000) round 1}}]
//   - Dragon knife [main/1/{{#expr:2000/(1999/2000) round 1}}]
//   - Grimy avantoe [main/1/{{#expr:1/(5*{{#var:herbbase}}) round 1}}]
//   - Grimy kwuarm [main/1/{{#expr:1/(5*{{#var:herbbase}}) round 1}}]
//   - Grimy ranarr weed [main/1/{{#expr:1/(4*{{#var:herbbase}}) round 1}}]
//   - Grimy snapdragon [main/1/{{#expr:1/(4*{{#var:herbbase}}) round 1}}]
//   - Grimy cadantine [main/1/{{#expr:1/(4*{{#var:herbbase}}) round 1}}]
//   - Grimy dwarf weed [main/1/{{#expr:1/(4*{{#var:herbbase}}) round 1}}]
//   - Grimy lantadyme [main/1/{{#expr:1/(3*{{#var:herbbase}}) round 1}}]
//   - Grimy torstol [main/1/{{#expr:1/(3*{{#var:herbbase}}) round 1}}]
