package org.rsmod.content.minigames.corruptedgauntlet

internal enum class CorruptedGauntletStage {
    PREPARATION,
    HUNLLEF,
    COMPLETED,
    FAILED,
}

internal enum class CorruptedGauntletCombatStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal enum class CorruptedGauntletDemiBoss {
    BEAR,
    DRAGON,
    DARK_BEAST,
}

internal enum class CorruptedGauntletResource(
    val nodeId: Int,
    val depletedNodeId: Int,
    val itemId: Int,
    val yield: Int,
) {
    ORE(
        CorruptedGauntletConfig.Loc.ORE,
        CorruptedGauntletConfig.Loc.DEPLETED_ORE,
        CorruptedGauntletConfig.Obj.ORE,
        3,
    ),
    BARK(
        CorruptedGauntletConfig.Loc.ROOTS,
        CorruptedGauntletConfig.Loc.DEPLETED_ROOTS,
        CorruptedGauntletConfig.Obj.BARK,
        3,
    ),
    FISH(
        CorruptedGauntletConfig.Loc.FISHING_SPOT,
        CorruptedGauntletConfig.Loc.DEPLETED_FISHING_SPOT,
        CorruptedGauntletConfig.Obj.RAW_PADDLEFISH,
        4,
    ),
    GRYM(
        CorruptedGauntletConfig.Loc.GRYM,
        CorruptedGauntletConfig.Loc.DEPLETED_GRYM,
        CorruptedGauntletConfig.Obj.GRYM_LEAF,
        1,
    ),
    LINUM(
        CorruptedGauntletConfig.Loc.LINUM,
        CorruptedGauntletConfig.Loc.DEPLETED_LINUM,
        CorruptedGauntletConfig.Obj.LINUM,
        3,
    ),
}

internal enum class CorruptedGauntletMonsterTier {
    WEAK,
    STRONG,
    DEMI_BOSS,
}

internal enum class CorruptedGauntletRoomKind {
    START,
    BOSS,
    RESOURCE,
    COMBAT,
    DEMI_BOSS,
}

internal enum class CorruptedGauntletRoomShape {
    MIDDLE,
    EDGE,
    CORNER,
}

internal data class CorruptedGauntletGridPoint(val x: Int, val z: Int) {
    init {
        require(x in 0 until CorruptedGauntletConfig.GRID_SIZE)
        require(z in 0 until CorruptedGauntletConfig.GRID_SIZE)
    }

    fun manhattanDistance(other: CorruptedGauntletGridPoint): Int =
        kotlin.math.abs(x - other.x) + kotlin.math.abs(z - other.z)
}

internal data class CorruptedGauntletRoom(
    val grid: CorruptedGauntletGridPoint,
    val kind: CorruptedGauntletRoomKind,
    val shape: CorruptedGauntletRoomShape,
    val sourceChunkX: Int,
    val sourceChunkZ: Int,
    val rotation: Int,
    val demiBoss: CorruptedGauntletDemiBoss? = null,
    val guaranteedFishNodes: Int = 0,
) {
    init {
        require(rotation in 0..3)
        require((kind == CorruptedGauntletRoomKind.DEMI_BOSS) == (demiBoss != null))
        require(guaranteedFishNodes >= 0)
    }
}

internal data class CorruptedGauntletLayout(
    val seed: Long,
    val start: CorruptedGauntletGridPoint,
    val rooms: List<CorruptedGauntletRoom>,
) {
    init {
        require(rooms.size == CorruptedGauntletConfig.GRID_SIZE * CorruptedGauntletConfig.GRID_SIZE)
        require(rooms.map { it.grid }.toSet().size == rooms.size)
        require(rooms.count { it.kind == CorruptedGauntletRoomKind.START } == 1)
        require(rooms.count { it.kind == CorruptedGauntletRoomKind.BOSS } == 1)
    }

    val boss: CorruptedGauntletGridPoint = CorruptedGauntletGridPoint(3, 3)

    operator fun get(point: CorruptedGauntletGridPoint): CorruptedGauntletRoom =
        rooms.first { it.grid == point }

    fun room(x: Int, z: Int): CorruptedGauntletRoom = get(CorruptedGauntletGridPoint(x, z))
}

internal data class CorruptedGauntletHunllefState(
    val outgoingStyle: CorruptedGauntletCombatStyle = CorruptedGauntletCombatStyle.RANGED,
    val attacksInStyle: Int = 0,
    val attacksSinceTornadoes: Int = 0,
    val protectionStyle: CorruptedGauntletCombatStyle = CorruptedGauntletCombatStyle.MELEE,
    val validHitsTowardProtectionSwitch: Int = 0,
)

internal enum class CorruptedGauntletHunllefPhase {
    FIRST,
    SECOND,
    FINAL,
}

internal data class CorruptedGauntletFloorSchedule(
    val warningTicks: Int,
    val activeTicks: Int,
    val cadenceTicks: Int,
) {
    init {
        require(warningTicks > 0)
        require(activeTicks > 0)
        require(cadenceTicks >= warningTicks + activeTicks) {
            "A new floor pattern cannot begin before the previous pattern expires."
        }
    }

    val lifetimeTicks: Int = warningTicks + activeTicks
}

internal data class CorruptedGauntletTornadoDamage(val minimum: Int, val maximum: Int) {
    init {
        require(minimum >= 0)
        require(maximum >= minimum)
    }
}

internal data class CorruptedGauntletIngredient(val itemId: Int, val amount: Int) {
    init {
        require(amount > 0)
    }
}

internal data class CorruptedGauntletRecipe(
    val outputItemId: Int,
    val shardCost: Int,
    val ingredients: List<CorruptedGauntletIngredient> = emptyList(),
    val experience: Int,
) {
    init {
        require(shardCost >= 0)
        require(experience >= 0)
    }
}

internal enum class CorruptedGauntletUniqueReward(val itemId: Int, val denominator: Int) {
    CRYSTAL_WEAPON_SEED(4_207, 50),
    CRYSTAL_ARMOUR_SEED(23_956, 50),
    ENHANCED_CRYSTAL_WEAPON_SEED(25_859, 400),
    YOUNGLLEF(23_757, 800),
    ELITE_CLUE_SCROLL(12_073, 20),
}
