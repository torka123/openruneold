package org.rsmod.content.minigames.tob

import kotlin.math.max
import kotlin.random.Random
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

internal const val TOB_GAME_TICK_MILLIS = 600
internal const val NYLOCAS_WAVE_THREE_BOSS_DELAY_TICKS = 17
internal const val NYLOCAS_BOSS_FORM_TICKS = 10
internal const val XARPUS_RECOVERY_TICKS = 34
internal const val XARPUS_RECOVERY_HEAL = 4

/** Normal-mode Verzik timings and caps, expressed in 600 ms server ticks. */
internal object VerzikNormalRules {
    const val PHASE_ONE_INITIAL_DELAY = 14
    const val PHASE_ONE_ATTACK_INTERVAL = 14
    const val PHASE_ONE_IMPACT_DELAY = 2
    const val PHASE_ONE_MAX_HIT = 137
    const val PHASE_ONE_PRAYER_REDUCTION = 50
    const val PHASE_ONE_PILLAR_MAX_HIT = 92

    const val PHASE_TWO_TRANSITION_TICKS = 14
    const val PHASE_TWO_TRANSITION_CLIENT_CYCLES = 406
    const val PHASE_TWO_ATTACK_INTERVAL = 4
    const val PHASE_TWO_LIGHTNING_INTERVAL = 5
    const val PHASE_TWO_URN_MAX_HIT = 44
    const val PHASE_TWO_BLOOD_MAX_HIT = 45
    const val PHASE_TWO_SLAM_MAX_HIT = 45
    const val PHASE_TWO_SLAM_CHANCE_DENOMINATOR = 4
    const val PHASE_TWO_KNOCKBACK_DISTANCE = 4
    const val PHASE_TWO_KNOCKBACK_CLIENT_CYCLES = 30
    const val PHASE_TWO_STUN_TICKS = 5
    const val PHASE_TWO_POINT_FIVE_PERCENT = 35
    const val MATOMENOS_SUMMON_TICKS = 12
    const val MATOMENOS_DAMAGE_HEAL_TICKS = 8
    const val MATOMENOS_HITPOINTS = 75
    const val MATOMENOS_ATTACKS_BEFORE_ABSORB = 7
    const val MATOMENOS_RESPAWN_DELAY = 12

    const val PHASE_THREE_ATTACK_INTERVAL = 7
    const val PHASE_THREE_ENRAGED_INTERVAL = 5
    const val PHASE_THREE_AUTOS_BEFORE_SPECIAL = 4
    const val PHASE_THREE_AUTO_MAX_HIT = 33
    const val PHASE_THREE_MELEE_MAX_HIT = 63
    const val WEB_VOLLEYS = 6
    const val WEB_PROJECTILES_PER_VOLLEY = 3
    const val WEB_VOLLEY_INTERVAL = 2
    const val WEB_LIFETIME = 45
    const val WEB_BIND_TICKS = 15
    const val WEB_HITPOINTS = 10
    const val GREEN_BALL_BOUNCES = 3
    const val GREEN_BALL_RANGE = 1
    const val GREEN_BALL_TRAVEL_TICKS = 2
    const val YELLOW_PROJECTILE_TICK = 7
    const val YELLOW_IMPACT_TICK = 11
    const val YELLOW_END_TICK = 15
    const val TORNADO_DAMAGE_PERCENT = 50
    const val TORNADO_HEAL_MULTIPLIER = 3
    const val THRONE_OPEN_TICKS = 5

    fun phaseOneDamage(rolled: Int, protectedAtLaunch: Boolean): Int =
        if (protectedAtLaunch) rolled * (100 - PHASE_ONE_PRAYER_REDUCTION) / 100 else rolled

    fun lightningDue(attackCount: Int): Boolean =
        attackCount > 0 && attackCount % PHASE_TWO_LIGHTNING_INTERVAL == 0

    fun greenBallDamage(baseHitpoints: Int): Int = baseHitpoints.coerceAtLeast(1) * 3 / 4

    fun scaledHitpoints(base: Int, partySize: Int): Int =
        when (partySize.coerceIn(1, TOB_MAX_PARTY_SIZE)) {
            in 1..3 -> base * 75 / 100
            4 -> base * 875 / 1000
            else -> base
        }.coerceAtLeast(1)
}

internal fun isProtectedByVerzikYellowPool(
    player: CoordGrid,
    occupancy: Map<CoordGrid, Int>,
): Boolean = occupancy[player] == 1

internal fun tobTicksForSeconds(seconds: Int): Int {
    require(seconds >= 0)
    return (seconds * 1_000 + TOB_GAME_TICK_MILLIS - 1) / TOB_GAME_TICK_MILLIS
}

internal fun canAdvanceTobRoom(source: TobRoomProgress, destination: TobRoomProgress): Boolean =
    destination.index == source.index + 1 &&
        source.state == TobRoomState.COMPLETE &&
        destination.state != TobRoomState.COMPLETE

