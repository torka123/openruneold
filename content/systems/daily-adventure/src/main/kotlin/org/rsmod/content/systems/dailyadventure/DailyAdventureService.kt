package org.rsmod.content.systems.dailyadventure

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.roundToLong
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.dailyadventure.DailyAdventureAction
import org.rsmod.api.dailyadventure.DailyAdventureCatalogue
import org.rsmod.api.dailyadventure.DailyAdventureClaimResult
import org.rsmod.api.dailyadventure.DailyAdventureClaimStatus
import org.rsmod.api.dailyadventure.DailyAdventureEngine
import org.rsmod.api.dailyadventure.DailyAdventureFeatures
import org.rsmod.api.dailyadventure.DailyAdventurePath
import org.rsmod.api.dailyadventure.DailyAdventureProgressEvent
import org.rsmod.api.dailyadventure.DailyAdventureProgressResult
import org.rsmod.api.dailyadventure.DailyAdventureRerollResult
import org.rsmod.api.dailyadventure.DailyAdventureSelectionStatus
import org.rsmod.api.dailyadventure.DailyAdventureState
import org.rsmod.api.dailyadventure.DailyAdventureStateCodec
import org.rsmod.api.dailyadventure.currentDailyAdventureDay
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.api.player.vars.boolVarBit

internal val DAILY_ADVENTURE_STATE =
    AttributeKey<MutableMap<String, Any>>(persistenceKey = "account.daily_adventure_v1")

