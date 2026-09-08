package org.rsmod.content.minigames.corruptedgauntlet

import kotlin.math.max

internal object CorruptedGauntletRules {
    fun remainingPreparationTicks(elapsedTicks: Int): Int =
        (CorruptedGauntletConfig.PREPARATION_TICKS - max(0, elapsedTicks)).coerceAtLeast(0)

    fun preparationExpired(elapsedTicks: Int): Boolean =
        elapsedTicks >= CorruptedGauntletConfig.PREPARATION_TICKS

    /**
     * Live Hunllef begins with a random protection prayer. Runs already carry a stable seed, so
     * deriving the initial prayer from it keeps deterministic tests and replay commands useful.
     */
    fun initialHunllefProtection(seed: Long): CorruptedGauntletCombatStyle {
        var mixed = seed - 7_046_029_254_386_353_131L
        mixed = (mixed xor (mixed ushr 30)) * -4_658_895_280_553_007_687L
        mixed = (mixed xor (mixed ushr 27)) * -7_723_592_293_110_705_685L
        mixed = mixed xor (mixed ushr 31)
        val index =
            Math.floorMod(
                (mixed xor (mixed ushr 32)).toInt(),
                CorruptedGauntletCombatStyle.entries.size,
            )
        return CorruptedGauntletCombatStyle.entries[index]
    }

    fun initialHunllefState(seed: Long): CorruptedGauntletHunllefState =
        CorruptedGauntletHunllefState(protectionStyle = initialHunllefProtection(seed))

    fun afterHunllefAttack(state: CorruptedGauntletHunllefState): CorruptedGauntletHunllefState =
        advanceOutgoingCycle(state, countTowardTornadoes = true)

    private fun advanceOutgoingCycle(
        state: CorruptedGauntletHunllefState,
        countTowardTornadoes: Boolean,
    ): CorruptedGauntletHunllefState {
        val attacks = state.attacksInStyle + 1
        val switchStyle = attacks >= CorruptedGauntletConfig.HUNLLEF_STYLE_ATTACKS
        return state.copy(
            outgoingStyle =
                if (switchStyle) state.outgoingStyle.otherRangedStyle() else state.outgoingStyle,
            attacksInStyle = if (switchStyle) 0 else attacks,
            attacksSinceTornadoes = state.attacksSinceTornadoes + if (countTowardTornadoes) 1 else 0,
        )
    }

    /** Tornado summoning occupies one of Hunllef's four outgoing attack turns. */
    fun afterTornadoCast(state: CorruptedGauntletHunllefState): CorruptedGauntletHunllefState =
        advanceOutgoingCycle(state, countTowardTornadoes = false).copy(attacksSinceTornadoes = 0)

    /**
     * Captured fights place tornado casts into the deterministic normal-attack cycle. Ten normal
     * attacks (50 ticks) is the observed corrupted cadence; trample never advances this counter.
     */
    fun tornadoesDue(state: CorruptedGauntletHunllefState): Boolean =
        state.attacksSinceTornadoes >= 10

    fun tornadoCount(currentHitpoints: Int): Int =
        when {
            currentHitpoints > 666 -> 2
            currentHitpoints > 333 -> 3
            else -> 4
        }

    fun afterValidPlayerAttack(
        state: CorruptedGauntletHunllefState,
        style: CorruptedGauntletCombatStyle,
    ): CorruptedGauntletHunllefState {
        if (style == state.protectionStyle) return state
        val hits = state.validHitsTowardProtectionSwitch + 1
        return if (hits >= CorruptedGauntletConfig.HUNLLEF_PROTECTION_HITS) {
            state.copy(protectionStyle = style, validHitsTowardProtectionSwitch = 0)
        } else {
            state.copy(validHitsTowardProtectionSwitch = hits)
        }
    }

    fun correctPrayerMaximumHit(armourTierSum: Int): Int =
        when {
            armourTierSum >= 9 -> 8
            armourTierSum >= 6 -> 10
            armourTierSum >= 3 -> 13
            else -> 16
        }

