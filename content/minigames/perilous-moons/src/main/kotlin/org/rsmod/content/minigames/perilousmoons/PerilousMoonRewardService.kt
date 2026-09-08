package org.rsmod.content.minigames.perilousmoons

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

@Singleton
internal class PerilousMoonRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val objs: ObjRepository,
    private val collectionLog: CollectionLogService,
) {
    private val claiming = mutableSetOf<PlayerUid>()

    fun verifyDefinitions() {
        val items =
            PerilousMoon.entries.flatMap(PerilousMoon::uniqueItems) +
                COMMON_REWARDS.map(CommonReward::internalName)
        items.distinct().forEach { internalName ->
            requireNotNull(ServerCacheManager.getItem(internalName.asRSCM(RSCMType.OBJ))) {
                "Missing Lunar Chest reward item: $internalName"
            }
        }
    }

    fun claim(player: Player, chest: CoordGrid): List<MoonReward>? {
        if (!claiming.add(player.uid)) {
            player.mes("You are already searching the Lunar Chest.")
            return null
        }
        try {
            val subduedFlags = player.attr.getOrDefault(MOON_SUBDUED_FLAGS, 0) and ALL_MOONS_MASK
            val subdued = PerilousMoon.entries.filter { subduedFlags and it.bit != 0 }
            if (subdued.isEmpty()) {
                player.mes("You must subdue at least one Moon before searching the Lunar Chest.")
                return null
            }

            val uniques = subdued.mapNotNull { rollUnique(player, it) }
            val rewards =
                if (uniques.isNotEmpty()) {
                        uniques
                    } else {
                        List(PerilousMoonRules.commonRolls(subdued.size)) { rollCommon() }
                    }
                    .groupBy(MoonReward::internalName)
                    .map { (internalName, entries) ->
                        MoonReward(
                            internalName = internalName,
                            count = entries.sumOf(MoonReward::count),
                            unique = entries.any(MoonReward::unique),
                        )
                    }

            // Commit progress before items are handed out. A repeated or simultaneous click can
            // never reroll the same completion flags, even when inventory space is unavailable.
            player.attr.remove(MOON_SUBDUED_FLAGS)
            val chestCount = player.lunarChestCount + 1
            player.lunarChestCount = chestCount
            player.bloodMoonSubdued = 0
            player.blueMoonSubdued = 0
            player.eclipseMoonSubdued = 0
            rewards.forEach { reward ->
                player.invAddOrDrop(
                    repo = objs,
                    type = reward.internalName,
                    count = reward.count,
                    coords = chest,
                )
                val name =
                    ServerCacheManager.getItem(reward.internalName.asRSCM(RSCMType.OBJ))?.name
                        ?: reward.internalName
                val color = if (reward.unique) "<col=ff0000>" else "<col=ff9900>"
                player.mes("$color${reward.count} x $name</col>")
            }
            collectionLog.recordBatch(
                player = player,
                awards =
                    rewards.map { reward ->
                        reward.internalName.asRSCM(RSCMType.OBJ) to reward.count
                    },
                source =
                    CollectionLogAwardSource(CollectionLogAwardKind.BossChest, "moons-of-peril"),
                eventId = "${player.uid}:$chestCount",
            )
            player.mes(
                "You search the Lunar Chest and claim ${rewards.size} reward${if (rewards.size == 1) "" else "s"}."
            )
            return rewards
        } finally {
            claiming.remove(player.uid)
        }
    }

    private fun rollUnique(player: Player, moon: PerilousMoon): MoonReward? {
        if (random.of(1..56) != 1) return null
        val key = moon.uniqueFlagKey()
        var protected = player.attr.getOrDefault(key, 0) and COMPLETE_SET_MASK
        if (protected == COMPLETE_SET_MASK) protected = 0
        val eligible = moon.uniqueItems.indices.filter { protected and (1 shl it) == 0 }
        val index = eligible[random.of(eligible.indices)]
        player.attr[key] = protected or (1 shl index)
        return MoonReward(moon.uniqueItems[index], 1, unique = true)
    }

    private fun rollCommon(): MoonReward {
        val ticket = random.of(1..COMMON_TOTAL_WEIGHT)
        var cursor = 0
        val reward =
            COMMON_REWARDS.first { entry ->
                cursor += entry.weight
                ticket <= cursor
            }
        return MoonReward(
            internalName = reward.internalName,
            count = random.of(reward.minimum..reward.maximum),
            unique = false,
        )
    }
}

internal data class MoonReward(val internalName: String, val count: Int, val unique: Boolean)

private data class CommonReward(
    val weight: Int,
    val internalName: String,
    val minimum: Int,
    val maximum: Int,
)

private fun PerilousMoon.uniqueFlagKey() =
    when (this) {
        PerilousMoon.Blood -> BLOOD_UNIQUE_FLAGS
        PerilousMoon.Blue -> BLUE_UNIQUE_FLAGS
        PerilousMoon.Eclipse -> ECLIPSE_UNIQUE_FLAGS
    }

private var Player.lunarChestCount by intVarp("varp.kc_moon_chest")

private var Player.bloodMoonSubdued by intVarBit("varbit.pmoon_boss_blood_dead")

private var Player.blueMoonSubdued by intVarBit("varbit.pmoon_boss_blue_dead")

private var Player.eclipseMoonSubdued by intVarBit("varbit.pmoon_boss_eclipse_dead")

private const val COMPLETE_SET_MASK = 0b1111
private const val ALL_MOONS_MASK = 0b111

private val COMMON_REWARDS =
    listOf(
        CommonReward(5, "obj.atlatl_dart", 72, 120),
        CommonReward(4, "obj.swamp_tar", 79, 119),
        CommonReward(3, "obj.cert_sun_kissed_bone", 6, 12),
        CommonReward(3, "obj.cert_bucket_supercompost", 6, 12),
        CommonReward(3, "obj.cert_softclay", 15, 25),
        CommonReward(2, "obj.cert_unidentified_harralander", 12, 18),
        CommonReward(2, "obj.blessed_bone_shard", 160, 179),
        CommonReward(2, "obj.cert_water_orb", 30, 45),
        CommonReward(2, "obj.maple_seed", 1, 2),
        CommonReward(2, "obj.cert_babywyrm_bones", 42, 54),
        CommonReward(1, "obj.cert_unidentified_irit", 12, 18),
        CommonReward(1, "obj.yew_seed", 1, 1),
    )

private val COMMON_TOTAL_WEIGHT = COMMON_REWARDS.sumOf(CommonReward::weight)