internal object TobScaling {
    fun hitpoints(base: Int, partySize: Int, mode: TobMode, room: TobRoom): Int {
        require(base > 0 && partySize in 1..TOB_MAX_PARTY_SIZE)
        if (mode == TobMode.ENTRY) return base * partySize
        val teamScaled = (base * (50.0 + 12.5 * (partySize - 1)) / 100.0).toInt().coerceAtLeast(1)
        return if (mode == TobMode.HARD && room == TobRoom.BLOAT) teamScaled * 120 / 100
        else teamScaled
    }

    fun maxHit(base: Int, mode: TobMode): Int =
        when (mode) {
            TobMode.ENTRY -> max(1, base / 2)
            TobMode.NORMAL -> base
            TobMode.HARD -> base * 120 / 100
        }
}

internal object TobScoreRules {
    const val PARTICIPATION_PER_ROOM = 3
    const val DEATH_PENALTY = 4
    val mvpBonus =
        mapOf(
            TobRoom.MAIDEN to 2,
            TobRoom.BLOAT to 2,
            TobRoom.NYLOCAS to 1,
            TobRoom.SOTETSEG to 1,
            TobRoom.XARPUS to 2,
            TobRoom.VERZIK to 6,
        )

    fun awardRoom(session: TobRaidSession, hero: TobParticipant?) {
        session.participants.values
            .filter(TobParticipant::entered)
            .filterNot { it.spectating }
            .forEach { it.score += PARTICIPATION_PER_ROOM }
        val bonus = mvpBonus[session.currentRoom.room] ?: 0
        if (hero != null) {
            hero.score += bonus
            hero.mvpPoints += bonus
        }
    }

    fun recordDeath(participant: TobParticipant) {
        participant.deaths++
        participant.score = (participant.score - DEATH_PENALTY).coerceAtLeast(0)
        participant.spectating = true
    }

    fun maximum(partySize: Int): Int = partySize * 18 + 14
}

internal data class WeightedTobUnique(val objId: Int, val weight: Int)

internal object TobRewardRules {
    const val PURPLE_CHEST_DENOMINATOR = 5

    val normalUniques =
        listOf(
            WeightedTobUnique(22477, 8),
            WeightedTobUnique(22324, 2),
            WeightedTobUnique(22481, 2),
            WeightedTobUnique(22326, 2),
            WeightedTobUnique(22327, 2),
            WeightedTobUnique(22328, 2),
            WeightedTobUnique(22486, 1),
        )
    val hardUniques = normalUniques.map { if (it.objId == 22477) it.copy(weight = 7) else it }

    fun teamUniqueChance(mode: TobMode, score: Int, partySize: Int): Double {
        require(partySize > 0)
        if (mode == TobMode.ENTRY || score <= 0) return 0.0
        return 1.0 / PURPLE_CHEST_DENOMINATOR
    }

    fun isUniqueRoll(chance: Double, denominatorRoll: Int, forceUnique: Boolean = false): Boolean {
        require(denominatorRoll in 1..PURPLE_CHEST_DENOMINATOR)
        return forceUnique || (chance > 0.0 && denominatorRoll == 1)
    }

    fun ownerTicket(participants: List<TobParticipant>, ticket: Int): TobParticipant? {
        val eligible = participants.filter { it.score > 0 }
        val total = eligible.sumOf(TobParticipant::score)
        if (ticket !in 0 until total) return null
        var cursor = 0
        return eligible.first {
            cursor += it.score
            ticket < cursor
        }
    }

    fun uniqueAtTicket(mode: TobMode, ticket: Int): WeightedTobUnique {
        val table = if (mode == TobMode.HARD) hardUniques else normalUniques
        require(ticket in 0 until table.sumOf(WeightedTobUnique::weight))
        var cursor = 0
        return table.first {
            cursor += it.weight
            ticket < cursor
        }
    }

    fun commonMultiplier(mode: TobMode, inTime: Boolean): Double =
        when (mode) {
            TobMode.ENTRY -> 0.20
            TobMode.NORMAL -> 1.0
            TobMode.HARD -> if (inTime) 1.30 else 1.15
        }

    fun hardTimeTicks(partySize: Int): Int =
        when (partySize) {
            1 -> 70 * 100
            2 -> 35 * 100
            3 -> 30 * 100
            4 -> 27 * 100
            else -> 25 * 100
        }
}

internal class MaidenState {
    private val pending = ArrayDeque(listOf(70, 50, 30))
    val bloodPools = linkedMapOf<CoordGrid, Int>()
    var empoweredNylocas = 0
        private set

    var bloodAttackCooldown = 0
        private set

    fun threshold(currentPercent: Int): Int? =
        pending.firstOrNull()?.takeIf { currentPercent <= it }?.also { pending.removeFirst() }