    fun tornadoDamage(armourTierSum: Int): CorruptedGauntletTornadoDamage =
        when {
            armourTierSum >= 9 -> CorruptedGauntletTornadoDamage(8, 15)
            armourTierSum >= 6 -> CorruptedGauntletTornadoDamage(10, 20)
            armourTierSum >= 3 -> CorruptedGauntletTornadoDamage(14, 25)
            else -> CorruptedGauntletTornadoDamage(15, 30)
        }

    fun armourTierSum(helmTier: Int, bodyTier: Int, legsTier: Int): Int {
        require(helmTier in 0..3)
        require(bodyTier in 0..3)
        require(legsTier in 0..3)
        return helmTier + bodyTier + legsTier
    }

    fun hunllefPhase(currentHitpoints: Int): CorruptedGauntletHunllefPhase =
        when {
            currentHitpoints > 666 -> CorruptedGauntletHunllefPhase.FIRST
            currentHitpoints > 333 -> CorruptedGauntletHunllefPhase.SECOND
            else -> CorruptedGauntletHunllefPhase.FINAL
        }

    /**
     * The warning and active windows are explicit reference constants. Later phases select patterns
     * more frequently, but never before the previous pattern has fully expired.
     */
    fun floorSchedule(currentHitpoints: Int): CorruptedGauntletFloorSchedule =
        when (hunllefPhase(currentHitpoints)) {
            CorruptedGauntletHunllefPhase.FIRST -> CorruptedGauntletFloorSchedule(5, 6, 24)
            CorruptedGauntletHunllefPhase.SECOND -> CorruptedGauntletFloorSchedule(5, 6, 16)
            CorruptedGauntletHunllefPhase.FINAL -> CorruptedGauntletFloorSchedule(5, 6, 11)
        }

    fun floorDamagesPlayer(
        warningAgeTicks: Int,
        playerOnTile: Boolean,
        schedule: CorruptedGauntletFloorSchedule,
    ): Boolean =
        playerOnTile &&
            warningAgeTicks >= schedule.warningTicks &&
            warningAgeTicks < schedule.lifetimeTicks

    fun floorExpired(warningAgeTicks: Int, schedule: CorruptedGauntletFloorSchedule): Boolean =
        warningAgeTicks >= schedule.lifetimeTicks

    fun floorDamagesPlayer(warningAgeTicks: Int, playerOnTile: Boolean): Boolean =
        floorDamagesPlayer(
            warningAgeTicks,
            playerOnTile,
            floorSchedule(CorruptedGauntletConfig.HUNLLEF_MAX_HITPOINTS),
        )

    fun floorExpired(warningAgeTicks: Int): Boolean =
        floorExpired(warningAgeTicks, floorSchedule(CorruptedGauntletConfig.HUNLLEF_MAX_HITPOINTS))

    fun monsterShardRange(tier: CorruptedGauntletMonsterTier): IntRange =
        when (tier) {
            CorruptedGauntletMonsterTier.WEAK -> 10..37
            CorruptedGauntletMonsterTier.STRONG,
            CorruptedGauntletMonsterTier.DEMI_BOSS -> 50..105
        }

    fun guaranteesWeaponFrame(
        tier: CorruptedGauntletMonsterTier,
        killsAtTierBeforeThisKill: Int,
        alreadyOwnsFrame: Boolean,
    ): Boolean {
        if (alreadyOwnsFrame) return false
        return when (tier) {
            CorruptedGauntletMonsterTier.WEAK -> killsAtTierBeforeThisKill >= 2
            CorruptedGauntletMonsterTier.STRONG -> killsAtTierBeforeThisKill == 0
            CorruptedGauntletMonsterTier.DEMI_BOSS -> true
        }
    }

