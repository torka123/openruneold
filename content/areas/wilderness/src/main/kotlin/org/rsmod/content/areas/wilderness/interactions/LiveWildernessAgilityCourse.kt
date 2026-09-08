package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import dev.openrune.types.BasType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.drops.ClueScrollTier
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.ownsClueScroll
import org.rsmod.content.drops.ownsLootingBag
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isAnyType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public const val WILDERNESS_AGILITY_STAGE_VARP: String = "varp.wilderness_agility_course_stage"
public const val WILDERNESS_AGILITY_UNCLAIMED_VARP: String = "varp.wilderness_agility_unclaimed_lap"
public const val WILDERNESS_AGILITY_PAID_VARP: String = "varp.wilderness_agility_paid_active"
public const val WILDERNESS_AGILITY_STREAK_VARP: String = "varp.wilderness_agility_paid_streak"

/** Donor success sequences plus cache-verified failure sequences for course thirteen. */
internal object WildernessAgilitySequences {
    const val BALANCE_WALK: Int = 762
    const val BALANCE_READY: Int = 763
    const val PIPE_ENTER: Int = 749
    const val PIPE_CRAWL: Int = 749
    const val PIPE_EXIT: Int = 749
    const val ROPE: Int = 751
    const val ROPE_MISS: Int = 1105
    const val STEPPING_STONE: Int = 741
    const val LOG_STUMBLE: Int = 764
    const val ROCK_CLIMB: Int = 740
}

/**
 * Pure rules are kept separate from packet handlers so boundary behavior is deterministic in tests.
 */
public object WildernessAgilityRules {
    public const val PIPE_XP: Double = 12.5
    public const val ROPE_XP: Double = 20.0
    public const val STONES_XP: Double = 20.0
    public const val LOG_XP: Double = 20.0
    public const val LAP_XP: Double = 498.9
    public const val FULL_LAP_XP: Double = 571.4
    public val ENTRANCE_SOUTH_APPROACH_TILE: CoordGrid = CoordGrid(2998, 3916)
    public val ENTRANCE_NORTH_APPROACH_TILE: CoordGrid = CoordGrid(2998, 3932)
    public val LOG_APPROACH_TILE: CoordGrid = CoordGrid(3002, 3945)
    public val LOG_EXIT_TILE: CoordGrid = CoordGrid(2995, 3945)
    public const val PAYMENT: Int = 150_000

    public enum class Obstacle(public val expectedStage: Int, public val nextStage: Int) {
        Pipe(0, 1),
        Rope(1, 2),
        Stones(2, 3),
        Log(3, 4),
        Rocks(4, 0),
    }

    public enum class RewardTier {
        One,
        Two,
        Three,
        Four,
    }

    public fun canEnter(effectiveAgility: Int): Boolean = effectiveAgility >= 52

    public fun canUsePipe(effectiveAgility: Int): Boolean = effectiveAgility >= 49

    public fun ordered(stage: Int, obstacle: Obstacle): Boolean = stage == obstacle.expectedStage

    public fun advance(stage: Int, obstacle: Obstacle, success: Boolean): Int =
        if (success && ordered(stage, obstacle)) obstacle.nextStage else stage

    public fun failureDamage(currentHitpoints: Int, percent: Int): Int =
        (max(0, currentHitpoints) * percent / 100) + 1

    /**
     * Provisional 0..255 curve retaining the documented failure tail at level 99. Exact server-side
     * Wilderness obstacle thresholds are not encoded in the revision-240 client cache.
     */
    public fun successThreshold(effectiveLevel: Int): Int {
        val level = effectiveLevel.coerceIn(49, 99)
        return 152 + ((level - 49) * 98 / 50)
    }

    public fun succeeds(effectiveLevel: Int, roll: Int): Boolean {
        require(roll in 0..255)
        return roll < successThreshold(effectiveLevel)
    }

    public fun rewardTier(streak: Int): RewardTier =
        when (streak) {
            in Int.MIN_VALUE..15 -> RewardTier.One
            in 16..30 -> RewardTier.Two
            in 31..60 -> RewardTier.Three
            else -> RewardTier.Four
        }

    public fun ticketXpEach(count: Int): Int =
        when (count) {
            in 1..10 -> 200
            in 11..50 -> 210
            in 51..100 -> 220
            else -> if (count > 100) 230 else 0
        }