@Singleton
public class DailyAdventureService
@Inject
constructor(private val players: PlayerList) {
    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")
    private val log = InlineLogger()
    private val engine = DailyAdventureEngine(loadOrCreatePrivateSalt())
    private val sessions = mutableMapOf<Int, AccountSession>()
    private val xpSnapshots = IdentityHashMap<Player, Map<String, Int>>()
    private val eventSequence = AtomicLong()

    public fun onLogin(player: Player) {
        val loaded = DailyAdventureStateCodec.decode(player.attr[DAILY_ADVENTURE_STATE])
        val session =
            synchronized(sessions) {
                val current = sessions[player.accountId]
                when {
                    current == null ->
                        AccountSession(loaded ?: DailyAdventureState()).also {
                            sessions[player.accountId] = it
                        }
                    loaded != null && loaded.updatedAtEpochMillis > current.state.updatedAtEpochMillis -> {
                        current.state = loaded
                        current
                    }
                    else -> current
                }
            }
        synchronized(session) {
            session.players += player
            ensureCurrentDay(player, session)
            persist(session)
        }
        xpSnapshots[player] = readXp(player)
        deliverPendingMemberBoxes(player)
    }

    public fun onLogout(player: Player) {
        xpSnapshots.remove(player)
        synchronized(sessions) {
            val session = sessions[player.accountId] ?: return
            synchronized(session) {
                session.players -= player
                persist(session)
                if (session.players.isEmpty()) sessions.remove(player.accountId)
            }
        }
    }

    public fun tick() {
        players.filter { it.isSlotAssigned }.forEach { player ->
            val session = session(player)
            synchronized(session) {
                val changed = ensureCurrentDay(player, session)
                if (changed) persist(session)
            }
            sampleExperience(player)
        }
    }

    public fun state(player: Player): DailyAdventureState {
        val session = session(player)
        synchronized(session) {
            if (ensureCurrentDay(player, session)) persist(session)
            return session.state
        }
    }

    public fun selectPath(
        player: Player,
        path: DailyAdventurePath,
    ): DailyAdventureSelectionStatus {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            val result =
                engine.selectFreePath(
                    session.state,
                    accountKey(player),
                    currentDailyAdventureDay(),
                    path,
                    ::eligible,
                    monotonicNow(session.state),
                )
            if (result == DailyAdventureSelectionStatus.Selected) {
                persist(session)
                log.info { "daily_path_selected account=${player.accountId} path=$path" }
            }
            return result
        }
    }

    public fun reroll(
        player: Player,
        path: DailyAdventurePath,
        slot: Int,
        requestId: String,
    ): DailyAdventureRerollResult {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            val result =
                engine.reroll(
                    session.state,
                    accountKey(player),
                    currentDailyAdventureDay(),
                    path,
                    slot,
                    requestId,
                    ::eligible,
                    monotonicNow(session.state),
                )
            if (result.replacementTaskId != null) {
                persist(session)
                log.info {
                    "daily_reroll account=${player.accountId} path=$path slot=$slot " +
                        "old=${result.previousTaskId} new=${result.replacementTaskId}"
                }
            }
            return result
        }
    }

    public fun progress(
        player: Player,
        event: DailyAdventureProgressEvent,
    ): DailyAdventureProgressResult {
        val session = session(player)
        val result =
            synchronized(session) {
                ensureCurrentDay(player, session)
                val progress =
                    engine.progress(
                        session.state,
                        currentDailyAdventureDay(),
                        event,
                        monotonicNow(session.state),
                    )
                if (progress.changedAssignments > 0) persist(session)
                progress
            }
        if (result.pointsAwarded > 0L) {
            player.mes(
                "Daily Adventure task complete: +${result.pointsAwarded} Task Points."
            )
            log.info {
                "daily_task_complete account=${player.accountId} tasks=${result.completedTaskIds} " +
                    "points=${result.pointsAwarded}"
            }
        }
        return result
    }

    public fun claim(
        player: Player,
        path: DailyAdventurePath,
    ): DailyAdventureClaimResult {
        val session = session(player)
        val result =
            synchronized(session) {
                ensureCurrentDay(player, session)
                val claim =
                    engine.claim(
                        session.state,
                        currentDailyAdventureDay(),
                        path,
                        monotonicNow(session.state),
                    )
                if (claim.status == DailyAdventureClaimStatus.Claimed) persist(session)
                claim
            }
        if (result.status == DailyAdventureClaimStatus.Claimed) {
            log.info {
                "daily_path_claim account=${player.accountId} path=$path bonus=${result.bonusPoints} " +
                    "streak=${result.streak} advanced=${result.streakAdvanced}"
            }
            deliverPendingMemberBoxes(player)
        }
        return result
    }

    public fun points(player: Player): Long = state(player).taskPoints

    public fun deductPoints(player: Player, amount: Long): Boolean {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            if (!engine.spendPoints(session.state, amount, monotonicNow(session.state))) return false
            persist(session)
            log.info { "daily_shop_purchase account=${player.accountId} cost=$amount" }
            return true
        }
    }

    public fun canAddProtectionCharge(player: Player): Boolean =
        state(player).streakProtectionCharges < DailyAdventureState.MAX_PROTECTION_CHARGES

    public fun addProtectionCharge(player: Player): Boolean {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            if (
                session.state.streakProtectionCharges >=
                    DailyAdventureState.MAX_PROTECTION_CHARGES
            ) {
                return false
            }
            session.state.streakProtectionCharges++
            session.state.updatedAtEpochMillis = monotonicNow(session.state)
            persist(session)
            log.info {
                "daily_streak_protection account=${player.accountId} " +
                    "charges=${session.state.streakProtectionCharges}"
            }
            return true
        }
    }
    /** Consumes one scroll and adds one charge under the same account-session lock. */
    public fun consumeProtectionScroll(player: Player, typeId: Int): Boolean {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            if (
                session.state.streakProtectionCharges >=
                    DailyAdventureState.MAX_PROTECTION_CHARGES
            ) {
                return false
            }
            val result =
                player.invTransaction(player.inv) {
                    val inventory = select(player.inv)
                    delete {
                        from = inventory
                        obj = typeId
                        strictCount = 1
                    }
                }
            if (!result.success) return false
            session.state.streakProtectionCharges++
            session.state.updatedAtEpochMillis = monotonicNow(session.state)
            persist(session)
            log.info {
                "daily_streak_protection_consumed account=${player.accountId} " +
                    "charges=${session.state.streakProtectionCharges}"
            }
            return true
        }
    }


    public fun deliverPendingMemberBoxes(player: Player): Int {
        val session = session(player)
        var delivered = 0
        synchronized(session) {
            while (session.state.pendingMemberBoxes > 0) {
                val result =
                    player.invTransaction(player.inv) {
                        add(
                            select(player.inv),
                            MEMBER_DAILY_BOX.asRSCM(RSCMType.OBJ),
                            1,
                        )
                    }
                if (!result.success) break
                session.state.pendingMemberBoxes--
                session.state.updatedAtEpochMillis = monotonicNow(session.state)
                delivered++
            }
            if (delivered > 0) persist(session)
        }
        if (delivered > 0) {
            player.mes(
                "Your member Daily Mystery Box has been placed in your inventory."
            )
        } else if (state(player).pendingMemberBoxes > 0) {
            player.mes(
                "A member Daily Mystery Box is waiting; make one inventory space and reopen your tasks."
            )
        }
        return delivered
    }

    public fun sampleExperience(player: Player) {
        val current = readXp(player)
        val previous = xpSnapshots.put(player, current) ?: return
        for ((skill, totalXp) in current) {
            val delta = totalXp - (previous[skill] ?: totalXp)
            if (delta <= 0 || delta > MAX_CREDITABLE_XP_PER_CYCLE) continue
            val baseXp = (delta.toDouble() / max(player.xpRate, 0.01)).roundToLong()
            if (baseXp <= 0L) continue
            progress(
                player,
                DailyAdventureProgressEvent(
                    id = nextEventId(player, "xp-$skill"),
                    action = DailyAdventureAction.BaseExperience,
                    amount = baseXp,
                    skill = skill,
                    dimension = skill,
                    wilderness = player.insideWilderness,
                ),
            )
            progress(
                player,
                DailyAdventureProgressEvent(
                    id = nextEventId(player, "action-$skill"),
                    action = DailyAdventureAction.SkillingAction,
                    amount = 1L,
                    skill = skill,
                    dimension = skill,
                    wilderness = player.insideWilderness,
                ),
            )
        }
    }

    public fun nextEventId(player: Player, source: String): String =
        "${currentDailyAdventureDay()}:${player.accountId}:$source:${eventSequence.incrementAndGet()}"

    public fun forceComplete(
        player: Player,
        path: DailyAdventurePath,
        slot: Int,
    ): Boolean {
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            val assignment = session.state.assignments[path]?.firstOrNull { it.slot == slot }
                ?: return false
            if (!assignment.complete) {
                assignment.progress = assignment.goal
                assignment.complete = true
                assignment.completedAtEpochMillis = monotonicNow(session.state)
                if (!assignment.rewardCredited) {
                    assignment.rewardCredited = true
                    session.state.taskPoints += assignment.reward
                    session.state.lifetime.tasksCompleted++
                    session.state.lifetime.pointsEarned += assignment.reward
                }
                session.state.updatedAtEpochMillis = monotonicNow(session.state)
                persist(session)
            }
            return true
        }
    }

    public fun addPoints(player: Player, amount: Long): Boolean {
        if (amount <= 0L) return false
        val session = session(player)
        synchronized(session) {
            ensureCurrentDay(player, session)
            if (session.state.taskPoints > Long.MAX_VALUE - amount) return false
            session.state.taskPoints += amount
            session.state.lifetime.pointsEarned += amount
            session.state.updatedAtEpochMillis = monotonicNow(session.state)
            persist(session)
            return true
        }
    }

    public fun status(player: Player): String {
        val state = state(player)
        val active = state.activePaths.joinToString { it.name }
        return "Daily Adventure day=${state.day} member=${state.memberDayEntitlement} " +
            "active=[$active] rerolls=${state.rerollsUsedToday}/${state.rerollCap()} " +
            "points=${state.taskPoints} streak=${state.streak} pendingBoxes=${state.pendingMemberBoxes}."
    }

    private fun session(player: Player): AccountSession {
        synchronized(sessions) {
            val existing = sessions[player.accountId]
            if (existing != null) {
                synchronized(existing) { existing.players += player }
                return existing
            }
            val loaded =
                DailyAdventureStateCodec.decode(player.attr[DAILY_ADVENTURE_STATE])
                    ?: DailyAdventureState()
            return AccountSession(loaded).also {
                it.players += player
                sessions[player.accountId] = it
            }
        }
    }

    private fun ensureCurrentDay(player: Player, session: AccountSession): Boolean {
        val before = session.state.updatedAtEpochMillis
        engine.ensureCurrentDay(
            session.state,
            accountKey(player),
            currentDailyAdventureDay(),
            player.members,
            ::eligible,
            monotonicNow(session.state),
        )
        return session.state.updatedAtEpochMillis != before
    }

    private fun persist(session: AccountSession) {
        session.players.toList().forEach { player ->
            player.attr[DAILY_ADVENTURE_STATE] = DailyAdventureStateCodec.encode(session.state)
        }
    }

    private fun eligible(definition: org.rsmod.api.dailyadventure.DailyAdventureTaskDefinition): Boolean =
        definition.id in LIVE_SUPPORTED_TASKS &&
            LIVE_FEATURES.containsAll(definition.requiredFeatures)

    private fun readXp(player: Player): Map<String, Int> =
        SKILL_KEYS.associateWith { skill -> player.statMap.getXP("stat.$skill") }

    private fun accountKey(player: Player): String = "account-${player.accountId}"

    private fun monotonicNow(state: DailyAdventureState): Long =
        max(System.currentTimeMillis(), state.updatedAtEpochMillis + 1L)

    private data class AccountSession(
        var state: DailyAdventureState,
        val players: MutableSet<Player> = mutableSetOf(),
    )

    private companion object {
        const val MEMBER_DAILY_BOX = "obj.member_daily_mystery_box"
        const val MAX_CREDITABLE_XP_PER_CYCLE = 5_000_000
        val LIVE_FEATURES =
            setOf(
                DailyAdventureFeatures.BOSS_REGISTRY,
                DailyAdventureFeatures.REVENANTS,
                DailyAdventureFeatures.WILDERNESS_BOSSES,
            )
        val LIVE_SUPPORTED_TASKS =
            buildSet {
                addAll(listOf("C01", "C02", "C03", "C04", "C05", "C07", "C08", "C26", "C27"))
                addAll(listOf("C09", "C10", "C11", "C12", "C13", "C14", "C15", "C18", "C28"))
                addAll(listOf("C19", "C20", "C29", "C30", "C31", "C32", "C33"))
                addAll(listOf("S01", "S02", "S03", "S04", "S05", "S06", "S07", "S08", "S26"))
                addAll(listOf("S09", "S11", "S12", "S14", "S15", "S17", "S18", "S30", "S31"))
                addAll(listOf("S19", "S20", "S21", "S23", "S27", "S28", "S29"))
            }
        val SKILL_KEYS =
            listOf(
                "mining",
                "woodcutting",
                "fishing",
                "cooking",
                "firemaking",
                "thieving",
                "fletching",
                "crafting",
                "smithing",
                "herblore",
                "runecrafting",
                "agility",
                "farming",
                "hunter",
                "construction",
                "prayer",
            )

        fun loadOrCreatePrivateSalt(): String {
            System.getProperty("openrune.daily-adventure.salt")
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
            System.getenv("OPENRUNE_DAILY_ADVENTURE_SALT")
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
            val path = Path.of(".data", "daily-adventure.salt")
            if (Files.exists(path)) return Files.readString(path).trim().also {
                require(it.isNotBlank()) { "Daily Adventure salt file is empty: $path" }
            }
            Files.createDirectories(path.parent)
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            runCatching {
                Files.writeString(
                    path,
                    generated,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            }
            return Files.readString(path).trim()
        }
    }
}