    fun demiBossComponent(
        boss: CorruptedGauntletDemiBoss,
        ownedComponents: Set<Int>,
        fallbackRoll: Int,
    ): Int {
        val natural =
            when (boss) {
                CorruptedGauntletDemiBoss.BEAR -> CorruptedGauntletConfig.Obj.SPIKE
                CorruptedGauntletDemiBoss.DRAGON -> CorruptedGauntletConfig.Obj.ORB
                CorruptedGauntletDemiBoss.DARK_BEAST -> CorruptedGauntletConfig.Obj.BOWSTRING
            }
        if (natural !in ownedComponents) return natural
        val missing =
            listOf(
                    CorruptedGauntletConfig.Obj.SPIKE,
                    CorruptedGauntletConfig.Obj.ORB,
                    CorruptedGauntletConfig.Obj.BOWSTRING,
                )
                .filterNot(ownedComponents::contains)
        if (missing.isNotEmpty()) return missing[Math.floorMod(fallbackRoll, missing.size)]
        val all =
            listOf(
                CorruptedGauntletConfig.Obj.SPIKE,
                CorruptedGauntletConfig.Obj.ORB,
                CorruptedGauntletConfig.Obj.BOWSTRING,
            )
        return all[Math.floorMod(fallbackRoll, all.size)]
    }

    private fun CorruptedGauntletCombatStyle.otherRangedStyle(): CorruptedGauntletCombatStyle =
        when (this) {
            CorruptedGauntletCombatStyle.RANGED -> CorruptedGauntletCombatStyle.MAGIC
            CorruptedGauntletCombatStyle.MAGIC -> CorruptedGauntletCombatStyle.RANGED
            CorruptedGauntletCombatStyle.MELEE -> CorruptedGauntletCombatStyle.RANGED
        }
}

internal object CorruptedGauntletCraftingRules {
    private fun ingredient(itemId: Int, amount: Int = 1) =
        CorruptedGauntletIngredient(itemId, amount)

    private val basicMaterials =
        listOf(
            ingredient(CorruptedGauntletConfig.Obj.ORE),
            ingredient(CorruptedGauntletConfig.Obj.BARK),
            ingredient(CorruptedGauntletConfig.Obj.LINUM),
        )

    private fun armourRecipe(
        output: Int,
        previous: Int?,
        shardCost: Int,
        materialCount: Int,
        experience: Int,
    ) =
        CorruptedGauntletRecipe(
            output,
            shardCost,
            buildList {
                if (previous != null) add(ingredient(previous))
                basicMaterials.forEach { add(it.copy(amount = materialCount)) }
            },
            experience,
        )

    private fun weaponRecipe(
        output: Int,
        previousOrFrame: Int,
        shardCost: Int,
        component: Int? = null,
    ) =
        CorruptedGauntletRecipe(
            output,
            shardCost,
            buildList {
                add(ingredient(previousOrFrame))
                if (component != null) add(ingredient(component))
            },
            if (component == null && previousOrFrame == CorruptedGauntletConfig.Obj.WEAPON_FRAME) 10
            else 30,
        )