    fun tick() {
        if (bloodAttackCooldown > 0) bloodAttackCooldown--
    }

    fun armBloodCooldown() {
        bloodAttackCooldown = 2
    }

    fun addPool(tile: CoordGrid, expiresAt: Int) {
        bloodPools[tile] = maxOf(bloodPools[tile] ?: 0, expiresAt)
    }

    fun expirePools(cycle: Int) {
        bloodPools.entries.removeIf { it.value <= cycle }
    }

    fun empower() {
        empoweredNylocas++
    }

    fun magicMaxHit(): Int = 36 + 3 * empoweredNylocas

    fun bloodDamage(): Int = 10 + 2 * empoweredNylocas
}

internal enum class BloatPhase {
    WALKING,
    DOWN,
}

internal class BloatState {
    var phase = BloatPhase.WALKING
        private set

    var cornerIndex = 0
        private set

    var phaseTicks = 0
        private set

    var downs = 0
        private set

    var fallingBodyAt: CoordGrid? = null
    var fallingBodyHitsAt: Int = -1

    fun tick(): BloatPhase {
        phaseTicks++
        val limit = if (phase == BloatPhase.WALKING) 18 else 32
        if (phaseTicks >= limit) {
            phase = if (phase == BloatPhase.WALKING) BloatPhase.DOWN else BloatPhase.WALKING
            phaseTicks = 0
            if (phase == BloatPhase.WALKING) {
                downs++
                cornerIndex = (cornerIndex + 1) % 4
            }
        }
        return phase
    }

    fun stompNow(): Boolean = phase == BloatPhase.DOWN && phaseTicks == 31

    fun nextCorner(): Int {
        cornerIndex = (cornerIndex + 1) % 4
        return cornerIndex
    }
}

internal class NylocasWaveState {
    val totalWaves = 3
    var wave = 0
        private set

    var bossSpawned = false
    val pillars = mutableListOf<Npc>()
    val pillarTargets = mutableMapOf<Npc, Int>()
    val pillarAttackAt = mutableMapOf<Npc, Int>()
    var waveThreeStartedAt: Int? = null
        private set

    var bossSpawnAt: Int? = null
        private set

    var bossFormTicks = 0

    fun canSpawn(alive: Int): Boolean = wave < totalWaves && alive < 12

    fun nextWave(cycle: Int? = null): Int =
        (++wave).also {
            require(it <= totalWaves)
            if (it == totalWaves && cycle != null) {
                waveThreeStartedAt = cycle
                bossSpawnAt = cycle + NYLOCAS_WAVE_THREE_BOSS_DELAY_TICKS
            }
        }

    fun shouldSpawnBoss(cycle: Int): Boolean =
        !bossSpawned && bossSpawnAt?.let { cycle >= it } == true

    fun markBossSpawned() {
        check(!bossSpawned)
        bossSpawned = true
        bossFormTicks = 0
    }

    fun nextBossForm(current: Int, forms: IntArray): Int {
        bossFormTicks = 0

        val index = forms.indexOf(current).takeIf { it >= 0 } ?: 0
        return forms[(index + 1) % forms.size]
    }
}

internal data class MazeTile(val x: Int, val z: Int)

internal object SotetsegMazeRules {
    fun generate(seed: Long, rows: Int = 15): List<MazeTile> {
        require(rows >= 4)
        val random = Random(seed)
        val path = mutableListOf(MazeTile(0, 0))
        var x = 0
        for (z in 0 until rows) {
            val targetX = (x + random.nextInt(-2, 3)).coerceIn(-5, 5)
            while (x != targetX) {
                x += if (targetX > x) 1 else -1
                path += MazeTile(x, z)
            }
            if (z + 1 < rows) path += MazeTile(x, z + 1)
        }
        return path
    }
}

internal enum class XarpusPhase {
    EXHUMEDS,
    COMBAT,
    SCREECH,
}

internal enum class XarpusStare {
    NORTH,
    EAST,
    SOUTH,
    WEST,
}

internal class XarpusState(partySize: Int = 1, mode: TobMode = TobMode.NORMAL) {
    init {
        require(partySize in 1..TOB_MAX_PARTY_SIZE)
    }

    var phase = XarpusPhase.EXHUMEDS
        private set

    val requiredExhumeds = intArrayOf(7, 9, 12, 15, 18)[partySize - 1]
    val exhumedSpawnInterval = intArrayOf(12, 8, 8, 4, 4)[partySize - 1]
    val healPerUnblockedTick = XARPUS_RECOVERY_HEAL
    val screechPercent = if (mode == TobMode.ENTRY) 22 else 25

    var completedExhumeds = 0
        private set

    var stare = XarpusStare.NORTH
        private set

