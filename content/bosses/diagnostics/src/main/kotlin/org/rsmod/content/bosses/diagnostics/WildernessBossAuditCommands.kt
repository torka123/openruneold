package org.rsmod.content.bosses.diagnostics

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.bosses.validation.BossAssetValidator
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Administrator-only controls for the evidence-backed Wilderness boss audit. */
class WildernessBossAuditCommands
@Inject
constructor(
    private val deps: BossDeps,
    private val encounters: EncounterRegistry,
) : PluginScript() {
    private val activeMeasurements = mutableSetOf<NpcUid>()

    override fun ScriptContext.startup() {
        onCommand("wildernessbossaudit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Validate and describe every audited Wilderness boss"
            cheat { audit(this) }
        }
        onCommand("testwildernessboss") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Spawn an owner-only, reward-free audited boss"
            invalidArgs = "Use as ::testwildernessboss <name-or-id>"
            cheat { spawnTestBoss(this) }
        }
        onCommand("measurebossattacks") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Measure real boss commit and impact ticks"
            invalidArgs = "Use as ::measurebossattacks <npc-id-or-name> [count]"
            cheat { measure(this) }
        }
        onCommand("forcebossattack") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Force the next verified boss ability"
            invalidArgs = "Use as ::forcebossattack <npc-id-or-name> <attack-key>"
            cheat { forceAttack(this) }
        }
        onCommand("resetwildernessboss") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Clear adds and pending effects, then reset a boss"
            invalidArgs = "Use as ::resetwildernessboss <npc-id-or-name>"
            cheat { reset(this) }
        }
    }

    private fun audit(cheat: Cheat) = with(cheat) {
        player.mes("Wilderness audit: active cache definitions and loaded combat owners")
        for (entry in AUDITED_BOSSES) {
            val spec = encounters.registeredBoss(entry.slug)
            val type = resolveNpc(entry.npcKey)
            if (spec == null || type == null) {
                player.mes("${entry.slug}: FAIL owner=${spec != null}, cache=${type != null}")
                continue
            }
            val validation = BossAssetValidator.validate(spec, deps.extensionRegistry)
            val rates = spec.phases.values.map { it.attackRate ?: spec.stats.attackRate }.distinct()
            player.mes(
                "${entry.slug}: npc=${type.id} ${entry.npcKey}; rate=${rates.joinToString("/")}t; " +
                    "arena=${entry.arena}; assets=${if (validation.valid) "PASS" else "FAIL"}",
            )
            player.mes("Abilities: ${spec.abilities.keys.joinToString()}; phases: ${validation.phases.joinToString()}")
            validation.assets.chunked(3).forEach { player.mes("Assets: ${it.joinToString()}") }
            validation.missing.chunked(2).forEach { player.mes("Missing: ${it.joinToString()}") }
        }
        player.mes("Automated status above is live cache validation; manual status is measured with the audit commands.")
    }

    private fun spawnTestBoss(cheat: Cheat) = with(cheat) {
        val token = args.firstOrNull()
        val entry = token?.let(::resolveAuditEntry)
        if (entry == null) {
            player.mes("Unknown audited boss. Use: ${AUDITED_BOSSES.joinToString { it.slug }}")
            return@with
        }
        val type = resolveNpc(entry.npcKey)
        if (type == null) {
            player.mes("Active cache is missing ${entry.npcKey}.")
            return@with
        }
        val spawn = findSpawnTile(player.coords, type.size)
        if (spawn == null) {
            player.mes("No safe nearby tile can fit this boss.")
            return@with
        }
        val npc = Npc(type, spawn)
        npc.mode = type.defaultMode
        npc.suppressRewards = true
        npc.assignSpawnOwner(player, deps.mapClock.cycle)
        deps.npcRepo.add(npc, TEST_DURATION_TICKS)
        player.mes(
            "Spawned reward-free ${entry.slug} (npc ${type.id}) for you only. " +
                "It expires in $TEST_DURATION_TICKS ticks.",
        )
    }

    private fun measure(cheat: Cheat) = with(cheat) {
        val token = args.firstOrNull()
        val count = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_MEASURE_COUNT
        if (token == null || count !in MIN_MEASURE_COUNT..MAX_MEASURE_COUNT) {
            player.mes("Use ::measurebossattacks <npc-id-or-name> [20-$MAX_MEASURE_COUNT]")
            return@with
        }
        val npc = nearestAuditedBoss(player, token)
        if (npc == null) {
            player.mes("No matching audited boss is within $SEARCH_RADIUS tiles.")
            return@with
        }
        if (!activeMeasurements.add(npc.uid)) {
            player.mes("That boss already has an active measurement.")
            return@with
        }
        val encounter = encounters.existing(npc) ?: encounters.of(npc)
        val startTick = deps.mapClock.cycle
        val timeout = (count * encounter.expectedAttackRate() * 10).coerceAtLeast(600)
        player.mes("Measuring $count intervals on npc ${npc.id}; keep it attacking you.")
        pollMeasurement(player, npc, encounter, startTick, count, startTick + timeout)
    }

    private fun pollMeasurement(
        player: Player,
        npc: Npc,
        encounter: BossEncounter,
        startTick: Int,
        count: Int,
        timeoutTick: Int,
    ) {
        deps.worldQueues.add(1) {
            if (!player.isSlotAssigned || !npc.isSlotAssigned || encounters.existing(npc) !== encounter) {
                activeMeasurements.remove(npc.uid)
                if (player.isSlotAssigned) player.mes("Boss measurement cancelled: encounter ended or reset.")
                return@add
            }
            val traces = encounter.attackTracesAfter(startTick)
            if (traces.size >= count + 1) {
                activeMeasurements.remove(npc.uid)
                var passed = 0
                for (index in 0 until count) {
                    val attack = traces[index]
                    val next = traces[index + 1]
                    val interval = next.commitTick - attack.commitTick
                    val ok = interval == attack.expectedRate
                    if (ok) passed++
                    player.mes(
                        "#${index + 1} ${attack.ability} style=${attack.style ?: "external"} " +
                            "commit=${attack.commitTick} impact=${attack.actualImpactTick ?: "-"}/" +
                            "${attack.expectedImpactTick ?: "-"} next=${next.commitTick} " +
                            "interval=$interval expected=${attack.expectedRate} ${if (ok) "PASS" else "FAIL"}",
                    )
                }
                player.mes("Attack measurement: $passed/$count intervals passed.")
                return@add
            }
            if (deps.mapClock.cycle >= timeoutTick) {
                activeMeasurements.remove(npc.uid)
                player.mes("Measurement timed out after ${traces.size} commits; boss must stay in combat.")
                return@add
            }
            pollMeasurement(player, npc, encounter, startTick, count, timeoutTick)
        }
    }

    private fun forceAttack(cheat: Cheat) = with(cheat) {
        val token = args.getOrNull(0)
        val ability = args.getOrNull(1)
        if (token == null || ability == null) {
            player.mes("Use ::forcebossattack <npc-id-or-name> <attack-key>")
            return@with
        }
        val npc = nearestAuditedBoss(player, token)
        if (npc == null) {
            player.mes("No matching audited boss is within $SEARCH_RADIUS tiles.")
            return@with
        }
        val encounter = encounters.existing(npc) ?: encounters.of(npc)
        if (!encounter.forceNextAbility(ability)) {
            player.mes("Unknown attack '$ability'. Valid: ${encounter.spec.abilities.keys.joinToString()}")
            return@with
        }
        player.mes("${encounter.spec.npcTypes.first()}: next attack forced to $ability.")
    }

    private fun reset(cheat: Cheat) = with(cheat) {
        val token = args.firstOrNull()
        val npc = token?.let { nearestAuditedBoss(player, it) }
        if (npc == null) {
            player.mes("No matching audited boss is within $SEARCH_RADIUS tiles.")
            return@with
        }
        activeMeasurements.remove(npc.uid)
        BossCombat.resetEncounter(npc, deps)
        player.mes("Reset npc ${npc.id}: health, phase, transform, adds, and delayed encounter effects cleared.")
    }

    private fun nearestAuditedBoss(player: Player, token: String): Npc? {
        val requestedId = token.toIntOrNull()
        val requestedEntry = if (requestedId == null) resolveAuditEntry(token) else null
        val requestedSpec = requestedEntry?.let { encounters.registeredBoss(it.slug) }
        val allowedSpecs = AUDITED_BOSSES.mapNotNull { encounters.registeredBoss(it.slug) }.toSet()
        return deps.npcRepo
            .findAll(ZoneKey.from(player.coords), zoneRadius = 4)
            .filter { it.isVisible && it.coords.level == player.coords.level }
            .filter { it.coords.chebyshevDistance(player.coords) <= SEARCH_RADIUS }
            .filter { encounters.registeredSpec(it) in allowedSpecs }
            .filter { npc ->
                when {
                    requestedId != null -> npc.id == requestedId || npc.visType.id == requestedId
                    requestedSpec != null -> encounters.registeredSpec(npc) === requestedSpec
                    else -> false
                }
            }
            .minByOrNull { it.coords.chebyshevDistance(player.coords) }
    }

    private fun resolveAuditEntry(token: String): AuditBoss? {
        val id = token.toIntOrNull()
        if (id != null) {
            return AUDITED_BOSSES.firstOrNull { resolveNpc(it.npcKey)?.id == id }
        }
        val normalized = token.lowercase().removePrefix("npc.").replace('_', '-').replace(' ', '-')
        return AUDITED_BOSSES.firstOrNull { normalized == it.slug || normalized in it.aliases }
    }

    private fun findSpawnTile(center: CoordGrid, size: Int): CoordGrid? {
        for (radius in 4..9) {
            val candidates = listOf(
                center.translate(radius, 0),
                center.translate(-radius, 0),
                center.translate(0, radius),
                center.translate(0, -radius),
            )
            for (candidate in candidates) {
                if ((0 until size).all { dx ->
                        (0 until size).all { dz -> !deps.collision.isWalkBlocked(candidate.translate(dx, dz)) }
                    }
                ) return candidate
            }
        }
        return null
    }

    private fun resolveNpc(key: String) =
        ServerCacheManager.getNpc(key.asRSCM(RSCMType.NPC))
}