    val recipes: List<CorruptedGauntletRecipe> =
        listOf(
            CorruptedGauntletRecipe(CorruptedGauntletConfig.Obj.VIAL, 10, experience = 5),
            CorruptedGauntletRecipe(
                CorruptedGauntletConfig.Obj.TELEPORT_CRYSTAL,
                40,
                experience = 20,
            ),
            armourRecipe(CorruptedGauntletConfig.Obj.BASIC_HELM, null, 40, 1, 20),
            armourRecipe(CorruptedGauntletConfig.Obj.BASIC_BODY, null, 40, 1, 20),
            armourRecipe(CorruptedGauntletConfig.Obj.BASIC_LEGS, null, 40, 1, 20),
            armourRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_HELM,
                CorruptedGauntletConfig.Obj.BASIC_HELM,
                60,
                1,
                30,
            ),
            armourRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_BODY,
                CorruptedGauntletConfig.Obj.BASIC_BODY,
                60,
                2,
                30,
            ),
            armourRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_LEGS,
                CorruptedGauntletConfig.Obj.BASIC_LEGS,
                60,
                1,
                30,
            ),
            armourRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_HELM,
                CorruptedGauntletConfig.Obj.ATTUNED_HELM,
                80,
                2,
                80,
            ),
            armourRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_BODY,
                CorruptedGauntletConfig.Obj.ATTUNED_BODY,
                80,
                2,
                80,
            ),
            armourRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_LEGS,
                CorruptedGauntletConfig.Obj.ATTUNED_LEGS,
                80,
                2,
                80,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.BASIC_HALBERD,
                CorruptedGauntletConfig.Obj.WEAPON_FRAME,
                20,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.BASIC_STAFF,
                CorruptedGauntletConfig.Obj.WEAPON_FRAME,
                20,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.BASIC_BOW,
                CorruptedGauntletConfig.Obj.WEAPON_FRAME,
                20,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_HALBERD,
                CorruptedGauntletConfig.Obj.BASIC_HALBERD,
                60,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_STAFF,
                CorruptedGauntletConfig.Obj.BASIC_STAFF,
                60,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.ATTUNED_BOW,
                CorruptedGauntletConfig.Obj.BASIC_BOW,
                60,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_HALBERD,
                CorruptedGauntletConfig.Obj.ATTUNED_HALBERD,
                0,
                CorruptedGauntletConfig.Obj.SPIKE,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_STAFF,
                CorruptedGauntletConfig.Obj.ATTUNED_STAFF,
                0,
                CorruptedGauntletConfig.Obj.ORB,
            ),
            weaponRecipe(
                CorruptedGauntletConfig.Obj.PERFECTED_BOW,
                CorruptedGauntletConfig.Obj.ATTUNED_BOW,
                0,
                CorruptedGauntletConfig.Obj.BOWSTRING,
            ),
            CorruptedGauntletRecipe(
                CorruptedGauntletConfig.Obj.CRYSTAL_PADDLEFISH,
                10,
                listOf(ingredient(CorruptedGauntletConfig.Obj.PADDLEFISH)),
                experience = 30,
            ),
            CorruptedGauntletRecipe(
                CorruptedGauntletConfig.Obj.ESCAPE_CRYSTAL,
                200,
                experience = 200,
            ),
        )

    val byOutput = recipes.associateBy(CorruptedGauntletRecipe::outputItemId)

    fun canCraft(recipe: CorruptedGauntletRecipe, inventory: Map<Int, Int>): Boolean =
        inventory.getOrDefault(CorruptedGauntletConfig.Obj.SHARDS, 0) >= recipe.shardCost &&
            recipe.ingredients.all { inventory.getOrDefault(it.itemId, 0) >= it.amount }
}

internal object CorruptedGauntletRewardRules {
    fun eligibleForCompletionRewards(
        hunllefKilled: Boolean,
        playerDied: Boolean,
        damageDealt: Int,
    ): Boolean = hunllefKilled && !playerDied && damageDealt > 0

    fun uniqueAwarded(reward: CorruptedGauntletUniqueReward, zeroBasedRoll: Int): Boolean {
        require(zeroBasedRoll >= 0)
        return zeroBasedRoll % reward.denominator == 0
    }

    fun completionShards(zeroBasedRoll: Int): Int {
        require(zeroBasedRoll >= 0)
        val range =
            CorruptedGauntletConfig.COMPLETION_SHARD_MAX -
                CorruptedGauntletConfig.COMPLETION_SHARD_MIN + 1
        return CorruptedGauntletConfig.COMPLETION_SHARD_MIN + zeroBasedRoll % range
    }

    /** A chest token may be consumed only once; clearing it precedes item delivery at runtime. */
    fun canClaim(pendingRewardToken: Long?, consumedRewardTokens: Set<Long>): Boolean =
        pendingRewardToken != null && pendingRewardToken !in consumedRewardTokens
}