    fun completeExhumed(): XarpusPhase {
        if (phase != XarpusPhase.EXHUMEDS) return phase
        completedExhumeds++
        return phase
    }

    fun updateHealth(percent: Int): XarpusPhase {
        if (phase == XarpusPhase.COMBAT && percent <= screechPercent) {
            phase = XarpusPhase.SCREECH
        }
        return phase
    }

    fun forceCombat() {
        completedExhumeds = requiredExhumeds
        phase = XarpusPhase.COMBAT
    }

    fun rotateStare(): XarpusStare {
        stare = XarpusStare.entries[(stare.ordinal + 1) % XarpusStare.entries.size]
        return stare
    }
}

internal enum class VerzikPhase {
    ONE,
    TWO,
    THREE,
    DEFEATED,
}

internal enum class VerzikEncounterStage {
    DORMANT,
    PHASE_ONE,
    PHASE_ONE_TO_TWO,
    PHASE_TWO,
    PHASE_TWO_TO_THREE,
    PHASE_THREE,
    ENRAGED,
    DYING,
    COMPLETE,
}

internal enum class VerzikSpecial {
    ELECTRIC,
    NYLOCAS,
    BLOOD,
    WEBS,
    YELLOW_POOLS,
    GREEN_BALL,
    TORNADO,
}

internal class VerzikState(val mode: TobMode) {
    var phase = VerzikPhase.ONE
        private set

    var stage = VerzikEncounterStage.DORMANT
        private set

    var specialIndex = 0
        private set

    fun activate() {
        check(stage == VerzikEncounterStage.DORMANT) { "Verzik is already active." }
        stage = VerzikEncounterStage.PHASE_ONE
    }

    fun beginTransition() {
        stage =
            when (phase) {
                VerzikPhase.ONE -> VerzikEncounterStage.PHASE_ONE_TO_TWO
                VerzikPhase.TWO -> VerzikEncounterStage.PHASE_TWO_TO_THREE
                VerzikPhase.THREE -> VerzikEncounterStage.DYING
                VerzikPhase.DEFEATED -> error("Already defeated")
            }
    }

    fun completeTransition(): Boolean {
        check(
            stage in
                setOf(
                    VerzikEncounterStage.PHASE_ONE_TO_TWO,
                    VerzikEncounterStage.PHASE_TWO_TO_THREE,
                    VerzikEncounterStage.DYING,
                )
        ) {
            "Verzik is not transitioning. (stage=$stage)"
        }
        phase =
            when (phase) {
                VerzikPhase.ONE -> VerzikPhase.TWO
                VerzikPhase.TWO -> VerzikPhase.THREE
                VerzikPhase.THREE -> VerzikPhase.DEFEATED
                VerzikPhase.DEFEATED -> error("Already defeated")
            }
        stage =
            when (phase) {
                VerzikPhase.ONE -> VerzikEncounterStage.PHASE_ONE
                VerzikPhase.TWO -> VerzikEncounterStage.PHASE_TWO
                VerzikPhase.THREE -> VerzikEncounterStage.PHASE_THREE
                VerzikPhase.DEFEATED -> VerzikEncounterStage.COMPLETE
            }
        specialIndex = 0
        return phase == VerzikPhase.DEFEATED
    }

    /** Compatibility helper for deterministic tests and admin phase forcing. */
    fun advance(): Boolean {
        if (stage == VerzikEncounterStage.DORMANT) activate()
        beginTransition()
        return completeTransition()
    }

    fun forcePhase(value: VerzikPhase) {
        require(value != VerzikPhase.DEFEATED)
        phase = value
        stage =
            when (value) {
                VerzikPhase.ONE -> VerzikEncounterStage.PHASE_ONE
                VerzikPhase.TWO -> VerzikEncounterStage.PHASE_TWO
                VerzikPhase.THREE -> VerzikEncounterStage.PHASE_THREE
                VerzikPhase.DEFEATED -> error("Cannot force the defeated phase")
            }
        specialIndex = 0
    }

    fun markEnraged() {
        if (phase == VerzikPhase.THREE) stage = VerzikEncounterStage.ENRAGED
    }

    fun nextSpecial(): VerzikSpecial {
        val cycle =
            when (phase) {
                VerzikPhase.ONE -> listOf(VerzikSpecial.ELECTRIC)
                VerzikPhase.TWO ->
                    listOf(VerzikSpecial.NYLOCAS, VerzikSpecial.BLOOD, VerzikSpecial.ELECTRIC)
                VerzikPhase.THREE ->
                    listOf(
                        VerzikSpecial.NYLOCAS,
                        VerzikSpecial.WEBS,
                        VerzikSpecial.YELLOW_POOLS,
                        VerzikSpecial.GREEN_BALL,
                    )
                VerzikPhase.DEFEATED -> error("No specials after defeat")
            }
        return cycle[specialIndex++ % cycle.size]
    }
}