    public fun ticketXp(count: Int): Double =
        count.toLong().coerceAtLeast(0L).toDouble() * ticketXpEach(count)

    public fun logoutStreak(streak: Int): Int = max(0, streak - 10)

    public fun isCourseArea(coords: CoordGrid): Boolean {
        if (coords.level != 0) return false
        val surface = coords.x in 2988..3008 && coords.z in 3932..3964
        val dungeon = coords.x in 2990..3008 && coords.z in 10339..10364
        return surface || dungeon
    }
}

/** The single authoritative, coordinate-scoped Wilderness Agility implementation. */
public class WildernessAgilityCourse
@Inject
constructor(private val random: GameRandom, private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        registerLoc(23554, CoordGrid(2997, 3931), 1) { exitCourse() }
        registerLoc(23552, CoordGrid(2998, 3931), 1) { exitCourse() }
        registerLoc(23555, CoordGrid(2998, 3917), 1) { enterCourse() }
        registerLoc(23137, CoordGrid(3004, 3938), 1) { usePipe(forward = true) }
        registerLoc(23137, CoordGrid(3004, 3948), 1) { usePipe(forward = false) }
        registerLoc(23132, CoordGrid(3005, 3952), 1) { useRopeSwing() }
        registerLoc(23556, CoordGrid(3001, 3960), 1) { useSteppingStones() }
        registerLoc(23542, CoordGrid(3001, 3945), 1) { useLogBalance() }
        for (x in 2993..2995) {
            registerLoc(23640, CoordGrid(x, 3936), 1) { useClimbingRocks(x) }
        }
        registerLoc(53224, CoordGrid(3005, 3936), 1) { tagDispenser() }
        registerLoc(53224, CoordGrid(3005, 3936), 2) { payDispenser() }
        registerLoc(53224, CoordGrid(3005, 3936), 3) { redeemTickets() }

        onOpHeld1(CLOSED_LOOTING_BAG) { openLootingBag() }
        onOpHeld1(OPEN_LOOTING_BAG) { closeLootingBag() }
        onOpHeld2(CLOSED_LOOTING_BAG) { checkLootingBag() }
        onOpHeld2(OPEN_LOOTING_BAG) { checkLootingBag() }

        onPlayerLogin {
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STAGE_VARP, 0)
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 0)
            if (!WildernessAgilityRules.isCourseArea(player.coords)) {
                player.clearPaidCourseState()
            }
        }
        onPlayerLogout {
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STAGE_VARP, 0)
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 0)
            if (
                WildernessAgilityRules.isCourseArea(player.coords) &&
                    player.vars[WILDERNESS_AGILITY_PAID_VARP] != 0
            ) {
                VarPlayerIntMapSetter.set(
                    player,
                    WILDERNESS_AGILITY_STREAK_VARP,
                    WildernessAgilityRules.logoutStreak(player.vars[WILDERNESS_AGILITY_STREAK_VARP]),
                )
            } else {
                player.clearPaidCourseState()
            }
        }
        onPlayerCoordsChanged {
            val wasInside = WildernessAgilityRules.isCourseArea(lastKnownCoords)
            val isInside = WildernessAgilityRules.isCourseArea(player.coords)
            if (wasInside && !isInside) {
                player.clearAllCourseState()
            }
        }
    }

    private fun ScriptContext.registerLoc(
        locId: Int,
        source: CoordGrid,
        option: Int,
        action: suspend ProtectedAccess.() -> Unit,
    ) {
        validateLocOption(locId, option)
        val key = LocCoordEvents.key(locId, source)
        when (option) {
            1 -> onProtectedEvent<LocCoordEvents.Op1>(key) { action() }
            2 -> onProtectedEvent<LocCoordEvents.Op2>(key) { action() }
            3 -> onProtectedEvent<LocCoordEvents.Op3>(key) { action() }
            else -> error("Unsupported Wilderness Agility loc option $option")
        }
    }

    private suspend fun ProtectedAccess.enterCourse() {
        arriveDelay()
        if (stat("stat.agility") < 52) {
            mes("You need an Agility level of 52 to enter this course.")
            return
        }
        crossEntranceCauseway(
            approach = WildernessAgilityRules.ENTRANCE_SOUTH_APPROACH_TILE,
            destination = CoordGrid(2998, 3932),
            direction = constants.em_face_north,
        )
    }

    private suspend fun ProtectedAccess.exitCourse() {
        arriveDelay()
        crossEntranceCauseway(
            approach = WildernessAgilityRules.ENTRANCE_NORTH_APPROACH_TILE,
            destination = CoordGrid(2998, 3916),
            direction = constants.em_face_south,
        )
        player.clearAllCourseState()
    }

    private suspend fun ProtectedAccess.crossEntranceCauseway(
        approach: CoordGrid,
        destination: CoordGrid,
        direction: Int,
    ) {
        playerWalkWithMinDelay(approach)
        if (player.coords != approach) {
            mes("You cannot reach the centre of the entrance ridge.")
            return
        }
        faceSquare(destination)
        val previousBas = player.bas
        player.bas = COURSE_BALANCE_BAS
        try {
            exactMove(
                approach,
                destination,
                0,
                ENTRANCE_CROSS_CLIENT_CYCLES,
                direction,
                TeleportType.Exempt,
            )
            delay(ENTRANCE_CROSS_TICKS)
        } finally {
            player.bas = previousBas
        }
    }

    private suspend fun ProtectedAccess.usePipe(forward: Boolean) {
        arriveDelay()
        if (stat("stat.agility") < 49) {
            mes("You need an Agility level of 49 to squeeze through this pipe.")
            return
        }
        if (!forward) {
            val destination = CoordGrid(3004, 3937)
            traversePipe(destination, constants.em_face_south)
            return
        }
        if (player.vars[WILDERNESS_AGILITY_UNCLAIMED_VARP] != 0) {
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 0)
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STREAK_VARP, 0)
            mes("You begin another lap without tagging the dispenser, resetting your lap streak.")
        }
        val stage = player.vars[WILDERNESS_AGILITY_STAGE_VARP]
        val ordered = WildernessAgilityRules.ordered(stage, WildernessAgilityRules.Obstacle.Pipe)
        traversePipe(CoordGrid(3004, 3949), constants.em_face_north)
        if (ordered) {
            statAdvance("stat.agility", WildernessAgilityRules.PIPE_XP)
            setStage(WildernessAgilityRules.Obstacle.Pipe.nextStage)
        }
    }

    private suspend fun ProtectedAccess.traversePipe(destination: CoordGrid, direction: Int) {
        val start = player.coords
        anim(WildernessAgilitySequences.PIPE_ENTER)
        delay(1)
        if (WildernessAgilitySequences.PIPE_CRAWL != WildernessAgilitySequences.PIPE_ENTER) {
            anim(WildernessAgilitySequences.PIPE_CRAWL)
        }
        exactMove(start, destination, 0, PIPE_CRAWL_CLIENT_CYCLES, direction, TeleportType.Exempt)
        delay(PIPE_CRAWL_TICKS)
        anim(WildernessAgilitySequences.PIPE_EXIT)
        delay(1)
    }

    private suspend fun ProtectedAccess.useRopeSwing() {
        arriveDelay()
        val stage = player.vars[WILDERNESS_AGILITY_STAGE_VARP]
        val ordered = WildernessAgilityRules.ordered(stage, WildernessAgilityRules.Obstacle.Rope)
        val succeeds = !ordered || rollSuccess()
        if (!succeeds) {
            anim(WildernessAgilitySequences.ROPE_MISS)
            delay(2)
            takeInstantHit(
                HitType.Typeless,
                WildernessAgilityRules.failureDamage(player.hitpoints, 15),
            )
            telejump(ROPE_FAILURE_TILE, TeleportType.Exempt)
            mes("You lose your grip and fall into the dungeon below.")
            return
        }
        anim(WildernessAgilitySequences.ROPE)
        exactMove(
            player.coords,
            CoordGrid(3005, 3958),
            0,
            64,
            constants.em_face_north,
            TeleportType.Exempt,
        )
        delay(3)
        if (ordered) {
            statAdvance("stat.agility", WildernessAgilityRules.ROPE_XP)
            setStage(WildernessAgilityRules.Obstacle.Rope.nextStage)
        }
    }

    private suspend fun ProtectedAccess.useSteppingStones() {
        arriveDelay()
        val stage = player.vars[WILDERNESS_AGILITY_STAGE_VARP]
        val ordered = WildernessAgilityRules.ordered(stage, WildernessAgilityRules.Obstacle.Stones)
        for ((index, destination) in STEPPING_STONE_PATH.withIndex()) {
            anim(WildernessAgilitySequences.STEPPING_STONE)
            exactMove(
                player.coords,
                destination,
                0,
                30,
                constants.em_face_west,
                TeleportType.Exempt,
            )
            delay(1)
            if (ordered && index == STEPPING_STONE_FAILURE_INDEX && !rollSuccess()) {
                val recovery = destination.translateZ(-1)
                exactMove(
                    player.coords,
                    recovery,
                    0,
                    30,
                    constants.em_face_south,
                    TeleportType.Exempt,
                )
                delay(1)
                takeInstantHit(
                    HitType.Typeless,
                    WildernessAgilityRules.failureDamage(player.hitpoints, 20),
                )
                mes("You lose your footing on the third stone.")
                return
            }
        }
        if (ordered) {
            statAdvance("stat.agility", WildernessAgilityRules.STONES_XP)
            setStage(WildernessAgilityRules.Obstacle.Stones.nextStage)
        }
    }

    private suspend fun ProtectedAccess.useLogBalance() {
        arriveDelay()
        playerWalkWithMinDelay(WildernessAgilityRules.LOG_APPROACH_TILE)
        if (coords != WildernessAgilityRules.LOG_APPROACH_TILE) {
            mes("You cannot reach the front of the log.")
            return
        }

        val stage = player.vars[WILDERNESS_AGILITY_STAGE_VARP]
        val ordered = WildernessAgilityRules.ordered(stage, WildernessAgilityRules.Obstacle.Log)
        faceSquare(WildernessAgilityRules.LOG_EXIT_TILE)
        if (ordered && !rollSuccess()) {
            anim(WildernessAgilitySequences.LOG_STUMBLE)
            delay(1)
            takeInstantHit(
                HitType.Typeless,
                WildernessAgilityRules.failureDamage(player.hitpoints, 15),
            )
            telejump(LOG_FAILURE_TILE, TeleportType.Exempt)
            mes("You lose your balance and fall into the dungeon below.")
            return
        }
        val previousBas = player.bas
        player.bas = COURSE_BALANCE_BAS
        try {
            exactMove(
                WildernessAgilityRules.LOG_APPROACH_TILE,
                WildernessAgilityRules.LOG_EXIT_TILE,
                0,
                150,
                constants.em_face_west,
                TeleportType.Exempt,
            )
            delay(5)
        } finally {
            player.bas = previousBas
        }
        if (ordered) {
            statAdvance("stat.agility", WildernessAgilityRules.LOG_XP)
            setStage(WildernessAgilityRules.Obstacle.Log.nextStage)
        }
    }

    private suspend fun ProtectedAccess.useClimbingRocks(x: Int) {
        arriveDelay()
        if (player.coords.z < 3936) {
            anim(WildernessAgilitySequences.ROCK_CLIMB)
            exactMove(
                player.coords,
                CoordGrid(x, 3937),
                0,
                44,
                constants.em_face_north,
                TeleportType.Exempt,
            )
            delay(2)
            return
        }
        val validLap =
            WildernessAgilityRules.ordered(
                player.vars[WILDERNESS_AGILITY_STAGE_VARP],
                WildernessAgilityRules.Obstacle.Rocks,
            )
        anim(WildernessAgilitySequences.ROCK_CLIMB)
        exactMove(
            player.coords,
            CoordGrid(x, 3933),
            0,
            44,
            constants.em_face_south,
            TeleportType.Exempt,
        )
        delay(2)
        setStage(0)
        if (validLap) {
            statAdvance("stat.agility", WildernessAgilityRules.LAP_XP)
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 1)
            mes("You complete a lap. Tag the dispenser to collect your ticket.")
        }
    }

    private suspend fun ProtectedAccess.tagDispenser() {
        arriveDelay()
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
        if (player.vars[WILDERNESS_AGILITY_UNCLAIMED_VARP] == 0) {
            mes("You tag the dispenser and restore your run energy.")
            return
        }
        invAddOrDrop(objRepo, WILDERNESS_AGILITY_TICKET)
        val paid = player.vars[WILDERNESS_AGILITY_PAID_VARP] != 0
        if (paid) {
            val streak = (player.vars[WILDERNESS_AGILITY_STREAK_VARP] + 1).coerceAtLeast(1)
            awardPaidLoot(streak)
            VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STREAK_VARP, streak)
            mes("Your paid-course lap streak is now $streak.")
        }
        VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_UNCLAIMED_VARP, 0)
        mes("You receive a Wilderness agility ticket.")
    }

    private suspend fun ProtectedAccess.payDispenser() {
        arriveDelay()
        if (player.vars[WILDERNESS_AGILITY_PAID_VARP] != 0) {
            mes("You have already paid for this course session.")
            return
        }
        val needsBag = !player.ownsLootingBag()
        val exactCoinStackFreesSlot = inv.count(COINS) == WildernessAgilityRules.PAYMENT
        if (needsBag && inv.isFull() && !exactCoinStackFreesSlot) {
            mes("You need a free inventory space to receive a looting bag.")
            return
        }
        var confirm = false
        startDialogue {
            chatPlayer(
                neutral,
                "Pay 150,000 coins from my inventory for paid Wilderness Agility rewards?",
            )
            confirm = choice2("Yes, pay 150,000 coins.", true, "No thanks.", false)
        }
        if (!confirm) return
        if (player.vars[WILDERNESS_AGILITY_PAID_VARP] != 0) {
            mes("You have already paid for this course session.")
            return
        }
        if (inv.count(COINS) < WildernessAgilityRules.PAYMENT) {
            mes("You need 150,000 coins in your inventory.")
            return
        }
        if (invDel(inv, COINS, WildernessAgilityRules.PAYMENT).failure) {
            mes("The payment could not be collected. No coins were taken.")
            return
        }
        if (needsBag && invAdd(inv, CLOSED_LOOTING_BAG).failure) {
            check(invAdd(inv, COINS, WildernessAgilityRules.PAYMENT).success)
            mes("You need a free inventory space to receive a looting bag.")
            return
        }
        VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_PAID_VARP, 1)
        VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STREAK_VARP, 0)
        mes("You pay 150,000 coins for access to the dispenser's paid rewards.")
        if (needsBag) mes("The dispenser gives you a looting bag.")
    }

    private suspend fun ProtectedAccess.redeemTickets() {
        arriveDelay()
        val available = inv.count(WILDERNESS_AGILITY_TICKET)
        if (available <= 0) {
            mes("You do not have any Wilderness agility tickets to redeem.")
            return
        }
        val requested = min(available, countDialog("How many tickets would you like to redeem?"))
        if (requested <= 0) return
        if (invDel(inv, WILDERNESS_AGILITY_TICKET, requested).failure) {
            mes("Those tickets could not be redeemed.")
            return
        }
        val xp = WildernessAgilityRules.ticketXp(requested)
        statAdvance("stat.agility", xp)
        mes("You redeem $requested ticket${if (requested == 1) "" else "s"} for $xp Agility XP.")
    }

    private fun ProtectedAccess.awardPaidLoot(streak: Int) {
        val clueAwarded = rollMediumClue()
        if (OPEN_LOOTING_BAG !in inv) {
            mes("Open your looting bag before tagging the dispenser to receive paid loot.")
            return
        }
        if (!clueAwarded && inv.hasFreeSpace()) {
            val supply = rollWeighted(UNNOTED_SUPPLIES)
            invAddOrDrop(objRepo, supply.obj, rollCount(supply))
        }
        val bag = player.invMap.getOrPut(LOOTING_BAG_INVENTORY)
        val tier = WildernessAgilityRules.rewardTier(streak)
        addNotedReward(bag, rollWeighted(resourceTable(tier)))
        addNotedReward(bag, rollWeighted(armourTable(tier)))
    }

    private fun ProtectedAccess.rollMediumClue(): Boolean {
        if (player.ownsClueScroll(ClueScrollTier.Medium)) return false
        val denominator = if (wearsImbuedRingOfWealth()) 20 else 40
        if (random.of(denominator) != 0) return false
        val clue = player.clueScrollTransformObj(MEDIUM_CLUE) ?: MEDIUM_CLUE
        invAddOrDrop(objRepo, clue)
        mes("You receive a medium clue scroll.")
        return true
    }

    private fun ProtectedAccess.wearsImbuedRingOfWealth(): Boolean =
        player.worn[Wearpos.Ring.slot].isAnyType(
            "obj.ring_of_wealth_i",
            "obj.ring_of_wealth_i1",
            "obj.ring_of_wealth_i2",
            "obj.ring_of_wealth_i3",
            "obj.ring_of_wealth_i4",
            "obj.ring_of_wealth_i5",
        )

    private fun ProtectedAccess.addNotedReward(inventory: Inventory, reward: WeightedReward) {
        val count = rollCount(reward)
        val noted = ocCert(reward.obj).internalName
        invAddOrDrop(objRepo, noted, count, inv = inventory)
    }

    private fun rollWeighted(table: List<WeightedReward>): WeightedReward {
        var roll = random.of(table.sumOf(WeightedReward::weight))
        for (entry in table) {
            if (roll < entry.weight) return entry
            roll -= entry.weight
        }
        error("Invalid Wilderness Agility reward table")
    }

    private fun rollCount(reward: WeightedReward): Int =
        reward.minimum + random.of(reward.maximum - reward.minimum + 1)

    private fun resourceTable(tier: WildernessAgilityRules.RewardTier): List<WeightedReward> {
        val quantities =
            when (tier) {
                WildernessAgilityRules.RewardTier.One -> arrayOf(3..6, 3..6, 3..6, 1..2)
                WildernessAgilityRules.RewardTier.Two -> arrayOf(6..11, 6..11, 6..11, 2..4)
                WildernessAgilityRules.RewardTier.Three -> arrayOf(10..16, 10..16, 10..16, 3..6)
                WildernessAgilityRules.RewardTier.Four -> arrayOf(14..20, 14..20, 14..20, 4..8)
            }
        return listOf(
            WeightedReward("obj.blighted_anglerfish", quantities[0].first, quantities[0].last, 8),
            WeightedReward("obj.blighted_mantaray", quantities[1].first, quantities[1].last, 8),
            WeightedReward("obj.blighted_karambwan", quantities[2].first, quantities[2].last, 8),
            WeightedReward("obj.blighted_4dose2restore", quantities[3].first, quantities[3].last, 7),
        )
    }

    private fun armourTable(tier: WildernessAgilityRules.RewardTier): List<WeightedReward> =
        when (tier) {
            WildernessAgilityRules.RewardTier.One ->
                listOf(
                    reward("obj.adamant_full_helm", 1),
                    reward("obj.adamant_platebody", 2),
                    reward("obj.adamant_platelegs", 1),
                    reward("obj.mithril_chainbody", 1),
                    reward("obj.mithril_platelegs", 1),
                    reward("obj.mithril_plateskirt", 1),
                    reward("obj.rune_med_helm", 2),
                    reward("obj.steel_platebody", 1),
                )
            WildernessAgilityRules.RewardTier.Two ->
                listOf(
                    reward("obj.adamant_full_helm", 1),
                    reward("obj.adamant_platebody", 1),
                    reward("obj.adamant_platelegs", 1),
                    reward("obj.mithril_chainbody", 1),
                    reward("obj.mithril_platelegs", 1),
                    reward("obj.mithril_plateskirt", 1),
                    reward("obj.rune_chainbody", 1),
                    reward("obj.rune_kiteshield", 1),
                    reward("obj.rune_med_helm", 1),
                )
            WildernessAgilityRules.RewardTier.Three ->
                listOf(
                    reward("obj.adamant_full_helm", 1),
                    reward("obj.adamant_platebody", 1),
                    reward("obj.adamant_platelegs", 1),
                    reward("obj.mithril_platelegs", 1),
                    reward("obj.mithril_plateskirt", 1),
                    reward("obj.rune_chainbody", 2),
                    reward("obj.rune_kiteshield", 2),
                    reward("obj.rune_med_helm", 1),
                )
            WildernessAgilityRules.RewardTier.Four ->
                listOf(
                    reward("obj.adamant_full_helm", 1),
                    reward("obj.adamant_platebody", 2),
                    reward("obj.adamant_platelegs", 1),
                    reward("obj.mithril_platelegs", 1),
                    reward("obj.mithril_plateskirt", 1),
                    reward("obj.rune_chainbody", 6),
                    reward("obj.rune_kiteshield", 6),
                    reward("obj.rune_med_helm", 2),
                )
        }

    private fun reward(obj: String, weight: Int): WeightedReward = WeightedReward(obj, 1, 1, weight)

    private fun ProtectedAccess.openLootingBag() {
        if (invReplace(inv, CLOSED_LOOTING_BAG, 1, OPEN_LOOTING_BAG).success) {
            mes("You open the looting bag.")
        }
    }

    private fun ProtectedAccess.closeLootingBag() {
        if (invReplace(inv, OPEN_LOOTING_BAG, 1, CLOSED_LOOTING_BAG).success) {
            mes("You close the looting bag.")
        }
    }

    private fun ProtectedAccess.checkLootingBag() {
        val bag = player.invMap.getOrPut(LOOTING_BAG_INVENTORY)
        mes("Your looting bag contains ${bag.occupiedSpace()} of ${bag.size} item stacks.")
    }

    private fun ProtectedAccess.rollSuccess(): Boolean =
        WildernessAgilityRules.succeeds(stat("stat.agility"), random.of(256))

    private fun ProtectedAccess.setStage(stage: Int) {
        VarPlayerIntMapSetter.set(player, WILDERNESS_AGILITY_STAGE_VARP, stage)
    }

    private fun validateLocOption(locId: Int, option: Int) {
        val type =
            requireNotNull(ServerCacheManager.getObject(locId)) {
                "Missing revision-240 Wilderness Agility object $locId"
            }
        require(!type.actions.getOpOrNull(option - 1).isNullOrBlank()) {
            "Wilderness Agility object $locId has no option $option"
        }
    }

    private data class WeightedReward(
        val obj: String,
        val minimum: Int,
        val maximum: Int,
        val weight: Int,
    )

    private companion object {
        private const val ENTRANCE_CROSS_TICKS = 16
        private const val ENTRANCE_CROSS_CLIENT_CYCLES = ENTRANCE_CROSS_TICKS * 30
        private val COURSE_BALANCE_BAS =
            BasType(
                readyAnim = WildernessAgilitySequences.BALANCE_READY,
                turnOnSpot = WildernessAgilitySequences.BALANCE_READY,
                walkForward = WildernessAgilitySequences.BALANCE_WALK,
                walkBack = WildernessAgilitySequences.BALANCE_WALK,
                walkLeft = WildernessAgilitySequences.BALANCE_WALK,
                walkRight = WildernessAgilitySequences.BALANCE_WALK,
                running = WildernessAgilitySequences.BALANCE_WALK,
            )
        private const val PIPE_CRAWL_TICKS = 3
        private const val PIPE_CRAWL_CLIENT_CYCLES = PIPE_CRAWL_TICKS * 30
        private const val STEPPING_STONE_FAILURE_INDEX = 2
        private const val WILDERNESS_AGILITY_TICKET = "obj.wildy_agility_token"
        private const val COINS = "obj.coins"
        private const val CLOSED_LOOTING_BAG = "obj.looting_bag"
        private const val OPEN_LOOTING_BAG = "obj.looting_bag_open"
        private const val LOOTING_BAG_INVENTORY = "inv.looting_bag"
        private const val MEDIUM_CLUE = "obj.trail_clue_medium_sextant001"

        private val ROPE_FAILURE_TILE = CoordGrid(3005, 10352)
        private val LOG_FAILURE_TILE = CoordGrid(3001, 10345)
        private val STEPPING_STONE_PATH =
            listOf(
                CoordGrid(3001, 3960),
                CoordGrid(3000, 3960),
                CoordGrid(2999, 3960),
                CoordGrid(2998, 3960),
                CoordGrid(2997, 3960),
                CoordGrid(2996, 3960),
            )
        private val UNNOTED_SUPPLIES =
            listOf(
                WeightedReward("obj.blighted_anglerfish", 1, 1, 5),
                WeightedReward("obj.blighted_mantaray", 1, 1, 5),
                WeightedReward("obj.blighted_karambwan", 1, 1, 5),
                WeightedReward("obj.blighted_4dose2restore", 1, 1, 1),
            )
    }
}

public fun Player.clearPaidCourseState() {
    VarPlayerIntMapSetter.set(this, WILDERNESS_AGILITY_PAID_VARP, 0)
    VarPlayerIntMapSetter.set(this, WILDERNESS_AGILITY_STREAK_VARP, 0)
}

public fun Player.clearAllCourseState() {
    VarPlayerIntMapSetter.set(this, WILDERNESS_AGILITY_STAGE_VARP, 0)
    VarPlayerIntMapSetter.set(this, WILDERNESS_AGILITY_UNCLAIMED_VARP, 0)
    clearPaidCourseState()
}