private data class AuditBoss(
    val slug: String,
    val npcKey: String,
    val arena: String,
    val aliases: Set<String> = emptySet(),
)

private val AUDITED_BOSSES = listOf(
    AuditBoss("chaos-fanatic", "npc.chaos_fanatic", "Wilderness multi", setOf("fanatic")),
    AuditBoss("scorpia", "npc.scorpia", "Scorpion Pit multi"),
    AuditBoss("king-black-dragon", "npc.king_dragon", "KBD lair (non-Wilderness)", setOf("kbd", "king-dragon")),
    AuditBoss("chaos-elemental", "npc.chaoselemental", "Wilderness multi", setOf("chaoselemental")),
    AuditBoss("revenant-maledictus", "npc.wild_cave_superior", "Revenant Caves multi", setOf("maledictus", "wild-cave-superior")),
    AuditBoss("callisto", "npc.callisto", "Wilderness multi"),
    AuditBoss("artio", "npc.callisto_singles", "Wilderness singles-plus", setOf("callisto-singles")),
    AuditBoss("venenatis", "npc.venenatis", "Wilderness multi"),
    AuditBoss("spindel", "npc.venenatis_singles", "Wilderness singles-plus", setOf("venenatis-singles")),
    AuditBoss("vetion", "npc.vetion", "Wilderness multi", setOf("vet-ion")),
    AuditBoss("calvarion", "npc.vetion_single", "Wilderness singles-plus", setOf("calvar-ion", "vetion-single")),
)

private const val DEFAULT_MEASURE_COUNT = 20
private const val MIN_MEASURE_COUNT = 20
private const val MAX_MEASURE_COUNT = 50
private const val SEARCH_RADIUS = 32
private const val TEST_DURATION_TICKS = 2500
