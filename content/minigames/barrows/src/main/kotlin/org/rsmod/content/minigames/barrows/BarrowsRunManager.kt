package org.rsmod.content.minigames.barrows

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class BarrowsRunManager
@Inject
constructor(
    private val mapClock: MapClock,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
) {
    private val logger = InlineLogger()
    private val ownedBrothers = mutableMapOf<PlayerUid, MutableMap<BarrowsBrother, Npc>>()
    private val ownedTunnelMonsters = mutableMapOf<PlayerUid, MutableSet<Npc>>()

    fun isRunActive(player: Player): Boolean = player.attr[RUN_ACTIVE] == true

    fun beginRun(player: Player) {
        if (isRunActive(player)) {
            return
        }

        resetClientState(player)
        player.attr[RUN_ACTIVE] = true
        player.attr[TUNNEL_BROTHER] = random.of(BarrowsBrother.entries.size)
        player.attr[TUNNEL_ENTERED] = false
        player.attr[CHEST_CLAIMED] = false
        notifyLootPotential(player)
        logger.info {
            "Barrows run started: player=" + player.displayName + ", tunnel=" + tunnelBrother(player)?.displayName
        }
    }

    fun tunnelBrother(player: Player): BarrowsBrother? {
        if (!isRunActive(player)) {
            return null
        }
        return player.attr[TUNNEL_BROTHER]?.let(BarrowsBrother.entries::getOrNull)
    }

    fun isTunnelEntered(player: Player): Boolean = player.attr[TUNNEL_ENTERED] == true

    fun ensureTunnelBrotherAtChest(player: Player) {
        if (!isRunActive(player) || !isTunnelEntered(player)) {
            return
        }

        val coords = player.coords
        if (
            coords.level != BARROWS_TUNNEL_LEVEL ||
            coords.x !in CHEST_ROOM_MIN_X..CHEST_ROOM_MAX_X ||
            coords.z !in CHEST_ROOM_MIN_Z..CHEST_ROOM_MAX_Z
        ) {
            return
        }

        val brother = tunnelBrother(player) ?: return
        if (isBrotherKilled(player, brother)) {
            return
        }
        if (spawnBrother(player, brother, CoordGrid(3553, 9694))) {
            player.mes(brother.displayName + " emerges to defend the chest!")
        }
    }

    fun markTunnelEntered(player: Player, brother: BarrowsBrother) {
        player.attr[TUNNEL_ENTERED] = true
        logger.info {
            "Barrows tunnel entered: player=" + player.displayName + ", tunnelBrother=" + brother.displayName
        }
    }

    fun isBrotherKilled(player: Player, brother: BarrowsBrother): Boolean =
        isRunActive(player) && player.vars[brother.killedVarbit] != 0

    fun areAllBrothersKilled(player: Player): Boolean =
        isRunActive(player) && BarrowsBrother.entries.all { isBrotherKilled(player, it) }

    fun areAllSurfaceBrothersKilled(player: Player): Boolean {
        val tunnel = tunnelBrother(player) ?: return false
        return BarrowsBrother.entries
            .filter { it != tunnel }
            .all { isBrotherKilled(player, it) }
    }

    fun isBrotherSpawned(player: Player, brother: BarrowsBrother): Boolean =
        ownedBrothers[player.uid]?.get(brother)?.isVisible == true

    fun spawnBrother(player: Player, brother: BarrowsBrother, coords: CoordGrid): Boolean {
        if (!isRunActive(player) || isBrotherKilled(player, brother)) {
            return false
        }

        val owned = ownedBrothers.getOrPut(player.uid) { mutableMapOf() }
        val existing = owned[brother]
        if (existing?.isVisible == true) {
            return false
        }
        if (existing != null) {
            owned.remove(brother)
        }

        val type =
            ServerCacheManager.getNpc(brother.npc.asRSCM(RSCMType.NPC))
                ?: error("Missing Barrows brother NPC: " + brother.npc)
        val npc = Npc(type, coords)
        npc.mode = type.defaultMode
        npc.apRangeOverride = brother.combatAttackRange
        npc.assignSpawnOwner(player, mapClock.cycle)
        npcRepo.add(npc, BROTHER_DESPAWN_TICKS)
        owned[brother] = npc

        logger.info {
            "Barrows brother spawned: player=" + player.displayName + ", brother=" + brother.displayName + ", coords=" + coords
        }
        return true
    }

    fun spawnCryptRat(player: Player): Boolean {
        if (!isRunActive(player) || !isTunnelEntered(player)) {
            return false
        }

        if (killedCryptRatCount(player) >= MAX_CRYPT_RATS) {
            return false
        }

        val owned = ownedTunnelMonsters.getOrPut(player.uid) { mutableSetOf() }
        if (owned.any { it.isVisible }) {
            return false
        }

        val type =
            ServerCacheManager.getNpc("npc.barrows_rat".asRSCM(RSCMType.NPC))
                ?: error("Missing Barrows crypt rat NPC: npc.barrows_rat")
        val npc = Npc(type, player.coords)
        npc.mode = type.defaultMode
        npc.assignSpawnOwner(player, mapClock.cycle)
        npcRepo.add(npc, TUNNEL_MONSTER_DESPAWN_TICKS)
        owned += npc

        logger.info {
            "Barrows crypt rat spawned: player=" + player.displayName + ", coords=" + player.coords
        }
        return true
    }

    fun spawnChestRoomCryptRats(player: Player): Int {
        if (!isRunActive(player) || !isTunnelEntered(player)) {
            return 0
        }

        val remaining =
            (MAX_CRYPT_RATS - killedCryptRatCount(player)).coerceAtMost(CHEST_ROOM_CRYPT_RAT_SPAWNS.size)
        if (remaining == 0) {
            return 0
        }

        val owned = ownedTunnelMonsters.getOrPut(player.uid) { mutableSetOf() }
        if (owned.any { it.isVisible }) {
            return 0
        }

        val type =
            ServerCacheManager.getNpc("npc.barrows_rat".asRSCM(RSCMType.NPC))
                ?: error("Missing Barrows crypt rat NPC: npc.barrows_rat")

        CHEST_ROOM_CRYPT_RAT_SPAWNS.take(remaining).forEach { coords ->
            val npc = Npc(type, coords)
            npc.mode = NpcMode.None
            npc.assignSpawnOwner(player, mapClock.cycle)
            npcRepo.add(npc, TUNNEL_MONSTER_DESPAWN_TICKS)
            owned += npc
        }

        logger.info {
            "Barrows chest-room crypt rats spawned: player=" + player.displayName + ", count=" + remaining
        }
        return remaining
    }

    fun isOwnedBrother(npc: Npc): Boolean =
        ownedBrothers.values.any { owned -> owned.values.any { it === npc } }

    fun isOwnedBrother(player: Player, npc: Npc): Boolean =
        npc.isSpawnOwnedBy(player) && ownedBrothers[player.uid]?.values?.any { it === npc } == true

    fun isOwnedBarrowsNpc(npc: Npc): Boolean =
        isOwnedBrother(npc) || ownedTunnelMonsters.values.any { owned -> owned.any { it === npc } }

    fun isOwnedBarrowsNpc(player: Player, npc: Npc): Boolean =
        isOwnedBrother(player, npc) ||
            (npc.isSpawnOwnedBy(player) && ownedTunnelMonsters[player.uid]?.any { it === npc } == true)

    fun handleKill(player: Player, npc: Npc) {
        if (handleTunnelMonsterKill(player, npc)) {
            return
        }

        val brother =
            BarrowsBrother.entries.firstOrNull {
                npc.id == it.npc.asRSCM(RSCMType.NPC)
            } ?: return
        val owned = ownedBrothers[player.uid] ?: return
        if (owned[brother] !== npc || !npc.isSpawnOwnedBy(player) || !isRunActive(player)) {
            return
        }

        owned.remove(brother)
        if (owned.isEmpty()) {
            ownedBrothers.remove(player.uid)
        }
        if (isBrotherKilled(player, brother)) {
            return
        }

        VarPlayerIntMapSetter.set(player, brother.killedVarbit, 1)
        VarPlayerIntMapSetter.set(player, KILLED_BROTHERS, killedBrotherCount(player))
        player.mes(brother.displayName + " has been defeated.")
        notifyLootPotential(player)
        logger.info {
            "Barrows brother defeated: player=" + player.displayName + ", brother=" + brother.displayName + ", kills=" + killedBrotherCount(player)
        }
    }

    private fun handleTunnelMonsterKill(player: Player, npc: Npc): Boolean {
        val owned = ownedTunnelMonsters[player.uid] ?: return false
        if (owned.none { it === npc } || !npc.isSpawnOwnedBy(player) || !isRunActive(player)) {
            return false
        }

        owned.remove(npc)
        if (owned.isEmpty()) {
            ownedTunnelMonsters.remove(player.uid)
        }

        val cryptRatKills = (killedCryptRatCount(player) + 1).coerceAtMost(MAX_CRYPT_RATS)
        VarPlayerIntMapSetter.set(player, KILLED_CRYPT_MONSTERS, cryptRatKills)
        notifyLootPotential(player)
        logger.info {
            "Barrows crypt rat defeated: player=" + player.displayName + ", lootPotential=" + lootPotentialPercent(player)
        }
        return true
    }

    fun canOpenChest(player: Player): Boolean =
        isRunActive(player) &&
            !isChestClaimed(player) &&
            isTunnelEntered(player) &&
            areAllBrothersKilled(player)

    fun openChest(player: Player, chest: CoordGrid): List<BarrowsReward> {
        check(canOpenChest(player)) { "Attempted to open a Barrows chest before its run was complete." }
        player.attr[CHEST_CLAIMED] = true

        val rewards = rollRewards(player)
        rewards.forEach { reward ->
            player.invAddOrDrop(objRepo, reward.obj, reward.count, coords = chest)
        }
        val chestCount = player.vars[TOTAL_CHESTS] + 1
        VarPlayerIntMapSetter.set(player, TOTAL_CHESTS, chestCount)
        collectionLog.recordBatch(
            player = player,
            awards = rewards.map { reward -> reward.obj.asRSCM(RSCMType.OBJ) to reward.count },
            source =
                CollectionLogAwardSource(
                    CollectionLogAwardKind.BossChest,
                    "barrows",
                ),
            eventId = "${player.uid}:$chestCount",
        )
        player.mes("You find treasure in the Barrows chest.")
        logger.info {
            "Barrows chest opened: player=" + player.displayName + ", rewards=" +
                rewards.joinToString { it.obj + "x" + it.count }
        }
        completeRun(player)
        return rewards
    }

    fun cleanupSpawnedBrothers(player: Player) {
        val brothers = ownedBrothers.remove(player.uid)
        val monsters = ownedTunnelMonsters.remove(player.uid)
        for (npc in brothers?.values.orEmpty()) {
            if (npc.isVisible) {
                npcRepo.del(npc, duration = 0)
            }
        }
        for (npc in monsters.orEmpty()) {
            if (npc.isVisible) {
                npcRepo.del(npc, duration = 0)
            }
        }
        if (brothers != null || monsters != null) {
            logger.info { "Barrows temporary spawns cleared: player=" + player.displayName }
        }
    }

    fun tickOwnedBrothers(player: Player) {
        val owned = ownedBrothers[player.uid] ?: return
        val playerInsideBarrows = isInBarrowsUnderground(player.coords)
        val iterator = owned.iterator()
        var cleared = false
        while (iterator.hasNext()) {
            val (_, npc) = iterator.next()
            val tooFar = chebyshevDistance(player.coords, npc.coords) > OWNER_VICINITY_RADIUS
            if (!npc.isVisible || !playerInsideBarrows || tooFar) {
                if (npc.isVisible) {
                    npcRepo.del(npc, duration = 0)
                }
                iterator.remove()
                cleared = true
            }
        }
        if (owned.isEmpty()) {
            ownedBrothers.remove(player.uid)
        }
        if (cleared) {
            logger.info { "Barrows temporary brother despawned after owner left its area: player=" + player.displayName }
        }
    }

    fun tickOwnedTunnelMonsters(player: Player) {
        val owned = ownedTunnelMonsters[player.uid] ?: return
        val playerInsideBarrows = isInBarrowsUnderground(player.coords)
        val iterator = owned.iterator()
        var cleared = false
        while (iterator.hasNext()) {
            val npc = iterator.next()
            val tooFar = chebyshevDistance(player.coords, npc.coords) > OWNER_VICINITY_RADIUS
            if (!npc.isVisible || !playerInsideBarrows || tooFar) {
                if (npc.isVisible) {
                    npcRepo.del(npc, duration = 0)
                }
                iterator.remove()
                cleared = true
            }
        }
        if (owned.isEmpty()) {
            ownedTunnelMonsters.remove(player.uid)
        }
        if (cleared) {
            logger.info { "Barrows tunnel monster despawned after owner left its area: player=" + player.displayName }
        }
    }

    private fun completeRun(player: Player) {
        cleanupSpawnedBrothers(player)
        resetClientState(player)
        player.attr.remove(RUN_ACTIVE)
        player.attr.remove(TUNNEL_BROTHER)
        player.attr.remove(TUNNEL_ENTERED)
        player.attr.remove(CHEST_CLAIMED)
        logger.info { "Barrows run reset: player=" + player.displayName }
    }

    private fun rollRewards(player: Player): List<BarrowsReward> {
        val killed = BarrowsBrother.entries.filter { isBrotherKilled(player, it) }
        val candidates = killed.flatMap { it.rewards }.toMutableList()
        val rewards = mutableListOf<BarrowsReward>()
        val pieceRoll = random.of(PIECE_ROLL_DENOMINATOR)
        val pieceCount =
            when {
                pieceRoll < THREE_PIECE_CHEST_WEIGHT -> 3
                pieceRoll < THREE_PIECE_CHEST_WEIGHT + TWO_PIECE_CHEST_WEIGHT -> 2
                pieceRoll < THREE_PIECE_CHEST_WEIGHT + TWO_PIECE_CHEST_WEIGHT + ONE_PIECE_CHEST_WEIGHT -> 1
                else -> 0
            }
        val lootPotential = lootPotentialPercent(player)
        val bonusCommonRewards = lootPotential / POTENTIAL_PERCENT_PER_BONUS_REWARD

        repeat(pieceCount) {
            rewards += BarrowsReward(candidates.removeAt(random.of(candidates.size)), 1)
        }
        if (random.of(HARD_CLUE_ROLL_DENOMINATOR) == 0) {
            rewards += BarrowsReward(HARD_CLUE_SCROLL, 1)
        }
        repeat(COMMON_REWARD_ROLLS + bonusCommonRewards) {
            rewards += rollCommonReward(lootPotential)
        }
        return rewards
    }

    fun previewFullChestRewards(chestCount: Int): List<List<BarrowsReward>> {
        require(chestCount > 0) { "Chest count must be positive." }
        val fullChestCandidates = BarrowsBrother.entries.flatMap { it.rewards }
        return List(chestCount) {
            val candidates = fullChestCandidates.toMutableList()
            val rewards = mutableListOf<BarrowsReward>()
            val pieceRoll = random.of(PIECE_ROLL_DENOMINATOR)
            val pieceCount =
                when {
                    pieceRoll < THREE_PIECE_CHEST_WEIGHT -> 3
                    pieceRoll < THREE_PIECE_CHEST_WEIGHT + TWO_PIECE_CHEST_WEIGHT -> 2
                    pieceRoll < THREE_PIECE_CHEST_WEIGHT + TWO_PIECE_CHEST_WEIGHT + ONE_PIECE_CHEST_WEIGHT -> 1
                    else -> 0
                }

            repeat(pieceCount) {
                rewards += BarrowsReward(candidates.removeAt(random.of(candidates.size)), 1)
            }
            if (random.of(HARD_CLUE_ROLL_DENOMINATOR) == 0) {
                rewards += BarrowsReward(HARD_CLUE_SCROLL, 1)
            }
            repeat(COMMON_REWARD_ROLLS + MAX_LOOT_POTENTIAL / POTENTIAL_PERCENT_PER_BONUS_REWARD) {
                rewards += rollCommonReward(MAX_LOOT_POTENTIAL)
            }
            rewards
        }
    }
    private fun rollCommonReward(lootPotential: Int): BarrowsReward {
        val base =
            when (random.of(5)) {
                0 -> BarrowsReward("obj.mindrune", random.of(120, 260))
                1 -> BarrowsReward("obj.chaosrune", random.of(60, 140))
                2 -> BarrowsReward("obj.deathrune", random.of(25, 80))
                3 -> BarrowsReward("obj.bloodrune", random.of(20, 60))
                else -> BarrowsReward("obj.coins", random.of(2_000, 6_000))
            }
        val bonus = base.count * lootPotential / MAX_LOOT_POTENTIAL
        return base.copy(count = base.count + bonus)
    }

    private fun resetClientState(player: Player) {
        BarrowsBrother.entries.forEach { brother ->
            VarPlayerIntMapSetter.set(player, brother.killedVarbit, 0)
        }
        VarPlayerIntMapSetter.set(player, KILLED_BROTHERS, 0)
        VarPlayerIntMapSetter.set(player, KILLED_CRYPT_MONSTERS, 0)
        VarPlayerIntMapSetter.set(player, CHEST_OPEN, 0)
    }

    private fun killedBrotherCount(player: Player): Int =
        BarrowsBrother.entries.count { isBrotherKilled(player, it) }

    private fun lootPotentialPercent(player: Player): Int =
        ((potentialBrotherKillCount(player) + killedCryptRatCount(player)) * POTENTIAL_PERCENT_PER_KILL)
            .coerceAtMost(MAX_LOOT_POTENTIAL)

    private fun potentialBrotherKillCount(player: Player): Int =
        BarrowsBrother.entries.count { brother ->
            brother != tunnelBrother(player) && isBrotherKilled(player, brother)
        }.coerceAtMost(MAX_POTENTIAL_BROTHERS)

    private fun killedCryptRatCount(player: Player): Int =
        player.vars[KILLED_CRYPT_MONSTERS].coerceIn(0, MAX_CRYPT_RATS)

    private fun notifyLootPotential(player: Player) {
        val brotherKills = killedBrotherCount(player)
        val cryptRatKills = killedCryptRatCount(player)
        val potential = lootPotentialPercent(player)
        player.mes(
            "Barrows loot potential: <col=00ff00>$potential%</col> " +
                "($brotherKills/${BarrowsBrother.entries.size} brothers, " +
                "$cryptRatKills/$MAX_CRYPT_RATS crypt rats).",
        )
    }

    private fun isChestClaimed(player: Player): Boolean = player.attr[CHEST_CLAIMED] == true

    private fun isInBarrowsUnderground(coords: CoordGrid): Boolean =
        coords.level in BARROWS_TUNNEL_LEVEL..BARROWS_CRYPT_LEVEL &&
            coords.x in BARROWS_MIN_X..BARROWS_MAX_X &&
            coords.z in BARROWS_MIN_Z..BARROWS_MAX_Z

    private fun chebyshevDistance(first: CoordGrid, second: CoordGrid): Int =
        maxOf(kotlin.math.abs(first.x - second.x), kotlin.math.abs(first.z - second.z))

    private companion object {
        private const val BROTHER_DESPAWN_TICKS = 1_800
        private const val TUNNEL_MONSTER_DESPAWN_TICKS = 300
        private const val OWNER_VICINITY_RADIUS = 32
        private const val COMMON_REWARD_ROLLS = 2
        private const val MAX_POTENTIAL_BROTHERS = 5
        private const val MAX_CRYPT_RATS = 12
        private const val MAX_LOOT_POTENTIAL = 100
        private const val POTENTIAL_PERCENT_PER_KILL = 10
        private const val POTENTIAL_PERCENT_PER_BONUS_REWARD = 20

        private val CHEST_ROOM_CRYPT_RAT_SPAWNS =
            listOf(
                CoordGrid(3549, 9693),
                CoordGrid(3550, 9695),
                CoordGrid(3552, 9695),
                CoordGrid(3553, 9693),
                CoordGrid(3551, 9696),
                CoordGrid(3550, 9693),
                CoordGrid(3551, 9693),
                CoordGrid(3552, 9693),
                CoordGrid(3554, 9693),
                CoordGrid(3549, 9694),
                CoordGrid(3550, 9694),
                CoordGrid(3551, 9694),
            )

        private val RUN_ACTIVE = AttributeKey<Boolean>(persistenceKey = "barrows_run_active")
        private val TUNNEL_BROTHER = AttributeKey<Int>(persistenceKey = "barrows_tunnel_brother")
        private val TUNNEL_ENTERED = AttributeKey<Boolean>(persistenceKey = "barrows_tunnel_entered")
        private val CHEST_CLAIMED = AttributeKey<Boolean>(persistenceKey = "barrows_chest_claimed")

        private const val KILLED_BROTHERS = "varbit.barrows_killed_count"
        private const val KILLED_CRYPT_MONSTERS = "varbit.barrows_killed_monster"
        private const val CHEST_OPEN = "varbit.barrows_chest_open"
        private const val TOTAL_CHESTS = "varp.total_barrows_chests"

        private const val BARROWS_TUNNEL_LEVEL = 0
        private const val BARROWS_CRYPT_LEVEL = 3
        private const val BARROWS_MIN_X = 3520
        private const val BARROWS_MAX_X = 3583
        private const val BARROWS_MIN_Z = 9664
        private const val BARROWS_MAX_Z = 9727
        private const val CHEST_ROOM_MIN_X = 3548
        private const val CHEST_ROOM_MAX_X = 3554
        private const val CHEST_ROOM_MIN_Z = 9692
        private const val CHEST_ROOM_MAX_Z = 9697

        private const val PIECE_ROLL_DENOMINATOR = 20
        private const val THREE_PIECE_CHEST_WEIGHT = 1
        private const val TWO_PIECE_CHEST_WEIGHT = 2
        private const val ONE_PIECE_CHEST_WEIGHT = 4
        private const val HARD_CLUE_ROLL_DENOMINATOR = 15
        private const val HARD_CLUE_SCROLL = "obj.trail_clue_hard_map001"
    }
}
