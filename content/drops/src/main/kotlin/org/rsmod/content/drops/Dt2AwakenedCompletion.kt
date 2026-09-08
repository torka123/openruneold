package org.rsmod.content.drops

import java.util.Base64
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.droptable.NpcKillRewardContext
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal enum class Dt2AwakenedBoss(val storageId: String, val npcNames: Set<String>) {
    VARDORVIS(
        "vardorvis",
        setOf(
            "npc.vardorvis",
            "npc.vardorvis_base_postquest",
            "npc.vardorvis_base_quest",
            "npc.vardorvis_cutscene",
            "npc.vardorvis_quest",
        ),
    ),
    LEVIATHAN("leviathan", setOf("npc.leviathan")),
    WHISPERER(
        "whisperer",
        setOf(
            "npc.whisperer",
            "npc.whisperer_melee",
            "npc.whisperer_melee_quest",
            "npc.whisperer_quest",
        ),
    ),
    DUKE(
        "duke",
        setOf(
            "npc.duke_sucellus_asleep",
            "npc.duke_sucellus_asleep_quest",
            "npc.duke_sucellus_awake",
            "npc.duke_sucellus_awake_quest",
            "npc.duke_sucellus_dead",
            "npc.duke_sucellus_dead_quest",
            "npc.duke_sucellus_inactive",
            "npc.duke_sucellus_inactive_quest",
        ),
    );

    companion object {
        private val byStorageId = entries.associateBy(Dt2AwakenedBoss::storageId)
        private val byNpcName = entries.flatMap { boss -> boss.npcNames.map { it to boss } }.toMap()

        fun fromStorageId(value: String): Dt2AwakenedBoss? = byStorageId[value]

        fun fromNpc(npc: Npc): Dt2AwakenedBoss? = byNpcName[npc.type.internalName]
    }
}

internal data class Dt2AwakenedCompletionState(
    val completed: Set<Dt2AwakenedBoss> = emptySet(),
    val kitClaimed: Boolean = false,
    val recentEventIds: List<String> = emptyList(),
)

internal data class Dt2AwakenedCompletionUpdate(
    val state: Dt2AwakenedCompletionState,
    val changed: Boolean,
    val awardKit: Boolean = false,
)

internal object Dt2AwakenedCompletionRules {
    const val KIT_OBJ = "obj.dt2_sanguine_torva_kit"
    const val MAX_RECENT_EVENT_IDS = 256

    fun record(
        current: Dt2AwakenedCompletionState,
        boss: Dt2AwakenedBoss,
        context: NpcKillRewardContext,
    ): Dt2AwakenedCompletionUpdate {
        if (!context.rewardEligible || !context.awakened) {
            return Dt2AwakenedCompletionUpdate(current, changed = false)
        }
        val eventId = context.eventId
        require(!eventId.isNullOrBlank()) {
            "Eligible awakened DT2 rewards require a stable event id."
        }
        if (eventId in current.recentEventIds) {
            return Dt2AwakenedCompletionUpdate(current, changed = false)
        }

        val firstCompletion = boss !in current.completed
        val allFourComplete = (current.completed + boss).containsAll(Dt2AwakenedBoss.entries)
        val awardKit = firstCompletion && allFourComplete && !current.kitClaimed
        val next =
            current.copy(
                completed = current.completed + boss,
                kitClaimed = current.kitClaimed || awardKit,
                recentEventIds =
                    (current.recentEventIds.filterNot { it == eventId } + eventId).takeLast(
                        MAX_RECENT_EVENT_IDS
                    ),
            )
        return Dt2AwakenedCompletionUpdate(next, changed = true, awardKit = awardKit)
    }
}

internal object Dt2AwakenedCompletionCodec {
    private const val VERSION = "v1"

    fun encode(state: Dt2AwakenedCompletionState): String =
        listOf(
                VERSION,
                state.completed.map(Dt2AwakenedBoss::storageId).sorted().joinToString(","),
                if (state.kitClaimed) "1" else "0",
                state.recentEventIds.joinToString(",") { encodeText(it) },
            )
            .joinToString("|")

    fun decode(value: String): Dt2AwakenedCompletionState {
        val fields = value.split('|', limit = 4)
        require(fields.size == 4 && fields[0] == VERSION) {
            "Malformed or unsupported DT2 awakened-completion state."
        }
        val completed =
            fields[1].split(',').filter(String::isNotBlank).map { id ->
                requireNotNull(Dt2AwakenedBoss.fromStorageId(id)) {
                    "Unknown DT2 awakened boss id: $id"
                }
            }
        val claimed =
            when (fields[2]) {
                "0" -> false
                "1" -> true
                else -> throw IllegalArgumentException("Invalid DT2 ornament-kit claimed flag.")
            }
        val events = fields[3].split(',').filter(String::isNotBlank).map(::decodeText)
        require(events.size <= Dt2AwakenedCompletionRules.MAX_RECENT_EVENT_IDS) {
            "DT2 awakened-completion event history exceeds its bound."
        }
        require(events.all(String::isNotBlank) && events.distinct().size == events.size) {
            "Malformed DT2 awakened-completion event history."
        }
        return Dt2AwakenedCompletionState(completed.toSet(), claimed, events)
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String =
        try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed DT2 awakened-completion event id.", error)
        }
}

internal object Dt2AwakenedCompletionAttributes {
    val STATE: AttributeKey<String> = AttributeKey(persistenceKey = "dt2_awakened_completion_v1")
}

public class Dt2AwakenedCompletionService public constructor() {
    private val lock = Any()

    internal fun record(
        player: Player,
        boss: Dt2AwakenedBoss,
        context: NpcKillRewardContext,
    ): Dt2AwakenedCompletionUpdate =
        synchronized(lock) {
            val current = snapshot(player)
            val update = Dt2AwakenedCompletionRules.record(current, boss, context)
            if (update.changed) {
                player.attr[Dt2AwakenedCompletionAttributes.STATE] =
                    Dt2AwakenedCompletionCodec.encode(update.state)
            }
            update
        }

    internal fun snapshot(player: Player): Dt2AwakenedCompletionState =
        player.attr[Dt2AwakenedCompletionAttributes.STATE]?.let(Dt2AwakenedCompletionCodec::decode)
            ?: Dt2AwakenedCompletionState()
}
